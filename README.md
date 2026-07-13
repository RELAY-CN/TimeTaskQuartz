# TimeTaskQuartz

基于 **Quartz** 的 Kotlin 定时任务管理库，为 `IronCore` 提供简单易用的定时任务支持。

## 特性

- 🎯 **简单易用** - 提供简洁的 API，无需深入了解 Quartz 的复杂配置
- ⏰ **多种调度方式** - 支持倒计时、固定间隔、Cron 表达式三种调度方式
- 🔄 **任务管理** - 支持任务的添加、暂停、恢复、删除、重调度和查询
- 🛡️ **稳定可靠** - 基于成熟的 Quartz 2.5.1，默认使用内存任务存储
- 📦 **轻量级** - 仅依赖 Quartz，任务回调保存在当前进程内
- 🧩 **Kotlin DSL** - 提供流畅的 DSL 语法创建任务
- ⚡ **性能优化** - 可配置线程池大小，优化资源使用

## 快速开始

### 添加依赖

```kotlin
// build.gradle.kts
// 当前发布版本号使用 Git short hash，例如 90845eb
dependencies {
    implementation("kim.der:TimeTaskQuartz:版本号")
}
```

TimeTaskQuartz 传递依赖 Quartz 与 `slf4j-api`。应用侧如果需要日志输出，请自行接入一个 SLF4J 实现，例如 Logback：

```kotlin
dependencies {
    implementation("ch.qos.logback:logback-classic:版本号")
}
```

升级已有项目时，请先阅读 [迁移指南](MIGRATION.md)。

### 基本使用

```kotlin
import kim.der.timetask.task.TimeTaskManage

// 创建任务管理器（默认5个线程）
val taskManager = TimeTaskManage()

// 或指定线程池大小
val taskManagerWithMoreThreads = TimeTaskManage(threadPoolSize = 10)
```

> 默认构造函数使用 `RAMJobStore`，任务和进程内 Lambda 不会在进程重启后恢复。
> 即使通过 `Properties` 配置其他 JobStore，本库也不提供 Lambda 的跨进程持久化恢复能力。

#### 传统 API

```kotlin
// 1. 添加倒计时任务（执行一次后自动删除）
taskManager.addCountdown(
    name = "myCountdown",
    group = "default",
    description = "倒计时任务",
    startTime = System.currentTimeMillis() + 5000, // 5秒后执行
) {
    println("倒计时任务执行了！")
}

// 2. 添加固定间隔的定时任务
taskManager.addTimedTask(
    name = "myTimedTask",
    group = "default",
    description = "每10秒执行一次",
    startTime = System.currentTimeMillis(),
    intervalTime = 10000, // 间隔10秒
) {
    println("定时任务执行了！")
}

// 3. 添加 Cron 表达式的定时任务
taskManager.addTimedTask(
    name = "myCronTask",
    group = "default",
    description = "每天凌晨执行",
    cron = "0 0 0 * * ?", // Cron 表达式
) {
    println("Cron 任务执行了！")
}

// 任务管理
// 检查任务是否存在
if (taskManager.contains("myTimedTask", "default")) {
    // 暂停任务
    taskManager.pause("myTimedTask", "default")

    // 恢复任务
    taskManager.resume("myTimedTask", "default")

    // 立即触发一次
    taskManager.triggerNow("myTimedTask", "default")

    // 重调度（修改间隔）
    taskManager.reschedule("myTimedTask", "default", 5000)

    // 获取任务状态
    val state = taskManager.getJobState("myTimedTask", "default")

    // 删除任务
    taskManager.remove("myTimedTask", "default")
}

// 关闭任务管理器
taskManager.shutdownNow()
```

#### Kotlin 扩展 & DSL

