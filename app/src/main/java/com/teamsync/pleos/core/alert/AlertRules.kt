package com.teamsync.pleos.core.alert

import com.teamsync.pleos.core.CarSession
import com.teamsync.pleos.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 실제 차량/내비 신호 → 1줄 알림. 아이디어별 문구·심각도 정책은 확정 후 교체할 기본 규칙만 둔다.
 * - 경고등이 새로 켜지거나 깜빡이기 시작함 (PRIVATE_CAR_SAFETY가 있을 때만 데이터가 옴)
 * - 배터리 20% / 10% 아래로 내려감 (5%p 히스테리시스)
 * - NaviHelper onError
 */
class AlertRules(private val scope: CoroutineScope) {

    private var lastActive: Map<String, String> = emptyMap()
    private val socArmed = mutableMapOf(SOC_WARN to true, SOC_CRITICAL to true)

    fun start() {
        scope.launch {
            CarSession.vehicle.state.map { s -> s.activeTelltales.associate { "${it.type}" to "${it.state}" } }
                .distinctUntilChanged()
                .collect(::onTelltales)
        }
        scope.launch {
            CarSession.vehicle.state.map { it.socPercent to it.rangeMeters }
                .distinctUntilChanged { a, b -> a.first == b.first }
                .collect { (soc, range) -> onSoc(soc, range) }
        }
        scope.launch {
            CarSession.navi.errors.collect { code ->
                AlertCenter.post(DrivingAlert("navi", AlertSeverity.INFO, "내비 요청 실패 · ${code.name}"))
            }
        }
        AppLog.i(TAG, "started")
    }

    private fun onTelltales(active: Map<String, String>) {
        // 새로 켜졌거나 ON→BLINK로 바뀐 것만
        val changed = active.filter { (type, state) -> lastActive[type] != state }
        lastActive = active
        if (changed.isEmpty()) return
        val names = changed.entries.joinToString(", ") { (type, state) -> if (state == "BLINK") "$type(깜빡임)" else type }
        val text = if (changed.size == 1) "경고등 켜짐 · $names" else "경고등 ${changed.size}개 · $names"
        AlertCenter.post(DrivingAlert("telltale", AlertSeverity.WARN, text))
    }

    private fun onSoc(soc: Int?, rangeMeters: Float?) {
        if (soc == null) return
        // 충분히 회복하면 다시 알릴 수 있게
        socArmed.keys.forEach { threshold -> if (soc >= threshold + SOC_HYSTERESIS) socArmed[threshold] = true }
        val threshold = listOf(SOC_CRITICAL, SOC_WARN).firstOrNull { soc < it && socArmed[it] == true } ?: return
        // 낮은 임계값에 걸리면 높은 임계값도 소진 처리
        socArmed.keys.filter { it >= threshold }.forEach { socArmed[it] = false }
        val range = rangeMeters?.let { " · 주행가능 ${(it / 1000).toInt()}km" }.orEmpty()
        val severity = if (threshold == SOC_CRITICAL) AlertSeverity.CRITICAL else AlertSeverity.WARN
        AlertCenter.post(DrivingAlert("soc", severity, "배터리 $soc%$range"))
    }

    companion object {
        private const val TAG = "Alert"
        private const val SOC_WARN = 20
        private const val SOC_CRITICAL = 10
        private const val SOC_HYSTERESIS = 5
    }
}
