package com.teamsync.pleos.car

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
import kotlinx.coroutines.flow.update

/** AAOS 속성 하나의 상태. 값을 못 받았으면 [value]=null, 이유는 [error]. */
data class AaosProperty<T>(
    val value: T? = null,
    val error: String? = null,
    val updatedAt: Long = 0,
)

/**
 * AAOS Car 서비스에서 받는 정차 판정 재료. Pleos 문서는 기어 같은 표준 데이터를 AAOS Car API로 읽으라고 안내한다.
 * [requiresDistractionOptimization]·[activeRestrictions]는 주행 규제를 아직 못 받았으면 null.
 */
data class AaosState(
    val connected: Boolean = false,
    val error: String? = null,
    val requiresDistractionOptimization: Boolean? = null,
    val activeRestrictions: Int? = null,
    /** `GEAR_SELECTION` 원시값 (`android.car.VehicleGear` 상수: P=4, D=8 …). */
    val gear: AaosProperty<Int> = AaosProperty(),
    val parkingBrake: AaosProperty<Boolean> = AaosProperty(),
)

/**
 * Car 서비스 연결 + 주행 규제 리스너 + 기어·주차 브레이크 속성 구독. 앱 프로세스 동안 하나만 둔다.
 * 퍼미션: `android.car.permission.CAR_POWERTRAIN` (GEAR_SELECTION·PARKING_BRAKE_ON 읽기). 주행 규제는 퍼미션 불필요.
 */
class AaosCarMonitor(private val context: Context) {

    private val _state = MutableStateFlow(AaosState())
    val state: StateFlow<AaosState> = _state.asStateFlow()

    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var propertyManager: CarPropertyManager? = null

