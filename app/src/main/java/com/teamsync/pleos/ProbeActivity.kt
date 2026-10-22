package com.teamsync.pleos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.teamsync.pleos.log.AppLog
import com.teamsync.pleos.probe.ProbeViewModel
import com.teamsync.pleos.ui.ProbeScreen

/**
 * SDK Probe (개발용). DO 미선언 → 주행 중엔 시스템이 화면을 가린다 (텍스트가 많아 주행 중 표시 금지 대상).
 * 퍼미션 요청은 MainActivity가 먼저 처리하므로 여기서는 바로 시작한다.
 */
class ProbeActivity : ComponentActivity() {

    private val vm: ProbeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i("UI", "ProbeActivity onCreate restored=${savedInstanceState != null}")
        setContent { ProbeScreen(vm) }
        vm.startIfNeeded()
    }
}
