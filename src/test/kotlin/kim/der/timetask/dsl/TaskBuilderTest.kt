/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.dsl

import kim.der.timetask.extensions.seconds
import kim.der.timetask.task.TimeTaskManage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * TaskBuilder 的测试类
 *
 * @author Dr (dr@der.kim)
 */
class TaskBuilderTest {
    private lateinit var taskManager: TimeTaskManage

    @BeforeEach
    fun setUp() {
        taskManager = TimeTaskManage()
        Thread.sleep(100)
    }

    @AfterEach
    fun tearDown() {
        taskManager.shutdownNow()
        Thread.sleep(100)
    }

    // ==================== 基本 DSL 测试 ====================

    @Test
    fun testTaskWithDefaultGroupCreatesTask() {
        val latch = CountDownLatch(1)

        taskManager.task("basicTask") {
            delay(500)
            action { latch.countDown() }
        }

        assertTrue(taskManager.contains("basicTask", "default"))
        assertTrue(latch.await(3, TimeUnit.SECONDS))
    }

    @Test
    fun testTaskWithCustomGroupCreatesTask() {
        taskManager.task("customGroupTask") {
            group("customGroup")
            delay(10000)
            action { }
        }

        assertTrue(taskManager.contains("customGroupTask", "customGroup"))
        assertFalse(taskManager.contains("customGroupTask", "default"))

        taskManager.remove("customGroupTask", "customGroup")
    }

    @Test
    fun testTaskWithDescriptionSetsDescription() {
        taskManager.task("descTask") {
            description("测试描述")
            delay(10000)
            action { }
        }

        assertTrue(taskManager.contains("descTask", "default"))
        taskManager.remove("descTask", "default")
    }

    // ==================== 延迟任务测试 ====================

    @Test
    fun testDelayCreatesCountdownTask() {
        val latch = CountDownLatch(1)
        val executed = AtomicBoolean(false)

        taskManager.task("delayTask") {
            delay(500)
            action {
                executed.set(true)
                latch.countDown()
            }
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertTrue(executed.get())

        // 倒计时任务执行后自动删除
        Thread.sleep(200)
        assertFalse(taskManager.contains("delayTask", "default"))
    }

    @Test
    fun testDelayWithNegativeValueThrowsException() {
        assertThrows<IllegalArgumentException> {
            taskManager.task("negativeDelay") {
                delay(-100)
                action { }
            }
        }
    }

    // ==================== 间隔任务测试 ====================

    @Test
    fun testIntervalCreatesRepeatingTask() {
        val latch = CountDownLatch(3)
        val counter = AtomicInteger(0)

        taskManager.task("intervalTask") {
            interval(100)
            action {
                counter.incrementAndGet()
                latch.countDown()
            }
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertTrue(counter.get() >= 3)

        // 间隔任务不会自动删除
        assertTrue(taskManager.contains("intervalTask", "default"))
        taskManager.remove("intervalTask", "default")
    }

    @Test
    fun testIntervalWithStartAtSetsStartTime() {
        val latch = CountDownLatch(1)
        val startTime = System.currentTimeMillis()

        taskManager.task("intervalStartAt") {
            startAt(System.currentTimeMillis() + 500)
            interval(100)
            action { latch.countDown() }
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertTrue(System.currentTimeMillis() - startTime >= 400)

        taskManager.remove("intervalStartAt", "default")
    }

    @Test
    fun testIntervalWithStartAfterSetsDelay() {
        val latch = CountDownLatch(1)
        val startTime = System.currentTimeMillis()

        taskManager.task("intervalStartAfter") {
            startAfter(500)
            interval(100)
            action { latch.countDown() }
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertTrue(System.currentTimeMillis() - startTime >= 400)

        taskManager.remove("intervalStartAfter", "default")
    }

    @Test
    fun testIntervalWithInvalidValueThrowsException() {
        assertThrows<IllegalArgumentException> {
            taskManager.task("invalidInterval") {
                interval(0)
                action { }
            }
        }
    }

    // ==================== Cron 任务测试 ====================

    @Test
    fun testCronCreatesCronTask() {
        val latch = CountDownLatch(1)

        taskManager.task("cronTask") {
            cron("0/1 * * * * ?")
            action { latch.countDown() }
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        taskManager.remove("cronTask", "default")
    }

    // ==================== 默认行为测试 ====================

    @Test
    fun testTaskWithoutScheduleExecutesImmediately() {
        val latch = CountDownLatch(1)

        taskManager.task("immediateTask") {
            action { latch.countDown() }
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun testTaskWithoutActionDoesNothing() {
        taskManager.task("noActionTask") {
            delay(100)
            // 没有设置 action
        }

        // 任务不应该被创建
        Thread.sleep(200)
        assertFalse(taskManager.contains("noActionTask", "default"))
    }

    // ==================== 便捷方法测试 ====================

    @Test
    fun testDelayTaskCreatesCountdownTask() {
        val latch = CountDownLatch(1)

        taskManager.delayTask("delayTaskFunc", 500) {
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
    }

    @Test
    fun testDelayTaskWithGroupAndDescription() {
        taskManager.delayTask(
            name = "delayTaskWithParams",
            delayMillis = 10000,
            group = "testGroup",
            description = "测试描述"
        ) { }

        assertTrue(taskManager.contains("delayTaskWithParams", "testGroup"))
        taskManager.remove("delayTaskWithParams", "testGroup")
    }

    @Test
    fun testIntervalTaskCreatesRepeatingTask() {
        val latch = CountDownLatch(2)

        taskManager.intervalTask("intervalTaskFunc", 100) {
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        taskManager.remove("intervalTaskFunc", "default")
    }

    @Test
    fun testIntervalTaskWithDelayAndGroup() {
        val latch = CountDownLatch(1)
        val startTime = System.currentTimeMillis()

        taskManager.intervalTask(
            name = "intervalTaskWithParams",
            intervalMillis = 100,
            group = "testGroup",
            delayMillis = 500
        ) {
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertTrue(System.currentTimeMillis() - startTime >= 400)

        taskManager.remove("intervalTaskWithParams", "testGroup")
    }

    @Test
    fun testCronTaskCreatesCronTask() {
        val latch = CountDownLatch(1)

        taskManager.cronTask("cronTaskFunc", "0/1 * * * * ?") {
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        taskManager.remove("cronTaskFunc", "default")
    }

    @Test
    fun testCronTaskWithGroupAndDescription() {
        taskManager.cronTask(
            name = "cronTaskWithParams",
            cron = "0 0 12 * * ?",
            group = "testGroup",
            description = "每天中午执行"
        ) { }

        assertTrue(taskManager.contains("cronTaskWithParams", "testGroup"))
        taskManager.remove("cronTaskWithParams", "testGroup")
    }

    // ==================== 链式调用测试 ====================

    @Test
    fun testChainedBuilderMethods() {
        val latch = CountDownLatch(1)

        taskManager.task("chainedTask") {
            group("chainGroup")
                .description("链式调用测试")
                .interval(100)
                .action { latch.countDown() }
        }

        assertTrue(taskManager.contains("chainedTask", "chainGroup"))
        assertTrue(latch.await(3, TimeUnit.SECONDS))

        taskManager.remove("chainedTask", "chainGroup")
    }

    // ==================== 时间单位扩展测试 ====================

    @Test
    fun testDelayWithTimeUnitExtension() {
        val latch = CountDownLatch(1)

        taskManager.task("timeUnitTask") {
            delay(1.seconds)
            action { latch.countDown() }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
    }
}
