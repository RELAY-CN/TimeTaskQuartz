/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.dsl

import kim.der.timetask.extensions.clearAll
import kim.der.timetask.extensions.getDescription
import kim.der.timetask.extensions.seconds
import kim.der.timetask.task.JobState
import kim.der.timetask.task.TimeTaskManage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream

/**
 * [TaskBuilder] DSL 的业务契约测试。
 *
 * 测试通过 [TimeTaskManage.task] 和便捷函数这些公开 API 进入，
 * 以真实 Quartz 调度结果验证 DSL 配置，而不是绑定构建器内部字段。
 */
@DisplayName("TaskBuilder DSL")
class TaskBuilderTest {
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
        @DisplayName("默认 DSL 创建默认组倒计时任务")
        fun `task dsl creates countdown task in default group`() {
            // Given：业务用最小 DSL 创建一次性延迟任务。
            val executed = CountDownLatch(1)

            // When：注册默认组任务。
            taskManager.task("welcome-message") {
                delay(80)
                action { executed.countDown() }
            }

            // Then：任务先进入默认组，执行后自动移除。
            assertAll(
                "默认组倒计时任务",
                {
                    assertThat(taskManager.contains("welcome-message", "default"))
                        .`as`("DSL 未指定 group 时应使用默认组")
                        .isTrue()
                },
                {
                    assertThat(taskManager.getDescription("welcome-message", "default"))
                        .`as`("默认描述应体现倒计时任务类型")
                        .isEqualTo("倒计时任务: welcome-message")
                },
                {
                    assertThat(executed.await(3, TimeUnit.SECONDS))
                        .`as`("倒计时任务应在超时时间内执行")
                        .isTrue()
                },
                {
                    assertThat(waitUntil { !taskManager.contains("welcome-message", "default") })
                        .`as`("倒计时任务执行后应自动清理")
                        .isTrue()
                },
            )
        }

        @Test
        @DisplayName("链式 DSL 保留自定义组名和描述")
        fun `chained dsl keeps custom group and description`() {
            // Given：业务用链式 DSL 配置租户组任务。
            val executed = CountDownLatch(1)

            // When：注册自定义组间隔任务。
            taskManager.task("tenant-heartbeat") {
                group("tenant-中国")
                    .description("租户心跳检查")
                    .interval(80)
                    .action { executed.countDown() }
            }

            // Then：组名、描述、状态和执行结果都应可观察。
            assertAll(
                "链式 DSL 间隔任务",
                {
                    assertThat(taskManager.contains("tenant-heartbeat", "tenant-中国"))
                        .`as`("链式 group 应写入自定义组")
                        .isTrue()
                },
                {
                    assertThat(taskManager.contains("tenant-heartbeat", "default"))
                        .`as`("自定义组任务不应落入默认组")
                        .isFalse()
                },
                {
                    assertThat(taskManager.getDescription("tenant-heartbeat", "tenant-中国"))
                        .`as`("链式 description 应保留业务文本")
                        .isEqualTo("租户心跳检查")
                },
                {
                    assertThat(taskManager.getJobState("tenant-heartbeat", "tenant-中国"))
                        .`as`("间隔任务创建后应处于 NORMAL 状态")
                        .isEqualTo(JobState.NORMAL)
                },
                {
                    assertThat(executed.await(3, TimeUnit.SECONDS))
                        .`as`("间隔任务应能被 Quartz 触发")
                        .isTrue()
                },
            )
        }

