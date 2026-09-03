plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            api(libs.koin.core)
        }
        
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            api(libs.koin.android)
            implementation(libs.androidx.core.ktx)
        }
        
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.cio)
        }
    }
}

android {
    namespace = "store.josepe.dev.sharedlogic"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val generateBuildConfig = tasks.register("generateBuildConfig") {
    val version = libs.versions.app.version.get()
    val outputDir = layout.buildDirectory.dir("generated/source/buildConfig/commonMain/kotlin").get().asFile
    inputs.property("version", version)
    outputs.dir(outputDir)
    doLast {
        val file = File(outputDir, "store/josepe/dev/BuildConfig.kt")
        file.parentFile.mkdirs()
        file.writeText(
            """
            package store.josepe.dev

            internal object SharedBuildConfig {
                const val VERSION_NAME: String = "$version"
            }
            """.trimIndent()
        )
    }
}

kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(generateBuildConfig.map { layout.buildDirectory.dir("generated/source/buildConfig/commonMain/kotlin") })

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(generateBuildConfig)
}

