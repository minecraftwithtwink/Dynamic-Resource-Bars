package dev.muon.dynamic_resource_bars.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.muon.dynamic_resource_bars.DynamicResourceBars;
import dev.muon.dynamic_resource_bars.config.ModConfigManager;
import dev.muon.dynamic_resource_bars.config.ClientConfig;
import dev.muon.dynamic_resource_bars.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;

#if NEWER_THAN_20_1
    import net.minecraft.client.DeltaTracker;
#endif

import net.minecraft.resources.ResourceLocation;

public class ManaBarRenderer {

    // --- Dynamic Sizing Constants ---
    // 1 Mana Point = 4 pixels. This constant defines how many pixels 1 Mana Point represents.
    private static final float PIXELS_PER_MANA_POINT = 4.0f; // 4 pixels per mana point (e.g., 100 max mana = 400px bar)
    private static final int MIN_BASE_BAR_WIDTH = 40; // Minimum width for the main mana bar (e.g., if max mana is very low)
    private static final int MAX_BASE_BAR_WIDTH = 400; // Maximum width to prevent bars from becoming too large
    private static final int MAX_RESERVED_MANA_POINTS = 50; // Maximum reserved mana points for scaling
    
    // --- Smart Scaling Constants ---
    private static final float SCALING_BASE = 1.5f; // Base for logarithmic scaling
    private static final float SCALING_FACTOR = 0.8f; // Controls how quickly the scaling curve flattens

    // --- 9-Slice Constants for Background and Foreground Textures ---
    // !!! IMPORTANT: These must match the actual pixel dimensions of your textures !!!
    // --- Texture sizes for each element ---
    private static final int BACKGROUND_SOURCE_TEXTURE_WIDTH = 182;
    private static final int BACKGROUND_SOURCE_TEXTURE_HEIGHT = 5;
    private static final int FOREGROUND_SOURCE_TEXTURE_WIDTH = 105;
    private static final int FOREGROUND_SOURCE_TEXTURE_HEIGHT = 11;
    private static final int BAR_SOURCE_TEXTURE_WIDTH = 182;
    private static final int BAR_SOURCE_TEXTURE_HEIGHT = 10;
    private static final int HORIZONTAL_SLICE_PADDING = 22; // User specified 22 pixels for each horizontal end padding
    private static final int BACKGROUND_FOREGROUND_TOTAL_PADDING = HORIZONTAL_SLICE_PADDING * 2; // Total width consumed by both end paddings (44 pixels)

    // --- Nine-slice paddings for each element ---
    private static final int CUSTOM_MANA_BAR_BACKGROUND_PADDING = 54; // For background
    private static final int CUSTOM_MANA_BAR_FOREGROUND_PADDING = 50; // For foreground
    private static final int CUSTOM_MANA_BAR_MAIN_PADDING = 40; // For main bar
    private static final int CUSTOM_MANA_BAR_MAIN_SHRINK = 10; // For main bar

    // For the new atlas: main bar is top half, overlay is bottom half
    private static final int ATLAS_MAIN_BAR_HEIGHT = BAR_SOURCE_TEXTURE_HEIGHT / 2;
    private static final int ATLAS_OVERLAY_HEIGHT = BAR_SOURCE_TEXTURE_HEIGHT / 2;
    private static final int ATLAS_TOTAL_HEIGHT = BAR_SOURCE_TEXTURE_HEIGHT;

    private static final float DAMPING_FACTOR = 0.85f; // Controls animation smoothness for mana regeneration
    private static float currentManaAnimated = -1.0f; // Animated mana for the main bar
    private static float currentReservedManaAnimated = -1.0f; // Animated reserved mana

    private static float lastMana = -1;
    private static long fullManaStartTime = 0;
    private static boolean manaBarSetVisible = true; // Default to visible, used for bar fade
    private static long manaBarDisabledStartTime = 0L; // Used for bar fade timing

    private static final int RESERVED_MANA_COLOR = 0x232323;

    /**
     * Calculates a smart-scaled width that prevents bars from becoming too large.
     * Uses logarithmic scaling to provide good visual distinction while limiting maximum size.
     */
    private static int calculateSmartScaledWidth(float maxValue, float pixelsPerPoint) {
        // Linear scaling for small values (up to 100 points for mana)
        if (maxValue <= 100.0f) {
            return Math.max(MIN_BASE_BAR_WIDTH, (int)(maxValue * pixelsPerPoint));
        }
        
        // Logarithmic scaling for larger values to prevent excessive growth
        float logValue = (float) Math.log(maxValue / 100.0f) / (float) Math.log(SCALING_BASE);
        float scaledValue = 100.0f + (logValue * SCALING_FACTOR * 100.0f);
        int scaledWidth = (int)(scaledValue * pixelsPerPoint);
        
        // Apply maximum width limit
        return Math.min(MAX_BASE_BAR_WIDTH, Math.max(MIN_BASE_BAR_WIDTH, scaledWidth));
    }

