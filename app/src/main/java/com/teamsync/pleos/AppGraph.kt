package com.teamsync.pleos

import android.app.Application
import com.teamsync.pleos.car.AaosCarMonitor
import com.teamsync.pleos.car.GateState
import com.teamsync.pleos.car.ParkingGate
import com.teamsync.pleos.log.AppLog
import com.teamsync.pleos.opening.OpeningDirector
import com.teamsync.pleos.tutorial.ProgressStore
import com.teamsync.pleos.tutorial.TutorialController
import com.teamsync.pleos.vehicle.VehicleStateRepository
import com.teamsync.pleos.voice.SpeechGuide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * 앱 프로세스 단위 객체. SDK 구독은 프로세스 동안 한 번만 등록한다 —
 * 화면 단위로 등록·해제하면 같은 속성 구독이 앱 전체에서 지워지는 SDK 동작(CLAUDE.md §7-4)에 걸린다.
 */
object AppGraph {
    lateinit var vehicle: VehicleStateRepository
        private set
    lateinit var car: AaosCarMonitor
        private set
    lateinit var gate: StateFlow<GateState>
        private set
    lateinit var speech: SpeechGuide
        private set
    lateinit var tutorial: TutorialController
        private set
    lateinit var opening: OpeningDirector
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(app: Application) {
        // vehicle을 먼저 시작해야 Car 연결 시 넘어오는 AAOS 속성(주유구·충전구) 상태가 vehicle.start()의 초기화에 지워지지 않는다
        vehicle = VehicleStateRepository(app).also { it.start() }
        car = AaosCarMonitor(app).also {
            it.onPropertyManager = vehicle::attachAaos
            it.start()
        }
        gate = car.state
            .map(ParkingGate::evaluate)
            .distinctUntilChanged()
            .onEach { AppLog.i("Gate", "gate → $it") }
            .stateIn(scope, SharingStarted.Eagerly, GateState.Unknown("시작 중"))
        // 첫 모델 로드가 0.4~0.9초 걸려 미리 초기화 (CLAUDE.md §4 TTS)
        speech = SpeechGuide(app)
        val store = ProgressStore(app)
        tutorial = TutorialController(vehicle, gate, speech, store)
        opening = OpeningDirector(app, vehicle, gate, tutorial, speech, store)
    }
}
