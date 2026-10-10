import java.util.Properties
import java.util.Base64
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
val backend = Properties().apply {
    val f = rootProject.file("backend.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val publicKey = backend.getProperty("supabasePublishableKey", "")
val safeClientKey = publicKey.isEmpty() || publicKey.startsWith("sb_publishable_") || runCatching {
    val payload = String(Base64.getUrlDecoder().decode(publicKey.split('.')[1]))
    Regex(""""role"\s*:\s*"anon"""").containsMatchIn(payload)
}.getOrDefault(false)
require(safeClientKey) { "Use somente uma chave publishable ou anon; segredos administrativos são proibidos no APK." }
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val signing = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
android {
    namespace = "com.ritmo.treinos"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.ritmo.treinos"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.1.0"
        buildConfigField("String", "SUPABASE_URL", quoted(backend.getProperty("supabaseUrl", "")))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", quoted(backend.getProperty("supabasePublishableKey", "")))
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GITHUB_REPOSITORY", "\"MARCELO887876653/ritmo-android\"")
    }
    signingConfigs {
        if (signing.isNotEmpty()) create("release") {
            storeFile = rootProject.file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (signing.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets.getByName("androidTest").assets.directories.add("schemas")
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    val bom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(bom); androidTestImplementation(bom); testImplementation(bom)
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.room:room-testing:2.8.5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Local optional Robolectric SDK cache; CI can download normally.
tasks.withType<Test>().configureEach {
    providers.gradleProperty("ritmo.robolectricCache").orNull?.let {
        systemProperty("robolectric.offline", "true")
        systemProperty("robolectric.dependency.dir", it)
    }
}