    /**
     * Returns the full width for the mana bar and overlays, including reserved mana if present
     */
    private static int getFullBarWidth(Player player, float reservedManaAmount, float maxMana) {
        int baseWidth = calculateSmartScaledWidth(maxMana, PIXELS_PER_MANA_POINT);
        int reservedWidth = (maxMana == 0) ? 0 : (int)(baseWidth * (reservedManaAmount / maxMana));
        return baseWidth + reservedWidth + BACKGROUND_FOREGROUND_TOTAL_PADDING;
    }

    /**
     * Helper to get the dynamic width for the main bar (mana only, no reserved, no caps)
     */
    private static int getMainBarWidth(Player player, float maxMana) {
        ClientConfig config = ModConfigManager.getClient();
        int baseWidth = calculateSmartScaledWidth(maxMana, PIXELS_PER_MANA_POINT);
        int percent = Math.max(0, Math.min(100, config.manaBarWidthModifier));
        int globalPercent = Math.max(0, Math.min(100, config.globalBarWidthModifier));
        int scaledWidth = Math.round(baseWidth * (percent / 100.0f) * (globalPercent / 100.0f));
        return Math.max(MIN_BASE_BAR_WIDTH, scaledWidth);
    }

    // Helper to get the reserved mana width (for background/foreground extension)
    private static int getReservedManaWidth(Player player, float reservedManaAmount, float maxMana) {
        int baseWidth = getMainBarWidth(player, maxMana);
        return (maxMana == 0) ? 0 : (int)(baseWidth * (reservedManaAmount / maxMana));
    }

    /**
     * Calculates the dynamic width for the background element, which includes padding around the main bar
     * determined by the 9-slice cap widths.
     */
    private static int getDynamicBackgroundWidth(Player player, float maxMana) {
        return getFullBarWidth(player, 0, maxMana); // No reserved mana for this calculation
    }

    /**
     * Calculates the dynamic width for the foreground/overlay element.
     * This is designed to be the same width as the main bar itself, as it's an overlay *on* the bar.
     */
    private static int getDynamicOverlayWidth(Player player, float maxMana) {
        return getFullBarWidth(player, 0, maxMana); // No reserved mana for this calculation
    }

    /**
     * Updates the animated mana values for smooth animation
     */
    private static void updateAnimatedValues(Player player, float partialTicks, float actualMana, float reservedManaAmount) {
        // --- Initialization and Reset on Major State Change ---
        if (currentManaAnimated < 0.0f || (actualMana < 0.01f && currentManaAnimated > 0.01f)) {
            currentManaAnimated = actualMana;
            currentReservedManaAnimated = reservedManaAmount;
        }

        // Capture the animated values from the *previous frame* (before current tick's updates)
        float previousFrameAnimatedMana = currentManaAnimated;
        float previousFrameAnimatedReserved = currentReservedManaAnimated;

        // Calculate interpolation factor for smooth animation
        float interpolationFactor = 1.0f - (float) Math.pow(DAMPING_FACTOR, partialTicks);
        interpolationFactor = Mth.clamp(interpolationFactor, 0.0f, 1.0f);

        // --- Update Main Mana Bar (`currentManaAnimated`) ---
        if (actualMana < previousFrameAnimatedMana - 0.01f) {
            // Mana decreased significantly (spent) -> instant snap down
            currentManaAnimated = actualMana;
        } else if (actualMana > previousFrameAnimatedMana + 0.01f) {
            // Mana increased significantly (regeneration) -> smooth animation up
            currentManaAnimated = Mth.lerp(interpolationFactor, previousFrameAnimatedMana, actualMana);
        } else {
            // Mana is very close or same -> snap to actual value to prevent micro-fluctuations
            currentManaAnimated = actualMana;
        }

        if (reservedManaAmount < previousFrameAnimatedReserved - 0.01f) {
            // Reserved mana decreased significantly -> instant snap down
            currentReservedManaAnimated = reservedManaAmount;
        } else if (reservedManaAmount > previousFrameAnimatedReserved + 0.01f) {
            // Reserved mana increased significantly -> smooth animation up
            currentReservedManaAnimated = Mth.lerp(interpolationFactor, previousFrameAnimatedReserved, reservedManaAmount);
        } else {
            // Reserved mana is very close or same -> snap to actual value
            currentReservedManaAnimated = reservedManaAmount;
        }
    }

