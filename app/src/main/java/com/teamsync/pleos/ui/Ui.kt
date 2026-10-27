package com.teamsync.pleos.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 차량 화면: 고대비 어두운 배경, 큰 글씨 (CLAUDE.md §1 차량 UX)
val Bg = Color(0xFF0E1116)
val CardBg = Color(0xFF181C23)
val Dim = Color(0xFF8A93A3)
val Body = Color(0xFFD1D5DB)
val Ok = Color(0xFF4ADE80)
val Warn = Color(0xFFFBBF24)
val Fail = Color(0xFFF87171)

@Composable
fun Card(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = CardBg, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(24.dp)) {
            if (title != null) Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            content()
        }
    }
}

fun clock(time: Long): String = if (time == 0L) "-" else SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(time))