```kotlin
import kim.der.timetask.dsl.cronTask
import kim.der.timetask.dsl.delayTask
import kim.der.timetask.dsl.intervalTask
import kim.der.timetask.dsl.task
import kim.der.timetask.extensions.CronExpressions
import kim.der.timetask.extensions.cron
import kim.der.timetask.extensions.delay
import kim.der.timetask.extensions.every
import kim.der.timetask.extensions.hours
import kim.der.timetask.extensions.minutes
import kim.der.timetask.extensions.runAt
import kim.der.timetask.extensions.runNow
import kim.der.timetask.extensions.seconds
import kim.der.timetask.task.TimeTaskManage

val taskManager = TimeTaskManage()

// 延迟 5 秒执行
taskManager.delay("warmup", 5.seconds) {
    println("ready.")
}

// 立即执行一次
taskManager.runNow("immediate") {
    println("立即执行")
}

// 在指定时间执行
taskManager.runAt("scheduled", System.currentTimeMillis() + 10.minutes) {
    println("10分钟后执行")
}

// 每分钟执行，先延迟 10 秒
taskManager.every(
    name = "heartbeat",
    intervalMillis = 1.minutes,
    delayMillis = 10.seconds,
) { println("tick") }

// 使用 Cron 常量
taskManager.cron("daily-report", CronExpressions.DAILY_NOON) {
    println("generate daily report")
}

// DSL 写法
taskManager.task("dsl-example") {
    group("custom")
    description("每小时执行一次")
    interval(1.hours)
    action { println("dsl task running") }
}

// 链式 DSL
taskManager.task("chained") {
    group("system")
        .description("链式调用")
        .startAfter(5.seconds)
        .interval(30.seconds)
        .repeatCount(2) // 首次执行后再重复 2 次，总共执行 3 次；-1 表示无限重复
        .action { println("chained task") }
}

// 便捷函数
taskManager.delayTask("delay", 5.seconds) { println("delayed") }
taskManager.intervalTask("interval", 10.seconds) { println("interval") }
taskManager.cronTask("cron", CronExpressions.EVERY_MINUTE) { println("cron") }
```

#### 批量管理

```kotlin
// 暂停默认组所有任务
taskManager.pauseAll()

// 恢复指定组所有任务
taskManager.resumeAll("myGroup")

// 删除组内所有任务
taskManager.removeAll("myGroup")

// 清空所有任务
taskManager.clearAll()

// 查询任务
val groups = taskManager.getAllGroupNames()
val jobNames = taskManager.getJobNames("default")
val allJobs = taskManager.getAllJobs()

// 获取任务详细信息
val jobInfo = taskManager.getJobInfo("myTask")
val allJobInfo = taskManager.getAllJobInfo()
```

## API 文档

### TimeTaskManage

定时任务管理器，基于 Quartz Scheduler 封装。

#### 构造函数

| 构造函数 | 说明 |
|---------|------|
| `TimeTaskManage()` | 使用默认配置（5个线程） |
| `TimeTaskManage(threadPoolSize: Int)` | 指定线程池大小（1-100） |
| `TimeTaskManage(properties: Properties)` | 使用自定义 Quartz 配置 |

#### 属性

| 属性 | 类型 | 说明 |
|-----|------|------|
| `isRunning` | `Boolean` | 调度器是否正在运行 |
| `isStandby` | `Boolean` | 调度器是否处于待机模式 |
| `jobCount` | `Int` | 当前任务总数 |

#### 添加任务

| 方法 | 说明 |
|-----|------|
| `addCountdown(name, group, description, startTime, runnable)` | 添加一次性倒计时任务，执行后自动删除 |
| `addTimedTask(name, group, description, startTime, intervalTime, runnable)` | 添加固定间隔的定时任务 |
| `addTimedTask(name, group, description, startTime, intervalTime, repeatCount, runnable)` | 添加固定次数的间隔任务；`repeatCount` 不包含首次执行 |
| `addTimedTask(name, group, description, cron, timeZone?, runnable)` | 添加 Cron 表达式的定时任务 |

#### 任务管理

| 方法 | 返回值 | 说明 |
|-----|-------|------|
| `contains(name, group)` | `Boolean` | 检查任务是否存在 |
| `pause(name, group)` | `Boolean` | 暂停任务 |
| `resume(name, group)` | `Boolean` | 恢复暂停的任务 |
| `remove(name, group)` | `Boolean` | 删除任务 |
| `triggerNow(name, group)` | `Boolean` | 额外触发一次任务，不改变原定调度 |
| `reschedule(name, group, newIntervalMillis)` | `Boolean` | 更新间隔任务并保留有限任务的剩余重复次数；类型不匹配时返回 `false` |
| `reschedule(name, group, newCron)` | `Boolean` | 更新 Cron 任务并保留原时区；类型不匹配时返回 `false` |
| `getJobState(name, group)` | `JobState?` | 获取任务状态 |

#### 调度器控制

| 方法 | 说明 |
|-----|------|
| `shutdown(waitForJobsToComplete)` | 关闭调度器 |
| `shutdownNow()` | 立即关闭调度器（不等待当前任务完成） |
| `standby()` | 将调度器置于待机模式 |
| `resume()` | 从待机模式恢复 |

### JobState 枚举

| 值 | 说明 |
|---|------|
| `NORMAL` | 正常运行中 |
| `PAUSED` | 已暂停 |
| `BLOCKED` | 被阻塞（正在执行中） |
| `COMPLETE` | 已完成 |
| `ERROR` | 错误状态 |

### 扩展函数（`kim.der.timetask.extensions`）

#### 任务创建

