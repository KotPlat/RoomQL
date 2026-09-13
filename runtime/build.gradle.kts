plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.binary.compatibility.validator)
    `maven-publish`
}

group = "com.github.ahmednobii.RoomQL"
version = System.getenv("VERSION") ?: "unspecified"

// JitPack sets VERSION to the tag it is building; CI sets it explicitly. Without it the
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

            pom {
                name.set("RoomQL Runtime")
                description.set("Type-safe Kotlin DSL for building dynamic Android Room queries at runtime: the query { } builder, typed Column references, and null-skipping conditions. Pure JVM, unit-testable without an emulator.")
                url.set("https://github.com/ahmednobii/RoomQL")
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
                    url.set("https://github.com/ahmednobii/RoomQL")
                    connection.set("scm:git:https://github.com/ahmednobii/RoomQL.git")
                    developerConnection.set("scm:git:ssh://git@github.com/ahmednobii/RoomQL.git")
                }
                issueManagement {
                    system.set("GitHub Issues")
                    url.set("https://github.com/ahmednobii/RoomQL/issues")
                }
            }
        }
    }
}
