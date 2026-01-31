import me.champeau.jmh.JMHTask

plugins {
    id("java")
    application
    id("me.champeau.jmh") version "0.7.2"
}

group = "me.flame.turboscanner"
version = "1.2.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    compileOnly("org.jetbrains:annotations:26.0.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Test>().configureEach {
    jvmArgs("--add-modules=jdk.incubator.vector")
}


application {
    applicationDefaultJvmArgs = mutableListOf(
        "--add-modules=jdk.incubator.vector"
    )
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(
        arrayOf(
            "--add-modules", "jdk.incubator.vector"
        )
    )
}

jmh {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    )

    jvmArgs.add("--add-modules=jdk.incubator.vector")

    warmupIterations.set(5)
    iterations.set(10)
    fork.set(2)

    timeUnit.set("ns")
    benchmarkMode.set(listOf("thrpt", "avgt"))

    resultFormat.set("JSON")
}