| 方法 | 说明 |
|-----|------|
| `delay(name, delayMillis, description?, action)` | 延迟执行一次（默认组） |
| `runNow(name, description?, action)` | 立即执行一次 |
| `runAt(name, timestamp, description?, action)` | 在指定时间执行一次 |
| `every(name, intervalMillis, delayMillis?, description?, action)` | 固定间隔任务 |
| `cron(name, cron, description?, action)` | Cron 任务 |

#### 单任务管理（默认组）

| 方法 | 说明 |
|-----|------|
| `contains(name)` | 检查任务是否存在 |
| `pause(name)` | 暂停任务 |
| `resume(name)` | 恢复任务 |
| `remove(name)` | 删除任务 |
| `triggerNow(name)` | 额外触发一次，不改变原定调度 |
| `getState(name)` | 获取状态 |

#### 批量管理

| 方法 | 返回值 | 说明 |
|-----|-------|------|
| `pauseAll(group?)` | `Int` | 暂停组内所有任务，返回数量 |
| `resumeAll(group?)` | `Int` | 恢复组内所有任务 |
| `removeAll(group?)` | `Int` | 删除组内所有任务 |
| `clearAll()` | `Int` | 清空所有任务 |

#### 查询

| 方法 | 返回值 | 说明 |
|-----|-------|------|
| `getAllGroupNames()` | `List<String>` | 获取所有组名 |
| `getJobNames(group?)` | `List<String>` | 获取组内任务名 |
| `getAllJobs()` | `List<JobKey>` | 获取所有任务 |
| `getJobsInGroup(group)` | `List<JobKey>` | 获取组内任务 |
| `getNextFireTime(name, group?)` | `Long?` | 下次执行时间 |
| `getPreviousFireTime(name, group?)` | `Long?` | 上次执行时间 |
| `getDescription(name, group?)` | `String?` | 任务描述 |
| `getJobInfo(name, group?)` | `JobInfo?` | 任务详细信息 |
| `getAllJobInfo()` | `List<JobInfo>` | 所有任务信息 |

#### 时间单位扩展

| 扩展 | 示例 | 结果 |
|-----|------|------|
| `Int.seconds` | `5.seconds` | `5000L` |
| `Int.minutes` | `2.minutes` | `120000L` |
| `Int.hours` | `1.hours` | `3600000L` |
| `Int.days` | `1.days` | `86400000L` |
| `Int.millis` | `500.millis` | `500L` |
| `Long.toReadableTime()` | `3661000L.toReadableTime()` | `"1h 1m 1s"` |
| `Long.fromNow()` | `5.seconds.fromNow()` | 当前时间+5秒 |
| `Long.ago()` | `5.minutes.ago()` | 当前时间-5分钟 |

### CronExpressions 常量

| 常量 | 表达式 | 说明 |
|-----|--------|------|
| `EVERY_SECOND` | `0/1 * * * * ?` | 每秒 |
| `EVERY_5_SECONDS` | `0/5 * * * * ?` | 每5秒 |
| `EVERY_MINUTE` | `0 * * * * ?` | 每分钟 |
| `EVERY_5_MINUTES` | `0 */5 * * * ?` | 每5分钟 |
| `EVERY_HOUR` | `0 0 * * * ?` | 每小时 |
| `DAILY_MIDNIGHT` | `0 0 0 * * ?` | 每天凌晨 |
| `DAILY_NOON` | `0 0 12 * * ?` | 每天中午 |
| `WEEKLY_MONDAY` | `0 0 0 ? * MON` | 每周一 |
| `WEEKDAYS_9AM` | `0 0 9 ? * MON-FRI` | 工作日9点 |
| `WEEKENDS_10AM` | `0 0 10 ? * SAT,SUN` | 周末10点 |
| `MONTHLY_FIRST_DAY` | `0 0 0 1 * ?` | 每月1号 |
| `MONTHLY_LAST_DAY` | `0 0 0 L * ?` | 每月最后一天 |
| `YEARLY_JANUARY_FIRST` | `0 0 0 1 1 ?` | 每年1月1日 |

### CronBuilder

