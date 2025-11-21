package kim.der.timetask.task

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * TimeTaskManage的测试类
 *
 * @author Dr (dr@der.kim)
 */
class TimeTaskManageTest {
    private lateinit var timeTaskManage: TimeTaskManage

    @BeforeEach
    fun setUp() {
        timeTaskManage = TimeTaskManage()
        // 确保调度器完全启动
        Thread.sleep(100)
    }

    @AfterEach
    fun tearDown() {
        timeTaskManage.shutdownNow()
        // 等待调度器完全关闭
        Thread.sleep(100)
    }

    @Test
    fun testAddCountdown() {
        val latch = CountDownLatch(1)
        val executed = AtomicBoolean(false)

        val startTime = System.currentTimeMillis() + 1000
        timeTaskManage.addCountdown(
            "testCountdown",
            "testGroup",
            "测试倒计时任务",
            startTime,
        ) {
            executed.set(true)
            latch.countDown()
        }

        Thread.sleep(100)

        // 验证任务已添加
        assertTrue(timeTaskManage.contains("testCountdown", "testGroup"))

        // 等待任务执行
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Task should execute within 10 seconds")
        assertTrue(executed.get(), "Task should have executed")

        // 增加短暂延迟确保任务被移除
        Thread.sleep(100)

        // 验证任务执行后被移除
        assertFalse(timeTaskManage.contains("testCountdown", "testGroup"))
    }

    @Test
    fun testAddTimedTask() {
        val latch = CountDownLatch(3)
        val counter = AtomicInteger(0)
        val startTime = System.currentTimeMillis()

        // 创建定时任务，立即开始，每100ms执行一次
        timeTaskManage.addTimedTask(
            "testTimedTask",
            "testGroup",
            "测试定时任务",
            startTime,
            100, // 使用更短的间隔以确保快速执行
        ) {
            counter.incrementAndGet()
            latch.countDown()
        }

        Thread.sleep(100)

        // 验证任务已添加
        assertTrue(timeTaskManage.contains("testTimedTask", "testGroup"))

        // 等待任务执行3次，最多等待3秒
        assertTrue(latch.await(3, TimeUnit.SECONDS), "Tasks should execute within 3 seconds")
        assertEquals(3, counter.get(), "Task should have executed exactly 3 times")

        // 验证任务仍然存在
        assertTrue(timeTaskManage.contains("testTimedTask", "testGroup"))

        // 移除任务
        assertTrue(timeTaskManage.remove("testTimedTask", "testGroup"))
        assertFalse(timeTaskManage.contains("testTimedTask", "testGroup"))
    }

    @Test
    fun testAddCronTimedTask() {
        val latch = CountDownLatch(2)
        val counter = AtomicInteger(0)

        // 使用每秒执行一次的cron表达式
        timeTaskManage.addTimedTask(
            "testCronTask",
            "testGroup",
            "测试Cron定时任务",
            "0/1 * * * * ?", // 每秒执行一次
        ) {
            counter.incrementAndGet()
            latch.countDown()
        }

        Thread.sleep(100)

        // 验证任务已添加
        assertTrue(timeTaskManage.contains("testCronTask", "testGroup"))

        // 等待任务执行2次
        assertTrue(latch.await(3, TimeUnit.SECONDS), "Task should execute twice within 3 seconds")
        assertTrue(counter.get() >= 2, "Task should have executed at least 2 times")

        // 移除任务
        assertTrue(timeTaskManage.remove("testCronTask", "testGroup"))
        assertFalse(timeTaskManage.contains("testCronTask", "testGroup"))
    }

    @Test
    fun testPauseAndUnPause() {
        val latch = CountDownLatch(2)
        val counter = AtomicInteger(0)

        // 创建定时任务，立即开始，每500ms执行一次
        timeTaskManage.addTimedTask(
            "testPauseTask",
            "testGroup",
            "测试暂停任务",
            System.currentTimeMillis(),
            500,
        ) {
            counter.incrementAndGet()
            latch.countDown()
        }

        // 等待任务执行至少一次
        assertTrue(latch.await(3, TimeUnit.SECONDS), "Task should execute at least once")
        val countBeforePause = counter.get()
        assertTrue(countBeforePause > 0, "Task should have executed at least once")

        // 暂停任务
        timeTaskManage.pause("testPauseTask", "testGroup")

        // 记录当前计数
        val countAfterPause = counter.get()

        // 等待一段时间，确认任务已暂停
        Thread.sleep(1000)
        assertEquals(countAfterPause, counter.get(), "Task should not execute while paused")

        // 恢复任务
        timeTaskManage.unPause("testPauseTask", "testGroup")

        // 等待任务继续执行
        Thread.sleep(1000)
        assertTrue(counter.get() > countAfterPause, "Task should resume execution after unpause")

        // 清理
        timeTaskManage.remove("testPauseTask", "testGroup")
    }
} 
