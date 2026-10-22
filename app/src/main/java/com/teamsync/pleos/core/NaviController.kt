package com.teamsync.pleos.core

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.DrivingState
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import com.teamsync.pleos.log.AppLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** 경유지/목적지로 넣을 지점. */
data class Place(val name: String, val latitude: Double, val longitude: Double, val address: String = "")

class NaviException(val code: NaviErrorCode?, message: String) : Exception(message)

data class NaviState(
    val ready: Boolean = false,
    val status: NavigationStatus? = null,
    val naviMode: NaviMode? = null,
    /** onDrivingInfo의 목적지 (남은 거리 m·시간 s·도착 시 잔량 Wh). */
    val destination: DrivingState? = null,
    /** onDrivingInfo의 경유지 (충전소 여부·충전기 수 포함). */
    val waypointStates: List<DrivingState> = emptyList(),
    /** onWayPointInfo의 경유지 목록. */
    val waypoints: List<WaypointInfo> = emptyList(),
    val error: String? = null,
) {
    val routeActive: Boolean get() = status == NavigationStatus.ROUTE_DRIVE || naviMode == NaviMode.ROUTE_GUIDING
}

/**
 * NaviHelper 래퍼. 콜백 기반 요청을 suspend 함수로 감싸고(결과 이벤트·onError까지 대기), 상태를 StateFlow로 노출한다.
 *
 * ⚠️ requestRoute / addWaypoint / removeWaypoint / cancelRoute를 호출하면 Navigation 앱이 전면으로 올라온다 (실측).
 *    호출하는 화면은 "내비로 넘어간다"는 흐름으로 설계할 것. 우리 앱이 백그라운드여도 MonitorService가 떠 있으면 이벤트는 계속 받는다.
 *
 * 요청은 [mutex]로 직렬화한다 — onError는 어떤 요청의 실패인지 알려주지 않으므로 진행 중인 요청에 귀속시킨다.
 */
class NaviController(private val context: Context) {

    private val _state = MutableStateFlow(NaviState())
    val state: StateFlow<NaviState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<NaviErrorCode>(extraBufferCapacity = 8)
    val errors: SharedFlow<NaviErrorCode> = _errors.asSharedFlow()

    private var helper: NaviHelper? = null
    private val mutex = Mutex()

    @Volatile private var pendingRoute: CompletableDeferred<RouteStartInfo>? = null
    @Volatile private var pendingCancel: CompletableDeferred<Unit>? = null
    @Volatile private var pendingWaypointChange: CompletableDeferred<WaypointChangedInfo>? = null
    @Volatile private var pendingWaypointList: CompletableDeferred<List<WaypointInfo>>? = null

    fun start() {
        if (helper != null) return
        val h = NaviHelper(context)
        helper = h
        AppLog.i(TAG, "initialize() call")
        try {
            h.initialize(
                {
                    AppLog.i(TAG, "initialize() onSuccess")
                    h.addListener(listener)
                    _state.update { it.copy(ready = true, error = null) }
                    // 현재 경로 상태·경유지 동기화 (결과는 리스너로)
                    h.getRouteStateInfo()
                    h.getWaypointInfo()
                },
                { t ->
                    AppLog.e(TAG, "initialize() onFailed ${t.javaClass.name}: ${t.message}", t)
                    _state.update { it.copy(ready = false, error = "NaviHelper 초기화 실패: ${t.message}") }
                },
            )
        } catch (e: Exception) {
            AppLog.e(TAG, "initialize() threw ${e.javaClass.name}: ${e.message}", e)
            _state.update { it.copy(ready = false, error = "NaviHelper 초기화 실패: ${e.message}") }
        }
    }

    fun stop() {
        val h = helper ?: return
        failPending(NaviException(null, "NaviHelper released"))
        runCatching { h.removeListener(listener) }.onFailure { AppLog.e(TAG, "removeListener threw ${it.message}", it) }
        runCatching { h.release() }
            .onSuccess { AppLog.i(TAG, "release() ok") }
            .onFailure { AppLog.e(TAG, "release() threw ${it.message}", it) }
        helper = null
        _state.value = NaviState()
    }

