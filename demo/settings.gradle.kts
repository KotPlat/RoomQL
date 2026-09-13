pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // RoomQL is consumed exactly as a real user consumes it: as published artifacts,
        // not as project dependencies. mavenLocal() comes first so the demo can be built
        // against an unreleased library via `./gradlew publishToMavenLocal` from the root
        // — which is also what jitpack.yml runs when building a tag.
        mavenLocal()
        maven("https://jitpack.io")
    }
}

rootProject.name = "roomql-demo"
