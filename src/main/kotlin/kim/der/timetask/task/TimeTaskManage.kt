/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.task

import kim.der.timetask.run.RunnableRun
import org.quartz.*
import org.quartz.CronScheduleBuilder.cronSchedule
import org.quartz.impl.StdSchedulerFactory
import java.util.Date
import java.util.Properties
import java.util.TimeZone

/**
 * 定时任务管理器，基于 Quartz Scheduler 封装。
 *
 * 提供简洁的 API 来管理定时任务，支持以下调度方式：
 * - 倒计时任务（一次性执行）
 * - 固定间隔任务（周期性执行）
 * - Cron 表达式任务（基于 Cron 表达式调度）
 *
 * ## 使用示例
 *
 * ```kotlin
 * val manager = TimeTaskManage()
 *
 * // 添加倒计时任务
 * manager.addCountdown("task1", "group1", "描述", System.currentTimeMillis() + 5000) {
 *     println("执行了！")
 * }
 *
 * // 添加定时任务
 * manager.addTimedTask("task2", "group1", "描述", System.currentTimeMillis(), 10000) {
 *     println("每10秒执行")
 * }
 *
 * // 关闭时
 * manager.shutdownNow()
 * ```
 *
 * @property scheduler 内部使用的 Quartz [Scheduler] 实例
 * @author Dr (dr@der.kim)
 * @date 2025-11-21
 * @since 1.0.0
 */
@Suppress("UNUSED")
class TimeTaskManage {
    /**
     * 内部 Quartz 调度器实例。
     *
     * 注意：直接操作此调度器可能导致状态不一致，建议使用本类提供的方法。
     */
    internal val scheduler: Scheduler

    private companion object {
        /** JobDataMap 中存储执行函数的键名 */
        private const val RUN_DATA_KEY = "run"

        /** 默认线程池大小 */
        private const val DEFAULT_THREAD_POOL_SIZE = 5

        /** 最小线程池大小 */
        private const val MIN_THREAD_POOL_SIZE = 1

        /** 最大线程池大小 */
        private const val MAX_THREAD_POOL_SIZE = 100
    }