    /** 목적지(+경유지 최대 2개)로 경로 요청 → onRouteStarted까지 대기. NAVI_ROUTE_SEARCH. */
    suspend fun requestRoute(destination: Place, waypoints: List<Place> = emptyList()): Result<RouteStartInfo> = mutex.withLock {
        val h = helper ?: return@withLock notReady("requestRoute")
        if (waypoints.size > MAX_WAYPOINTS) return@withLock fail("requestRoute", "경유지는 최대 $MAX_WAYPOINTS 개")
        val route = RouteInfo(
            latitude = destination.latitude,
            longitude = destination.longitude,
            displayLatitude = null,
            displayLongitude = null,
            poiName = destination.name,
            poiId = "",
            address = destination.address,
            poiSubId = "",
            routeOption = RouteOption.RECOMMENDED,
            poiProvider = "",
        )
        val deferred = CompletableDeferred<RouteStartInfo>().also { pendingRoute = it }
        val call = if (waypoints.isEmpty()) {
            "requestRoute(${destination.name})"
        } else {
            "requestRouteWithWaypoints(${destination.name}, via=${waypoints.map { it.name }})"
        }
        try {
            if (waypoints.isEmpty()) {
                h.requestRoute(route)
            } else {
                h.requestRouteWithWaypoints(
                    RouteWithWaypointsRequest(route, waypoints.mapIndexed { i, p -> p.toRequest(INDICES[i]) }),
                )
            }
            AppLog.i(TAG, "$call requested")
        } catch (e: Exception) {
            pendingRoute = null
            return@withLock fail(call, "${e.javaClass.simpleName}: ${e.message}", e)
        }
        await(call, deferred, ROUTE_TIMEOUT_MS).also { pendingRoute = null }
    }

    /** 경로 취소 → onRouteCancelled까지 대기. NAVI_ROUTE_SEARCH. */
    suspend fun cancelRoute(): Result<Unit> = mutex.withLock {
        val h = helper ?: return@withLock notReady("cancelRoute")
        val deferred = CompletableDeferred<Unit>().also { pendingCancel = it }
        try {
            h.cancelRoute()
            AppLog.i(TAG, "cancelRoute() requested")
        } catch (e: Exception) {
            pendingCancel = null
            return@withLock fail("cancelRoute", "${e.javaClass.simpleName}: ${e.message}", e)
        }
        await("cancelRoute", deferred, CANCEL_TIMEOUT_MS).also { pendingCancel = null }
    }

    /**
     * 현재 경로에 경유지 추가 → onWaypointChanged(ADD)까지 대기 후 경유지 목록을 다시 조회해 돌려준다.
     * 빈 슬롯(FIRST → SECOND)을 자동으로 고른다. 경로 안내 중이 아니면 호출하지 않고 실패.
     */
    suspend fun addWaypoint(place: Place): Result<List<WaypointInfo>> = mutex.withLock {
        val h = helper ?: return@withLock notReady("addWaypoint")
        if (!_state.value.routeActive) return@withLock fail("addWaypoint", "경로 안내 중이 아님 (status=${_state.value.status}, mode=${_state.value.naviMode})")
        val current = fetchWaypoints(h).getOrElse { return@withLock Result.failure(it) }
        val index = INDICES.getOrNull(current.size)
            ?: return@withLock fail("addWaypoint", "경유지는 최대 $MAX_WAYPOINTS 개 (현재 ${current.size})")
        val call = "addWaypoint(${place.name}, $index)"
        val deferred = CompletableDeferred<WaypointChangedInfo>().also { pendingWaypointChange = it }
        try {
            h.addWaypoint(place.toRequest(index))
            AppLog.i(TAG, "$call requested")
        } catch (e: Exception) {
            pendingWaypointChange = null
            return@withLock fail(call, "${e.javaClass.simpleName}: ${e.message}", e)
        }
        await(call, deferred, WAYPOINT_TIMEOUT_MS).also { pendingWaypointChange = null }
            .getOrElse { return@withLock Result.failure(it) }
        fetchWaypoints(h)
    }

    /** 경유지 제거 (FIRST / SECOND / ALL) → onWaypointChanged(REMOVE)까지 대기 후 목록 재조회. */
    suspend fun removeWaypoint(index: WaypointIndex): Result<List<WaypointInfo>> = mutex.withLock {
        val h = helper ?: return@withLock notReady("removeWaypoint")
        val call = "removeWaypoint($index)"
        val deferred = CompletableDeferred<WaypointChangedInfo>().also { pendingWaypointChange = it }
        try {
            h.removeWaypoint(index)
            AppLog.i(TAG, "$call requested")
        } catch (e: Exception) {
            pendingWaypointChange = null
            return@withLock fail(call, "${e.javaClass.simpleName}: ${e.message}", e)
        }
        await(call, deferred, WAYPOINT_TIMEOUT_MS).also { pendingWaypointChange = null }
            .getOrElse { return@withLock Result.failure(it) }
        fetchWaypoints(h)
    }

    // ---- 내부 -----------------------------------------------------------------------------

