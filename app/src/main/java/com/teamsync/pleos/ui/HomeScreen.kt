package com.teamsync.pleos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamsync.pleos.car.AaosState
import com.teamsync.pleos.car.GateState
import com.teamsync.pleos.log.AppLog
import com.teamsync.pleos.vehicle.Support
import com.teamsync.pleos.vehicle.VehicleConnection
import com.teamsync.pleos.vehicle.VehicleSignal
import com.teamsync.pleos.vehicle.VehicleState

/**
 * Phase 1 홈: 정차 게이트와 이 차에서 확인 가능한 기능 목록만 보여준다 (튜토리얼은 Phase 2).
 * 제목을 3초 안에 5번 누르면 개발자 패널.
 */
@Composable
fun HomeScreen(gate: GateState, car: AaosState, vehicle: VehicleState, onOpenDevPanel: () -> Unit) {
    val taps = remember { ArrayDeque<Long>() }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            "내 차 튜토리얼",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                val now = System.currentTimeMillis()
                taps.addLast(now)
                while (taps.isNotEmpty() && now - taps.first() > SECRET_WINDOW_MS) taps.removeFirst()
                if (taps.size >= SECRET_TAPS) {
                    taps.clear()
                    AppLog.i("UI", "developer panel opened")
                    onOpenDevPanel()
                }
            },
        )

        GateCard(gate, car)

        if (vehicle.connection is VehicleConnection.Failed) {
            Card(null, Modifier.fillMaxWidth()) {
                Text("차량 연결 안 됨", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Fail)
                Text((vehicle.connection as VehicleConnection.Failed).reason, fontSize = 22.sp, color = Body)
            }
        }

        Card("이 차에서 확인할 수 있는 기능", Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(12.dp))
            VehicleSignal.entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    pair.forEach { s -> SupportItem(s.label, vehicle[s].support, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GateCard(gate: GateState, car: AaosState) {
    Card(null, Modifier.fillMaxWidth()) {
        val (headline, color, detail) = when (gate) {
            GateState.Parked -> Triple("정차 확인됨 — 튜토리얼을 진행할 수 있어요", Ok, "기어 P")
            is GateState.NotParked -> Triple("차를 세우고 기어를 P로 바꿔 주세요", Warn, gate.reason)
            is GateState.Unknown -> Triple("차량 상태를 확인하고 있어요", Dim, gate.reason)
        }
        Text(headline, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.height(8.dp))
        Text(detail, fontSize = 24.sp, color = Body)
        val brake = car.parkingBrake
        val brakeText = when {
            brake.value == true -> "채워짐"
            brake.value == false -> "풀림"
            brake.error != null -> "확인 불가 (${brake.error})"
            else -> "확인 중"
        }
        Text("주차 브레이크: $brakeText", fontSize = 24.sp, color = Body)
    }
}

@Composable
private fun SupportItem(label: String, support: Support, modifier: Modifier) {
    val (text, color) = when (support) {
        Support.Checking -> "확인 중" to Dim
        is Support.Supported -> "지원" to Ok
        is Support.Unsupported -> "지원 안 됨" to Fail
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, fontSize = 26.sp, color = Color.White)
        Text(text, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

private const val SECRET_TAPS = 5
private const val SECRET_WINDOW_MS = 3_000L
