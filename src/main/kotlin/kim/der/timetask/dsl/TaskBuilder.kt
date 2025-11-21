/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.dsl

import kim.der.timetask.task.TimeTaskManage
import org.quartz.JobKey

/**
 * 任务构建器 DSL
 */
class TaskBuilder(
    private val manager: TimeTaskManage,
    private val name: String,
    private var group: String = "default",
    private var description: String = "",
) {
    private var startTime: Long? = null
    private var intervalTime: Long? = null
    private var cronExpression: String? = null
    private var action: (() -> Unit)? = null
    private var isCountdown: Boolean = false

    /**
     * 设置任务组
     */
    fun group(group: String) {
        this.group = group
    }

    /**
     * 设置任务描述
     */
    fun description(desc: String) {
        this.description = desc
    }

    /**
     * 设置延迟执行（倒计时任务）
     */
    fun delay(millis: Long) {
        this.startTime = System.currentTimeMillis() + millis
        this.isCountdown = true
    }

    /**
     * 设置开始时间
     */
    fun startAt(timestamp: Long) {
        this.startTime = timestamp
    }

    /**
     * 设置执行间隔
     */
    fun interval(millis: Long) {
        this.intervalTime = millis
    }

    /**
     * 设置 Cron 表达式
     */
    fun cron(expression: String) {
        this.cronExpression = expression
    }

    /**
     * 设置要执行的操作
     */
    fun action(block: () -> Unit) {
        this.action = block
    }

    /**
     * 构建并添加任务
     */
    internal fun build() {
        val runnable = action ?: return

        when {
            isCountdown -> {
                manager.addCountdown(
                    name = name,
                    group = group,
                    description = description.ifEmpty { "倒计时任务: $name" },
                    startTime = startTime ?: System.currentTimeMillis(),
                    runnable = java.lang.Runnable(runnable),
                )
            }
            cronExpression != null -> {
                manager.addTimedTask(
                    name = name,
                    group = group,
                    description = description.ifEmpty { "Cron 任务: $name" },
                    cron = cronExpression!!,
                    runnable = java.lang.Runnable(runnable),
                )
            }
            intervalTime != null -> {
                manager.addTimedTask(
                    name = name,
                    group = group,
                    description = description.ifEmpty { "定时任务: $name" },
                    startTime = startTime ?: System.currentTimeMillis(),
                    intervalTime = intervalTime!!,
                    runnable = java.lang.Runnable(runnable),
                )
            }
            else -> {
                // 默认立即执行一次
                manager.addCountdown(
                    name = name,
                    group = group,
                    description = description.ifEmpty { "立即执行: $name" },
                    startTime = System.currentTimeMillis() + 1,
                    runnable = java.lang.Runnable(runnable),
                )
            }
        }
    }
}

/**
 * DSL 方式添加任务
 */
fun TimeTaskManage.task(name: String, block: TaskBuilder.() -> Unit) {
    TaskBuilder(this, name).apply(block).build()
}

/**
 * 便捷方法：延迟执行
 */
fun TimeTaskManage.delayTask(name: String, delayMillis: Long, action: () -> Unit) {
    task(name) {
        delay(delayMillis)
        this.action(action)
    }
}

/**
 * 便捷方法：定时执行
 */
fun TimeTaskManage.intervalTask(name: String, intervalMillis: Long, action: () -> Unit) {
    task(name) {
        interval(intervalMillis)
        this.action(action)
    }
}

/**
 * 便捷方法：Cron 执行
 */
fun TimeTaskManage.cronTask(name: String, cron: String, action: () -> Unit) {
    task(name) {
        cron(cron)
        this.action(action)
    }
}

