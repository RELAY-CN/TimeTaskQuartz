/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.extensions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.stream.Stream

/**
 * [CronExpressions]、[CronBuilder] 与 Cron 辅助函数的业务契约测试。
 *
 * 测试以用户配置定时任务的真实场景组织：预置模板、构建器边界、非法输入诊断、
 * 以及表达式解析失败后的安全降级。
 */
@DisplayName("Cron 表达式扩展")
class CronExtensionsTest {

    @Nested
    @DisplayName("正常场景")
    inner class NormalScenarios {

        @ParameterizedTest(name = "{0}")
        @MethodSource("kim.der.timetask.extensions.CronExtensionsTest#predefinedCronExpressions")
        @DisplayName("内置 Cron 模板应全部可被 Quartz 接受")
        fun `predefined cron expressions are valid quartz expressions`(
            caseName: String,
            cron: String,
        ) {
            // Given：业务侧直接使用库提供的常用 Cron 模板，不再手写表达式。
            // When：将模板交给公共校验 API。
            val valid = isValidCron(cron)

            // Then：每个公开模板都必须是 Quartz 可接受的合法表达式。
            assertThat(valid)
                .`as`("内置 Cron 模板应可直接用于 Quartz 调度：%s -> %s", caseName, cron)
                .isTrue()
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("kim.der.timetask.extensions.CronExtensionsTest#intervalCronCases")
        @DisplayName("按固定间隔构建 Cron 表达式")
        fun `build interval cron expression for fixed cadence task`(
            caseName: String,
            actualCron: String,
            expectedCron: String,
        ) {
            // Given：业务希望用类型安全 API 创建固定间隔任务。
            // When：通过 CronBuilder 构建表达式。
            // Then：表达式内容和 Quartz 合法性同时满足，避免生成不可调度配置。
            assertAll(
                "固定间隔 Cron 构建结果：$caseName",
                {
                    assertThat(actualCron)
                        .`as`("构建出的 Cron 字符串应符合 Quartz 语义")
                        .isEqualTo(expectedCron)
                },
                {
                    assertThat(isValidCron(actualCron))
                        .`as`("构建出的 Cron 应能通过 Quartz 校验")
                        .isTrue()
                },
            )
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("kim.der.timetask.extensions.CronExtensionsTest#calendarCronCases")
        @DisplayName("按日历时间构建 Cron 表达式")
        fun `build calendar cron expression for business schedule`(
            caseName: String,
            actualCron: String,
            expectedCron: String,
        ) {
            // Given：业务在日、周、月、年等自然日历维度配置任务。
            // When：通过公开构建器生成 Cron。
            // Then：表达式应准确表达业务调度点，并保持 Quartz 可解析。
            assertAll(
                "日历 Cron 构建结果：$caseName",
                {
                    assertThat(actualCron)
                        .`as`("Cron 字符串应表达预期日历时间")
                        .isEqualTo(expectedCron)
                },
                {
                    assertThat(isValidCron(actualCron))
                        .`as`("日历 Cron 应能通过 Quartz 校验")
                        .isTrue()
                },
            )
        }

        @ParameterizedTest
        @EnumSource(CronBuilder.DayOfWeek::class)
        @DisplayName("星期枚举应映射为 Quartz 星期缩写")
        fun `weekly enum values map to quartz day names`(dayOfWeek: CronBuilder.DayOfWeek) {
            // Given：业务使用枚举避免手写星期缩写。
            val cron = CronBuilder.weeklyAt(dayOfWeek, 9, 30, 15)

            // When：生成每周执行的 Cron 表达式。
            val valid = isValidCron(cron)

            // Then：生成结果应包含枚举对应的星期值，且 Quartz 能够解析。
            assertAll(
                "星期枚举 Cron 映射：$dayOfWeek",
                {
                    assertThat(cron)
                        .`as`("Cron 应以 Quartz 星期缩写结尾")
                        .endsWith(" ${dayOfWeek.name.take(3)}")
                },
                {
                    assertThat(valid)
                        .`as`("枚举星期生成的 Cron 应有效")
                        .isTrue()
                },
            )
        }
    }

    @Nested
    @DisplayName("边界场景")
    inner class BoundaryScenarios {

        @ParameterizedTest(name = "{0}")
        @MethodSource("kim.der.timetask.extensions.CronExtensionsTest#boundaryCronCases")
        @DisplayName("构建器接受 Quartz 允许的最小和最大时间边界")
        fun `builder accepts minimum and maximum supported time boundaries`(
            caseName: String,
            cron: String,
        ) {
            // Given：业务配置落在 Quartz 支持范围的边界值上。
            // When：通过公共 API 构建 Cron。
            // Then：边界值不应被误判为非法配置。
            assertThat(isValidCron(cron))
                .`as`("Quartz 支持的边界 Cron 应合法：%s -> %s", caseName, cron)
                .isTrue()
        }

        @ParameterizedTest(name = "数字星期 {0} 应映射为 {1}")
        @MethodSource("kim.der.timetask.extensions.CronExtensionsTest#numericDayOfWeekCases")
        @DisplayName("数字星期边界与 Quartz 语义保持一致")
        fun `numeric day of week boundary follows quartz semantics`(
            dayOfWeek: Int,
            expectedDayName: String,
        ) {
            // Given：业务通过数字配置星期，1 代表周日、7 代表周六。
            val cron = CronBuilder.weeklyAt(dayOfWeek, 10)

            // When：读取生成的 Cron。
            // Then：边界数字应准确映射到 Quartz 星期缩写。
            assertThat(cron)
                .`as`("数字星期边界应与 Quartz 的 SUN..SAT 语义一致")
                .isEqualTo("0 0 10 ? * $expectedDayName")
        }
    }

    @Nested
    @DisplayName("异常场景")
    inner class ErrorScenarios {

        @ParameterizedTest(name = "{0}")
        @MethodSource("kim.der.timetask.extensions.CronExtensionsTest#invalidBuilderInputs")
        @DisplayName("构建器拒绝超出 Quartz 支持范围的输入")
        fun `builder rejects invalid values with diagnostic message`(
            caseName: String,
            action: () -> String,
            expectedMessagePart: String,
        ) {
            // Given：业务配置超出 Quartz 支持范围。
            // When：通过构建器创建表达式。
            val exception = assertThrows<IllegalArgumentException>("非法配置应在构建阶段失败：$caseName") {
                action()
            }

            // Then：失败消息应指出具体字段和非法值，便于定位配置来源。
            assertThat(exception.message)
                .`as`("异常消息应包含可诊断片段 '%s'，实际为：%s", expectedMessagePart, exception.message)
                .contains(expectedMessagePart)
        }

        @ParameterizedTest(name = "非法表达式：{0}")
        @ValueSource(strings = ["invalid", "", "0 0 25 * * ?", "0 60 12 * * ?", "0 0 12 32 * ?"])
        @DisplayName("非法 Cron 表达式校验失败且不产生下次触发时间")
        fun `invalid cron expression is rejected and has no next fire time`(cron: String) {
            // Given：外部配置中心传入非法 Cron 文本。
            // When：校验表达式并计算下一次触发时间。
            val valid = isValidCron(cron)
            val nextFireTime = getNextFireTime(cron)

            // Then：公共 API 应安全返回失败状态，而不是向调用方抛出解析异常。
            assertAll(
                "非法 Cron 表达式安全降级：$cron",
                {
                    assertThat(valid)
                        .`as`("非法 Cron 应返回 false")
                        .isFalse()
                },
                {
                    assertThat(nextFireTime)
                        .`as`("非法 Cron 不应返回下一次触发时间")
                        .isNull()
                },
            )
        }

        @Test
        @DisplayName("无法解析描述时返回原始表达式便于回显配置")
        fun `describe cron returns original text when expression cannot be parsed`() {
            // Given：管理后台保存了无法被 Quartz 解析的 Cron 文本。
            val rawCron = "invalid cron with spaces"

            // When：尝试获取可读描述。
            val description = describeCron(rawCron)

            // Then：描述 API 应回显原始值，方便用户修正配置。
            assertThat(description)
                .`as`("无法解析时应返回原始 Cron 文本")
                .isEqualTo(rawCron)
        }
    }

    @Nested
    @DisplayName("时间相关场景")
    inner class TimeScenarios {

        @Test
        @DisplayName("有效 Cron 的下一次触发时间应在当前时间之后")
        fun `valid cron expression returns future next fire time`() {
            // Given：每秒执行一次的心跳任务。
            val before = System.currentTimeMillis()

            // When：计算下一次触发时间。
            val nextFireTime = getNextFireTime(CronExpressions.EVERY_SECOND)

            // Then：下一次触发时间必须是未来时间，且不应远离当前调度窗口。
            assertAll(
                "下一次触发时间",
                {
                    assertThat(nextFireTime)
                        .`as`("有效 Cron 应返回下一次触发时间")
                        .isNotNull()
                },
                {
                    assertThat(nextFireTime!!)
                        .`as`("下一次触发时间应不早于查询开始时间")
                        .isGreaterThanOrEqualTo(before)
                },
                {
                    assertThat(nextFireTime!!)
                        .`as`("每秒任务的下一次触发时间应落在合理窗口内")
                        .isLessThanOrEqualTo(before + 2_000)
                },
            )
        }

        @Test
        @DisplayName("有效 Cron 描述应包含 Quartz 摘要信息")
        fun `valid cron expression returns quartz summary`() {
            // Given：每天中午执行的报表任务。
            val cron = CronExpressions.DAILY_NOON

            // When：获取 Quartz 生成的表达式摘要。
            val description = describeCron(cron)

            // Then：描述应保留可读摘要，供管理页面展示。
            assertAll(
                "Cron 描述摘要",
                {
                    assertThat(description)
                        .`as`("有效 Cron 描述不应为空")
                        .isNotBlank()
                },
                {
                    assertThat(description)
                        .`as`("Quartz 摘要应包含秒字段说明")
                        .contains("seconds")
                },
                {
                    assertThat(description)
                        .`as`("Quartz 摘要应包含小时字段说明")
                        .contains("hours")
                },
            )
        }
    }

    private companion object {
        @JvmStatic
        fun predefinedCronExpressions(): Stream<Arguments> =
            Stream.of(
                Arguments.of("每秒", CronExpressions.EVERY_SECOND),
                Arguments.of("每 5 秒", CronExpressions.EVERY_5_SECONDS),
                Arguments.of("每 10 秒", CronExpressions.EVERY_10_SECONDS),
                Arguments.of("每 15 秒", CronExpressions.EVERY_15_SECONDS),
                Arguments.of("每 30 秒", CronExpressions.EVERY_30_SECONDS),
                Arguments.of("每分钟", CronExpressions.EVERY_MINUTE),
                Arguments.of("每 5 分钟", CronExpressions.EVERY_5_MINUTES),
                Arguments.of("每 10 分钟", CronExpressions.EVERY_10_MINUTES),
                Arguments.of("每 15 分钟", CronExpressions.EVERY_15_MINUTES),
                Arguments.of("每 30 分钟", CronExpressions.EVERY_30_MINUTES),
                Arguments.of("每小时", CronExpressions.EVERY_HOUR),
                Arguments.of("每 2 小时", CronExpressions.EVERY_2_HOURS),
                Arguments.of("每 4 小时", CronExpressions.EVERY_4_HOURS),
                Arguments.of("每 6 小时", CronExpressions.EVERY_6_HOURS),
                Arguments.of("每 12 小时", CronExpressions.EVERY_12_HOURS),
                Arguments.of("每天凌晨", CronExpressions.DAILY_MIDNIGHT),
                Arguments.of("每天早上 6 点", CronExpressions.DAILY_6AM),
                Arguments.of("每天早上 8 点", CronExpressions.DAILY_8AM),
                Arguments.of("每天早上 9 点", CronExpressions.DAILY_9AM),
                Arguments.of("每天中午", CronExpressions.DAILY_NOON),
                Arguments.of("每天晚上 6 点", CronExpressions.DAILY_6PM),
                Arguments.of("每天晚上 9 点", CronExpressions.DAILY_9PM),
                Arguments.of("每天晚上 11 点", CronExpressions.DAILY_11PM),
                Arguments.of("每周一", CronExpressions.WEEKLY_MONDAY),
                Arguments.of("每周二", CronExpressions.WEEKLY_TUESDAY),
                Arguments.of("每周三", CronExpressions.WEEKLY_WEDNESDAY),
                Arguments.of("每周四", CronExpressions.WEEKLY_THURSDAY),
                Arguments.of("每周五", CronExpressions.WEEKLY_FRIDAY),
                Arguments.of("每周六", CronExpressions.WEEKLY_SATURDAY),
                Arguments.of("每周日", CronExpressions.WEEKLY_SUNDAY),
                Arguments.of("工作日早上 9 点", CronExpressions.WEEKDAYS_9AM),
                Arguments.of("周末早上 10 点", CronExpressions.WEEKENDS_10AM),
                Arguments.of("每月 1 号", CronExpressions.MONTHLY_FIRST_DAY),
                Arguments.of("每月 15 号", CronExpressions.MONTHLY_15TH),
                Arguments.of("每月最后一天", CronExpressions.MONTHLY_LAST_DAY),
                Arguments.of("每年 1 月 1 日", CronExpressions.YEARLY_JANUARY_FIRST),
                Arguments.of("每年 7 月 1 日", CronExpressions.YEARLY_JULY_FIRST),
                Arguments.of("每年 12 月 31 日", CronExpressions.YEARLY_DECEMBER_31ST),
            )

        @JvmStatic
        fun intervalCronCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of("每 5 秒", CronBuilder.everySeconds(5), "0/5 * * * * ?"),
                Arguments.of("每 30 分钟", CronBuilder.everyMinutes(30), "0 */30 * * * ?"),
                Arguments.of("每 12 小时", CronBuilder.everyHours(12), "0 0 */12 * * ?"),
            )

        @JvmStatic
        fun calendarCronCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of("每天 08:00:00", CronBuilder.dailyAt(8), "0 0 8 * * ?"),
                Arguments.of("每天 14:30:15", CronBuilder.dailyAt(14, 30, 15), "15 30 14 * * ?"),
                Arguments.of("每周五 10:30:00", CronBuilder.weeklyAt(CronBuilder.DayOfWeek.FRIDAY, 10, 30), "0 30 10 ? * FRI"),
                Arguments.of("每月 15 日 09:30:00", CronBuilder.monthlyAt(15, 9, 30), "0 30 9 15 * ?"),
                Arguments.of("每月最后一天 23:30:00", CronBuilder.monthlyLastDay(23, 30), "0 30 23 L * ?"),
                Arguments.of("每年 12 月 25 日 12:00:00", CronBuilder.yearlyAt(12, 25, 12), "0 0 12 25 12 ?"),
                Arguments.of("工作日 17:00:00", CronBuilder.weekdaysAt(17), "0 0 17 ? * MON-FRI"),
                Arguments.of("周末 09:30:00", CronBuilder.weekendsAt(9, 30), "0 30 9 ? * SAT,SUN"),
            )

        @JvmStatic
        fun boundaryCronCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of("秒最小值 1", CronBuilder.everySeconds(1)),
                Arguments.of("秒最大值 59", CronBuilder.everySeconds(59)),
                Arguments.of("分钟最小值 1", CronBuilder.everyMinutes(1)),
                Arguments.of("分钟最大值 59", CronBuilder.everyMinutes(59)),
                Arguments.of("小时最小值 1", CronBuilder.everyHours(1)),
                Arguments.of("小时最大值 23", CronBuilder.everyHours(23)),
                Arguments.of("一天开始 00:00:00", CronBuilder.dailyAt(0, 0, 0)),
                Arguments.of("一天结束 23:59:59", CronBuilder.dailyAt(23, 59, 59)),
                Arguments.of("月份最小值 1", CronBuilder.yearlyAt(1, 1, 0)),
                Arguments.of("月份最大值 12", CronBuilder.yearlyAt(12, 31, 23, 59, 59)),
            )

