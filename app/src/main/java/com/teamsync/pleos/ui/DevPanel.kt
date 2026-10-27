package com.teamsync.pleos.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamsync.pleos.car.AaosCarMonitor
import com.teamsync.pleos.car.AaosState
import com.teamsync.pleos.car.GateState
import com.teamsync.pleos.car.gearName
import com.teamsync.pleos.log.AppLog
import com.teamsync.pleos.vehicle.Support
import com.teamsync.pleos.vehicle.VehicleConnection
import com.teamsync.pleos.vehicle.VehicleSignal
import com.teamsync.pleos.vehicle.VehicleState

/**
 * 숨김 개발자 패널: 구독 중인 속성의 실시간 원시 값, capability 결과, 퍼미션, 최근 오류 50개.
 * 화면에 보이는 값은 전부 SDK/Car 서비스에서 받은 그대로다 (받지 못했으면 "-").
 */
@Composable
fun DevPanel(
    gate: GateState,
    car: AaosState,
    vehicle: VehicleState,
    errors: List<AppLog.Entry>,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val permissions = remember { permissionStatus(context) }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth()) {
                Text("개발자 패널", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                Button(onClick = onClose, modifier = Modifier.height(72.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))) {
                    Text("닫기", fontSize = 24.sp)
                }
            }
        }
        item {
            Card("연결 · 정차 게이트", Modifier.fillMaxWidth()) {
                Mono("Vehicle SDK: ${connectionText(vehicle.connection)}")
                Mono("AAOS Car: connected=${car.connected}${car.error?.let { " error=$it" } ?: ""}")
                Mono("gate: $gate")
                Mono("GEAR_SELECTION: raw=${car.gear.value ?: "-"} (${gearName(car.gear.value)}) at ${clock(car.gear.updatedAt)}${car.gear.error?.let { " error=$it" } ?: ""}")
                Mono("PARKING_BRAKE_ON: ${car.parkingBrake.value ?: "-"} at ${clock(car.parkingBrake.updatedAt)}${car.parkingBrake.error?.let { " error=$it" } ?: ""}")
                val bits = car.activeRestrictions
                Mono("UX: requiresDO=${car.requiresDistractionOptimization ?: "-"} active=${bits?.let { "0x${it.toString(16)} [${AaosCarMonitor.describeRestrictions(it)}]" } ?: "-"}")
            }
        }
        item { Text("Vehicle SDK 신호", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        items(VehicleSignal.entries) { s -> SignalRow(s, vehicle) }
        item {
            Card("퍼미션 (매니페스트 선언 → 부여 여부)", Modifier.fillMaxWidth()) {
                permissions.forEach { (name, granted) -> Mono("${if (granted) "✓" else "✗"} $name", if (granted) Body else Fail) }
            }
        }
        item { Text("최근 오류·경고 ${errors.size}개 (최신순)", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        if (errors.isEmpty()) item { Mono("없음", Dim) }
        items(errors.asReversed()) { e ->
            Mono("${clock(e.time)} ${e.level}/${e.tag} ${e.message}", if (e.level == 'E') Fail else Warn)
        }
    }
}

@Composable
private fun SignalRow(s: VehicleSignal, vehicle: VehicleState) {
    val st = vehicle[s]
    Card(null, Modifier.fillMaxWidth()) {
        Row {
            Text(s.label, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(260.dp))
            Text(st.value?.toString() ?: "-", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (st.value == null) Dim else Ok)
            Spacer(Modifier.width(24.dp))
            Text("${st.source ?: ""} 변경 ${clock(st.changedAt)} · 수신 ${clock(st.receivedAt)} ×${st.receiveCount}", fontSize = 18.sp, color = Dim)
        }
        Mono(s.api, Dim)
        when (val sup = st.support) {
            Support.Checking -> Mono("capability: 확인 중", Dim)
            is Support.Supported -> Mono("capability: ${sup.detail}")
            is Support.Unsupported -> Mono("지원 안 됨: ${sup.reason}", Fail)
        }
        st.lastError?.let { Mono("최근 오류: $it", Fail) }
    }
}

@Composable
private fun Mono(text: String, color: Color = Body) {
    Text(text, fontSize = 18.sp, fontFamily = FontFamily.Monospace, color = color, modifier = Modifier.padding(top = 4.dp))
}

private fun connectionText(c: VehicleConnection) = when (c) {
    VehicleConnection.NotStarted -> "시작 전"
    VehicleConnection.Connected -> "initialize 완료"
    is VehicleConnection.Failed -> "실패 — ${c.reason}"
}

private fun permissionStatus(context: Context): List<Pair<String, Boolean>> {
    val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
    return info.requestedPermissions.orEmpty().map { it to (context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED) }
}
