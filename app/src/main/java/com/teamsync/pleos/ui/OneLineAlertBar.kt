package com.teamsync.pleos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamsync.pleos.core.UxState
import com.teamsync.pleos.core.alert.AlertSeverity
import com.teamsync.pleos.core.alert.DrivingAlert

/**
 * 주행 중 1줄 알림. 고대비·큰 글자·한 줄·애니메이션 없음 (운전 분산 가이드).
 * LIMIT_STRING_LENGTH가 걸리면 UxState.limit로 잘라서 보여준다. 탭하면 닫힘(큰 터치 영역).
 */
@Composable
fun OneLineAlertBar(alert: DrivingAlert?, ux: UxState, driving: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val height = if (driving) 128.dp else 88.dp
    if (alert == null) {
        Box(modifier.fillMaxWidth().height(height))
        return
    }
    val (background, foreground) = when (alert.severity) {
        AlertSeverity.CRITICAL -> Color(0xFFDC2626) to Color.White
        AlertSeverity.WARN -> Color(0xFFFACC15) to Color.Black
        AlertSeverity.INFO -> Color(0xFF1F2937) to Color.White
    }
    Surface(
        color = background,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clickable(onClick = onDismiss),
    ) {
        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 32.dp)) {
            Text(
                ux.limit(alert.text),
                color = foreground,
                fontSize = if (driving) 44.sp else 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