        @JvmStatic
        fun numericDayOfWeekCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of(1, "SUN"),
                Arguments.of(2, "MON"),
                Arguments.of(7, "SAT"),
            )

        @JvmStatic
        fun invalidBuilderInputs(): Stream<Arguments> =
            Stream.of(
                Arguments.of("秒为 0", { CronBuilder.everySeconds(0) }, "Seconds must be between 1 and 59, got: 0"),
                Arguments.of("秒为 60", { CronBuilder.everySeconds(60) }, "Seconds must be between 1 and 59, got: 60"),
                Arguments.of("分钟为 0", { CronBuilder.everyMinutes(0) }, "Minutes must be between 1 and 59, got: 0"),
                Arguments.of("分钟为 60", { CronBuilder.everyMinutes(60) }, "Minutes must be between 1 and 59, got: 60"),
                Arguments.of("小时为 0", { CronBuilder.everyHours(0) }, "Hours must be between 1 and 23, got: 0"),
                Arguments.of("小时为 24", { CronBuilder.everyHours(24) }, "Hours must be between 1 and 23, got: 24"),
                Arguments.of("日期为 0", { CronBuilder.monthlyAt(0, 9) }, "Day of month must be between 1 and 31, got: 0"),
                Arguments.of("日期为 32", { CronBuilder.monthlyAt(32, 9) }, "Day of month must be between 1 and 31, got: 32"),
                Arguments.of("星期数字为 0", { CronBuilder.weeklyAt(0, 9) }, "Day of week must be between 1 (Sunday) and 7 (Saturday), got: 0"),
                Arguments.of("星期数字为 8", { CronBuilder.weeklyAt(8, 9) }, "Day of week must be between 1 (Sunday) and 7 (Saturday), got: 8"),
                Arguments.of("月份为 0", { CronBuilder.yearlyAt(0, 1, 0) }, "Month must be between 1 and 12, got: 0"),
                Arguments.of("月份为 13", { CronBuilder.yearlyAt(13, 1, 0) }, "Month must be between 1 and 12, got: 13"),
                Arguments.of("小时为 -1", { CronBuilder.dailyAt(-1) }, "Hour must be between 0 and 23, got: -1"),
                Arguments.of("分钟为 -1", { CronBuilder.dailyAt(12, -1) }, "Minute must be between 0 and 59, got: -1"),
                Arguments.of("秒为 -1", { CronBuilder.dailyAt(12, 0, -1) }, "Second must be between 0 and 59, got: -1"),
            )
    }
}
