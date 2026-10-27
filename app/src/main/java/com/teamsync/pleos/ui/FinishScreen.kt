package com.teamsync.pleos.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamsync.pleos.tutorial.Outcome
import com.teamsync.pleos.tutorial.TutorialState
import com.teamsync.pleos.tutorial.TutorialStep

/** 완료 화면: 완료·건너뜀·미지원 개수, 단계별 결과와 다시 해보기, 배지. */
@Composable
fun FinishScreen(
    state: TutorialState,
    steps: List<TutorialStep>,
    isMaster: Boolean,
    canStart: Boolean,
    onRetry: (String) -> Unit,
    onRestart: () -> Unit,
    onHome: () -> Unit,
) {
    val counts = Outcome.entries.associateWith { o -> state.results.values.count { it.outcome == o } }
    LazyColumn(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("튜토리얼 결과", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                BigButton("홈으로", Color(0xFF374151), onHome)
            }
        }
        if (isMaster) item {
            Surface(color = Color(0xFF1E3A2B), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(28.dp)) {
                    Text("내 차 마스터", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Ok)
                    Text("이 차에서 확인할 수 있는 기능을 모두 직접 조작했어요", fontSize = 26.sp, color = Body)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CountCard("완료", counts[Outcome.COMPLETED] ?: 0, Ok, Modifier.weight(1f))
                CountCard("건너뜀", counts[Outcome.SKIPPED] ?: 0, Warn, Modifier.weight(1f))
                CountCard("지원 안 됨", counts[Outcome.UNSUPPORTED] ?: 0, Dim, Modifier.weight(1f))
            }
        }
        if (!canStart) item { Text("다시 해보려면 정차 후 기어를 P로 바꿔 주세요", fontSize = 24.sp, color = Warn) }
        items(steps) { step ->
            val result = state.results[step.id]
            Card(null, Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(step.title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.width(20.dp))
                            Text(
                                result?.outcome?.label ?: "아직 안 함",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (result?.outcome) {
                                    Outcome.COMPLETED -> Ok
                                    Outcome.SKIPPED -> Warn
                                    else -> Dim
                                },
                            )
                        }
                        result?.detail?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 20.sp, color = Dim) }
                    }
                    if (result?.outcome != Outcome.UNSUPPORTED) {
                        BigButton("다시 해보기", Color(0xFF2563EB), { onRetry(step.id) }, enabled = canStart)
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            BigButton("처음부터 다시", Color(0xFF4B5563), onRestart, Modifier.fillMaxWidth(), enabled = canStart)
        }
    }
}

@Composable
private fun CountCard(label: String, count: Int, color: Color, modifier: Modifier) {
    Card(null, modifier) {
        Text("$count", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 26.sp, color = Body)
    }
}
