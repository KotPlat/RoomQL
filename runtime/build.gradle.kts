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
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "roomql-runtime"
            from(components["java"])
        }
    }
}
