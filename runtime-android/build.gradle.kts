plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
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

android {
    namespace = "com.roomql.android"
    compileSdk = 36

    defaultConfig {
        minSdk = 21

        // Packaged into the AAR as proguard.txt and applied to every consuming app.
        // Carries no keep rules today; see the file for why, and for where a rule would
        // go if RoomQL ever needs one.
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    // RoomQlQuery is the extension receiver and SupportSQLiteQuery the return type,
    // so both are part of this module's public API.
    api(project(":runtime"))
    api(libs.sqlite)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "roomql-runtime-android"
            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("RoomQL Runtime for Android")
                description.set("Android bridge for RoomQL: adapts a RoomQlQuery into the SupportSQLiteQuery that Room @RawQuery methods accept.")
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
