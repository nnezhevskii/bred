plugins {
    kotlin("jvm") version "2.3.10"
    application
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

group = "org.nnezh"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation(project(":lexer"))
    implementation(project(":parser"))
    implementation(project(":analyzer"))
    implementation(project(":tac"))
    implementation(project(":c-backend"))
    implementation("io.arrow-kt:arrow-core:2.2.2.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("org.nnezh.MainKt")
}

sourceSets {
    main {
        kotlin.setSrcDirs(listOf("src/main/kotlin"))
    }
    test {
        kotlin.setSrcDirs(listOf("src/test/kotlin"))
        kotlin.include("org/nnezh/root/**/*.kt")
        resources.setSrcDirs(emptyList<String>())
    }
}

tasks.test {
    useJUnitPlatform()
}
