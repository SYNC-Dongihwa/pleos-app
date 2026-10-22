package com.teamsync.pleos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teamsync.pleos.log.AppLog
import com.teamsync.pleos.probe.NaviProbe
import com.teamsync.pleos.probe.PermissionReport
import com.teamsync.pleos.probe.PermissionState
import com.teamsync.pleos.probe.ProbeKind
import com.teamsync.pleos.probe.ProbeRow
import com.teamsync.pleos.probe.ProbeStatus
import com.teamsync.pleos.probe.ProbeViewModel
import kotlinx.coroutines.delay

private val Bg = Color(0xFF0E1116)
private val Card = Color(0xFF181C23)
private val CardRecent = Color(0xFF1F3A2A)
private val Dim = Color(0xFF8A93A3)
private val Ok = Color(0xFF4ADE80)
private val Fail = Color(0xFFF87171)
private val Warn = Color(0xFFFBBF24)
private val Info = Color(0xFF60A5FA)

private const val RECENT_MS = 3_000L

private enum class Filter(val label: String, val accept: (ProbeRow) -> Boolean) {
    ALL("전체", { true }),
    OK("OK", { it.status == ProbeStatus.OK || it.status == ProbeStatus.LISTENING }),
    FAILED("실패/타임아웃", { it.status == ProbeStatus.FAILED || it.status == ProbeStatus.TIMEOUT }),
    LIVE("실시간 수신", { (it.kind == ProbeKind.LISTENER || it.kind == ProbeKind.NAVI_EVENT) && it.updates > 0 }),
    SKIPPED("건너뜀", { it.status == ProbeStatus.SKIPPED }),
}

@Composable
fun ProbeScreen(vm: ProbeViewModel) {
    val vehicleRows by vm.vehicle.rows.collectAsStateWithLifecycle()
    val naviRows by vm.navi.rows.collectAsStateWithLifecycle()
    val vehicleState by vm.vehicle.state.collectAsStateWithLifecycle()
    val naviState by vm.navi.state.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    val logs by AppLog.tail.collectAsStateWithLifecycle()

    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var domain by rememberSaveable { mutableStateOf<String?>(null) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(500)
        }
    }

    val allRows = remember(vehicleRows, naviRows) {
        (naviRows.values + vehicleRows.values).sortedWith(
            compareBy<ProbeRow>({ it.domain != NaviProbe.DOMAIN }, { it.domain }, { it.kind.ordinal }, { it.label })
        )
    }
    val domains = remember(allRows) { allRows.map { it.domain }.distinct() }
    val shown = allRows.filter { (domain == null || it.domain == domain) && filter.accept(it) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Bg)
                .padding(24.dp)
        ) {
            Header(vehicleState, naviState, vehicleRows.values, naviRows.values, vm)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DomainRail(domains, domain, allRows) { domain = it }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    FilterBar(filter) { filter = it }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(shown, key = { it.key }) { RowItem(it, now) }
                    }
                }
                Column(Modifier.width(760.dp).fillMaxHeight()) {
                    PermissionCard(permissions)
                    Spacer(Modifier.height(12.dp))
                    LogPanel(logs)
                }
            }
        }
    }
}

@Composable
private fun Header(
    vehicleState: String,
    naviState: String,
    vehicleRows: Collection<ProbeRow>,
    naviRows: Collection<ProbeRow>,
    vm: ProbeViewModel,
) {
    Column {
        Column {
            Text("Pleos SDK Probe", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Vehicle: $vehicleState", fontSize = 20.sp, color = stateColor(vehicleState))
            Text("NaviHelper: $naviState", fontSize = 20.sp, color = stateColor(naviState))
            val c = { s: ProbeStatus -> vehicleRows.count { it.status == s } }
            val live = vehicleRows.filter { it.kind == ProbeKind.LISTENER }.sumOf { it.updates }
            Text(
                "Vehicle rows ${vehicleRows.size} · OK ${c(ProbeStatus.OK)} · LISTENING ${c(ProbeStatus.LISTENING)} · " +
                    "FAILED ${c(ProbeStatus.FAILED)} · TIMEOUT ${c(ProbeStatus.TIMEOUT)} · SKIPPED ${c(ProbeStatus.SKIPPED)} · " +
                    "live updates $live · Navi events ${naviRows.sumOf { it.updates }}",
                fontSize = 18.sp,
                color = Dim,
            )
            Text("log file: ${AppLog.currentFilePath() ?: "-"}", fontSize = 14.sp, color = Dim)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Vehicle 재실행") { vm.rerunVehicle() }
            BigButton("Navi 정보 요청") { vm.requestNaviInfo() }
            BigButton("테스트 경로(판교역)") { vm.requestTestRoute() }
            BigButton("경로 취소") { vm.cancelRoute() }
            BigButton("퍼미션 새로고침") { vm.refreshPermissions() }
        }
    }
}

@Composable
private fun BigButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(72.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
    ) {
        Text(text, fontSize = 22.sp)
    }
}

