import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.provider.Property

val localProperties = Properties().apply {
    load(rootProject.file("local.properties").inputStream())
}

val keystorePropertiesFile = File(System.getProperty("user.home"), ".wakebridge-private/keystore.properties")
val keystoreProperties = Properties()
val releaseSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val releaseSigningError = if (!keystorePropertiesFile.isFile) {
    "Release signing requires ${keystorePropertiesFile.path} with storeFile, storePassword, keyAlias, and keyPassword."
} else {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
    val missingKeys = releaseSigningKeys.filter { keystoreProperties.getProperty(it).isNullOrBlank() }
    if (missingKeys.isEmpty()) {
        null
    } else {
        "Release signing file ${keystorePropertiesFile.path} is missing: ${missingKeys.joinToString(", ")}."
    }
}
val hasReleaseKeystore = releaseSigningError == null

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.shallange.wakebridge_android"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.shallange.wakebridge_android"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "MQTT_HOST",
            "\"${localProperties.getProperty("MQTT_HOST")}\""
        )

        buildConfigField(
            "String",
            "MQTT_USERNAME",
            "\"${localProperties.getProperty("MQTT_USERNAME")}\""
        )

        buildConfigField(
            "String",
            "MQTT_PASSWORD",
            "\"${localProperties.getProperty("MQTT_PASSWORD")}\""
        )
    }

    if (hasReleaseKeystore) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    implementation(libs.hivemq.mqtt.client)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

abstract class ReleaseSigningGuard : DefaultTask() {
    @get:Input
    abstract val problem: Property<String>

    @TaskAction
    fun guard() {
        val message = problem.get()
        if (message.isNotEmpty()) {
            throw GradleException(message)
        }
    }
}

val checkReleaseSigning = tasks.register<ReleaseSigningGuard>("checkReleaseSigning") {
    problem.set(releaseSigningError ?: "")
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        dependsOn(checkReleaseSigning)
    }
}