    /**
     * Calculates the overall bounding rectangle for the mana bar and its sub-elements,
     * based on the configured anchor and total offsets.
     */
    public static ScreenRect getScreenRect(Player player, float maxMana) {
        if (player == null) return new ScreenRect(0, 0, 0, 0);
        ClientConfig config = ModConfigManager.getClient();
        int mainBarWidth = getMainBarWidth(player, maxMana);
        int totalPadding = CUSTOM_MANA_BAR_MAIN_PADDING * 2;
        
        // Ensure minimum width that can accommodate the nine-slice padding
        int minRequiredWidth = CUSTOM_MANA_BAR_BACKGROUND_PADDING * 2; // Left + right padding
        int width = Math.max(minRequiredWidth, mainBarWidth + totalPadding);
        int height = config.manaBackgroundHeight;
        ScreenRect parentBox = new ScreenRect(0, 0, width, height);
        Position anchorPos = HUDPositioning.alignBoundingBoxToAnchor(parentBox, config.manaBarAnchor);
        Position finalPos = anchorPos.offset(config.manaTotalXOffset, config.manaTotalYOffset);
        return new ScreenRect(finalPos.x(), finalPos.y(), width, height);
    }

    /**
     * Returns the bounding rectangle for a specific sub-element (e.g., background, main bar, text).
     * Dimensions are taken from dynamic calculations or client configuration (for height and offsets).
     */
    public static ScreenRect getSubElementRect(SubElementType type, Player player, float maxMana) {
        ScreenRect complexRect = getScreenRect(player, maxMana);
        if (complexRect == null || (complexRect.width() == 0 && complexRect.height() == 0))
            return new ScreenRect(0, 0, 0, 0);
        ClientConfig config = ModConfigManager.getClient();
        int x = complexRect.x();
        int y = complexRect.y();
        int baseWidth = getMainBarWidth(player, maxMana);
        switch (type) {
            case BACKGROUND:
                return new ScreenRect(x + config.manaBackgroundXOffset, y + config.manaBackgroundYOffset, baseWidth + CUSTOM_MANA_BAR_MAIN_SHRINK * 2, config.manaBackgroundHeight);
            case BAR_MAIN:
                return new ScreenRect(x + config.manaBarXOffset + CUSTOM_MANA_BAR_BACKGROUND_PADDING, y + config.manaBarYOffset, baseWidth, config.manaBarHeight);
            case FOREGROUND_DETAIL:
                return new ScreenRect(x + config.manaOverlayXOffset, y + config.manaOverlayYOffset, baseWidth + CUSTOM_MANA_BAR_MAIN_SHRINK * 2, config.manaOverlayHeight);
            case TEXT:
                return new ScreenRect(x + config.manaTextXOffset + CUSTOM_MANA_BAR_BACKGROUND_PADDING, y + config.manaTextYOffset, baseWidth, config.manaBarHeight);
            case TRAILING_ICON:
                // Fixed position at the end of the bar progress, using bar height
                float manaRatio = (maxMana == 0) ? 0 : (currentManaAnimated / maxMana);
                manaRatio = Mth.clamp(manaRatio, 0.0f, 1.0f);
                int iconX = x + config.manaBarXOffset + CUSTOM_MANA_BAR_BACKGROUND_PADDING + (int)(baseWidth * manaRatio);
                int iconY = y + config.manaBarYOffset;
                return new ScreenRect(iconX, iconY, BACKGROUND_SOURCE_TEXTURE_HEIGHT, BACKGROUND_SOURCE_TEXTURE_HEIGHT);
            default:
                return new ScreenRect(0, 0, 0, 0);
        }
    }

    public static void render(GuiGraphics graphics, #if NEWER_THAN_20_1 DeltaTracker deltaTracker #else float partialTicks #endif, ManaProvider manaProvider, Player player) {
        if (!Minecraft.getInstance().gameMode.canHurtPlayer() && !EditModeManager.isEditModeEnabled()) {
            return;
        }

        ClientConfig config = ModConfigManager.getClient();
        float currentPartialTicks = #if NEWER_THAN_20_1 deltaTracker.getGameTimeDeltaTicks() #else partialTicks #endif;

        // Get mana values from provider
        float actualMana = (float) manaProvider.getCurrentMana();
        float maxMana = manaProvider.getMaxMana();
        float reservedMana = manaProvider.getReservedMana();

        // Update animated values using partialTicks for smooth animation
        updateAnimatedValues(player, currentPartialTicks, actualMana, reservedMana);

        // Override hideWhenFull if player has reserved mana or is in edit mode
        boolean shouldFade = config.fadeManaWhenFull && actualMana >= maxMana && reservedMana == 0;
        setManaBarVisibility(!shouldFade || EditModeManager.isEditModeEnabled());

        // Don't render if fully faded and not in edit mode
        if (!isManaBarVisible() && !EditModeManager.isEditModeEnabled() && (System.currentTimeMillis() - manaBarDisabledStartTime) > RenderUtil.BAR_FADEOUT_DURATION) {
            return;
        }

        float currentAlphaForRender = getManaBarAlpha();
        if (EditModeManager.isEditModeEnabled() && !isManaBarVisible()) {
            currentAlphaForRender = 1.0f; // Show fully if in edit mode, even if normally faded
        }

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, currentAlphaForRender);

