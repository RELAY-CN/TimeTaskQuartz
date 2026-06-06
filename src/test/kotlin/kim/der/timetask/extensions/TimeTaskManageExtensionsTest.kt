/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.extensions

import kim.der.timetask.task.JobState
import kim.der.timetask.task.TimeTaskManage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream

/**
 * [TimeTaskManage] 默认组扩展和查询扩展的业务契约测试。
 *
 * 测试使用真实 Quartz 调度器验证状态变化，避免 mock 任务管理器本身；
 * 等待异步调度时统一使用 latch 或轮询条件，减少固定 sleep 带来的不稳定性。
 */
@DisplayName("TimeTaskManage 扩展 API")
class TimeTaskManageExtensionsTest {
    private lateinit var taskManager: TimeTaskManage

    @BeforeEach
    fun setUp() {
        taskManager = TimeTaskManage()
    }

    @AfterEach
    fun tearDown() {
        if (this::taskManager.isInitialized && taskManager.isRunning) {
            taskManager.clearAll()
            taskManager.shutdownNow()
        }
    }

    @Nested
    @DisplayName("正常场景")
    inner class NormalScenarios {

        @Test
        @DisplayName("delay 使用默认组创建一次性任务并在执行后自动移除")
        fun `delay creates one shot task in default group and removes it after execution`() {
            // Given：业务通过默认组创建一次性通知任务。
            val executed = CountDownLatch(1)

            // When：任务延迟执行。
            taskManager.delay("delay-notification", 80, description = "用户注册欢迎通知") {
                executed.countDown()
            }

            // Then：任务先可查询，执行完成后自动清理。
            assertAll(
                "delay 一次性任务生命周期",
                {
                    assertThat(taskManager.contains("delay-notification"))
                        .`as`("delay 应把任务注册到默认组")
                        .isTrue()
                },
                {
                    assertThat(taskManager.getDescription("delay-notification"))
                        .`as`("任务描述应保留用户输入")
                        .isEqualTo("用户注册欢迎通知")
                },
                {
                    assertThat(executed.await(3, TimeUnit.SECONDS))
                        .`as`("一次性任务应在超时时间内执行")
                        .isTrue()
                },
                {
                    assertThat(waitUntil { !taskManager.contains("delay-notification") })
                        .`as`("一次性任务执行后应自动从默认组移除")
                        .isTrue()
                },
            )
        }

        @Test
        @DisplayName("runNow 几乎立即执行任务")
        fun `run now executes task immediately`() {
            // Given：业务需要立刻刷新缓存。
            val executed = CountDownLatch(1)

            // When：通过 runNow 创建立即执行任务。
            taskManager.runNow("cache-refresh", description = "立即刷新缓存") {
                executed.countDown()
            }

            // Then：任务应快速执行，并保留诊断描述直到任务完成。
            assertAll(
                "runNow 立即任务",
                {
                    assertThat(executed.await(2, TimeUnit.SECONDS))
                        .`as`("runNow 应在短时间内触发业务动作")
                        .isTrue()
                },
                {
                    assertThat(waitUntil { !taskManager.contains("cache-refresh") })
                        .`as`("runNow 底层是一次性任务，执行完成后应自动清理")
                        .isTrue()
                },
            )
        }

        @Test
        @DisplayName("runAt 在指定未来时间执行任务")
        fun `run at executes task at configured timestamp`() {
            // Given：业务需要在未来某个时间点执行补偿任务。
            val executed = CountDownLatch(1)
            val before = System.currentTimeMillis()

            // When：指定未来 250ms 执行。
            taskManager.runAt("future-compensation", before + 250, description = "补偿任务") {
                executed.countDown()
            }

            // Then：任务应在合理窗口内执行，不应立刻触发。
            assertThat(executed.await(3, TimeUnit.SECONDS))
                .`as`("runAt 应在指定时间附近触发任务")
                .isTrue()
            assertThat(System.currentTimeMillis() - before)
                .`as`("runAt 不应把未来任务立即触发，应尊重配置的时间点")
                .isGreaterThanOrEqualTo(150)
        }

        @Test
        @DisplayName("every 支持延迟启动并按固定间隔重复执行")
        fun `every starts after delay and repeats`() {
            // Given：业务配置带冷启动延迟的心跳任务。
            val executions = CountDownLatch(2)
            val counter = AtomicInteger(0)
            val before = System.currentTimeMillis()

            // When：延迟后按固定间隔执行。
            taskManager.every("heartbeat", intervalMillis = 80, delayMillis = 200, description = "服务心跳") {
                counter.incrementAndGet()
                executions.countDown()
            }

            // Then：任务先延迟启动，再重复触发，且不会自动删除。
            assertAll(
                "every 固定间隔任务",
                {
                    assertThat(executions.await(3, TimeUnit.SECONDS))
                        .`as`("固定间隔任务应重复触发")
                        .isTrue()
                },
                {
                    assertThat(System.currentTimeMillis() - before)
                        .`as`("every 应尊重首次执行延迟")
                        .isGreaterThanOrEqualTo(150)
                },
                {
                    assertThat(counter.get())
                        .`as`("固定间隔任务应产生多次业务状态变化")
                        .isGreaterThanOrEqualTo(2)
                },
                {
                    assertThat(taskManager.getState("heartbeat"))
                        .`as`("重复任务执行后仍应保持正常调度状态")
                        .isEqualTo(JobState.NORMAL)
                },
                {
                    assertThat(taskManager.getDescription("heartbeat"))
                        .`as`("重复任务描述应可查询")
                        .isEqualTo("服务心跳")
                },
            )
        }

        @Test
        @DisplayName("cron 使用默认组创建 Cron 任务")
        fun `cron creates default group cron task`() {
            // Given：业务配置每秒执行的轻量 Cron 任务。
            val executed = CountDownLatch(1)

            // When：通过默认组 Cron 扩展注册任务。
            taskManager.cron("daily-report", CronExpressions.EVERY_SECOND, description = "日报生成") {
                executed.countDown()
            }

            // Then：任务可查询、状态正常、描述保留，并能被 Quartz 触发。
            assertAll(
                "cron 默认组任务",
                {
                    assertThat(taskManager.contains("daily-report"))
                        .`as`("Cron 扩展应使用默认组注册任务")
                        .isTrue()
                },
                {
                    assertThat(taskManager.getState("daily-report"))
                        .`as`("Cron 任务注册后应处于正常状态")
                        .isEqualTo(JobState.NORMAL)
                },
                {
                    assertThat(taskManager.getDescription("daily-report"))
                        .`as`("Cron 任务描述应保留业务文案")
                        .isEqualTo("日报生成")
                },
                {
                    assertThat(executed.await(3, TimeUnit.SECONDS))
                        .`as`("Cron 任务应按表达式触发")
                        .isTrue()
                },
            )
        }
    }

