import java.util.Properties
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
if(file("google-services.json").exists()) apply(plugin="com.google.gms.google-services")
val signing = Properties().apply { rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
android {
 namespace="com.tertiaryinfotech.ailmstms"
 compileSdk=36
 defaultConfig {
  applicationId="com.tertiaryinfotech.ailmstms"; minSdk=26; targetSdk=36
  versionCode=4; versionName="1.0.3"
  testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner"
  buildConfigField("String","API_BASE_URL","\"https://lms-tms.tertiaryinfotech.com\"")
 }
 signingConfigs { if(signing.isNotEmpty()) create("release") {
  storeFile=rootProject.file(signing.getProperty("storeFile")); storePassword=signing.getProperty("storePassword")
  keyAlias=signing.getProperty("keyAlias"); keyPassword=signing.getProperty("keyPassword")
 } }
 buildTypes { release { isMinifyEnabled=false; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro"); if(signing.isNotEmpty()) signingConfig=signingConfigs.getByName("release") } }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
 buildFeatures { compose=true; buildConfig=true }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-core")
 implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("androidx.security:security-crypto:1.1.0-alpha06")
 implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
 implementation("com.google.firebase:firebase-messaging")
 implementation("androidx.work:work-runtime-ktx:2.10.0")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.json:json:20240303")
 testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
 androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 androidTestImplementation("androidx.test.ext:junit:1.3.0")
 androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
 debugImplementation("androidx.compose.ui:ui-tooling")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
}
