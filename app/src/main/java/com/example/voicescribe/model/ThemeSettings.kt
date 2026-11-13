package com.example.voicescribe.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode(val displayName: String) {
    SYSTEM("Follow System"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

@Serializable
enum class AppThemePalette(val displayName: String, val primaryHex: Long) {
    DYNAMIC("Dynamic Material You", 0xFF6750A4),
    OCEAN_BLUE("Ocean Breeze", 0xFF0288D1),
    EMERALD_FOREST("Emerald Forest", 0xFF2E7D32),
    SUNSET_GLOW("Sunset Amber", 0xFFE65100),
    MIDNIGHT_NEBULA("Midnight Nebula", 0xFF5E35B1),
    ROSE_BLOOM("Rose Berry", 0xFFC2185B),
    SLATE_MONO("Slate Minimal", 0xFF37474F)
}
