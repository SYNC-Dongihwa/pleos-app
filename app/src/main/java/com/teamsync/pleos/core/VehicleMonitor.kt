package com.teamsync.pleos.core

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.telltale.TelltaleData
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.EvChargePortOpenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.RangeRemainingListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import android.content.Context
import com.teamsync.pleos.log.AppLog
import com.teamsync.pleos.vehicle.VehicleErrors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.CopyOnWriteArrayList

/** 공통 기반이 상시 감시하는 차량 신호. 값이 null이면 아직 모름, [unavailable]에 있으면 이 차량/권한에서 못 씀. */
data class VehicleSnapshot(
    val connected: Boolean = false,
    val gear: VehicleGear? = null,
    val socPercent: Int? = null,
    val rangeMeters: Float? = null,
    val chargePortOpen: Boolean? = null,
    /** state가 OFF가 아닌 경고등만. */
    val activeTelltales: List<TelltaleData> = emptyList(),
    /** 신호 이름 → 못 쓰는 이유 (capability 실패, 권한 등). */
    val unavailable: Map<String, String> = emptyMap(),
    val error: String? = null,
)

/**
 * Vehicle SDK 상시 구독 (Probe와 달리 필요한 신호만 타입 안전하게).
 * 순서: capability 확인(있는 API만) → 초기값 Get → 리스너 등록. SOC 등은 등록 직후 초기값을 안 주므로 Get이 필요하다 (실측).
 */
class VehicleMonitor(private val context: Context) {

    private val _state = MutableStateFlow(VehicleSnapshot())
    val state: StateFlow<VehicleSnapshot> = _state.asStateFlow()

    @Volatile private var vehicle: Vehicle? = null

    /** 같은 프로세스의 다른 기능(Probe 등)이 공유할 SDK 인스턴스. 인스턴스를 따로 만들어 release하면 이쪽 콜백도 끊긴다. */
    val sdkVehicle: Vehicle? get() = vehicle
    private val unregisters = CopyOnWriteArrayList<Pair<String, () -> Unit>>()

    // stop() 이후 늦게 도착한 capability 콜백이 리스너를 다시 등록하지 않도록
    @Volatile private var generation = 0

    fun start() {
        if (vehicle != null) return
        val gen = ++generation
        val v = Vehicle(context)
        AppLog.i(TAG, "initialize() call")
        try {
            v.initialize()
        } catch (e: Exception) {
            AppLog.e(TAG, "initialize() threw ${VehicleErrors.describe(e)}", e)
            _state.value = VehicleSnapshot(error = "Vehicle 초기화 실패: ${e.message}")
            return
        }
        vehicle = v
        _state.value = VehicleSnapshot(connected = true)
        AppLog.i(TAG, "initialize() returned")
        watchAll(v, gen)
    }

    /**
     * 같은 앱의 다른 곳(Probe 등)이 같은 속성의 리스너를 unregister하면, 서비스가 앱 전체의 그 속성 구독을 지운다 —
     * 이쪽 리스너가 남아 있어도 콜백이 더는 안 옴 (2026-10-22 실측, 참조 카운트 없음). 그런 일이 있은 뒤 호출해 다시 등록한다.
     */
    fun resubscribe(reason: String) {
        val v = vehicle ?: return
        AppLog.i(TAG, "resubscribe ($reason)")
        val gen = ++generation
        unregisterOwn()
        watchAll(v, gen)
    }

    fun stop() {
        generation++
        unregisterOwn()
        vehicle?.let { v ->
            runCatching { v.release() }
                .onSuccess { AppLog.i(TAG, "release() ok") }
                .onFailure { AppLog.e(TAG, "release() threw ${it.message}", it) }
        }
        vehicle = null
        _state.value = VehicleSnapshot()
    }

    private fun watchAll(v: Vehicle, gen: Int) {
        watchGear(v)
        watchSoc(v, gen)
        watchRange(v)
        watchChargePort(v, gen)
        watchTelltales(v, gen)
    }

    private fun unregisterOwn() {
        for ((name, unregister) in unregisters) {
            runCatching(unregister).onFailure { AppLog.e(TAG, "unregister $name threw ${it.message}", it) }
        }
        AppLog.i(TAG, "unregistered ${unregisters.size} listeners")
        unregisters.clear()
    }

