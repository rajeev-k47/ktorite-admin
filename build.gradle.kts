
plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    id("java-library")
    id("maven-publish")
}

group = "org.ktorite"
version = "1.0.0"

repositories { mavenLocal(); mavenCentral(); maven("https://jitpack.io") }

kotlin { jvmToolchain(17) }

publishing {
    publications {
        create<MavenPublication>("admin") {
            from(components["java"])
            groupId = "com.github.ktorite"
            artifactId = "ktorite-admin"
            version = "1.0.0"
        }
    }
}

dependencies {
    api("com.github.ktorite:ktorite-core:1.0.0")
    api("io.ktor:ktor-server-core:3.5.0")
    api("io.ktor:ktor-server-thymeleaf:3.5.0")
    api("io.ktor:ktor-server-sessions:3.5.0")
    api("io.ktor:ktor-server-auth:3.5.0")
    api("io.ktor:ktor-server-host-common:3.5.0")
    api("org.jetbrains.exposed:exposed-core:1.3.0")
    api("org.jetbrains.exposed:exposed-jdbc:1.3.0")
    api("ch.qos.logback:logback-classic:1.5.34")
}
