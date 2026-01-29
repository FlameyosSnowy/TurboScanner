plugins {
    id("java")
    application
}

group = "me.flame.turboscanner"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
