/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.task

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
import org.quartz.CronTrigger
import org.quartz.SimpleTrigger
import org.quartz.TriggerKey
import java.util.Properties
import java.util.TimeZone
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.stream.Stream

/**
 * [TimeTaskManage] 核心调度管理器的业务契约测试。
 *
 * 测试使用真实 Quartz 调度器执行任务，优先验证可观察状态变化；
 * 只有 Quartz 上下文本身属于外部依赖时，才通过配置或公共 API 模拟异常路径。
 */
@DisplayName("TimeTaskManage 核心调度管理器")
class TimeTaskManageTest {
    private lateinit var taskManager: TimeTaskManage

    @BeforeEach
    fun setUp() {
        taskManager = TimeTaskManage()
    }

    @AfterEach
    fun tearDown() {
        if (this::taskManager.isInitialized && taskManager.isRunning) {
            taskManager.shutdownNow()
        }
    }

    @Nested
    @DisplayName("构造与生命周期场景")
    inner class ConstructorAndLifecycleScenarios {

        @Test
        @DisplayName("默认构造器创建运行中的调度器")
        fun `default constructor creates running scheduler`() {
            // Given：业务使用默认配置创建调度管理器。
            // When：读取调度器状态。
            // Then：调度器应已启动，且未进入待机。
            assertAll(
                "默认调度器状态",
                {
                    assertThat(taskManager.isRunning)
                        .`as`("默认构造器应启动 Quartz 调度器")
                        .isTrue()
                },
                {
                    assertThat(taskManager.isStandby)
                        .`as`("默认调度器不应处于 standby 模式")
                        .isFalse()
                },
                {
                    assertThat(taskManager.jobCount)
                        .`as`("新建调度器不应包含任务")
                        .isZero()
                },
            )
        }

        @ParameterizedTest(name = "线程池大小 {0}")
        @MethodSource("kim.der.timetask.task.TimeTaskManageTest#validThreadPoolSizes")
        @DisplayName("线程池大小边界内可创建调度器")
        fun `thread pool size inside boundary creates scheduler`(threadPoolSize: Int) {
            // Given：业务按支持范围配置线程池大小。
            val manager = TimeTaskManage(threadPoolSize)

            // When & Then：调度器应可启动，并在测试结束时关闭资源。
            try {
                assertThat(manager.isRunning)
                    .`as`("合法线程池大小应创建运行中的调度器：%s", threadPoolSize)
                    .isTrue()
            } finally {
                manager.shutdownNow()
            }
        }

        @ParameterizedTest(name = "非法线程池大小 {0}")
        @MethodSource("kim.der.timetask.task.TimeTaskManageTest#invalidThreadPoolSizes")
        @DisplayName("线程池大小越界时给出可诊断异常")
        fun `invalid thread pool size fails with diagnostic message`(threadPoolSize: Int) {
            // Given：业务配置越界线程池大小。
            // When：创建调度管理器。
            val exception = assertThrows<IllegalArgumentException>("越界线程池大小应在构造阶段失败") {
                TimeTaskManage(threadPoolSize)
            }

            // Then：异常消息应包含允许范围和实际值。
            assertThat(exception.message)
                .`as`("线程池大小异常消息应便于定位配置错误")
                .isEqualTo("Thread pool size must be between 1 and 100, got: $threadPoolSize")
        }

        @Test
        @DisplayName("自定义 Quartz 配置可创建独立调度器")
        fun `custom quartz properties create independent scheduler`() {
            // Given：业务提供自定义 Quartz 配置。
            val props = Properties().apply {
                setProperty("org.quartz.scheduler.instanceName", "TimeTaskQuartz-Test-${System.nanoTime()}")
                setProperty("org.quartz.threadPool.threadCount", "2")
                setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore")
                setProperty("org.quartz.scheduler.skipUpdateCheck", "true")
            }
            val manager = TimeTaskManage(props)

            // When & Then：自定义调度器应运行，并能安全关闭。
            try {
                assertAll(
                    "自定义调度器状态",
                    {
                        assertThat(manager.isRunning)
                            .`as`("自定义配置应启动 Quartz 调度器")
                            .isTrue()
                    },
                    {
                        assertThat(manager.jobCount)
                            .`as`("自定义调度器初始任务数应为 0")
                            .isZero()
                    },
                )
            } finally {
                manager.shutdownNow()
            }
        }

        @Test
        @DisplayName("standby 与 resume 在合法状态间切换")
        fun `standby and resume transition scheduler state`() {
            // Given：调度器正在运行。
            // When：进入 standby，再恢复。
            taskManager.standby()
            val standbyState = taskManager.isStandby
            taskManager.resume()

            // Then：状态应按 Quartz 生命周期切换。
            assertAll(
                "standby/resume 状态转换",
                {
                    assertThat(standbyState)
                        .`as`("standby 后调度器应进入待机状态")
                        .isTrue()
                },
                {
                    assertThat(taskManager.isStandby)
                        .`as`("resume 后调度器应退出待机状态")
                        .isFalse()
                },
                {
                    assertThat(taskManager.isRunning)
                        .`as`("resume 后调度器仍应运行")
                        .isTrue()
                },
            )
        }
    }

