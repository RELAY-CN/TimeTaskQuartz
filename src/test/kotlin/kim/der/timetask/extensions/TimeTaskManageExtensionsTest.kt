package kim.der.timetask.extensions

import kim.der.timetask.dsl.task
import kim.der.timetask.task.TimeTaskManage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TimeTaskManageExtensionsTest {

    private lateinit var timeTaskManage: TimeTaskManage

    @BeforeEach
    fun setUp() {
        timeTaskManage = TimeTaskManage()
        Thread.sleep(100)
    }

    @AfterEach
    fun tearDown() {
        timeTaskManage.clearAll()
        timeTaskManage.shutdownNow()
        Thread.sleep(100)
    }

    @Test
    fun `delay executes task once`() {
        val latch = CountDownLatch(1)
        timeTaskManage.delay(name = "delayTest", delayMillis = 1000) {
            latch.countDown()
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(!timeTaskManage.contains("delayTest"))
    }

    @Test
    fun `every executes multiple times`() {
        val latch = CountDownLatch(2)
        timeTaskManage.every(
            name = "everyTest",
            intervalMillis = 100,
            delayMillis = 0,
        ) {
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        val nextFireTime = timeTaskManage.getNextFireTime("everyTest")
        assertNotNull(nextFireTime)
        timeTaskManage.remove("everyTest")
    }

    @Test
    fun `cron schedules task`() {
        val latch = CountDownLatch(1)
        timeTaskManage.cron(
            name = "cronTest",
            cron = CronExpressions.EVERY_SECOND,
        ) {
            latch.countDown()
            timeTaskManage.remove("cronTest")
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
    }

    @Test
    fun `dsl task builder works`() {
        val latch = CountDownLatch(1)
        val executions = AtomicInteger(0)

        timeTaskManage.task("dslTask") {
            group("dslGroup")
            description("DSL 定时任务")
            interval(100)
            action {
                if (executions.incrementAndGet() >= 1) {
                    latch.countDown()
                    timeTaskManage.remove("dslTask", "dslGroup")
                }
            }
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
    }

    @Test
    fun `time unit extensions provide expected milliseconds`() {
        val oneSecond = 1.seconds
        val twoMinutes = 2.minutes
        val threeHours = 3.hours

        val latch = CountDownLatch(1)
        timeTaskManage.delay("timeUnitTest", oneSecond) {
            latch.countDown()
            timeTaskManage.remove("timeUnitTest")
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(twoMinutes == TimeUnit.MINUTES.toMillis(2))
        assertTrue(threeHours == TimeUnit.HOURS.toMillis(3))
    }
}

