package dev.muon.dynamic_resource_bars.util;

public enum SubElementType {
    BACKGROUND,
    BAR_MAIN,       // The main resource bar (health, mana, stamina)
    FOREGROUND_DETAIL, // The detailed overlay often called "detail_overlay.png"
    TEXT,           // The text display (e.g., "100 / 100")
    ICON,           // Icons like armor tier or air level
    ABSORPTION_TEXT, // Absorption amount text (e.g., "+8")
    TRAILING_ICON        // Icon anchored to the end of the main bar progress
} 