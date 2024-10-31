plugins {
    id("com.android.application")
}


android {
    namespace = "com.audiofetch.afsdksample"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.audiofetch.afsdksample"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        create("profile") {
            initWith(getByName("debug"))
        }

    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

configurations {
    getByName("profileImplementation") {
    }
}


dependencies {

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // af_disco integration
    debugImplementation("com.audiofetch.af_disco_module:flutter_debug:1.0")
    releaseImplementation("com.audiofetch.af_disco_module:flutter_release:1.0")
    add("profileImplementation", "com.audiofetch.af_disco_module:flutter_profile:1.0")

    // af_audio integration
    implementation("io.reactivex.rxjava2:rxjava:2.2.0")
    implementation("io.reactivex.rxjava2:rxandroid:2.0.1")
    implementation("com.jakewharton.rxrelay2:rxrelay:2.0.0")
    implementation("com.google.guava:guava:33.3.1-android")
    implementation(files("libs/afaudiolib.aar"))
}

