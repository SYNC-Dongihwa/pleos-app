package com.teamsync.pleos.tutorial

import com.teamsync.pleos.vehicle.VehicleSignal
import kotlin.math.abs

/**
 * 튜토리얼 단계 = 데이터. 엔진은 단계 내용을 모르고 [phases]를 순서대로 판정만 한다 → 단계 추가 시 엔진 수정 불필요.
 * 상태를 바꾸는 단계는 마지막 phase에서 원래 값으로 되돌렸는지까지 확인한다 ([Goals.returnedToOriginal]).
 */
data class TutorialStep(
    /** 진행 저장 키. 바꾸면 저장된 결과와 연결이 끊긴다. */
    val id: String,
    val title: String,
    val signal: VehicleSignal,
    val phases: List<Phase>,
    /** SDK 원시값 → 화면·음성용 말 (null = 아직 모름). */
    val valueLabel: (Any?) -> String,
)

/** 상황별 코스. 단계는 여러 코스에 속할 수 있고, 결과는 단계 id로 공유된다. */
data class Course(
    val id: String,
    val title: String,
    /** 언제 필요한지 한 줄 ("주유소·충전소·정비소에서"). */
    val subtitle: String,
    val stepIds: List<String>,
)

data class Phase(
    /** 단계 시작 시점의 값(original)을 보고 문구를 고른다 (예: 이미 켜져 있으면 "꺼 보세요"). 화면과 TTS에 같이 쓴다. */
    val instruction: (original: Any?) -> String,
    /** 20초 동안 변화가 없을 때 한 번 더 안내하는 위치 힌트. */
    val hint: String,
    val goal: Goal,
)

/** 판정은 콜백으로 받은 값 변화(previous → current)가 올 때만 일어난다. 그래서 모든 완료는 실제 상태 전이를 근거로 한다. */
data class GoalContext(
    /** 단계 시작 시점 값. */
    val original: Any?,
    /** 현재 phase 시작 시점 값. */
    val phaseStart: Any?,
    val previous: Any?,
    val current: Any?,
)

class Goal(val description: String, private val test: (GoalContext) -> Boolean) {
    fun isMet(c: GoalContext): Boolean = runCatching { test(c) }.getOrDefault(false)
    override fun toString() = description
}

object Goals {
    fun changedFromOriginal() = Goal("원래 값에서 바뀜") { it.current != it.original }

    fun returnedToOriginal() = Goal("원래 값으로 돌아옴") { it.current == it.original }

    fun becomes(value: Any) = Goal("$value 이 됨") { it.current == value }

    /** 숫자 값이 phase 시작보다 [delta] 이상 작아짐 (온도 1도 내리기 등). */
    fun decreasedBy(delta: Float) = Goal("$delta 이상 감소") {
        val start = (it.phaseStart as Number).toFloat()
        val now = (it.current as Number).toFloat()
        start - now >= delta - EPSILON
    }

    /** 숫자 값이 phase 시작보다 [delta] 이상 커짐 (바람 세기 한 단계 올리기 등). */
    fun increasedBy(delta: Float) = Goal("$delta 이상 증가") {
        val start = (it.phaseStart as Number).toFloat()
        val now = (it.current as Number).toFloat()
        now - start >= delta - EPSILON
    }

    /** 숫자 값이 phase 시작에서 [delta] 이상 움직임 (방향 무관 — 창문 값의 0=닫힘 여부가 문서에 없어서). */
    fun movedBy(delta: Int) = Goal("$delta 이상 움직임") {
        abs((it.current as Number).toInt() - (it.phaseStart as Number).toInt()) >= delta
    }

    private const val EPSILON = 0.01f
}

/**
 * 켜기→되돌리기 형태 단계. 처음 값이 켜짐/꺼짐인지에 따라 문구를 고르고,
 * 1단계 = 원래 값에서 바뀜, 2단계 = 원래 값으로 돌아옴.
 */
fun toggleStep(
    id: String,
    title: String,
    signal: VehicleSignal,
    isOn: (Any?) -> Boolean,
    turnOn: String,
    turnOff: String,
    restoreOn: String,
    restoreOff: String,
    hint: String,
    restoreHint: String = hint,
    valueLabel: (Any?) -> String,
) = TutorialStep(
    id = id,
    title = title,
    signal = signal,
    phases = listOf(
        Phase({ original -> if (isOn(original)) turnOff else turnOn }, hint, Goals.changedFromOriginal()),
        Phase({ original -> if (isOn(original)) restoreOn else restoreOff }, restoreHint, Goals.returnedToOriginal()),
    ),
    valueLabel = valueLabel,
)
