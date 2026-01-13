/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.task

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * TimeTaskManage 的测试类
 *
 * @author Dr (dr@der.kim)
 */
class TimeTaskManageTest {
    private lateinit var taskManager: TimeTaskManage

    @BeforeEach
    fun setUp() {
        taskManager = TimeTaskManage()
        Thread.sleep(100)
    }

    @AfterEach
    fun tearDown() {
        if (taskManager.isRunning) {
            taskManager.shutdownNow()
        }
        Thread.sleep(100)
    }

    // ==================== 构造函数测试 ====================

    @Test
    fun testDefaultConstructorCreatesRunningScheduler() {
        assertTrue(taskManager.isRunning)
        assertFalse(taskManager.isStandby)
    }

    @Test
    fun testConstructorWithThreadPoolSizeCreatesScheduler() {
        val manager = TimeTaskManage(3)
        try {
            assertTrue(manager.isRunning)
        } finally {
            manager.shutdownNow()
        }
    }

    @Test
    fun testConstructorWithInvalidThreadPoolSizeThrowsException() {
        assertThrows<IllegalArgumentException> {
            TimeTaskManage(0)
        }
        assertThrows<IllegalArgumentException> {
            TimeTaskManage(101)
        }
    }

    @Test
    fun testConstructorWithCustomPropertiesCreatesScheduler() {
        val props = Properties().apply {
            setProperty("org.quartz.scheduler.instanceName", "TestScheduler")
            setProperty("org.quartz.threadPool.threadCount", "2")
            setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore")
        }
        val manager = TimeTaskManage(props)
        try {
            assertTrue(manager.isRunning)
        } finally {
            manager.shutdownNow()
        }
    }

    // ==================== 倒计时任务测试 ====================

    @Test
    fun testAddCountdownExecutesTaskAtSpecifiedTime() {
        val latch = CountDownLatch(1)
        val executed = AtomicBoolean(false)

        taskManager.addCountdown(
            name = "countdown",
            group = "test",
            description = "测试倒计时",
            startTime = System.currentTimeMillis() + 500,
        ) {
            executed.set(true)
            latch.countDown()
        }

        assertTrue(taskManager.contains("countdown", "test"))
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(executed.get())

        Thread.sleep(200)
        assertFalse(taskManager.contains("countdown", "test"))
    }

