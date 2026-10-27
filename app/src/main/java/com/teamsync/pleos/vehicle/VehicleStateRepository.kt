package com.teamsync.pleos.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.api.model.capability.HvacTemperatureConfigArray
import ai.pleos.playground.vehicle.constant.ErrorCode
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.DrivingModeType
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.DoorLockStateListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.DrivingModeStateListener
import ai.pleos.playground.vehicle.listener.HvacAcStateListener
import ai.pleos.playground.vehicle.listener.HvacAutoStateListener
import ai.pleos.playground.vehicle.listener.HvacDefrosterStateListener
import ai.pleos.playground.vehicle.listener.HvacFanSpeedListener
import ai.pleos.playground.vehicle.listener.HvacRecirculationStateListener
import ai.pleos.playground.vehicle.listener.HvacSeatTemperatureListener
import ai.pleos.playground.vehicle.listener.HvacSteeringWheelHeatStateListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WindowLockStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyConfig
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import com.teamsync.pleos.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 튜토리얼 신호의 단일 등록 주체. 신호마다 capability 확인 → Area 선택 → 기준값 get → 리스너 등록 순서로 진행하고 [state]로 노출한다.
 *
 * - 같은 속성의 리스너를 다른 곳에서 unregister하면 앱 전체 구독이 서비스에서 지워진다 (CLAUDE.md §7-4) → 이 클래스만 등록한다.
 * - register는 미지원 속성이어도 예외 없이 끝나는 경우가 있어 capability를 먼저 본다 (§7-5).
 * - Area는 capability가 준 것만 쓴다. 운전석이 필요한 신호는 ROW_1_LEFT를 포함하는 Area 중 가장 좁은 것을 고른다.
 * - SDK 콜백은 binder 스레드에서 온다. 상태 갱신은 [MutableStateFlow.update]로만.
 */
class VehicleStateRepository(private val context: Context) {

    private val _state = MutableStateFlow(VehicleState())
    val state: StateFlow<VehicleState> = _state.asStateFlow()

    private val _changes = MutableSharedFlow<SignalChange>(extraBufferCapacity = 64)
    /** 값이 바뀔 때마다 하나씩 (합쳐지지 않음). 튜토리얼 전이 판정용. */
    val changes: SharedFlow<SignalChange> = _changes.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var vehicle: Vehicle? = null
    private val unregisters = CopyOnWriteArrayList<Pair<VehicleSignal, () -> Unit>>()

    // stop() 이후 늦게 도착한 capability 콜백이 리스너를 다시 등록하지 않도록
    @Volatile private var generation = 0

    fun start() {
        if (vehicle != null) return
        val gen = ++generation
        val v = Vehicle(context)
        AppLog.i(TAG, "Vehicle.initialize() call")
        try {
            v.initialize()
        } catch (e: Exception) {
            AppLog.e(TAG, "Vehicle.initialize() threw ${VehicleErrors.describe(e)}", e)
            _state.value = VehicleState(connection = VehicleConnection.Failed("Vehicle 초기화 실패: ${e.message}"))
            return
        }
        vehicle = v
        _state.value = VehicleState(connection = VehicleConnection.Connected)
        AppLog.i(TAG, "Vehicle.initialize() returned")

        watchLowBeam(v, gen)
        watchHighBeam(v, gen)
        watchTurnSignal(v, gen)
        watchWipers(v, gen)
        watchDriverWindow(v, gen)
        watchHvacAc(v, gen)
        watchDriverTemperature(v, gen)
        watchSteeringHeat(v, gen)
        watchDoorLock(v, gen)
        watchDoorPosition(v, gen)
        watchDrivingMode(v, gen)
        watchDefrosters(v, gen)
        watchRecirculation(v, gen)
        watchHvacAuto(v, gen)
        watchFanSpeed(v, gen)
        watchSeatHeat(v, gen)
        watchWindowLock(v, gen)
        watchCabinLight(v, gen)
        watchReadingLight(v, gen)
        VehicleSignal.entries.forEach { armCapabilityTimeout(it, gen) }
    }

    // ---- AAOS 표준 속성 (Pleos SDK에 없는 신호) ----------------------------------------------

    private var aaosManager: CarPropertyManager? = null

