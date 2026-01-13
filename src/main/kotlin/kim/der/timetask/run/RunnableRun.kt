/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.run

import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException

/**
 * 基于 Lambda 函数的 Quartz Job 实现。
 *
 * 此类作为 Quartz 调度器和用户定义的任务逻辑之间的桥梁。
 * 它从 [JobExecutionContext] 的 JobDataMap 中获取存储的 Lambda 函数并执行。
 *
 * ## 内部实现
 *
 * 任务函数存储在 Trigger 的 JobDataMap 中，键名为 "run"。
 * 执行时从 mergedJobDataMap 中获取并调用。
 *
 * ## 异常处理
 *
 * 如果任务执行过程中抛出异常，会被包装为 [JobExecutionException] 重新抛出，
 * 以便 Quartz 调度器能够正确处理。
 *
 * @author Dr (dr@der.kim)
 * @since 1.0.0
 * @see org.quartz.Job
 */
class RunnableRun : Job {

    companion object {
        /**
         * JobDataMap 中存储执行函数的键名。
         */
        internal const val RUN_DATA_KEY = "run"
    }

    /**
     * 执行任务。
     *
     * 从 JobDataMap 中获取存储的 Lambda 函数并执行。
     *
     * @param context Quartz 提供的任务执行上下文
     * @throws JobExecutionException 如果任务执行失败或找不到执行函数
     */
    @Throws(JobExecutionException::class)
    override fun execute(context: JobExecutionContext) {
        val action = context.mergedJobDataMap[RUN_DATA_KEY]

        if (action == null) {
            throw JobExecutionException("No action found in JobDataMap with key '$RUN_DATA_KEY'")
        }

        try {
            @Suppress("UNCHECKED_CAST")
            (action as (() -> Unit))()
        } catch (e: ClassCastException) {
            throw JobExecutionException("Action in JobDataMap is not a valid function", e)
        } catch (e: Exception) {
            throw JobExecutionException("Task execution failed: ${e.message}", e, false)
        }
    }
}
