/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.extensions

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * CronExtensions 的测试类
 *
 * @author Dr (dr@der.kim)
 */
class CronExtensionsTest {

    // ==================== CronExpressions 常量测试 ====================

    @Test
    fun testCronExpressionsSecondsConstantsAreValid() {
        assertTrue(isValidCron(CronExpressions.EVERY_SECOND))
        assertTrue(isValidCron(CronExpressions.EVERY_5_SECONDS))
        assertTrue(isValidCron(CronExpressions.EVERY_10_SECONDS))
        assertTrue(isValidCron(CronExpressions.EVERY_15_SECONDS))
        assertTrue(isValidCron(CronExpressions.EVERY_30_SECONDS))
    }

    @Test
    fun testCronExpressionsMinutesConstantsAreValid() {
        assertTrue(isValidCron(CronExpressions.EVERY_MINUTE))
        assertTrue(isValidCron(CronExpressions.EVERY_5_MINUTES))
        assertTrue(isValidCron(CronExpressions.EVERY_10_MINUTES))
        assertTrue(isValidCron(CronExpressions.EVERY_15_MINUTES))
        assertTrue(isValidCron(CronExpressions.EVERY_30_MINUTES))
    }

    @Test
    fun testCronExpressionsHoursConstantsAreValid() {
        assertTrue(isValidCron(CronExpressions.EVERY_HOUR))
        assertTrue(isValidCron(CronExpressions.EVERY_2_HOURS))
        assertTrue(isValidCron(CronExpressions.EVERY_4_HOURS))
        assertTrue(isValidCron(CronExpressions.EVERY_6_HOURS))
        assertTrue(isValidCron(CronExpressions.EVERY_12_HOURS))
    }

    @Test
    fun testCronExpressionsDailyConstantsAreValid() {
        assertTrue(isValidCron(CronExpressions.DAILY_MIDNIGHT))
        assertTrue(isValidCron(CronExpressions.DAILY_6AM))
        assertTrue(isValidCron(CronExpressions.DAILY_8AM))
        assertTrue(isValidCron(CronExpressions.DAILY_9AM))
        assertTrue(isValidCron(CronExpressions.DAILY_NOON))
        assertTrue(isValidCron(CronExpressions.DAILY_6PM))
        assertTrue(isValidCron(CronExpressions.DAILY_9PM))
        assertTrue(isValidCron(CronExpressions.DAILY_11PM))
    }

    @Test
    fun testCronExpressionsWeeklyConstantsAreValid() {
        assertTrue(isValidCron(CronExpressions.WEEKLY_MONDAY))
        assertTrue(isValidCron(CronExpressions.WEEKLY_TUESDAY))
        assertTrue(isValidCron(CronExpressions.WEEKLY_WEDNESDAY))
        assertTrue(isValidCron(CronExpressions.WEEKLY_THURSDAY))
        assertTrue(isValidCron(CronExpressions.WEEKLY_FRIDAY))
        assertTrue(isValidCron(CronExpressions.WEEKLY_SATURDAY))
        assertTrue(isValidCron(CronExpressions.WEEKLY_SUNDAY))
        assertTrue(isValidCron(CronExpressions.WEEKDAYS_9AM))
        assertTrue(isValidCron(CronExpressions.WEEKENDS_10AM))
    }

    @Test
    fun testCronExpressionsMonthlyConstantsAreValid() {
        assertTrue(isValidCron(CronExpressions.MONTHLY_FIRST_DAY))
        assertTrue(isValidCron(CronExpressions.MONTHLY_15TH))
        assertTrue(isValidCron(CronExpressions.MONTHLY_LAST_DAY))
    }

    @Test
    fun testCronExpressionsYearlyConstantsAreValid() {
        assertTrue(isValidCron(CronExpressions.YEARLY_JANUARY_FIRST))
        assertTrue(isValidCron(CronExpressions.YEARLY_JULY_FIRST))
        assertTrue(isValidCron(CronExpressions.YEARLY_DECEMBER_31ST))
    }

    // ==================== CronBuilder 测试 ====================

