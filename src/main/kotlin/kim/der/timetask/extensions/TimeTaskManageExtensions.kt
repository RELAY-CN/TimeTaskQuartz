/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.extensions

import kim.der.timetask.task.JobState
import kim.der.timetask.task.TimeTaskManage
import org.quartz.JobKey
import org.quartz.impl.matchers.GroupMatcher

/**
 * 默认任务组名。
 */
const val DEFAULT_GROUP = "default"

// ==================== 任务创建扩展 ====================

/**
 * 延迟执行倒计时任务（使用默认组）。
 *
 * 任务在指定延迟后执行一次，然后自动删除。
 *
 * @param name 任务名称
 * @param delayMillis 延迟毫秒数
 * @param description 任务描述，默认为空
 * @param action 要执行的操作
 *
 * ## 示例
 * ```kotlin
 * manager.delay("reminder", 5.seconds) {
 *     println("5秒后执行")
 * }
 * ```
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
 * 立即执行一次任务。
 *
 * 任务几乎立即执行一次后自动删除。
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
        startTime = System.currentTimeMillis() + 1,
        runnable = Runnable(action),
    )
}

/**
 * 在指定时间执行一次任务。
 *
 * @param name 任务名称
 * @param timestamp 执行时间（Unix 时间戳，毫秒）
 * @param description 任务描述
 * @param action 要执行的操作
 */
fun TimeTaskManage.runAt(
    name: String,
    timestamp: Long,
    description: String = "",
    action: () -> Unit,
) {
    addCountdown(
        name = name,
        group = DEFAULT_GROUP,
        description = description.ifEmpty { "定时执行" },
        startTime = timestamp,
        runnable = Runnable(action),
    )
}

/**
 * 添加固定间隔的定时任务（使用默认组）。
 *
 * @param name 任务名称
 * @param intervalMillis 执行间隔（毫秒）
 * @param delayMillis 延迟开始时间（毫秒），默认为 0
 * @param description 任务描述
 * @param action 要执行的操作
 *
 * ## 示例
 * ```kotlin
 * manager.every("heartbeat", 10.seconds) {
 *     println("每10秒执行")
 * }
 * ```
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
 * 添加 Cron 表达式的定时任务（使用默认组）。
 *
 * @param name 任务名称
 * @param cron Cron 表达式
 * @param description 任务描述
 * @param action 要执行的操作
 *
 * ## 示例
 * ```kotlin
 * manager.cron("daily-report", CronExpressions.DAILY_NOON) {
 *     println("每天中午执行")
 * }
 * ```
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

// ==================== 单任务管理扩展（默认组） ====================

/**
 * 检查任务是否存在（使用默认组）。
 *
 * @param name 任务名称
 * @return 如果任务存在返回 `true`
 */
fun TimeTaskManage.contains(name: String): Boolean = contains(name, DEFAULT_GROUP)

/**
 * 暂停任务（使用默认组）。
 *
 * @param name 任务名称
 * @return 如果暂停成功返回 `true`
 */
fun TimeTaskManage.pause(name: String): Boolean = pause(name, DEFAULT_GROUP)

/**
 * 恢复任务（使用默认组）。
 *
 * @param name 任务名称
 * @return 如果恢复成功返回 `true`
 */
fun TimeTaskManage.resume(name: String): Boolean = unPause(name, DEFAULT_GROUP)

/**
 * 删除任务（使用默认组）。
 *
 * @param name 任务名称
 * @return 如果删除成功返回 `true`
 */
fun TimeTaskManage.remove(name: String): Boolean = remove(name, DEFAULT_GROUP)

/**
 * 立即触发任务（使用默认组）。
 *
 * @param name 任务名称
 * @return 如果触发成功返回 `true`
 */
fun TimeTaskManage.triggerNow(name: String): Boolean = triggerNow(name, DEFAULT_GROUP)

/**
 * 获取任务状态（使用默认组）。
 *
 * @param name 任务名称
 * @return 任务状态，如果任务不存在返回 `null`
 */
fun TimeTaskManage.getState(name: String): JobState? = getJobState(name, DEFAULT_GROUP)

// ==================== 批量管理扩展 ====================

/**
 * 暂停指定组的所有任务。
 *
 * @param group 任务组名，默认为 [DEFAULT_GROUP]
 * @return 暂停的任务数量
 */
fun TimeTaskManage.pauseAll(group: String = DEFAULT_GROUP): Int {
    return try {
        val jobKeys = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(group))
        scheduler.pauseJobs(GroupMatcher.jobGroupEquals(group))
        jobKeys.size
    } catch (_: Exception) {
        0
    }
}

/**
 * 恢复指定组的所有任务。
 *
 * @param group 任务组名，默认为 [DEFAULT_GROUP]
 * @return 恢复的任务数量
 */
fun TimeTaskManage.resumeAll(group: String = DEFAULT_GROUP): Int {
    return try {
        val jobKeys = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(group))
        scheduler.resumeJobs(GroupMatcher.jobGroupEquals(group))
        jobKeys.size
    } catch (_: Exception) {
        0
    }
}

/**
 * 删除指定组的所有任务。
 *
 * @param group 任务组名，默认为 [DEFAULT_GROUP]
 * @return 删除的任务数量
 */
fun TimeTaskManage.removeAll(group: String = DEFAULT_GROUP): Int {
    return try {
        val jobKeys = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(group))
        var count = 0
        jobKeys.forEach { jobKey ->
            if (remove(jobKey)) count++
        }
        count
    } catch (_: Exception) {
        0
    }
}

/**
 * 清空所有任务。
 *
 * @return 删除的任务数量
 */
