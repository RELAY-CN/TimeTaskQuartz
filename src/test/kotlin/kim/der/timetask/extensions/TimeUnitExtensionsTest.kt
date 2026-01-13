/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.extensions

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * TimeUnitExtensions 的测试类
 *
 * @author Dr (dr@der.kim)
 */
class TimeUnitExtensionsTest {

    // ==================== Int 时间单位扩展测试 ====================

    @Test
    fun testIntSecondsConvertsCorrectly() {
        assertEquals(1000L, 1.seconds)
        assertEquals(5000L, 5.seconds)
        assertEquals(60000L, 60.seconds)
        assertEquals(0L, 0.seconds)
    }

    @Test
    fun testIntMinutesConvertsCorrectly() {
        assertEquals(60000L, 1.minutes)
        assertEquals(300000L, 5.minutes)
        assertEquals(3600000L, 60.minutes)
        assertEquals(0L, 0.minutes)
    }

    @Test
    fun testIntHoursConvertsCorrectly() {
        assertEquals(3600000L, 1.hours)
        assertEquals(7200000L, 2.hours)
        assertEquals(86400000L, 24.hours)
        assertEquals(0L, 0.hours)
    }

    @Test
    fun testIntDaysConvertsCorrectly() {
        assertEquals(86400000L, 1.days)
        assertEquals(172800000L, 2.days)
        assertEquals(604800000L, 7.days)
        assertEquals(0L, 0.days)
    }

    @Test
    fun testIntMillisReturnsSameValueAsLong() {
        assertEquals(100L, 100.millis)
        assertEquals(500L, 500.millis)
        assertEquals(0L, 0.millis)
    }

    // ==================== Long 时间单位扩展测试 ====================

    @Test
    fun testLongSecondsConvertsCorrectly() {
        assertEquals(1000L, 1L.seconds)
        assertEquals(5000L, 5L.seconds)
        assertEquals(TimeUnit.SECONDS.toMillis(100), 100L.seconds)
    }

    @Test
    fun testLongMinutesConvertsCorrectly() {
        assertEquals(60000L, 1L.minutes)
        assertEquals(300000L, 5L.minutes)
        assertEquals(TimeUnit.MINUTES.toMillis(100), 100L.minutes)
    }

    @Test
    fun testLongHoursConvertsCorrectly() {
        assertEquals(3600000L, 1L.hours)
        assertEquals(7200000L, 2L.hours)
        assertEquals(TimeUnit.HOURS.toMillis(48), 48L.hours)
    }

    @Test
    fun testLongDaysConvertsCorrectly() {
        assertEquals(86400000L, 1L.days)
        assertEquals(172800000L, 2L.days)
        assertEquals(TimeUnit.DAYS.toMillis(30), 30L.days)
    }

    @Test
    fun testLongMillisReturnsSameValue() {
        assertEquals(100L, 100L.millis)
        assertEquals(999L, 999L.millis)
    }

    // ==================== 时间格式化测试 ====================

    @Test
    fun testToReadableTimeFormatsMillisecondsOnly() {
        assertEquals("500ms", 500L.toReadableTime())
        assertEquals("1ms", 1L.toReadableTime())
        assertEquals("999ms", 999L.toReadableTime())
    }

    @Test
    fun testToReadableTimeFormatsSeconds() {
        assertEquals("1s", 1000L.toReadableTime())
        assertEquals("5s", 5000L.toReadableTime())
        assertEquals("59s", 59000L.toReadableTime())
    }

    @Test
    fun testToReadableTimeFormatsMinutes() {
        assertEquals("1m", 60000L.toReadableTime())
        assertEquals("5m", 300000L.toReadableTime())
        assertEquals("1m 30s", 90000L.toReadableTime())
    }

    @Test
    fun testToReadableTimeFormatsHours() {
        assertEquals("1h", 3600000L.toReadableTime())
        assertEquals("2h 30m", 9000000L.toReadableTime())
        assertEquals("1h 1m 1s", 3661000L.toReadableTime())
    }

    @Test
    fun testToReadableTimeFormatsDays() {
        assertEquals("1d", 86400000L.toReadableTime())
        assertEquals("1d 12h", 129600000L.toReadableTime())
        assertEquals("7d", 604800000L.toReadableTime())
    }

    @Test
    fun testToReadableTimeHandlesZero() {
        assertEquals("0ms", 0L.toReadableTime())
    }

    @Test
    fun testToReadableTimeHandlesNegative() {
        assertEquals("0ms", (-100L).toReadableTime())
    }

    // ==================== 时间转换测试 ====================

    @Test
    fun testToSecondsConvertsCorrectly() {
        assertEquals(1L, 1000L.toSeconds())
        assertEquals(60L, 60000L.toSeconds())
        assertEquals(0L, 500L.toSeconds())
    }

    @Test
    fun testToMinutesConvertsCorrectly() {
        assertEquals(1L, 60000L.toMinutes())
        assertEquals(5L, 300000L.toMinutes())
        assertEquals(0L, 30000L.toMinutes())
    }

    @Test
    fun testToHoursConvertsCorrectly() {
        assertEquals(1L, 3600000L.toHours())
        assertEquals(24L, 86400000L.toHours())
        assertEquals(0L, 1800000L.toHours())
    }

    @Test
    fun testToDaysConvertsCorrectly() {
        assertEquals(1L, 86400000L.toDays())
        assertEquals(7L, 604800000L.toDays())
        assertEquals(0L, 43200000L.toDays())
    }

    // ==================== 时间计算测试 ====================

    @Test
    fun testFromNowReturnsFutureTimestamp() {
        val before = System.currentTimeMillis()
        val future = 5.seconds.fromNow()
        val after = System.currentTimeMillis()

        assertTrue(future >= before + 5000)
        assertTrue(future <= after + 5000)
    }

    @Test
    fun testAgoReturnsPastTimestamp() {
        val before = System.currentTimeMillis()
        val past = 5.seconds.ago()
        val after = System.currentTimeMillis()

        assertTrue(past <= before - 5000)
        assertTrue(past >= after - 5000 - 100) // 允许100ms误差
    }

    @Test
    fun testFromNowWithZeroReturnsCurrentTime() {
        val now = System.currentTimeMillis()
        val result = 0L.fromNow()

        assertTrue(result >= now)
        assertTrue(result <= now + 100)
    }

    @Test
    fun testAgoWithZeroReturnsCurrentTime() {
        val now = System.currentTimeMillis()
        val result = 0L.ago()

        assertTrue(result <= now)
        assertTrue(result >= now - 100)
    }

    // ==================== 组合使用测试 ====================

    @Test
    fun testTimeUnitsCanBeCombined() {
        val total = 1.hours + 30.minutes + 45.seconds
        assertEquals(5445000L, total)
    }

    @Test
    fun testTimeUnitsWorkWithTimeUnit() {
        assertEquals(TimeUnit.SECONDS.toMillis(5), 5.seconds)
        assertEquals(TimeUnit.MINUTES.toMillis(10), 10.minutes)
        assertEquals(TimeUnit.HOURS.toMillis(2), 2.hours)
        assertEquals(TimeUnit.DAYS.toMillis(1), 1.days)
    }
}
