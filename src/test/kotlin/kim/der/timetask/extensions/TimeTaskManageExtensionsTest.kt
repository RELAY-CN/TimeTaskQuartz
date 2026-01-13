/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.extensions

import kim.der.timetask.dsl.cronTask
import kim.der.timetask.dsl.delayTask
import kim.der.timetask.dsl.intervalTask
import kim.der.timetask.dsl.task
import kim.der.timetask.task.JobState
import kim.der.timetask.task.TimeTaskManage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * TimeTaskManageExtensions 的测试类
 *
 * @author Dr (dr@der.kim)
 */
class TimeTaskManageExtensionsTest {
    private lateinit var taskManager: TimeTaskManage

    @BeforeEach
    fun setUp() {
        taskManager = TimeTaskManage()
        Thread.sleep(100)
    }

    @AfterEach
    fun tearDown() {
        taskManager.clearAll()
        taskManager.shutdownNow()
        Thread.sleep(100)
    }

    // ==================== 任务创建扩展测试 ====================

    @Test
    fun testDelayExecutesTaskOnceAfterDelay() {
        val latch = CountDownLatch(1)

        taskManager.delay("delayTest", 500) {
            latch.countDown()
        }

        assertTrue(taskManager.contains("delayTest"))
        assertTrue(latch.await(3, TimeUnit.SECONDS))

        Thread.sleep(200)
        assertFalse(taskManager.contains("delayTest"))
    }

