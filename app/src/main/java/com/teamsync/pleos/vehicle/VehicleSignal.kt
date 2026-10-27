package com.teamsync.pleos.vehicle

/**
 * 튜토리얼이 구독하는 Vehicle SDK 신호. [api]는 로그·개발자 패널 표시용 (실제 호출은 [VehicleStateRepository]).
 * 신호를 추가하면 매니페스트 퍼미션과 CLAUDE.md §4·§5도 같이 고친다.
 */
enum class VehicleSignal(val label: String, val api: String) {
    LOW_BEAM("전조등(하향등)", "Light.LowBeamLightState"),
    HIGH_BEAM("상향등", "Light.HighBeamLightState"),
    TURN_SIGNAL("방향지시등", "TurnSignal.TurnSignalState"),
    FRONT_WIPER("앞 와이퍼", "Wiper.WiperState(FRONT_WINDSHIELD)"),
    DRIVER_WINDOW("운전석 창문", "Window.WindowPosition(ROW_1_LEFT)"),
    HVAC_AC("에어컨", "HVAC.HvacAcState"),
    DRIVER_TEMPERATURE("운전석 설정 온도", "HVAC.HvacTemperature(ROW_1_LEFT, ℃)"),
    STEERING_HEAT("핸들 열선", "SteeringWheel.HvacSteeringWheelHeatState"),
    DOOR_LOCK("도어 잠금", "Door.DoorLockState"),
    TRUNK("트렁크", "Door.DoorPosition(REAR)"),
    DRIVER_DOOR("운전석 문", "Door.DoorPosition(ROW_1_LEFT)"),
    DRIVING_MODE("주행 모드", "DrivingMode.DrivingModeState"),

    // 주유·충전 — Pleos SDK에 주유구 API가 없어 AAOS 표준 속성 사용 (문서: 표준 데이터는 AAOS Car API)
    FUEL_DOOR("주유구", "AAOS FUEL_DOOR_OPEN"),
    CHARGE_PORT("충전구", "AAOS EV_CHARGE_PORT_OPEN"),
    HOOD("보닛", "Door.DoorPosition(HOOD)"),

    // 비·김서림
    FRONT_DEFROSTER("앞유리 김서림 제거", "HVAC.HvacDefrosterState(FRONT_WINDSHIELD)"),
    REAR_DEFROSTER("뒷유리 열선", "HVAC.HvacDefrosterState(REAR_WINDSHIELD)"),
    REAR_WIPER("뒷유리 와이퍼", "Wiper.WiperState(REAR_WINDSHIELD)"),
    RECIRCULATION("내기 순환", "HVAC.HvacRecirculationState"),

    // 추위·공조
    HVAC_AUTO("오토 공조", "HVAC.HvacAutoState"),
    FAN_SPEED("바람 세기", "HVAC.HvacFanSpeed"),
    SEAT_HEAT("운전석 시트 열선", "Seat.HvacSeatTemperature(ROW_1_LEFT)"),

    // 아이·야간
    // 실측: 콘솔 Window Lock은 전체 창문 묶음 Area(1360)로 발행 → 뒷좌석을 포함하는 가장 넓은 Area를 구독 (실제 버튼도 동승석 창문 전체를 잠금)
    WINDOW_LOCK("창문 잠금", "Window.WindowLockState(전체 창문 묶음)"),
    CABIN_LIGHT("실내등", "Light.CabinLightState"),
    READING_LIGHT("뒷좌석 독서등", "Light.ReadingLightState(ROW_2_LEFT)"),
}

sealed interface Support {
    /** capability 응답 대기 중. */
    data object Checking : Support

    /** [detail] = capability 결과 요약 (accessType, 사용한 Area, 범위). */
    data class Supported(val detail: String) : Support

    data class Unsupported(val reason: String) : Support
}

/**
 * 한 신호의 현재 상태. [value]는 SDK가 준 값 그대로 (LightState·TurnSignalValue 같은 enum, Int, Boolean, Float).
 * 값을 아직 못 받았으면 null — 가짜 기본값을 채우지 않는다.
 */
data class SignalState(
    val support: Support = Support.Checking,
    val value: Any? = null,
    /** "get" 또는 "live". */
    val source: String? = null,
    /** 값이 마지막으로 바뀐 시각. */
    val changedAt: Long = 0,
    /** 콜백을 마지막으로 받은 시각 (같은 값 반복 포함). */
    val receivedAt: Long = 0,
    val receiveCount: Int = 0,
    val lastError: String? = null,
)

/**
 * 값이 바뀐 순간 하나. StateFlow는 빠른 연속 변화를 합쳐 버릴 수 있어서, 전이 판정용으로 따로 흘려보낸다.
 * 첫 값(기준값)은 [previous]=null.
 */
data class SignalChange(
    val signal: VehicleSignal,
    val previous: Any?,
    val value: Any?,
    /** "get" 또는 "live". 완료 판정은 "live"(콜백)만 쓴다. */
    val source: String,
    val time: Long,
)

sealed interface VehicleConnection {
    data object NotStarted : VehicleConnection
    data object Connected : VehicleConnection
    data class Failed(val reason: String) : VehicleConnection
}

data class VehicleState(
    val connection: VehicleConnection = VehicleConnection.NotStarted,
    val signals: Map<VehicleSignal, SignalState> = VehicleSignal.entries.associateWith { SignalState() },
) {
    operator fun get(signal: VehicleSignal): SignalState = signals[signal] ?: SignalState()
}
