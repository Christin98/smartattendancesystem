import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties
import java.util.Locale
import java.util.TimeZone
import java.io.FileInputStream
import java.io.FileOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt) // added for Room compiler
}

// Load version properties
val versionPropsFile = file("version.properties")
val versionProps = Properties()

if (versionPropsFile.exists()) {
    versionProps.load(FileInputStream(versionPropsFile))
} else {
    // Create default properties if file doesn't exist
    versionProps["VERSION_CODE"] = "1"
    versionProps["VERSION_NAME"] = "1.0.0"
    versionProps["VERSION_BUILD"] = "0"
    versionProps.store(FileOutputStream(versionPropsFile), null)
}

// Function to auto-increment version
fun autoIncrementVersion() {
    val currentCode = versionProps["VERSION_CODE"].toString().toInt()
    val currentBuild = versionProps["VERSION_BUILD"].toString().toInt()
    val versionName = versionProps["VERSION_NAME"].toString()

    // Increment build number
    val newBuild = currentBuild + 1
    versionProps["VERSION_BUILD"] = newBuild.toString()

    // Always increment version code on every build
    val newCode = currentCode + 1
    versionProps["VERSION_CODE"] = newCode.toString()

    // Every 10 builds, increment version name patch number
    if (newBuild % 10 == 0) {
        // Auto increment version name patch number
        val versionParts = versionName.split(".")
        if (versionParts.size == 3) {
            val major = versionParts[0]
            val minor = versionParts[1]
            val patch = versionParts[2].toIntOrNull() ?: 0
            versionProps["VERSION_NAME"] = "$major.$minor.${patch + 1}"
        }
    }

    // Save updated properties with custom date format
    val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH)
    dateFormat.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    val buildTime = "#${dateFormat.format(Date())}"

    // Write properties file with custom header
    FileOutputStream(versionPropsFile).use { output ->
        versionProps.store(output, buildTime)
    }
}

// Task to increment version
tasks.register("incrementVersion") {
    doLast {
        autoIncrementVersion()
        println("Version incremented:")
        println("  Version Code: ${versionProps["VERSION_CODE"]}")
        println("  Version Name: ${versionProps["VERSION_NAME"]}")
        println("  Build Number: ${versionProps["VERSION_BUILD"]}")
    }
}

// Automatically increment on each build for assembleDebug and assembleRelease
afterEvaluate {
    tasks.named("assembleDebug") {
        dependsOn("incrementVersion")
    }
    tasks.named("assembleRelease") {
        dependsOn("incrementVersion")
    }
}

android {
    namespace = "com.mandelbulb.smartattendancesystem"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mandelbulb.smartattendancesystem"
        minSdk = 26
        targetSdk = 36
        versionCode = versionProps["VERSION_CODE"].toString().toInt()
        versionName = "${versionProps["VERSION_NAME"]}.${versionProps["VERSION_BUILD"]}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Add build timestamp and build number
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        buildConfigField("String", "BUILD_TIME", "\"${dateFormat.format(Date())}\"")
        buildConfigField("String", "BUILD_NUMBER", "\"${versionProps["VERSION_BUILD"]}\"")
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug-b${versionProps["VERSION_BUILD"]}"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true    // useful if you use view binding anywhere
        buildConfig = true   // Enable BuildConfig generation
    }
}

dependencies {
    // Core + lifecycle + compose BOM (your existing)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // CameraX (use PreviewView and ImageCapture)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.lifecycle)

    // ML Kit Face Detection
    implementation(libs.mlkit.face.detection)

    // TensorFlow Lite for FaceNet
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)
    implementation(libs.tensorflow.lite.gpu)

    // Room (runtime + ktx) + kapt compiler
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // WorkManager (background sync)
    implementation(libs.androidx.work.runtime.ktx)

    // Networking: OkHttp + Retrofit + Moshi converter
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.moshi.core)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Material
    implementation(libs.material)
    
    // DataStore for preferences
    implementation(libs.androidx.datastore.preferences)

    // Location services
    implementation(libs.play.services.location)

    // Testing (leave as-is)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
