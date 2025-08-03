package dev.muon.dynamic_resource_bars.util;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.muon.dynamic_resource_bars.config.ModConfigManager; // ADDED THIS IMPORT
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth; // ADDED THIS IMPORT

public class RenderUtil {
    public static final int BASE_TEXT_ALPHA = 200;
    public static final long TEXT_DISPLAY_DURATION = 1000; // milliseconds
    public static final long BAR_FADEOUT_DURATION = 500; // milliseconds

    public static final long DAMAGE_INDICATOR_HOLD_MS = 500L;
    public static final long DAMAGE_INDICATOR_ANIM_DURATION_MS = 200L;

    /**
     * Calculates the alpha value for text fading out.
     * @param timeSinceFull Time in milliseconds since the bar became full.
     * @return Alpha value from 255 down to 0.
     */
    public static int calculateTextAlpha(long timeSinceFull) {
        if (timeSinceFull > TEXT_DISPLAY_DURATION) {
            return 0;
        }
        float fadeProgress = (float) timeSinceFull / TEXT_DISPLAY_DURATION;
        return (int) (BASE_TEXT_ALPHA * (1.0F - fadeProgress));
    }

    // Overlay flash animation
    private static long tick = 0; // Not actually used here, but kept if you plan to use this for general flashes
    private static final long FLASH_DURATION = 20; // Ticks for one full flash cycle (e.g., 1 second = 20 ticks)
    private static final float MAX_FLASH_ALPHA = 0.5f; // Not currently used but kept for context

    public static void setTick(long currentTick) {
        tick = currentTick; // Not actually used here
    }

    public static float getOverlayFlashAlpha() {
        if (Minecraft.getInstance().player == null) return 0.0f;
        // Simple sinusoidal fade: oscillates between 0 and 1, used for pulsing effects
        float progress = (float)(Minecraft.getInstance().player.tickCount % FLASH_DURATION) / FLASH_DURATION;
        return Mth.sin(progress * Mth.PI * 2.0f) * 0.5f + 0.5f; // Oscillates between 0 and 1
    }

    /**
     * Draws a texture using a horizontal 3-slice (9-slice with zero vertical padding) method.
     * The left and right caps maintain their fixed widths, while the middle section stretches.
     * Assumes source texture's vertical dimensions are uniform (no vertical stretching).
     *
     * @param graphics The GuiGraphics instance.
     * @param texture The ResourceLocation of the texture to draw.
     * @param x The X coordinate to draw the texture at.
     * @param y The Y coordinate to draw the texture at.
     * @param destWidth The desired total width to draw the texture.
     * @param destHeight The desired total height to draw the texture (will be used for all slices).
     * @param sourceTextureWidth The actual width of the source texture file.
     * @param sourceTextureHeight The actual height of the source texture file.
     * @param leftPadding The fixed width of the left end padding in the source texture.
     * @param rightPadding The fixed width of the right end padding in the source texture.
     * @param alpha The alpha transparency to apply to the texture (0.0f to 1.0f).
     */
    public static void drawHorizontalNineSlice(GuiGraphics graphics, ResourceLocation texture, int x, int y, int destWidth, int destHeight, int sourceTextureWidth, int sourceTextureHeight, int leftPadding, int rightPadding, float alpha) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha); // Apply alpha to the entire drawn texture
        RenderSystem.enableBlend(); // Ensure blending is enabled for transparency (important for alpha)
        RenderSystem.defaultBlendFunc(); // Standard blend function

        int sourceStretchableWidth = sourceTextureWidth - leftPadding - rightPadding;
        int destMiddleWidth = destWidth - leftPadding - rightPadding;
        if (destMiddleWidth < 0) {
            destMiddleWidth = 0; // No stretchable middle, caps might overlap
        }

        // --- Tile Left Cap Vertically ---
        for (int tileY = 0; tileY < destHeight; tileY += sourceTextureHeight) {
            int drawHeight = Math.min(sourceTextureHeight, destHeight - tileY);
            graphics.blit(texture, x, y + tileY,
                    0, 0, // Source U, V
                    leftPadding, drawHeight, // Destination Width, Height (left padding)
                    sourceTextureWidth, sourceTextureHeight);
        }

        // --- Tile Right Cap Vertically ---
        for (int tileY = 0; tileY < destHeight; tileY += sourceTextureHeight) {
            int drawHeight = Math.min(sourceTextureHeight, destHeight - tileY);
            graphics.blit(texture, x + destWidth - rightPadding, y + tileY,
                    sourceTextureWidth - rightPadding, 0, // Source U, V (start of right padding in texture)
                    rightPadding, drawHeight, // Destination Width, Height (right padding)
                    sourceTextureWidth, sourceTextureHeight);
        }

        // --- Tile Middle Section Horizontally and Vertically ---
        if (destMiddleWidth > 0 && sourceStretchableWidth > 0) {
            int tiledX = x + leftPadding;
            int remainingX = destMiddleWidth;
            while (remainingX > 0) {
                int tileWidth = Math.min(sourceStretchableWidth, remainingX);
                int tileX = tiledX;
                // For each horizontal tile, tile vertically as well
                for (int tileY = 0; tileY < destHeight; tileY += sourceTextureHeight) {
                    int drawHeight = Math.min(sourceTextureHeight, destHeight - tileY);
                    graphics.blit(texture, tileX, y + tileY,
                            leftPadding, 0, // Source U, V (start of middle section in texture)
                            tileWidth, drawHeight, // Destination Width, Height (tile)
                            sourceTextureWidth, sourceTextureHeight);
                }
                tiledX += tileWidth;
                remainingX -= tileWidth;
            }
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f); // Reset shader color to full white
        // Don't disable blend here, as other rendering might rely on it.
    }
}
