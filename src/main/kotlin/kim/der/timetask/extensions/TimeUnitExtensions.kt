/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.extensions

import java.util.concurrent.TimeUnit

/**
 * 将 Int 转换为毫秒数（秒）
 */
val Int.seconds: Long
    get() = TimeUnit.SECONDS.toMillis(this.toLong())

/**
 * 将 Int 转换为毫秒数（分钟）
 */
val Int.minutes: Long
    get() = TimeUnit.MINUTES.toMillis(this.toLong())

/**
 * 将 Int 转换为毫秒数（小时）
 */
val Int.hours: Long
    get() = TimeUnit.HOURS.toMillis(this.toLong())

/**
 * 将 Int 转换为毫秒数（天）
 */
val Int.days: Long
    get() = TimeUnit.DAYS.toMillis(this.toLong())

/**
 * 将 Long 转换为毫秒数（秒）
 */
val Long.seconds: Long
    get() = TimeUnit.SECONDS.toMillis(this)

/**
 * 将 Long 转换为毫秒数（分钟）
 */
val Long.minutes: Long
    get() = TimeUnit.MINUTES.toMillis(this)

/**
 * 将 Long 转换为毫秒数（小时）
 */
val Long.hours: Long
    get() = TimeUnit.HOURS.toMillis(this)

/**
 * 将 Long 转换为毫秒数（天）
 */
val Long.days: Long
    get() = TimeUnit.DAYS.toMillis(this)

