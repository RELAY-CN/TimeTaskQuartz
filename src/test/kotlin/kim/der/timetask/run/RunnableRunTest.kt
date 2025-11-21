package kim.der.timetask.run

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * RunnableRun的测试类
 *
 * @author Dr (dr@der.kim)
 */
class RunnableRunTest {
    @Test
    fun testExecute() {
        // 创建一个模拟的JobExecutionContext
        val context = mock(JobExecutionContext::class.java)

        // 创建一个标记变量用于验证Runnable是否执行
        val executed = AtomicBoolean(false)

        // 创建JobDataMap并设置run函数
        val dataMap = JobDataMap()
        dataMap["run"] = { executed.set(true) }

        // 设置模拟对象的行为
        `when`(context.mergedJobDataMap).thenReturn(dataMap)

        // 创建RunnableRun实例并执行
        val runnableRun = RunnableRun()
        runnableRun.execute(context)

        // 验证Runnable是否被执行
        assertTrue(executed.get())
    }
}