        ScreenRect complexRect = getScreenRect(player, maxMana);

        int backgroundHeight = config.manaBackgroundHeight;
        int animationCycles = config.manaBarAnimationCycles;
        int frameHeight = config.manaBarFrameHeight;

        // Render Background (using 9-slice)
        if (config.enableManaBackground) {
            ScreenRect bgRect = getSubElementRect(SubElementType.BACKGROUND, player, maxMana);
            RenderUtil.drawHorizontalNineSlice(graphics, DynamicResourceBars.loc("textures/gui/mana_background.png"),
                    bgRect.x(), bgRect.y(), bgRect.width(), bgRect.height(),
                    BACKGROUND_SOURCE_TEXTURE_WIDTH, BACKGROUND_SOURCE_TEXTURE_HEIGHT,
                    CUSTOM_MANA_BAR_BACKGROUND_PADDING, CUSTOM_MANA_BAR_BACKGROUND_PADDING,
                    currentAlphaForRender // Pass current bar's alpha
            );
        }

        // Calculate animation offset for the bar texture
        float ticks = player.tickCount + currentPartialTicks;
        int animOffset = (int) ((ticks / 3) % animationCycles) * frameHeight;

        ScreenRect mainBarRect = getSubElementRect(SubElementType.BAR_MAIN, player, maxMana);
        boolean isRightAnchored = config.manaBarAnchor == AnchorPoint.TOP_RIGHT || config.manaBarAnchor == AnchorPoint.CENTER_RIGHT || config.manaBarAnchor == AnchorPoint.BOTTOM_RIGHT;

        // --- Calculate a consistent total maximum for scaling all mana-related bars ---
        float maxPossibleTotalMana = maxMana + MAX_RESERVED_MANA_POINTS;
        float currentAnimatedTotalMana = currentManaAnimated + currentReservedManaAnimated;

        // --- Render Main Mana Bar ---
        renderBaseBar(graphics, player, maxPossibleTotalMana, currentManaAnimated, mainBarRect.x(), mainBarRect.y(), mainBarRect.width(), mainBarRect.height(), 0, 0, animOffset, isRightAnchored, maxMana);
        
        // --- Render Gradient Overlay ---
        renderGradientOverlay(graphics, player, mainBarRect, currentAlphaForRender, maxMana);
        
        // --- Render Trailing Icon ---
        if (config.enableManaTrailingIcon) {
            renderTrailingIcon(graphics, player, currentManaAnimated, mainBarRect, currentAlphaForRender, maxMana);
        }
        
        // --- Render Reserved Mana Overlay ---
        if (currentReservedManaAnimated > 0) {
            renderReservedManaOverlay(graphics, player, currentManaAnimated, currentReservedManaAnimated, mainBarRect, animOffset, isRightAnchored, maxMana);
        }

        // Render foreground overlay (using 9-slice)
        if (config.enableManaForeground) {
            ScreenRect fgRect = getSubElementRect(SubElementType.FOREGROUND_DETAIL, player, maxMana);
            RenderUtil.drawHorizontalNineSlice(graphics, DynamicResourceBars.loc("textures/gui/mana_foreground.png"),
                    fgRect.x(), fgRect.y(), fgRect.width(), fgRect.height(),
                    FOREGROUND_SOURCE_TEXTURE_WIDTH, FOREGROUND_SOURCE_TEXTURE_HEIGHT,
                    CUSTOM_MANA_BAR_FOREGROUND_PADDING, CUSTOM_MANA_BAR_FOREGROUND_PADDING,
                    currentAlphaForRender // Pass current bar's alpha
            );
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f); // Reset shader color to full white after rendering all overlays/backgrounds

        // Update text rendering to use draggable position
        if (shouldRenderManaText(actualMana, maxMana, player)) { // Use actual mana for text visibility logic
            ScreenRect textRect = getSubElementRect(SubElementType.TEXT, player, maxMana);
            int textX = textRect.x() + (textRect.width() / 2);
            int textY = textRect.y() + (textRect.height() - Minecraft.getInstance().font.lineHeight) / 2; // Center text vertically
            
            int color = getManaTextColor(actualMana, maxMana);
            HorizontalAlignment alignment = config.manaTextAlign;

            int baseX = textRect.x();
            if (alignment == HorizontalAlignment.CENTER) {
                baseX = textX;
            } else if (alignment == HorizontalAlignment.RIGHT) {
                baseX = textRect.x() + textRect.width();
            }

            // Remove: RenderUtil.renderText((int)actualMana, (int)maxMana, graphics, baseX, textY, color, alignment);
        }

