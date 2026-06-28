plugins {
    kotlin("jvm") version "2.3.10"
}

group = "org.nnezh"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation("io.arrow-kt:arrow-core:2.2.2.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
    workingDir = rootProject.projectDir
}
