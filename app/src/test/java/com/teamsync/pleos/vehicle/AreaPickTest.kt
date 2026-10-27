package com.teamsync.pleos.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [VehicleStateRepository.pick]: capability가 준 Area 중 대상 좌석을 포함하는 가장 좁은 것. */
class AreaPickTest {

    private fun pick(areas: List<List<String>>, target: String) =
        VehicleStateRepository.pick(areas, { it.toTypedArray() }, target)

    @Test fun `단독 Area가 있으면 그것을 고른다`() {
        val areas = listOf(listOf("ROW_1_LEFT", "ROW_1_RIGHT"), listOf("ROW_1_LEFT"))
        assertEquals(listOf("ROW_1_LEFT"), pick(areas, "ROW_1_LEFT"))
    }

    @Test fun `묶음 Area뿐이면 묶음을 고른다 (도어 잠금 실측 형태)`() {
        val areas = listOf(listOf("HOOD"), listOf("ROW_1_LEFT", "ROW_1_RIGHT", "ROW_2_LEFT", "ROW_2_RIGHT"))
        assertEquals(areas[1], pick(areas, "ROW_1_LEFT"))
    }

    @Test fun `대상이 없으면 null`() {
        assertNull(pick(listOf(listOf("HOOD")), "REAR"))
    }
}