```kotlin
import kim.der.timetask.extensions.CronBuilder
import kim.der.timetask.extensions.describeCron
import kim.der.timetask.extensions.getNextFireTime
import kim.der.timetask.extensions.isValidCron

// 每 N 秒/分钟/小时（Quartz Cron 字段步进）
val every30Seconds = CronBuilder.everySeconds(30) // "0/30 * * * * ?"
val every15Minutes = CronBuilder.everyMinutes(15) // "0 */15 * * * ?"
val every2Hours = CronBuilder.everyHours(2)       // "0 0 */2 * * ?"

// 每天指定时间
val dailyAt830 = CronBuilder.dailyAt(8, 30) // "0 30 8 * * ?"

// 每周指定时间
val mondayAt9 = CronBuilder.weeklyAt(CronBuilder.DayOfWeek.MONDAY, 9) // "0 0 9 ? * MON"

// 每月指定日期
val monthlyAt1030 = CronBuilder.monthlyAt(15, 10, 30) // "0 30 10 15 * ?"
val lastDayAt23 = CronBuilder.monthlyLastDay(23)      // "0 0 23 L * ?"

// 工作日/周末
val weekdaysAt9 = CronBuilder.weekdaysAt(9)  // "0 0 9 ? * MON-FRI"
val weekendsAt10 = CronBuilder.weekendsAt(10) // "0 0 10 ? * SAT,SUN"

// 辅助函数
val valid = isValidCron(dailyAt830)            // true
val nextFireTime = getNextFireTime(dailyAt830) // 下次触发时间
val description = describeCron(dailyAt830)     // 表达式描述
```

`everySeconds()`、`everyMinutes()` 和 `everyHours()` 按 Quartz Cron 字段边界步进，
并不保证从任务注册或上次执行时刻起经过固定时长后触发。例如 `everyHours(5)`
会在每天的 0、5、10、15、20 点触发。需要严格固定间隔时，请使用 `TimeTaskManage.every()`。

### DSL（`kim.der.timetask.dsl`）

```kotlin
// 延迟执行一次
taskManager.task("one-shot") {
    group("default")
    description("延迟 5 秒执行")
    delay(5_000)
    action { println("one-shot") }
}

// 延迟开始的有限间隔任务
taskManager.task("interval-report") {
    group("reports")
    startAfter(5_000)
    interval(60_000)
    repeatCount(2) // 首次执行后再重复 2 次，总共执行 3 次
    action { println("interval report") }
}

// Cron 任务
taskManager.task("daily-report") {
    cron("0 0 12 * * ?")
    action { println("daily report") }
}

// 便捷函数
taskManager.delayTask("delay", 5_000) { println("delayed") }
taskManager.intervalTask("interval", 10_000) { println("interval") }
taskManager.cronTask("cron", "0 * * * * ?") { println("cron") }
```

## 版本变更

本次 API 契约修正的行为变化与升级检查项见 [迁移指南](MIGRATION.md)。

### v1.1.0 (当前)

#### 新增功能
- `TimeTaskManage` 支持自定义线程池大小
- `TimeTaskManage` 支持自定义 Quartz Properties 配置
- 新增 `triggerNow()` 立即触发任务
- 新增 `reschedule()` 重调度任务（修改间隔或 Cron）
- 新增 `getJobState()` 获取任务状态
- 新增 `JobState` 枚举
- 新增 `isRunning`、`isStandby`、`jobCount` 属性
- 新增 `standby()`、`resume()` 调度器控制
- 扩展函数新增 `runAt()`、`getState()`、`getPreviousFireTime()`
- 扩展函数新增 `JobInfo` 数据类和相关查询方法
- 时间单位扩展新增 `millis`、`toReadableTime()`、`fromNow()`、`ago()`
- CronExpressions 新增更多常量
- CronBuilder 新增 `DayOfWeek` 枚举、`monthlyLastDay()`、`yearlyAt()`、`weekdaysAt()`、`weekendsAt()`
- 新增 `isValidCron()`、`getNextFireTime()`、`describeCron()` 辅助函数
- DSL TaskBuilder 支持链式调用，新增 `startAfter()`、`repeatCount()`

#### 优化
- 所有方法添加完整 KDoc 文档
- `pause()`、`resume()`、`remove()` 等任务管理方法返回操作结果
- 移除旧命名 `unPause()`，统一使用标准命名 `resume()`
- 移除不可靠的 `getExecutionCount()`，执行次数请在任务 action 或监听器中自行统计
- 优化异常处理，添加参数验证
- RunnableRun 添加详细异常信息

#### 破坏性变更
- `pause(JobKey)` 返回类型从 `Unit` 改为 `Boolean`
- `unPause()` 已移除，请迁移到 `resume()`
- `getExecutionCount()` 已移除，避免暴露 Quartz 默认不维护的伪计数字段
- `repeatCount()` 和 `addTimedTask(..., repeatCount, ...)` 仅对固定间隔任务生效；`repeatCount(2)` 表示首次执行后再重复 2 次

## 构建

### 要求

- JDK 11+
- Gradle 8.14.2+

### 构建命令

```powershell
# 构建项目
gradle build

# 运行测试
gradle test

# 打包
gradle jar
```

## 许可证

RELAY-CN LICENSE

## 相关项目

- [IronCore](https://github.com/RELAY-CN/IronCore) - 主项目
