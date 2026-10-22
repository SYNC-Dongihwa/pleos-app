package com.teamsync.pleos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.teamsync.pleos.core.CarSession
import com.teamsync.pleos.log.AppLog
import com.teamsync.pleos.probe.PermissionReport
import com.teamsync.pleos.service.MonitorService
import com.teamsync.pleos.ui.HomeScreen
import com.teamsync.pleos.ui.HomeViewModel

/**
 * 홈 화면. 매니페스트에서 distractionOptimized=true로 선언 → 주행 중에도 시스템이 가리지 않는 대신
 * CarUxRestrictions에 따라 화면을 직접 줄인다 (HomeScreen의 DrivingLayout).
 */
class MainActivity : ComponentActivity() {

    private val vm: HomeViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        result.forEach { (name, granted) -> AppLog.i("Perm", "request result ${name.substringAfterLast('.')} granted=$granted") }
        // 퍼미션 결과와 무관하게 시작 — 거부된 기능은 각 모니터가 onFailed/unavailable로 기록한다
        MonitorService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i("UI", "MainActivity onCreate restored=${savedInstanceState != null}")
        setContent { HomeScreen(vm, onOpenProbe = { startActivity(Intent(this, ProbeActivity::class.java)) }) }
        if (savedInstanceState == null) requestPermissionsThenStart()
    }

    // 화면이 보이는 동안은 서비스와 무관하게 주행 규제를 직접 구독 (서비스를 꺼도 DO 화면은 규제를 지켜야 함)
    override fun onStart() {
        super.onStart()
        CarSession.ux.acquire(UX_OWNER)
    }

    override fun onStop() {
        CarSession.ux.release(UX_OWNER)
        super.onStop()
    }

    private fun requestPermissionsThenStart() {
        val wanted = PermissionReport.declared(this) +
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
        val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            MonitorService.start(this)
        } else {
            AppLog.i("Perm", "requesting ${missing.size}: ${missing.joinToString { it.substringAfterLast('.') }}")
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private companion object {
        const val UX_OWNER = "home"
    }
}
