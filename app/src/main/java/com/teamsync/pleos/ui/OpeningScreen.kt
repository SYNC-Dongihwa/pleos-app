package com.teamsync.pleos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamsync.pleos.car.GateState

/** "새 차 첫날" 오프닝: 환영 문구 + 시작하기. 정차(기어 P)가 아니면 시작 버튼을 막고 이유를 보여준다. */
@Composable
fun OpeningScreen(gate: GateState, canStart: Boolean, onStart: () -> Unit, onLater: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("새 차에 오신 걸 환영해요", fontSize = 60.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(24.dp))
        Text("처음 타는 차의 기능을 하나씩 직접 조작해 보면서 익혀 볼게요", fontSize = 32.sp, color = Body)
        Spacer(Modifier.height(12.dp))
        Text("조작하면 차가 실제로 바뀌었는지 확인해 드려요", fontSize = 28.sp, color = Dim)
        Spacer(Modifier.height(48.dp))
        if (!canStart) {
            val reason = when (gate) {
                is GateState.NotParked -> gate.reason
                is GateState.Unknown -> gate.reason
                GateState.Parked -> "차량 연결 대기 중"
            }
            Text("차를 세우고 기어를 P로 바꾸면 시작할 수 있어요 ($reason)", fontSize = 26.sp, color = Warn)
            Spacer(Modifier.height(24.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            BigButton("시작하기", Ok.copy(alpha = 0.85f), onStart, Modifier.padding(horizontal = 8.dp), enabled = canStart)
            BigButton("나중에", Color(0xFF4B5563), onLater)
        }
    }
}
