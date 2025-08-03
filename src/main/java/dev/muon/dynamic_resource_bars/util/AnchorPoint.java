package dev.muon.dynamic_resource_bars.util;

public enum AnchorPoint {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    CENTER_LEFT,
    CENTER,
    CENTER_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT;

    public String getDisplayName() {
        switch (this) {
            case TOP_LEFT: return "Top Left";
            case TOP_CENTER: return "Top Center";
            case TOP_RIGHT: return "Top Right";
            case CENTER_LEFT: return "Center Left";
            case CENTER: return "Center";
            case CENTER_RIGHT: return "Center Right";
            case BOTTOM_LEFT: return "Bottom Left";
            case BOTTOM_CENTER: return "Bottom Center";
            case BOTTOM_RIGHT: return "Bottom Right";
        }
        return this.name();
    }
}
