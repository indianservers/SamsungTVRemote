plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.indianservers.samsungsmarttvremote"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.indianservers.samsungsmarttvremote"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "3.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val signingPath=providers.environmentVariable("SMARTREMOTE_KEYSTORE").orNull
    val signingPassword=providers.environmentVariable("SMARTREMOTE_STORE_PASSWORD").orNull
    val signingAlias=providers.environmentVariable("SMARTREMOTE_KEY_ALIAS").orNull
    val signingKeyPassword=providers.environmentVariable("SMARTREMOTE_KEY_PASSWORD").orNull
    val signingValues=listOf(signingPath,signingPassword,signingAlias,signingKeyPassword)
    require(signingValues.all { it==null } || signingValues.all { !it.isNullOrBlank() }) { "Set all four SMARTREMOTE signing environment variables, or none. Values are never logged." }
    if(signingPath!=null) {
        signingConfigs.create("controlledRelease") {
            storeFile=file(signingPath)
            storePassword=signingPassword
            keyAlias=signingAlias
            keyPassword=signingKeyPassword
        }
    }
    buildTypes {
        release {
            if(signingPath!=null) signingConfig=signingConfigs.getByName("controlledRelease")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

