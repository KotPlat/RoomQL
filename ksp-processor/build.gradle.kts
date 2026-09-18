plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.binary.compatibility.validator)
    `maven-publish`
}

group = "io.github.kotplat.roomql"
version = System.getenv("VERSION") ?: "unspecified"

// JitPack and the Maven Central release workflow both set VERSION to the tag being
// built; local CI runs set it explicitly. Without it the
// version silently falls back to "unspecified" and publishes artifacts nothing can
// resolve — which surfaces on JitPack as an unhelpful "No build artifacts found".
// Fail at the publish step instead, where the cause is obvious.
tasks.withType<AbstractPublishToMaven>().configureEach {
    doFirst {
        check(System.getenv("VERSION") != null) {
            "VERSION is not set. Publish with: VERSION=<tag> ./gradlew $name"
        }
    }
}

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
            artifactId = "ksp-processor"
            from(components["java"])

            pom {
                name.set("RoomQL KSP Processor")
                description.set("KSP processor that reads Room @Entity classes and generates typed Column references, so dynamic Room queries built with the RoomQL DSL are checked by the Kotlin compiler.")
                url.set("https://github.com/KotPlat/RoomQL")
                inceptionYear.set("2026")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("ahmednobii")
                        name.set("Ahmed Nobi")
                        url.set("https://github.com/ahmednobii")
                    }
                }
                scm {
                    url.set("https://github.com/KotPlat/RoomQL")
                    connection.set("scm:git:https://github.com/KotPlat/RoomQL.git")
                    developerConnection.set("scm:git:ssh://git@github.com/KotPlat/RoomQL.git")
                }
                issueManagement {
                    system.set("GitHub Issues")
                    url.set("https://github.com/KotPlat/RoomQL/issues")
                }
            }
        }
    }
}
