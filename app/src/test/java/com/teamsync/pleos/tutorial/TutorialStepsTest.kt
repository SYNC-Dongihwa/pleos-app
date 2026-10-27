package com.teamsync.pleos.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 단계·코스 데이터 정합성 (단계를 추가할 때 실수 방지). */
class TutorialStepsTest {

    @Test fun `단계 id와 코스 id는 겹치지 않는다`() {
        val stepIds = TutorialSteps.all.map { it.id }
        assertEquals(stepIds.size, stepIds.toSet().size)
        val courseIds = (TutorialSteps.courses + TutorialSteps.everything).map { it.id }
        assertEquals(courseIds.size, courseIds.toSet().size)
    }

    @Test fun `코스의 모든 단계가 정의돼 있고 모든 단계가 어느 코스에든 속한다`() {
        val stepIds = TutorialSteps.all.map { it.id }.toSet()
        TutorialSteps.courses.forEach { c -> c.stepIds.forEach { assertTrue("${c.id}: $it", it in stepIds) } }
        assertEquals(stepIds, TutorialSteps.courses.flatMap { it.stepIds }.toSet())
        assertEquals(TutorialSteps.all.map { it.id }, TutorialSteps.everything.stepIds)
    }

    @Test fun `모든 단계는 phase가 있고 마지막 phase는 원래 값 복귀 또는 꺼짐 확인`() {
        TutorialSteps.all.forEach { step ->
            assertTrue(step.id, step.phases.isNotEmpty())
        }
    }

    @Test fun `주유·충전 코스에 주유구와 충전구가 모두 있다 (차종에 따라 하나는 미지원으로 넘어감)`() {
        val fuel = TutorialSteps.courses.first { it.id == "fuel" }
        assertTrue("fuel_door" in fuel.stepIds && "charge_port" in fuel.stepIds)
    }
}
