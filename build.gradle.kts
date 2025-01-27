import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.10"
    kotlin("plugin.serialization") version "1.4.10"
}

group = "net.perfectdreams.loritta.helper"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://jcenter.bintray.com")
    maven("https://jitpack.io")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("ch.qos.logback:logback-classic:1.3.0-alpha5")
    implementation("io.github.microutils:kotlin-logging:1.8.3")

    implementation("net.dv8tion:JDA:4.2.0_207")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9")
    implementation("com.github.ben-manes.caffeine:caffeine:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0-RC2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-hocon:1.0.0-RC2")
    implementation("com.github.pemistahl:lingua:v1.1.0-wip-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.4.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.4.2")

    implementation("io.ktor:ktor-client-cio:1.4.0")
}

tasks {
    val fatJar = task("fatJar", type = Jar::class) {
        println("Building fat jar for ${project.name}...")

        archiveBaseName.set("${project.name}-fat")

        manifest {
            fun addIfAvailable(name: String, attrName: String) {
                attributes[attrName] = System.getProperty(name) ?: "Unknown"
            }

            addIfAvailable("build.number", "Build-Number")
            addIfAvailable("commit.hash", "Commit-Hash")
            addIfAvailable("git.branch", "Git-Branch")
            addIfAvailable("compiled.at", "Compiled-At")

            attributes["Main-Class"] = "net.perfectdreams.loritta.helper.LorittaHelperLauncher"
            attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(" ", transform = { "libs/" + it.name })
        }

        val libs = File(rootProject.projectDir, "libs")
        // libs.deleteRecursively()
        libs.mkdirs()

        from(configurations.runtimeClasspath.get().mapNotNull {
            val output = File(libs, it.name)

            if (!output.exists())
                it.copyTo(output, true)

            null
        })

        with(jar.get() as CopySpec)
    }

    "build" {
        dependsOn(fatJar)
    }
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        showStandardStreams = true
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.javaParameters = true
}