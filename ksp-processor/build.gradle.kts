plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.binary.compatibility.validator)
    `maven-publish`
}

group = "com.github.ahmednobii.RoomQL"
version = System.getenv("VERSION") ?: "unspecified"

kotlin {
    jvmToolchain(17)
    explicitApi()
}

java {
    withSourcesJar()
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit5)
    testImplementation(libs.kotlin.compile.testing.ksp)
    testImplementation(libs.room.common)
    testImplementation(project(":runtime"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "roomql-ksp-processor"
            from(components["java"])
        }
    }
}
