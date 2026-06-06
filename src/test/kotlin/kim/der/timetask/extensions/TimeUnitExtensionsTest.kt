/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.extensions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

/**
 * 时间单位扩展的业务契约测试。
 *
 * 测试以定时任务配置中的真实时间表达为核心，覆盖零值、负值、长周期、
 * 组合耗时和当前时间窗口，避免只为了行覆盖而重复简单断言。
 */
@DisplayName("时间单位扩展")
class TimeUnitExtensionsTest {

    @Nested
    @DisplayName("正常场景")
    inner class NormalScenarios {

        @ParameterizedTest(name = "{0}")
        @MethodSource("kim.der.timetask.extensions.TimeUnitExtensionsTest#intDurationCases")
        @DisplayName("Int 时间单位转换为毫秒")
        fun `int duration extensions convert to millis for scheduler configuration`(
            caseName: String,
            actualMillis: Long,
            expectedMillis: Long,
        ) {
            // Given：业务使用 Int DSL 配置短周期任务。
            // When：读取扩展属性得到 Quartz 使用的毫秒值。
            // Then：毫秒值应与 TimeUnit 标准换算一致。
            assertThat(actualMillis)
                .`as`("Int 时间单位转换应符合业务预期：%s", caseName)
                .isEqualTo(expectedMillis)
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("kim.der.timetask.extensions.TimeUnitExtensionsTest#longDurationCases")
        @DisplayName("Long 时间单位转换为毫秒")
        fun `long duration extensions convert to millis for long running schedule`(
            caseName: String,
            actualMillis: Long,
            expectedMillis: Long,
        ) {
            // Given：业务使用 Long DSL 配置更长周期任务。
            // When：读取扩展属性得到 Quartz 使用的毫秒值。
            // Then：毫秒值应与 TimeUnit 标准换算一致。
            assertThat(actualMillis)
                .`as`("Long 时间单位转换应符合业务预期：%s", caseName)
                .isEqualTo(expectedMillis)
        }

        @Test
        @DisplayName("多个时间单位可组合成完整业务超时时间")
        fun `time units can be combined into business timeout`() {
            // Given：业务将 1 小时 30 分 45 秒组合为任务超时时间。
            val expectedMillis = TimeUnit.HOURS.toMillis(1) +
                TimeUnit.MINUTES.toMillis(30) +
                TimeUnit.SECONDS.toMillis(45)

            // When：使用 DSL 扩展组合时间。
            val actualMillis = 1.hours + 30.minutes + 45.seconds

            // Then：组合值应准确表达完整超时时间。
            assertThat(actualMillis)
                .`as`("组合时间单位应准确转换为毫秒")
                .isEqualTo(expectedMillis)
        }
    }

    @Nested
    @DisplayName("边界场景")
    inner class BoundaryScenarios {

        @ParameterizedTest(name = "{0}")
        @MethodSource("kim.der.timetask.extensions.TimeUnitExtensionsTest#zeroAndNegativeCases")
        @DisplayName("零值和负值保持 TimeUnit 原生语义")
        fun `zero and negative durations keep time unit semantics`(
            caseName: String,
            actualMillis: Long,
            expectedMillis: Long,
        ) {
            // Given：业务配置可能传入零延迟或负数，底层扩展只做单位表达，不做业务校验。
            // When：转换为毫秒。
            // Then：结果应保持 TimeUnit 原生语义，非法业务值交由调度 API 校验。
            assertThat(actualMillis)
                .`as`("零值和负值转换应保持原生语义：%s", caseName)
                .isEqualTo(expectedMillis)
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("kim.der.timetask.extensions.TimeUnitExtensionsTest#readableTimeCases")
        @DisplayName("毫秒值格式化为用户可读时间")
        fun `duration formats into readable text for diagnostics`(
            caseName: String,
            millis: Long,
            expectedText: String,
        ) {
            // Given：日志或诊断页面需要展示用户可读的时间。
            // When：将毫秒值格式化。
            val actualText = millis.toReadableTime()

            // Then：输出应省略无意义的零值字段，并保留最关键单位。
            assertThat(actualText)
                .`as`("可读时间格式应匹配业务诊断文本：%s", caseName)
                .isEqualTo(expectedText)
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("kim.der.timetask.extensions.TimeUnitExtensionsTest#millisToUnitCases")
        @DisplayName("毫秒值可反向换算为粗粒度单位")
        fun `millis converts back to coarse time units`(
            caseName: String,
            actualValue: Long,
            expectedValue: Long,
        ) {
            // Given：业务需要在监控输出中展示粗粒度时间。
            // When：从毫秒转换为秒、分钟、小时或天。
            // Then：转换结果应按 TimeUnit 语义向下取整。
            assertThat(actualValue)
                .`as`("毫秒反向换算应按粗粒度单位取整：%s", caseName)
                .isEqualTo(expectedValue)
        }
    }

    @Nested
    @DisplayName("时间相关场景")
    inner class TimeScenarios {

        @Test
        @DisplayName("fromNow 返回当前时间之后的目标时间戳")
        fun `from now returns timestamp in expected future window`() {
            // Given：业务配置 5 秒后执行任务。
            val before = System.currentTimeMillis()

            // When：计算未来时间戳。
            val future = 5.seconds.fromNow()
            val after = System.currentTimeMillis()

            // Then：时间戳应落在调用窗口内，避免依赖某个固定当前时间实现。
            assertAll(
                "fromNow 未来时间窗口",
                {
                    assertThat(future)
                        .`as`("未来时间不应早于调用开始时间加延迟")
                        .isGreaterThanOrEqualTo(before + 5_000)
                },
                {
                    assertThat(future)
                        .`as`("未来时间不应晚于调用结束时间加延迟")
                        .isLessThanOrEqualTo(after + 5_000)
                },
            )
        }

        @Test
        @DisplayName("ago 返回当前时间之前的目标时间戳")
        fun `ago returns timestamp in expected past window`() {
            // Given：业务需要查询 5 秒前的历史窗口。
            val before = System.currentTimeMillis()

            // When：计算过去时间戳。
            val past = 5.seconds.ago()
            val after = System.currentTimeMillis()

            // Then：时间戳应落在调用窗口内，保留少量调度误差。
            assertAll(
                "ago 过去时间窗口",
                {
                    assertThat(past)
                        .`as`("过去时间不应晚于调用开始时间减延迟")
                        .isLessThanOrEqualTo(before - 5_000)
                },
                {
                    assertThat(past)
                        .`as`("过去时间不应早于调用结束时间减延迟并超过误差窗口")
                        .isGreaterThanOrEqualTo(after - 5_100)
                },
            )
        }

        @Test
        @DisplayName("零延迟 fromNow 和 ago 均落在当前调用窗口")
        fun `zero duration timestamps stay inside current invocation window`() {
            // Given：业务配置零延迟或当前时间查询。
            val before = System.currentTimeMillis()

            // When：分别计算未来和过去时间。
            val future = 0L.fromNow()
            val past = 0L.ago()
            val after = System.currentTimeMillis()

            // Then：两个结果都应在本次调用窗口附近。
            assertAll(
                "零延迟时间窗口",
                {
                    assertThat(future)
                        .`as`("零延迟 fromNow 应接近当前时间")
                        .isBetween(before, after + 100)
                },
                {
                    assertThat(past)
                        .`as`("零延迟 ago 应接近当前时间")
                        .isBetween(before - 100, after)
                },
            )
        }
    }

    private companion object {
        @JvmStatic
        fun intDurationCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of("1 秒", 1.seconds, TimeUnit.SECONDS.toMillis(1)),
                Arguments.of("5 秒", 5.seconds, TimeUnit.SECONDS.toMillis(5)),
                Arguments.of("1 分钟", 1.minutes, TimeUnit.MINUTES.toMillis(1)),
                Arguments.of("60 分钟", 60.minutes, TimeUnit.MINUTES.toMillis(60)),
                Arguments.of("2 小时", 2.hours, TimeUnit.HOURS.toMillis(2)),
                Arguments.of("24 小时", 24.hours, TimeUnit.HOURS.toMillis(24)),
                Arguments.of("7 天", 7.days, TimeUnit.DAYS.toMillis(7)),
                Arguments.of("500 毫秒", 500.millis, 500L),
            )

        @JvmStatic
        fun longDurationCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of("5L 秒", 5L.seconds, TimeUnit.SECONDS.toMillis(5)),
                Arguments.of("100L 秒", 100L.seconds, TimeUnit.SECONDS.toMillis(100)),
                Arguments.of("5L 分钟", 5L.minutes, TimeUnit.MINUTES.toMillis(5)),
                Arguments.of("48L 小时", 48L.hours, TimeUnit.HOURS.toMillis(48)),
                Arguments.of("30L 天", 30L.days, TimeUnit.DAYS.toMillis(30)),
                Arguments.of("999L 毫秒", 999L.millis, 999L),
            )

        @JvmStatic
        fun zeroAndNegativeCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of("0 秒", 0.seconds, 0L),
                Arguments.of("0L 毫秒", 0L.millis, 0L),
                Arguments.of("-1 秒", (-1).seconds, -1_000L),
                Arguments.of("-5L 分钟", (-5L).minutes, TimeUnit.MINUTES.toMillis(-5)),
            )

        @JvmStatic
        fun readableTimeCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of("负数保护为 0ms", -100L, "0ms"),
                Arguments.of("零值", 0L, "0ms"),
                Arguments.of("纯毫秒", 500L, "500ms"),
                Arguments.of("纯秒", 5_000L, "5s"),
                Arguments.of("分钟加秒", 90_000L, "1m 30s"),
                Arguments.of("小时分钟秒", 3_661_000L, "1h 1m 1s"),
                Arguments.of("天和小时", 129_600_000L, "1d 12h"),
                Arguments.of("整周", 604_800_000L, "7d"),
            )

        @JvmStatic
        fun millisToUnitCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of("500ms 转秒向下取整", 500L.toSeconds(), 0L),
                Arguments.of("60_000ms 转分钟", 60_000L.toMinutes(), 1L),
                Arguments.of("1_800_000ms 转小时向下取整", 1_800_000L.toHours(), 0L),
                Arguments.of("86_400_000ms 转天", 86_400_000L.toDays(), 1L),
            )
    }
}
