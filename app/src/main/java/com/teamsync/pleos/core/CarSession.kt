package com.teamsync.pleos.core

import android.content.Context
import com.teamsync.pleos.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 앱 전역 차량·내비·주행규제 연결.
 * - [init]은 Application에서 한 번 (객체만 만들고 연결은 안 함 → UI가 언제든 같은 flow를 구독 가능)
 * - [start]/[stop]은 MonitorService가 호출 (서비스가 프로세스를 전면 상태로 유지해야 백그라운드에서도 콜백이 옴)
 */
object CarSession {
    private const val TAG = "Session"

    lateinit var vehicle: VehicleMonitor
        private set
    lateinit var navi: NaviController
        private set
    lateinit var ux: UxRestrictionsMonitor
        private set

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun init(context: Context) {
        if (::vehicle.isInitialized) return
        val app = context.applicationContext
        vehicle = VehicleMonitor(app)
        navi = NaviController(app)
        ux = UxRestrictionsMonitor(app)
    }

    @Synchronized
    fun start() {
        if (_running.value) return
        AppLog.i(TAG, "start")
        ux.acquire(OWNER)
        vehicle.start()
        navi.start()
        _running.value = true
    }

    @Synchronized
    fun stop() {
        if (!_running.value) return
        AppLog.i(TAG, "stop")
        navi.stop()
        vehicle.stop()
        ux.release(OWNER)
        _running.value = false
    }

    private const val OWNER = "session"
}
