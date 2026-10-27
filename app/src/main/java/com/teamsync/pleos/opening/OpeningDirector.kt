package com.teamsync.pleos.opening

import android.content.Context
import android.content.pm.PackageManager
import com.teamsync.pleos.car.GateState
import com.teamsync.pleos.log.AppLog
import com.teamsync.pleos.tutorial.Mode
import com.teamsync.pleos.tutorial.ProgressStore
import com.teamsync.pleos.tutorial.TutorialController
import com.teamsync.pleos.tutorial.TutorialState
import com.teamsync.pleos.vehicle.SignalChange
import com.teamsync.pleos.vehicle.VehicleSignal
import com.teamsync.pleos.vehicle.VehicleStateRepository
import com.teamsync.pleos.voice.SpeechGuide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 오프닝 재생 조건 (순수 함수 — 단위 테스트). */
object OpeningRules {
    const val DOOR_COOLDOWN_MS = 60_000L

    /** 홈에서 아직 튜토리얼을 시작한 적 없을 때 운전석 문이 닫힘→열림으로 바뀌면(콜백) 재생. */
    fun shouldPlayOnDoor(c: SignalChange, tutorial: TutorialState, visible: Boolean, lastPlayedAt: Long): Boolean =
        c.signal == VehicleSignal.DRIVER_DOOR &&
            c.source == "live" &&
            (c.previous as? Number)?.toInt() == 0 &&
            ((c.value as? Number)?.toInt() ?: 0) > 0 &&
            tutorial.mode == Mode.Idle &&
            tutorial.results.isEmpty() &&
            !visible &&
            c.time - lastPlayedAt >= DOOR_COOLDOWN_MS
}

/**
 * "새 차 첫날" 오프닝: 첫 실행 또는 운전석 문이 열리는 순간 환영 화면·음성 → 튜토리얼 시작으로 연결.
 * 실내등 연출은 `CONTROL_CAR_LIGHTS`가 부여된 경우에만 (release는 App Market 승인 전이라 선언하지 않음 → 로그만 남기고 생략).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpeningDirector(
    private val context: Context,
    private val vehicle: VehicleStateRepository,
    private val gate: StateFlow<GateState>,
    private val tutorial: TutorialController,
    private val speech: SpeechGuide,
    private val store: ProgressStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    private var lastPlayedAt = 0L

    init {
        scope.launch {
            if (!store.openingSeen()) play("first_launch")
            vehicle.changes.collect { c ->
                if (OpeningRules.shouldPlayOnDoor(c, tutorial.state.value, _visible.value, lastPlayedAt)) play("driver_door")
            }
        }
    }

    fun startTutorial() {
        AppLog.i(TAG, "UI start from opening")
        _visible.value = false
        tutorial.start(fromBeginning = true)
    }

    fun dismiss() {
        AppLog.i(TAG, "UI dismiss")
        _visible.value = false
    }

    private suspend fun play(trigger: String) {
        lastPlayedAt = System.currentTimeMillis()
        _visible.value = true
        val parked = gate.value is GateState.Parked
        AppLog.i(TAG, "OPENING play trigger=$trigger parked=$parked")
        speech.speak(
            "새 차에 오신 걸 환영해요. 처음 타는 차의 기능을 하나씩 직접 조작해 보면서 익혀 볼게요. " +
                if (parked) "준비되면 시작하기를 눌러 주세요." else "차를 세우고 기어를 P로 바꾸면 시작할 수 있어요.",
        )
        lighting()
        store.markOpeningSeen()
    }

    private fun lighting() {
        val granted = context.checkSelfPermission(CONTROL_CAR_LIGHTS) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            AppLog.i(TAG, "OPENING lighting skipped: $CONTROL_CAR_LIGHTS not granted (App Market 승인 후 매니페스트에 추가)")
            return
        }
        vehicle.flashCabinLight(LIGHT_MS)
    }

    private companion object {
        const val TAG = "Opening"
        const val CONTROL_CAR_LIGHTS = "pleos.car.permission.CONTROL_CAR_LIGHTS"
        const val LIGHT_MS = 4_000L
    }
}