    @Nested
    @DisplayName("倒计时任务场景")
    inner class CountdownScenarios {

        @Test
        @DisplayName("未来时间倒计时任务执行一次后自动删除")
        fun `countdown executes once and removes itself`() {
            // Given：业务配置未来执行的一次性提醒任务。
            val executed = CountDownLatch(1)
            val counter = AtomicInteger(0)

            // When：添加倒计时任务。
            taskManager.addCountdown(
                name = "reminder",
                group = "alerts",
                description = "一次性提醒",
                startTime = System.currentTimeMillis() + 120,
            ) {
                counter.incrementAndGet()
                executed.countDown()
            }

            // Then：任务先存在，执行后只触发一次并自动移除。
            assertAll(
                "倒计时任务生命周期",
                {
                    assertThat(taskManager.contains("reminder", "alerts"))
                        .`as`("倒计时任务注册后应可查询")
                        .isTrue()
                },
                {
                    assertThat(executed.await(3, TimeUnit.SECONDS))
                        .`as`("倒计时任务应在配置时间后执行")
                        .isTrue()
                },
                {
                    assertThat(waitUntil { !taskManager.contains("reminder", "alerts") })
                        .`as`("倒计时任务执行后应自然清理 non-durable Job")
                        .isTrue()
                },
                {
                    assertThat(counter.get())
                        .`as`("倒计时任务应只执行一次")
                        .isEqualTo(1)
                },
            )
        }

        @Test
        @DisplayName("手动触发未来倒计时后仍保留原定执行")
        fun `trigger now does not consume future countdown schedule`() {
            // Given：未来仍有一次正式调度的倒计时任务。
            val counter = AtomicInteger(0)
            val executions = CountDownLatch(2)
            taskManager.addCountdown(
                name = "manual-countdown",
                group = "alerts",
                description = "手动预触发后仍需正式执行",
                startTime = System.currentTimeMillis() + 2_000,
            ) {
                counter.incrementAndGet()
                executions.countDown()
            }

            // When：在正式时间之前手动触发一次。
            val triggered = taskManager.triggerNow("manual-countdown", "alerts")

            // Then：手动执行不应消费原 trigger，正式时间仍会再次执行并自然清理。
            assertThat(triggered)
                .`as`("未来倒计时应支持手动触发")
                .isTrue()
            assertThat(waitUntil(timeoutMillis = 1_000) { counter.get() == 1 })
                .`as`("手动触发应先执行一次业务动作")
                .isTrue()
            assertThat(taskManager.contains("manual-countdown", "alerts"))
                .`as`("手动触发后原倒计时仍应保持注册")
                .isTrue()
            assertThat(executions.await(4, TimeUnit.SECONDS))
                .`as`("到达原定时间后应再次执行")
                .isTrue()
            assertThat(counter.get())
                .`as`("手动触发与原定触发应各执行一次")
                .isEqualTo(2)
            assertThat(waitUntil { !taskManager.contains("manual-countdown", "alerts") })
                .`as`("原定一次性触发完成后应自然清理")
                .isTrue()
        }

        @Test
        @DisplayName("过去时间倒计时任务立即执行")
        fun `countdown with past time executes immediately`() {
            // Given：业务补录一个已经到期的任务。
            val executed = CountDownLatch(1)

            // When：添加过去时间任务。
            taskManager.addCountdown(
                name = "expired-reminder",
                group = "alerts",
                description = "已到期提醒",
                startTime = System.currentTimeMillis() - 1_000,
            ) {
                executed.countDown()
            }

            // Then：Quartz 应尽快触发已到期任务。
            assertThat(executed.await(3, TimeUnit.SECONDS))
                .`as`("过去时间倒计时任务应立即补偿执行")
                .isTrue()
        }

        @Test
        @DisplayName("倒计时任务业务动作抛错时仍清理任务定义")
        fun `countdown removes job even when action throws`() {
            // Given：一次性任务执行过程中外部依赖失败。
            val attempted = CountDownLatch(1)

            // When：添加会抛错的倒计时任务。
            taskManager.addCountdown(
                name = "throwing-reminder",
                group = "alerts",
                description = "失败也要清理",
                startTime = System.currentTimeMillis() - 1_000,
            ) {
                attempted.countDown()
                throw IllegalStateException("通知网关不可用")
            }

            // Then：即使业务动作失败，Quartz 仍应按 non-durable 生命周期自然清理。
            assertAll(
                "倒计时异常清理",
                {
                    assertThat(attempted.await(3, TimeUnit.SECONDS))
                        .`as`("失败任务也应实际尝试执行")
                        .isTrue()
                },
                {
                    assertThat(waitUntil { !taskManager.contains("throwing-reminder", "alerts") })
                        .`as`("业务动作抛错后仍应清理一次性任务")
                        .isTrue()
                },
            )
        }
    }

