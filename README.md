# TimeTaskQuartz

基于 **Quartz** 的 Kotlin 定时任务管理库，为 `IronCore` 提供简单易用的定时任务支持。

## 特性

- 🎯 **简单易用** - 提供简洁的 API，无需深入了解 Quartz 的复杂配置
- ⏰ **多种调度方式** - 支持倒计时、固定间隔、Cron 表达式三种调度方式
- 🔄 **任务管理** - 支持任务的添加、暂停、恢复、删除和查询
- 🛡️ **稳定可靠** - 基于成熟的 Quartz 2.5.1，支持任务恢复和持久化
- 📦 **轻量级** - 仅依赖 Quartz，无额外持久化依赖（运行时独立运行）

## 快速开始

### 添加依赖

```kotlin
// build.gradle.kts
// 注 当前版本号为哈希(Short)
dependencies {
    implementation("kim.der:TimeTaskQuartz:版本号")
}
```

### 基本使用

```kotlin
import kim.der.timetask.task.TimeTaskManage

// 创建任务管理器
val taskManager = TimeTaskManage()
```

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
    taskManager.unPause("myTimedTask", "default")
    
    // 删除任务
    taskManager.remove("myTimedTask", "default")
}

// 关闭任务管理器
taskManager.shutdownNow()
```

#### Kotlin 扩展 & DSL

```kotlin
import kim.der.timetask.extensions.*
import kim.der.timetask.dsl.task

val taskManager = TimeTaskManage()

// 延迟 5 秒执行
taskManager.delay("warmup", 5.seconds) {
    println("ready.")
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
```

## API 文档

### TimeTaskManage

定时任务管理器，基于 Quartz Scheduler 封装。

#### 主要方法

##### 添加任务

- `addCountdown(name, group, description, startTime, runnable)` - 添加一次性倒计时任务
  - 任务执行一次后自动删除
  - `startTime`: 任务开始执行的时间戳（毫秒）

- `addTimedTask(name, group, description, startTime, intervalTime, runnable)` - 添加固定间隔的定时任务
  - 从 `startTime` 开始，每隔 `intervalTime` 毫秒执行一次
  - 持续执行直到手动删除

- `addTimedTask(name, group, description, cron, runnable)` - 添加 Cron 表达式的定时任务
  - 使用标准的 Cron 表达式定义执行时间
  - 例如：`"0 0 12 * * ?"` 表示每天中午12点执行

##### 任务管理

- `contains(name, group)` / `contains(jobKey)` - 检查任务是否存在
- `pause(name, group)` / `pause(jobKey)` - 暂停任务
- `unPause(name, group)` / `unPause(jobKey)` - 恢复暂停的任务
- `remove(name, group)` / `remove(jobKey)` - 删除任务
- `shutdownNow()` - 关闭任务调度器

### 扩展函数（`kim.der.timetask.extensions`）

- `delay(name, delayMillis, description, action)` - 使用默认组延迟执行
- `runNow(name, description, action)` - 立即执行一次
- `every(name, intervalMillis, delayMillis, description, action)` - 固定间隔任务
- `cron(name, cron, description, action)` - Cron 任务
- `pauseAll(group)` / `resumeAll(group)` / `removeAll(group)` / `clearAll()` - 批量管理
- `getAllGroupNames()` / `getJobNames(group)` / `getAllJobs()` - 查询任务
- `getNextFireTime(name, group)` - 查询下次触发时间
- 时间单位扩展 `Int.seconds / minutes / hours / days`、`Long.seconds / ...`
- `CronExpressions` 常量 & `CronBuilder` 帮助快速构造表达式

### DSL（`kim.der.timetask.dsl`）

- `task(name) { ... }` - 通过 DSL 描述任务信息
  - `group("自定义组")`
  - `description("描述")`
  - `delay(5000)` / `startAt(timestamp)`
  - `interval(60000)` / `cron("0 0 12 * * ?")`
  - `action { ... }`
- 便捷函数：`delayTask` / `intervalTask` / `cronTask`

## 构建

### 要求

- JDK 8+
- Gradle 8.14.2+

### 构建命令

```bash
# 构建项目
./gradlew build

# 运行测试
./gradlew test

# 打包
./gradlew jar
```

## 许可证

RELAY-CN LICENSE

## 相关项目

- [IronCore](https://github.com/RELAY-CN/IronCore) - 主项目
