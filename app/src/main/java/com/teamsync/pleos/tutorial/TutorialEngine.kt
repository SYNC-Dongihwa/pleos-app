package com.teamsync.pleos.tutorial

import com.teamsync.pleos.vehicle.SignalChange
import com.teamsync.pleos.vehicle.Support
import com.teamsync.pleos.vehicle.VehicleConnection
import com.teamsync.pleos.vehicle.VehicleState

enum class Outcome(val label: String) { COMPLETED("완료"), SKIPPED("건너뜀"), UNSUPPORTED("지원 안 됨") }

data class StepResult(val outcome: Outcome, val detail: String)

sealed interface Mode {
    data object Idle : Mode
    data object Running : Mode
    data class Paused(val reason: String) : Mode
    data object Finished : Mode
}

data class TutorialState(
    val mode: Mode = Mode.Idle,
    val stepIndex: Int = 0,
    val phaseIndex: Int = 0,
    val results: Map<String, StepResult> = emptyMap(),
    /** 지금 화면·음성으로 내는 안내. 단계를 아직 시작하지 못했으면 null. */
    val instruction: String? = null,
    /** 지원 여부·기준값을 기다리는 중이면 그 이유. */
    val waiting: String? = null,
    /** 20초 무변화로 나간 위치 힌트. */
    val hint: String? = null,
    val currentValue: Any? = null,
    /** 이번 단계에서 판정에 쓰인 전이 ("꺼짐 → 켜짐"). */
    val evidence: List<String> = emptyList(),
    /** 방금 끝난 단계 알림 ("전조등 완료"). */
    val lastCompleted: String? = null,
    /** 완료 화면에서 한 단계만 다시 하는 중. */
    val singleStep: Boolean = false,
    val parked: Boolean = false,
    val parkedReason: String? = null,
)

/** 엔진이 바깥(컨트롤러)에 요청하는 부수 효과. 엔진 자체는 Android를 모른다 → 단위 테스트 가능. */
sealed interface Effect {
    data class Speak(val text: String) : Effect
    data class Log(val level: Char, val message: String) : Effect
    data object Save : Effect
}

/**
 * 튜토리얼 상태 기계. 입력: 차량 스냅샷(지원 여부·기준값), 값 변화 이벤트(전이 판정), 정차 게이트, 1초 tick(힌트), 사용자 조작.
 * 완료 판정은 [SignalChange.source]가 "live"(SDK 콜백)인 전이로만 한다 — 타이머·버튼으로 완료 처리하지 않는다.
 * 스레드 안전하지 않음: 컨트롤러가 단일 스레드에서만 호출한다.
 */
