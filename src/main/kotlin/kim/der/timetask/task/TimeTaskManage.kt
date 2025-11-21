/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.task

import kim.der.timetask.run.RunnableRun
import org.quartz.*
import org.quartz.CronScheduleBuilder.cronSchedule
import org.quartz.impl.StdSchedulerFactory
import java.util.Date
import java.util.TimeZone

/**
 * 定时任务管理, 基于 Quartz
 *
 * @property scheduler Scheduler
 * @author Dr (dr@der.kim)
 */
@Suppress("UNUSED")
class TimeTaskManage {
    internal val scheduler: Scheduler = StdSchedulerFactory().scheduler

    private companion object {
        private const val RUN_DATA_KEY = "run"
    }

    init {
        this.scheduler.start()
    }

    fun shutdownNow() {
        scheduler.shutdown(true)
    }

    /**
     * 创建一个定时计时器
     * @param name 名字
     * @param group 组
     * @param description 描述
     * @param startTime 何时开始
     * @param runnable Runnable
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
                    runnable.run()
                    remove(key)
                },
            ) { startAt(Date(startTime)) }

        scheduleJob(key, trigger)
    }

    /**
     * 创建一个定时循环计时器
     * @param name 名字
     * @param group 组
     * @param description 描述
     * @param intervalTime 执行间隔
     * @param startTime 何时开始
     * @param runnable Runnable
     */
    fun addTimedTask(
        name: String,
        group: String,
        description: String,
        startTime: Long,
        intervalTime: Long,
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
                    SimpleScheduleBuilder
                        .simpleSchedule()
                        .withIntervalInMilliseconds(intervalTime)
                        .repeatForever()
                        .withMisfireHandlingInstructionNextWithExistingCount(),
                )
                startAt(Date(startTime))
            }

        scheduleJob(key, trigger)
    }

    /**
     * 创建一个定时循环计时器
     * @param name 名字
     * @param group 组
     * @param description 描述
     * @param cron Cron 表达式
     * @param runnable Runnable
     */
    fun addTimedTask(
        name: String,
        group: String,
        description: String,
        cron: String,
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
                        .inTimeZone(TimeZone.getDefault()),
                )
            }

        scheduleJob(key, trigger)
    }

    /**
     * 查找是否有对应的任务
     *
     * @param jobKey 任务 [JobKey] 实例
     * @return 是否存在
     */
    fun contains(jobKey: JobKey): Boolean = scheduler.checkExists(jobKey)

    /**
     * 查找是否有对应的任务
     *
     * @param name 任务名字
     * @param group 任务组
     * @return 是否存在
     */
    fun contains(
        name: String,
        group: String,
    ): Boolean = contains(JobKey(name, group))

    /**
     * 暂停对应的任务
     *
     * @param jobKey 任务 [JobKey] 实例
     */
    fun pause(jobKey: JobKey) {
        scheduler.pauseJob(jobKey)
    }

    /**
     * 暂停对应的任务
     *
     * @param name 任务名字
     * @param group 任务组
     */
    fun pause(
        name: String,
        group: String,
    ) {
        pause(JobKey(name, group))
    }

    /**
     * 取消暂停对应的任务
     *
     * @param jobKey 任务 [JobKey] 实例
     */
    private fun unPause(jobKey: JobKey) {
        scheduler.resumeJob(jobKey)
    }

    /**
     * 取消暂停对应的任务
     *
     * @param name 任务名字
     * @param group 任务组
     */
    fun unPause(
        name: String,
        group: String,
    ) {
        unPause(JobKey(name, group))
    }

    /**
     * 取消对应的任务
     *
     * @param jobKey 任务 [JobKey] 实例
     * @return 是否取消
     */
    fun remove(jobKey: JobKey): Boolean {
        pause(jobKey)
        scheduler.unscheduleJob(TriggerKey.triggerKey(jobKey.name, jobKey.group))
        return scheduler.deleteJob(jobKey)
    }

    /**
     * 取消对应的任务
     *
     * @param name 任务名字
     * @param group 任务组
     * @return 是否取消
     */
    fun remove(
        name: String,
        group: String,
    ): Boolean = remove(JobKey(name, group))

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

    private fun scheduleJob(key: JobKey, trigger: Trigger) {
        val jobDetail =
            JobBuilder
                .newJob(RunnableRun::class.java)
                .withIdentity(key)
                .requestRecovery(true)
                .storeDurably(true)
                .build()

        scheduler.scheduleJob(jobDetail, trigger)
    }
}
