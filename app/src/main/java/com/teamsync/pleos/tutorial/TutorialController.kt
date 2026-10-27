package com.teamsync.pleos.tutorial

import com.teamsync.pleos.car.GateState
import com.teamsync.pleos.log.AppLog
import com.teamsync.pleos.vehicle.VehicleStateRepository
import com.teamsync.pleos.voice.SpeechGuide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 엔진 ↔ 바깥 세계 연결. 차량 신호·게이트를 엔진에 넣고, 엔진이 요청한 음성·로그·저장을 실행한다.
 * 엔진 호출은 전부 단일 스레드 디스패처에서 (엔진은 스레드 안전하지 않음).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TutorialController(
    private val vehicle: VehicleStateRepository,
    private val gate: StateFlow<GateState>,
    private val speech: SpeechGuide,
    private val store: ProgressStore,
) {
    private val engine = TutorialEngine(TutorialSteps.all, TutorialSteps.courses + TutorialSteps.everything)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    private val _state = MutableStateFlow(engine.state)
    val state: StateFlow<TutorialState> = _state.asStateFlow()

    val steps: List<TutorialStep> get() = engine.steps
    /** 상황별 코스 (홈 카드). 전체 진행은 [everything]. */
    val courses: List<Course> get() = TutorialSteps.courses
    val everything: Course get() = TutorialSteps.everything
    val isMaster: Boolean get() = engine.isMaster
    fun course(id: String): Course? = engine.course(id)
    fun isCourseComplete(id: String): Boolean = engine.isCourseComplete(id)
    fun isCourseResolved(id: String): Boolean = engine.isCourseResolved(id)

    init {
        scope.launch {
            apply(engine.restore(store.load()))
            launch { vehicle.state.collect { apply(engine.onSnapshot(it, now())) } }
            launch { vehicle.changes.collect { apply(engine.onChange(it, now())) } }
            launch {
                // 정차 해제는 즉시 반영(안전), 정차 복귀는 짧게 확인 후 반영 — D→P 때 기어가 규제 해제보다 먼저 와서 생기는 깜빡임 방지 (§7-12)
                gate.collectLatest { g ->
                    if (g is GateState.Parked) delay(RESUME_DEBOUNCE_MS)
                    apply(engine.onGate(g is GateState.Parked, reasonOf(g), now()))
                }
            }
            launch {
                while (isActive) {
                    delay(TICK_MS)
                    apply(engine.tick(now()))
                }
            }
        }
    }

    fun start(courseId: String, fromBeginning: Boolean) = act("start($courseId, fromBeginning=$fromBeginning)") { engine.start(courseId, fromBeginning, it) }
    fun skip() = act("skip") { engine.skip(it) }
    fun repeat() = act("repeat") { engine.repeat() }
    fun quit() = act("quit") { engine.quit() }
    fun retry(stepId: String) = act("retry($stepId)") { engine.retry(stepId, it) }
    fun showResults(courseId: String) = act("showResults($courseId)") { engine.showResults(courseId) }

    private fun act(name: String, block: (Long) -> List<Effect>) {
        AppLog.i(TAG, "UI $name")
        scope.launch { apply(block(now())) }
    }

    private fun apply(effects: List<Effect>) {
        _state.value = engine.state
        var save = false
        for (e in effects) {
            when (e) {
                is Effect.Speak -> speech.speak(e.text)
                is Effect.Log -> when (e.level) {
                    'E' -> AppLog.e(TAG, e.message)
                    'W' -> AppLog.w(TAG, e.message)
                    else -> AppLog.i(TAG, e.message)
                }
                Effect.Save -> save = true
            }
        }
        if (save) {
            val snapshot = engine.state.results
            scope.launch(Dispatchers.IO) { store.save(snapshot) }
        }
    }

    private fun reasonOf(g: GateState) = when (g) {
        GateState.Parked -> null
        is GateState.NotParked -> g.reason
        is GateState.Unknown -> g.reason
    }

    private fun now() = System.currentTimeMillis()

    private companion object {
        const val TAG = "Tutorial"
        const val RESUME_DEBOUNCE_MS = 800L
        const val TICK_MS = 1_000L
    }
}
