package com.teamsync.pleos.ui

import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teamsync.pleos.BuildConfig
import com.teamsync.pleos.core.CarSession
import com.teamsync.pleos.core.NaviState
import com.teamsync.pleos.core.UxRestrictionsMonitor
import com.teamsync.pleos.core.UxState
import com.teamsync.pleos.core.VehicleSnapshot
import com.teamsync.pleos.core.alert.AlertCenter
import com.teamsync.pleos.service.MonitorService

private val Bg = Color(0xFF0E1116)
private val Card = Color(0xFF181C23)
private val Dim = Color(0xFF8A93A3)
private val Ok = Color(0xFF4ADE80)
private val Fail = Color(0xFFF87171)

@Composable
fun HomeScreen(vm: HomeViewModel, onOpenProbe: () -> Unit) {
    val ux by CarSession.ux.state.collectAsStateWithLifecycle()
    val vehicle by CarSession.vehicle.state.collectAsStateWithLifecycle()
    val navi by CarSession.navi.state.collectAsStateWithLifecycle()
    val alert by AlertCenter.current.collectAsStateWithLifecycle()
    val monitoring by MonitorService.running.collectAsStateWithLifecycle()
    val lastAction by vm.lastAction.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Bg)
                .padding(24.dp),
        ) {
            OneLineAlertBar(alert, ux, driving = ux.restricted, onDismiss = AlertCenter::dismiss)
            Spacer(Modifier.height(24.dp))
            if (ux.restricted) {
                DrivingLayout(ux, vehicle, navi, monitoring)
            } else {
                ParkedLayout(ux, vehicle, navi, monitoring, lastAction, busy, vm, onOpenProbe)
            }
        }
    }
}

/** 주행 중(requiresDO) 또는 주행 상태를 모를 때: 큰 글자 3개 + 목적지 1줄, 버튼 없음. */
@Composable
private fun ColumnScope.DrivingLayout(ux: UxState, vehicle: VehicleSnapshot, navi: NaviState, monitoring: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(80.dp), verticalAlignment = Alignment.Bottom) {
            BigValue(gearLabel(vehicle.gear), "기어")
            BigValue(vehicle.socPercent?.let { "$it%" } ?: "--", "배터리")
            BigValue(vehicle.rangeMeters?.let { "${(it / 1000).toInt()}" } ?: "--", "주행가능 km")
        }
        navi.destination?.takeIf { navi.routeActive && it.name.isNotEmpty() }?.let { d ->
            Spacer(Modifier.height(48.dp))
            Text(
                ux.limit("${d.name} · ${"%.1f".format(d.distance / 1000f)}km · ${d.duration / 60}분"),
                fontSize = 40.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!monitoring) {
            Spacer(Modifier.height(48.dp))
            Text("모니터링 꺼짐 — 정차 후 시작하세요", fontSize = 28.sp, color = Dim)
        }
        if (!ux.connected) {
            Spacer(Modifier.height(24.dp))
            Text("주행 상태 확인 불가 — 제한 화면으로 표시 중 (${ux.error ?: "연결 대기"})", fontSize = 22.sp, color = Dim)
        }
    }
}

@Composable
private fun BigValue(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 120.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = 28.sp, color = Dim)
    }
}