        // Add focus mode outline rendering
        if (EditModeManager.isEditModeEnabled()) {
            DraggableElement currentBarType = DraggableElement.MANA_BAR;
            if (EditModeManager.getFocusedElement() == currentBarType) {
                int focusedBorderColor = 0xA0FFFF00; // Yellow for focused element
                ScreenRect bgRect = getSubElementRect(SubElementType.BACKGROUND, player, maxMana);
                graphics.renderOutline(bgRect.x() - 1, bgRect.y() - 1, bgRect.width() + 2, bgRect.height() + 2, focusedBorderColor);

                ScreenRect barRect = getSubElementRect(SubElementType.BAR_MAIN, player, maxMana);
                graphics.renderOutline(barRect.x() - 1, barRect.y() - 1, barRect.width() + 2, barRect.height() + 2, 0xA000FF00); // Green for main bar

                if (config.enableManaForeground) { // Check config for foreground enabled
                    ScreenRect fgRect = getSubElementRect(SubElementType.FOREGROUND_DETAIL, player, maxMana);
                    graphics.renderOutline(fgRect.x() - 1, fgRect.y() - 1, fgRect.width() + 2, fgRect.height() + 2, 0xA0FF00FF); // Magenta for foreground
                }

                graphics.renderOutline(complexRect.x() - 2, complexRect.y() - 2, complexRect.width() + 4, complexRect.height() + 4, 0x80FFFFFF); // Overall bar outline
            } else {
                int borderColor = 0x80FFFFFF; // Less prominent outline for unfocused bars
                graphics.renderOutline(complexRect.x() - 1, complexRect.y() - 1, complexRect.width() + 2, complexRect.height() + 2, borderColor);
            }
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f); // Reset shader color again
        RenderSystem.disableBlend(); // Ensure blend is disabled after rendering this bar
    }

    /**
     * Renders the base mana bar (filled portion)
     * @param maxTotalMana The maximum value to which the bar should visually scale (e.g., player max mana + max reserved).
     */
    private static void renderBaseBar(GuiGraphics graphics, Player player, float maxTotalMana, float manaToDisplay, int barAbsX, int barAbsY, int barAbsWidth, int barAbsHeight, int barXOffsetWithinTexture, int barYOffsetWithinTexture, int animOffset, boolean isRightAnchored, float maxMana) {
        float fillRatio = (maxMana == 0) ? 0.0f : (manaToDisplay / maxMana);
        fillRatio = Mth.clamp(fillRatio, 0.0f, 1.0f);
        FillDirection fillDirection = ModConfigManager.getClient().manaFillDirection;
        if (fillDirection == FillDirection.VERTICAL) {
            int filledHeight = (int) (barAbsHeight * fillRatio);
            if (manaToDisplay > 0 && filledHeight == 0) filledHeight = 1;
            if (filledHeight > 0) {
                int barRenderY = barAbsY + (barAbsHeight - filledHeight);
                int textureVOffset = BAR_SOURCE_TEXTURE_HEIGHT - filledHeight;
                graphics.blit(
                        DynamicResourceBars.loc("textures/gui/mana_bar.png"),
                        barAbsX, barRenderY,
                        0, textureVOffset,
                        barAbsWidth, filledHeight,
                        BAR_SOURCE_TEXTURE_WIDTH, BAR_SOURCE_TEXTURE_HEIGHT
                );
            }
        } else {
            drawAsymmetricBarNineSliceWithV(
                graphics,
                DynamicResourceBars.loc("textures/gui/mana_bar.png"),
                barAbsX, barAbsY,
                barAbsWidth, barAbsHeight,
                BAR_SOURCE_TEXTURE_WIDTH, BAR_SOURCE_TEXTURE_HEIGHT,
                CUSTOM_MANA_BAR_MAIN_PADDING, // Left cap width (now configurable)
                0,  // Right cap width (no right padding)
                fillRatio,
                0 // v offset for main bar
            );
        }
    }

    /**
     * Renders the reserved mana overlay using proper nine-slice rendering with a dedicated reserved mana texture.
     */
    private static void renderReservedManaOverlay(GuiGraphics graphics, Player player, float currentMana, float currentReservedMana, ScreenRect barRect, int animOffset, boolean isRightAnchored, float maxMana) {
        if (currentReservedMana <= 0) return;

        float manaRatio = (maxMana == 0) ? 0 : (currentMana / maxMana);
        float reservedRatio = (maxMana == 0) ? 0 : (currentReservedMana / maxMana);
        
        // Calculate the reserved mana bar dimensions
        int reservedBarWidth = (int) (barRect.width() * reservedRatio);
        if (reservedBarWidth <= 0) return;

        // Calculate starting position for reserved mana bar
        int reservedStartX = barRect.x() + (int) (barRect.width() * manaRatio);
        int reservedEndX = reservedStartX + reservedBarWidth;

        // Use dedicated reserved mana texture instead of main mana bar texture
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(((RESERVED_MANA_COLOR >> 16) & 0xFF) / 255f,
                ((RESERVED_MANA_COLOR >> 8) & 0xFF) / 255f,
                (RESERVED_MANA_COLOR & 0xFF) / 255f,
                1.0f);

        FillDirection fillDirection = ModConfigManager.getClient().manaFillDirection;

        if (fillDirection == FillDirection.VERTICAL) {
            // For vertical bars, reserved mana appears as a separate section
            int reservedHeight = (int) (barRect.height() * reservedRatio);
            if (reservedHeight > 0) {
                int yPos = barRect.y() + (barRect.height() - reservedHeight);
        graphics.blit(
                    DynamicResourceBars.loc("textures/gui/mana_bar.png"),
                    barRect.x(), yPos,
                    0, animOffset,
                    barRect.width(), reservedHeight,
                    BAR_SOURCE_TEXTURE_WIDTH, BAR_SOURCE_TEXTURE_HEIGHT
                );
            }
        } else {
            // For horizontal bars, use nine-slice rendering for reserved mana
            drawAsymmetricBarNineSlice(
                graphics,
                DynamicResourceBars.loc("textures/gui/mana_bar.png"),
                reservedStartX, barRect.y(),
                reservedBarWidth, barRect.height(),
                BAR_SOURCE_TEXTURE_WIDTH, BAR_SOURCE_TEXTURE_HEIGHT,
                64, // Left cap width
                0,  // Right cap width
                1.0f // Full fill for reserved mana
            );
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    /**
     * Renders a gradient overlay on the mana bar for visual enhancement.
     * Uses the bottom half of the main mana bar texture as an overlay.
     */
    private static void renderGradientOverlay(GuiGraphics graphics, Player player, ScreenRect barRect, float alpha, float maxMana) {
        int barWidth = barRect.width();
        int barHeight = barRect.height();
        
        if (barWidth <= 0 || barHeight <= 0) return;
        
        // Calculate the current mana fill ratio
        float manaRatio = (maxMana == 0) ? 0 : (currentManaAnimated / maxMana);
        manaRatio = Mth.clamp(manaRatio, 0.0f, 1.0f);
        int filledWidth = (int)(barWidth * manaRatio);
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        // Overlay: use the bottom half of the main bar texture as a right-side overlay
        if (filledWidth > 0) {
            ResourceLocation barTexture = DynamicResourceBars.loc("textures/gui/mana_bar.png");
            int overlayWidth = Math.min(CUSTOM_MANA_BAR_MAIN_PADDING, filledWidth); // Use the rightmost N pixels
            int overlayX = barRect.x() + filledWidth - overlayWidth;
            graphics.blit(
                barTexture,
                overlayX, barRect.y(),
                BAR_SOURCE_TEXTURE_WIDTH - overlayWidth, ATLAS_MAIN_BAR_HEIGHT, // U, V
                overlayWidth, barRect.height(),
                BAR_SOURCE_TEXTURE_WIDTH, ATLAS_TOTAL_HEIGHT
            );
        }
        
        RenderSystem.disableBlend();
    }

    /**
     * Renders the trailing icon that follows the mana bar progress.
     * The icon is positioned at the end of the current mana fill and moves with the bar.
     * @param graphics GuiGraphics instance.
     * @param player The player entity.
     * @param manaToDisplay The exact same mana value used by the main bar for fill ratio calculation.
     * @param mainBarRect The main bar's rectangle for positioning calculations.
     * @param alpha The alpha value for rendering.
     * @param maxMana The maximum mana value for scaling.
     */
    private static void renderTrailingIcon(GuiGraphics graphics, Player player, float manaToDisplay, ScreenRect mainBarRect, float alpha, float maxMana) {
        // Calculate the position using the exact same fill ratio calculation as the main bar
        float fillRatio = (maxMana == 0) ? 0.0f : (manaToDisplay / maxMana);
        fillRatio = Mth.clamp(fillRatio, 0.0f, 1.0f);
        
        int iconX = mainBarRect.x() + (int)(mainBarRect.width() * fillRatio);
        int iconY = mainBarRect.y();
        
        // Apply alpha blending
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        
        // Render the trailing icon texture with fixed bar height
        graphics.blit(
            DynamicResourceBars.loc("textures/gui/mana_trailing_icon.png"),
            iconX, iconY,
            0, 0,
            BACKGROUND_SOURCE_TEXTURE_HEIGHT, BACKGROUND_SOURCE_TEXTURE_HEIGHT,
            BACKGROUND_SOURCE_TEXTURE_HEIGHT, BACKGROUND_SOURCE_TEXTURE_HEIGHT
        );

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    /**
     * Calculates the color for the mana text, including alpha for fading.
     */
    private static int getManaTextColor(float currentMana, float maxMana) {
        TextBehavior behavior = ModConfigManager.getClient().showManaText;
        ClientConfig config = ModConfigManager.getClient();

        int baseColor = config.manaTextColor & 0xFFFFFF; // Use configured text color
        int alpha = config.manaTextOpacity;

        // Apply fading logic based on text behavior
        if (behavior == TextBehavior.WHEN_NOT_FULL && currentMana >= maxMana) {
            long timeSinceFull = System.currentTimeMillis() - fullManaStartTime;
            alpha = (int) (alpha * (RenderUtil.calculateTextAlpha(timeSinceFull) / (float) RenderUtil.BASE_TEXT_ALPHA));
        }

        alpha = (int) (alpha * getManaBarAlpha()); // Modulate text alpha with bar's overall alpha
        alpha = Mth.clamp(alpha, 10, 255); // Clamp alpha to ensure visibility

        return (alpha << 24) | baseColor;
    }

    /**
     * Determines if the mana text should be rendered based on configuration and player state.
     */
    private static boolean shouldRenderManaText(float currentMana, float maxMana, Player player) {
        TextBehavior behavior = ModConfigManager.getClient().showManaText;

        if (EditModeManager.isEditModeEnabled()) {
            return behavior == TextBehavior.ALWAYS || behavior == TextBehavior.WHEN_NOT_FULL;
        }

        if (behavior == TextBehavior.NEVER) {
            return false;
        }
        if (behavior == TextBehavior.ALWAYS) {
            return true;
        }

        // WHEN_NOT_FULL logic: Show when not full, or fade out after becoming full
        boolean isFull = currentMana >= maxMana;
        if (isFull) {
            if (lastMana < maxMana || lastMana == -1) { // Just became full or first check
                fullManaStartTime = System.currentTimeMillis(); // Reset timer
            }
            lastMana = currentMana;
            // Show for a short duration after becoming full
            return (System.currentTimeMillis() - fullManaStartTime) < RenderUtil.TEXT_DISPLAY_DURATION;
        } else {
            lastMana = currentMana;
            return true; // Not full, so show
        }
    }

    /**
     * Controls the visibility state of the mana bar for fading purposes.
     * @param visible True if the bar should currently be considered "visible" (e.g., mana not full).
     */
    private static void setManaBarVisibility(boolean visible) {
        if (manaBarSetVisible != visible) {
            if (!visible) {
                manaBarDisabledStartTime = System.currentTimeMillis(); // Start fade timer
            }
            manaBarSetVisible = visible;
        }
    }

    /**
     * Checks if the mana bar is currently considered visible (not fully faded out).
     */
    private static boolean isManaBarVisible() {
        return manaBarSetVisible;
    }

    /**
     * Calculates the current alpha value for the mana bar, for fading in/out effects.
     */
    private static float getManaBarAlpha() {
        if (isManaBarVisible()) {
            return 1.0f; // Fully opaque if visible
        }
        long timeSinceDisabled = System.currentTimeMillis() - manaBarDisabledStartTime;
        if (timeSinceDisabled >= RenderUtil.BAR_FADEOUT_DURATION) {
            return 0.0f; // Fully transparent if fade duration passed
        }
        // Calculate fading alpha
        return Mth.clamp(1.0f - (timeSinceDisabled / (float) RenderUtil.BAR_FADEOUT_DURATION), 0.0f, 1.0f);
    }

    // Helper to draw an asymmetric nine-slice bar (different left and right cap widths)
    private static void drawAsymmetricBarNineSlice(GuiGraphics graphics, ResourceLocation texture, int x, int y, int destWidth, int destHeight, int sourceTextureWidth, int sourceTextureHeight, int leftPadding, int rightPadding, float fillRatio) {
        int filledWidth = (int)(destWidth * fillRatio);
        if (filledWidth <= 0) return;
        
        // Draw left padding (tile vertically)
        if (filledWidth > 0 && leftPadding > 0) {
            int leftWidth = Math.min(leftPadding, filledWidth);
            for (int tileY = 0; tileY < destHeight; tileY += sourceTextureHeight) {
                int drawHeight = Math.min(sourceTextureHeight, destHeight - tileY);
                graphics.blit(texture, x, y + tileY, 0, 0, leftWidth, drawHeight, sourceTextureWidth, sourceTextureHeight);
            }
        }
        
        // Draw tiled middle (horizontally and vertically)
        int sourceMiddleWidth = sourceTextureWidth - leftPadding - rightPadding;
        int destMiddleWidth = filledWidth - leftPadding - rightPadding;
        int tiledX = x + leftPadding;
        int remaining = destMiddleWidth;
        while (remaining > 0) {
            int tileWidth = Math.min(sourceMiddleWidth, remaining);
            for (int tileY = 0; tileY < destHeight; tileY += sourceTextureHeight) {
                int drawHeight = Math.min(sourceTextureHeight, destHeight - tileY);
                graphics.blit(texture, tiledX, y + tileY, leftPadding, 0, tileWidth, drawHeight, sourceTextureWidth, sourceTextureHeight);
            }
            tiledX += tileWidth;
            remaining -= tileWidth;
        }
        
        // Draw right padding (tile vertically) - only if there's enough filled width
        if (filledWidth > leftPadding && rightPadding > 0) {
            int rightStart = x + filledWidth - rightPadding;
            int rightWidth = Math.min(rightPadding, filledWidth - leftPadding);
            if (rightWidth > 0) {
                for (int tileY = 0; tileY < destHeight; tileY += sourceTextureHeight) {
                    int drawHeight = Math.min(sourceTextureHeight, destHeight - tileY);
                    graphics.blit(texture, rightStart, y + tileY, sourceTextureWidth - rightPadding, 0, rightWidth, drawHeight, sourceTextureWidth, sourceTextureHeight);
                }
            }
        }
    }

    // Like drawAsymmetricBarNineSlice, but with a v offset for vertical cropping in the atlas
    private static void drawAsymmetricBarNineSliceWithV(GuiGraphics graphics, ResourceLocation texture, int x, int y, int destWidth, int destHeight, int sourceTextureWidth, int sourceTextureHeight, int leftPadding, int rightPadding, float fillRatio, int vOffset) {
        int filledWidth = (int)(destWidth * fillRatio);
        if (filledWidth <= 0) return;

        // If the bar is smaller than the sum of paddings, only draw what fits
        if (filledWidth <= rightPadding) {
            // Only draw the right cap, aligned to the right edge
            int rightStart = x;
            int rightWidth = filledWidth;
            for (int tileY = 0; tileY < destHeight; tileY += sourceTextureHeight) {
                int drawHeight = Math.min(sourceTextureHeight, destHeight - tileY);
                graphics.blit(texture, rightStart, y + tileY, sourceTextureWidth - rightPadding, vOffset, rightWidth, drawHeight, sourceTextureWidth, sourceTextureHeight);
            }
            return;
        }
        if (filledWidth <= leftPadding) {
            // Only draw the left cap
            int leftWidth = filledWidth;
            for (int tileY = 0; tileY < destHeight; tileY += sourceTextureHeight) {
                int drawHeight = Math.min(sourceTextureHeight, destHeight - tileY);
                graphics.blit(texture, x, y + tileY, 0, vOffset, leftWidth, drawHeight, sourceTextureWidth, sourceTextureHeight);
            }
            return;
        }

        // Draw left padding (tile vertically)
        if (leftPadding > 0) {
            int leftWidth = leftPadding;
            for (int tileY = 0; tileY < destHeight; tileY += sourceTextureHeight) {
                int drawHeight = Math.min(sourceTextureHeight, destHeight - tileY);
                graphics.blit(texture, x, y + tileY, 0, vOffset, leftWidth, drawHeight, sourceTextureWidth, sourceTextureHeight);
            }
        }
        // Draw tiled middle (horizontally and vertically)
        int sourceMiddleWidth = sourceTextureWidth - leftPadding - rightPadding;
        int destMiddleWidth = filledWidth - leftPadding - rightPadding;
        int tiledX = x + leftPadding;
        int remaining = destMiddleWidth;
        while (remaining > 0) {
            int tileWidth = Math.min(sourceMiddleWidth, remaining);
            for (int tileY = 0; tileY < destHeight; tileY += sourceTextureHeight) {
                int drawHeight = Math.min(sourceTextureHeight, destHeight - tileY);
                graphics.blit(texture, tiledX, y + tileY, leftPadding, vOffset, tileWidth, drawHeight, sourceTextureWidth, sourceTextureHeight);
            }
            tiledX += tileWidth;
            remaining -= tileWidth;
        }
        // Draw right padding (tile vertically)
        if (rightPadding > 0) {
            int rightStart = x + filledWidth - rightPadding;
            int rightWidth = rightPadding;
            for (int tileY = 0; tileY < destHeight; tileY += sourceTextureHeight) {
                int drawHeight = Math.min(sourceTextureHeight, destHeight - tileY);
                graphics.blit(texture, rightStart, y + tileY, sourceTextureWidth - rightPadding, vOffset, rightWidth, drawHeight, sourceTextureWidth, sourceTextureHeight);
            }
        }
    }
}