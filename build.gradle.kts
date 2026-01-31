plugins {
    id("java")
    application
}

group = "me.flame.turboscanner"
version = "1.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
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
