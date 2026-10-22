package com.teamsync.pleos.core

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import com.teamsync.pleos.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AAOS 주행 규제 상태. 아직 Car 서비스에 못 붙었거나 끊겼으면 [connected]=false이고,
 * 이때 UI는 안전하게 "제한 모드"로 그린다 ([requiresDistractionOptimization] 기본값 true).
 */
data class UxState(
    val connected: Boolean = false,
    val requiresDistractionOptimization: Boolean = true,
    val activeRestrictions: Int = 0,
    val maxStringLength: Int = Int.MAX_VALUE,
    val maxCumulativeContentItems: Int = Int.MAX_VALUE,
    val maxContentDepth: Int = Int.MAX_VALUE,
    val error: String? = null,
) {
    /** UI가 주행용(최소) 레이아웃을 써야 하는지. 상태를 모르면 true. */
    val restricted: Boolean get() = !connected || requiresDistractionOptimization

    fun has(flag: Int): Boolean = activeRestrictions and flag != 0

    /** LIMIT_STRING_LENGTH가 걸려 있으면 maxStringLength로 자른다. */
    fun limit(text: String): String =
        if (has(CarUxRestrictions.UX_RESTRICTIONS_LIMIT_STRING_LENGTH) && text.length > maxStringLength) {
            text.take((maxStringLength - 1).coerceAtLeast(1)) + "…"
        } else text
}

class UxRestrictionsMonitor(private val context: Context) {

