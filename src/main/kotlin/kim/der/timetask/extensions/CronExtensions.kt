/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.extensions

/**
 * 常用的 Cron 表达式常量
 */
object CronExpressions {
    /** 每秒执行 */
    const val EVERY_SECOND = "0/1 * * * * ?"
    
    /** 每5秒执行 */
    const val EVERY_5_SECONDS = "0/5 * * * * ?"
    
    /** 每10秒执行 */
    const val EVERY_10_SECONDS = "0/10 * * * * ?"
    
    /** 每30秒执行 */
    const val EVERY_30_SECONDS = "0/30 * * * * ?"
    
    /** 每分钟执行 */
    const val EVERY_MINUTE = "0 * * * * ?"
    
    /** 每5分钟执行 */
    const val EVERY_5_MINUTES = "0 */5 * * * ?"
    
    /** 每10分钟执行 */
    const val EVERY_10_MINUTES = "0 */10 * * * ?"
    
    /** 每30分钟执行 */
    const val EVERY_30_MINUTES = "0 */30 * * * ?"
    
    /** 每小时执行 */
    const val EVERY_HOUR = "0 0 * * * ?"
    
    /** 每天凌晨执行 */
    const val DAILY_MIDNIGHT = "0 0 0 * * ?"
    
    /** 每天中午执行 */
    const val DAILY_NOON = "0 0 12 * * ?"
    
    /** 每周一凌晨执行 */
    const val WEEKLY_MONDAY = "0 0 0 ? * MON"
    
    /** 每月1号凌晨执行 */
    const val MONTHLY_FIRST_DAY = "0 0 0 1 * ?"
    
    /** 每年1月1号凌晨执行 */
    const val YEARLY_JANUARY_FIRST = "0 0 0 1 1 ?"
}

/**
 * 构建 Cron 表达式的辅助函数
 */
object CronBuilder {
    /**
     * 每 N 秒执行
     */
    fun everySeconds(seconds: Int): String = "0/$seconds * * * * ?"
    
    /**
     * 每 N 分钟执行
     */
    fun everyMinutes(minutes: Int): String = "0 */$minutes * * * ?"
    
    /**
     * 每 N 小时执行
     */
    fun everyHours(hours: Int): String = "0 0 */$hours * * ?"
    
    /**
     * 每天指定时间执行
     * @param hour 小时 (0-23)
     * @param minute 分钟 (0-59)
     * @param second 秒 (0-59)，默认为 0
     */
    fun dailyAt(hour: Int, minute: Int = 0, second: Int = 0): String {
        require(hour in 0..23) { "Hour must be between 0 and 23" }
        require(minute in 0..59) { "Minute must be between 0 and 59" }
        require(second in 0..59) { "Second must be between 0 and 59" }
        return "$second $minute $hour * * ?"
    }
    
    /**
     * 每周指定时间执行
     * @param dayOfWeek 星期几 (1=Sunday, 2=Monday, ..., 7=Saturday)
     * @param hour 小时 (0-23)
     * @param minute 分钟 (0-59)
     * @param second 秒 (0-59)，默认为 0
     */
    fun weeklyAt(dayOfWeek: Int, hour: Int, minute: Int = 0, second: Int = 0): String {
        require(dayOfWeek in 1..7) { "Day of week must be between 1 and 7" }
        require(hour in 0..23) { "Hour must be between 0 and 23" }
        require(minute in 0..59) { "Minute must be between 0 and 59" }
        require(second in 0..59) { "Second must be between 0 and 59" }
        val dayNames = arrayOf("", "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
        return "$second $minute $hour ? * ${dayNames[dayOfWeek]}"
    }
    
    /**
     * 每月指定日期和时间执行
     * @param dayOfMonth 日期 (1-31)
     * @param hour 小时 (0-23)
     * @param minute 分钟 (0-59)
     * @param second 秒 (0-59)，默认为 0
     */
    fun monthlyAt(dayOfMonth: Int, hour: Int, minute: Int = 0, second: Int = 0): String {
        require(dayOfMonth in 1..31) { "Day of month must be between 1 and 31" }
        require(hour in 0..23) { "Hour must be between 0 and 23" }
        require(minute in 0..59) { "Minute must be between 0 and 59" }
        require(second in 0..59) { "Second must be between 0 and 59" }
        return "$second $minute $hour $dayOfMonth * ?"
    }
}

