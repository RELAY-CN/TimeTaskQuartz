/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.run

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream

/**
 * [RunnableRun] 的高价值业务测试。
 *
 * 这些用例只 mock Quartz 提供的执行上下文，真实使用 [JobDataMap] 和业务状态对象，
 * 确保测试验证的是任务执行契约，而不是 mock 调用次数等实现细节。
 */
@DisplayName("RunnableRun Quartz 任务执行适配器")
class RunnableRunTest {

    @Nested
    @DisplayName("正常场景")
    inner class NormalScenarios {

        @ParameterizedTest(name = "{0}")
        @MethodSource("kim.der.timetask.run.RunnableRunTest#businessPayloads")
        @DisplayName("执行任务时保留真实业务载荷并更新派发状态")
        fun `execute preserves business payload and updates dispatch state`(
            caseName: String,
            payload: String,
        ) {
            // Given：通知任务中包含真实系统常见的空值、长文本、特殊字符和国际化内容。
            val dispatchState = DispatchState()
            val context = contextWithAction {
                dispatchState.lastPayload = payload
                dispatchState.dispatchCount.incrementAndGet()
            }

            // When：Quartz 调用 Job 公共入口执行任务。
            RunnableRun().execute(context)

            // Then：验证业务状态变化，而不是验证内部读取 JobDataMap 的次数。
            assertAll(
                "任务执行后应完整保留载荷并只派发一次：$caseName",
                {
                    assertThat(dispatchState.lastPayload)
                        .`as`("派发内容应与 JobDataMap 中的业务载荷一致")
                        .isEqualTo(payload)
                },
                {
                    assertThat(dispatchState.dispatchCount.get())
                        .`as`("单次 execute 只能触发一次业务动作")
                        .isEqualTo(1)
                },
            )
        }

        @Test
        @DisplayName("同一 Job 多次触发时每次都产生一次可观测业务状态变化")
        fun `execute can be called repeatedly with deterministic state changes`() {
            // Given：固定间隔任务会被 Quartz 多次触发，业务侧以状态累计派发次数。
            val dispatchState = DispatchState()
            val context = contextWithAction {
                dispatchState.dispatchCount.incrementAndGet()
            }
            val runnableRun = RunnableRun()

            // When：模拟 Quartz 连续触发同一个任务三次。
            repeat(3) {
                runnableRun.execute(context)
            }

            // Then：重复执行是可预测的，每次触发正好对应一次业务状态变化。
            assertThat(dispatchState.dispatchCount.get())
                .`as`("重复触发任务时，派发次数应等于 Quartz 调用 execute 的次数")
                .isEqualTo(3)
        }
    }

