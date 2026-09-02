package store.josepe.dev.ui.util

import java.io.BufferedReader
import java.io.InputStreamReader

object DesktopSystemThemeHelper {
    fun isSystemDarkTheme(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        return try {
            if (os.contains("win")) {
                val process = Runtime.getRuntime().exec(
                    arrayOf(
                        "reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "AppsUseLightTheme"
                    )
                )
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readText()
                output.contains("0x0")
            } else if (os.contains("mac")) {
                val process = Runtime.getRuntime().exec(arrayOf("defaults", "read", "-g", "AppleInterfaceStyle"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readText().trim()
                output.equals("Dark", ignoreCase = true)
            } else {
                // Linux: Check GNOME color-scheme first
                val process = Runtime.getRuntime().exec(
                    arrayOf("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")
                )
                val output = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
                if (output.contains("dark", ignoreCase = true)) {
                    true
                } else {
                    // Check gtk-theme (e.g. ZorinOrange-Dark, Yaru-dark, Adwaita-dark)
                    val gtkProcess = Runtime.getRuntime().exec(
                        arrayOf("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme")
                    )
                    val gtkOutput = BufferedReader(InputStreamReader(gtkProcess.inputStream)).readText().trim()
                    gtkOutput.contains("dark", ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getSystemAccentColorHex(): String? {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("win") -> getWindowsAccentColorHex()
            os.contains("linux") || os.contains("unix") -> getLinuxAccentColorHex()
            else -> null
        }
    }

    fun getWindowsAccentColorHex(): String? {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("win")) return null
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf(
                    "reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\DWM",
                    "/v", "AccentColor"
                )
            )
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            val regex = "0x[0-9a-fA-F]+".toRegex()
            val match = regex.find(output)?.value
            if (match != null) {
                val cleanHex = match.removePrefix("0x")
                if (cleanHex.length >= 8) {
                    val r = cleanHex.substring(6, 8)
                    val g = cleanHex.substring(4, 6)
                    val b = cleanHex.substring(2, 4)
                    "#$r$g$b"
                } else {
                    null
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun getLinuxAccentColorHex(): String? {
        return try {
            // 1. Try GNOME 47+ accent-color setting
            val accentProc = Runtime.getRuntime().exec(
                arrayOf("gsettings", "get", "org.gnome.desktop.interface", "accent-color")
            )
            val accentVal = BufferedReader(InputStreamReader(accentProc.inputStream)).readText().trim().removeSurrounding("'")
            if (accentVal.isNotBlank() && !accentVal.startsWith("No such schema") && !accentVal.startsWith("No such key")) {
                accentToHex(accentVal)?.let { return it }
            }

            // 2. Try GTK Theme name (Zorin OS, Ubuntu Yaru, Pop OS, Mint, etc.)
            val gtkProc = Runtime.getRuntime().exec(
                arrayOf("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme")
            )
            val gtkVal = BufferedReader(InputStreamReader(gtkProc.inputStream)).readText().trim().removeSurrounding("'")
            if (gtkVal.isNotBlank()) {
                val lower = gtkVal.lowercase()
                when {
                    lower.contains("orange") -> return "#F86E2F"
                    lower.contains("blue") -> return "#1B6EF3"
                    lower.contains("green") -> return "#26A269"
                    lower.contains("purple") -> return "#9141AC"
                    lower.contains("red") -> return "#E01B24"
                    lower.contains("teal") -> return "#009688"
                    lower.contains("grey") || lower.contains("gray") -> return "#77767B"
                    lower.contains("yellow") -> return "#F6D32D"
                    lower.contains("pink") -> return "#D56199"
                    lower.contains("indigo") -> return "#3584E4"
                    lower.contains("bark") -> return "#787859"
                    lower.contains("sage") -> return "#657B69"
                    lower.contains("olive") -> return "#6F784E"
                    lower.contains("viridian") -> return "#26A269"
                    lower.contains("prussiangreen") -> return "#007A78"
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun accentToHex(name: String): String? {
        return when (name.lowercase()) {
            "blue" -> "#3584E4"
            "teal" -> "#2190A4"
            "green" -> "#3A944A"
            "yellow" -> "#C88800"
            "orange" -> "#ED5B00"
            "red" -> "#E62D42"
            "pink" -> "#D56199"
            "purple" -> "#9141AC"
            "slate" -> "#6F8396"
            else -> null
        }
    }
}
