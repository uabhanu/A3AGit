plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.Able3Studios.A3A"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.Able3Studios.A3A"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes{
        release{
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt") , "proguard-rules.pro")
        }
    }

    afterEvaluate {
        // For generating AAB (Debug)
        tasks.named("bundleDebug") {
            doLast {
                val debugOutputDir = file("$buildDir/outputs/bundle/debug")
                val aabFile = debugOutputDir.listFiles()?.firstOrNull { it.name.endsWith(".aab") }
                aabFile?.let {
                    it.renameTo(File(debugOutputDir, "A3A-debug.aab"))
                }
            }
        }

        // For generating AAB (Release)
        tasks.named("bundleRelease") {
            doLast {
                val releaseOutputDir = file("$buildDir/outputs/bundle/release")
                val aabFile = releaseOutputDir.listFiles()?.firstOrNull { it.name.endsWith(".aab") }
                aabFile?.let {
                    it.renameTo(File(releaseOutputDir, "A3A.aab"))
                }
            }
        }

        tasks.named("packageDebug") {
            doLast {
                val debugOutputDir = file("$buildDir/outputs/apk/debug")
                val apkFile = debugOutputDir.listFiles()?.firstOrNull { it.name.endsWith(".apk") }
                apkFile?.renameTo(File(debugOutputDir , "A3A Debug.apk"))
            }
        }

        tasks.named("packageRelease") {
            doLast{
                val releaseOutputDir = file("$buildDir/outputs/apk/release")
                val apkFile = releaseOutputDir.listFiles()?.firstOrNull { it.name.endsWith(".apk") }
                apkFile?.renameTo(File(releaseOutputDir , "A3A.apk"))
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.compose.material:material-icons-extended:1.0.5")
    implementation("androidx.camera:camera-camera2:1.1.0")
    implementation("androidx.camera:camera-core:1.1.0")
    implementation("androidx.camera:camera-lifecycle:1.1.0")
    implementation("androidx.camera:camera-view:1.0.0-alpha30")
    implementation("com.google.mlkit:barcode-scanning:17.0.2")
    implementation("com.google.code.gson:gson:2.8.8")
    implementation("commons-codec:commons-codec:1.15")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(platform(libs.androidx.compose.bom))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}