    @Test
    fun testEverySecondsGeneratesValidCron() {
        assertEquals("0/5 * * * * ?", CronBuilder.everySeconds(5))
        assertEquals("0/30 * * * * ?", CronBuilder.everySeconds(30))
        assertTrue(isValidCron(CronBuilder.everySeconds(15)))
    }

    @Test
    fun testEverySecondsWithInvalidValueThrowsException() {
        assertThrows<IllegalArgumentException> { CronBuilder.everySeconds(0) }
        assertThrows<IllegalArgumentException> { CronBuilder.everySeconds(60) }
        assertThrows<IllegalArgumentException> { CronBuilder.everySeconds(-1) }
    }

    @Test
    fun testEveryMinutesGeneratesValidCron() {
        assertEquals("0 */5 * * * ?", CronBuilder.everyMinutes(5))
        assertEquals("0 */15 * * * ?", CronBuilder.everyMinutes(15))
        assertTrue(isValidCron(CronBuilder.everyMinutes(30)))
    }

    @Test
    fun testEveryMinutesWithInvalidValueThrowsException() {
        assertThrows<IllegalArgumentException> { CronBuilder.everyMinutes(0) }
        assertThrows<IllegalArgumentException> { CronBuilder.everyMinutes(60) }
    }

    @Test
    fun testEveryHoursGeneratesValidCron() {
        assertEquals("0 0 */2 * * ?", CronBuilder.everyHours(2))
        assertEquals("0 0 */6 * * ?", CronBuilder.everyHours(6))
        assertTrue(isValidCron(CronBuilder.everyHours(12)))
    }

    @Test
    fun testEveryHoursWithInvalidValueThrowsException() {
        assertThrows<IllegalArgumentException> { CronBuilder.everyHours(0) }
        assertThrows<IllegalArgumentException> { CronBuilder.everyHours(24) }
    }

    @Test
    fun testDailyAtGeneratesValidCron() {
        assertEquals("0 0 8 * * ?", CronBuilder.dailyAt(8))
        assertEquals("0 30 9 * * ?", CronBuilder.dailyAt(9, 30))
        assertEquals("15 30 14 * * ?", CronBuilder.dailyAt(14, 30, 15))
        assertTrue(isValidCron(CronBuilder.dailyAt(23, 59, 59)))
    }

    @Test
    fun testDailyAtWithInvalidValuesThrowsException() {
        assertThrows<IllegalArgumentException> { CronBuilder.dailyAt(-1) }
        assertThrows<IllegalArgumentException> { CronBuilder.dailyAt(24) }
        assertThrows<IllegalArgumentException> { CronBuilder.dailyAt(12, -1) }
        assertThrows<IllegalArgumentException> { CronBuilder.dailyAt(12, 60) }
        assertThrows<IllegalArgumentException> { CronBuilder.dailyAt(12, 0, -1) }
        assertThrows<IllegalArgumentException> { CronBuilder.dailyAt(12, 0, 60) }
    }

    @Test
    fun testWeeklyAtWithDayOfWeekGeneratesValidCron() {
        assertEquals("0 0 9 ? * MON", CronBuilder.weeklyAt(CronBuilder.DayOfWeek.MONDAY, 9))
        assertEquals("0 30 10 ? * FRI", CronBuilder.weeklyAt(CronBuilder.DayOfWeek.FRIDAY, 10, 30))
        assertTrue(isValidCron(CronBuilder.weeklyAt(CronBuilder.DayOfWeek.SUNDAY, 8, 0, 0)))
    }

    @Test
    fun testWeeklyAtWithIntGeneratesValidCron() {
        assertEquals("0 0 9 ? * MON", CronBuilder.weeklyAt(2, 9)) // 2 = Monday
        assertEquals("0 0 10 ? * SUN", CronBuilder.weeklyAt(1, 10)) // 1 = Sunday
        assertEquals("0 0 11 ? * SAT", CronBuilder.weeklyAt(7, 11)) // 7 = Saturday
    }

    @Test
    fun testWeeklyAtWithInvalidDayThrowsException() {
        assertThrows<IllegalArgumentException> { CronBuilder.weeklyAt(0, 9) }
        assertThrows<IllegalArgumentException> { CronBuilder.weeklyAt(8, 9) }
    }

