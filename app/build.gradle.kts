plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.example.facerecognizer"
    compileSdk = 34

    buildFeatures{
        viewBinding = true
    }

    defaultConfig {
        applicationId = "com.example.facerecognizer"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // On Apple silicon, you can omit x86_64.
            abiFilters += listOf("arm64-v8a")
//            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
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
    sourceSets {
        getByName("main") {
            assets {
                srcDirs("src/main/assets")
            }
        }
    }
    buildToolsVersion = "29.0.2"
    ndkVersion = "26.1.10909125"
    dependenciesInfo {
        includeInApk = true
        includeInBundle = true
    }
}
chaquopy {
    defaultConfig {
        version = "3.8"
        pip {
//            install("--upgrade pip setuptools wheel")
            install("numpy")
//            install("cv2")
            install("opencv-python")
            install("deepface==0.0.83")
//            install("sklearn")
            install("scikit-learn")
        }
    }
    sourceSets {
        getByName("main") {
            srcDir("src/main/python")
        }
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    //custom dependencies
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")
    //blaze-face
    implementation("com.google.mediapipe:tasks-vision:0.20230731")
    implementation("androidx.activity:activity-ktx:1.8.1")

    //tensorFlowLite
    implementation("org.tensorflow:tensorflow-lite-task-vision-play-services:0.4.2")
    implementation("org.tensorflow:tensorflow-lite:2.12.0")
    implementation("com.google.android.gms:play-services-tflite-gpu:16.2.0")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

//    implementation("org.chaquo.python:tensorflow:1.9.0")
}