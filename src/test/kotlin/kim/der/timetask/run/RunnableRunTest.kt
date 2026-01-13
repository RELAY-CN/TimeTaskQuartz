/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.run

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * RunnableRun 的测试类
 *
 * @author Dr (dr@der.kim)
 */
class RunnableRunTest {

    @Test
    fun testExecuteRunsAction() {
        val context = mock(JobExecutionContext::class.java)
        val executed = AtomicBoolean(false)

        val dataMap = JobDataMap()
        dataMap[RunnableRun.RUN_DATA_KEY] = { executed.set(true) }

        `when`(context.mergedJobDataMap).thenReturn(dataMap)

        val runnableRun = RunnableRun()
        runnableRun.execute(context)

        assertTrue(executed.get())
    }

    @Test
    fun testExecuteWithComplexAction() {
        val context = mock(JobExecutionContext::class.java)
        val counter = AtomicInteger(0)

        val dataMap = JobDataMap()
        dataMap[RunnableRun.RUN_DATA_KEY] = {
            counter.incrementAndGet()
            counter.incrementAndGet()
        }

        `when`(context.mergedJobDataMap).thenReturn(dataMap)

        val runnableRun = RunnableRun()
        runnableRun.execute(context)

        assertEquals(2, counter.get())
    }

    @Test
    fun testExecuteWithNullActionThrowsException() {
        val context = mock(JobExecutionContext::class.java)
        val dataMap = JobDataMap()
        // 不设置 RUN_DATA_KEY

        `when`(context.mergedJobDataMap).thenReturn(dataMap)

        val runnableRun = RunnableRun()

        assertThrows<JobExecutionException> {
            runnableRun.execute(context)
        }
    }

    @Test
    fun testExecuteWithInvalidActionTypeThrowsException() {
        val context = mock(JobExecutionContext::class.java)
        val dataMap = JobDataMap()
        dataMap[RunnableRun.RUN_DATA_KEY] = "not a function"

        `when`(context.mergedJobDataMap).thenReturn(dataMap)

        val runnableRun = RunnableRun()

        assertThrows<JobExecutionException> {
            runnableRun.execute(context)
        }
    }

    @Test
    fun testExecuteWithExceptionInActionThrowsJobExecutionException() {
        val context = mock(JobExecutionContext::class.java)
        val dataMap = JobDataMap()
        dataMap[RunnableRun.RUN_DATA_KEY] = {
            throw RuntimeException("Test exception")
        }

        `when`(context.mergedJobDataMap).thenReturn(dataMap)

        val runnableRun = RunnableRun()

        val exception = assertThrows<JobExecutionException> {
            runnableRun.execute(context)
        }

        assertTrue(exception.message?.contains("Test exception") == true)
    }

    @Test
    fun testRunDataKeyConstant() {
        assertEquals("run", RunnableRun.RUN_DATA_KEY)
    }

    @Test
    fun testMultipleExecutions() {
        val context = mock(JobExecutionContext::class.java)
        val counter = AtomicInteger(0)

        val dataMap = JobDataMap()
        dataMap[RunnableRun.RUN_DATA_KEY] = { counter.incrementAndGet() }

        `when`(context.mergedJobDataMap).thenReturn(dataMap)

        val runnableRun = RunnableRun()

        runnableRun.execute(context)
        runnableRun.execute(context)
        runnableRun.execute(context)

        assertEquals(3, counter.get())
    }
}
