package com.teamsync.pleos.core.alert

import com.teamsync.pleos.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 표시 시간은 심각도별. 운전 중 1줄로 읽히도록 텍스트는 짧게 (UxState.limit로 한 번 더 자름). */
enum class AlertSeverity(val displayMs: Long) {
    INFO(6_000),
    WARN(10_000),
    CRITICAL(20_000),
}

data class DrivingAlert(
    /** 같은 key의 알림은 서로 대체된다 (예: "soc", "telltale"). */
    val key: String,
    val severity: AlertSeverity,
    val text: String,
    val postedAt: Long = System.currentTimeMillis(),
)

/**
 * 운전자에게 보여줄 1줄 알림의 단일 창구.
 * 한 번에 하나만 보여준다: 더 높은 심각도가 떠 있으면 낮은 것은 억제(로그만), 아니면 교체.
 * 화면(OneLineAlertBar)은 [current]를, 백그라운드 알림(AlertNotifier)은 [posted]를 구독한다.
 */
object AlertCenter {
    private const val TAG = "Alert"

    private val _current = MutableStateFlow<DrivingAlert?>(null)
    val current: StateFlow<DrivingAlert?> = _current.asStateFlow()

    private val _posted = MutableSharedFlow<DrivingAlert>(extraBufferCapacity = 16)
    val posted: SharedFlow<DrivingAlert> = _posted.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var expiry: Job? = null

    @Synchronized
    fun post(alert: DrivingAlert) {
        val shown = _current.value
        if (shown != null && shown.key != alert.key && shown.severity > alert.severity) {
            AppLog.i(TAG, "suppressed ${alert.severity} '${alert.text}' (showing ${shown.severity} '${shown.text}')")
            return
        }
        AppLog.i(TAG, "post ${alert.severity} key=${alert.key} '${alert.text}'")
        _current.value = alert
        _posted.tryEmit(alert)
        expiry?.cancel()
        expiry = scope.launch {
            delay(alert.severity.displayMs)
            clearIf(alert)
        }
    }

    /** 운전자가 탭해서 닫음. */
    @Synchronized
    fun dismiss() {
        _current.value?.let { AppLog.i(TAG, "dismissed '${it.text}'") }
        expiry?.cancel()
        _current.value = null
    }

    @Synchronized
    private fun clearIf(alert: DrivingAlert) {
        if (_current.value === alert) _current.value = null
    }
}
