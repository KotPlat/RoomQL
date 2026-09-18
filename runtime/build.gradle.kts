plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.dokka)
    alias(libs.plugins.dokka.javadoc)
    `maven-publish`
    signing
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
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
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
            artifactId = "runtime"
            from(components["java"])
            artifact(dokkaJavadocJar)

            pom {
                name.set("RoomQL Runtime")
                description.set("Type-safe Kotlin DSL for building dynamic Android Room queries at runtime: the query { } builder, typed Column references, and required/IfNotNull condition pairs for filters that can disappear. Pure JVM, unit-testable without an emulator.")
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
