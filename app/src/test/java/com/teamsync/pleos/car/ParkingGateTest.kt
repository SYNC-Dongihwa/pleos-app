package com.teamsync.pleos.car

import android.car.VehicleGear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingGateTest {

    private val connectedPark = AaosState(
        connected = true,
        requiresDistractionOptimization = false,
        activeRestrictions = 0,
        gear = AaosProperty(VehicleGear.GEAR_PARK),
    )

    @Test fun `기어 P이고 규제 없으면 정차`() {
        assertEquals(GateState.Parked, ParkingGate.evaluate(connectedPark))
    }

    @Test fun `주차 브레이크가 false여도 정차로 본다 (표시만)`() {
        assertEquals(GateState.Parked, ParkingGate.evaluate(connectedPark.copy(parkingBrake = AaosProperty(false))))
    }

    @Test fun `기어 D면 일시정지`() {
        val gate = ParkingGate.evaluate(connectedPark.copy(gear = AaosProperty(VehicleGear.GEAR_DRIVE)))
        assertEquals(GateState.NotParked("기어 D"), gate)
    }

    @Test fun `기어 P라도 주행 규제가 걸리면 일시정지`() {
        val gate = ParkingGate.evaluate(connectedPark.copy(requiresDistractionOptimization = true, activeRestrictions = 0x50))
        assertTrue(gate is GateState.NotParked)
    }

    @Test fun `기어를 못 읽으면 시작하지 않는다`() {
        val gate = ParkingGate.evaluate(connectedPark.copy(gear = AaosProperty(error = "SecurityException: denied")))
        assertEquals(GateState.Unknown("기어를 읽을 수 없음: SecurityException: denied"), gate)
    }

    @Test fun `Car 서비스 미연결이면 알 수 없음`() {
        assertTrue(ParkingGate.evaluate(AaosState()) is GateState.Unknown)
    }
}
