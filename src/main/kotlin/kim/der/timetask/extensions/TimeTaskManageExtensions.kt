/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.extensions

import kim.der.timetask.task.TimeTaskManage
import org.quartz.JobKey

/**
 * 默认任务组名
 */
const val DEFAULT_GROUP = "default"

/**
 * 延迟执行倒计时任务（使用默认组）
 *
 * @param name 任务名称
 * @param delayMillis 延迟毫秒数
 * @param description 任务描述，默认为空
 * @param action 要执行的操作
 */
fun TimeTaskManage.delay(
    name: String,
    delayMillis: Long,
    description: String = "",
    action: () -> Unit,
) {
    addCountdown(
        name = name,
        group = DEFAULT_GROUP,
        description = description.ifEmpty { "延迟 ${delayMillis}ms 执行" },
        startTime = System.currentTimeMillis() + delayMillis,
        runnable = Runnable(action),
    )
}

/**
 * 立即执行一次任务
 *
 * @param name 任务名称
 * @param description 任务描述
 * @param action 要执行的操作
 */
fun TimeTaskManage.runNow(
    name: String,
    description: String = "立即执行",
    action: () -> Unit,
) {
    addCountdown(
        name = name,
        group = DEFAULT_GROUP,
        description = description,
        startTime = System.currentTimeMillis() + 1, // 1ms 后执行，基本等于立即执行
        runnable = Runnable(action),
    )
}

/**
 * 添加固定间隔的定时任务（使用默认组）
 *
 * @param name 任务名称
 * @param intervalMillis 执行间隔（毫秒）
 * @param delayMillis 延迟开始时间（毫秒），默认为 0
 * @param description 任务描述
 * @param action 要执行的操作
 */
fun TimeTaskManage.every(
    name: String,
    intervalMillis: Long,
    delayMillis: Long = 0,
    description: String = "",
    action: () -> Unit,
) {
    addTimedTask(
        name = name,
        group = DEFAULT_GROUP,
        description = description.ifEmpty { "每 ${intervalMillis}ms 执行一次" },
        startTime = System.currentTimeMillis() + delayMillis,
        intervalTime = intervalMillis,
        runnable = Runnable(action),
    )
}

/**
 * 添加 Cron 表达式的定时任务（使用默认组）
 *
 * @param name 任务名称
 * @param cron Cron 表达式
 * @param description 任务描述
 * @param action 要执行的操作
 */
fun TimeTaskManage.cron(
    name: String,
    cron: String,
    description: String = "",
    action: () -> Unit,
) {
    addTimedTask(
        name = name,
        group = DEFAULT_GROUP,
        description = description.ifEmpty { "Cron: $cron" },
        cron = cron,
        runnable = Runnable(action),
    )
}

/**
 * 检查任务是否存在（使用默认组）
 */
fun TimeTaskManage.contains(name: String): Boolean = contains(name, DEFAULT_GROUP)

/**
 * 暂停任务（使用默认组）
 */
fun TimeTaskManage.pause(name: String) = pause(name, DEFAULT_GROUP)

/**
 * 恢复任务（使用默认组）
 */
fun TimeTaskManage.resume(name: String) = unPause(name, DEFAULT_GROUP)

/**
 * 删除任务（使用默认组）
 */
fun TimeTaskManage.remove(name: String): Boolean = remove(name, DEFAULT_GROUP)

/**
 * 获取所有任务组名
 */
fun TimeTaskManage.getAllGroupNames(): List<String> {
    return try {
        scheduler.jobGroupNames.toList()
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * 获取指定组的所有任务名称
 */
fun TimeTaskManage.getJobNames(group: String = DEFAULT_GROUP): List<String> {
    return try {
        scheduler.getJobKeys(org.quartz.impl.matchers.GroupMatcher.jobGroupEquals(group))
            .map { it.name }
            .toList()
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * 获取所有任务
 */
fun TimeTaskManage.getAllJobs(): List<JobKey> {
    return try {
        scheduler.jobGroupNames.flatMap { groupName ->
            scheduler.getJobKeys(org.quartz.impl.matchers.GroupMatcher.jobGroupEquals(groupName))
        }.toList()
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * 批量暂停任务
 */
fun TimeTaskManage.pauseAll(group: String = DEFAULT_GROUP) {
    try {
        scheduler.pauseJobs(org.quartz.impl.matchers.GroupMatcher.jobGroupEquals(group))
    } catch (e: Exception) {
        // 忽略异常
    }
}

/**
 * 批量恢复任务
 */
fun TimeTaskManage.resumeAll(group: String = DEFAULT_GROUP) {
    try {
        scheduler.resumeJobs(org.quartz.impl.matchers.GroupMatcher.jobGroupEquals(group))
    } catch (e: Exception) {
        // 忽略异常
    }
}

/**
 * 删除组内所有任务
 */
fun TimeTaskManage.removeAll(group: String = DEFAULT_GROUP): Int {
    return try {
        val jobKeys = scheduler.getJobKeys(org.quartz.impl.matchers.GroupMatcher.jobGroupEquals(group))
        var count = 0
        jobKeys.forEach { jobKey ->
            if (remove(jobKey)) count++
        }
        count
    } catch (e: Exception) {
        0
    }
}

/**
 * 清空所有任务
 */
fun TimeTaskManage.clearAll(): Int {
    return try {
        var count = 0
        getAllJobs().forEach { jobKey ->
            if (remove(jobKey)) count++
        }
        count
    } catch (e: Exception) {
        0
    }
}

/**
 * 获取任务的下次执行时间
 */
fun TimeTaskManage.getNextFireTime(name: String, group: String = DEFAULT_GROUP): Long? {
    return try {
        val triggers = scheduler.getTriggersOfJob(JobKey(name, group))
        triggers.firstOrNull()?.nextFireTime?.time
    } catch (e: Exception) {
        null
    }
}

/**
 * 获取任务的执行次数（需要 Quartz 支持）
 */
fun TimeTaskManage.getExecutionCount(name: String, group: String = DEFAULT_GROUP): Int? {
    return try {
        val jobDetail = scheduler.getJobDetail(JobKey(name, group))
        jobDetail?.jobDataMap?.get("executionCount") as? Int
    } catch (e: Exception) {
        null
    }
}