    /** Car 서비스에 (재)연결될 때마다 CarPropertyManager를 넘긴다 — 주유구·충전구처럼 다른 곳이 구독하는 AAOS 속성용. [start] 전에 설정. */
    var onPropertyManager: ((CarPropertyManager) -> Unit)? = null

    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r -> applyUx(r, "changed") }

    private val propertyCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.GEAR_SELECTION -> setGear(value.value as? Int, "live", value.status)
                VehiclePropertyIds.PARKING_BRAKE_ON -> setParkingBrake(value.value as? Boolean, "live", value.status)
            }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            val name = VehiclePropertyIds.toString(propertyId)
            AppLog.w(TAG, "PROP onErrorEvent $name area=$areaId")
            when (propertyId) {
                VehiclePropertyIds.GEAR_SELECTION -> _state.update { it.copy(gear = it.gear.copy(error = "onErrorEvent")) }
                VehiclePropertyIds.PARKING_BRAKE_ON -> _state.update { it.copy(parkingBrake = it.parkingBrake.copy(error = "onErrorEvent")) }
            }
        }
    }

    fun start() {
        if (car != null) return
        AppLog.i(TAG, "Car.createCar() call")
        try {
            // DO_NOT_WAIT: 즉시 반환, 연결/재연결은 lifecycle 콜백(메인 스레드)으로 받음
            car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
                if (ready) onConnected(c) else onDisconnected()
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Car.createCar() threw ${e.javaClass.name}: ${e.message}", e)
            _state.value = AaosState(error = "Car 서비스 연결 실패: ${e.message}")
        }
    }

    fun stop() {
        runCatching { uxManager?.unregisterListener() }.onFailure { AppLog.e(TAG, "UX unregisterListener threw ${it.message}", it) }
        runCatching { propertyManager?.unregisterCallback(propertyCallback) }.onFailure { AppLog.e(TAG, "PROP unregisterCallback threw ${it.message}", it) }
        uxManager = null
        propertyManager = null
        runCatching { car?.disconnect() }.onFailure { AppLog.e(TAG, "Car.disconnect threw ${it.message}", it) }
        car = null
        _state.value = AaosState()
        AppLog.i(TAG, "stopped")
    }

    private fun onConnected(c: Car) {
        AppLog.i(TAG, "Car connected")
        _state.update { it.copy(connected = true, error = null) }
        connectUx(c)
        connectProperties(c)
    }

    private fun onDisconnected() {
        AppLog.w(TAG, "Car service disconnected (lifecycle listener가 재연결)")
        uxManager = null
        propertyManager = null
        _state.value = AaosState(error = "Car 서비스 연결 끊김")
    }

    private fun connectUx(c: Car) {
        val m = runCatching { c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager }
            .onFailure { AppLog.e(TAG, "getCarManager(CAR_UX_RESTRICTION_SERVICE) threw ${it.message}", it) }
            .getOrNull()
        if (m == null) {
            AppLog.e(TAG, "CarUxRestrictionsManager unavailable")
            _state.update { it.copy(error = "주행 규제 서비스 없음") }
            return
        }
        uxManager = m
        m.registerListener(uxListener)
        AppLog.i(TAG, "UX listener registered")
        applyUx(m.currentCarUxRestrictions, "initial")
    }

    private fun connectProperties(c: Car) {
        val pm = runCatching { c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager }
            .onFailure { AppLog.e(TAG, "getCarManager(PROPERTY_SERVICE) threw ${it.message}", it) }
            .getOrNull()
        if (pm == null) {
            AppLog.e(TAG, "CarPropertyManager unavailable")
            val reason = "CarPropertyManager 없음"
            _state.update { it.copy(gear = AaosProperty(error = reason), parkingBrake = AaosProperty(error = reason)) }
            return
        }
        propertyManager = pm
        // 기준값 get → 변경 구독. 퍼미션이 없으면 SecurityException → 그대로 오류로 노출 (가짜 값 없음)
        try {
            setGear(pm.getIntProperty(VehiclePropertyIds.GEAR_SELECTION, 0), "get", CarPropertyValue.STATUS_AVAILABLE)
        } catch (e: Exception) {
            propertyFailed("GEAR_SELECTION get", e) { err -> _state.update { it.copy(gear = it.gear.copy(error = err)) } }
        }
        try {
            setParkingBrake(pm.getBooleanProperty(VehiclePropertyIds.PARKING_BRAKE_ON, 0), "get", CarPropertyValue.STATUS_AVAILABLE)
        } catch (e: Exception) {
            propertyFailed("PARKING_BRAKE_ON get", e) { err -> _state.update { it.copy(parkingBrake = it.parkingBrake.copy(error = err)) } }
        }
        subscribe(pm, VehiclePropertyIds.GEAR_SELECTION) { err -> _state.update { it.copy(gear = it.gear.copy(error = err)) } }
        subscribe(pm, VehiclePropertyIds.PARKING_BRAKE_ON) { err -> _state.update { it.copy(parkingBrake = it.parkingBrake.copy(error = err)) } }
        onPropertyManager?.invoke(pm)
    }

    @Suppress("DEPRECATION") // API 34 기기에는 subscribePropertyEvents가 없음
    private fun subscribe(pm: CarPropertyManager, propertyId: Int, onError: (String) -> Unit) {
        val name = VehiclePropertyIds.toString(propertyId)
        try {
            val ok = pm.registerCallback(propertyCallback, propertyId, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            AppLog.i(TAG, "PROP REG $name ok=$ok")
            if (!ok) onError("registerCallback=false")
        } catch (e: Exception) {
            propertyFailed("$name registerCallback", e, onError)
        }
    }

    private fun propertyFailed(what: String, e: Exception, onError: (String) -> Unit) {
        AppLog.e(TAG, "PROP $what threw ${e.javaClass.simpleName}: ${e.message}", e)
        onError("${e.javaClass.simpleName}: ${e.message}")
    }

    private fun setGear(gear: Int?, source: String, status: Int) {
        if (status != CarPropertyValue.STATUS_AVAILABLE) {
            AppLog.w(TAG, "PROP GEAR_SELECTION status=$status ($source)")
            _state.update { it.copy(gear = it.gear.copy(error = "status=$status")) }
            return
        }
        if (_state.value.gear.value != gear) AppLog.i(TAG, "PROP GEAR_SELECTION = $gear ${gearName(gear)} ($source)")
        _state.update { it.copy(gear = AaosProperty(gear, null, System.currentTimeMillis())) }
    }

    private fun setParkingBrake(on: Boolean?, source: String, status: Int) {
        if (status != CarPropertyValue.STATUS_AVAILABLE) {
            AppLog.w(TAG, "PROP PARKING_BRAKE_ON status=$status ($source)")
            _state.update { it.copy(parkingBrake = it.parkingBrake.copy(error = "status=$status")) }
            return
        }
        if (_state.value.parkingBrake.value != on) AppLog.i(TAG, "PROP PARKING_BRAKE_ON = $on ($source)")
        _state.update { it.copy(parkingBrake = AaosProperty(on, null, System.currentTimeMillis())) }
    }

    private fun applyUx(r: CarUxRestrictions?, reason: String) {
        if (r == null) {
            AppLog.w(TAG, "UX restrictions null ($reason)")
            return
        }
        AppLog.i(
            TAG,
            "UX $reason: requiresDO=${r.isRequiresDistractionOptimization} " +
                "active=0x${r.activeRestrictions.toString(16)} [${describeRestrictions(r.activeRestrictions)}]",
        )
        _state.update { it.copy(requiresDistractionOptimization = r.isRequiresDistractionOptimization, activeRestrictions = r.activeRestrictions) }
    }

    companion object {
        private const val TAG = "Car"

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

        fun describeRestrictions(bits: Int): String =
            if (bits == 0) "BASELINE" else FLAGS.filter { bits and it.first != 0 }.joinToString("|") { it.second }
    }
}