    @Nested
    @DisplayName("状态转换场景")
    inner class StateTransitionScenarios {

        @Test
        @DisplayName("默认组任务可暂停、恢复、立即触发并删除")
        fun `default group task can pause resume trigger and remove`() {
            // Given：默认组中存在一个未来才会自然触发的任务。
            val triggered = CountDownLatch(1)
            taskManager.every("ops-window", intervalMillis = 60_000, delayMillis = 60_000) {
                triggered.countDown()
            }

            // When & Then：通过默认组扩展完成完整生命周期状态转换。
            assertAll(
                "默认组任务状态转换",
                {
                    assertThat(taskManager.contains("ops-window"))
                        .`as`("任务创建后应存在于默认组")
                        .isTrue()
                },
                {
                    assertThat(taskManager.pause("ops-window"))
                        .`as`("存在的默认组任务应可暂停")
                        .isTrue()
                },
                {
                    assertThat(taskManager.getState("ops-window"))
                        .`as`("暂停后状态应变为 PAUSED")
                        .isEqualTo(JobState.PAUSED)
                },
                {
                    assertThat(taskManager.resume("ops-window"))
                        .`as`("已暂停任务应可恢复")
                        .isTrue()
                },
                {
                    assertThat(taskManager.getState("ops-window"))
                        .`as`("恢复后状态应回到 NORMAL")
                        .isEqualTo(JobState.NORMAL)
                },
                {
                    assertThat(taskManager.triggerNow("ops-window"))
                        .`as`("存在的任务应可立即触发一次")
                        .isTrue()
                },
                {
                    assertThat(triggered.await(3, TimeUnit.SECONDS))
                        .`as`("triggerNow 应触发业务动作")
                        .isTrue()
                },
                {
                    assertThat(taskManager.remove("ops-window"))
                        .`as`("存在的任务应可删除")
                        .isTrue()
                },
                {
                    assertThat(taskManager.contains("ops-window"))
                        .`as`("删除后默认组中不应再存在该任务")
                        .isFalse()
                },
            )
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("kim.der.timetask.extensions.TimeTaskManageExtensionsTest#missingTaskOperations")
        @DisplayName("不存在的默认组任务操作安全返回失败状态")
        fun `missing default group task operations return safe state`(
            caseName: String,
            operation: (TimeTaskManage) -> Boolean,
        ) {
            // Given：默认组中不存在目标任务。
            // When：执行状态变更操作。
            val result = operation(taskManager)

            // Then：操作应安全返回 false，不抛出异常。
            assertThat(result)
                .`as`("不存在任务的操作应返回 false：%s", caseName)
                .isFalse()
        }

        @Test
        @DisplayName("不存在的任务查询返回空状态")
        fun `missing task queries return empty state`() {
            // Given：默认组中不存在目标任务。
            // When & Then：查询类扩展返回空值，调用方可以直接判断缺失状态。
            assertAll(
                "不存在任务查询",
                {
                    assertThat(taskManager.contains("missing"))
                        .`as`("不存在任务 contains 应为 false")
                        .isFalse()
                },
                {
                    assertThat(taskManager.getState("missing"))
                        .`as`("不存在任务没有 Quartz 状态")
                        .isNull()
                },
                {
                    assertThat(taskManager.getJobInfo("missing"))
                        .`as`("不存在任务没有详情信息")
                        .isNull()
                },
                {
                    assertThat(taskManager.getNextFireTime("missing"))
                        .`as`("不存在任务没有下次触发时间")
                        .isNull()
                },
                {
                    assertThat(taskManager.getPreviousFireTime("missing"))
                        .`as`("不存在任务没有上次触发时间")
                        .isNull()
                },
                {
                    assertThat(taskManager.getDescription("missing"))
                        .`as`("不存在任务没有描述")
                        .isNull()
                },
            )
        }
    }

    @Nested
    @DisplayName("组合场景")
    inner class CombinedScenarios {

        @Test
        @DisplayName("批量暂停和恢复只影响指定组")
        fun `pause all and resume all affect only selected group`() {
            // Given：默认组和自定义组同时存在任务。
            taskManager.every("default-a", 1_000) {}
            taskManager.every("default-b", 1_000) {}
            taskManager.addTimedTask("other-a", "other", "其他组任务", System.currentTimeMillis() + 10_000, 1_000) {}

            // When：批量暂停默认组。
            val pausedCount = taskManager.pauseAll()

            // Then：默认组任务暂停，自定义组保持正常。
            assertAll(
                "按组批量暂停",
                {
                    assertThat(pausedCount)
                        .`as`("pauseAll 默认只应统计默认组任务")
                        .isEqualTo(2)
                },
                {
                    assertThat(taskManager.getState("default-a"))
                        .`as`("默认组任务 default-a 应暂停")
                        .isEqualTo(JobState.PAUSED)
                },
                {
                    assertThat(taskManager.getState("default-b"))
                        .`as`("默认组任务 default-b 应暂停")
                        .isEqualTo(JobState.PAUSED)
                },
                {
                    assertThat(taskManager.getJobState("other-a", "other"))
                        .`as`("其他组任务不应受默认组批量暂停影响")
                        .isEqualTo(JobState.NORMAL)
                },
            )

            // When：批量恢复默认组。
            val resumedCount = taskManager.resumeAll()

            // Then：默认组任务恢复，自定义组仍保持正常。
            assertAll(
                "按组批量恢复",
                {
                    assertThat(resumedCount)
                        .`as`("resumeAll 默认只应统计默认组任务")
                        .isEqualTo(2)
                },
                {
                    assertThat(taskManager.getState("default-a"))
                        .`as`("默认组任务 default-a 应恢复")
                        .isEqualTo(JobState.NORMAL)
                },
                {
                    assertThat(taskManager.getState("default-b"))
                        .`as`("默认组任务 default-b 应恢复")
                        .isEqualTo(JobState.NORMAL)
                },
                {
                    assertThat(taskManager.getJobState("other-a", "other"))
                        .`as`("其他组任务状态不应被改变")
                        .isEqualTo(JobState.NORMAL)
                },
            )
        }

        @Test
        @DisplayName("removeAll 只删除指定组，clearAll 再清空剩余任务")
        fun `remove all deletes selected group and clear all deletes remaining tasks`() {
            // Given：默认组和其他组都有任务。
            taskManager.every("default-cleanup", 1_000) {}
            taskManager.addTimedTask("other-cleanup", "other", "其他组清理", System.currentTimeMillis() + 10_000, 1_000) {}

            // When：先删除默认组。
            val removedDefault = taskManager.removeAll()

            // Then：只删除默认组，其他组仍存在。
            assertAll(
                "removeAll 默认组删除",
                {
                    assertThat(removedDefault)
                        .`as`("removeAll 默认只删除默认组任务")
                        .isEqualTo(1)
                },
                {
                    assertThat(taskManager.contains("default-cleanup"))
                        .`as`("默认组任务应被删除")
                        .isFalse()
                },
                {
                    assertThat(taskManager.contains("other-cleanup", "other"))
                        .`as`("其他组任务应保留")
                        .isTrue()
                },
            )

            // When：清空所有组任务。
            val cleared = taskManager.clearAll()

            // Then：所有任务清空，重复清理保持幂等。
            assertAll(
                "clearAll 全量清理和幂等性",
                {
                    assertThat(cleared)
                        .`as`("clearAll 应删除剩余其他组任务")
                        .isEqualTo(1)
                },
                {
                    assertThat(taskManager.jobCount)
                        .`as`("clearAll 后任务总数应为 0")
                        .isZero()
                },
                {
                    assertThat(taskManager.clearAll())
                        .`as`("再次 clearAll 应保持幂等并返回 0")
                        .isZero()
                },
            )
        }
    }

    @Nested
    @DisplayName("查询场景")
    inner class QueryScenarios {

        @Test
        @DisplayName("查询接口返回分组、名称和完整任务详情")
        fun `query APIs return groups names and complete job info`() {
            // Given：任务名称、组名和描述包含真实环境常见的国际化和特殊字符。
            val name = "report_日报:2026"
            val group = "tenant-中国/alpha"
            val description = "生成日报\n包含特殊字符: '\"\\"
            taskManager.addTimedTask(
                name = name,
                group = group,
                description = description,
                startTime = System.currentTimeMillis() + 60_000,
                intervalTime = 1_000,
            ) {}

            // When：通过查询扩展读取任务视图。
            val groups = taskManager.getAllGroupNames()
            val names = taskManager.getJobNames(group)
            val jobs = taskManager.getJobsInGroup(group)
            val info = taskManager.getJobInfo(name, group)
            val groupInfos = taskManager.getJobInfoInGroup(group)
            val allInfos = taskManager.getAllJobInfo()

            // Then：查询结果应完整保留任务身份、描述、状态和下一次触发时间。
            assertAll(
                "任务查询视图",
                {
                    assertThat(groups)
                        .`as`("所有组名应包含国际化租户组")
                        .contains(group)
                },
                {
                    assertThat(names)
                        .`as`("指定组任务名应完整保留特殊字符")
                        .containsExactly(name)
                },
                {
                    assertThat(jobs.single().name)
                        .`as`("JobKey 应保留任务名")
                        .isEqualTo(name)
                },
                {
                    assertThat(info)
                        .`as`("存在的任务应返回详情")
                        .isNotNull()
                },
                {
                    assertThat(info?.name)
                        .`as`("详情中的任务名应一致")
                        .isEqualTo(name)
                },
                {
                    assertThat(info?.group)
                        .`as`("详情中的组名应一致")
                        .isEqualTo(group)
                },
                {
                    assertThat(info?.description)
                        .`as`("详情中的描述应保留换行和特殊字符")
                        .isEqualTo(description)
                },
                {
                    assertThat(info?.state)
                        .`as`("新建间隔任务应处于 NORMAL 状态")
                        .isEqualTo(JobState.NORMAL)
                },
                {
                    assertThat(info?.nextFireTime)
                        .`as`("未来调度任务应有下一次触发时间")
                        .isNotNull()
                },
                {
                    assertThat(info?.previousFireTime)
                        .`as`("尚未触发的未来任务不应有上次触发时间")
                        .isNull()
                },
                {
                    assertThat(groupInfos)
                        .`as`("指定组详情列表应只包含该组任务")
                        .hasSize(1)
                },
                {
                    assertThat(allInfos)
                        .`as`("全量详情应包含目标任务")
                        .anySatisfy {
                            assertThat(it.name).isEqualTo(name)
                            assertThat(it.group).isEqualTo(group)
                        }
                },
            )
        }

        @Test
        @DisplayName("任务触发后可查询到上次执行时间")
        fun `previous fire time appears after task execution`() {
            // Given：一个很快执行的固定间隔任务。
            val executed = CountDownLatch(1)
            taskManager.every("previous-fire", 120) {
                executed.countDown()
            }

            // When：等待任务至少触发一次。
            assertThat(executed.await(3, TimeUnit.SECONDS))
                .`as`("任务应至少执行一次")
                .isTrue()

            // Then：上次触发时间最终应可查询到。
            assertThat(waitUntil { taskManager.getPreviousFireTime("previous-fire") != null })
                .`as`("任务执行后应能查询到 previousFireTime")
                .isTrue()
        }
    }

    private companion object {
        @JvmStatic
        fun missingTaskOperations(): Stream<Arguments> =
            Stream.of(
                Arguments.of("pause", { manager: TimeTaskManage -> manager.pause("missing") }),
                Arguments.of("resume", { manager: TimeTaskManage -> manager.resume("missing") }),
                Arguments.of("remove", { manager: TimeTaskManage -> manager.remove("missing") }),
                Arguments.of("triggerNow", { manager: TimeTaskManage -> manager.triggerNow("missing") }),
            )
    }

    private fun waitUntil(
        timeoutMillis: Long = 2_000,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(25)
        }
        return condition()
    }
}