        @Test
        @DisplayName("未配置调度方式时退化为立即执行一次")
        fun `task without schedule executes once immediately`() {
            // Given：业务只配置 action，期望作为一次性立即任务运行。
            val executed = CountDownLatch(1)

            // When：构建任务。
            taskManager.task("immediate-dsl") {
                action { executed.countDown() }
            }

            // Then：任务应快速执行并自动清理。
            assertAll(
                "无调度配置的 DSL 任务",
                {
                    assertThat(executed.await(2, TimeUnit.SECONDS))
                        .`as`("未配置调度方式时应几乎立即执行")
                        .isTrue()
                },
                {
                    assertThat(waitUntil { !taskManager.contains("immediate-dsl", "default") })
                        .`as`("立即任务执行后应自动清理")
                        .isTrue()
                },
            )
        }
    }

    @Nested
    @DisplayName("边界场景")
    inner class BoundaryScenarios {

        @Test
        @DisplayName("未配置 action 时 DSL 保持 no-op")
        fun `task without action does not register job`() {
            // Given：配置中只有调度信息，没有业务动作。
            // When：构建任务。
            taskManager.task("declared-only") {
                delay(50)
                description("仅声明配置")
            }

            // Then：没有 action 不应注册 Quartz 任务，避免空任务污染调度器。
            assertThat(taskManager.contains("declared-only", "default"))
                .`as`("未配置 action 时 DSL 应保持 no-op，不创建任务")
                .isFalse()
        }

        @Test
        @DisplayName("repeatCount 表示首次触发后的重复次数")
        fun `repeat count stops interval task after configured repeats`() {
            // Given：业务配置首次执行后再重复 2 次，总共执行 3 次。
            val executions = CountDownLatch(3)
            val counter = AtomicInteger(0)

            // When：创建有限次数间隔任务。
            taskManager.task("finite-retry") {
                interval(80)
                repeatCount(2)
                action {
                    counter.incrementAndGet()
                    executions.countDown()
                }
            }

            // Then：任务执行 3 次后被 Quartz 自动移除。
            assertAll(
                "有限重复次数任务",
                {
                    assertThat(executions.await(3, TimeUnit.SECONDS))
                        .`as`("有限次数任务应完成首次加重复次数的执行")
                        .isTrue()
                },
                {
                    assertThat(waitUntil { !taskManager.contains("finite-retry", "default") })
                        .`as`("有限次数任务完成后不应继续留在调度器中")
                        .isTrue()
                },
                {
                    assertThat(counter.get())
                        .`as`("repeatCount(2) 应产生 3 次总执行次数")
                        .isEqualTo(3)
                },
            )
        }

        @Test
        @DisplayName("时间单位扩展可直接作为 DSL 延迟值")
        fun `time unit extension can be used as dsl delay`() {
            // Given：业务用 1.seconds 表达延迟，保持 DSL 可读性。
            val executed = CountDownLatch(1)

            // When：创建延迟任务。
            taskManager.task("time-unit-delay") {
                delay(1.seconds)
                action { executed.countDown() }
            }

            // Then：任务应在 1 秒附近执行。
            assertThat(executed.await(5, TimeUnit.SECONDS))
                .`as`("DSL 应接受时间单位扩展返回的毫秒值")
                .isTrue()
        }
    }

    @Nested
    @DisplayName("异常场景")
    inner class ErrorScenarios {

        @ParameterizedTest(name = "{0}")
        @MethodSource("kim.der.timetask.dsl.TaskBuilderTest#invalidBuilderInputs")
        @DisplayName("DSL 拒绝非法时间配置并给出可诊断消息")
        fun `dsl rejects invalid timing values with diagnostic messages`(
            caseName: String,
            action: (TimeTaskManage) -> Unit,
            expectedMessagePart: String,
        ) {
            // Given：业务配置传入非法时间值。
            // When：构建 DSL 任务。
            val exception = assertThrows<IllegalArgumentException>("非法 DSL 配置应在注册阶段失败：$caseName") {
                action(taskManager)
            }

            // Then：异常消息应包含字段语义和非法值，便于定位配置来源。
            assertThat(exception.message)
                .`as`("异常消息应包含 '%s'，实际为：%s", expectedMessagePart, exception.message)
                .contains(expectedMessagePart)
        }
    }

    @Nested
    @DisplayName("组合场景")
    inner class CombinedScenarios {

        @Test
        @DisplayName("delay 与 interval 同时配置时按倒计时任务处理")
        fun `delay takes precedence over interval when both are configured`() {
            // Given：调用方先配置 delay 后又配置 interval，DSL 历史语义以 delay 为准。
            val executed = CountDownLatch(1)
            val counter = AtomicInteger(0)

            // When：构建组合任务。
            taskManager.task("delay-wins") {
                delay(80)
                interval(50)
                action {
                    counter.incrementAndGet()
                    executed.countDown()
                }
            }

            // Then：任务只执行一次并自动移除，而不是变成长期间隔任务。
            assertAll(
                "delay 优先级",
                {
                    assertThat(executed.await(3, TimeUnit.SECONDS))
                        .`as`("组合配置应触发倒计时任务")
                        .isTrue()
                },
                {
                    assertThat(waitUntil { !taskManager.contains("delay-wins", "default") })
                        .`as`("delay 优先时任务执行后应自动移除")
                        .isTrue()
                },
                {
                    assertThat(counter.get())
                        .`as`("delay 与 interval 同时配置时不应重复执行")
                        .isEqualTo(1)
                },
            )
        }

        @Test
        @DisplayName("cron 与 interval 同时配置时按 Cron 任务处理")
        fun `cron takes precedence over interval when delay is absent`() {
            // Given：调用方配置 Cron 后又配置 interval，DSL 在没有 delay 时以 Cron 为准。
            val executed = CountDownLatch(1)

            // When：构建组合任务。
            taskManager.task("cron-wins") {
                cron("0/1 * * * * ?")
                interval(50)
                action { executed.countDown() }
            }

            // Then：任务描述和状态体现 Cron 调度，并且不会被当作 50ms 间隔任务立即多次执行。
            assertAll(
                "cron 优先级",
                {
                    assertThat(taskManager.contains("cron-wins", "default"))
                        .`as`("Cron 组合任务应被注册")
                        .isTrue()
                },
                {
                    assertThat(taskManager.getDescription("cron-wins", "default"))
                        .`as`("没有自定义描述时应使用 Cron 默认描述")
                        .isEqualTo("Cron 任务: cron-wins")
                },
                {
                    assertThat(taskManager.getJobState("cron-wins", "default"))
                        .`as`("Cron 组合任务应处于 NORMAL 状态")
                        .isEqualTo(JobState.NORMAL)
                },
                {
                    assertThat(executed.await(3, TimeUnit.SECONDS))
                        .`as`("Cron 组合任务应按 Cron 表达式触发")
                        .isTrue()
                },
            )
        }
    }

    @Nested
    @DisplayName("便捷函数场景")
    inner class ShortcutScenarios {

        @Test
        @DisplayName("delayTask 保留自定义组名和描述")
        fun `delay task shortcut keeps group and description`() {
            // Given：业务使用便捷函数创建自定义组一次性任务。
            val executed = CountDownLatch(1)

            // When：调用 delayTask。
            taskManager.delayTask(
                name = "shortcut-delay",
                delayMillis = 80,
                group = "tenant-shortcut",
                description = "便捷延迟任务",
            ) {
                executed.countDown()
            }

            // Then：组名、描述和执行结果都应与完整 DSL 一致。
            assertAll(
                "delayTask 便捷函数",
                {
                    assertThat(taskManager.contains("shortcut-delay", "tenant-shortcut"))
                        .`as`("delayTask 应注册到指定组")
                        .isTrue()
                },
                {
                    assertThat(taskManager.getDescription("shortcut-delay", "tenant-shortcut"))
                        .`as`("delayTask 应保留描述")
                        .isEqualTo("便捷延迟任务")
                },
                {
                    assertThat(executed.await(3, TimeUnit.SECONDS))
                        .`as`("delayTask 应执行业务动作")
                        .isTrue()
                },
            )
        }

        @Test
        @DisplayName("intervalTask 支持延迟启动和自定义组")
        fun `interval task shortcut supports delay and custom group`() {
            // Given：业务用便捷函数创建延迟启动的定时任务。
            val executed = CountDownLatch(1)
            val before = System.currentTimeMillis()

            // When：调用 intervalTask。
            taskManager.intervalTask(
                name = "shortcut-interval",
                intervalMillis = 80,
                group = "tenant-shortcut",
                delayMillis = 200,
                description = "便捷间隔任务",
            ) {
                executed.countDown()
            }

            // Then：任务延迟启动并保留配置。
            assertAll(
                "intervalTask 便捷函数",
                {
                    assertThat(taskManager.contains("shortcut-interval", "tenant-shortcut"))
                        .`as`("intervalTask 应注册到指定组")
                        .isTrue()
                },
                {
                    assertThat(taskManager.getDescription("shortcut-interval", "tenant-shortcut"))
                        .`as`("intervalTask 应保留描述")
                        .isEqualTo("便捷间隔任务")
                },
                {
                    assertThat(executed.await(3, TimeUnit.SECONDS))
                        .`as`("intervalTask 应触发业务动作")
                        .isTrue()
                },
                {
                    assertThat(System.currentTimeMillis() - before)
                        .`as`("intervalTask 应尊重 delayMillis")
                        .isGreaterThanOrEqualTo(150)
                },
            )
        }

        @Test
        @DisplayName("cronTask 支持自定义组和描述")
        fun `cron task shortcut keeps group and description`() {
            // Given：业务用便捷函数创建 Cron 任务。
            val executed = CountDownLatch(1)

            // When：调用 cronTask。
            taskManager.cronTask(
                name = "shortcut-cron",
                cron = "0/1 * * * * ?",
                group = "tenant-shortcut",
                description = "便捷 Cron 任务",
            ) {
                executed.countDown()
            }

            // Then：任务应可查询、可触发，并保留业务描述。
            assertAll(
                "cronTask 便捷函数",
                {
                    assertThat(taskManager.contains("shortcut-cron", "tenant-shortcut"))
                        .`as`("cronTask 应注册到指定组")
                        .isTrue()
                },
                {
                    assertThat(taskManager.getDescription("shortcut-cron", "tenant-shortcut"))
                        .`as`("cronTask 应保留描述")
                        .isEqualTo("便捷 Cron 任务")
                },
                {
                    assertThat(taskManager.getJobState("shortcut-cron", "tenant-shortcut"))
                        .`as`("cronTask 创建后应正常调度")
                        .isEqualTo(JobState.NORMAL)
                },
                {
                    assertThat(executed.await(3, TimeUnit.SECONDS))
                        .`as`("cronTask 应按 Cron 表达式触发")
                        .isTrue()
                },
            )
        }
    }

    private companion object {
        @JvmStatic
        fun invalidBuilderInputs(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    "负 delay",
                    { manager: TimeTaskManage ->
                        manager.task("negative-delay") {
                            delay(-1)
                            action {}
                        }
                    },
                    "Delay must be non-negative, got: -1",
                ),
                Arguments.of(
                    "负 startAfter",
                    { manager: TimeTaskManage ->
                        manager.task("negative-start-after") {
                            startAfter(-1)
                            interval(100)
                            action {}
                        }
                    },
                    "Delay must be non-negative",
                ),
                Arguments.of(
                    "零 interval",
                    { manager: TimeTaskManage ->
                        manager.task("zero-interval") {
                            interval(0)
                            action {}
                        }
                    },
                    "Interval must be positive, got: 0",
                ),
                Arguments.of(
                    "非法 repeatCount",
                    { manager: TimeTaskManage ->
                        manager.task("invalid-repeat") {
                            interval(100)
                            repeatCount(-2)
                            action {}
                        }
                    },
                    "Repeat count must be -1 or non-negative, got: -2",
                ),
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
