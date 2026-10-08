import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
    jacoco
}

// Release signing comes from untracked local.properties or from CI secrets (environment variables); never from the repo.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun signingValue(name: String): String? = localProperties.getProperty(name) ?: System.getenv(name)

android {
    namespace = "com.hourstracker.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hourstracker.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0-alpha1"
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("RELEASE_STORE_FILE")
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = signingValue("RELEASE_STORE_PASSWORD")
                keyAlias = signingValue("RELEASE_KEY_ALIAS")
                keyPassword = signingValue("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // `./gradlew :app:createDebugUnitTestCoverageReport` writes the HTML report under build/reports/coverage.
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            // Unsigned when no key is configured (CI, other machines); signed bundles are built on the owner's machine.
            if (signingValue("RELEASE_STORE_FILE") != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // First closed test ships Hebrew and English; Arabic and Russian follow in M2c.
    androidResources {
        localeFilters += listOf("en", "he", "iw")
    }

    // The language is switchable inside the app, so every language must ship in the base APK/bundle.
    bundle {
        language {
            enableSplit = false
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // Typography style hints only; the strings come from the iOS catalog and are not rewritten here.
        // targetSdk stays at 36: AGP 8.13 supports at most compileSdk 36, and the newest platform is not installed everywhere.
        disable += listOf("OldTargetApi", "TypographyDashes", "TypographyQuotes", "TypographyEllipsis", "TypographyFractions", "TypographyOther")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-data"))

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    // Test-only (dev): Compose UI tests on the JVM with Robolectric. Robolectric and Compose's test rule are JUnit 4,
    // so the Vintage engine runs them next to the JUnit 5 tests.
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    debugImplementation(libs.compose.ui.test.manifest)

    // Test-only (dev): screenshot comparison of the key screens.
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}

roborazzi {
    // Golden images are committed here; `recordRoborazziDebug` rewrites them, `verifyRoborazziDebug` compares.
    outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // `-PplayScreenshots` also renders the Play Store pictures (see PlayScreenshots); ordinary runs skip them.
    if (providers.gradleProperty("playScreenshots").isPresent) systemProperty("playScreenshots", "true")
    // Robolectric loads classes through its own loader; without these the coverage report comes out empty.
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}