class TutorialEngine(
    val steps: List<TutorialStep>,
    private val hintAfterMs: Long = HINT_AFTER_MS,
) {
    var state = TutorialState()
        private set

    private var snapshot = VehicleState()
    private var original: Any? = null
    private var phaseStart: Any? = null
    private var lastValue: Any? = null
    private var lastActivity = 0L
    private var hintGiven = false
    private var prefix = ""
    private val fx = mutableListOf<Effect>()

    val isMaster: Boolean
        get() = steps.all { state.results[it.id]?.outcome in setOf(Outcome.COMPLETED, Outcome.UNSUPPORTED) } &&
            steps.any { state.results[it.id]?.outcome == Outcome.COMPLETED }

    val allResolved: Boolean get() = steps.all { it.id in state.results }

    private val current: TutorialStep? get() = steps.getOrNull(state.stepIndex)

    // ---- 입력 ------------------------------------------------------------------------------

    fun restore(saved: Map<String, StepResult>): List<Effect> = effects {
        val known = steps.map { it.id }.toSet()
        state = state.copy(results = saved.filterKeys { it in known })
        log('I', "restore ${state.results.size}/${steps.size} results")
    }

    fun start(fromBeginning: Boolean, now: Long): List<Effect> = effects {
        if (!state.parked) {
            log('W', "start ignored: not parked (${state.parkedReason})")
            return@effects
        }
        val results = if (fromBeginning) emptyMap() else state.results
        state = state.copy(mode = Mode.Running, results = results, singleStep = false, lastCompleted = null)
        log('I', "START fromBeginning=$fromBeginning resolved=${results.size}/${steps.size}")
        if (fromBeginning) fx += Effect.Save
        prefix = if (results.isEmpty()) "튜토리얼을 시작할게요. " else "이어서 할게요. "
        enter(nextUnresolved(from = 0), now)
    }

    fun retry(stepId: String, now: Long): List<Effect> = effects {
        val index = steps.indexOfFirst { it.id == stepId }
        if (index < 0 || !state.parked) {
            log('W', "retry ignored id=$stepId parked=${state.parked}")
            return@effects
        }
        // 이전 결과는 새 결과가 기록될 때까지 유지 — 다시 해보기 도중 그만두면 원래 결과가 남는다 (10-27 화면 확인 중 발견)
        state = state.copy(mode = Mode.Running, singleStep = true, lastCompleted = null)
        log('I', "RETRY $stepId")
        prefix = ""
        enter(index, now)
    }

    fun skip(now: Long): List<Effect> = effects {
        val step = current ?: return@effects
        if (state.mode !is Mode.Running && state.mode !is Mode.Paused) return@effects
        val seen = state.evidence.joinToString(", ").ifEmpty { "관찰된 전이 없음" }
        record(step, Outcome.SKIPPED, "사용자가 건너뜀 ($seen)")
        log('I', "SKIP ${step.id} phase=${state.phaseIndex + 1}/${step.phases.size} ($seen)")
        prefix = "${topic(step.title)} 건너뛸게요. "
        if (state.mode is Mode.Paused) {
            // 일시정지 중 건너뛰기는 기록만, 다음 단계는 재개할 때 시작
            state = state.copy(stepIndex = nextUnresolved(state.stepIndex + 1), phaseIndex = 0, instruction = null, evidence = emptyList(), waiting = WAIT_RESUME)
        } else {
            next(now)
        }
    }

    fun quit(): List<Effect> = effects {
        log('I', "QUIT at step=${current?.id} phase=${state.phaseIndex + 1}")
        state = state.copy(mode = Mode.Idle, instruction = null, hint = null, waiting = null, singleStep = false)
        fx += Effect.Save
    }

    fun showResults(): List<Effect> = effects {
        state = state.copy(mode = Mode.Finished, singleStep = false)
    }

    fun repeat(): List<Effect> = effects {
        val text = listOfNotNull(state.instruction, state.hint).joinToString(" ")
        if (text.isNotEmpty()) speak(text)
    }

    fun onGate(parked: Boolean, reason: String?, now: Long): List<Effect> = effects {
        state = state.copy(parked = parked, parkedReason = reason)
        val mode = state.mode
        if (!parked && mode is Mode.Running) {
            state = state.copy(mode = Mode.Paused(reason ?: "정차 상태 아님"))
            log('W', "PAUSE step=${current?.id} reason=$reason")
            speak("차가 움직일 수 있는 상태라 튜토리얼을 잠시 멈출게요. 기어를 P로 바꾸면 이어서 할게요.")
        } else if (parked && mode is Mode.Paused) {
            state = state.copy(mode = Mode.Running, hint = null)
            hintGiven = false
            lastActivity = now
            log('I', "RESUME step=${current?.id}")
            if (state.instruction == null) {
                prefix = "다시 시작할게요. "
                if (current == null) finish() else tryBegin(now)
            } else {
                lastValue = current?.let { snapshot[it.signal].value } ?: lastValue
                // 멈춘 동안 바뀐 값을 새 기준으로: "1도 내려 보세요"는 재개 시점부터 (원래 값 복귀 목표는 original 기준이라 영향 없음)
                phaseStart = lastValue
                state = state.copy(currentValue = lastValue)
                speak("다시 시작할게요. ${state.instruction}")
            }
        }
    }

    fun onSnapshot(v: VehicleState, now: Long): List<Effect> = effects {
        snapshot = v
        val step = current ?: return@effects
        if (state.mode != Mode.Running) return@effects
        if (state.instruction == null) {
            tryBegin(now)
        } else if (v[step.signal].support is Support.Unsupported) {
            // 진행 도중 capability 시간 초과 등으로 미지원 판정이 나온 경우
            unsupported(step, (v[step.signal].support as Support.Unsupported).reason, now)
        }
    }

    fun onChange(c: SignalChange, now: Long): List<Effect> = effects {
        val step = current ?: return@effects
        if (c.signal != step.signal) return@effects
        if (state.mode != Mode.Running && state.mode !is Mode.Paused) return@effects
        if (state.instruction == null) return@effects // 아직 기준값 전 (onSnapshot이 시작함)
        val prev = lastValue
        if (c.value == prev) return@effects // 스냅샷으로 이미 반영된 값
        lastValue = c.value
        state = state.copy(currentValue = c.value)
        val transition = "${step.valueLabel(prev)} → ${step.valueLabel(c.value)}"
        if (state.mode is Mode.Paused) {
            log('I', "CHANGE ${step.id} $prev→${c.value} ignored (paused)")
            return@effects
        }
        lastActivity = now
        if (c.source != "live") {
            log('I', "CHANGE ${step.id} $prev→${c.value} source=${c.source} — 콜백이 아니라 판정 안 함")
            return@effects
        }
        val phase = step.phases[state.phaseIndex]
        val met = phase.goal.isMet(GoalContext(original, phaseStart, prev, c.value))
        log(
            'I',
            "JUDGE ${step.id} phase=${state.phaseIndex + 1}/${step.phases.size} goal='${phase.goal}' " +
                "original=$original phaseStart=$phaseStart $prev→${c.value} (live) → ${if (met) "충족" else "미충족"}",
        )
        if (!met) return@effects
        state = state.copy(evidence = state.evidence + transition)
        if (state.phaseIndex + 1 < step.phases.size) {
            phaseStart = c.value
            hintGiven = false
            val nextPhase = step.phases[state.phaseIndex + 1]
            val text = nextPhase.instruction(original)
            state = state.copy(phaseIndex = state.phaseIndex + 1, instruction = text, hint = null)
            speak(text)
        } else {
            record(step, Outcome.COMPLETED, state.evidence.joinToString(", "))
            log('I', "STEP ${step.id} COMPLETED (${state.evidence.joinToString(", ")})")
            state = state.copy(lastCompleted = "${step.title} 완료")
            prefix = "${step.title} 완료! "
            next(now)
        }
    }

    fun tick(now: Long): List<Effect> = effects {
        val step = current ?: return@effects
        if (state.mode != Mode.Running || state.instruction == null || hintGiven) return@effects
        if (now - lastActivity < hintAfterMs) return@effects
        hintGiven = true
        val hint = step.phases[state.phaseIndex].hint
        state = state.copy(hint = hint)
        log('I', "HINT ${step.id} phase=${state.phaseIndex + 1} after ${(now - lastActivity) / 1000}s without change")
        speak(hint)
    }

    // ---- 내부 ------------------------------------------------------------------------------

    private fun enter(index: Int, now: Long) {
        if (index !in steps.indices) {
            finish()
            return
        }
        state = state.copy(stepIndex = index, phaseIndex = 0, instruction = null, waiting = null, hint = null, evidence = emptyList(), currentValue = null)
        tryBegin(now)
    }

    private fun tryBegin(now: Long) {
        val step = current ?: return finish()
        if (snapshot.connection is VehicleConnection.Failed) {
            state = state.copy(waiting = "차량 연결 안 됨: ${(snapshot.connection as VehicleConnection.Failed).reason}")
            return
        }
        val sig = snapshot[step.signal]
        when (val support = sig.support) {
            is Support.Unsupported -> unsupported(step, support.reason, now)
            Support.Checking -> state = state.copy(waiting = "이 차가 ${step.title} 확인을 지원하는지 알아보고 있어요")
            is Support.Supported -> {
                if (sig.value == null) {
                    state = state.copy(waiting = "${step.title} 현재 상태를 확인하고 있어요")
                    return
                }
                original = sig.value
                phaseStart = sig.value
                lastValue = sig.value
                lastActivity = now
                hintGiven = false
                val phase = step.phases[0]
                val text = phase.instruction(original)
                state = state.copy(waiting = null, instruction = text, currentValue = sig.value, hint = null)
                log('I', "STEP ${step.id} start original=${sig.value} (${step.valueLabel(sig.value)}) goal='${phase.goal}'")
                speak(prefix + text)
                prefix = ""
            }
        }
    }

    private fun unsupported(step: TutorialStep, reason: String, now: Long) {
        record(step, Outcome.UNSUPPORTED, reason)
        log('W', "STEP ${step.id} UNSUPPORTED: $reason")
        prefix += "${topic(step.title)} 이 차에서 확인할 수 없어 넘어갈게요. "
        next(now)
    }

    private fun next(now: Long) {
        if (state.singleStep) finish() else enter(nextUnresolved(state.stepIndex + 1), now)
    }

    private fun nextUnresolved(from: Int): Int {
        val i = (from until steps.size).firstOrNull { steps[it].id !in state.results }
        return i ?: steps.size
    }

    private fun finish() {
        val counts = Outcome.entries.associateWith { o -> state.results.values.count { it.outcome == o } }
        state = state.copy(mode = Mode.Finished, instruction = null, waiting = null, hint = null, singleStep = false)
        log('I', "FINISH ${counts.map { "${it.key}=${it.value}" }} master=$isMaster")
        val summary = "완료 ${counts[Outcome.COMPLETED]}개, 건너뜀 ${counts[Outcome.SKIPPED]}개, 지원 안 됨 ${counts[Outcome.UNSUPPORTED]}개예요."
        speak(prefix + if (allResolved) "튜토리얼을 마쳤어요. $summary" + (if (isMaster) " 내 차 마스터 배지를 받았어요!" else "") else summary)
        prefix = ""
        fx += Effect.Save
    }

    private fun record(step: TutorialStep, outcome: Outcome, detail: String) {
        state = state.copy(results = state.results + (step.id to StepResult(outcome, detail)))
        fx += Effect.Save
    }

    private fun speak(text: String) {
        fx += Effect.Speak(text)
    }

    private fun log(level: Char, message: String) {
        fx += Effect.Log(level, message)
    }

    private inline fun effects(block: () -> Unit): List<Effect> {
        fx.clear()
        block()
        return fx.toList().also { fx.clear() }
    }

    companion object {
        const val HINT_AFTER_MS = 20_000L
        private const val WAIT_RESUME = "정차하면 다음 단계를 시작해요"
    }
}

/** 한국어 주제 조사: 받침이 있으면 "은", 없으면 "는" ("전조등은", "주행 모드는"). */
internal fun topic(word: String): String {
    val last = word.lastOrNull() ?: return word
    if (last !in '가'..'힣') return "${word}은(는)"
    val hasFinalConsonant = (last - '가') % 28 != 0
    return word + if (hasFinalConsonant) "은" else "는"
}
