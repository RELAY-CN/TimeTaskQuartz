# 迁移指南

本文说明从早期版本升级到当前 API 契约修正版时需要关注的行为变化。

## Quartz 现在是 API 传递依赖

公开 API 会直接使用 Quartz 类型，例如 `org.quartz.JobKey`。Quartz 依赖现已从
Gradle `implementation` 调整为 `api`，因此正常引入 TimeTaskQuartz 后，Quartz 会自动出现在
消费者的编译类路径和运行时类路径中。

如果项目以前为了解决编译错误而额外声明了同版本 Quartz，可以删除这项重复依赖；如果主动排除了
TimeTaskQuartz 的传递依赖，则仍需显式添加兼容版本的 Quartz。

## `reschedule()` 不再跨类型转换 Trigger

两个重调度重载现在只接受与参数类型对应的任务：

- `reschedule(name, group, newIntervalMillis)` 仅处理 `SimpleTrigger` 间隔任务。
- `reschedule(name, group, newCron)` 仅处理 `CronTrigger` 任务。
- 任务不存在或 Trigger 类型不匹配时返回 `false`，原任务保持不变。

调用方应检查返回值，不要再依赖旧版本把 Cron 与固定间隔 Trigger 静默互相转换的行为：

```kotlin
val updated = taskManager.reschedule("cleanup", "default", 60_000L)
if (!updated) {
    // 任务不存在、不是间隔任务，或 Quartz 重调度失败
}
```

成功重调度时还会保留以下调度属性：

- 有限 `SimpleTrigger` 会保留尚未执行的剩余重复次数，不会变成无限任务。
- `CronTrigger` 会继续使用原 Trigger 的时区，不会回落到系统默认时区。

其中 Quartz 的 `repeatCount` 不包含首次执行。例如 `repeatCount = 2` 表示总共执行 3 次；
任务已经执行一次后再重调度，只会保留剩余的 2 次执行机会。

## 倒计时任务的 `triggerNow()` 是额外执行

倒计时任务现在使用 non-durable Job。对尚未到期的倒计时任务调用 `triggerNow()` 时：

1. Quartz 立即额外触发一次任务。
2. 原定 Trigger 仍然保留，并会在计划时间再次执行。
3. 原定 Trigger 完成后，Quartz 自动清理 non-durable Job。

因此，`triggerNow()` 不再隐式消费或取消未来的原定执行。若业务只允许执行一次，调用方需要根据自身的
并发与状态约束显式协调 `remove()`，不要把 `triggerNow()` 当作取消操作。

## 默认运行模式不支持跨进程恢复

`TimeTaskManage()` 和 `TimeTaskManage(threadPoolSize)` 默认使用 Quartz `RAMJobStore`，任务定义会随
进程退出而消失。同时，TimeTaskQuartz 把任务动作作为进程内 Kotlin Lambda 保存在 `JobDataMap` 中，
没有提供 Lambda 的序列化或重建协议。

即使通过 `TimeTaskManage(properties)` 配置数据库 JobStore，本库也不承诺任务 Lambda 能在进程重启后
恢复。需要跨进程持久化调度时，应使用可重建的 Quartz `Job` 类型和持久化参数，而不是本库的 Lambda API。

## `CronBuilder.every*` 是字段步进

`CronBuilder.everySeconds()`、`everyMinutes()` 和 `everyHours()` 生成 Quartz Cron 字段步进表达式，
触发点锚定在对应的自然时间字段，而不是锚定在任务注册时间或上一次执行时间。

例如：

- `CronBuilder.everyMinutes(15)` 在每小时的 0、15、30、45 分触发。
- `CronBuilder.everyHours(5)` 在每天的 0、5、10、15、20 点触发；20 点到次日 0 点只有 4 小时。

如果业务需要严格的固定间隔，请改用 SimpleTrigger 封装 `every()`：

```kotlin
import kim.der.timetask.extensions.every
import kim.der.timetask.extensions.hours

taskManager.every(
    name = "sync",
    intervalMillis = 5.hours,
) {
    println("每经过 5 小时执行")
}
```