fun TimeTaskManage.clearAll(): Int {
    return try {
        var count = 0
        getAllJobs().forEach { jobKey ->
            if (remove(jobKey)) count++
        }
        count
    } catch (_: Exception) {
        0
    }
}

// ==================== 查询扩展 ====================

/**
 * 获取所有任务组名。
 *
 * @return 组名列表
 */
fun TimeTaskManage.getAllGroupNames(): List<String> {
    return try {
        scheduler.jobGroupNames.toList()
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * 获取指定组的所有任务名称。
 *
 * @param group 任务组名，默认为 [DEFAULT_GROUP]
 * @return 任务名称列表
 */
fun TimeTaskManage.getJobNames(group: String = DEFAULT_GROUP): List<String> {
    return try {
        scheduler.getJobKeys(GroupMatcher.jobGroupEquals(group))
            .map { it.name }
            .toList()
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * 获取所有任务的 JobKey。
 *
 * @return JobKey 列表
 */
fun TimeTaskManage.getAllJobs(): List<JobKey> {
    return try {
        scheduler.jobGroupNames.flatMap { groupName ->
            scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))
        }.toList()
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * 获取指定组的所有任务 JobKey。
 *
 * @param group 任务组名
 * @return JobKey 列表
 */
fun TimeTaskManage.getJobsInGroup(group: String): List<JobKey> {
    return try {
        scheduler.getJobKeys(GroupMatcher.jobGroupEquals(group)).toList()
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * 获取任务的下次执行时间。
 *
 * @param name 任务名称
 * @param group 任务组名，默认为 [DEFAULT_GROUP]
 * @return 下次执行时间（Unix 时间戳，毫秒），如果任务不存在返回 `null`
 */
fun TimeTaskManage.getNextFireTime(name: String, group: String = DEFAULT_GROUP): Long? {
    return try {
        val triggers = scheduler.getTriggersOfJob(JobKey(name, group))
        triggers.firstOrNull()?.nextFireTime?.time
    } catch (_: Exception) {
        null
    }
}

/**
 * 获取任务的上次执行时间。
 *
 * @param name 任务名称
 * @param group 任务组名，默认为 [DEFAULT_GROUP]
 * @return 上次执行时间（Unix 时间戳，毫秒），如果任务未执行过返回 `null`
 */
fun TimeTaskManage.getPreviousFireTime(name: String, group: String = DEFAULT_GROUP): Long? {
    return try {
        val triggers = scheduler.getTriggersOfJob(JobKey(name, group))
        triggers.firstOrNull()?.previousFireTime?.time
    } catch (_: Exception) {
        null
    }
}

/**
 * 获取任务的描述。
 *
 * @param name 任务名称
 * @param group 任务组名，默认为 [DEFAULT_GROUP]
 * @return 任务描述，如果任务不存在返回 `null`
 */
fun TimeTaskManage.getDescription(name: String, group: String = DEFAULT_GROUP): String? {
    return try {
        val triggers = scheduler.getTriggersOfJob(JobKey(name, group))
        triggers.firstOrNull()?.description
    } catch (_: Exception) {
        null
    }
}

/**
 * 获取任务的执行次数（需要 Quartz 支持）。
 *
 * 注意：此方法依赖于 JobDataMap 中的 executionCount 字段，
 * 默认情况下 Quartz 不会自动维护此字段。
 *
 * @param name 任务名称
 * @param group 任务组名，默认为 [DEFAULT_GROUP]
 * @return 执行次数，如果不可用返回 `null`
 */
fun TimeTaskManage.getExecutionCount(name: String, group: String = DEFAULT_GROUP): Int? {
    return try {
        val jobDetail = scheduler.getJobDetail(JobKey(name, group))
        jobDetail?.jobDataMap?.get("executionCount") as? Int
    } catch (_: Exception) {
        null
    }
}

// ==================== 任务信息数据类 ====================

/**
 * 任务信息数据类。
 *
 * @property name 任务名称
 * @property group 任务组名
 * @property description 任务描述
 * @property state 任务状态
 * @property nextFireTime 下次执行时间
 * @property previousFireTime 上次执行时间
 */
data class JobInfo(
    val name: String,
    val group: String,
    val description: String?,
    val state: JobState?,
    val nextFireTime: Long?,
    val previousFireTime: Long?,
)

/**
 * 获取任务的详细信息。
 *
 * @param name 任务名称
 * @param group 任务组名，默认为 [DEFAULT_GROUP]
 * @return 任务信息，如果任务不存在返回 `null`
 */
fun TimeTaskManage.getJobInfo(name: String, group: String = DEFAULT_GROUP): JobInfo? {
    if (!contains(name, group)) return null

    return JobInfo(
        name = name,
        group = group,
        description = getDescription(name, group),
        state = getJobState(name, group),
        nextFireTime = getNextFireTime(name, group),
        previousFireTime = getPreviousFireTime(name, group),
    )
}

/**
 * 获取所有任务的详细信息。
 *
 * @return 任务信息列表
 */
fun TimeTaskManage.getAllJobInfo(): List<JobInfo> {
    return getAllJobs().mapNotNull { jobKey ->
        getJobInfo(jobKey.name, jobKey.group)
    }
}

/**
 * 获取指定组所有任务的详细信息。
 *
 * @param group 任务组名
 * @return 任务信息列表
 */
fun TimeTaskManage.getJobInfoInGroup(group: String): List<JobInfo> {
    return getJobsInGroup(group).mapNotNull { jobKey ->
        getJobInfo(jobKey.name, jobKey.group)
    }
}
