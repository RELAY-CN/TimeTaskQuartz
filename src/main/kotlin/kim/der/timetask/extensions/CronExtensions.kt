/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.extensions

/**
 * 常用的 Cron 表达式常量。
 *
 * 提供预定义的 Cron 表达式，避免手动编写容易出错的表达式。
 *
 * ## Cron 表达式格式
 * ```
 * 秒 分 时 日 月 周 [年]
 * ```
 *
 * ## 使用示例
 * ```kotlin
 * manager.cron("daily-task", CronExpressions.DAILY_MIDNIGHT) {
 *     println("每天凌晨执行")
 * }
 * ```
 *
 * @author Dr (dr@der.kim)
 * @since 1.0.0
 * @see CronBuilder
 */
object CronExpressions {
    // ==================== 秒级 ====================

    /** 每秒执行一次 */
    const val EVERY_SECOND = "0/1 * * * * ?"

    /** 每 5 秒执行一次 */
    const val EVERY_5_SECONDS = "0/5 * * * * ?"

    /** 每 10 秒执行一次 */
    const val EVERY_10_SECONDS = "0/10 * * * * ?"

    /** 每 15 秒执行一次 */
    const val EVERY_15_SECONDS = "0/15 * * * * ?"

    /** 每 30 秒执行一次 */
    const val EVERY_30_SECONDS = "0/30 * * * * ?"

    // ==================== 分钟级 ====================

    /** 每分钟执行一次（在第 0 秒） */
    const val EVERY_MINUTE = "0 * * * * ?"

    /** 每 5 分钟执行一次 */
    const val EVERY_5_MINUTES = "0 */5 * * * ?"

    /** 每 10 分钟执行一次 */
    const val EVERY_10_MINUTES = "0 */10 * * * ?"

    /** 每 15 分钟执行一次 */
    const val EVERY_15_MINUTES = "0 */15 * * * ?"

    /** 每 30 分钟执行一次 */
    const val EVERY_30_MINUTES = "0 */30 * * * ?"

    // ==================== 小时级 ====================

    /** 每小时执行一次（在第 0 分 0 秒） */
    const val EVERY_HOUR = "0 0 * * * ?"

    /** 每 2 小时执行一次 */
    const val EVERY_2_HOURS = "0 0 */2 * * ?"

    /** 每 4 小时执行一次 */
    const val EVERY_4_HOURS = "0 0 */4 * * ?"

    /** 每 6 小时执行一次 */
    const val EVERY_6_HOURS = "0 0 */6 * * ?"

    /** 每 12 小时执行一次 */
    const val EVERY_12_HOURS = "0 0 */12 * * ?"

    // ==================== 每日 ====================

    /** 每天凌晨 0:00 执行 */
    const val DAILY_MIDNIGHT = "0 0 0 * * ?"

    /** 每天早上 6:00 执行 */
    const val DAILY_6AM = "0 0 6 * * ?"

    /** 每天早上 8:00 执行 */
    const val DAILY_8AM = "0 0 8 * * ?"

    /** 每天早上 9:00 执行 */
    const val DAILY_9AM = "0 0 9 * * ?"

    /** 每天中午 12:00 执行 */
    const val DAILY_NOON = "0 0 12 * * ?"

    /** 每天下午 6:00 执行 */
    const val DAILY_6PM = "0 0 18 * * ?"

    /** 每天晚上 9:00 执行 */
    const val DAILY_9PM = "0 0 21 * * ?"

    /** 每天晚上 11:00 执行 */
    const val DAILY_11PM = "0 0 23 * * ?"

    // ==================== 每周 ====================

    /** 每周一凌晨 0:00 执行 */
    const val WEEKLY_MONDAY = "0 0 0 ? * MON"

    /** 每周二凌晨 0:00 执行 */
    const val WEEKLY_TUESDAY = "0 0 0 ? * TUE"

    /** 每周三凌晨 0:00 执行 */
    const val WEEKLY_WEDNESDAY = "0 0 0 ? * WED"

    /** 每周四凌晨 0:00 执行 */
    const val WEEKLY_THURSDAY = "0 0 0 ? * THU"

    /** 每周五凌晨 0:00 执行 */
    const val WEEKLY_FRIDAY = "0 0 0 ? * FRI"

    /** 每周六凌晨 0:00 执行 */
    const val WEEKLY_SATURDAY = "0 0 0 ? * SAT"

    /** 每周日凌晨 0:00 执行 */
    const val WEEKLY_SUNDAY = "0 0 0 ? * SUN"

    /** 工作日（周一至周五）早上 9:00 执行 */
    const val WEEKDAYS_9AM = "0 0 9 ? * MON-FRI"

    /** 周末（周六和周日）早上 10:00 执行 */
    const val WEEKENDS_10AM = "0 0 10 ? * SAT,SUN"

    // ==================== 每月 ====================

    /** 每月 1 号凌晨 0:00 执行 */
    const val MONTHLY_FIRST_DAY = "0 0 0 1 * ?"

