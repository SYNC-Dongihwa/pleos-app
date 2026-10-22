package com.teamsync.pleos

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.teamsync.pleos.log.AppLog
import com.teamsync.pleos.probe.PermissionReport
import com.teamsync.pleos.probe.ProbeViewModel
import com.teamsync.pleos.ui.ProbeScreen

class MainActivity : ComponentActivity() {

    private val vm: ProbeViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        result.forEach { (name, granted) ->
            AppLog.i("Perm", "request result ${name.removePrefix(PermissionReport.PREFIX)} granted=$granted")
        }
        vm.startIfNeeded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i("UI", "MainActivity onCreate restored=${savedInstanceState != null}")
        setContent { ProbeScreen(vm) }

        if (savedInstanceState == null) {
            val missing = PermissionReport.declared(this).filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isEmpty()) {
                vm.startIfNeeded()
            } else {
                // dangerous가 아닌 퍼미션(signature 등)은 다이얼로그 없이 즉시 denied로 돌아온다 → 결과 로그로 구분
                AppLog.i("Perm", "requesting ${missing.size}: ${missing.joinToString { it.removePrefix(PermissionReport.PREFIX) }}")
                permissionLauncher.launch(missing.toTypedArray())
            }
        } else {
            vm.startIfNeeded()
        }
    }
}
