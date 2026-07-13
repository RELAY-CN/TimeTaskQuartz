/*
 * Copyright 2020-2025 Dr (dr@der.kim) and contributors.
 */

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("java-library")
    id("maven-publish")
}

val gitCommitHash =
    runCatching {
        providers
            .exec {
                workingDir(rootDir)
                commandLine("git", "rev-parse", "--short", "HEAD")
                isIgnoreExitValue = true
            }.let { output ->
                if (output.result.get().exitValue == 0) {
                    output.standardOutput.asText.get().trim()
                } else {
                    ""
                }
            }
    }.getOrDefault("")
        .ifEmpty { "unknown" }

group = "kim.der"
version = gitCommitHash

repositories {
    maven(url = "https://mirrors.cloud.tencent.com/nexus/repository/maven-public")
    maven(url = "https://repo.huaweicloud.com/repository/maven")
    maven(url = "https://jitpack.io")
    maven(url = "https://plugins.gradle.org/m2")
    maven(url = "https://files.minecraftforge.net/maven")
    mavenCentral()
    google()
    gradlePluginPortal()
}

dependencies {
    // 公开重载暴露 JobKey 等 Quartz 类型，消费者编译时必须能传递获取 Quartz。
    api("org.quartz-scheduler:quartz:2.5.1") {
        // Stand-alone operation, does not require any persistence
        exclude("com.mchange", "c3p0")
        exclude("com.mchange", "mchange-commons-java")
        exclude("com.zaxxer", "HikariCP-java7")
    }

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation(gradleTestKit())
    testRuntimeOnly("org.slf4j:slf4j-nop:2.0.13")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

java {
    withSourcesJar()
}

tasks.withType<JavaCompile> {
    // 使用Java11做标准语法并编译
    sourceCompatibility = JvmTarget.JVM_11.target
    targetCompatibility = JvmTarget.JVM_11.target

    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    filter {
        excludeTestsMatching("kim.der.timetask.contract.PublishedApiContractTest")
    }
}

val publishedApiRepository = layout.buildDirectory.dir("test-maven-repository")
val testSourceSet = extensions.getByType(SourceSetContainer::class.java).named("test")

publishing {
    repositories {
        maven {
            name = "test"
            url = uri(publishedApiRepository)
        }
    }
}

val publishedApiContractTest = tasks.register<Test>("publishedApiContractTest") {
    description = "发布到隔离 Maven 仓库并验证外部消费者的 API 编译契约"
    group = "verification"
    useJUnitPlatform()
    filter {
        includeTestsMatching("kim.der.timetask.contract.PublishedApiContractTest")
    }
    testClassesDirs = testSourceSet.get().output.classesDirs
    classpath = testSourceSet.get().runtimeClasspath
    dependsOn(testSourceSet.get().classesTaskName)
    dependsOn("publishMavenPublicationToTestRepository")
    shouldRunAfter(tasks.test)
    systemProperty("publishedApiRepository", publishedApiRepository.get().asFile.absolutePath)
    systemProperty("publishedApiVersion", project.version.toString())
    inputs
        .dir(publishedApiRepository)
        .withPropertyName("publishedApiRepository")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.named("check") {
    dependsOn(publishedApiContractTest)
}

// 最终形态：生成到 build/generated-resources，再由 jar 注入运行时路径（源树不再被污染）
configureGradleRes()
configureGraalVmAgent()

publishing {
    publications {
        create<MavenPublication>("maven") {
            val publicationName = project.name
            groupId = "kim.der"
            artifactId = publicationName
            version = project.version.toString()

            from(project.components.getByName("java"))

            pom {
                scm {
                    url.set("https://github.com/RELAY-CN/TimeTaskQuartz")
                    connection.set("scm:https://github.com/RELAY-CN/TimeTaskQuartz.git")
                    developerConnection.set("scm:git@github.com:RELAY-CN/TimeTaskQuartz.git")
                }

                licenses {
                    license {
                        name.set("RELAY-CN LICENSE")
                        url.set("https://github.com/RELAY-CN/TimeTaskQuartz/blob/master/LICENSE")
                    }
                }

                developers {
                    developer {
                        id.set("TimeTaskQuartz")
                        name.set("Dr (RELAY-CN Technologies)")
                        email.set("dr@der.kim")
                    }
                }
            }

            pom.withXml {
                val root = asNode()
                root.appendNode("description", "TimeTask-Quartz")
                root.appendNode("name", publicationName)
                root.appendNode("url", "https://github.com/RELAY-CN/TimeTaskQuartz")
            }
        }
    }

    repositories {
        maven {
            name = "maven-releases"
            url = uri((project.findProperty("mavenCentralUrl") ?: "").toString() + "$name/")

            credentials {
                username = (project.findProperty("mavenCentralUsername") ?: "").toString()
                password = (project.findProperty("mavenCentralPassword") ?: "").toString()
            }
        }
    }
}

/**
 * 生成运行时构建元数据，并从 Jar 阶段直接注入，避免依赖 classes 的 FileList
 * 与 processResources 形成任务环。
 */
fun Project.configureGradleRes() {
    val gitCommitHash = project.version.toString()
    val generatedGradleResDir = layout.buildDirectory.dir("generated-resources/gradleRes/${project.name}")
    val compileClasspath = configurations.named("compileClasspath")
    val dependencyResources =
        providers.provider {
            val compileOnlyArtifacts = HashSet<Triple<String, String, String?>>()
            configurations.findByName("compileOnly")?.allDependencies?.forEach { dependency ->
                configurations
                    .detachedConfiguration(dependency)
                    .resolvedConfiguration
                    .firstLevelModuleDependencies
                    .flatMapTo(compileOnlyArtifacts) { module ->
                        module.allModuleArtifacts.map { artifact ->
                            artifact.dependencyKey()
                        }
                    }
            }

            val implementation = ArrayList<String>()
            val compileOnly = ArrayList<String>()
            compileClasspath
                .get()
                .resolvedConfiguration
                .resolvedArtifacts
                .sortedWith(
                    compareBy<ResolvedArtifact>(
                        { it.moduleVersion.id.group },
                        { it.moduleVersion.id.name },
                        { it.moduleVersion.id.version },
                        { it.classifier.orEmpty() },
                    ),
                ).forEach { artifact ->
                    val id = artifact.moduleVersion.id
                    val type = if (id.group == rootProject.name) "project" else artifact.type
                    val line = "$type:${id.group}:${id.name}:${id.version}:${artifact.classifier}"
                    if (artifact.dependencyKey() in compileOnlyArtifacts) {
                        compileOnly += line
                    } else {
                        implementation += line
                    }
                }

            DependencyResources(
                implementation = implementation.asDependencyText(),
                compileOnly = compileOnly.asDependencyText(),
            )
        }
    val implementationDependencies = dependencyResources.map { it.implementation }
    val compileOnlyDependencies = dependencyResources.map { it.compileOnly }

    val generateGradleRes =
        tasks.register<GenerateGradleRes>("generateGradleRes") {
            group = "build"
            description = "生成依赖清单、主输出清单和 Git 提交哈希"
            dependsOn("classes")

            this.gitCommitHash.set(gitCommitHash)
            this.implementationDependencies.set(implementationDependencies)
            this.compileOnlyDependencies.set(compileOnlyDependencies)
            this.compileClasspath.from(compileClasspath)
            kotlinMainOutputs.from(layout.buildDirectory.dir("classes/kotlin/main"))
            javaMainOutputs.from(layout.buildDirectory.dir("classes/java/main"))
            mainResourceOutputs.from(layout.buildDirectory.dir("resources/main"))
            outputDirectory.set(generatedGradleResDir)
        }

    // 迁移期从 source set 排除本地遗留副本，避免 processResources 和 sourcesJar 形成第二事实源。
    extensions.getByType(SourceSetContainer::class.java).named("main") {
        resources.exclude("gradleRes/**")
        resources.exclude("META-INF/native-image/${project.group}/${project.name}/**")
    }

    tasks.named("processResources", ProcessResources::class.java) {
        includeEmptyDirs = false
    }

    tasks.named("jar", Jar::class.java) {
        dependsOn(generateGradleRes)
        from(generatedGradleResDir) {
            into("gradleRes/${project.name}")
        }
    }

    tasks.named("sourcesJar", Jar::class.java) {
        includeEmptyDirs = false
    }
}

abstract class GenerateGradleRes : DefaultTask() {
    @get:Input
    abstract val gitCommitHash: Property<String>

    @get:Input
    abstract val implementationDependencies: Property<String>

    @get:Input
    abstract val compileOnlyDependencies: Property<String>

    @get:Classpath
    abstract val compileClasspath: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    abstract val kotlinMainOutputs: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    abstract val javaMainOutputs: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    abstract val mainResourceOutputs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        gitCommitHash.finalizeValueOnRead()
        implementationDependencies.finalizeValueOnRead()
        compileOnlyDependencies.finalizeValueOnRead()
    }

    @TaskAction
    fun generate() {
        val outputDir = outputDirectory.get().asFile
        outputDir.resolve("implementation.txt").writeTextIfChanged(implementationDependencies.get())
        outputDir.resolve("compileOnly.txt").writeTextIfChanged(compileOnlyDependencies.get())

        val mainFiles = ArrayList<String>()
        addMainOutputs(kotlinMainOutputs.singleFile, "kotlin/main", mainFiles)
        addMainOutputs(javaMainOutputs.singleFile, "java/main", mainFiles)
        addMainOutputs(mainResourceOutputs.singleFile, "main", mainFiles)
        outputDir.resolve("FileList.txt").writeTextIfChanged(mainFiles.joinToString("\n"))
        outputDir.resolve("GitCommitHash.txt").writeTextIfChanged(gitCommitHash.get())
    }

    private fun addMainOutputs(
        root: File,
        prefix: String,
        destination: MutableList<String>,
    ) {
        if (!root.isDirectory) {
            return
        }
        root
            .walkTopDown()
            .filter(File::isFile)
            .map { "$prefix/${it.relativeTo(root).path.replace('\\', '/')}" }
            .sorted()
            .forEach(destination::add)
    }

    private fun File.writeTextIfChanged(content: String) {
        parentFile?.mkdirs()
        if (!isFile || readText() != content) {
            writeText(content)
        }
    }
}

private data class DependencyResources(
    val implementation: String,
    val compileOnly: String,
)

private fun ResolvedArtifact.dependencyKey(): Triple<String, String, String?> =
    Triple(moduleVersion.id.group, moduleVersion.id.name, classifier)

private fun List<String>.asDependencyText(): String =
    if (isEmpty()) "" else joinToString(separator = "\n", postfix = "\n")

/*
 * GraalVM Native Image Support
 */
fun Project.configureGraalVmAgent() {
    val nativeImagePath = "META-INF/native-image/${project.group}/${project.name}"
    val nativeImageDir = layout.buildDirectory.dir("generated-resources/$nativeImagePath")

    // Jar 只注入明确归属本模块的 native-image 目录，不打包 generated-resources 下的其他文件。
    tasks.named("jar", Jar::class.java) {
        from(nativeImageDir) {
            into(nativeImagePath)
        }
    }

    if (providers.gradleProperty("disableGraalVmAgent").orNull == "true") {
        return
    }

    val accessFilterFile = layout.buildDirectory.file("graalvm-filters/access-filter.json")
    val generateAccessFilter =
        tasks.register<WriteGraalAccessFilter>("generateGraalVmAccessFilter") {
            group = "native-image"
            description = "生成 GraalVM Native Image Agent 访问过滤器"
            filterContent.set(
                """
                {
                    "rules": [
                        {"excludeClasses": "gradle.**"},
                        {"excludeClasses": "org.gradle.**"},
                        {"excludeClasses": "junit.**"},
                        {"excludeClasses": "org.junit.**"},
                        {"excludeClasses": "org.mockito.**"},
                        {"excludeClasses": "net.bytebuddy.**"},
                        {"excludeClasses": "com.sun.tools.attach.**"},
                        {"excludeClasses": "org.opentest4j.**"},
                        {"excludeClasses": "org.apiguardian.**"}
                    ],
                    "regexRules": [
                        {"excludeClasses": ".*Test"},
                        {"excludeClasses": ".*Test\\${'$'}.*"},
                        {"excludeClasses": ".*Mock.*"},
                        {"excludeClasses": ".*\\${'$'}MockitoMock\\${'$'}.*"}
                    ]
                }
                """.trimIndent(),
            )
            filterFile.set(accessFilterFile)
        }

    // 发布 API 契约只验证外部编译，不执行库运行时；仅标准 test 参与 Native Image 元数据采集。
    tasks.named("test", Test::class.java) {
        val agentOutputDir = layout.buildDirectory.dir("native-image-agent/$name").get().asFile
        val filterFile = accessFilterFile.get().asFile
        val generatedMetadata = nativeImageDir.get().asFile

        dependsOn(generateAccessFilter)
        inputs
            .file(accessFilterFile)
            .withPropertyName("graalVmAccessFilter")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        jvmArgs(
            "-XX:+EnableDynamicAgentLoading",
            "-Djdk.instrument.traceUsage=false",
            "-agentlib:native-image-agent=" +
                "config-output-dir=${agentOutputDir.absolutePath}," +
                "access-filter-file=${filterFile.absolutePath}",
        )
        doFirst(PrepareGraalAgentOutput(agentOutputDir))
        // 保持旧契约：仅在 Test 成功后复制，失败任务不会发布不完整元数据。
        doLast(CopyGraalAgentOutput(agentOutputDir, generatedMetadata))
    }
}

/** 将访问过滤器建模为显式输入输出，避免 Test 动作捕获 Project。 */
abstract class WriteGraalAccessFilter : DefaultTask() {
    @get:Input
    abstract val filterContent: Property<String>

    @get:OutputFile
    abstract val filterFile: RegularFileProperty

    @TaskAction
    fun writeFilter() {
        val target = filterFile.get().asFile
        target.parentFile?.mkdirs()
        val content = filterContent.get()
        if (!target.isFile || target.readText() != content) {
            target.writeText(content)
        }
    }
}

/** Test 启动前只创建 agent 输出目录，Action 不持有 Gradle 模型对象。 */
class PrepareGraalAgentOutput(
    private val outputDirectory: File,
) : Action<Task> {
    override fun execute(task: Task) {
        check(outputDirectory.mkdirs() || outputDirectory.isDirectory) {
            "无法创建 GraalVM Agent 输出目录: ${outputDirectory.absolutePath}"
        }
    }
}

/** Test 成功后复制 agent JSON，并保留 native-image.properties 的既有内容。 */
class CopyGraalAgentOutput(
    private val sourceDirectory: File,
    private val targetDirectory: File,
) : Action<Task> {
    override fun execute(task: Task) {
        check(targetDirectory.mkdirs() || targetDirectory.isDirectory) {
            "无法创建 Native Image 元数据目录: ${targetDirectory.absolutePath}"
        }
        task.logger.lifecycle("复制 GraalVM Agent 配置: ${sourceDirectory.absolutePath}")

        val propertiesFile = File(targetDirectory, "native-image.properties")
        if (!propertiesFile.exists()) {
            propertiesFile.writeText("Args = --no-fallback")
        }

        val predefinedClassesConfig = "predefined-classes-config.json"
        val stalePredefinedClassesConfig = targetDirectory.resolve(predefinedClassesConfig)
        check(!stalePredefinedClassesConfig.exists() || stalePredefinedClassesConfig.delete()) {
            "无法删除不完整的 Native Image 配置: ${stalePredefinedClassesConfig.absolutePath}"
        }
        sourceDirectory
            .listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") && it.name != predefinedClassesConfig }
            ?.sortedBy(File::getName)
            ?.forEach { sourceFile ->
                Files.copy(
                    sourceFile.toPath(),
                    targetDirectory.resolve(sourceFile.name).toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
    }
}