    @Test
    fun testRunNowExecutesTaskImmediately() {
        val latch = CountDownLatch(1)

        taskManager.runNow("runNowTest") {
            latch.countDown()
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun testRunAtExecutesTaskAtSpecifiedTime() {
        val latch = CountDownLatch(1)

        taskManager.runAt("runAtTest", System.currentTimeMillis() + 500) {
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
    }

    @Test
    fun testEveryExecutesTaskRepeatedly() {
        val latch = CountDownLatch(3)
        val counter = AtomicInteger(0)

        taskManager.every("everyTest", 100) {
            counter.incrementAndGet()
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertTrue(counter.get() >= 3)

        taskManager.remove("everyTest")
    }

    @Test
    fun testEveryWithDelayStartsAfterDelay() {
        val latch = CountDownLatch(1)
        val startTime = System.currentTimeMillis()

        taskManager.every("everyDelayTest", 100, delayMillis = 500) {
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertTrue(System.currentTimeMillis() - startTime >= 400)

        taskManager.remove("everyDelayTest")
    }

    @Test
    fun testCronSchedulesTaskCorrectly() {
        val latch = CountDownLatch(1)

        taskManager.cron("cronTest", CronExpressions.EVERY_SECOND) {
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        taskManager.remove("cronTest")
    }

    // ==================== 单任务管理扩展测试 ====================

    @Test
    fun testContainsWithSingleNameUsesDefaultGroup() {
        taskManager.delay("containsTest", 10000) {}

        assertTrue(taskManager.contains("containsTest"))
        assertTrue(taskManager.contains("containsTest", DEFAULT_GROUP))

        taskManager.remove("containsTest")
    }

    @Test
    fun testPauseWithSingleNameUsesDefaultGroup() {
        taskManager.every("pauseTest", 100) {}

        assertTrue(taskManager.pause("pauseTest"))
        assertEquals(JobState.PAUSED, taskManager.getState("pauseTest"))

        taskManager.remove("pauseTest")
    }

    @Test
    fun testResumeWithSingleNameUsesDefaultGroup() {
        taskManager.every("resumeTest", 100) {}
        taskManager.pause("resumeTest")

        assertTrue(taskManager.resume("resumeTest"))
        assertEquals(JobState.NORMAL, taskManager.getState("resumeTest"))

        taskManager.remove("resumeTest")
    }

    @Test
    fun testRemoveWithSingleNameUsesDefaultGroup() {
        taskManager.delay("removeTest", 10000) {}

        assertTrue(taskManager.remove("removeTest"))
        assertFalse(taskManager.contains("removeTest"))
    }

    @Test
    fun testTriggerNowWithSingleNameUsesDefaultGroup() {
        // 创建一个正常的间隔任务
        taskManager.every("triggerTest", 60000) {}

        Thread.sleep(200)
        assertTrue(taskManager.contains("triggerTest"))

        // triggerNow 应该返回 true（任务存在）
        assertTrue(taskManager.triggerNow("triggerTest"))

        taskManager.remove("triggerTest")
    }

    @Test
    fun testGetStateReturnsCorrectState() {
        taskManager.every("stateTest", 1000) {}

        assertEquals(JobState.NORMAL, taskManager.getState("stateTest"))

        taskManager.pause("stateTest")
        assertEquals(JobState.PAUSED, taskManager.getState("stateTest"))

        taskManager.remove("stateTest")
    }

    // ==================== 批量管理扩展测试 ====================

    @Test
    fun testPauseAllPausesAllTasksInGroup() {
        taskManager.every("task1", 1000) {}
        taskManager.every("task2", 1000) {}

        val count = taskManager.pauseAll()
        assertEquals(2, count)

        assertEquals(JobState.PAUSED, taskManager.getState("task1"))
        assertEquals(JobState.PAUSED, taskManager.getState("task2"))
    }

    @Test
    fun testResumeAllResumesAllTasksInGroup() {
        taskManager.every("task1", 1000) {}
        taskManager.every("task2", 1000) {}
        taskManager.pauseAll()

        val count = taskManager.resumeAll()
        assertEquals(2, count)

        assertEquals(JobState.NORMAL, taskManager.getState("task1"))
        assertEquals(JobState.NORMAL, taskManager.getState("task2"))
    }

    @Test
    fun testRemoveAllRemovesAllTasksInGroup() {
        taskManager.every("task1", 1000) {}
        taskManager.every("task2", 1000) {}

        val count = taskManager.removeAll()
        assertEquals(2, count)

        assertFalse(taskManager.contains("task1"))
        assertFalse(taskManager.contains("task2"))
    }

    @Test
    fun testClearAllRemovesAllTasks() {
        taskManager.every("task1", 1000) {}
        taskManager.addTimedTask("task2", "other", "其他组", System.currentTimeMillis(), 1000) {}

        val count = taskManager.clearAll()
        assertEquals(2, count)
        assertEquals(0, taskManager.jobCount)
    }

    // ==================== 查询扩展测试 ====================

    @Test
    fun testGetAllGroupNamesReturnsAllGroups() {
        taskManager.every("task1", 1000) {}
        taskManager.addTimedTask("task2", "group2", "组2", System.currentTimeMillis(), 1000) {}

        val groups = taskManager.getAllGroupNames()
        assertTrue(groups.contains(DEFAULT_GROUP))
        assertTrue(groups.contains("group2"))
    }

    @Test
    fun testGetJobNamesReturnsJobNamesInGroup() {
        taskManager.every("task1", 1000) {}
        taskManager.every("task2", 1000) {}

        val names = taskManager.getJobNames()
        assertEquals(2, names.size)
        assertTrue(names.contains("task1"))
        assertTrue(names.contains("task2"))
    }

    @Test
    fun testGetAllJobsReturnsAllJobKeys() {
        taskManager.every("task1", 1000) {}
        taskManager.addTimedTask("task2", "other", "其他", System.currentTimeMillis(), 1000) {}

        val jobs = taskManager.getAllJobs()
        assertEquals(2, jobs.size)
    }

    @Test
    fun testGetJobsInGroupReturnsJobsInSpecificGroup() {
        taskManager.every("task1", 1000) {}
        taskManager.every("task2", 1000) {}
        taskManager.addTimedTask("task3", "other", "其他", System.currentTimeMillis(), 1000) {}

        val jobs = taskManager.getJobsInGroup(DEFAULT_GROUP)
        assertEquals(2, jobs.size)
    }

    @Test
    fun testGetNextFireTimeReturnsNextFireTime() {
        taskManager.every("fireTimeTest", 1000) {}

        val nextFireTime = taskManager.getNextFireTime("fireTimeTest")
        assertNotNull(nextFireTime)
        assertTrue(nextFireTime!! > System.currentTimeMillis() - 1000)

        taskManager.remove("fireTimeTest")
    }

    @Test
    fun testGetPreviousFireTimeReturnsNullForNewTask() {
        taskManager.every("prevFireTest", 10000, delayMillis = 10000) {}

        val prevFireTime = taskManager.getPreviousFireTime("prevFireTest")
        assertNull(prevFireTime)

        taskManager.remove("prevFireTest")
    }

    @Test
    fun testGetDescriptionReturnsTaskDescription() {
        taskManager.every("descTest", 1000, description = "测试描述") {}

        val desc = taskManager.getDescription("descTest")
        assertEquals("测试描述", desc)

        taskManager.remove("descTest")
    }

    // ==================== JobInfo 测试 ====================

    @Test
    fun testGetJobInfoReturnsCompleteJobInfo() {
        taskManager.every("infoTest", 1000, description = "信息测试") {}

        val info = taskManager.getJobInfo("infoTest")
        assertNotNull(info)
        assertEquals("infoTest", info!!.name)
        assertEquals(DEFAULT_GROUP, info.group)
        assertEquals("信息测试", info.description)
        assertEquals(JobState.NORMAL, info.state)
        assertNotNull(info.nextFireTime)

        taskManager.remove("infoTest")
    }

    @Test
    fun testGetJobInfoReturnsNullForNonexistentTask() {
        val info = taskManager.getJobInfo("nonexistent")
        assertNull(info)
    }

    @Test
    fun testGetAllJobInfoReturnsAllJobInfo() {
        taskManager.every("task1", 1000) {}
        taskManager.every("task2", 1000) {}

        val infos = taskManager.getAllJobInfo()
        assertEquals(2, infos.size)
    }

    @Test
    fun testGetJobInfoInGroupReturnsJobInfoInGroup() {
        taskManager.every("task1", 1000) {}
        taskManager.addTimedTask("task2", "other", "其他", System.currentTimeMillis(), 1000) {}

        val infos = taskManager.getJobInfoInGroup(DEFAULT_GROUP)
        assertEquals(1, infos.size)
        assertEquals("task1", infos[0].name)
    }

    // ==================== DSL 测试 ====================

    @Test
    fun testTaskDslCreatesTaskCorrectly() {
        val latch = CountDownLatch(1)

        taskManager.task("dslTask") {
            group("dslGroup")
            description("DSL任务")
            interval(100)
            action { latch.countDown() }
        }

        assertTrue(taskManager.contains("dslTask", "dslGroup"))
        assertTrue(latch.await(3, TimeUnit.SECONDS))

        taskManager.remove("dslTask", "dslGroup")
    }

    @Test
    fun testDelayTaskCreatesCountdownTask() {
        val latch = CountDownLatch(1)

        taskManager.delayTask("delayDsl", 500) {
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
    }

    @Test
    fun testIntervalTaskCreatesIntervalTask() {
        val latch = CountDownLatch(2)

        taskManager.intervalTask("intervalDsl", 100) {
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        taskManager.remove("intervalDsl")
    }

    @Test
    fun testCronTaskCreatesCronTask() {
        val latch = CountDownLatch(1)

        taskManager.cronTask("cronDsl", CronExpressions.EVERY_SECOND) {
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        taskManager.remove("cronDsl")
    }

    // ==================== 时间单位扩展测试 ====================

    @Test
    fun testTimeUnitExtensionsWorkCorrectly() {
        assertEquals(1000L, 1.seconds)
        assertEquals(60000L, 1.minutes)
        assertEquals(3600000L, 1.hours)
        assertEquals(86400000L, 1.days)
        assertEquals(500L, 500.millis)

        assertEquals(5000L, 5L.seconds)
        assertEquals(300000L, 5L.minutes)
    }

    @Test
    fun testDelayWithTimeUnitExtensionWorks() {
        val latch = CountDownLatch(1)

        taskManager.delay("timeUnitTest", 1.seconds) {
            latch.countDown()
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
    }
}