    /**
     * 使用默认配置创建任务管理器。
     *
     * 默认配置：
     * - 线程池大小：5
     * - 使用 RAMJobStore（内存存储）
     * - 禁用更新检查
     *
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    constructor() : this(DEFAULT_THREAD_POOL_SIZE)

    /**
     * 使用指定线程池大小创建任务管理器。
     *
     * @param threadPoolSize 线程池大小，范围 [1, 100]，默认为 5
     * @throws IllegalArgumentException 如果线程池大小超出范围
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    constructor(threadPoolSize: Int) {
        require(threadPoolSize in MIN_THREAD_POOL_SIZE..MAX_THREAD_POOL_SIZE) {
            "Thread pool size must be between $MIN_THREAD_POOL_SIZE and $MAX_THREAD_POOL_SIZE, got: $threadPoolSize"
        }

        val props = Properties().apply {
            setProperty("org.quartz.scheduler.instanceName", "TimeTaskQuartz-${System.nanoTime()}")
            setProperty("org.quartz.scheduler.skipUpdateCheck", "true")
            setProperty("org.quartz.threadPool.threadCount", threadPoolSize.toString())
            setProperty("org.quartz.threadPool.threadPriority", Thread.NORM_PRIORITY.toString())
            setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore")
            setProperty("org.quartz.jobStore.misfireThreshold", "60000")
        }

        scheduler = StdSchedulerFactory(props).scheduler
        scheduler.start()
    }

    /**
     * 使用自定义 Quartz 配置创建任务管理器。
     *
     * @param properties Quartz 配置属性
     * @see <a href="http://www.quartz-scheduler.org/documentation/quartz-2.3.0/configuration/">Quartz Configuration</a>
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    constructor(properties: Properties) {
        scheduler = StdSchedulerFactory(properties).scheduler
        scheduler.start()
    }

    /**
     * 检查调度器是否正在运行。
     *
     * @return 如果调度器已启动且未关闭，返回 `true`
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    val isRunning: Boolean
        get() = scheduler.isStarted && !scheduler.isShutdown

    /**
     * 检查调度器是否处于待机模式。
     *
     * @return 如果调度器处于待机模式，返回 `true`
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    val isStandby: Boolean
        get() = scheduler.isInStandbyMode

    /**
     * 获取当前任务总数。
     *
     * 查询过程中若 Quartz 抛出异常，当前实现会返回 `0`，避免把状态探测失败扩散给调用方。
     *
     * @return 所有组中的任务总数；查询失败时返回 `0`
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    val jobCount: Int
        get() = try {
            scheduler.jobGroupNames.sumOf { groupName ->
                scheduler.getJobKeys(
                    org.quartz.impl.matchers.GroupMatcher.jobGroupEquals(groupName)
                ).size
            }
        } catch (_: Exception) {
            0
        }

    /**
     * 立即关闭任务调度器。
     *
     * 此方法不会等待当前正在执行的任务完成后再返回。
     * 关闭后，此管理器实例不可再使用。
     *
     * @see shutdown
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    fun shutdownNow() {
        if (!scheduler.isShutdown) {
            scheduler.shutdown(false)
        }
    }

    /**
     * 关闭任务调度器。
     *
     * @param waitForJobsToComplete 是否等待正在执行的任务完成
     *        - `true`：等待所有任务完成后关闭
     *        - `false`：立即关闭，可能中断正在执行的任务
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    fun shutdown(waitForJobsToComplete: Boolean = true) {
        if (!scheduler.isShutdown) {
            scheduler.shutdown(waitForJobsToComplete)
        }
    }

    /**
     * 将调度器置于待机模式。
     *
     * 待机模式下，调度器不会触发任何任务，但任务定义仍然保留。
     * 可以通过 [resume] 恢复调度。
     *
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    fun standby() {
        scheduler.standby()
    }

    /**
     * 从待机模式恢复调度器。
     *
     * @see standby
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    fun resume() {
        if (scheduler.isInStandbyMode) {
            scheduler.start()
        }
    }

    /**
     * 创建一个倒计时任务（一次性任务）。
     *
     * 任务在指定时间执行一次后自动删除。
     * 底层 Quartz Job 会以 durable 形式登记，因此回调执行完成后会主动移除任务定义与触发器。
     *
     * @param name 任务名称，在同一组内必须唯一
     * @param group 任务组名
     * @param description 任务描述
     * @param startTime 任务执行时间（Unix 时间戳，毫秒）
     * @param runnable 要执行的操作
     * @throws SchedulerException 如果任务添加失败
     *
     * ## 示例
     * ```kotlin
     * // 5秒后执行
     * manager.addCountdown("reminder", "alerts", "提醒任务",
     *     System.currentTimeMillis() + 5000) {
     *     println("时间到！")
     * }
     * ```
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    fun addCountdown(
        name: String,
        group: String,
        description: String,
        startTime: Long,
        runnable: Runnable,
    ) {
        val key = JobKey(name, group)
        val trigger =
            buildTrigger(
                key = key,
                description = description,
                action = {
                    try {
                        runnable.run()
                    } finally {
                        // 倒计时任务只执行一次；即使回调抛异常，也要拆掉 durable Job 与 trigger。
                        remove(key)
                    }
                },
            ) { startAt(Date(startTime)) }

        scheduleJob(key, trigger)
    }

    /**
     * 创建一个固定间隔的定时任务。
     *
     * 任务从指定时间开始，按固定间隔重复执行，直到手动删除。
     *
     * @param name 任务名称，在同一组内必须唯一
     * @param group 任务组名
     * @param description 任务描述
     * @param startTime 首次执行时间（Unix 时间戳，毫秒）
     * @param intervalTime 执行间隔（毫秒），必须大于 0
     * @param runnable 要执行的操作
     * @throws IllegalArgumentException 如果 intervalTime <= 0
     * @throws SchedulerException 如果任务添加失败
     *
     * ## 示例
     * ```kotlin
     * // 每10秒执行一次
     * manager.addTimedTask("heartbeat", "system", "心跳检测",
     *     System.currentTimeMillis(), 10000) {
     *     println("心跳")
     * }
     * ```
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    fun addTimedTask(
        name: String,
        group: String,
        description: String,
        startTime: Long,
        intervalTime: Long,
        runnable: Runnable,
    ) = addTimedTask(
        name = name,
        group = group,
        description = description,
        startTime = startTime,
        intervalTime = intervalTime,
        repeatCount = SimpleTrigger.REPEAT_INDEFINITELY,
        runnable = runnable,
    )

    /**
     * 创建一个固定间隔的定时任务。
     *
     * @param name 任务名称，在同一组内必须唯一
     * @param group 任务组名
     * @param description 任务描述
     * @param startTime 首次执行时间（Unix 时间戳，毫秒）
     * @param intervalTime 执行间隔（毫秒），必须大于 0
     * @param repeatCount 重复次数，`-1` 表示无限重复；Quartz 不把首次执行计入重复次数
     * @param runnable 要执行的操作
     * @throws IllegalArgumentException 如果 intervalTime <= 0 或 repeatCount < -1
     * @throws SchedulerException 如果任务添加失败
     *
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    fun addTimedTask(
        name: String,
        group: String,
        description: String,
        startTime: Long,
        intervalTime: Long,
        repeatCount: Int,
        runnable: Runnable,
    ) {
        require(intervalTime > 0) { "Interval time must be positive, got: $intervalTime" }
        require(repeatCount >= SimpleTrigger.REPEAT_INDEFINITELY) {
            "Repeat count must be -1 or non-negative, got: $repeatCount"
        }

        val key = JobKey(name, group)
        val trigger =
            buildTrigger(
                key = key,
                description = description,
                action = { runnable.run() },
            ) {
                withSchedule(
                    SimpleScheduleBuilder
                        .simpleSchedule()
                        .withIntervalInMilliseconds(intervalTime)
                        .withRepeatCount(repeatCount)
                        .withMisfireHandlingInstructionNextWithExistingCount(),
                )
                startAt(Date(startTime))
            }

        scheduleJob(key, trigger, storeDurably = repeatCount == SimpleTrigger.REPEAT_INDEFINITELY)
    }

    /**
     * 创建一个基于 Cron 表达式的定时任务。
     *
     * 使用标准 Cron 表达式定义执行时间，支持秒级精度。
     *
     * @param name 任务名称，在同一组内必须唯一
     * @param group 任务组名
     * @param description 任务描述
     * @param cron Cron 表达式（6位或7位）
     * @param timeZone 时区，默认为系统默认时区
     * @param runnable 要执行的操作
     * @throws IllegalArgumentException 如果 Cron 表达式无效
     * @throws SchedulerException 如果任务添加失败
     *
     * ## Cron 表达式格式
     * ```
     * 秒 分 时 日 月 周 [年]
     * ```
     *
     * ## 示例
     * ```kotlin
     * // 每天凌晨执行
     * manager.addTimedTask("cleanup", "maintenance", "清理任务",
     *     "0 0 0 * * ?") {
     *     println("执行清理")
     * }
     * ```
     *
     * @see <a href="http://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html">Cron Trigger Tutorial</a>
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    fun addTimedTask(
        name: String,
        group: String,
        description: String,
        cron: String,
        timeZone: TimeZone = TimeZone.getDefault(),
        runnable: Runnable,
    ) {
        val key = JobKey(name, group)
        val trigger =
            buildTrigger(
                key = key,
                description = description,
                action = { runnable.run() },
            ) {
                withSchedule(
                    cronSchedule(cron)
                        .withMisfireHandlingInstructionFireAndProceed()
                        .inTimeZone(timeZone),
                )
            }

        scheduleJob(key, trigger, storeDurably = true)
    }

    /**
     * 立即触发一次指定任务。
     *
     * 无论任务当前的调度状态如何，都会立即执行一次。
     * 这不会影响任务的正常调度。
     *
     * @param jobKey 任务的 [JobKey]
     * @return 如果触发成功返回 `true`；任务不存在或 Quartz 触发失败时返回 `false`
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    fun triggerNow(jobKey: JobKey): Boolean {
        return try {
            if (contains(jobKey)) {
                scheduler.triggerJob(jobKey)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 立即触发一次指定任务。
     *
     * @param name 任务名称
     * @param group 任务组名
     * @return 如果触发成功返回 `true`；任务不存在或 Quartz 触发失败时返回 `false`
     * @see triggerNow(JobKey)
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    fun triggerNow(name: String, group: String): Boolean = triggerNow(JobKey(name, group))

    /**
     * 检查指定任务是否存在。
     *
     * @param jobKey 任务的 [JobKey]
     * @return 如果任务存在返回 `true`
     * @throws SchedulerException 当 Quartz 查询任务状态失败时抛出
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    fun contains(jobKey: JobKey): Boolean = scheduler.checkExists(jobKey)

    /**
     * 检查指定任务是否存在。
     *
     * @param name 任务名称
     * @param group 任务组名
     * @return 如果任务存在返回 `true`
     * @throws SchedulerException 当 Quartz 查询任务状态失败时抛出
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    fun contains(name: String, group: String): Boolean = contains(JobKey(name, group))

    /**
     * 暂停指定任务。
     *
     * 暂停后任务不会被触发，但任务定义仍然保留。
     * 可以通过 [resume] 恢复任务。
     *
     * @param jobKey 任务的 [JobKey]
     * @return 如果暂停成功返回 `true`；任务不存在或 Quartz 暂停失败时返回 `false`
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    fun pause(jobKey: JobKey): Boolean {
        return try {
            if (contains(jobKey)) {
                scheduler.pauseJob(jobKey)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 暂停指定任务。
     *
     * @param name 任务名称
     * @param group 任务组名
     * @return 如果暂停成功返回 `true`；任务不存在或 Quartz 暂停失败时返回 `false`
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    fun pause(name: String, group: String): Boolean = pause(JobKey(name, group))

    /**
     * 恢复已暂停的任务。
     *
     * @param jobKey 任务的 [JobKey]
     * @return 如果恢复成功返回 `true`；任务不存在或 Quartz 恢复失败时返回 `false`
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    fun resume(jobKey: JobKey): Boolean {
        return try {
            if (contains(jobKey)) {
                scheduler.resumeJob(jobKey)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 恢复已暂停的任务。
     *
     * @param name 任务名称
     * @param group 任务组名
     * @return 如果恢复成功返回 `true`；任务不存在或 Quartz 恢复失败时返回 `false`
     * @see resume(JobKey)
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    fun resume(name: String, group: String): Boolean = resume(JobKey(name, group))

    /**
     * 删除指定任务。
     *
     * 删除后任务将不再执行，且无法恢复。
     *
     * @param jobKey 任务的 [JobKey]
     * @return 如果删除成功返回 `true`；任务不存在或 Quartz 删除失败时返回 `false`
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    fun remove(jobKey: JobKey): Boolean {
        return try {
            // 先暂停再拆 trigger / job，避免删除窗口里再次被调度器拉起。
            pause(jobKey)
            scheduler.unscheduleJob(TriggerKey.triggerKey(jobKey.name, jobKey.group))
            scheduler.deleteJob(jobKey)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 删除指定任务。
     *
     * @param name 任务名称
     * @param group 任务组名
     * @return 如果删除成功返回 `true`；任务不存在或 Quartz 删除失败时返回 `false`
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    fun remove(name: String, group: String): Boolean = remove(JobKey(name, group))

    /**
     * 更新任务的执行间隔。
     *
     * 仅适用于使用 SimpleSchedule 的任务（通过 [addTimedTask] 创建的间隔任务）。
     * 当前实现会重建一个新 trigger，并从“调用 reschedule 的当前时刻”重新开始计算下一次触发时间。
     *
     * @param name 任务名称
     * @param group 任务组名
     * @param newIntervalMillis 新的执行间隔（毫秒）
     * @return 如果更新成功返回 `true`；任务不存在、类型不匹配或 Quartz 重建失败时返回 `false`
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    fun reschedule(name: String, group: String, newIntervalMillis: Long): Boolean {
        require(newIntervalMillis > 0) { "Interval must be positive" }

        return try {
            val triggerKey = TriggerKey.triggerKey(name, group)
            val oldTrigger = scheduler.getTrigger(triggerKey) ?: return false

            val newTrigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .withDescription(oldTrigger.description)
                .usingJobData(oldTrigger.jobDataMap)
                .withSchedule(
                    SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMilliseconds(newIntervalMillis)
                        .repeatForever()
                        .withMisfireHandlingInstructionNextWithExistingCount()
                )
                // 重建后从当前时刻重新起算，避免沿用旧 trigger 已经过期的首触发时间。
                .startNow()
                .build()

            scheduler.rescheduleJob(triggerKey, newTrigger) != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 更新任务的 Cron 表达式。
     *
     * 仅适用于使用 CronSchedule 的任务。
     * 当前实现会基于系统默认时区重建 trigger，不会保留旧 trigger 上的自定义时区设置。
     *
     * @param name 任务名称
     * @param group 任务组名
     * @param newCron 新的 Cron 表达式
     * @return 如果更新成功返回 `true`；任务不存在、表达式无效或 Quartz 重建失败时返回 `false`
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    fun reschedule(name: String, group: String, newCron: String): Boolean {
        return try {
            val triggerKey = TriggerKey.triggerKey(name, group)
            val oldTrigger = scheduler.getTrigger(triggerKey) ?: return false

            val newTrigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .withDescription(oldTrigger.description)
                .usingJobData(oldTrigger.jobDataMap)
                .withSchedule(
                    cronSchedule(newCron)
                        .withMisfireHandlingInstructionFireAndProceed()
                        // 历史行为固定回落到系统默认时区，而不是继承旧 trigger 的配置。
                        .inTimeZone(TimeZone.getDefault())
                )
                .build()

            scheduler.rescheduleJob(triggerKey, newTrigger) != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 获取任务的当前状态。
     *
     * @param name 任务名称
     * @param group 任务组名
     * @return 任务状态；任务不存在或 Quartz 查询失败时返回 `null`
     * @author Dr (dr@der.kim)
     * @date 2025-11-21
     */
    fun getJobState(name: String, group: String): JobState? {
        return try {
            val triggerKey = TriggerKey.triggerKey(name, group)
            val state = scheduler.getTriggerState(triggerKey)
            when (state) {
                Trigger.TriggerState.NORMAL -> JobState.NORMAL
                Trigger.TriggerState.PAUSED -> JobState.PAUSED
                Trigger.TriggerState.BLOCKED -> JobState.BLOCKED
                Trigger.TriggerState.COMPLETE -> JobState.COMPLETE
                Trigger.TriggerState.ERROR -> JobState.ERROR
                Trigger.TriggerState.NONE -> null
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildTrigger(
        key: JobKey,
        description: String,
        action: () -> Unit,
        configure: TriggerBuilder<Trigger>.() -> Unit,
    ): Trigger =
        TriggerBuilder
            .newTrigger()
            .usingJobData(JobDataMap().apply { put(RUN_DATA_KEY, action) })
            .withDescription(description)
            .withIdentity(key.name, key.group)
            .apply(configure)
            .build()

    private fun scheduleJob(
        key: JobKey,
        trigger: Trigger,
        storeDurably: Boolean = true,
    ) {
        val jobDetail =
            JobBuilder
                .newJob(RunnableRun::class.java)
                .withIdentity(key)
                // triggerNow 直接触发 JobDetail，不携带原 Trigger 的 JobDataMap；
                // 同步写入回调可保证计划触发和手动触发都执行同一业务动作。
                .usingJobData(JobDataMap(trigger.jobDataMap))
                .requestRecovery(true)
                .apply { if (storeDurably) storeDurably(true) }
                .build()

        scheduler.scheduleJob(jobDetail, trigger)
    }
}

/**
 * 任务状态枚举。
 *
 * @author Dr (dr@der.kim)
 * @date 2025-11-21
 * @since 1.1.0
 */
enum class JobState {
    /** 正常运行中 */
    NORMAL,
    /** 已暂停 */
    PAUSED,
    /** 被阻塞（正在执行中） */
    BLOCKED,
    /** 已完成 */
    COMPLETE,
    /** 错误状态 */
    ERROR
}