    @Nested
    @DisplayName("间隔与 Cron 场景")
    inner class ScheduledScenarios {

        @Test
        @DisplayName("固定间隔任务重复执行且保持注册状态")
        fun `interval task repeats and stays registered`() {
            // Given：业务配置服务心跳任务。
            val executions = CountDownLatch(3)
            val counter = AtomicInteger(0)

            // When：添加固定间隔任务。
            taskManager.addTimedTask(
                name = "heartbeat",
                group = "system",
                description = "系统心跳",
                startTime = System.currentTimeMillis(),
                intervalTime = 80,
            ) {
                counter.incrementAndGet()
                executions.countDown()
            }

            // Then：任务重复执行且不会自动删除。
            assertAll(
                "固定间隔任务",
                {
                    assertThat(executions.await(3, TimeUnit.SECONDS))
                        .`as`("固定间隔任务应重复执行")
                        .isTrue()
                },
                {
                    assertThat(counter.get())
                        .`as`("心跳任务应产生多次状态变化")
                        .isGreaterThanOrEqualTo(3)
                },
                {
                    assertThat(taskManager.contains("heartbeat", "system"))
                        .`as`("无限重复任务执行后仍应保留")
                        .isTrue()
                },
                {
                    assertThat(taskManager.getJobState("heartbeat", "system"))
                        .`as`("无限重复任务应保持 NORMAL 状态")
                        .isEqualTo(JobState.NORMAL)
                },
            )
        }

        @Test
        @DisplayName("有限重复任务在完成后自动结束并移除非 durable Job")
        fun `finite interval task completes after configured repeat count`() {
            // Given：业务配置最多尝试 3 次的重试任务。
            val executions = CountDownLatch(3)
            val counter = AtomicInteger(0)

            // When：repeatCount=2 表示首次触发后再重复 2 次。
            taskManager.addTimedTask(
                name = "finite-retry",
                group = "system",
                description = "有限重试",
                startTime = System.currentTimeMillis(),
                intervalTime = 80,
                repeatCount = 2,
            ) {
                counter.incrementAndGet()
                executions.countDown()
            }

            // Then：总执行次数为 3，完成后任务不再存在。
            assertAll(
                "有限重复任务",
                {
                    assertThat(executions.await(3, TimeUnit.SECONDS))
                        .`as`("有限重复任务应完成全部配置次数")
                        .isTrue()
                },
                {
                    assertThat(waitUntil { !taskManager.contains("finite-retry", "system") })
                        .`as`("非 durable 有限任务完成后应从调度器移除")
                        .isTrue()
                },
                {
                    assertThat(counter.get())
                        .`as`("repeatCount=2 应对应 3 次总执行")
                        .isEqualTo(3)
                },
            )
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("kim.der.timetask.task.TimeTaskManageTest#invalidIntervalInputs")
        @DisplayName("非法间隔配置在注册阶段失败")
        fun `invalid interval task configuration fails early`(
            caseName: String,
            intervalMillis: Long,
            repeatCount: Int,
            expectedMessagePart: String,
        ) {
            // Given：业务传入非法间隔或重复次数。
            // When：添加间隔任务。
            val exception = assertThrows<IllegalArgumentException>("非法间隔任务配置应立即失败：$caseName") {
                taskManager.addTimedTask(
                    name = "invalid-$caseName",
                    group = "system",
                    description = "非法配置",
                    startTime = System.currentTimeMillis(),
                    intervalTime = intervalMillis,
                    repeatCount = repeatCount,
                ) {}
            }

            // Then：异常消息应指出具体非法字段。
            assertThat(exception.message)
                .`as`("非法配置异常消息应包含 '%s'，实际为：%s", expectedMessagePart, exception.message)
                .contains(expectedMessagePart)
        }

        @Test
        @DisplayName("Cron 任务按表达式触发")
        fun `cron task executes according to expression`() {
            // Given：业务配置每秒执行的巡检任务。
            val executions = CountDownLatch(2)
            val counter = AtomicInteger(0)

            // When：添加 Cron 任务。
            taskManager.addTimedTask(
                name = "cron-health-check",
                group = "system",
                description = "Cron 巡检",
                cron = "0/1 * * * * ?",
            ) {
                counter.incrementAndGet()
                executions.countDown()
            }

            // Then：Cron 任务应被 Quartz 调度触发。
            assertAll(
                "Cron 任务执行",
                {
                    assertThat(taskManager.contains("cron-health-check", "system"))
                        .`as`("Cron 任务注册后应可查询")
                        .isTrue()
                },
                {
                    assertThat(taskManager.getJobState("cron-health-check", "system"))
                        .`as`("Cron 任务应处于 NORMAL 状态")
                        .isEqualTo(JobState.NORMAL)
                },
                {
                    assertThat(executions.await(5, TimeUnit.SECONDS))
                        .`as`("Cron 任务应按表达式触发至少两次")
                        .isTrue()
                },
                {
                    assertThat(counter.get())
                        .`as`("Cron 任务应产生多次业务状态变化")
                        .isGreaterThanOrEqualTo(2)
                },
            )
        }
    }

    @Nested
    @DisplayName("任务管理状态场景")
    inner class ManagementScenarios {

        @Test
        @DisplayName("contains 精确区分任务名和组名")
        fun `contains matches exact job name and group`() {
            // Given：任务名和组名包含真实环境常见的国际化和特殊字符。
            val name = "report_日报:2026"
            val group = "tenant-中国"

            // When：添加未来执行任务。
            taskManager.addCountdown(name, group, "特殊字符任务", System.currentTimeMillis() + 60_000) {}

            // Then：contains 应精确匹配 name/group 组合。
            assertAll(
                "contains 精确匹配",
                {
                    assertThat(taskManager.contains(name, group))
                        .`as`("正确 name/group 应存在")
                        .isTrue()
                },
                {
                    assertThat(taskManager.contains(name, "other"))
                        .`as`("相同 name 不同 group 不应匹配")
                        .isFalse()
                },
                {
                    assertThat(taskManager.contains("other", group))
                        .`as`("相同 group 不同 name 不应匹配")
                        .isFalse()
                },
            )
        }

        @Test
        @DisplayName("任务可暂停、恢复并保持状态可观测")
        fun `pause and resume transition job state`() {
            // Given：未来才会自然执行的间隔任务，便于观察状态。
            taskManager.addTimedTask(
                name = "maintenance",
                group = "system",
                description = "维护窗口",
                startTime = System.currentTimeMillis() + 60_000,
                intervalTime = 1_000,
            ) {}

            // When & Then：状态从 NORMAL -> PAUSED -> NORMAL。
            assertAll(
                "任务暂停恢复状态",
                {
                    assertThat(taskManager.getJobState("maintenance", "system"))
                        .`as`("新建任务应处于 NORMAL")
                        .isEqualTo(JobState.NORMAL)
                },
                {
                    assertThat(taskManager.pause("maintenance", "system"))
                        .`as`("存在任务应可暂停")
                        .isTrue()
                },
                {
                    assertThat(taskManager.getJobState("maintenance", "system"))
                        .`as`("暂停后状态应为 PAUSED")
                        .isEqualTo(JobState.PAUSED)
                },
                {
                    assertThat(taskManager.resume("maintenance", "system"))
                        .`as`("已暂停任务应可恢复")
                        .isTrue()
                },
                {
                    assertThat(taskManager.getJobState("maintenance", "system"))
                        .`as`("恢复后状态应为 NORMAL")
                        .isEqualTo(JobState.NORMAL)
                },
            )
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("kim.der.timetask.task.TimeTaskManageTest#missingTaskOperations")
        @DisplayName("不存在任务的状态变更安全返回失败")
        fun `missing task operations return false`(
            caseName: String,
            operation: (TimeTaskManage) -> Boolean,
        ) {
            // Given：目标任务不存在。
            // When：执行状态变更。
            val result = operation(taskManager)

            // Then：返回 false，不向调用方抛出 Quartz 细节。
            assertThat(result)
                .`as`("不存在任务操作应返回 false：%s", caseName)
                .isFalse()
        }

        @Test
        @DisplayName("remove 删除任务并保持幂等失败语义")
        fun `remove deletes task and repeated remove returns false`() {
            // Given：一个可删除的未来任务。
            taskManager.addCountdown("to-remove", "system", "删除测试", System.currentTimeMillis() + 60_000) {}

            // When & Then：第一次删除成功，第二次删除返回 false。
            assertAll(
                "remove 幂等语义",
                {
                    assertThat(taskManager.contains("to-remove", "system"))
                        .`as`("删除前任务应存在")
                        .isTrue()
                },
                {
                    assertThat(taskManager.remove("to-remove", "system"))
                        .`as`("第一次删除存在任务应成功")
                        .isTrue()
                },
                {
                    assertThat(taskManager.contains("to-remove", "system"))
                        .`as`("删除后任务不应存在")
                        .isFalse()
                },
                {
                    assertThat(taskManager.remove("to-remove", "system"))
                        .`as`("重复删除不存在任务应返回 false")
                        .isFalse()
                },
            )
        }

        @Test
        @DisplayName("triggerNow 立即触发存在任务的业务动作")
        fun `trigger now executes existing job action`() {
            // Given：自然触发时间很远的任务，只有 triggerNow 会在测试窗口内执行。
            val triggered = CountDownLatch(1)
            taskManager.addTimedTask(
                name = "manual-trigger",
                group = "system",
                description = "手动触发",
                startTime = System.currentTimeMillis() + 60_000,
                intervalTime = 1_000,
            ) {
                triggered.countDown()
            }

            // When：手动触发任务。
            val result = taskManager.triggerNow("manual-trigger", "system")

            // Then：返回值和业务动作都应证明立即触发真实生效。
            assertAll(
                "triggerNow 立即触发",
                {
                    assertThat(result)
                        .`as`("存在任务 triggerNow 应返回 true")
                        .isTrue()
                },
                {
                    assertThat(triggered.await(3, TimeUnit.SECONDS))
                        .`as`("triggerNow 应实际执行业务动作")
                        .isTrue()
                },
            )
        }

        @Test
        @DisplayName("jobCount 随任务增删反映当前任务总数")
        fun `job count follows task lifecycle`() {
            // Given：空调度器。
            // When & Then：任务增删过程中 jobCount 应反映当前状态。
            assertThat(taskManager.jobCount)
                .`as`("空调度器任务数应为 0")
                .isZero()

            taskManager.addCountdown("job-1", "system", "任务1", System.currentTimeMillis() + 60_000) {}
            taskManager.addCountdown("job-2", "other", "任务2", System.currentTimeMillis() + 60_000) {}

            assertAll(
                "任务数新增",
                {
                    assertThat(taskManager.jobCount)
                        .`as`("跨组任务都应计入 jobCount")
                        .isEqualTo(2)
                },
                {
                    assertThat(taskManager.remove("job-1", "system"))
                        .`as`("删除第一个任务应成功")
                        .isTrue()
                },
                {
                    assertThat(taskManager.jobCount)
                        .`as`("删除一个任务后 jobCount 应减少")
                        .isEqualTo(1)
                },
                {
                    assertThat(taskManager.remove("job-2", "other"))
                        .`as`("删除第二个任务应成功")
                        .isTrue()
                },
                {
                    assertThat(taskManager.jobCount)
                        .`as`("任务全部删除后 jobCount 应归零")
                        .isZero()
                },
            )
        }

        @Test
        @DisplayName("不存在任务状态查询返回 null")
        fun `missing job state is null`() {
            // Given：任务不存在。
            // When & Then：不存在任务没有状态。
            assertThat(taskManager.getJobState("missing", "system"))
                .`as`("不存在任务状态应为 null")
                .isNull()
        }
    }

    @Nested
    @DisplayName("重调度场景")
    inner class RescheduleScenarios {

        @Test
        @DisplayName("间隔任务重调度后按新间隔执行")
        fun `interval task reschedules to new interval`() {
            // Given：原始间隔很长的任务。
            val counter = AtomicInteger(0)
            taskManager.addTimedTask(
                name = "reschedule-interval",
                group = "system",
                description = "重调度间隔",
                startTime = System.currentTimeMillis() + 60_000,
                intervalTime = 60_000,
            ) {
                counter.incrementAndGet()
            }

            // When：改为短间隔并从当前时间重新起算。
            val result = taskManager.reschedule("reschedule-interval", "system", 80)

            // Then：任务应按新间隔快速触发。
            assertAll(
                "间隔重调度",
                {
                    assertThat(result)
                        .`as`("存在的间隔任务应可重调度")
                        .isTrue()
                },
                {
                    assertThat(waitUntil(timeoutMillis = 3_000) { counter.get() >= 2 })
                        .`as`("重调度后任务应按新间隔重复执行")
                        .isTrue()
                },
            )
        }

        @Test
        @DisplayName("Cron 任务可更新为新的 Cron 表达式")
        fun `cron task reschedules to new expression`() {
            // Given：原始 Cron 很远，测试窗口内不会自然触发。
            val executed = CountDownLatch(1)
            taskManager.addTimedTask(
                name = "reschedule-cron",
                group = "system",
                description = "重调度 Cron",
                cron = "0 0 12 * * ?",
            ) {
                executed.countDown()
            }

            // When：改为每秒触发。
            val result = taskManager.reschedule("reschedule-cron", "system", "0/1 * * * * ?")

            // Then：重调度成功，任务在测试窗口内被触发。
            assertAll(
                "Cron 重调度",
                {
                    assertThat(result)
                        .`as`("存在的 Cron 任务应可重调度")
                        .isTrue()
                },
                {
                    assertThat(executed.await(3, TimeUnit.SECONDS))
                        .`as`("更新 Cron 后任务应按新表达式触发")
                        .isTrue()
                },
            )
        }

        @Test
        @DisplayName("重调度拒绝静默改变任务类型")
        fun `reschedule rejects cross type trigger conversion`() {
            // Given：分别存在 Cron 与固定间隔任务。
            taskManager.addTimedTask(
                name = "cron-contract",
                group = "system",
                description = "Cron 类型契约",
                cron = "0 0 12 * * ?",
            ) {}
            taskManager.addTimedTask(
                name = "interval-contract",
                group = "system",
                description = "间隔类型契约",
                startTime = System.currentTimeMillis() + 60_000,
                intervalTime = 60_000,
            ) {}

            // When：调用与原 trigger 类型不匹配的重调度入口。
            val cronToInterval = taskManager.reschedule("cron-contract", "system", 1_000)
            val intervalToCron = taskManager.reschedule("interval-contract", "system", "0/1 * * * * ?")

            // Then：操作失败且原 trigger 类型保持不变。
            assertAll(
                "跨类型重调度保护",
                {
                    assertThat(cronToInterval)
                        .`as`("Cron 任务不应被间隔入口静默转换")
                        .isFalse()
                },
                {
                    assertThat(intervalToCron)
                        .`as`("间隔任务不应被 Cron 入口静默转换")
                        .isFalse()
                },
                {
                    assertThat(taskManager.scheduler.getTrigger(TriggerKey("cron-contract", "system")))
                        .`as`("Cron trigger 类型应保持不变")
                        .isInstanceOf(CronTrigger::class.java)
                },
                {
                    assertThat(taskManager.scheduler.getTrigger(TriggerKey("interval-contract", "system")))
                        .`as`("Simple trigger 类型应保持不变")
                        .isInstanceOf(SimpleTrigger::class.java)
                },
            )
        }

        @Test
        @DisplayName("有限间隔任务重调度后保留剩余执行次数")
        fun `interval reschedule preserves finite repeat policy`() {
            // Given：总共计划执行三次的有限任务已完成第一次执行。
            val executionCount = AtomicInteger(0)
            val firstExecution = CountDownLatch(1)
            val allExecutions = CountDownLatch(3)
            taskManager.addTimedTask(
                name = "finite-reschedule",
                group = "system",
                description = "有限次数重调度",
                startTime = System.currentTimeMillis() + 200,
                intervalTime = 60_000,
                repeatCount = 2,
            ) {
                if (executionCount.incrementAndGet() == 1) {
                    firstExecution.countDown()
                }
                allExecutions.countDown()
            }
            assertThat(firstExecution.await(3, TimeUnit.SECONDS))
                .`as`("有限任务应先完成第一次执行")
                .isTrue()

            // When：第一次执行后只修改执行间隔。
            val result = taskManager.reschedule("finite-reschedule", "system", 100)
            val trigger =
                taskManager.scheduler.getTrigger(TriggerKey("finite-reschedule", "system")) as SimpleTrigger

            // Then：新 trigger 只保留剩余两次执行，完成后自然清理 Job。
            assertAll(
                "有限重复策略",
                {
                    assertThat(result)
                        .`as`("同类型间隔任务应可重调度")
                        .isTrue()
                },
                {
                    assertThat(trigger.repeatCount)
                        .`as`("已执行一次后，新 trigger 应仅再重复一次")
                        .isEqualTo(1)
                },
            )
            assertThat(allExecutions.await(3, TimeUnit.SECONDS))
                .`as`("重调度后应完成原计划剩余两次执行")
                .isTrue()
            assertThat(executionCount.get())
                .`as`("重调度不应增加或减少有限任务的总执行次数")
                .isEqualTo(3)
            assertThat(waitUntil { !taskManager.contains("finite-reschedule", "system") })
                .`as`("有限任务完成全部执行后应自然清理")
                .isTrue()
        }

        @Test
        @DisplayName("Cron 重调度保留原任务时区")
        fun `cron reschedule preserves configured time zone`() {
            // Given：使用与 JVM 默认值不同的业务时区创建 Cron 任务。
            val timeZone =
                listOf("Pacific/Honolulu", "UTC", "Asia/Tokyo")
                    .map(TimeZone::getTimeZone)
                    .first { it.id != TimeZone.getDefault().id }
            taskManager.addTimedTask(
                name = "timezone-reschedule",
                group = "system",
                description = "跨时区报表",
                cron = "0 0 12 * * ?",
                timeZone = timeZone,
            ) {}

            // When：只更新 Cron 表达式。
            val result = taskManager.reschedule("timezone-reschedule", "system", "0 30 12 * * ?")
            val trigger =
                taskManager.scheduler.getTrigger(TriggerKey("timezone-reschedule", "system")) as CronTrigger

            // Then：原业务时区必须继续生效。
            assertAll(
                "Cron 时区契约",
                {
                    assertThat(result)
                        .`as`("同类型 Cron 任务应可重调度")
                        .isTrue()
                },
                {
                    assertThat(trigger.timeZone.id)
                        .`as`("重调度不应回落到 JVM 默认时区")
                        .isEqualTo(timeZone.id)
                },
            )
        }

        @Test
        @DisplayName("重调度不存在任务安全返回 false")
        fun `reschedule missing task returns false`() {
            // Given：目标任务不存在。
            // When & Then：两种重调度入口均安全返回 false。
            assertAll(
                "不存在任务重调度",
                {
                    assertThat(taskManager.reschedule("missing", "system", 1_000))
                        .`as`("不存在间隔任务重调度应返回 false")
                        .isFalse()
                },
                {
                    assertThat(taskManager.reschedule("missing", "system", "0/1 * * * * ?"))
                        .`as`("不存在 Cron 任务重调度应返回 false")
                        .isFalse()
                },
            )
        }

        @Test
        @DisplayName("非法重调度参数保持原任务状态")
        fun `invalid reschedule parameter keeps original task`() {
            // Given：存在一个正常任务。
            taskManager.addTimedTask(
                name = "invalid-reschedule",
                group = "system",
                description = "非法重调度",
                startTime = System.currentTimeMillis() + 60_000,
                intervalTime = 1_000,
            ) {}

            // When：使用非法间隔重调度。
            val exception = assertThrows<IllegalArgumentException>("非法新间隔应失败") {
                taskManager.reschedule("invalid-reschedule", "system", 0)
            }

            // Then：异常可诊断，原任务仍存在且状态正常。
            assertAll(
                "非法重调度保护",
                {
                    assertThat(exception.message)
                        .`as`("非法新间隔异常消息应清晰")
                        .isEqualTo("Interval must be positive")
                },
                {
                    assertThat(taskManager.contains("invalid-reschedule", "system"))
                        .`as`("非法重调度不应删除原任务")
                        .isTrue()
                },
                {
                    assertThat(taskManager.getJobState("invalid-reschedule", "system"))
                        .`as`("非法重调度后原任务应保持 NORMAL")
                        .isEqualTo(JobState.NORMAL)
                },
            )
        }
    }

    @Nested
    @DisplayName("并发与资源清理场景")
    inner class ConcurrentAndShutdownScenarios {

        @Test
        @DisplayName("并发 triggerNow 不丢失触发状态")
        fun `concurrent trigger now records every business execution`() {
            // Given：多个外部请求同时手动触发同一个任务。
            val workerCount = 4
            val startTogether = CyclicBarrier(workerCount)
            val finished = CountDownLatch(workerCount)
            val executions = CountDownLatch(workerCount)
            val failures = ConcurrentLinkedQueue<Throwable>()
            val counter = AtomicInteger(0)

            taskManager.addTimedTask(
                name = "concurrent-trigger",
                group = "system",
                description = "并发手动触发",
                startTime = System.currentTimeMillis() + 60_000,
                intervalTime = 60_000,
            ) {
                counter.incrementAndGet()
                executions.countDown()
            }

            // When：并发调用公共 triggerNow API。
            val workers = List(workerCount) {
                Thread {
                    try {
                        startTogether.await(2, TimeUnit.SECONDS)
                        assertThat(taskManager.triggerNow("concurrent-trigger", "system"))
                            .`as`("并发 triggerNow 应返回成功")
                            .isTrue()
                    } catch (throwable: Throwable) {
                        failures.add(throwable)
                    } finally {
                        finished.countDown()
                    }
                }
            }
            workers.forEach(Thread::start)

            // Then：调用线程和业务动作都应在窗口内完成。
            assertAll(
                "并发手动触发",
                {
                    assertThat(finished.await(3, TimeUnit.SECONDS))
                        .`as`("并发 triggerNow 调用应全部返回")
                        .isTrue()
                },
                {
                    assertThat(executions.await(3, TimeUnit.SECONDS))
                        .`as`("每次并发 triggerNow 都应触发业务动作")
                        .isTrue()
                },
                {
                    assertThat(failures)
                        .`as`("并发触发不应产生异常：${failures.joinToString { it.message.orEmpty() }}")
                        .isEmpty()
                },
                {
                    assertThat(counter.get())
                        .`as`("业务执行次数应等于并发触发次数")
                        .isEqualTo(workerCount)
                },
            )
            workers.forEach { it.join(1_000) }
        }

        @Test
        @DisplayName("shutdownNow 不等待正在执行的任务完成")
        fun `shutdown now returns without waiting for running job`() {
            // Given：正在执行的任务被外部依赖阻塞。
            val started = CountDownLatch(1)
            val allowFinish = CountDownLatch(1)
            val finished = CountDownLatch(1)
            val elapsedMillis = AtomicLong(-1)
            val shutdownReturned = CountDownLatch(1)

            taskManager.addCountdown(
                name = "blocking-shutdown-now",
                group = "system",
                description = "shutdownNow 阻塞测试",
                startTime = System.currentTimeMillis() - 1_000,
            ) {
                started.countDown()
                allowFinish.await(5, TimeUnit.SECONDS)
                finished.countDown()
            }

            assertThat(started.await(3, TimeUnit.SECONDS))
                .`as`("阻塞任务应已经开始执行")
                .isTrue()

            // When：调用 shutdownNow。
            val shutdownThread = Thread {
                val start = System.nanoTime()
                taskManager.shutdownNow()
                elapsedMillis.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
                shutdownReturned.countDown()
            }
            shutdownThread.start()

            // Then：shutdownNow 快速返回，但任务可在释放后完成清理。
            try {
                assertAll(
                    "shutdownNow 资源清理",
                    {
                        assertThat(shutdownReturned.await(1, TimeUnit.SECONDS))
                            .`as`("shutdownNow 不应等待阻塞任务完成")
                            .isTrue()
                    },
                    {
                        assertThat(elapsedMillis.get())
                            .`as`("shutdownNow 返回耗时应小于 1 秒")
                            .isBetween(0, 999)
                    },
                )
            } finally {
                allowFinish.countDown()
                assertThat(finished.await(5, TimeUnit.SECONDS))
                    .`as`("释放阻塞后任务应完成")
                    .isTrue()
                shutdownThread.join(5_000)
            }
        }

        @Test
        @DisplayName("shutdown 默认等待正在执行的任务完成")
        fun `shutdown waits for running job by default`() {
            // Given：正在执行的任务需要等待外部信号结束。
            val started = CountDownLatch(1)
            val allowFinish = CountDownLatch(1)
            val shutdownReturned = CountDownLatch(1)

            taskManager.addCountdown(
                name = "blocking-shutdown",
                group = "system",
                description = "shutdown 等待测试",
                startTime = System.currentTimeMillis() - 1_000,
            ) {
                started.countDown()
                allowFinish.await(5, TimeUnit.SECONDS)
            }

            assertThat(started.await(3, TimeUnit.SECONDS))
                .`as`("阻塞任务应已经开始执行")
                .isTrue()

            // When：在后台调用默认 shutdown。
            val shutdownThread = Thread {
                taskManager.shutdown()
                shutdownReturned.countDown()
            }
            shutdownThread.start()

            // Then：释放任务前 shutdown 不应返回；释放后应正常结束。
            try {
                assertThat(shutdownReturned.await(200, TimeUnit.MILLISECONDS))
                    .`as`("默认 shutdown 应等待正在执行的任务完成")
                    .isFalse()
                allowFinish.countDown()
                assertThat(shutdownReturned.await(5, TimeUnit.SECONDS))
                    .`as`("任务完成后默认 shutdown 应返回")
                    .isTrue()
                assertThat(taskManager.isRunning)
                    .`as`("shutdown 完成后调度器应停止运行")
                    .isFalse()
            } finally {
                allowFinish.countDown()
                shutdownThread.join(5_000)
            }
        }

        @Test
        @DisplayName("shutdownNow 可重复调用保持幂等")
        fun `shutdown now is idempotent`() {
            // Given：运行中的调度器。
            // When：重复关闭。
            taskManager.shutdownNow()
            taskManager.shutdownNow()

            // Then：重复关闭不抛异常，状态保持停止。
            assertThat(taskManager.isRunning)
                .`as`("重复 shutdownNow 后调度器应保持停止状态")
                .isFalse()
        }
    }

    private companion object {
        @JvmStatic
        fun validThreadPoolSizes(): Stream<Arguments> =
            Stream.of(
                Arguments.of(1),
                Arguments.of(3),
            )

        @JvmStatic
        fun invalidThreadPoolSizes(): Stream<Arguments> =
            Stream.of(
                Arguments.of(0),
                Arguments.of(101),
            )

        @JvmStatic
        fun invalidIntervalInputs(): Stream<Arguments> =
            Stream.of(
                Arguments.of("零间隔", 0L, -1, "Interval time must be positive, got: 0"),
                Arguments.of("负间隔", -100L, -1, "Interval time must be positive, got: -100"),
                Arguments.of("非法重复次数", 100L, -2, "Repeat count must be -1 or non-negative, got: -2"),
            )

        @JvmStatic
        fun missingTaskOperations(): Stream<Arguments> =
            Stream.of(
                Arguments.of("pause", { manager: TimeTaskManage -> manager.pause("missing", "system") }),
                Arguments.of("resume", { manager: TimeTaskManage -> manager.resume("missing", "system") }),
                Arguments.of("remove", { manager: TimeTaskManage -> manager.remove("missing", "system") }),
                Arguments.of("triggerNow", { manager: TimeTaskManage -> manager.triggerNow("missing", "system") }),
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