    private val _state = MutableStateFlow(UxState())
    val state: StateFlow<UxState> = _state.asStateFlow()

    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    private val listener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r -> apply(r, "changed") }

    // 서비스와 화면이 각자 필요로 함 (서비스를 꺼도 떠 있는 DO 화면은 주행 규제를 알아야 함) → 참조 카운트
    private val owners = mutableSetOf<String>()

    @Synchronized
    fun acquire(owner: String) {
        if (owners.add(owner) && owners.size == 1) start()
    }

    @Synchronized
    fun release(owner: String) {
        if (owners.remove(owner) && owners.isEmpty()) stop()
    }

    private fun start() {
        if (car != null) return
        AppLog.i(TAG, "Car.createCar() call")
        try {
            // DO_NOT_WAIT: 즉시 반환하고 연결/재연결은 lifecycle 콜백으로 받음 (메인 스레드)
            car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
                if (ready) onConnected(c) else onDisconnected()
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Car.createCar() threw ${e.javaClass.name}: ${e.message}", e)
            _state.value = UxState(error = "Car 서비스 연결 실패: ${e.message}")
        }
    }

    private fun stop() {
        runCatching { manager?.unregisterListener() }
            .onFailure { AppLog.e(TAG, "unregisterListener threw ${it.message}", it) }
        manager = null
        runCatching { car?.disconnect() }
            .onFailure { AppLog.e(TAG, "Car.disconnect threw ${it.message}", it) }
        car = null
        _state.value = UxState()
        AppLog.i(TAG, "stopped")
    }

    private fun onConnected(c: Car) {
        val m = try {
            c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        } catch (e: Exception) {
            AppLog.e(TAG, "getCarManager(CAR_UX_RESTRICTION_SERVICE) threw ${e.message}", e)
            null
        }
        if (m == null) {
            AppLog.e(TAG, "CarUxRestrictionsManager unavailable")
            _state.value = UxState(error = "CarUxRestrictionsManager 없음")
            return
        }
        manager = m
        m.registerListener(listener)
        AppLog.i(TAG, "connected, listener registered")
        apply(m.currentCarUxRestrictions, "initial")
        logDistractionOptimized(c)
        logAaosProperties(c)
    }

    /**
     * 진단: Pleos Vehicle SDK에 없는 신호(차속·주차 브레이크·야간·외기온도·비상등)를 AAOS 표준 CarPropertyManager로 읽을 수 있는지.
     * CAR_SPEED는 dangerous(런타임 허용 필요), CAR_POWERTRAIN·CAR_EXTERIOR_ENVIRONMENT는 normal, 비상등의 CAR_EXTERIOR_LIGHTS는 signature (2026-10-22 실측).
     */
    private fun logAaosProperties(c: Car) {
        val pm = try {
            c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        } catch (e: Exception) {
            AppLog.e(TAG, "getCarManager(PROPERTY_SERVICE) threw ${e.message}", e)
            null
        } ?: return
        fun read(name: String, block: () -> Any?) = try {
            AppLog.i(TAG, "AAOS $name = ${block()}")
        } catch (e: Exception) {
            AppLog.w(TAG, "AAOS $name failed: ${e.javaClass.simpleName}: ${e.message}")
        }
        read("PERF_VEHICLE_SPEED(m/s)") { pm.getFloatProperty(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0) }
        read("PARKING_BRAKE_ON") { pm.getBooleanProperty(VehiclePropertyIds.PARKING_BRAKE_ON, 0) }
        read("NIGHT_MODE") { pm.getBooleanProperty(VehiclePropertyIds.NIGHT_MODE, 0) }
        read("ENV_OUTSIDE_TEMPERATURE(C)") { pm.getFloatProperty(VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE, 0) }
        read("HAZARD_LIGHTS_STATE") { pm.getIntProperty(VehiclePropertyIds.HAZARD_LIGHTS_STATE, 0) }
        try {
            val ok = pm.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            AppLog.i(TAG, "AAOS speed callback registered=$ok")
        } catch (e: Exception) {
            AppLog.w(TAG, "AAOS speed callback failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    @Volatile private var lastSpeed = -1f

    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val speed = (value.value as? Float) ?: return
            if (kotlin.math.abs(speed - lastSpeed) >= 0.5f) {
                AppLog.i(TAG, "AAOS speed → ${"%.1f".format(speed * 3.6f)} km/h")
                lastSpeed = speed
            }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            AppLog.w(TAG, "AAOS speed onErrorEvent prop=$propertyId area=$areaId")
        }
    }

    /** 진단: 시스템(CarPackageManagerService)이 우리 Activity들을 DO로 인정하는지. 매니페스트 선언만으로 충분한지 판단용. */
    private fun logDistractionOptimized(c: Car) {
        try {
            val pm = c.getCarManager(Car.PACKAGE_SERVICE) as? android.car.content.pm.CarPackageManager ?: return
            for (activity in listOf("com.teamsync.pleos.MainActivity", "com.teamsync.pleos.ProbeActivity")) {
                val optimized = pm.isActivityDistractionOptimized(context.packageName, activity)
                AppLog.i(TAG, "CarPackageManager.isActivityDistractionOptimized($activity) = $optimized")
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "isActivityDistractionOptimized check threw ${e.javaClass.name}: ${e.message}", e)
        }
    }

    private fun onDisconnected() {
        AppLog.w(TAG, "Car service disconnected (will reconnect via lifecycle listener)")
        manager = null
        _state.value = UxState(error = "Car 서비스 연결 끊김")
    }

    private fun apply(r: CarUxRestrictions?, reason: String) {
        if (r == null) {
            AppLog.w(TAG, "restrictions null ($reason)")
            return
        }
        val next = UxState(
            connected = true,
            requiresDistractionOptimization = r.isRequiresDistractionOptimization,
            activeRestrictions = r.activeRestrictions,
            maxStringLength = r.maxRestrictedStringLength,
            maxCumulativeContentItems = r.maxCumulativeContentItems,
            maxContentDepth = r.maxContentDepth,
        )
        // CLAUDE.md 로깅 규칙: isRequiresDistractionOptimization + activeRestrictions 비트값
        AppLog.i(
            TAG,
            "uxr $reason: requiresDO=${next.requiresDistractionOptimization} " +
                "active=0x${next.activeRestrictions.toString(16)} [${describe(next.activeRestrictions)}] " +
                "maxString=${next.maxStringLength} maxItems=${next.maxCumulativeContentItems} maxDepth=${next.maxContentDepth}",
        )
        _state.value = next
    }

    companion object {
        private const val TAG = "UX"

        private val FLAGS = listOf(
            CarUxRestrictions.UX_RESTRICTIONS_NO_DIALPAD to "NO_DIALPAD",
            CarUxRestrictions.UX_RESTRICTIONS_NO_FILTERING to "NO_FILTERING",
            CarUxRestrictions.UX_RESTRICTIONS_LIMIT_STRING_LENGTH to "LIMIT_STRING_LENGTH",
            CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD to "NO_KEYBOARD",
            CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO to "NO_VIDEO",
            CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT to "LIMIT_CONTENT",
            CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP to "NO_SETUP",
            CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE to "NO_TEXT_MESSAGE",
            CarUxRestrictions.UX_RESTRICTIONS_NO_VOICE_TRANSCRIPTION to "NO_VOICE_TRANSCRIPTION",
        )

        fun describe(bits: Int): String =
            if (bits == 0) "BASELINE" else FLAGS.filter { bits and it.first != 0 }.joinToString("|") { it.second }
    }
}
