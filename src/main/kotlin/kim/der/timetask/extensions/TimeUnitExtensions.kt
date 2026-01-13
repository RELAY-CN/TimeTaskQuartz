/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.extensions

import java.util.concurrent.TimeUnit

// ==================== Int 时间单位扩展 ====================

/**
 * 将 Int 值转换为毫秒数（秒）。
 *
 * ## 示例
 * ```kotlin
 * val delay = 5.seconds  // 5000L
 * manager.delay("task", 10.seconds) { ... }
 * ```
 */
val Int.seconds: Long
    get() = TimeUnit.SECONDS.toMillis(this.toLong())

/**
 * 将 Int 值转换为毫秒数（分钟）。
 *
 * ## 示例
 * ```kotlin
 * val interval = 5.minutes  // 300000L
 * ```
 */
val Int.minutes: Long
    get() = TimeUnit.MINUTES.toMillis(this.toLong())

/**
 * 将 Int 值转换为毫秒数（小时）。
 *
 * ## 示例
 * ```kotlin
 * val interval = 2.hours  // 7200000L
 * ```
 */
val Int.hours: Long
    get() = TimeUnit.HOURS.toMillis(this.toLong())

/**
 * 将 Int 值转换为毫秒数（天）。
 *
 * ## 示例
 * ```kotlin
 * val interval = 1.days  // 86400000L
 * ```
 */
val Int.days: Long
    get() = TimeUnit.DAYS.toMillis(this.toLong())

/**
 * 将 Int 值转换为毫秒数（毫秒，原值返回）。
 *
 * 用于代码可读性，明确表示单位。
 *
 * ## 示例
 * ```kotlin
 * val delay = 500.millis  // 500L
 * ```
 */
val Int.millis: Long
    get() = this.toLong()

// ==================== Long 时间单位扩展 ====================

/**
 * 将 Long 值转换为毫秒数（秒）。
 *
 * ## 示例
 * ```kotlin
 * val delay = 5L.seconds  // 5000L
 * ```
 */
val Long.seconds: Long
    get() = TimeUnit.SECONDS.toMillis(this)

/**
 * 将 Long 值转换为毫秒数（分钟）。
 *
 * ## 示例
 * ```kotlin
 * val interval = 5L.minutes  // 300000L
 * ```
 */
val Long.minutes: Long
    get() = TimeUnit.MINUTES.toMillis(this)

/**
 * 将 Long 值转换为毫秒数（小时）。
 *
 * ## 示例
 * ```kotlin
 * val interval = 2L.hours  // 7200000L
 * ```
 */
val Long.hours: Long
    get() = TimeUnit.HOURS.toMillis(this)

/**
 * 将 Long 值转换为毫秒数（天）。
 *
 * ## 示例
 * ```kotlin
 * val interval = 1L.days  // 86400000L
 * ```
 */
val Long.days: Long
    get() = TimeUnit.DAYS.toMillis(this)

/**
 * 将 Long 值转换为毫秒数（毫秒，原值返回）。
 *
 * 用于代码可读性，明确表示单位。
 *
 * ## 示例
 * ```kotlin
 * val delay = 500L.millis  // 500L
 * ```
 */
val Long.millis: Long
    get() = this

// ==================== 时间格式化扩展 ====================

/**
 * 将毫秒数格式化为人类可读的时间字符串。
 *
 * ## 示例
 * ```kotlin
 * 3661000L.toReadableTime()  // "1h 1m 1s"
 * 5000L.toReadableTime()     // "5s"
 * 500L.toReadableTime()      // "500ms"
 * ```
 *
 * @return 格式化的时间字符串
 */
fun Long.toReadableTime(): String {
    if (this < 0) return "0ms"

    val days = TimeUnit.MILLISECONDS.toDays(this)
    val hours = TimeUnit.MILLISECONDS.toHours(this) % 24
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(this) % 60
    val millis = this % 1000

    return buildString {
        if (days > 0) append("${days}d ")
        if (hours > 0) append("${hours}h ")
        if (minutes > 0) append("${minutes}m ")
        if (seconds > 0) append("${seconds}s ")
        if (millis > 0 && days == 0L && hours == 0L && minutes == 0L && seconds == 0L) {
            append("${millis}ms")
        }
    }.trim().ifEmpty { "0ms" }
}

/**
 * 将毫秒数转换为秒数。
 *
 * @return 秒数
 */
fun Long.toSeconds(): Long = TimeUnit.MILLISECONDS.toSeconds(this)

/**
 * 将毫秒数转换为分钟数。
 *
 * @return 分钟数
 */
fun Long.toMinutes(): Long = TimeUnit.MILLISECONDS.toMinutes(this)

/**
 * 将毫秒数转换为小时数。
 *
 * @return 小时数
 */
fun Long.toHours(): Long = TimeUnit.MILLISECONDS.toHours(this)

/**
 * 将毫秒数转换为天数。
 *
 * @return 天数
 */
fun Long.toDays(): Long = TimeUnit.MILLISECONDS.toDays(this)

// ==================== 时间计算扩展 ====================

/**
 * 获取从现在开始指定毫秒数后的时间戳。
 *
 * ## 示例
 * ```kotlin
 * val futureTime = 5.seconds.fromNow()  // 当前时间 + 5秒
 * ```
 *
 * @return Unix 时间戳（毫秒）
 */
fun Long.fromNow(): Long = System.currentTimeMillis() + this

/**
 * 获取从现在开始指定毫秒数前的时间戳。
 *
 * ## 示例
 * ```kotlin
 * val pastTime = 5.minutes.ago()  // 当前时间 - 5分钟
 * ```
 *
 * @return Unix 时间戳（毫秒）
 */
fun Long.ago(): Long = System.currentTimeMillis() - this
