/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.run

import org.quartz.Job
import org.quartz.JobExecutionContext

/**
 * 基于Runnable的任务器
 *
 * @author Dr (dr@der.kim)
 */
class RunnableRun : Job {
    override fun execute(context: JobExecutionContext) {
        @Suppress("UNCHECKED_CAST")
        (context.mergedJobDataMap["run"] as (() -> Unit))()
    }
}
