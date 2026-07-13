/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

package kim.der.timetask.contract

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * 隔离 Maven 仓库发布物的外部消费者编译契约测试。
 */
@DisplayName("发布 API 契约")
class PublishedApiContractTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    @DisplayName("消费者仅声明本库也能编译公开的 Quartz 类型 API")
    fun `consumer compiles public quartz api from published metadata`() {
        // Given：一个只依赖已发布 TimeTaskQuartz 的全新 Java 消费者。
        val repository = requireNotNull(System.getProperty("publishedApiRepository")) {
            "publishedApiRepository must point to the isolated Maven repository"
        }
        val version = requireNotNull(System.getProperty("publishedApiVersion")) {
            "publishedApiVersion must identify the isolated publication"
        }
        tempDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"api-consumer\"\n")
        tempDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                `java-library`
            }

            repositories {
                maven { url = uri("${Path.of(repository).toUri()}") }
                mavenCentral()
            }

            dependencies {
                implementation("kim.der:TimeTaskQuartz:$version")
            }

            """.trimIndent(),
        )
        val sourceDir = tempDir.resolve("src/main/java").createDirectories()
        sourceDir.resolve("Consumer.java").writeText(
            """
            import kim.der.timetask.task.TimeTaskManage;
            import org.quartz.JobKey;

            final class Consumer {
                static boolean trigger(TimeTaskManage manager, JobKey key) {
                    return manager.triggerNow(key);
                }
            }
            """.trimIndent(),
        )

        // When：按外部项目的真实依赖元数据编译。
        val result =
            GradleRunner
                .create()
                .withProjectDir(tempDir.toFile())
                .withArguments("compileJava")
                .build()

        // Then：Quartz 应作为 API 依赖进入消费者的编译 classpath。
        assertThat(result.output)
            .`as`("公开 JobKey API 的外部消费者应编译成功")
            .contains("BUILD SUCCESSFUL")
    }
}
