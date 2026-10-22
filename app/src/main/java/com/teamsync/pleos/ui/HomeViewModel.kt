package com.teamsync.pleos.ui

import ai.pleos.playground.navi.constants.WaypointIndex
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teamsync.pleos.core.CarSession
import com.teamsync.pleos.core.OnDeviceLlm
import com.teamsync.pleos.core.Place
import com.teamsync.pleos.core.SpeechGuide
import com.teamsync.pleos.log.AppLog
import com.teamsync.pleos.service.MonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val _lastAction = MutableStateFlow("")
    /** 개발자 도구 버튼의 마지막 결과 (성공/실패 사유). */
    val lastAction: StateFlow<String> = _lastAction.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private var speech: SpeechGuide? = null

    /** 진단: 표준 TextToSpeech로 한국어 음성 안내가 나오는지. */
    fun testSpeech() {
        val guide = speech ?: SpeechGuide(getApplication()).also { speech = it }
        _lastAction.value = "TTS 테스트 요청 (결과는 PLEOS/Speech 로그)"
        guide.speak("비상등을 켜고 트렁크를 연 뒤, 가드레일 밖으로 대피하세요.")
    }

    private var llm: OnDeviceLlm? = null

    /** 진단: 온디바이스 LLM으로 2차 사고 신고 문구 생성 (D안 시나리오). */
    fun testLlm() = navi("온디바이스 LLM") {
        val model = llm ?: OnDeviceLlm(getApplication()).also { llm = it }
        model.generate(
            "다음 정보로 고속도로 사고 신고 전화에서 읽을 한국어 한두 문장을 만들어줘. " +
                "위치: 경기 성남시 수정구 시흥동 부근, 진행 방향: 판교IC 방면, 상황: 고장으로 갓길 정차, " +
                "탑승자: 어른 2명·아이 1명·반려견 1마리, 모두 가드레일 밖으로 대피 완료.",
        )
    }

    override fun onCleared() {
        speech?.shutdown()
        llm?.release()
    }

    fun startMonitor() = MonitorService.start(getApplication())

    fun stopMonitor() = MonitorService.stop(getApplication())

    fun testRoute() = navi("경로 요청 ${TestPlaces.PANGYO.name}") {
        CarSession.navi.requestRoute(TestPlaces.PANGYO).map { "${it.name} ${it.distance}m ${it.duration}s" }
    }

    fun addWaypoint(place: Place) = navi("경유지 추가 ${place.name}") {
        CarSession.navi.addWaypoint(place).map { list -> list.joinToString { it.name }.ifEmpty { "(없음)" } }
    }

    fun removeWaypoint(index: WaypointIndex) = navi("경유지 제거 $index") {
        CarSession.navi.removeWaypoint(index).map { list -> list.joinToString { it.name }.ifEmpty { "(없음)" } }
    }

    fun cancelRoute() = navi("경로 취소") { CarSession.navi.cancelRoute().map { "취소됨" } }

    fun estimateTrip(place: Place) = navi("도착 예상 ${place.name}") {
        CarSession.navi.estimateTrip(place).map { t ->
            val charging = t.summary.routePoints.count { it.poiType?.name == "EV_CHARGING" }
            "${t.distance}m · eta ${t.eta} · 도착 SOC ${t.estimatedSocAtDest}% · ${t.energyConsumption} · 충전 경유 ${charging}곳"
        }
    }

    private fun navi(action: String, block: suspend () -> Result<String>) {
        if (_busy.value) return
        _busy.value = true
        _lastAction.value = "$action …"
        AppLog.i("UI", action)
        viewModelScope.launch {
            val result = block()
            _lastAction.value = result.fold({ "$action ✓ $it" }, { "$action ✗ ${it.message}" })
            _busy.value = false
        }
    }
}

/** 에뮬레이터 기본 위치(성남 수정구 시흥동) 근처 테스트 지점. 좌표는 근사값. */
object TestPlaces {
    val PANGYO = Place("판교역", 37.394776, 127.111158)
    val JEONGJA = Place("정자역", 37.366950, 127.108380)
    val SEOHYEON = Place("서현역", 37.385050, 127.123400)
    val GANGNEUNG = Place("강릉역", 37.763700, 128.899900)
}
