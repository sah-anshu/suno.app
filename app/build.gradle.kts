import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.android.junit)
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps =
    Properties().apply {
        if (keystorePropsFile.exists()) {
            keystorePropsFile
                .inputStream()
                .use { load(it) }
        }
    }

val privacyPolicyUrl = providers.gradleProperty("sunoPrivacyPolicyUrl").orElse("")
val supportEmail = providers.gradleProperty("sunoSupportEmail").orElse("")
val localRelease =
    providers
        .gradleProperty("sunoLocalRelease")
        .orElse("false")
        .get()
        .toBooleanStrict()
// APK splits do not filter bundle ABIs; build bundles with this flag off.
val splitApks =
    providers
        .gradleProperty("sunoSplitApks")
        .orElse("false")
        .get()
        .toBooleanStrict()
val supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
val flavourNames = listOf("patient", "companion", "tv")

/** Release APKs per app: key "patient-arm64-v8a" (or "patient-universal") to its path under outputs/apk. */
val localApkNames =
    flavourNames
        .flatMap { flavour ->
            if (splitApks) {
                supportedAbis.map { "$flavour-$it" to "$flavour/release/app-$flavour-$it-release.apk" }
            } else {
                listOf("$flavour-universal" to "$flavour/release/app-$flavour-release.apk")
            }
        }.toMap()
val unsignedAudit = providers.gradleProperty("sunoUnsignedAudit").orElse("false")

// Only validated characters are interpolated into generated Java source.
require(privacyPolicyUrl.get().matches(Regex("[A-Za-z0-9:/?&=._%+#~-]*"))) { "Invalid privacy URL characters" }
require(supportEmail.get().matches(Regex("[A-Za-z0-9@._+%-]*"))) { "Invalid support email characters" }

android {
    namespace = "au.remotiq.suno"
    // The APK packager needs the same installed strip tool as the native build.
    ndkVersion = libs.versions.ndk.get()
    compileSdk =
        libs
            .versions
            .compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "au.remotiq.suno"
        minSdk =
            libs
                .versions
                .minSdk
                .get()
                .toInt()
        targetSdk =
            libs
                .versions
                .targetSdk
                .get()
                .toInt()
        // Suno 0.1.0: first Suno release (EarCast used 1-2). Raise both for every release.
        versionCode = 3
        versionName = "0.1.0"
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${privacyPolicyUrl.get()}\"")
        buildConfigField("String", "SUPPORT_EMAIL", "\"${supportEmail.get()}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // One codebase, three apps. See arch.md section 3.
    flavorDimensions += "role"
    productFlavors {
        create("patient") {
            dimension = "role"
        }
        create("companion") {
            dimension = "role"
            applicationIdSuffix = ".companion"
        }
        create("tv") {
            dimension = "role"
            applicationIdSuffix = ".tv"
        }
    }

    splits {
        abi {
            isEnable = splitApks
            reset()
            include(*supportedAbis.toTypedArray())
            isUniversalApk = false
        }
    }

    lint {
        // Hindi covers the patient, family, SOS, call and TV screens. EarCast's helper screens stay
        // English by design (arch.md section 11.3), so missing Hindi there is a warning, not an error.
        warning += "MissingTranslation"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                signingConfigs.findByName("release")
                    ?: if (localRelease) signingConfigs.getByName("debug") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":foundation"))
    implementation(project(":sound-profile"))
    implementation(project(":audio-engine"))
    implementation(project(":audio-graph"))
    implementation(project(":link-android"))
    implementation(libs.zxing.core)
    // QR scanning only in the companion app (it declares the camera permission).
    add("companionImplementation", libs.zxing.android.embedded)
    implementation(project(":local-storage"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    // Screenshot tests of the patient screens (Robolectric renders Compose on the JVM).
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}

val verifyPlayRelease =
    tasks.register("verifyPlayRelease") {
        group = "verification"
        description = "Checks signing and public privacy contact before packaging a Play bundle."
        val auditOnly = unsignedAudit.get().toBoolean()
        val apkSplitsEnabled = splitApks
        val signingValues =
            listOf("storeFile", "storePassword", "keyAlias", "keyPassword").map {
                keystoreProps.getProperty(it)
            }
        val uploadStore = keystoreProps.getProperty("storeFile")?.let { rootProject.file(it) }
        val policyUrl = privacyPolicyUrl.get()
        val contactEmail = supportEmail.get()
        doLast {
            check(!apkSplitsEnabled) {
                "Build App Bundles with -PsunoSplitApks=false; AGP 8.10 resource shrinking requires one APK output."
            }
            if (auditOnly) {
                logger.warn("UNSIGNED AUDIT ONLY: this bundle has not passed Play release configuration checks.")
            } else {
                check(signingValues.all { !it.isNullOrBlank() }) {
                    "Configure signing in gitignored keystore.properties. See docs/RELEASE.md."
                }
                check(uploadStore?.isFile == true) { "Upload keystore does not exist." }
                val uri = runCatching { URI(policyUrl) }.getOrNull()
                check(uri?.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) {
                    "Set sunoPrivacyPolicyUrl to your published HTTPS privacy policy."
                }
                check(contactEmail.matches(Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))) {
                    "Set sunoSupportEmail to a monitored public contact address."
                }
            }
        }
    }
tasks.matching { it.name.matches(Regex("bundle\\w*Release|build\\w*ReleasePreBundle")) }.configureEach {
    dependsOn(verifyPlayRelease)
}

val signatureChecks =
    localApkNames.map { (key, fileName) ->
        val suffix = key.split('-', '_').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
        val flavour = key.substringBefore('-')
        tasks.register<Exec>("verifyLocalRelease${suffix}Signature") {
            group = "verification"
            description = "Verifies the $key APK signature."
            dependsOn("assemble${flavour.replaceFirstChar(Char::uppercaseChar)}Release")
            val sdk =
                androidComponents.sdkComponents.sdkDirectory
                    .get()
                    .asFile
            val signerName = if (System.getProperty("os.name").startsWith("Windows")) "apksigner.bat" else "apksigner"
            commandLine(
                sdk.resolve("build-tools/${android.buildToolsVersion}/$signerName"),
                "verify",
                "--verbose",
                layout.buildDirectory
                    .file("outputs/apk/$fileName")
                    .get()
                    .asFile,
            )
        }
    }

val verifyLocalReleaseApk =
    tasks.register("verifyLocalReleaseApk") {
        group = "verification"
        description = "Verifies the signatures of all APKs produced for local installation."
        dependsOn(signatureChecks)
    }

val assembleLocalRelease =
    tasks.register("assembleLocalRelease") {
        group = "build"
        description = "Builds signed, optimized APKs of all three apps for installation on local devices."
        val localSigningAllowed = localRelease
        val configuredReleaseSigning = keystoreProps.isNotEmpty()
        val apks =
            localApkNames.mapValues { (_, name) ->
                layout.buildDirectory.file("outputs/apk/$name")
            }
        dependsOn(verifyLocalReleaseApk)
        doLast {
            check(configuredReleaseSigning || localSigningAllowed) {
                "Use just build-prod or pass -PsunoLocalRelease=true for local signing."
            }
            if (!configuredReleaseSigning) {
                logger.lifecycle(
                    "LOCAL TEST APK: signed with the debug key; configure keystore.properties before publishing.",
                )
            }
            apks.forEach { (abi, output) ->
                val apk = output.get().asFile
                check(apk.isFile) { "Expected signed APK was not produced: $apk" }
                logger.lifecycle("APK ($abi): $apk (${apk.length() / 1_000_000} MB)")
            }
        }
    }
