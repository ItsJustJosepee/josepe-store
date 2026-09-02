import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "store.josepe.dev"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "store.josepe.dev"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = libs.versions.app.versionCode.get().toInt()
        versionName = libs.versions.app.version.get()
    }

    signingConfigs {
        create("release") {
            val localProperties = Properties()
            val file = rootProject.file("local.properties")
            if (file.exists()) {
                file.inputStream().use { localProperties.load(it) }
            }

            val storePath = localProperties.getProperty("keystore.file") ?: System.getenv("KEYSTORE_FILE")
            val storePwd = localProperties.getProperty("keystore.password") ?: System.getenv("KEYSTORE_PASSWORD")
            val keyAliasName = localProperties.getProperty("key.alias") ?: System.getenv("KEY_ALIAS")
            val keyPwd = localProperties.getProperty("key.password") ?: System.getenv("KEY_PASSWORD")

            if (storePath != null && storePwd != null) {
                val keystoreFile = file(storePath)
                storeFile = if (keystoreFile.exists()) keystoreFile else rootProject.file(storePath)
                storePassword = storePwd
                keyAlias = keyAliasName
                keyPassword = keyPwd
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.kotlin_module",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "DebugProbesKt.bin"
            )
        }
        jniLibs {
            useLegacyPackaging = false
            keepDebugSymbols.clear()
        }
    }
}

dependencies {
    implementation(project(":sharedUI"))
    implementation(project(":sharedLogic"))
    implementation(libs.ktor.client.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
}
