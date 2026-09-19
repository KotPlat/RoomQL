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
        // Checked before mavenCentral() so a local `publishToMavenLocal` build overrides
        // an already-published version of the same number.
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "roomql-demo"