    /** 每月 15 号凌晨 0:00 执行 */
    const val MONTHLY_15TH = "0 0 0 15 * ?"

    /** 每月最后一天凌晨 0:00 执行 */
    const val MONTHLY_LAST_DAY = "0 0 0 L * ?"

    // ==================== 每年 ====================

    /** 每年 1 月 1 日凌晨 0:00 执行 */
    const val YEARLY_JANUARY_FIRST = "0 0 0 1 1 ?"

    /** 每年 7 月 1 日凌晨 0:00 执行（年中） */
    const val YEARLY_JULY_FIRST = "0 0 0 1 7 ?"

    /** 每年 12 月 31 日凌晨 0:00 执行（年末） */
    const val YEARLY_DECEMBER_31ST = "0 0 0 31 12 ?"
}

/**
 * Cron 表达式构建器，提供类型安全的方式构建 Cron 表达式。
 *
 * ## 使用示例
 *
 * ```kotlin
 * // 每 30 秒执行
 * val cron1 = CronBuilder.everySeconds(30)
 *
 * // 每天早上 8:30 执行
 * val cron2 = CronBuilder.dailyAt(8, 30)
 *
 * // 每周一早上 9:00 执行
 * val cron3 = CronBuilder.weeklyAt(DayOfWeek.MONDAY, 9, 0)
 * ```
 *
 * @author Dr (dr@der.kim)
 * @since 1.0.0
 * @see CronExpressions
 */
object CronBuilder {
    /**
     * 星期几枚举，用于 [weeklyAt] 方法。
     */
    enum class DayOfWeek(internal val cronValue: String) {
        SUNDAY("SUN"),
        MONDAY("MON"),
        TUESDAY("TUE"),
        WEDNESDAY("WED"),
        THURSDAY("THU"),
        FRIDAY("FRI"),
        SATURDAY("SAT")
    }

    /**
     * 创建每 N 秒执行的 Cron 表达式。
     *
     * @param seconds 间隔秒数，范围 [1, 59]
     * @return Cron 表达式
     * @throws IllegalArgumentException 如果 seconds 超出范围
     */
    fun everySeconds(seconds: Int): String {
        require(seconds in 1..59) { "Seconds must be between 1 and 59, got: $seconds" }
        return "0/$seconds * * * * ?"
    }

    /**
     * 创建每 N 分钟执行的 Cron 表达式。
     *
     * @param minutes 间隔分钟数，范围 [1, 59]
     * @return Cron 表达式
     * @throws IllegalArgumentException 如果 minutes 超出范围
     */
    fun everyMinutes(minutes: Int): String {
        require(minutes in 1..59) { "Minutes must be between 1 and 59, got: $minutes" }
        return "0 */$minutes * * * ?"
    }

    /**
     * 创建每 N 小时执行的 Cron 表达式。
     *
     * @param hours 间隔小时数，范围 [1, 23]
     * @return Cron 表达式
     * @throws IllegalArgumentException 如果 hours 超出范围
     */
    fun everyHours(hours: Int): String {
        require(hours in 1..23) { "Hours must be between 1 and 23, got: $hours" }
        return "0 0 */$hours * * ?"
    }

    /**
     * 创建每天指定时间执行的 Cron 表达式。
     *
     * @param hour 小时，范围 [0, 23]
     * @param minute 分钟，范围 [0, 59]，默认为 0
     * @param second 秒，范围 [0, 59]，默认为 0
     * @return Cron 表达式
     * @throws IllegalArgumentException 如果参数超出范围
     */
    fun dailyAt(hour: Int, minute: Int = 0, second: Int = 0): String {
        validateTime(hour, minute, second)
        return "$second $minute $hour * * ?"
    }

    /**
     * 创建每周指定时间执行的 Cron 表达式。
     *
     * @param dayOfWeek 星期几
     * @param hour 小时，范围 [0, 23]
     * @param minute 分钟，范围 [0, 59]，默认为 0
     * @param second 秒，范围 [0, 59]，默认为 0
     * @return Cron 表达式
     * @throws IllegalArgumentException 如果参数超出范围
     */
    fun weeklyAt(dayOfWeek: DayOfWeek, hour: Int, minute: Int = 0, second: Int = 0): String {
        validateTime(hour, minute, second)
        return "$second $minute $hour ? * ${dayOfWeek.cronValue}"
    }