    private suspend fun fetchWaypoints(h: NaviHelper): Result<List<WaypointInfo>> {
        val deferred = CompletableDeferred<List<WaypointInfo>>().also { pendingWaypointList = it }
        try {
            h.getWaypointInfo()
        } catch (e: Exception) {
            pendingWaypointList = null
            return fail("getWaypointInfo", "${e.javaClass.simpleName}: ${e.message}", e)
        }
        return await("getWaypointInfo", deferred, INFO_TIMEOUT_MS).also { pendingWaypointList = null }
    }

    private suspend fun <T> await(call: String, deferred: CompletableDeferred<T>, timeoutMs: Long): Result<T> {
        val result = withTimeoutOrNull(timeoutMs) { runCatching { deferred.await() } }
            ?: return fail(call, "응답 없음 (${timeoutMs}ms)")
        result.onSuccess { AppLog.i(TAG, "$call done") }
            .onFailure { AppLog.e(TAG, "$call failed: ${it.message}") }
        return result
    }

    private fun <T> notReady(call: String): Result<T> =
        fail(call, "NaviHelper 미초기화 (state=${_state.value.error ?: "not started"})")

    private fun <T> fail(call: String, reason: String, e: Throwable? = null): Result<T> {
        AppLog.e(TAG, "$call failed: $reason", e)
        return Result.failure(e as? NaviException ?: NaviException(null, reason))
    }

    private fun failPending(e: Exception) {
        pendingRoute?.completeExceptionally(e)
        pendingCancel?.completeExceptionally(e)
        pendingWaypointChange?.completeExceptionally(e)
        pendingWaypointList?.completeExceptionally(e)
    }

    private fun Place.toRequest(index: WaypointIndex) = RequestWaypointInfo(
        latitude = latitude,
        longitude = longitude,
        poiName = name,
        waypointIndex = index,
        poiId = "",
        address = address,
        poiSubId = "",
        poiProvider = "",
    )

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) {
            AppLog.i(TAG, "onRouteStarted $info")
            pendingRoute?.complete(info)
        }

        override fun onRouteCancelled() {
            AppLog.i(TAG, "onRouteCancelled")
            _state.update { it.copy(destination = null, waypointStates = emptyList(), waypoints = emptyList()) }
            pendingCancel?.complete(Unit)
        }

        override fun onRouteEnded() = AppLog.i(TAG, "onRouteEnded")

        override fun onNavigationStatus(status: NavigationStatus) {
            if (_state.value.status != status) AppLog.i(TAG, "onNavigationStatus $status")
            _state.update { it.copy(status = status) }
        }

        override fun onRouteStateInfo(info: RouteStateInfo) {
            if (_state.value.naviMode != info.naviMode) AppLog.i(TAG, "onRouteStateInfo naviMode=${info.naviMode} isRouting=${info.isRouting}")
            _state.update { it.copy(naviMode = info.naviMode) }
        }

        override fun onDrivingInfo(info: DrivingInfo) {
            val prev = _state.value
            if (prev.destination?.name != info.destination.name || prev.waypointStates.map { it.name } != info.waypoints.map { it.name }) {
                AppLog.i(TAG, "onDrivingInfo destination=${info.destination.name} waypoints=${info.waypoints.map { it.name }}")
            }
            _state.update { it.copy(destination = info.destination, waypointStates = info.waypoints) }
        }

        override fun onWaypointChanged(info: WaypointChangedInfo) {
            AppLog.i(TAG, "onWaypointChanged index=${info.index} cmd=${info.cmdType} route=${info.routeStartInfo}")
            pendingWaypointChange?.complete(info)
        }

        override fun onWayPointInfo(list: List<WaypointInfo>) {
            AppLog.i(TAG, "onWayPointInfo ${list.map { "${it.name}(${it.remainDistance}m)" }}")
            _state.update { it.copy(waypoints = list) }
            pendingWaypointList?.complete(list)
        }

        override fun onWayPointArrived(info: WaypointArrivedInfo) = AppLog.i(TAG, "onWayPointArrived $info")

        override fun onDestinationArrived(info: DestinationArrivedInfo) = AppLog.i(TAG, "onDestinationArrived $info")

        override fun onError(errorCode: NaviErrorCode) {
            AppLog.e(TAG, "onError(NaviErrorCode=${errorCode.name})")
            _errors.tryEmit(errorCode)
            failPending(NaviException(errorCode, "NaviErrorCode.${errorCode.name}"))
        }
    }

    companion object {
        private const val TAG = "Navi"
        const val MAX_WAYPOINTS = 2
        private val INDICES = listOf(WaypointIndex.FIRST, WaypointIndex.SECOND)
        private const val ROUTE_TIMEOUT_MS = 20_000L
        private const val CANCEL_TIMEOUT_MS = 10_000L
        private const val WAYPOINT_TIMEOUT_MS = 20_000L
        private const val INFO_TIMEOUT_MS = 5_000L
    }
}
