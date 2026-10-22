package com.teamsync.pleos.probe

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.ChargerOperator
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import com.teamsync.pleos.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * NaviHelper 초기화 + 모든 이벤트 콜백 수신 + 조회 요청(get*Info, 결과는 리스너로 옴).
 * 경로 이벤트(onRouteStarted, 남은 거리/시간 등)를 보려면 경로가 있어야 하므로
 * 버튼으로만 requestRoute(고정 목적지)/cancelRoute를 호출한다.
 */
class NaviProbe(context: Context) {

    private val navi = NaviHelper(context.applicationContext)

    private val _rows = MutableStateFlow<Map<String, ProbeRow>>(emptyMap())
    val rows: StateFlow<Map<String, ProbeRow>> = _rows.asStateFlow()

    private val _state = MutableStateFlow("NOT_INITIALIZED")
    val state: StateFlow<String> = _state.asStateFlow()

    @Volatile private var ready = false
    @Volatile private var listenerAdded = false

    fun start() {
        if (ready) return
        AppLog.i(TAG, "initialize() call")
        _state.value = "INITIALIZING"
        try {
            navi.initialize(
                {
                    ready = true
                    _state.value = "READY"
                    AppLog.i(TAG, "initialize() onSuccess")
                    addListener()
                    requestAllInfo()
                },
                { t ->
                    _state.value = "INIT_FAILED: ${t.javaClass.simpleName}: ${t.message}"
                    AppLog.e(TAG, "initialize() onFailed ${t.javaClass.name}: ${t.message}", t)
                },
            )
        } catch (e: Exception) {
            _state.value = "INIT_FAILED: ${e.javaClass.simpleName}: ${e.message}"
            AppLog.e(TAG, "initialize() threw ${e.javaClass.name}: ${e.message}", e)
        }
    }

    /** 조회 API는 void이고 결과는 on*Info 콜백으로 온다. NAVI_ROUTE 퍼미션 필요. */
    fun requestAllInfo() {
        if (!ready) {
            AppLog.w(TAG, "requestAllInfo skipped: not initialized (state=${_state.value})")
            return
        }
        val requests: List<Pair<String, () -> Unit>> = listOf(
            "getCurrentLocationInfo" to navi::getCurrentLocationInfo,
            "getRouteStateInfo" to navi::getRouteStateInfo,
            "getDestinationInfo" to navi::getDestinationInfo,
            "getWaypointInfo" to navi::getWaypointInfo,
            "getTBTInfo" to navi::getTBTInfo,
            "getChargerOperatorInfo" to navi::getChargerOperatorInfo,
            "getBookmarkInfo" to navi::getBookmarkInfo,
            "getRecentDestinationInfo" to navi::getRecentDestinationInfo,
        )
        for ((name, request) in requests) invokeRequest(name, "requested (결과는 on*Info 행)", request)
    }

    /** NAVI_ROUTE_SEARCH. 목적지는 고정 테스트 지점 — 결과로 오는 경로 이벤트를 관찰하기 위함. */
    fun requestTestRoute() {
        if (!ready) {
            AppLog.w(TAG, "requestTestRoute skipped: not initialized (state=${_state.value})")
            return
        }
        val route = RouteInfo(
            latitude = TEST_DEST_LAT,
            longitude = TEST_DEST_LNG,
            displayLatitude = null,
            displayLongitude = null,
            poiName = TEST_DEST_NAME,
            poiId = "",
            address = "",
            poiSubId = "",
            routeOption = RouteOption.RECOMMENDED,
            poiProvider = "",
        )
        invokeRequest("requestRoute", "requested $route") { navi.requestRoute(route) }
    }

    fun cancelRoute() {
        if (!ready) {
            AppLog.w(TAG, "cancelRoute skipped: not initialized (state=${_state.value})")
            return
        }
        invokeRequest("cancelRoute", "requested") { navi.cancelRoute() }
    }

    private fun invokeRequest(name: String, okValue: String, request: () -> Unit) {
        try {
            request()
            record("request:$name", okValue, countUpdate = false)
            AppLog.i(TAG, "$name() $okValue")
        } catch (e: Exception) {
            record("request:$name", "${e.javaClass.simpleName}: ${e.message}", ProbeStatus.FAILED)
            AppLog.e(TAG, "$name() threw ${e.javaClass.name}: ${e.message}", e)
        }
    }