    @Test
    fun testMonthlyAtGeneratesValidCron() {
        assertEquals("0 0 0 1 * ?", CronBuilder.monthlyAt(1, 0))
        assertEquals("0 30 9 15 * ?", CronBuilder.monthlyAt(15, 9, 30))
        assertTrue(isValidCron(CronBuilder.monthlyAt(28, 12, 0, 0)))
    }

    @Test
    fun testMonthlyAtWithInvalidDayThrowsException() {
        assertThrows<IllegalArgumentException> { CronBuilder.monthlyAt(0, 9) }
        assertThrows<IllegalArgumentException> { CronBuilder.monthlyAt(32, 9) }
    }

    @Test
    fun testMonthlyLastDayGeneratesValidCron() {
        assertEquals("0 0 0 L * ?", CronBuilder.monthlyLastDay(0))
        assertEquals("0 30 23 L * ?", CronBuilder.monthlyLastDay(23, 30))
        assertTrue(isValidCron(CronBuilder.monthlyLastDay(12, 0, 0)))
    }

    @Test
    fun testYearlyAtGeneratesValidCron() {
        assertEquals("0 0 0 1 1 ?", CronBuilder.yearlyAt(1, 1, 0))
        assertEquals("0 0 12 25 12 ?", CronBuilder.yearlyAt(12, 25, 12))
        assertTrue(isValidCron(CronBuilder.yearlyAt(6, 15, 9, 30)))
    }

    @Test
    fun testYearlyAtWithInvalidValuesThrowsException() {
        assertThrows<IllegalArgumentException> { CronBuilder.yearlyAt(0, 1, 0) }
        assertThrows<IllegalArgumentException> { CronBuilder.yearlyAt(13, 1, 0) }
        assertThrows<IllegalArgumentException> { CronBuilder.yearlyAt(1, 0, 0) }
        assertThrows<IllegalArgumentException> { CronBuilder.yearlyAt(1, 32, 0) }
    }

    @Test
    fun testWeekdaysAtGeneratesValidCron() {
        assertEquals("0 0 9 ? * MON-FRI", CronBuilder.weekdaysAt(9))
        assertEquals("0 30 8 ? * MON-FRI", CronBuilder.weekdaysAt(8, 30))
        assertTrue(isValidCron(CronBuilder.weekdaysAt(17, 0, 0)))
    }

    @Test
    fun testWeekendsAtGeneratesValidCron() {
        assertEquals("0 0 10 ? * SAT,SUN", CronBuilder.weekendsAt(10))
        assertEquals("0 30 9 ? * SAT,SUN", CronBuilder.weekendsAt(9, 30))
        assertTrue(isValidCron(CronBuilder.weekendsAt(12, 0, 0)))
    }

    // ==================== 辅助函数测试 ====================

    @Test
    fun testIsValidCronReturnsTrueForValidExpressions() {
        assertTrue(isValidCron("0 0 12 * * ?"))
        assertTrue(isValidCron("0 15 10 ? * MON-FRI"))
        assertTrue(isValidCron("0 0/5 * * * ?"))
    }

    @Test
    fun testIsValidCronReturnsFalseForInvalidExpressions() {
        assertFalse(isValidCron("invalid"))
        assertFalse(isValidCron(""))
        assertFalse(isValidCron("0 0 25 * * ?")) // 25点无效
        assertFalse(isValidCron("0 60 12 * * ?")) // 60分无效
    }

    @Test
    fun testGetNextFireTimeReturnsFutureTimeForValidCron() {
        val nextTime = getNextFireTime(CronExpressions.EVERY_SECOND)
        assertNotNull(nextTime)
        assertTrue(nextTime!! > System.currentTimeMillis() - 1000)
    }

    @Test
    fun testGetNextFireTimeReturnsNullForInvalidCron() {
        val nextTime = getNextFireTime("invalid")
        assertNull(nextTime)
    }

    @Test
    fun testDescribeCronReturnsDescriptionForValidCron() {
        val desc = describeCron(CronExpressions.DAILY_NOON)
        assertNotNull(desc)
        assertTrue(desc.isNotEmpty())
    }

    @Test
    fun testDescribeCronReturnsOriginalForInvalidCron() {
        val desc = describeCron("invalid")
        assertEquals("invalid", desc)
    }
}
