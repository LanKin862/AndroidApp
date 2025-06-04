plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.myapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.myapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.1"

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
    }
    buildFeatures {
        compose = true // 启用 Compose
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
    }
}

dependencies {
    implementation ("androidx.compose.ui:ui:1.8.2")  // 或更新版本
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // DataStore
    implementation(libs.androidx.datastore.preferences)
    
    // Media
    implementation(libs.androidx.media)

    implementation (libs.androidx.material)
    implementation (libs.androidx.material.icons.core)
    implementation (libs.androidx.material.icons.extended)
    implementation (libs.ui) // 基础 UI
    implementation (libs.androidx.runtime) // 包含 @Composable
    implementation (libs.androidx.activity.compose.v172) // 支持 Activity 中使用 Compose

    // OkHttp for API calls
    implementation(libs.okhttp)
    
    // Markdown
    implementation("com.github.jeziellago:compose-markdown:0.3.6")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

}