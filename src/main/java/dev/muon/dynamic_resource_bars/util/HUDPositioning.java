package dev.muon.dynamic_resource_bars.util;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import dev.muon.dynamic_resource_bars.config.ModConfigManager;
import dev.muon.dynamic_resource_bars.util.AnchorPoint;

public class HUDPositioning {
    // AnchorPoint is now a top-level enum in util.AnchorPoint

    public enum AnchorSide {
        LEFT,
        RIGHT
    }

    @Setter
    @Getter
    private static int vanillaHealthHeight = 9; // Default fallback

    @Setter
    @Getter
    private static int vanillaHungerHeight = 9; // Default fallback

    public static Position getHealthAnchor() {
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        AnchorPoint anchor = ModConfigManager.getClient().healthBarAnchor;
        switch (anchor) {
            case TOP_LEFT:
                return new Position(0, 0);
            case TOP_CENTER:
                return new Position(screenWidth / 2, 0);
            case TOP_RIGHT:
                return new Position(screenWidth, 0);
            case CENTER_LEFT:
                return new Position(0, screenHeight / 2);
            case CENTER:
                return new Position(screenWidth / 2, screenHeight / 2);
            case CENTER_RIGHT:
                return new Position(screenWidth, screenHeight / 2);
            case BOTTOM_LEFT:
                return new Position(0, screenHeight);
            case BOTTOM_CENTER:
                return new Position(screenWidth / 2, screenHeight);
            case BOTTOM_RIGHT:
                return new Position(screenWidth, screenHeight);
            default:
                return new Position(screenWidth / 2, screenHeight - 40); // fallback
        }
    }

    public static Position getArmorAnchor() {
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int healthHeight = ModConfigManager.getClient().enableHealthBar ?
            ModConfigManager.getClient().healthBackgroundHeight :
                getVanillaHealthHeight();
        return new Position(
                (screenWidth / 2) - 91,
                getHealthAnchor().y() - healthHeight - 1
        );
    }

    public static Position getHungerAnchor() {
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return new Position(
                (screenWidth / 2) + 91,
                screenHeight - 40
        );
    }

    public static Position getAirAnchor() {
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int staminaHeight = ModConfigManager.getClient().enableStaminaBar ?
            ModConfigManager.getClient().staminaBackgroundHeight :
            getVanillaHungerHeight();
        return new Position(
                (screenWidth / 2) + 91,
                getHungerAnchor().y() - staminaHeight - 1
        );
    }

    public static Position getAboveUtilitiesAnchor() {
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return new Position(
                screenWidth / 2,
                screenHeight - 65   // TODO: reimplement shift
        );
    }

    /**
     * Returns the top-left position for a bounding box so that the specified anchor of the box aligns with the given anchor point on the screen.
     * For example, if anchor is TOP_LEFT, aligns box's top-left to (0,0). If BOTTOM_RIGHT, aligns box's bottom-right to (screenWidth, screenHeight).
     */
    public static Position alignBoundingBoxToAnchor(ScreenRect box, AnchorPoint anchor) {
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int x = 0, y = 0;
        switch (anchor) {
            case TOP_LEFT:
                x = 0;
                y = 0;
                break;
            case TOP_CENTER:
                x = (screenWidth - box.width()) / 2;
                y = 0;
                break;
            case TOP_RIGHT:
                x = screenWidth - box.width();
                y = 0;
                break;
            case CENTER_LEFT:
                x = 0;
                y = (screenHeight - box.height()) / 2;
                break;
            case CENTER:
                x = (screenWidth - box.width()) / 2;
                y = (screenHeight - box.height()) / 2;
                break;
            case CENTER_RIGHT:
                x = screenWidth - box.width();
                y = (screenHeight - box.height()) / 2;
                break;
            case BOTTOM_LEFT:
                x = 0;
                y = screenHeight - box.height();
                break;
            case BOTTOM_CENTER:
                x = (screenWidth - box.width()) / 2;
                y = screenHeight - box.height();
                break;
            case BOTTOM_RIGHT:
                x = screenWidth - box.width();
                y = screenHeight - box.height();
                break;
        }
        return new Position(x, y);
    }
}