@Composable
private fun DomainRail(domains: List<String>, selected: String?, rows: List<ProbeRow>, onSelect: (String?) -> Unit) {
    LazyColumn(Modifier.width(260.dp).fillMaxHeight()) {
        item { RailItem("전체", rows.size, 0, selected == null) { onSelect(null) } }
        items(domains) { d ->
            val inDomain = rows.filter { it.domain == d }
            val ok = inDomain.count { it.status == ProbeStatus.OK || it.status == ProbeStatus.LISTENING }
            RailItem(d, inDomain.size, ok, selected == d) { onSelect(d) }
        }
    }
}

@Composable
private fun RailItem(name: String, total: Int, ok: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) Color(0xFF1E3A8A) else Card,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontSize = 20.sp, color = Color.White, modifier = Modifier.weight(1f))
            Text(if (ok > 0) "$ok/$total" else "$total", fontSize = 16.sp, color = if (ok > 0) Ok else Dim)
        }
    }
}

@Composable
private fun FilterBar(selected: Filter, onSelect: (Filter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Filter.entries.forEach { f ->
            Surface(
                color = if (f == selected) Color(0xFF1E3A8A) else Card,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.clickable { onSelect(f) },
            ) {
                Text(f.label, fontSize = 18.sp, color = Color.White, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun RowItem(row: ProbeRow, now: Long) {
    val recent = row.updates > 0 && now - row.updatedAt < RECENT_MS
    Surface(
        color = if (recent) CardRecent else Card,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(row.kind.badge, fontSize = 16.sp, color = Info, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
            Text(row.status.name, fontSize = 16.sp, color = statusColor(row.status), modifier = Modifier.width(120.dp))
            Column(Modifier.weight(1f)) {
                Text("${row.domain}.${row.label}", fontSize = 19.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                if (row.value.isNotEmpty()) {
                    Text(
                        row.value,
                        fontSize = 17.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFD1D5DB),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (row.permissions.isNotEmpty()) {
                    Text(row.permissions.joinToString(), fontSize = 14.sp, color = Dim)
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(120.dp)) {
                if (row.updates > 0) Text("×${row.updates}", fontSize = 16.sp, color = if (recent) Ok else Dim)
                if (row.updatedAt > 0) Text(ago(now - row.updatedAt), fontSize = 14.sp, color = Dim)
            }
        }
    }
}

@Composable
private fun PermissionCard(permissions: List<PermissionState>) {
    Surface(color = Card, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("pleos.car.permission.*", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
            if (permissions.isEmpty()) Text("(조사 전)", fontSize = 16.sp, color = Dim)
            permissions.forEach { p ->
                Row {
                    Text(
                        if (p.granted) "GRANTED" else "DENIED",
                        fontSize = 15.sp,
                        color = if (p.granted) Ok else Fail,
                        modifier = Modifier.width(100.dp),
                    )
                    Text(p.name.removePrefix(PermissionReport.PREFIX), fontSize = 15.sp, color = Color.White, modifier = Modifier.width(230.dp))
                    Text(p.protection, fontSize = 13.sp, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun LogPanel(logs: List<AppLog.Entry>) {
    Surface(color = Card, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxSize()) {
        Column(Modifier.padding(12.dp)) {
            Text("AppLog (최근 ${logs.size}줄, 최신이 위)", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
            LazyColumn {
                items(logs.asReversed()) { e ->
                    Text(
                        "${e.level} ${e.tag.removePrefix(AppLog.PREFIX)}: ${e.message}",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = when (e.level) {
                            'E' -> Fail
                            'W' -> Warn
                            else -> Color(0xFFC9D1D9)
                        },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun statusColor(s: ProbeStatus) = when (s) {
    ProbeStatus.OK -> Ok
    ProbeStatus.LISTENING -> Info
    ProbeStatus.FAILED -> Fail
    ProbeStatus.TIMEOUT -> Warn
    ProbeStatus.SKIPPED -> Dim
    ProbeStatus.PENDING -> Dim
}

private fun stateColor(state: String) = when {
    state.startsWith("READY") -> Ok
    state.contains("FAIL") -> Fail
    else -> Warn
}

private fun ago(ms: Long): String = when {
    ms < 1_000 -> "now"
    ms < 60_000 -> "${ms / 1000}s ago"
    ms < 3_600_000 -> "${ms / 60_000}m ago"
    else -> "${ms / 3_600_000}h ago"
}
