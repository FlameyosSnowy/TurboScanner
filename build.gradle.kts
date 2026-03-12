plugins {
    `java-library`
    signing
    id("application")
    id("me.champeau.jmh") version "0.7.2"
    id("com.vanniktech.maven.publish") version "0.33.0"
}

group = "me.flame"
version = "1.4.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    //withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")

    compileOnly("org.jetbrains:annotations:26.0.2")

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
    applicationDefaultJvmArgs = listOf("--add-modules=jdk.incubator.vector")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--add-modules", "jdk.incubator.vector"))
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

configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
    coordinates(group as String, "turboscanner", version as String)

    pom {
        name.set("TurboScanner")
        description.set("High-performance and lightweight SIMD library for byte scanning and classification")
        inceptionYear.set("2026")
        url.set("https://github.com/FlameyosSnowy/TurboScanner")
        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("https://mit-license.org/")
            }
        }
        developers {
            developer {
                id.set("flameyosflow")
                name.set("FlameyosFlow")
                url.set("https://github.com/FlameyosSnowy/")
            }
        }
        scm {
            url.set("https://github.com/FlameyosSnowy/TurboScanner")
            connection.set("scm:git:git://github.com/FlameyosSnowy/TurboScanner.git")
            developerConnection.set("scm:git:ssh://git@github.com/FlameyosSnowy/TurboScanner.git")
        }
    }

    publishToMavenCentral()
    signAllPublications()
}


signing {
    useGpgCmd()
}

tasks.withType<Test>().configureEach {
    jvmArgs("--add-modules", "jdk.incubator.vector")
}

application {
    applicationDefaultJvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}

afterEvaluate {
    tasks.named<com.vanniktech.maven.publish.tasks.JavadocJar>("plainJavadocJar") {
        dependsOn(tasks.named("javadoc"))
        archiveClassifier.set("javadoc")
        from(tasks.named<Javadoc>("javadoc"))
    }

    // Ensure metadata generation depends on Javadoc
    tasks.named("generateMetadataFileForMavenPublication") {
        dependsOn(tasks.named("plainJavadocJar"))
    }

    // Make publish depend on the Javadoc artifact
    tasks.named("publish") {
        dependsOn(tasks.named("plainJavadocJar"))
    }
}