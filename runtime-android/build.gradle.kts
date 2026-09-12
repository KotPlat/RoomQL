plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.binary.compatibility.validator)
    `maven-publish`
}

group = "com.github.ahmednobii.RoomQL"
version = System.getenv("VERSION") ?: "unspecified"

android {
    namespace = "com.roomql.android"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
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
        }
    }
}
