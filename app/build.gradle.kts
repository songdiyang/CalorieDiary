import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("app.cash.paparazzi")
}

// 云端服务配置：local.properties > gradle 属性 > 环境变量。
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun buildTimeConfig(key: String, envName: String): String =
    (localProps.getProperty(key)
        ?: (project.findProperty(key) as String?)
        ?: System.getenv(envName)
        ?: "")
        .trim()
        .replace("\\", "")
        .replace("\"", "")

val releaseStoreFile = localProps.getProperty("signing.storeFile")?.trim().orEmpty()
val releaseStorePassword = localProps.getProperty("signing.storePassword")?.trim().orEmpty()
val releaseStoreType = localProps.getProperty("signing.storeType")?.trim().orEmpty()
val releaseKeyAlias = localProps.getProperty("signing.keyAlias")?.trim().orEmpty()
val releaseKeyPassword = localProps.getProperty("signing.keyPassword")?.trim().orEmpty()

val visionProxyUrl = buildTimeConfig("vision.proxyUrl", "VISION_PROXY_URL")
val visionAppKey = buildTimeConfig("vision.appKey", "VISION_APP_KEY")
val supabaseUrl = buildTimeConfig("supabase.url", "SUPABASE_URL").trimEnd('/')
val supabaseAnonKey = buildTimeConfig("supabase.anonKey", "SUPABASE_ANON_KEY")

android {
    namespace = "com.calorie.diary"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.calorie.diary"
        minSdk = 26
        targetSdk = 34
        versionCode = 37
        versionName = "0.25"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 云端识别代理地址与应用标识；未配置时 ProxyVisionClient 直接报服务不可用
        buildConfigField("String", "VISION_PROXY_URL", "\"$visionProxyUrl\"")
        buildConfigField("String", "VISION_APP_KEY", "\"$visionAppKey\"")
        // Supabase Publishable key 可公开；真正的数据隔离由用户令牌和 RLS 负责。
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (releaseStoreFile.isNotEmpty()) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                if (releaseStoreType.isNotEmpty()) {
                    storeType = releaseStoreType
                }
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
