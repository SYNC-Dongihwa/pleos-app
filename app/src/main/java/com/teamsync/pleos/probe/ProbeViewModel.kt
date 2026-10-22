package com.teamsync.pleos.probe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teamsync.pleos.core.CarSession
import com.teamsync.pleos.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProbeViewModel(app: Application) : AndroidViewModel(app) {

    val vehicle = VehicleProbe(app, viewModelScope, CarSession.vehicle.sdkVehicle) {
        CarSession.vehicle.resubscribe("probe unregistered listeners")
    }
    val navi = NaviProbe(app)

    private val _permissions = MutableStateFlow<List<PermissionState>>(emptyList())
    val permissions: StateFlow<List<PermissionState>> = _permissions.asStateFlow()

    private var started = false

    fun refreshPermissions() {
        _permissions.value = PermissionReport.inspect(getApplication())
    }

    /** 퍼미션 요청 결과가 나온 뒤 한 번만 호출 (화면 회전 등으로 Activity가 재생성돼도 재시작하지 않음). */
    fun startIfNeeded() {
        if (started) return
        started = true
        refreshPermissions()
        vehicle.start()
        navi.start()
    }

    fun rerunVehicle() {
        AppLog.i("UI", "rerun vehicle probe")
        refreshPermissions()
        vehicle.start()
    }

    fun requestNaviInfo() {
        AppLog.i("UI", "request navi info")
        navi.requestAllInfo()
    }

    fun requestTestRoute() {
        AppLog.i("UI", "request test route")
        navi.requestTestRoute()
    }

    fun cancelRoute() {
        AppLog.i("UI", "cancel route")
        navi.cancelRoute()
    }

    override fun onCleared() {
        vehicle.release()
        navi.release()
    }
}