    /**
     * 创建每周指定时间执行的 Cron 表达式（使用数字表示星期）。
     *
     * @param dayOfWeek 星期几，1=周日, 2=周一, ..., 7=周六
     * @param hour 小时，范围 [0, 23]
     * @param minute 分钟，范围 [0, 59]，默认为 0
     * @param second 秒，范围 [0, 59]，默认为 0
     * @return Cron 表达式
     * @throws IllegalArgumentException 如果参数超出范围
     */
    fun weeklyAt(dayOfWeek: Int, hour: Int, minute: Int = 0, second: Int = 0): String {
        require(dayOfWeek in 1..7) { "Day of week must be between 1 (Sunday) and 7 (Saturday), got: $dayOfWeek" }
        validateTime(hour, minute, second)
        val dayNames = arrayOf("", "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
        return "$second $minute $hour ? * ${dayNames[dayOfWeek]}"
    }

    /**
     * 创建每月指定日期和时间执行的 Cron 表达式。
     *
     * @param dayOfMonth 日期，范围 [1, 31]
     * @param hour 小时，范围 [0, 23]
     * @param minute 分钟，范围 [0, 59]，默认为 0
     * @param second 秒，范围 [0, 59]，默认为 0
     * @return Cron 表达式
     * @throws IllegalArgumentException 如果参数超出范围
     */
    fun monthlyAt(dayOfMonth: Int, hour: Int, minute: Int = 0, second: Int = 0): String {
        require(dayOfMonth in 1..31) { "Day of month must be between 1 and 31, got: $dayOfMonth" }
        validateTime(hour, minute, second)
        return "$second $minute $hour $dayOfMonth * ?"
    }

    /**
     * 创建每月最后一天指定时间执行的 Cron 表达式。
     *
     * @param hour 小时，范围 [0, 23]
     * @param minute 分钟，范围 [0, 59]，默认为 0
     * @param second 秒，范围 [0, 59]，默认为 0
     * @return Cron 表达式
     * @throws IllegalArgumentException 如果参数超出范围
     */
    fun monthlyLastDay(hour: Int, minute: Int = 0, second: Int = 0): String {
        validateTime(hour, minute, second)
        return "$second $minute $hour L * ?"
    }

    /**
     * 创建每年指定日期和时间执行的 Cron 表达式。
     *
     * @param month 月份，范围 [1, 12]
     * @param dayOfMonth 日期，范围 [1, 31]
     * @param hour 小时，范围 [0, 23]
     * @param minute 分钟，范围 [0, 59]，默认为 0
     * @param second 秒，范围 [0, 59]，默认为 0
     * @return Cron 表达式
     * @throws IllegalArgumentException 如果参数超出范围
     */
    fun yearlyAt(month: Int, dayOfMonth: Int, hour: Int, minute: Int = 0, second: Int = 0): String {
        require(month in 1..12) { "Month must be between 1 and 12, got: $month" }
        require(dayOfMonth in 1..31) { "Day of month must be between 1 and 31, got: $dayOfMonth" }
        validateTime(hour, minute, second)
        return "$second $minute $hour $dayOfMonth $month ?"
    }

    /**
     * 创建工作日（周一至周五）指定时间执行的 Cron 表达式。
     *
     * @param hour 小时，范围 [0, 23]
     * @param minute 分钟，范围 [0, 59]，默认为 0
     * @param second 秒，范围 [0, 59]，默认为 0
     * @return Cron 表达式
     */
    fun weekdaysAt(hour: Int, minute: Int = 0, second: Int = 0): String {
        validateTime(hour, minute, second)
        return "$second $minute $hour ? * MON-FRI"
    }

    /**
     * 创建周末（周六和周日）指定时间执行的 Cron 表达式。
     *
     * @param hour 小时，范围 [0, 23]
     * @param minute 分钟，范围 [0, 59]，默认为 0
     * @param second 秒，范围 [0, 59]，默认为 0
     * @return Cron 表达式
     */
    fun weekendsAt(hour: Int, minute: Int = 0, second: Int = 0): String {
        validateTime(hour, minute, second)
        return "$second $minute $hour ? * SAT,SUN"
    }

    private fun validateTime(hour: Int, minute: Int, second: Int) {
        require(hour in 0..23) { "Hour must be between 0 and 23, got: $hour" }
        require(minute in 0..59) { "Minute must be between 0 and 59, got: $minute" }
        require(second in 0..59) { "Second must be between 0 and 59, got: $second" }
    }
}

/**
 * 验证 Cron 表达式是否有效。
 *
 * @param cron 要验证的 Cron 表达式
 * @return 如果表达式有效返回 `true`
 */
fun isValidCron(cron: String): Boolean {
    return try {
        org.quartz.CronExpression.validateExpression(cron)
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * 获取 Cron 表达式的下次触发时间。
 *
 * @param cron Cron 表达式
 * @return 下次触发时间的 Unix 时间戳（毫秒），如果表达式无效返回 `null`
 */
fun getNextFireTime(cron: String): Long? {
    return try {
        val expression = org.quartz.CronExpression(cron)
        expression.getNextValidTimeAfter(java.util.Date())?.time
    } catch (_: Exception) {
        null
    }
}

/**
 * 获取 Cron 表达式的描述（人类可读格式）。
 *
 * @param cron Cron 表达式
 * @return 表达式描述，如果无法解析返回原表达式
 */
fun describeCron(cron: String): String {
    return try {
        val expression = org.quartz.CronExpression(cron)
        expression.expressionSummary
    } catch (_: Exception) {
        cron
    }
}
