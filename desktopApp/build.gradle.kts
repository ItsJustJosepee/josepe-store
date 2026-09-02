import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":sharedUI"))
    implementation(project(":sharedLogic"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.foundation)
    implementation(compose.runtime)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.jna)
    implementation(libs.jna.platform)
}

compose.desktop {
    application {
        mainClass = "store.josepe.dev.MainKt"
        jvmArgs += listOf(
            "-Xms32m",
            "-Xmx256m",
            "-XX:+UseG1GC",
            "-XX:MinHeapFreeRatio=20",
            "-XX:MaxHeapFreeRatio=40",
            "-XX:+UseStringDeduplication"
        )

        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
        }

        nativeDistributions {
            val currentOs = org.gradle.internal.os.OperatingSystem.current()
            when {
                currentOs.isWindows -> targetFormats(TargetFormat.Msi)
                currentOs.isMacOsX -> targetFormats(TargetFormat.Dmg)
                else -> targetFormats(TargetFormat.Deb, TargetFormat.AppImage)
            }
            packageName = "Josepe Store"
            packageVersion = libs.versions.app.version.get()
            description = "Josepe Store - Catalogo y Gestor Oficial de Aplicaciones"
            copyright = "© 2026 Josepe Dev"
            vendor = "Josepe Dev"
            modules(
                "java.base",
                "java.desktop",
                "java.management",
                "java.naming",
                "java.net.http",
                "java.sql",
                "java.security.jgss",
                "java.security.sasl",
                "java.xml",
                "java.instrument",
                "java.logging",
                "jdk.crypto.ec",
                "jdk.crypto.cryptoki",
                "jdk.httpserver",
                "jdk.unsupported"
            )

            windows {
                menuGroup = "Josepe Store"
                shortcut = true
                dirChooser = true
                upgradeUuid = "3f851d2e-7214-4a2e-9d3c-619f772e51a8"
            }

            linux {
                menuGroup = "Josepe Store"
                shortcut = true
                debPackageVersion = libs.versions.app.version.get()
                debMaintainer = "Josepe Dev <soporte@josepe.dev>"
            }

            macOS {
                bundleID = "store.josepe.dev"
                dockName = "Josepe Store"
            }
        }
    }
}