    @Nested
    @DisplayName("异常场景")
    inner class ErrorScenarios {

        @Test
        @DisplayName("缺少业务动作时返回可诊断的 JobExecutionException")
        fun `execute fails with diagnostic exception when action is missing`() {
            // Given：外部调度上下文缺少 run 回调，模拟任务数据不一致。
            val context = contextWith(JobDataMap())

            // When：执行任务。
            val exception = assertThrows<JobExecutionException>(
                "缺少业务动作时，应通过 JobExecutionException 暴露可诊断失败原因",
            ) {
                RunnableRun().execute(context)
            }

            // Then：异常消息应指出缺失的数据键，方便定位 Quartz JobDataMap 注入问题。
            assertAll(
                "缺少 run 回调时的失败信息",
                {
                    assertThat(exception.message)
                        .`as`("异常消息应明确指出缺失的 JobDataMap key")
                        .isEqualTo("No action found in JobDataMap with key '${RunnableRun.RUN_DATA_KEY}'")
                },
                {
                    assertThat(exception.cause)
                        .`as`("缺少动作是数据不一致，不应伪造底层 cause")
                        .isNull()
                },
            )
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("kim.der.timetask.run.RunnableRunTest#invalidActionValues")
        @DisplayName("业务动作类型错误时包装为可诊断异常")
        fun `execute fails with diagnostic exception when action has invalid type`(
            caseName: String,
            invalidAction: Any,
        ) {
            // Given：JobDataMap 中 run 键存在，但值不是函数，模拟持久化或集成层写入脏数据。
            val dataMap = JobDataMap()
            dataMap[RunnableRun.RUN_DATA_KEY] = invalidAction
            val context = contextWith(dataMap)

            // When：执行任务。
            val exception = assertThrows<JobExecutionException>(
                "run 值不是函数时，应包装为 Quartz 可识别的 JobExecutionException：$caseName",
            ) {
                RunnableRun().execute(context)
            }

            // Then：异常类型和 cause 同时说明根因，便于线上日志诊断。
            assertAll(
                "run 值类型错误时的失败信息：$caseName",
                {
                    assertThat(exception.message)
                        .`as`("异常消息应说明 run 不是可执行函数")
                        .isEqualTo("Action in JobDataMap is not a valid function")
                },
                {
                    assertThat(exception.cause)
                        .`as`("cause 应保留 Kotlin 函数强转失败根因")
                        .isInstanceOf(ClassCastException::class.java)
                },
            )
        }

        @Test
        @DisplayName("业务动作抛错时保留根因并禁止 Quartz 立即重试")
        fun `execute wraps business failure and does not request immediate refire`() {
            // Given：通知下游依赖超时，业务动作已经开始执行但最终失败。
            val attempts = AtomicInteger(0)
            val rootCause = IllegalStateException("通知网关超时: cn-north-1")
            val context = contextWithAction {
                attempts.incrementAndGet()
                throw rootCause
            }

            // When：执行任务。
            val exception = assertThrows<JobExecutionException>(
                "业务动作失败时，应包装为 JobExecutionException 并保留根因",
            ) {
                RunnableRun().execute(context)
            }

            // Then：验证业务尝试状态、异常链和 Quartz 重试语义。
            assertAll(
                "业务动作失败后的可诊断状态",
                {
                    assertThat(attempts.get())
                        .`as`("失败任务也应记录已经尝试执行一次")
                        .isEqualTo(1)
                },
                {
                    assertThat(exception.message)
                        .`as`("异常消息应包含业务根因摘要")
                        .isEqualTo("Task execution failed: ${rootCause.message}")
                },
                {
                    assertThat(exception.cause)
                        .`as`("异常 cause 应保留原始业务异常，方便排查根因")
                        .isSameAs(rootCause)
                },
                {
                    assertThat(exception.refireImmediately())
                        .`as`("当前适配器不应要求 Quartz 对失败任务立即重试")
                        .isFalse()
                },
            )
        }

        @Test
        @DisplayName("Quartz 上下文读取失败时不吞掉外部依赖异常")
        fun `execute propagates scheduler context failure`() {
            // Given：Quartz 上下文自身不可用，尚未读取到任何业务动作。
            val context = mock(JobExecutionContext::class.java)
            val schedulerFailure = IllegalStateException("Quartz 上下文不可用")
            `when`(context.mergedJobDataMap).thenThrow(schedulerFailure)

            // When：执行任务。
            val exception = assertThrows<IllegalStateException>(
                "外部调度上下文失败时，不应被误包装成业务动作失败",
            ) {
                RunnableRun().execute(context)
            }

            // Then：保持原异常向上传递，调用方可以区分调度器问题和业务动作问题。
            assertThat(exception)
                .`as`("调度上下文读取失败应保留原始异常实例")
                .isSameAs(schedulerFailure)
        }
    }

    @Nested
    @DisplayName("并发场景")
    inner class ConcurrentScenarios {

        @Test
        @DisplayName("多个 Quartz 线程并发执行时不丢失业务状态更新")
        fun `execute supports concurrent scheduler invocations`() {
            // Given：线程池中的多个 Quartz worker 同时触发同一个 Job 适配器。
            val workerCount = 6
            val startTogether = CyclicBarrier(workerCount)
            val finished = CountDownLatch(workerCount)
            val failures = ConcurrentLinkedQueue<Throwable>()
            val dispatchState = DispatchState()
            val context = contextWithAction {
                startTogether.await(2, TimeUnit.SECONDS)
                dispatchState.dispatchCount.incrementAndGet()
            }

            // When：并发执行同一个公共 API。
            val workers = List(workerCount) {
                Thread {
                    try {
                        RunnableRun().execute(context)
                    } catch (throwable: Throwable) {
                        failures.add(throwable)
                    } finally {
                        finished.countDown()
                    }
                }
            }
            workers.forEach(Thread::start)

            // Then：所有线程都完成，且每次触发都能留下独立的业务状态变化。
            assertThat(finished.await(5, TimeUnit.SECONDS))
                .`as`("并发任务应在超时时间内全部完成，避免 Quartz worker 被阻塞")
                .isTrue()
            workers.forEach { it.join(1_000) }
            assertAll(
                "并发执行结果",
                {
                    assertThat(failures)
                        .`as`("并发执行不应产生异常：${failures.joinToString { it.message.orEmpty() }}")
                        .isEmpty()
                },
                {
                    assertThat(dispatchState.dispatchCount.get())
                        .`as`("并发触发次数应完整反映到业务派发状态")
                        .isEqualTo(workerCount)
                },
            )
        }
    }

    private data class DispatchState(
        var lastPayload: String? = null,
        val dispatchCount: AtomicInteger = AtomicInteger(0),
    )

    private companion object {
        @JvmStatic
        fun businessPayloads(): Stream<Arguments> =
            Stream.of(
                Arguments.of("空字符串内容", ""),
                Arguments.of("超长消息内容", "A".repeat(4_096)),
                Arguments.of("特殊字符内容", "line1\nline2\t'\"\\/:?&=%"),
                Arguments.of("国际化内容", "中文-日本語-Русский-العربية"),
            )

        @JvmStatic
        fun invalidActionValues(): Stream<Arguments> =
            Stream.of(
                Arguments.of("空字符串", ""),
                Arguments.of("数字", 404),
                Arguments.of("集合", listOf("send", "audit")),
            )
    }

    private fun contextWithAction(action: () -> Unit): JobExecutionContext {
        val dataMap = JobDataMap()
        dataMap[RunnableRun.RUN_DATA_KEY] = action
        return contextWith(dataMap)
    }

    private fun contextWith(dataMap: JobDataMap): JobExecutionContext {
        val context = mock(JobExecutionContext::class.java)
        `when`(context.mergedJobDataMap).thenReturn(dataMap)
        return context
    }
}
