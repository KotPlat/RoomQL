plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.roomql.sample"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":annotations"))
    implementation(project(":runtime"))

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.coroutines.core)

    // RoomQl KSP processor generates the *Columns objects; Room's KSP generates DAO_Impl.
    ksp(project(":ksp-processor"))
    ksp(libs.room.compiler)

    // Robolectric drives a real in-memory Room DB on the JVM. It mandates the JUnit4
    // runner (@RunWith(RobolectricTestRunner)), so this module uses JUnit4 rather than
    // the JUnit5 the pure-JVM modules (:runtime, :ksp-processor) run on.
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