/** 정차 중: 상태 상세 + 개발자 도구. */
@Composable
private fun ParkedLayout(
    ux: UxState,
    vehicle: VehicleSnapshot,
    navi: NaviState,
    monitoring: Boolean,
    lastAction: String,
    busy: Boolean,
    vm: HomeViewModel,
    onOpenProbe: () -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoCard("모니터링 서비스", Modifier.weight(1f)) {
                Line(if (monitoring) "실행 중 (Foreground Service)" else "중지됨", if (monitoring) Ok else Fail)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton("시작", enabled = !monitoring) { vm.startMonitor() }
                    ActionButton("중지", enabled = monitoring) { vm.stopMonitor() }
                }
            }
            InfoCard("주행 규제 (CarUxRestrictions)", Modifier.weight(1f)) {
                Line(if (ux.connected) "연결됨" else "미연결 ${ux.error.orEmpty()}", if (ux.connected) Ok else Fail)
                Line("requiresDO=${ux.requiresDistractionOptimization}")
                Line("active=0x${ux.activeRestrictions.toString(16)} ${UxRestrictionsMonitor.describe(ux.activeRestrictions)}")
                Line("maxString=${ux.maxStringLength} maxItems=${ux.maxCumulativeContentItems}")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoCard("차량", Modifier.weight(1f)) {
                if (vehicle.error != null) Line(vehicle.error, Fail)
                Line("기어 ${gearLabel(vehicle.gear)} · 배터리 ${vehicle.socPercent ?: "--"}% · 주행가능 ${vehicle.rangeMeters?.let { "${(it / 1000).toInt()}km" } ?: "--"}")
                Line("충전 포트 ${when (vehicle.chargePortOpen) { true -> "열림"; false -> "닫힘"; null -> "--" }}")
                Line("경고등 ${vehicle.activeTelltales.joinToString { "${it.type}:${it.state}" }.ifEmpty { "없음" }}")
                vehicle.unavailable.forEach { (signal, reason) -> Line("✗ $signal: $reason", Fail) }
            }
            InfoCard("내비 (NaviHelper)", Modifier.weight(1f)) {
                if (navi.error != null) Line(navi.error, Fail)
                Line("ready=${navi.ready} status=${navi.status ?: "-"} mode=${navi.naviMode ?: "-"}")
                navi.destination?.takeIf { it.name.isNotEmpty() }?.let { d ->
                    Line("목적지 ${d.name} · ${d.distance}m · ${d.duration}s · 도착 잔량 ${d.remainDestinationChargingValue}")
                }
                Line("경유지 ${navi.waypoints.joinToString { "${it.name}(${it.remainDistance}m)" }.ifEmpty { "없음" }}")
                navi.waypointStates.filter { it.isChargingStation }.forEach { Line("⚡ ${it.name} · ${it.maxChargingPower}kW × ${it.chargerCount}") }
            }
        }
        // 심사 기준: 테스트용 더미 데이터·임시 기능 금지 → 개발자 도구(테스트 지점 포함)는 debug 빌드에서만
        if (BuildConfig.DEBUG) InfoCard("개발자 도구", Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("SDK Probe 열기") { onOpenProbe() }
                ActionButton("TTS 테스트") { vm.testSpeech() }
                ActionButton("LLM 테스트", enabled = !busy) { vm.testLlm() }
                ActionButton("경로: ${TestPlaces.PANGYO.name}", enabled = !busy) { vm.testRoute() }
                ActionButton("경유지 +${TestPlaces.JEONGJA.name}", enabled = !busy) { vm.addWaypoint(TestPlaces.JEONGJA) }
                ActionButton("경유지 +${TestPlaces.SEOHYEON.name}", enabled = !busy) { vm.addWaypoint(TestPlaces.SEOHYEON) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("첫 경유지 제거", enabled = !busy) { vm.removeWaypoint(WaypointIndex.FIRST) }
                ActionButton("경유지 모두 제거", enabled = !busy) { vm.removeWaypoint(WaypointIndex.ALL) }
                ActionButton("경로 취소", enabled = !busy) { vm.cancelRoute() }
                ActionButton("도착 예상: ${TestPlaces.PANGYO.name}", enabled = !busy) { vm.estimateTrip(TestPlaces.PANGYO) }
                ActionButton("도착 예상: ${TestPlaces.GANGNEUNG.name}", enabled = !busy) { vm.estimateTrip(TestPlaces.GANGNEUNG) }
            }
            if (lastAction.isNotEmpty()) Line(lastAction)
            Line("※ 경로/경유지 요청 시 Navigation 앱이 전면으로 전환됩니다", Dim)
        }
    }
}

@Composable
private fun InfoCard(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Card, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            content()
        }
    }
}

@Composable
private fun Line(text: String, color: Color = Color(0xFFD1D5DB)) {
    Text(text, fontSize = 19.sp, color = color)
}

@Composable
private fun ActionButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
    ) {
        Text(text, fontSize = 20.sp)
    }
}

private fun gearLabel(gear: VehicleGear?): String = when (gear) {
    null -> "--"
    VehicleGear.GEAR_PARK -> "P"
    VehicleGear.GEAR_REVERSE -> "R"
    VehicleGear.GEAR_NEUTRAL -> "N"
    VehicleGear.GEAR_DRIVE -> "D"
    else -> gear.name.removePrefix("GEAR_")
}
