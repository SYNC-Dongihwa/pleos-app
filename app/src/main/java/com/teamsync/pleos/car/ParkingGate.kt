package com.teamsync.pleos.car

import android.car.VehicleGear

/** 튜토리얼 진행 가능 여부. 주차 브레이크는 판정에 쓰지 않고 표시만 한다 (CLAUDE.md §3: 에뮬레이터에서 항상 false). */
sealed interface GateState {
    data object Parked : GateState

    /** 기어가 P가 아니거나 주행 규제가 걸림 → 튜토리얼 일시정지. */
    data class NotParked(val reason: String) : GateState

    /** 기어를 아직 모르거나 읽을 수 없음 → 시작하지 않는다 (안전 쪽으로). */
    data class Unknown(val reason: String) : GateState
}

object ParkingGate {

    fun evaluate(s: AaosState): GateState {
        if (!s.connected) return GateState.Unknown(s.error ?: "차량 서비스 연결 대기 중")
        val gear = s.gear.value
            ?: return GateState.Unknown(s.gear.error?.let { "기어를 읽을 수 없음: $it" } ?: "기어 값 대기 중")
        if (gear != VehicleGear.GEAR_PARK) return GateState.NotParked("기어 ${gearName(gear)}")
        if (s.requiresDistractionOptimization == true) {
            return GateState.NotParked("주행 규제 중 (0x${s.activeRestrictions?.toString(16)})")
        }
        return GateState.Parked
    }
}

/** `android.car.VehicleGear` 상수 → 표시 이름. */
fun gearName(gear: Int?): String = when (gear) {
    null -> "?"
    VehicleGear.GEAR_PARK -> "P"
    VehicleGear.GEAR_REVERSE -> "R"
    VehicleGear.GEAR_NEUTRAL -> "N"
    VehicleGear.GEAR_DRIVE -> "D"
    VehicleGear.GEAR_UNKNOWN -> "UNKNOWN"
    else -> "0x${gear.toString(16)}"
}