    private val aaosCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val s = AAOS_SIGNALS.entries.firstOrNull { it.value == value.propertyId }?.key ?: return
            if (value.status == CarPropertyValue.STATUS_AVAILABLE) publish(s, value.value, "live")
            else update(s) { it.copy(lastError = "AAOS status=${value.status}") }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            val s = AAOS_SIGNALS.entries.firstOrNull { it.value == propertyId }?.key ?: return
            AppLog.w(TAG, "AAOS onErrorEvent ${s.name} area=$areaId")
            update(s) { it.copy(lastError = "AAOS onErrorEvent area=$areaId") }
        }
    }

    /**
     * 주유구·충전구: Car 서비스가 연결되면 [com.teamsync.pleos.car.AaosCarMonitor]가 넘겨준다.
     * carPropertyConfig에 속성이 없으면(예: 전기차의 FUEL_DOOR_OPEN) '지원 안 됨'. 퍼미션 `android.car.permission.CAR_ENERGY_PORTS`.
     */
    fun attachAaos(pm: CarPropertyManager) {
        aaosManager = pm
        for ((s, id) in AAOS_SIGNALS) {
            val name = VehiclePropertyIds.toString(id)
            try {
                val config = pm.getCarPropertyConfig(id)
                if (config == null) {
                    unsupported(s, "AAOS $name 속성 없음 (carPropertyConfig)")
                    continue
                }
                val readable = config.access == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ ||
                    config.access == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE
                AppLog.i(TAG, "CAP ${s.name} AAOS $name access=${config.access}")
                if (!readable) {
                    unsupported(s, "AAOS $name 읽기 불가 (access=${config.access})")
                    continue
                }
                update(s) { it.copy(support = Support.Supported("AAOS $name access=${config.access}")) }
                publish(s, pm.getBooleanProperty(id, 0), "get")
                @Suppress("DEPRECATION") // API 34 기기에는 subscribePropertyEvents가 없음
                val ok = pm.registerCallback(aaosCallback, id, CarPropertyManager.SENSOR_RATE_ONCHANGE)
                AppLog.i(TAG, "REG ${s.name} (AAOS $name) ok=$ok")
            } catch (e: Exception) {
                // 퍼미션이 없으면 SecurityException
                AppLog.e(TAG, "AAOS ${s.name} $name failed ${e.javaClass.simpleName}: ${e.message}", e)
                unsupported(s, "AAOS $name: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    fun stop() {
        generation++
        for ((signal, unregister) in unregisters) {
            runCatching(unregister)
                .onSuccess { AppLog.i(TAG, "UNREG ${signal.name}") }
                .onFailure { AppLog.e(TAG, "UNREG ${signal.name} threw ${it.message}", it) }
        }
        unregisters.clear()
        aaosManager?.let { pm ->
            runCatching { pm.unregisterCallback(aaosCallback) }.onFailure { AppLog.e(TAG, "AAOS unregister threw ${it.message}", it) }
        }
        aaosManager = null
        vehicle?.let { v ->
            runCatching { v.release() }
                .onSuccess { AppLog.i(TAG, "Vehicle.release() ok") }
                .onFailure { AppLog.e(TAG, "Vehicle.release() threw ${it.message}", it) }
        }
        vehicle = null
        _state.value = VehicleState()
    }

    /**
     * 오프닝 실내등 연출 (차량 제어). 호출자가 `CONTROL_CAR_LIGHTS` 부여를 확인한 뒤에만 부른다.
     * capability가 쓰기를 허용할 때만: 원래 스위치 값 get → ON → [durationMs] 뒤 원래 값으로 복구.
     */
    fun flashCabinLight(durationMs: Long) {
        val v = vehicle ?: return AppLog.w(TAG, "CABIN skipped: Vehicle not initialized")
        val api = v.getLight()
        api.checkCabinLightSwitchCapabilities({ config: CapabilityConfig, values: List<LightSwitch> ->
            AppLog.i(TAG, "CAP CABIN_LIGHT_SWITCH accessType=${config.accessType} values=$values")
            if (config.accessType != AccessType.WRITE && config.accessType != AccessType.READ_WRITE) {
                AppLog.w(TAG, "CABIN skipped: not writable")
                return@checkCabinLightSwitchCapabilities
            }
            api.getCabinLightSwitch({ original ->
                AppLog.i(TAG, "CABIN original=$original → ON")
                api.setCabinLightSwitch(LightSwitch.ON, { set ->
                    AppLog.i(TAG, "CABIN set → $set")
                    scope.launch {
                        delay(durationMs)
                        val restore = original ?: LightSwitch.OFF
                        api.setCabinLightSwitch(restore, { AppLog.i(TAG, "CABIN restored → $it") }) { e ->
                            AppLog.e(TAG, "CABIN restore failed ${VehicleErrors.describe(e)}", e)
                        }
                    }
                }) { e -> AppLog.e(TAG, "CABIN set failed ${VehicleErrors.describe(e)}", e) }
            }) { e -> AppLog.e(TAG, "CABIN get failed ${VehicleErrors.describe(e)}", e) }
        }) { e -> AppLog.w(TAG, "CABIN capability failed ${VehicleErrors.describe(e)}") }
    }

    // ---- 신호별 구독 ------------------------------------------------------------------------

    private fun watchLowBeam(v: Vehicle, gen: Int) {
        val s = VehicleSignal.LOW_BEAM
        val api = v.getLight()
        api.checkLowBeamLightStateCapabilities({ config: CapabilityConfig, values: List<LightState> ->
            onCapability(gen, s, config, "values=$values") {
                api.getCurrentLowBeamLightState({ publish(s, it, "get") }, failed(s, "getCurrentLowBeamLightState"))
                val l = object : LightStateListener {
                    override fun onLightStateUpdated(state: LightState?) = publish(s, state, "live")
                    override fun onFailed(e: Exception) = failed(s, "registerLowBeamLightState")(e)
                }
                api.registerLowBeamLightState(l)
                registered(s) { api.unregisterLowBeamLightState(l) }
            }
        }, capabilityFailed(s, "checkLowBeamLightStateCapabilities"))
    }

    private fun watchHighBeam(v: Vehicle, gen: Int) {
        val s = VehicleSignal.HIGH_BEAM
        val api = v.getLight()
        api.checkHighBeamLightStateCapabilities({ config: CapabilityConfig, values: List<LightState> ->
            onCapability(gen, s, config, "values=$values") {
                api.getCurrentHighBeamLightState({ publish(s, it, "get") }, failed(s, "getCurrentHighBeamLightState"))
                val l = object : LightStateListener {
                    override fun onLightStateUpdated(state: LightState?) = publish(s, state, "live")
                    override fun onFailed(e: Exception) = failed(s, "registerHighBeamLightState")(e)
                }
                api.registerHighBeamLightState(l)
                registered(s) { api.unregisterHighBeamLightState(l) }
            }
        }, capabilityFailed(s, "checkHighBeamLightStateCapabilities"))
    }

    private fun watchTurnSignal(v: Vehicle, gen: Int) {
        val s = VehicleSignal.TURN_SIGNAL
        val api = v.getTurnSignal()
        api.checkTurnSignalStateCapabilities({ config: CapabilityConfig ->
            onCapability(gen, s, config, "") {
                api.getCurrentTurnSignalState({ publish(s, it, "get") }, failed(s, "getCurrentTurnSignalState"))
                val l = object : TurnSignalStateListener {
                    override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = publish(s, state, "live")
                    override fun onFailed(e: Exception) = failed(s, "registerTurnSignalState")(e)
                }
                api.registerTurnSignalState(l)
                registered(s) { api.unregisterTurnSignalState(l) }
            }
        }, capabilityFailed(s, "checkTurnSignalStateCapabilities"))
    }

    /** 앞·뒤 와이퍼는 같은 capability(WiperState)를 공유 → 한 번 확인하고 Area별로 등록. */
    private fun watchWipers(v: Vehicle, gen: Int) {
        val api = v.getWiper()
        val targets = listOf(VehicleSignal.FRONT_WIPER to WindowValue.FRONT_WINDSHIELD, VehicleSignal.REAR_WIPER to WindowValue.REAR_WINDSHIELD)
        api.checkWiperStateCapabilities({ config: CapabilityConfig, areas: Map<WindowArea, List<WiperState>> ->
            for ((s, value) in targets) {
                val area = pick(areas.keys, { it.values }, value)
                onCapability(gen, s, config, "areas=${areas.describe { it.values }} use=${area?.values?.toList()}", area) {
                    api.getCurrentWiperState(area!!, { publish(s, it, "get") }, failed(s, "getCurrentWiperState"))
                    val l = object : WiperStateListener {
                        override fun onWiperStateUpdated(state: WiperState?) = publish(s, state, "live")
                        override fun onFailed(e: Exception) = failed(s, "registerWiperState")(e)
                    }
                    api.registerWiperState(area, l)
                    registered(s) { api.unregisterWiperState(area, l) }
                }
            }
        }) { e -> targets.forEach { (s, _) -> capabilityFailed(s, "checkWiperStateCapabilities")(e) } }
    }

    /** 앞유리 김서림 제거·뒷유리 열선: 같은 capability(HvacDefroster). */
    private fun watchDefrosters(v: Vehicle, gen: Int) {
        val api = v.getHVAC()
        val targets = listOf(VehicleSignal.FRONT_DEFROSTER to WindowValue.FRONT_WINDSHIELD, VehicleSignal.REAR_DEFROSTER to WindowValue.REAR_WINDSHIELD)
        api.checkHvacDefrosterCapabilities({ config: CapabilityConfig, areas: List<WindowArea> ->
            for ((s, value) in targets) {
                val area = pick(areas, { it.values }, value)
                onCapability(gen, s, config, "areas=${areas.map { it.values.toList() }} use=${area?.values?.toList()}", area) {
                    api.getHvacDefrosterState(area!!, { publish(s, it, "get") }, failed(s, "getHvacDefrosterState"))
                    val l = object : HvacDefrosterStateListener {
                        override fun onHvacDefrosterStateUpdated(state: Boolean?) = publish(s, state, "live")
                        override fun onFailed(e: Exception) = failed(s, "registerHvacDefrosterState")(e)
                    }
                    api.registerHvacDefrosterState(area, l)
                    registered(s) { api.unregisterHvacDefrosterState(area, l) }
                }
            }
        }) { e -> targets.forEach { (s, _) -> capabilityFailed(s, "checkHvacDefrosterCapabilities")(e) } }
    }

    private fun watchRecirculation(v: Vehicle, gen: Int) {
        val s = VehicleSignal.RECIRCULATION
        val api = v.getHVAC()
        api.checkHvacRecirculationCapabilities({ config: CapabilityConfig, areas: List<SeatArea> ->
            val area = pick(areas, { it.values }, SeatValue.ROW_1_LEFT)
            onCapability(gen, s, config, "areas=${areas.map { it.values.toList() }} use=${area?.values?.toList()}", area) {
                api.getHvacRecirculationState(area!!, { publish(s, it, "get") }, failed(s, "getHvacRecirculationState"))
                // SDK의 리스너 메서드 이름은 onHvacAutoRecirculationStateUpdated (내기 순환·자동 내기 순환이 같은 리스너를 씀)
                val l = object : HvacRecirculationStateListener {
                    override fun onHvacAutoRecirculationStateUpdated(state: Boolean?) = publish(s, state, "live")
                    override fun onFailed(e: Exception) = failed(s, "registerHvacRecirculationState")(e)
                }
                api.registerHvacRecirculationState(area, l)
                registered(s) { api.unregisterHvacRecirculationState(area, l) }
            }
        }, capabilityFailed(s, "checkHvacRecirculationCapabilities"))
    }

    private fun watchHvacAuto(v: Vehicle, gen: Int) {
        val s = VehicleSignal.HVAC_AUTO
        val api = v.getHVAC()
        api.checkHvacAutoCapabilities({ config: CapabilityConfig, areas: List<SeatArea> ->
            val area = pick(areas, { it.values }, SeatValue.ROW_1_LEFT)
            onCapability(gen, s, config, "areas=${areas.map { it.values.toList() }} use=${area?.values?.toList()}", area) {
                api.getHvacAutoState(area!!, { publish(s, it, "get") }, failed(s, "getHvacAutoState"))
                val l = object : HvacAutoStateListener {
                    override fun onHvacAutoStateUpdated(state: Boolean?) = publish(s, state, "live")
                    override fun onFailed(e: Exception) = failed(s, "registerHvacAutoState")(e)
                }
                api.registerHvacAutoState(area, l)
                registered(s) { api.unregisterHvacAutoState(area, l) }
            }
        }, capabilityFailed(s, "checkHvacAutoCapabilities"))
    }

    private fun watchFanSpeed(v: Vehicle, gen: Int) {
        val s = VehicleSignal.FAN_SPEED
        val api = v.getHVAC()
        api.checkHvacFanSpeedCapabilities({ config: CapabilityConfig, areas: Map<SeatArea, AreaBounds<Int>> ->
            val area = pick(areas.keys, { it.values }, SeatValue.ROW_1_LEFT)
            onCapability(gen, s, config, "areas=${areas.describe { it.values }} use=${area?.values?.toList()} bounds=${areas[area]?.range()}", area) {
                api.getHvacFanSpeed(area!!, { publish(s, it, "get") }, failed(s, "getHvacFanSpeed"))
                val l = object : HvacFanSpeedListener {
                    override fun onHvacFanSpeedUpdated(speed: Int?) = publish(s, speed, "live")
                    override fun onFailed(e: Exception) = failed(s, "registerHvacFanSpeed")(e)
                }
                api.registerHvacFanSpeed(area, l)
                registered(s) { api.unregisterHvacFanSpeed(area, l) }
            }
        }, capabilityFailed(s, "checkHvacFanSpeedCapabilities"))
    }

    private fun watchSeatHeat(v: Vehicle, gen: Int) {
        val s = VehicleSignal.SEAT_HEAT
        val api = v.getSeat()
        api.checkSeatTemperatureCapabilities({ config: CapabilityConfig, areas: Map<SeatArea, AreaBounds<Int>> ->
            val area = pick(areas.keys, { it.values }, SeatValue.ROW_1_LEFT)
            onCapability(gen, s, config, "areas=${areas.describe { it.values }} use=${area?.values?.toList()} bounds=${areas[area]?.range()}", area) {
                api.getHvacSeatTemperature(area!!, { publish(s, it, "get") }, failed(s, "getHvacSeatTemperature"))
                val l = object : HvacSeatTemperatureListener {
                    override fun onHvacSeatTemperatureUpdated(level: Int?) = publish(s, level, "live")
                    override fun onFailed(e: Exception) = failed(s, "registerHvacSeatTemperature")(e)
                }
                api.registerHvacSeatTemperature(area, l)
                registered(s) { api.unregisterHvacSeatTemperature(area, l) }
            }
        }, capabilityFailed(s, "checkSeatTemperatureCapabilities"))
    }

    private fun watchWindowLock(v: Vehicle, gen: Int) {
        val s = VehicleSignal.WINDOW_LOCK
        val api = v.getWindow()
        api.checkWindowLockCapabilities({ config: CapabilityConfig, areas: List<WindowArea> ->
            // 단독 ROW_2_LEFT Area로 구독하면 묶음 Area(1360) 이벤트가 걸러져 안 옴 (10-27 로그: onChangeEvent areaId=1360) → 가장 넓은 Area
            val area = areas.filter { WindowValue.ROW_2_LEFT in it.values }.maxByOrNull { it.values.size }
            onCapability(gen, s, config, "areas=${areas.map { it.values.toList() }} use=${area?.values?.toList()}", area) {
                api.getCurrentWindowLockState(area!!, { publish(s, it, "get") }, failed(s, "getCurrentWindowLockState"))
                val l = object : WindowLockStateListener {
                    override fun onWindowLockStateUpdated(state: Boolean?) = publish(s, state, "live")
                    override fun onFailed(e: Exception) = failed(s, "registerWindowLockState")(e)
                }
                api.registerWindowLockState(area, l)
                registered(s) { api.unregisterWindowLockState(area, l) }
            }
        }, capabilityFailed(s, "checkWindowLockCapabilities"))
    }

    private fun watchCabinLight(v: Vehicle, gen: Int) {
        val s = VehicleSignal.CABIN_LIGHT
        val api = v.getLight()
        api.checkCabinLightStateCapabilities({ config: CapabilityConfig, values: List<LightState> ->
            onCapability(gen, s, config, "values=$values") {
                api.getCurrentCabinLightState({ publish(s, it, "get") }, failed(s, "getCurrentCabinLightState"))
                val l = object : LightStateListener {
                    override fun onLightStateUpdated(state: LightState?) = publish(s, state, "live")
                    override fun onFailed(e: Exception) = failed(s, "registerCabinLightState")(e)
                }
                api.registerCabinLightState(l)
                registered(s) { api.unregisterCabinLightState(l) }
            }
        }, capabilityFailed(s, "checkCabinLightStateCapabilities"))
    }

    private fun watchReadingLight(v: Vehicle, gen: Int) {
        val s = VehicleSignal.READING_LIGHT
        val api = v.getLight()
        api.checkReadingLightStateCapabilities({ config: CapabilityConfig, areas: Map<SeatArea, List<LightState>> ->
            val area = pick(areas.keys, { it.values }, SeatValue.ROW_2_LEFT)
            onCapability(gen, s, config, "areas=${areas.describe { it.values }} use=${area?.values?.toList()}", area) {
                api.getCurrentReadingLightState(area!!, { publish(s, it, "get") }, failed(s, "getCurrentReadingLightState"))
                val l = object : LightStateListener {
                    override fun onLightStateUpdated(state: LightState?) = publish(s, state, "live")
                    override fun onFailed(e: Exception) = failed(s, "registerReadingLightState")(e)
                }
                api.registerReadingLightState(area, l)
                registered(s) { api.unregisterReadingLightState(area, l) }
            }
        }, capabilityFailed(s, "checkReadingLightStateCapabilities"))
    }

    private fun watchDriverWindow(v: Vehicle, gen: Int) {
        val s = VehicleSignal.DRIVER_WINDOW
        val api = v.getWindow()
        api.checkWindowPositionCapabilities({ config: CapabilityConfig, areas: Map<WindowArea, AreaBounds<Int>> ->
            val area = pick(areas.keys, { it.values }, WindowValue.ROW_1_LEFT)
            onCapability(gen, s, config, "areas=${areas.describe { it.values }} use=${area?.values?.toList()} bounds=${areas[area]?.range()}", area) {
                api.getCurrentWindowPosition(area!!, { publish(s, it, "get") }, failed(s, "getCurrentWindowPosition"))
                val l = object : WindowPositionListener {
                    override fun onWindowPositionUpdated(position: Int?) = publish(s, position, "live")
                    override fun onFailed(e: Exception) = failed(s, "registerWindowPosition")(e)
                }
                api.registerWindowPosition(area, l)
                registered(s) { api.unregisterWindowPosition(area, l) }
            }
        }, capabilityFailed(s, "checkWindowPositionCapabilities"))
    }

    private fun watchHvacAc(v: Vehicle, gen: Int) {
        val s = VehicleSignal.HVAC_AC
        val api = v.getHVAC()
        api.checkHvacAcCapabilities({ config: CapabilityConfig, areas: List<SeatArea> ->
            val area = pick(areas, { it.values }, SeatValue.ROW_1_LEFT)
            onCapability(gen, s, config, "areas=${areas.map { it.values.toList() }} use=${area?.values?.toList()}", area) {
                api.getHvacAcState(area!!, { publish(s, it, "get") }, failed(s, "getHvacAcState"))
                val l = object : HvacAcStateListener {
                    override fun onHvacAcStateUpdated(state: Boolean?) = publish(s, state, "live")
                    override fun onFailed(e: Exception) = failed(s, "registerHvacAcState")(e)
                }
                api.registerHvacAcState(area, l)
                registered(s) { api.unregisterHvacAcState(area, l) }
            }
        }, capabilityFailed(s, "checkHvacAcCapabilities"))
    }

    private fun watchDriverTemperature(v: Vehicle, gen: Int) {
        val s = VehicleSignal.DRIVER_TEMPERATURE
        val api = v.getHVAC()
        api.checkHvacTemperatureCapabilities(
            { config: CapabilityConfig, areas: Map<SeatArea, AreaBounds<Float>>, _: HvacTemperatureConfigArray? ->
                val area = pick(areas.keys, { it.values }, SeatValue.ROW_1_LEFT)
                onCapability(gen, s, config, "areas=${areas.describe { it.values }} use=${area?.values?.toList()} bounds=${areas[area]?.range()}", area) {
                    api.getHvacTemperature(area!!, TemperatureUnit.CELSIUS, { publish(s, it, "get") }, failed(s, "getHvacTemperature"))
                    val l = object : HvacTemperatureListener {
                        override fun onHvacTemperatureUpdated(temperature: Float?) = publish(s, temperature, "live")
                        override fun onFailed(e: Exception) = failed(s, "registerHvacTemperature")(e)
                    }
                    api.registerHvacTemperature(area, l)
                    registered(s) { api.unregisterHvacTemperature(area, l) }
                }
            },
            capabilityFailed(s, "checkHvacTemperatureCapabilities"),
        )
    }

    private fun watchSteeringHeat(v: Vehicle, gen: Int) {
        val s = VehicleSignal.STEERING_HEAT
        val api = v.getSteeringWheel()
        api.checkHvacSteeringWheelHeatCapabilities({ config: CapabilityConfig, bounds: AreaBounds<Int> ->
            onCapability(gen, s, config, "bounds=${bounds.range()}") {
                api.getHvacSteeringWheelHeatState({ publish(s, it, "get") }, failed(s, "getHvacSteeringWheelHeatState"))
                val l = object : HvacSteeringWheelHeatStateListener {
                    override fun onHvacSteeringWheelHeatStateUpdated(state: Int?) = publish(s, state, "live")
                    override fun onFailed(e: Exception) = failed(s, "registerHvacSteeringWheelHeatState")(e)
                }
                api.registerHvacSteeringWheelHeatState(l)
                registered(s) { api.unregisterHvacSteeringWheelHeatState(l) }
            }
        }, capabilityFailed(s, "checkHvacSteeringWheelHeatCapabilities"))
    }

    private fun watchDoorLock(v: Vehicle, gen: Int) {
        val s = VehicleSignal.DOOR_LOCK
        val api = v.getDoor()
        api.checkDoorLockCapabilities({ config: CapabilityConfig, areas: List<DoorArea> ->
            // 실측: 4도어 묶음 Area 하나 + HOOD만 지원 (개별 도어 Area는 "area ID not supported")
            val area = pick(areas, { it.values }, DoorValue.ROW_1_LEFT)
            onCapability(gen, s, config, "areas=${areas.map { it.values.toList() }} use=${area?.values?.toList()}", area) {
                api.getDoorLockState(area!!, { publish(s, it, "get") }, failed(s, "getDoorLockState"))
                val l = object : DoorLockStateListener {
                    override fun onDoorLockStateUpdated(state: Boolean?) = publish(s, state, "live")
                    override fun onFailed(e: Exception) = failed(s, "registerDoorLockState")(e)
                }
                api.registerDoorLockState(area, l)
                registered(s) { api.unregisterDoorLockState(area, l) }
            }
        }, capabilityFailed(s, "checkDoorLockCapabilities"))
    }

    /** 트렁크·운전석 문·보닛은 같은 capability(DoorPosition)를 공유 → 한 번 확인하고 Area별로 나눠 등록. */
    private fun watchDoorPosition(v: Vehicle, gen: Int) {
        val api = v.getDoor()
        val targets = listOf(VehicleSignal.TRUNK to DoorValue.REAR, VehicleSignal.DRIVER_DOOR to DoorValue.ROW_1_LEFT, VehicleSignal.HOOD to DoorValue.HOOD)
        api.checkDoorPositionCapabilities({ config: CapabilityConfig, areas: Map<DoorArea, AreaBounds<Int>> ->
            for ((s, value) in targets) {
                val area = pick(areas.keys, { it.values }, value)
                onCapability(gen, s, config, "areas=${areas.describe { it.values }} use=${area?.values?.toList()} bounds=${areas[area]?.range()}", area) {
                    api.getCurrentDoorPosition(area!!, { publish(s, it, "get") }, failed(s, "getCurrentDoorPosition"))
                    val l = object : DoorPositionListener {
                        override fun onPositionUpdated(pos: Int?) = publish(s, pos, "live")
                        override fun onFailed(e: Exception) = failed(s, "registerDoorPosition")(e)
                    }
                    api.registerDoorPosition(area, l)
                    registered(s) { api.unregisterDoorPosition(area, l) }
                }
            }
        }) { e -> targets.forEach { (s, _) -> capabilityFailed(s, "checkDoorPositionCapabilities")(e) } }
    }

    private fun watchDrivingMode(v: Vehicle, gen: Int) {
        val s = VehicleSignal.DRIVING_MODE
        val api = v.getDrivingMode()
        api.checkDrivingModeStateCapabilities({ config: CapabilityConfig ->
            onCapability(gen, s, config, "") {
                api.getCurrentDrivingModeState({ publish(s, it, "get") }, failed(s, "getCurrentDrivingModeState"))
                val l = object : DrivingModeStateListener {
                    override fun onDrivingModeStateUpdated(state: DrivingModeType?) = publish(s, state, "live")
                    override fun onFailed(e: Exception) = failed(s, "registerDrivingModeState")(e)
                }
                api.registerDrivingModeState(l)
                registered(s) { api.unregisterDrivingModeState(l) }
            }
        }, capabilityFailed(s, "checkDrivingModeStateCapabilities"))
    }

    // ---- 공통 처리 --------------------------------------------------------------------------

    /**
     * capability 성공 콜백 공통 처리. 읽기 권한이 없거나(accessType) 필요한 Area가 없으면 '지원 안 됨'으로 끝낸다.
     * [requiredArea]를 넘기는 신호는 null이면 미지원 — Area 없는 신호는 생략.
     */
    private fun onCapability(
        gen: Int,
        s: VehicleSignal,
        config: CapabilityConfig,
        detail: String,
        requiredArea: Any? = Unit,
        subscribe: () -> Unit,
    ) {
        if (gen != generation) return
        AppLog.i(TAG, "CAP ${s.name} accessType=${config.accessType} $detail")
        val readable = config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE
        when {
            !readable -> unsupported(s, "읽기 불가 (accessType=${config.accessType})")
            requiredArea == null -> unsupported(s, "필요한 Area가 capability 목록에 없음 ($detail)")
            else -> {
                update(s) { it.copy(support = Support.Supported("accessType=${config.accessType} $detail".trim())) }
                try {
                    subscribe()
                    AppLog.i(TAG, "REG ${s.name} (${s.api})")
                } catch (e: Exception) {
                    // register 자체가 던지는 경우 (권한 등) — 지원 여부는 capability 기준 유지, 오류만 기록
                    AppLog.e(TAG, "REG ${s.name} threw ${VehicleErrors.describe(e)}", e)
                    update(s) { it.copy(lastError = "등록 실패: ${VehicleErrors.describe(e)}") }
                }
            }
        }
    }

    private fun registered(s: VehicleSignal, unregister: () -> Unit) {
        unregisters += s to unregister
    }

    private fun publish(s: VehicleSignal, value: Any?, source: String) {
        val now = System.currentTimeMillis()
        val before = _state.value[s].value
        val changed = before != value || _state.value[s].receiveCount == 0
        // 주기 콜백은 같은 값이 1~3초마다 반복됨 (§7-6) → 바뀔 때만 로그
        if (changed) AppLog.i(TAG, "VAL ${s.name} = $value ($source)")
        update(s) {
            it.copy(
                value = value,
                source = source,
                changedAt = if (before != value || it.receiveCount == 0) now else it.changedAt,
                receivedAt = now,
                receiveCount = it.receiveCount + 1,
            )
        }
        if (changed && !_changes.tryEmit(SignalChange(s, before, value, source, now))) {
            AppLog.w(TAG, "change buffer full, dropped ${s.name} $before→$value")
        }
    }

    private fun failed(s: VehicleSignal, api: String): (Exception?) -> Unit = { e ->
        val reason = if (e == null) "onFailed(null)" else VehicleErrors.describe(e)
        AppLog.e(TAG, "FAIL ${s.name} api=$api $reason", e)
        val code = e?.let { VehicleErrors.errorCodeOf(it) }
        if (code in NOT_SUPPORTED) unsupported(s, "$api: $reason") else update(s) { it.copy(lastError = "$api: $reason") }
    }

    private fun capabilityFailed(s: VehicleSignal, api: String): (Exception) -> Unit = { e ->
        AppLog.w(TAG, "CAP ${s.name} failed api=$api ${VehicleErrors.describe(e)}")
        unsupported(s, "$api: ${VehicleErrors.describe(e)}")
    }

    private fun unsupported(s: VehicleSignal, reason: String) {
        AppLog.w(TAG, "UNSUPPORTED ${s.name}: $reason")
        update(s) { it.copy(support = Support.Unsupported(reason)) }
    }

    /** capability 콜백이 끝내 안 오면 '확인 중'에 영원히 머무른다 → 시간 초과를 미지원 사유로 기록. */
    private fun armCapabilityTimeout(s: VehicleSignal, gen: Int) {
        scope.launch {
            delay(CAPABILITY_TIMEOUT_MS)
            if (gen == generation && _state.value[s].support == Support.Checking) {
                unsupported(s, "capability 응답 없음 (${CAPABILITY_TIMEOUT_MS / 1000}초)")
            }
        }
    }

    private fun update(s: VehicleSignal, f: (SignalState) -> SignalState) {
        _state.update { st -> st.copy(signals = st.signals + (s to f(st[s]))) }
    }

    companion object {
        private const val TAG = "Vehicle"
        private const val CAPABILITY_TIMEOUT_MS = 5_000L
        private val AAOS_SIGNALS = linkedMapOf(
            VehicleSignal.FUEL_DOOR to VehiclePropertyIds.FUEL_DOOR_OPEN,
            VehicleSignal.CHARGE_PORT to VehiclePropertyIds.EV_CHARGE_PORT_OPEN,
        )
        private val NOT_SUPPORTED = setOf(
            ErrorCode.ERROR_NOT_SUPPORTED_PROPERTY,
            ErrorCode.ERROR_NOT_SUPPORTED_AREA,
            ErrorCode.ERROR_NOT_SUPPORTED_VALUE_TYPE,
        )

        /** [target] 값을 포함하는 Area 중 가장 좁은 것 (운전석 단독 Area가 있으면 그것, 없으면 묶음 Area). */
        internal fun <A> pick(areas: Collection<A>, values: (A) -> Array<out Any>, target: Any): A? =
            areas.filter { target in values(it) }.minByOrNull { values(it).size }

        private fun <A> Map<A, *>.describe(values: (A) -> Array<out Any>): String = keys.map { values(it).toList() }.toString()

        private fun AreaBounds<*>.range(): String = "$minValue..$maxValue"
    }
}