    @Test
    fun testAddCountdownWithPastTimeExecutesImmediately() {
        val latch = CountDownLatch(1)

        taskManager.addCountdown(
            name = "pastCountdown",
            group = "test",
            description = "过去时间",
            startTime = System.currentTimeMillis() - 1000,
        ) {
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
    }

    // ==================== 间隔任务测试 ====================

    @Test
    fun testAddTimedTaskWithIntervalExecutesRepeatedly() {
        val latch = CountDownLatch(3)
        val counter = AtomicInteger(0)

        taskManager.addTimedTask(
            name = "interval",
            group = "test",
            description = "间隔任务",
            startTime = System.currentTimeMillis(),
            intervalTime = 100,
        ) {
            counter.incrementAndGet()
            latch.countDown()
        }

        assertTrue(taskManager.contains("interval", "test"))
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(counter.get() >= 3)
        assertTrue(taskManager.contains("interval", "test"))
        taskManager.remove("interval", "test")
    }

    @Test
    fun testAddTimedTaskWithZeroIntervalThrowsException() {
        assertThrows<IllegalArgumentException> {
            taskManager.addTimedTask(
                name = "invalid",
                group = "test",
                description = "无效间隔",
                startTime = System.currentTimeMillis(),
                intervalTime = 0,
            ) {}
        }
    }

    @Test
    fun testAddTimedTaskWithNegativeIntervalThrowsException() {
        assertThrows<IllegalArgumentException> {
            taskManager.addTimedTask(
                name = "invalid",
                group = "test",
                description = "负间隔",
                startTime = System.currentTimeMillis(),
                intervalTime = -100,
            ) {}
        }
    }

    // ==================== Cron 任务测试 ====================

    @Test
    fun testAddTimedTaskWithCronExecutesOnSchedule() {
        val latch = CountDownLatch(2)
        val counter = AtomicInteger(0)

        taskManager.addTimedTask(
            name = "cron",
            group = "test",
            description = "Cron任务",
            cron = "0/1 * * * * ?"
        ) {
            counter.incrementAndGet()
            latch.countDown()
        }

        assertTrue(taskManager.contains("cron", "test"))
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(counter.get() >= 2)
        taskManager.remove("cron", "test")
    }

    // ==================== 任务管理测试 ====================

    @Test
    fun testContainsReturnsCorrectResult() {
        assertFalse(taskManager.contains("nonexistent", "test"))

        taskManager.addCountdown("exists", "test", "测试", System.currentTimeMillis() + 10000) {}

        assertTrue(taskManager.contains("exists", "test"))
        assertFalse(taskManager.contains("exists", "other"))
        assertFalse(taskManager.contains("other", "test"))

        taskManager.remove("exists", "test")
    }

    @Test
    fun testPauseAndUnPauseWorkCorrectly() {
        val counter = AtomicInteger(0)
        val latch = CountDownLatch(1)

        taskManager.addTimedTask(
            name = "pauseTest",
            group = "test",
            description = "暂停测试",
            startTime = System.currentTimeMillis(),
            intervalTime = 100,
        ) {
            counter.incrementAndGet()
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        val countBeforePause = counter.get()

        assertTrue(taskManager.pause("pauseTest", "test"))
        Thread.sleep(300)
        assertEquals(countBeforePause, counter.get())

        assertTrue(taskManager.unPause("pauseTest", "test"))
        Thread.sleep(300)
        assertTrue(counter.get() > countBeforePause)

        taskManager.remove("pauseTest", "test")
    }

    @Test
    fun testPauseNonexistentTaskReturnsFalse() {
        assertFalse(taskManager.pause("nonexistent", "test"))
    }

    @Test
    fun testUnPauseNonexistentTaskReturnsFalse() {
        assertFalse(taskManager.unPause("nonexistent", "test"))
    }

    @Test
    fun testRemoveDeletesTask() {
        taskManager.addCountdown("toRemove", "test", "删除测试", System.currentTimeMillis() + 10000) {}

        assertTrue(taskManager.contains("toRemove", "test"))
        assertTrue(taskManager.remove("toRemove", "test"))
        assertFalse(taskManager.contains("toRemove", "test"))
    }

    @Test
    fun testRemoveNonexistentTaskReturnsFalse() {
        assertFalse(taskManager.remove("nonexistent", "test"))
    }

    // ==================== triggerNow 测试 ====================

    @Test
    fun testTriggerNowNonexistentTaskReturnsFalse() {
        assertFalse(taskManager.triggerNow("nonexistent", "test"))
    }

    @Test
    fun testTriggerNowReturnsTrue() {
        // 创建一个正常的间隔任务
        taskManager.addTimedTask(
            name = "triggerTest",
            group = "test",
            description = "触发测试",
            startTime = System.currentTimeMillis(),
            intervalTime = 60000,
        ) {}

        Thread.sleep(200)
        assertTrue(taskManager.contains("triggerTest", "test"))

        // triggerNow 应该返回 true（任务存在）
        assertTrue(taskManager.triggerNow("triggerTest", "test"))

        taskManager.remove("triggerTest", "test")
    }

    // ==================== reschedule 测试 ====================

    @Test
    fun testRescheduleWithNewIntervalWorks() {
        val counter = AtomicInteger(0)

        taskManager.addTimedTask(
            name = "rescheduleTest",
            group = "test",
            description = "重调度测试",
            startTime = System.currentTimeMillis(),
            intervalTime = 5000,
        ) {
            counter.incrementAndGet()
        }

        Thread.sleep(200)
        assertTrue(taskManager.reschedule("rescheduleTest", "test", 100))
        Thread.sleep(500)
        assertTrue(counter.get() >= 3)

        taskManager.remove("rescheduleTest", "test")
    }

    @Test
    fun testRescheduleNonexistentTaskReturnsFalse() {
        assertFalse(taskManager.reschedule("nonexistent", "test", 1000))
    }

    @Test
    fun testRescheduleWithInvalidIntervalThrowsException() {
        taskManager.addTimedTask(
            name = "invalidReschedule",
            group = "test",
            description = "测试",
            startTime = System.currentTimeMillis(),
            intervalTime = 1000,
        ) {}

        assertThrows<IllegalArgumentException> {
            taskManager.reschedule("invalidReschedule", "test", 0)
        }

        taskManager.remove("invalidReschedule", "test")
    }

    // ==================== getJobState 测试 ====================

    @Test
    fun testGetJobStateReturnsCorrectState() {
        taskManager.addTimedTask(
            name = "stateTest",
            group = "test",
            description = "状态测试",
            startTime = System.currentTimeMillis() + 10000,
            intervalTime = 1000,
        ) {}

        assertEquals(JobState.NORMAL, taskManager.getJobState("stateTest", "test"))

        taskManager.pause("stateTest", "test")
        assertEquals(JobState.PAUSED, taskManager.getJobState("stateTest", "test"))

        taskManager.remove("stateTest", "test")
    }

    @Test
    fun testGetJobStateNonexistentTaskReturnsNull() {
        assertNull(taskManager.getJobState("nonexistent", "test"))
    }

    // ==================== jobCount 测试 ====================

    @Test
    fun testJobCountReturnsCorrectCount() {
        assertEquals(0, taskManager.jobCount)

        taskManager.addCountdown("job1", "test", "任务1", System.currentTimeMillis() + 10000) {}
        assertEquals(1, taskManager.jobCount)

        taskManager.addCountdown("job2", "test", "任务2", System.currentTimeMillis() + 10000) {}
        assertEquals(2, taskManager.jobCount)

        taskManager.remove("job1", "test")
        assertEquals(1, taskManager.jobCount)

        taskManager.remove("job2", "test")
        assertEquals(0, taskManager.jobCount)
    }

    // ==================== standby 和 resume 测试 ====================

    @Test
    fun testStandbyAndResumeWorkCorrectly() {
        assertTrue(taskManager.isRunning)
        assertFalse(taskManager.isStandby)

        taskManager.standby()
        assertTrue(taskManager.isStandby)

        taskManager.resume()
        assertFalse(taskManager.isStandby)
    }

    // ==================== shutdown 测试 ====================

    @Test
    fun testShutdownStopsScheduler() {
        assertTrue(taskManager.isRunning)
        taskManager.shutdown(false)
        assertFalse(taskManager.isRunning)
    }

    @Test
    fun testShutdownNowStopsScheduler() {
        assertTrue(taskManager.isRunning)
        taskManager.shutdownNow()
        assertFalse(taskManager.isRunning)
    }

    @Test
    fun testMultipleShutdownCallsAreSafe() {
        taskManager.shutdownNow()
        taskManager.shutdownNow()
        assertFalse(taskManager.isRunning)
    }
}
