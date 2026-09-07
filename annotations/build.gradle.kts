plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

group = "com.github.ahmednobii.room-query-beauty"
version = System.getenv("VERSION") ?: "unspecified"

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "roomql-annotations"
            from(components["java"])
        }
    }
}
