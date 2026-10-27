package com.teamsync.pleos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamsync.pleos.tutorial.Mode
import com.teamsync.pleos.tutorial.TutorialState
import com.teamsync.pleos.tutorial.TutorialStep
import com.teamsync.pleos.voice.SpeechGuide

/** 진행 중 화면: 큰 안내 한 줄 + 지금 상태 + 건너뛰기/다시 듣기. 완료는 차량 상태 변화로만 넘어간다 (완료 버튼 없음). */
@Composable
fun TutorialScreen(
    state: TutorialState,
    steps: List<TutorialStep>,
    courseTitle: String?,
    speech: SpeechGuide.Status,
    onSkip: () -> Unit,
    onRepeat: () -> Unit,
    onQuit: () -> Unit,
) {
    val step = steps.getOrNull(state.stepIndex)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(
                        if (state.singleStep) null else "${(state.coursePos + 1).coerceAtMost(state.courseSize)} / ${state.courseSize}",
                        courseTitle,
                        step?.title,
                    ).joinToString("  ·  "),
                    fontSize = 30.sp, color = Dim, modifier = Modifier.weight(1f),
                )
                if (state.singleStep) Text("다시 해보기", fontSize = 24.sp, color = Warn)
                Spacer(Modifier.width(24.dp))
                BigButton("그만하기", Color(0xFF374151), onQuit)
            }
            state.lastCompleted?.let { Text("✓ $it", fontSize = 28.sp, color = Ok, fontWeight = FontWeight.Bold) }

            Card(null, Modifier.fillMaxWidth().weight(1f)) {
                if (step != null && step.phases.size > 1) PhaseDots(step.phases.size, state.phaseIndex)
                Spacer(Modifier.height(16.dp))
                Text(
                    state.instruction ?: state.waiting ?: "",
                    fontSize = if (state.instruction != null) 48.sp else 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.instruction != null) Color.White else Dim,
                    lineHeight = 60.sp,
                )
                Spacer(Modifier.height(24.dp))
                if (step != null && state.instruction != null) {
                    Text("지금 상태: ${step.valueLabel(state.currentValue)}", fontSize = 32.sp, color = Body)
                }
                state.hint?.let {
                    Spacer(Modifier.height(24.dp))
                    Surface(color = Color(0xFF3B2F0B), shape = RoundedCornerShape(12.dp)) {
                        Text("힌트  $it", fontSize = 28.sp, color = Warn, modifier = Modifier.padding(20.dp), lineHeight = 38.sp)
                    }
                }
                if (state.evidence.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("확인됨: ${state.evidence.joinToString(", ")}", fontSize = 22.sp, color = Ok)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                BigButton("다시 듣기", Color(0xFF2563EB), onRepeat, Modifier.weight(1f))
                BigButton("건너뛰기", Color(0xFF4B5563), onSkip, Modifier.weight(1f))
            }
            if (speech is SpeechGuide.Status.Failed) Text("음성 안내 사용 불가 — ${speech.reason}", fontSize = 20.sp, color = Fail)
        }

        val mode = state.mode
        if (mode is Mode.Paused) PausedOverlay(mode.reason)
    }
}

@Composable
private fun PhaseDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(18.dp)
                    .background(if (i < current) Ok else if (i == current) Color.White else Color(0xFF374151), CircleShape),
            )
        }
    }
}

@Composable
private fun PausedOverlay(reason: String) {
    Box(Modifier.fillMaxSize().background(Color(0xE6000000)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("잠시 멈췄어요", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Warn)
            Spacer(Modifier.height(16.dp))
            Text("기어를 P로 바꾸면 이어서 할게요", fontSize = 32.sp, color = Color.White)
            Text(reason, fontSize = 24.sp, color = Dim)
        }
    }
}

@Composable
fun BigButton(text: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(16.dp),
        // contentColor를 지정하지 않으면 darkColorScheme의 onPrimary(어두운 보라)가 쓰여 파란 배경에서 안 읽힘 (10-27 화면 확인)
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF1F2937),
            disabledContentColor = Dim,
        ),
    ) {
        Text(text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
}
