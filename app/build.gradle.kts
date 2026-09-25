plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "org.camillian.community"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.camillian.community"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        val supabaseUrl = providers.environmentVariable("SUPABASE_URL").orNull ?: ""
        val supabaseKey = providers.environmentVariable("SUPABASE_PUBLISHABLE_KEY").orNull ?: ""

        buildConfigField("String", "SUPABASE_URL", ""$supabaseUrl"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", ""$supabaseKey"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("io.github.jan-tennert.supabase:auth-kt:3.5.0")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:3.5.0")
    implementation("io.ktor:ktor-client-android:3.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