    private fun watchGear(v: Vehicle) {
        val api = v.getGear()
        // Gear 도메인에는 check*Capabilities가 없음 (AAR 확인)
        api.getCurrentGearSelectionState({ gear -> setGear(gear, "get") }, failed("Gear.getCurrentGearSelectionState", "gear"))
        val listener = object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) = setGear(gear, "live")
            override fun onFailed(e: Exception) = failed("Gear.registerGearSelection", "gear")(e)
        }
        api.registerGearSelection(listener)
        unregisters += "Gear" to { api.unregisterGearSelection(listener) }
    }

    private fun watchSoc(v: Vehicle, gen: Int) {
        val api = v.getEvBattery()
        api.checkEvBatteryStateOfChargeCapabilities({ config ->
            if (gen != generation) return@checkEvBatteryStateOfChargeCapabilities
            AppLog.i(TAG, "CAP EvBattery.StateOfCharge → $config")
            api.getEvBatteryStateOfCharge({ soc -> setSoc(soc, "get") }, failed("EvBattery.getEvBatteryStateOfCharge", "soc"))
            val listener = object : EvBatteryStateOfChargeListener {
                override fun onEvBatteryStateOfChargeUpdated(soc: Int?) = setSoc(soc, "live")
                override fun onFailed(e: Exception) = failed("EvBattery.registerEvBatteryStateOfCharge", "soc")(e)
            }
            api.registerEvBatteryStateOfCharge(listener)
            unregisters += "EvBatteryStateOfCharge" to { api.unregisterEvBatteryStateOfCharge(listener) }
        }, failed("EvBattery.checkEvBatteryStateOfChargeCapabilities", "soc"))
    }

    private fun watchRange(v: Vehicle) {
        val api = v.getOdometer()
        // 주행가능거리는 전용 check API가 없음 (AAR 확인). 단위 m (실측).
        api.getRangeRemainingDistance({ m -> setRange(m, "get") }, failed("Odometer.getRangeRemainingDistance", "range"))
        val listener = object : RangeRemainingListener {
            override fun onRangeRemainingChanged(meters: Float?) = setRange(meters, "live")
            override fun onFailed(e: Exception) = failed("Odometer.registerRangeRemainingListener", "range")(e)
        }
        api.registerRangeRemainingListener(listener)
        unregisters += "RangeRemaining" to { api.unregisterRangeRemainingListener(listener) }
    }

    private fun watchChargePort(v: Vehicle, gen: Int) {
        val api = v.getEvBattery()
        api.checkEvChargePortOpenCapabilities({ config ->
            if (gen != generation) return@checkEvChargePortOpenCapabilities
            AppLog.i(TAG, "CAP EvBattery.ChargePortOpen → $config")
            api.isEvChargePortOpen({ open -> setChargePort(open, "get") }, failed("EvBattery.isEvChargePortOpen", "chargePort"))
            val listener = object : EvChargePortOpenListener {
                override fun onEvChargePortOpenUpdated(open: Boolean?) = setChargePort(open, "live")
                override fun onFailed(e: Exception) = failed("EvBattery.registerEvChargePortOpen", "chargePort")(e)
            }
            api.registerEvChargePortOpen(listener)
            unregisters += "EvChargePortOpen" to { api.unregisterEvChargePortOpen(listener) }
        }, failed("EvBattery.checkEvChargePortOpenCapabilities", "chargePort"))
    }

    /** PRIVATE_CAR_SAFETY 필요. 권한이 없거나 미지원이면 capability에서 실패 → unavailable로 표시하고 끝. */
    private fun watchTelltales(v: Vehicle, gen: Int) {
        val api = v.getSafety()
        api.checkTelltalesCapabilities({ config ->
            if (gen != generation) return@checkTelltalesCapabilities
            AppLog.i(TAG, "CAP Safety.Telltales → $config")
            api.getTelltalesInfo({ t -> setTelltales(t, "get") }, failed("Safety.getTelltalesInfo", "telltales"))
            val listener = object : TelltaleInfoListener {
                override fun onTelltaleInfoUpdated(telltales: Telltales?) = setTelltales(telltales, "live")
                override fun onFailed(e: Exception) = failed("Safety.registerTelltales", "telltales")(e)
            }
            api.registerTelltales(listener)
            unregisters += "Telltales" to { api.unregisterTelltales(listener) }
        }, failed("Safety.checkTelltalesCapabilities", "telltales"))
    }

    // ---- 상태 갱신 (값이 바뀔 때만 로그) -------------------------------------------------------

    private fun setGear(gear: VehicleGear?, source: String) {
        if (_state.value.gear != gear) AppLog.i(TAG, "gear → $gear ($source)")
        _state.update { it.copy(gear = gear, unavailable = it.unavailable - "gear") }
    }

    private fun setSoc(soc: Int?, source: String) {
        if (_state.value.socPercent != soc) AppLog.i(TAG, "soc → $soc% ($source)")
        _state.update { it.copy(socPercent = soc, unavailable = it.unavailable - "soc") }
    }

    private fun setRange(meters: Float?, source: String) {
        if (_state.value.rangeMeters != meters) AppLog.i(TAG, "range → $meters m ($source)")
        _state.update { it.copy(rangeMeters = meters, unavailable = it.unavailable - "range") }
    }

    private fun setChargePort(open: Boolean?, source: String) {
        if (_state.value.chargePortOpen != open) AppLog.i(TAG, "chargePortOpen → $open ($source)")
        _state.update { it.copy(chargePortOpen = open, unavailable = it.unavailable - "chargePort") }
    }

    private fun setTelltales(telltales: Telltales?, source: String) {
        val active = telltales?.telltales.orEmpty().filter { it.state?.name != "OFF" }
        if (_state.value.activeTelltales != active) {
            AppLog.i(TAG, "telltales → ${active.joinToString { "${it.type}:${it.state}" }.ifEmpty { "(none)" }} ($source)")
        }
        _state.update { it.copy(activeTelltales = active, unavailable = it.unavailable - "telltales") }
    }

    private fun failed(api: String, signal: String): (Exception?) -> Unit = { e ->
        val reason = if (e == null) "onFailed(null)" else VehicleErrors.describe(e)
        AppLog.e(TAG, "onFailed api=$api $reason", e)
        _state.update { it.copy(unavailable = it.unavailable + (signal to reason)) }
    }

    companion object {
        private const val TAG = "Vehicle"
    }
}
