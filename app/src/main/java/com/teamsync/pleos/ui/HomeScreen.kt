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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamsync.pleos.car.AaosState
import com.teamsync.pleos.car.GateState
import com.teamsync.pleos.log.AppLog
import com.teamsync.pleos.tutorial.Course
import com.teamsync.pleos.tutorial.TutorialState
import com.teamsync.pleos.tutorial.TutorialStep
import com.teamsync.pleos.vehicle.Support
import com.teamsync.pleos.vehicle.VehicleConnection
import com.teamsync.pleos.vehicle.VehicleState

/**
 * 홈: 정차 게이트 + 상황별 코스 카드. 코스마다 진행도, 이 차가 지원하지 않는 단계 수, 시작/이어서/다시 하기.
 * 제목을 3초 안에 5번 누르면 개발자 패널.
 */
@Composable
fun HomeScreen(
    gate: GateState,
    car: AaosState,
    vehicle: VehicleState,
    tutorial: TutorialState,
    courses: List<Course>,
    everything: Course,
    steps: List<TutorialStep>,
    isCourseComplete: (String) -> Boolean,
    isMaster: Boolean,
    onStart: (courseId: String, fromBeginning: Boolean) -> Unit,
    onShowResults: (courseId: String) -> Unit,
    onOpenDevPanel: () -> Unit,
) {
    val taps = remember { ArrayDeque<Long>() }
    val canStart = gate is GateState.Parked && vehicle.connection is VehicleConnection.Connected
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            Spacer(Modifier.weight(1f))
            if (isMaster) {
                Surface(color = Color(0xFF1E3A2B), shape = RoundedCornerShape(12.dp)) {
                    Text("내 차 마스터", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ok, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                }
            }
        }

        GateCard(gate, car)

        if (vehicle.connection is VehicleConnection.Failed) {
            Card(null, Modifier.fillMaxWidth()) {
                Text("차량 연결 안 됨", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Fail)
                Text((vehicle.connection as VehicleConnection.Failed).reason, fontSize = 22.sp, color = Body)
            }
        }

        Text("어떤 걸 익혀 볼까요?", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        (courses + everything).chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                pair.forEach { course ->
                    CourseCard(course, steps, vehicle, tutorial, isCourseComplete(course.id), canStart, onStart, onShowResults, Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CourseCard(
    course: Course,
    steps: List<TutorialStep>,
    vehicle: VehicleState,
    tutorial: TutorialState,
    complete: Boolean,
    canStart: Boolean,
    onStart: (String, Boolean) -> Unit,
    onShowResults: (String) -> Unit,
    modifier: Modifier,
) {
    val courseSteps = course.stepIds.mapNotNull { id -> steps.firstOrNull { it.id == id } }
    val resolved = course.stepIds.count { it in tutorial.results }
    val unsupported = courseSteps.count { vehicle[it.signal].support is Support.Unsupported }
    Card(null, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(course.title, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.weight(1f))
            if (complete) Text("✓ 완료", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ok)
        }
        Text(course.subtitle, fontSize = 24.sp, color = Body)
        Spacer(Modifier.height(8.dp))
        Text(
            "${courseSteps.size}단계 · 진행 $resolved/${courseSteps.size}" + if (unsupported > 0) " · 이 차 미지원 $unsupported" else "",
            fontSize = 22.sp,
            color = Dim,
        )
        Text(courseSteps.joinToString(" · ") { it.title }, fontSize = 20.sp, color = Dim, maxLines = 2)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            when {
                resolved == 0 -> BigButton("시작", Ok.copy(alpha = 0.85f), { onStart(course.id, true) }, Modifier.weight(1f), enabled = canStart)
                resolved < courseSteps.size -> BigButton("이어서", Ok.copy(alpha = 0.85f), { onStart(course.id, false) }, Modifier.weight(1f), enabled = canStart)
                else -> BigButton("다시 하기", Color(0xFF4B5563), { onStart(course.id, true) }, Modifier.weight(1f), enabled = canStart)
            }
            if (resolved > 0) BigButton("결과", Color(0xFF2563EB), { onShowResults(course.id) }, Modifier.weight(1f))
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
        val brake = car.parkingBrake
        val brakeText = when {
            brake.value == true -> "채워짐"
            brake.value == false -> "풀림"
            brake.error != null -> "확인 불가 (${brake.error})"
            else -> "확인 중"
        }
        Text("$detail · 주차 브레이크: $brakeText", fontSize = 24.sp, color = Body)
    }
}

private const val SECRET_TAPS = 5
private const val SECRET_WINDOW_MS = 3_000L