    fun release() {
        if (listenerAdded) {
            runCatching { navi.removeListener(listener) }
                .onSuccess { AppLog.i(TAG, "removeListener() ok") }
                .onFailure { AppLog.e(TAG, "removeListener() threw ${it.message}", it) }
            listenerAdded = false
        }
        if (ready) {
            runCatching { navi.release() }
                .onSuccess { AppLog.i(TAG, "release() ok") }
                .onFailure { AppLog.e(TAG, "release() threw ${it.message}", it) }
            ready = false
        }
        _state.value = "RELEASED"
    }

    private fun addListener() {
        try {
            navi.addListener(listener)
            listenerAdded = true
            AppLog.i(TAG, "addListener() ok")
        } catch (e: Exception) {
            AppLog.e(TAG, "addListener() threw ${e.javaClass.name}: ${e.message}", e)
        }
    }

    private fun record(name: String, value: String, status: ProbeStatus = ProbeStatus.OK, countUpdate: Boolean = true) {
        val now = System.currentTimeMillis()
        _rows.update { rows ->
            val prev = rows[name] ?: ProbeRow(name, DOMAIN, ProbeKind.NAVI_EVENT, name)
            rows + (name to prev.copy(
                status = status,
                value = value,
                updates = if (countUpdate) prev.updates + 1 else prev.updates,
                updatedAt = now,
            ))
        }
    }

    private fun event(name: String, vararg args: Any?) {
        val value = if (args.isEmpty()) "(no args)" else args.joinToString(" | ") { describe(it) }
        val previous = _rows.value[name]
        record(name, value)
        // onCurrentRoadSpeedLimit 등은 ~1초마다 같은 값이 반복됨 → 값이 바뀔 때만 로그 (무인자 이벤트는 매번)
        if (args.isEmpty() || previous?.value != value) {
            AppLog.i(TAG, "$name → $value (event #${(previous?.updates ?: 0) + 1})")
        }
    }

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) = event("onRouteStarted", info)
        override fun onRouteEnded() = event("onRouteEnded")
        override fun onRoutingCompleted() = event("onRoutingCompleted")
        override fun onReRouteEnded() = event("onReRouteEnded")
        override fun onRouteOptionChanged(option: RouteOption) = event("onRouteOptionChanged", option)
        override fun onRouteCancelled() = event("onRouteCancelled")
        override fun onWaypointChanged(info: WaypointChangedInfo) = event("onWaypointChanged", info)
        override fun onDestinationArrived(info: DestinationArrivedInfo) = event("onDestinationArrived", info)
        override fun onWayPointArrived(info: WaypointArrivedInfo) = event("onWayPointArrived", info)
        override fun onBookMarkChanged() = event("onBookMarkChanged")
        override fun onDestinationChanged() = event("onDestinationChanged")
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) = event("onCurrentLocationInfo", info)
        override fun onRouteStateInfo(info: RouteStateInfo) = event("onRouteStateInfo", info)
        override fun onDestinationInfo(info: DestinationInfo) = event("onDestinationInfo", info)
        override fun onWayPointInfo(list: List<WaypointInfo>) = event("onWayPointInfo", list)
        override fun onRecentDestinationInfo(list: List<RecentDestinationInfo>) = event("onRecentDestinationInfo", list)
        override fun onBookmarkInfo(list: List<BookmarkInfo>) = event("onBookmarkInfo", list)
        override fun onCurrentRoadSpeedLimit(limit: Int) = event("onCurrentRoadSpeedLimit", limit)
        override fun onCameraAlert(list: List<CameraAlert>) = event("onCameraAlert", list)
        override fun onDrivingInfo(info: DrivingInfo) = event("onDrivingInfo", info)
        override fun onNavigationStatus(status: NavigationStatus) = event("onNavigationStatus", status)
        override fun onTBTInfo(list: List<TBTInfo>) = event("onTBTInfo", list)
        override fun onChargerOperatorInfo(list: List<ChargerOperator>) = event("onChargerOperatorInfo", list)
        override fun onCautionPointInfo(list: List<CautionPoint>) = event("onCautionPointInfo", list)
        override fun onTripInfo(info: TripInfo) = event("onTripInfo", info)

        override fun onError(errorCode: NaviErrorCode) {
            record("onError", errorCode.name, ProbeStatus.FAILED)
            AppLog.e(TAG, "onError(NaviErrorCode=${errorCode.name})")
        }
    }

    companion object {
        private const val TAG = "Navi"
        const val DOMAIN = "NaviHelper"

        // 에뮬레이터 기본 현재 위치(성남 수정구 시흥동 37.4127, 127.0947)에서 가까운 판교역
        private const val TEST_DEST_NAME = "판교역"
        private const val TEST_DEST_LAT = 37.394776
        private const val TEST_DEST_LNG = 127.111158
    }
}
