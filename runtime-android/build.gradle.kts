plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.dokka)
    alias(libs.plugins.dokka.javadoc)
    `maven-publish`
    signing
}

group = "io.github.kotplat.roomql"
version = System.getenv("VERSION") ?: "unspecified"

// Without VERSION set, publishing silently falls back to "unspecified" — fail loudly instead.
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

// Maven Central requires a Javadoc jar alongside every artifact. Dokka's javadoc format
// renders KDoc as real Javadoc-style HTML rather than shipping an empty placeholder jar.
val dokkaJavadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.named("dokkaGeneratePublicationJavadoc"))
    from(layout.buildDirectory.dir("dokka/javadoc"))
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "runtime-android"
            afterEvaluate {
                from(components["release"])
            }
            artifact(dokkaJavadocJar)

            pom {
                name.set("RoomQL Runtime for Android")
                description.set("Android bridge for RoomQL: adapts a RoomQlQuery into the SupportSQLiteQuery that Room @RawQuery methods accept.")
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

// Maven Central rejects unsigned artifacts. Signing only activates when the in-memory
// key is present — set by the release workflow, absent for local publishToMavenLocal —
// so local development and CI's build/test/publishToMavenLocal checks stay unaffected.
val signingKey = System.getenv("ORG_GPG_KEY")
val signingPassphrase = System.getenv("ORG_GPG_PASSPHRASE")
if (signingKey != null && signingPassphrase != null) {
    signing {
        useInMemoryPgpKeys(signingKey, signingPassphrase)
        sign(publishing.publications["maven"])
    }
}
