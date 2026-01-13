/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.dsl

import kim.der.timetask.task.TimeTaskManage

/**
 * 任务构建器 DSL，提供流畅的 API 来配置和创建定时任务。
 *
 * 支持三种任务类型：
 * - 倒计时任务：延迟执行一次后自动删除
 * - 间隔任务：按固定间隔重复执行
 * - Cron 任务：按 Cron 表达式调度执行
 *
 * ## 使用示例
 *
 * ```kotlin
 * val manager = TimeTaskManage()
 *
 * // 使用 DSL 创建任务
 * manager.task("myTask") {
 *     group("myGroup")
 *     description("任务描述")
 *     interval(5000)  // 每5秒执行
 *     action { println("执行了！") }
 * }
 * ```
 *
 * @property manager 任务管理器实例
 * @property name 任务名称
 * @param group 任务组名，默认为 "default"
 * @param description 任务描述，默认为空
 * @author Dr (dr@der.kim)
 * @since 1.0.0
 */
class TaskBuilder(
    private val manager: TimeTaskManage,
    private val name: String,
    private var group: String = DEFAULT_GROUP,
    private var description: String = "",
) {
    private var startTime: Long? = null
    private var intervalTime: Long? = null
    private var cronExpression: String? = null
    private var action: (() -> Unit)? = null
    private var isCountdown: Boolean = false
    private var repeatCount: Int = REPEAT_FOREVER

    private companion object {
        const val DEFAULT_GROUP = "default"
        const val REPEAT_FOREVER = -1
    }

    /**
     * 设置任务组名。
     *
     * @param group 组名
     * @return 当前构建器实例，支持链式调用
     */
    fun group(group: String): TaskBuilder {
        this.group = group
        return this
    }

    /**
     * 设置任务描述。
     *
     * @param desc 描述文本
     * @return 当前构建器实例，支持链式调用
     */
    fun description(desc: String): TaskBuilder {
        this.description = desc
        return this
    }

    /**
     * 设置延迟执行时间（创建倒计时任务）。
     *
     * 调用此方法后，任务将在指定延迟后执行一次，然后自动删除。
     *
     * @param millis 延迟毫秒数
     * @return 当前构建器实例，支持链式调用
     * @throws IllegalArgumentException 如果 millis < 0
     */
    fun delay(millis: Long): TaskBuilder {
        require(millis >= 0) { "Delay must be non-negative, got: $millis" }
        this.startTime = System.currentTimeMillis() + millis
        this.isCountdown = true
        return this
    }

    /**
     * 设置任务开始时间。
     *
     * @param timestamp Unix 时间戳（毫秒）
     * @return 当前构建器实例，支持链式调用
     */
    fun startAt(timestamp: Long): TaskBuilder {
        this.startTime = timestamp
        return this
    }

    /**
     * 设置任务开始时间为当前时间加上指定延迟。
     *
     * @param delayMillis 延迟毫秒数
     * @return 当前构建器实例，支持链式调用
     */
    fun startAfter(delayMillis: Long): TaskBuilder {
        require(delayMillis >= 0) { "Delay must be non-negative" }
        this.startTime = System.currentTimeMillis() + delayMillis
        return this
    }

    /**
     * 设置执行间隔（创建间隔任务）。
     *
     * @param millis 间隔毫秒数，必须大于 0
     * @return 当前构建器实例，支持链式调用
     * @throws IllegalArgumentException 如果 millis <= 0
     */
    fun interval(millis: Long): TaskBuilder {
        require(millis > 0) { "Interval must be positive, got: $millis" }
        this.intervalTime = millis
        return this
    }

    /**
     * 设置重复次数。
     *
     * 仅对间隔任务有效。设置后任务将执行指定次数后自动停止。
     *
     * @param count 重复次数，-1 表示无限重复
     * @return 当前构建器实例，支持链式调用
     */
    fun repeatCount(count: Int): TaskBuilder {
        this.repeatCount = count
        return this
    }

    /**
     * 设置 Cron 表达式（创建 Cron 任务）。
     *
     * @param expression Cron 表达式
     * @return 当前构建器实例，支持链式调用
     * @see <a href="http://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html">Cron Trigger Tutorial</a>
     */
    fun cron(expression: String): TaskBuilder {
        this.cronExpression = expression
        return this
    }

    /**
     * 设置要执行的操作。
     *
     * @param block 执行的代码块
     * @return 当前构建器实例，支持链式调用
     */
    fun action(block: () -> Unit): TaskBuilder {
        this.action = block
        return this
    }

    /**
     * 构建并添加任务到管理器。
     *
     * 根据配置自动选择任务类型：
     * 1. 如果调用了 [delay]，创建倒计时任务
     * 2. 如果设置了 [cron]，创建 Cron 任务
     * 3. 如果设置了 [interval]，创建间隔任务
     * 4. 否则创建立即执行一次的任务
     *
     * @throws IllegalStateException 如果未设置 action
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
                    runnable = Runnable(runnable),
                )
            }

            cronExpression != null -> {
                manager.addTimedTask(
                    name = name,
                    group = group,
                    description = description.ifEmpty { "Cron 任务: $name" },
                    cron = cronExpression!!,
                    runnable = Runnable(runnable),
                )
            }

            intervalTime != null -> {
                manager.addTimedTask(
                    name = name,
                    group = group,
                    description = description.ifEmpty { "定时任务: $name" },
                    startTime = startTime ?: System.currentTimeMillis(),
                    intervalTime = intervalTime!!,
                    runnable = Runnable(runnable),
                )
            }

            else -> {
                // 默认立即执行一次
                manager.addCountdown(
                    name = name,
                    group = group,
                    description = description.ifEmpty { "立即执行: $name" },
                    startTime = System.currentTimeMillis() + 1,
                    runnable = Runnable(runnable),
                )
            }
        }
    }
}

/**
 * 使用 DSL 方式添加任务。
 *
 * ## 示例
 *
 * ```kotlin
 * manager.task("heartbeat") {
 *     group("system")
 *     description("心跳检测")
 *     interval(10.seconds)
 *     action { println("心跳") }
 * }
 * ```
 *
 * @param name 任务名称
 * @param block DSL 配置块
 */
fun TimeTaskManage.task(name: String, block: TaskBuilder.() -> Unit) {
    TaskBuilder(this, name).apply(block).build()
}

/**
 * 便捷方法：创建延迟执行的倒计时任务。
 *
 * @param name 任务名称
 * @param delayMillis 延迟毫秒数
 * @param group 任务组名，默认为 "default"
 * @param description 任务描述
 * @param action 要执行的操作
 */
fun TimeTaskManage.delayTask(
    name: String,
    delayMillis: Long,
    group: String = "default",
    description: String = "",
    action: () -> Unit,
) {
    task(name) {
        group(group)
        if (description.isNotEmpty()) description(description)
        delay(delayMillis)
        action(action)
    }
}

/**
 * 便捷方法：创建固定间隔的定时任务。
 *
 * @param name 任务名称
 * @param intervalMillis 执行间隔（毫秒）
 * @param group 任务组名，默认为 "default"
 * @param delayMillis 首次执行延迟（毫秒），默认为 0
 * @param description 任务描述
 * @param action 要执行的操作
 */
fun TimeTaskManage.intervalTask(
    name: String,
    intervalMillis: Long,
    group: String = "default",
    delayMillis: Long = 0,
    description: String = "",
    action: () -> Unit,
) {
    task(name) {
        group(group)
        if (description.isNotEmpty()) description(description)
        if (delayMillis > 0) startAfter(delayMillis)
        interval(intervalMillis)
        action(action)
    }
}

/**
 * 便捷方法：创建 Cron 表达式的定时任务。
 *
 * @param name 任务名称
 * @param cron Cron 表达式
 * @param group 任务组名，默认为 "default"
 * @param description 任务描述
 * @param action 要执行的操作
 */
fun TimeTaskManage.cronTask(
    name: String,
    cron: String,
    group: String = "default",
    description: String = "",
    action: () -> Unit,
) {
    task(name) {
        group(group)
        if (description.isNotEmpty()) description(description)
        cron(cron)
        action(action)
    }
}
