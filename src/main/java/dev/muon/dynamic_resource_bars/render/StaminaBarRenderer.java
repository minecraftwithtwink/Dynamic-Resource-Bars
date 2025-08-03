package dev.muon.dynamic_resource_bars.render;


import com.mojang.blaze3d.systems.RenderSystem;
import dev.muon.dynamic_resource_bars.DynamicResourceBars;
import dev.muon.dynamic_resource_bars.config.ModConfigManager;
import dev.muon.dynamic_resource_bars.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import dev.muon.dynamic_resource_bars.compat.AppleSkinCompat;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
#if UPTO_20_1 && FABRIC
import moriyashiine.bewitchment.api.BewitchmentAPI;
import moriyashiine.bewitchment.common.registry.BWComponents;
import moriyashiine.bewitchment.api.component.BloodComponent;
#endif

#if NEWER_THAN_20_1
    import net.minecraft.client.DeltaTracker;
#endif


import vectorwing.farmersdelight.common.registry.ModEffects;
import dev.muon.dynamic_resource_bars.config.ClientConfig;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;

public class StaminaBarRenderer {
    private static final float CRITICAL_THRESHOLD = 6.0f;
    
    // --- Dynamic Sizing Constants ---
    // 1 Stamina Point = 4 pixels. This constant defines how many pixels 1 Stamina Point represents.
    private static final float PIXELS_PER_STAMINA_POINT = 4.0f; // 4 pixels per stamina point (e.g., 20 max stamina = 80px bar)
    private static final int MIN_BASE_BAR_WIDTH = 40; // Minimum width for the main stamina bar
    private static final int MAX_BASE_BAR_WIDTH = 400; // Maximum width to prevent bars from becoming too large
    
    // --- Smart Scaling Constants ---
    private static final float SCALING_BASE = 1.5f; // Base for logarithmic scaling
    private static final float SCALING_FACTOR = 0.8f; // Controls how quickly the scaling curve flattens

    // --- 9-Slice Constants for Background and Foreground Textures ---
    // !!! IMPORTANT: These must match the actual pixel dimensions of your textures !!!
    private static final int BACKGROUND_SOURCE_TEXTURE_WIDTH = 182;
    private static final int BACKGROUND_SOURCE_TEXTURE_HEIGHT = 5;
    private static final int FOREGROUND_SOURCE_TEXTURE_WIDTH = 84;
    private static final int FOREGROUND_SOURCE_TEXTURE_HEIGHT = 12;
    private static final int BAR_SOURCE_TEXTURE_WIDTH = 182;
    private static final int BAR_SOURCE_TEXTURE_HEIGHT = 10;
    private static final int HORIZONTAL_SLICE_PADDING = 22; // User specified 22 pixels for each horizontal end padding
    private static final int BACKGROUND_FOREGROUND_TOTAL_PADDING = HORIZONTAL_SLICE_PADDING * 2; // Total width consumed by both end paddings (44 pixels)

    // --- Nine-slice paddings for each element ---
    private static final int CUSTOM_STAMINA_BAR_BACKGROUND_PADDING = 54; // For background
    private static final int CUSTOM_STAMINA_BAR_FOREGROUND_PADDING = 40; // For foreground
    private static final int CUSTOM_STAMINA_BAR_MAIN_PADDING = 40; // For main bar
    private static final int CUSTOM_STAMINA_BAR_MAIN_SHRINK = 10; // For main bar

    // For the new atlas: main bar is top half, overlay is bottom half
    private static final int ATLAS_MAIN_BAR_HEIGHT = BAR_SOURCE_TEXTURE_HEIGHT / 2;
    private static final int ATLAS_OVERLAY_HEIGHT = BAR_SOURCE_TEXTURE_HEIGHT / 2;
    private static final int ATLAS_TOTAL_HEIGHT = BAR_SOURCE_TEXTURE_HEIGHT;

    private static final float DAMPING_FACTOR = 0.85f; // Controls animation smoothness for stamina regeneration
    private static float currentStaminaAnimated = -1.0f; // Animated stamina for the main bar
    
    private static float lastStamina = -1;
    private static long fullStaminaStartTime = 0;
    private static boolean staminaBarSetVisible = true; // Default to visible
    private static long staminaBarDisabledStartTime = 0L;
    
    // Mount health tracking
    private static float lastMountHealth = -1;
    private static float lastMountMaxHealth = -1;
    private static long fullMountHealthStartTime = 0;

    private enum BarType {
        NORMAL("stamina_bar"),
        NOURISHED("stamina_bar_nourished"),
        HUNGER("stamina_bar_hunger"),
        CRITICAL("stamina_bar_critical"),
        MOUNTED("stamina_bar_mounted");

        private final String texture;

        BarType(String texture) {
            this.texture = texture;
        }

        public String getTexture() {
            return texture;
        }

        public static BarType fromPlayerState(Player player, float value) {
            if (player.getVehicle() instanceof LivingEntity mount) {
                float healthPercentage = value / mount.getMaxHealth();
                if (healthPercentage <= 0.2f) {
                    return CRITICAL;
                }
                return MOUNTED;
            }
            if (PlatformUtil.isModLoaded("farmersdelight") && hasNourishmentEffect(player)) {
                return NOURISHED;
            }
            if (player.hasEffect(MobEffects.HUNGER)) return HUNGER;
            if (value <= CRITICAL_THRESHOLD) return CRITICAL;
            return NORMAL;
        }

    }

    /**
     * Calculates a smart-scaled width that prevents bars from becoming too large.
     * Uses logarithmic scaling to provide good visual distinction while limiting maximum size.
     */
    private static int calculateSmartScaledWidth(float maxValue, float pixelsPerPoint) {
        // Linear scaling for small values (up to 20 points for stamina)
        if (maxValue <= 20.0f) {
            return Math.max(MIN_BASE_BAR_WIDTH, (int)(maxValue * pixelsPerPoint));
        }
        
        // Logarithmic scaling for larger values to prevent excessive growth
        float logValue = (float) Math.log(maxValue / 20.0f) / (float) Math.log(SCALING_BASE);
        float scaledValue = 20.0f + (logValue * SCALING_FACTOR * 20.0f);
        int scaledWidth = (int)(scaledValue * pixelsPerPoint);
        
        // Apply maximum width limit
        return Math.min(MAX_BASE_BAR_WIDTH, Math.max(MIN_BASE_BAR_WIDTH, scaledWidth));
    }

    /**
     * Returns the full width for the stamina bar and overlays
     */
    private static int getFullBarWidth(Player player, float maxStamina) {
        int mainBarWidth = getMainBarWidth(player, maxStamina);
        return mainBarWidth + BACKGROUND_FOREGROUND_TOTAL_PADDING;
    }

    /**
     * Helper to get the dynamic width for the main bar
     */
    private static int getMainBarWidth(Player player, float maxStamina) {
        ClientConfig config = ModConfigManager.getClient();
        int baseWidth = calculateSmartScaledWidth(maxStamina, PIXELS_PER_STAMINA_POINT);
        int percent = Math.max(0, Math.min(100, config.staminaBarWidthModifier));
        int globalPercent = Math.max(0, Math.min(100, config.globalBarWidthModifier));
        int scaledWidth = Math.round(baseWidth * (percent / 100.0f) * (globalPercent / 100.0f));
        return Math.max(MIN_BASE_BAR_WIDTH, scaledWidth);
    }



    /**
     * Updates animated values for smooth transitions
     */
    private static void updateAnimatedValues(Player player, float partialTicks, float actualStamina) {
        // Initialize animated values if not set
        if (currentStaminaAnimated < 0) {
            currentStaminaAnimated = actualStamina;
        }

        // Smooth animation for stamina changes
        float targetStamina = actualStamina;
        float staminaDiff = targetStamina - currentStaminaAnimated;
        
        if (Math.abs(staminaDiff) > 0.01f) {
            currentStaminaAnimated += staminaDiff * (1.0f - DAMPING_FACTOR);
        } else {
            currentStaminaAnimated = targetStamina;
        }
    }

    public static ScreenRect getScreenRect(Player player) {
        if (player == null) return new ScreenRect(0, 0, 0, 0);
        
        // Determine the bar values based on player state
        BarValues values = getBarValues(player);
        float maxStamina = values.max;
        
        ClientConfig config = ModConfigManager.getClient();
        int mainBarWidth = getMainBarWidth(player, maxStamina);
        int totalPadding = CUSTOM_STAMINA_BAR_MAIN_PADDING * 2;
        
        // Ensure minimum width that can accommodate the nine-slice padding
        int minRequiredWidth = CUSTOM_STAMINA_BAR_BACKGROUND_PADDING * 2; // Left + right padding
        int width = Math.max(minRequiredWidth, mainBarWidth + totalPadding);
        int height = config.staminaBackgroundHeight;
        
        ScreenRect parentBox = new ScreenRect(0, 0, width, height);
        Position anchorPos = HUDPositioning.alignBoundingBoxToAnchor(parentBox, config.staminaBarAnchor);
        Position finalPos = anchorPos.offset(config.staminaTotalXOffset, config.staminaTotalYOffset);
        return new ScreenRect(finalPos.x(), finalPos.y(), width, height);
    }

    public static ScreenRect getSubElementRect(SubElementType type, Player player) {
        ScreenRect complexRect = getScreenRect(player);
        if (complexRect == null || (complexRect.width() == 0 && complexRect.height() == 0))
            return new ScreenRect(0, 0, 0, 0);

        // Determine the bar values based on player state
        BarValues values = getBarValues(player);
        float maxStamina = values.max;
        
        ClientConfig config = ModConfigManager.getClient();
        int x = complexRect.x();
        int y = complexRect.y();
        int baseWidth = getMainBarWidth(player, maxStamina);
        
        switch (type) {
            case BACKGROUND:
                return new ScreenRect(x + config.staminaBackgroundXOffset, y + config.staminaBackgroundYOffset, baseWidth + CUSTOM_STAMINA_BAR_MAIN_SHRINK * 2, config.staminaBackgroundHeight);
            case BAR_MAIN:
                return new ScreenRect(x + config.staminaBarXOffset + CUSTOM_STAMINA_BAR_BACKGROUND_PADDING, y + config.staminaBarYOffset, baseWidth, config.staminaBarHeight);
            case FOREGROUND_DETAIL:
                return new ScreenRect(x + config.staminaOverlayXOffset, y + config.staminaOverlayYOffset, baseWidth + CUSTOM_STAMINA_BAR_MAIN_SHRINK * 2, config.staminaOverlayHeight);
            case TEXT:
                return new ScreenRect(x + config.staminaTextXOffset + CUSTOM_STAMINA_BAR_BACKGROUND_PADDING, y + config.staminaTextYOffset, baseWidth, config.staminaBarHeight);
            case TRAILING_ICON:
                // Fixed position at the end of the bar progress, using bar height
                float staminaRatio = (maxStamina == 0) ? 0 : (currentStaminaAnimated / maxStamina);
                staminaRatio = Mth.clamp(staminaRatio, 0.0f, 1.0f);
                int iconX = x + config.staminaBarXOffset + CUSTOM_STAMINA_BAR_BACKGROUND_PADDING + (int)(baseWidth * staminaRatio);
                int iconY = y + config.staminaBarYOffset;
                return new ScreenRect(iconX, iconY, BACKGROUND_SOURCE_TEXTURE_HEIGHT, BACKGROUND_SOURCE_TEXTURE_HEIGHT);
            default:
                return new ScreenRect(0, 0, 0, 0);
        }
    }

    public static void render(GuiGraphics graphics, Player player, #if NEWER_THAN_20_1 DeltaTracker deltaTracker #else float partialTicks #endif ) {
        // Determine the bar values based on player state
        BarValues values = getBarValues(player);
        
        // Update animated values for smooth transitions
        updateAnimatedValues(player, #if NEWER_THAN_20_1 deltaTracker.getGameTimeDeltaTicks() #else partialTicks #endif, values.current);
        
        // Determine fade behavior
        boolean shouldFade = shouldBarFade(player, values);
        setStaminaBarVisibility(!shouldFade || EditModeManager.isEditModeEnabled());

        if (!isStaminaBarVisible() && !EditModeManager.isEditModeEnabled() && (System.currentTimeMillis() - staminaBarDisabledStartTime) > RenderUtil.BAR_FADEOUT_DURATION) {
            return;
        }

        if (!Minecraft.getInstance().gameMode.canHurtPlayer() && !EditModeManager.isEditModeEnabled()) {
            return;
        }

        float currentAlphaForRender = getStaminaBarAlpha();
        if (EditModeManager.isEditModeEnabled() && !isStaminaBarVisible()) {
            currentAlphaForRender = 1.0f; // Show fully if in edit mode
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, currentAlphaForRender);

        ScreenRect complexRect = getScreenRect(player);

        boolean isRightAnchored = ModConfigManager.getClient().staminaBarAnchor == AnchorPoint.TOP_RIGHT || ModConfigManager.getClient().staminaBarAnchor == AnchorPoint.CENTER_RIGHT || ModConfigManager.getClient().staminaBarAnchor == AnchorPoint.BOTTOM_RIGHT;

        if (ModConfigManager.getClient().enableStaminaBackground) {
            ScreenRect bgRect = getSubElementRect(SubElementType.BACKGROUND, player);
            RenderUtil.drawHorizontalNineSlice(graphics, DynamicResourceBars.loc("textures/gui/stamina_background.png"),
                    bgRect.x(), bgRect.y(), bgRect.width(), bgRect.height(),
                    BACKGROUND_SOURCE_TEXTURE_WIDTH, BACKGROUND_SOURCE_TEXTURE_HEIGHT,
                    CUSTOM_STAMINA_BAR_BACKGROUND_PADDING, CUSTOM_STAMINA_BAR_BACKGROUND_PADDING,
                    currentAlphaForRender // Pass current bar's alpha
            );
        }

        ScreenRect barRect = getSubElementRect(SubElementType.BAR_MAIN, player);
        renderBaseBar(graphics, player, values.current, values.max,
                barRect,
                isRightAnchored);

        // --- Render Gradient Overlay ---
        renderGradientOverlay(graphics, player, barRect, currentAlphaForRender, values.max);

        // --- Render Trailing Icon ---
        if (ModConfigManager.getClient().enableStaminaTrailingIcon) {
            renderTrailingIcon(graphics, player, currentStaminaAnimated, barRect, currentAlphaForRender, values.max);
        }

        // Overlays should not show for vampires or when mounted
        if (values.type == BarValueType.FOOD && !values.isMounted) {
            if (PlatformUtil.isModLoaded("appleskin")) {
                ItemStack heldFood = getHeldFood(player);
                renderHungerRestoredOverlay(graphics, player, heldFood, barRect, #if NEWER_THAN_20_1 deltaTracker.getGameTimeDeltaTicks() #else partialTicks #endif , isRightAnchored);
                renderSaturationOverlay(graphics, player, barRect, isRightAnchored);
            }

            if (PlatformUtil.isModLoaded("farmersdelight") && hasNourishmentEffect(player)) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                float pulseAlpha = TickHandler.getOverlayFlashAlpha();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, pulseAlpha);

                graphics.blit(
                        DynamicResourceBars.loc("textures/gui/nourishment_overlay.png"),
                        barRect.x(), barRect.y(),
                        0, 0, barRect.width(), barRect.height(),
                        256, 256
                );
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                RenderSystem.disableBlend();
            }
        }

        if (ModConfigManager.getClient().enableStaminaForeground) {
            ScreenRect fgRect = getSubElementRect(SubElementType.FOREGROUND_DETAIL, player);
            RenderUtil.drawHorizontalNineSlice(graphics, DynamicResourceBars.loc("textures/gui/stamina_foreground.png"),
                    fgRect.x(), fgRect.y(), fgRect.width(), fgRect.height(),
                    FOREGROUND_SOURCE_TEXTURE_WIDTH, FOREGROUND_SOURCE_TEXTURE_HEIGHT,
                    CUSTOM_STAMINA_BAR_FOREGROUND_PADDING, CUSTOM_STAMINA_BAR_FOREGROUND_PADDING,
                    currentAlphaForRender // Pass current bar's alpha
            );
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        if (shouldRenderStaminaText(values.current, values.max, player, values.isMounted)) {
            ScreenRect textRect = getSubElementRect(SubElementType.TEXT, player);
            int textX = textRect.x() + (textRect.width() / 2);
            int textY = textRect.y() + (textRect.height() / 2);
            
            int color = getStaminaTextColor(values.current, values.max, values.isMounted);
            HorizontalAlignment alignment = ModConfigManager.getClient().staminaTextAlign;

            int baseX = textRect.x();
            if (alignment == HorizontalAlignment.CENTER) {
                baseX = textX;
            } else if (alignment == HorizontalAlignment.RIGHT) {
                baseX = textRect.x() + textRect.width();
            }

            // Remove: RenderUtil.renderText(values.current, values.max, graphics, baseX, textY, color, alignment);
        }

        if (EditModeManager.isEditModeEnabled()) {
            DraggableElement currentBarType = DraggableElement.STAMINA_BAR;
            if (EditModeManager.getFocusedElement() == currentBarType) {
                int focusedBorderColor = 0xA0FFFF00;
                ScreenRect bgRect = getSubElementRect(SubElementType.BACKGROUND, player);
                if (ModConfigManager.getClient().enableStaminaBackground) {
                    graphics.renderOutline(bgRect.x() - 1, bgRect.y() - 1, bgRect.width() + 2, bgRect.height() + 2, focusedBorderColor);
                }

                ScreenRect barRectOutline = getSubElementRect(SubElementType.BAR_MAIN, player);
                graphics.renderOutline(barRectOutline.x() - 1, barRectOutline.y() - 1, barRectOutline.width() + 2, barRectOutline.height() + 2, 0xA0FFA500);

                if (ModConfigManager.getClient().enableStaminaForeground) {
                    ScreenRect fgRect = getSubElementRect(SubElementType.FOREGROUND_DETAIL, player);
                    graphics.renderOutline(fgRect.x() - 1, fgRect.y() - 1, fgRect.width() + 2, fgRect.height() + 2, 0xA0FF00FF);
                }
                graphics.renderOutline(complexRect.x() - 2, complexRect.y() - 2, complexRect.width() + 4, complexRect.height() + 4, 0x80FFFFFF);
            } else {
                int borderColor = 0x80FFFFFF;
                graphics.renderOutline(complexRect.x() - 1, complexRect.y() - 1, complexRect.width() + 2, complexRect.height() + 2, borderColor);
            }
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    /**
     * Renders a gradient overlay on the stamina bar for visual enhancement.
     * Uses the bottom half of the main stamina bar texture as an overlay.
     */
    private static void renderGradientOverlay(GuiGraphics graphics, Player player, ScreenRect barRect, float alpha, float maxStamina) {
        int barWidth = barRect.width();
        int barHeight = barRect.height();
        
        if (barWidth <= 0 || barHeight <= 0) return;
        
        // Calculate the current stamina fill ratio
        float staminaRatio = (maxStamina == 0) ? 0 : (currentStaminaAnimated / maxStamina);
        staminaRatio = Mth.clamp(staminaRatio, 0.0f, 1.0f);
        int filledWidth = (int)(barWidth * staminaRatio);
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        if (filledWidth > 0) {
            ResourceLocation barTexture = DynamicResourceBars.loc("textures/gui/stamina_bar.png");
            int overlayWidth = Math.min(CUSTOM_STAMINA_BAR_MAIN_PADDING, filledWidth);
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
     * Renders the trailing icon that follows the stamina bar progress.
     * The icon is positioned at the end of the current stamina fill and moves with the bar.
     * @param graphics GuiGraphics instance.
     * @param player The player entity.
     * @param staminaToDisplay The exact same stamina value used by the main bar for fill ratio calculation.
     * @param mainBarRect The main bar's rectangle for positioning calculations.
     * @param alpha The alpha value for rendering.
     * @param maxStamina The maximum stamina value for scaling.
     */
    private static void renderTrailingIcon(GuiGraphics graphics, Player player, float staminaToDisplay, ScreenRect mainBarRect, float alpha, float maxStamina) {
        // Calculate the position using the exact same fill ratio calculation as the main bar
        float fillRatio = (maxStamina == 0) ? 0.0f : (staminaToDisplay / maxStamina);
        fillRatio = Mth.clamp(fillRatio, 0.0f, 1.0f);
        
        int iconX = mainBarRect.x() + (int)(mainBarRect.width() * fillRatio);
        int iconY = mainBarRect.y();
        
        // Apply alpha blending
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        
        // Render the trailing icon texture with fixed bar height
        graphics.blit(
            DynamicResourceBars.loc("textures/gui/stamina_trailing_icon.png"),
            iconX, iconY,
            0, 0,
            BACKGROUND_SOURCE_TEXTURE_HEIGHT, BACKGROUND_SOURCE_TEXTURE_HEIGHT,
            BACKGROUND_SOURCE_TEXTURE_HEIGHT, BACKGROUND_SOURCE_TEXTURE_HEIGHT
        );
        
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    // Helper class to hold bar values
    private static class BarValues {
        float current;
        float max;
        BarValueType type;
        boolean isMounted;
        
        BarValues(float current, float max, BarValueType type, boolean isMounted) {
            this.current = current;
            this.max = max;
            this.type = type;
            this.isMounted = isMounted;
        }
    }
    
    private enum BarValueType {
        FOOD,
        NOURISHED,
        MOUNT_HEALTH
    }
    
    // Clean method to determine bar values based on player state
    private static BarValues getBarValues(Player player) {
        // Check if mounted first (highest priority)
        if (player.getVehicle() instanceof LivingEntity mount) {
            return new BarValues(mount.getHealth(), mount.getMaxHealth(), BarValueType.MOUNT_HEALTH, true);
        }
        
        // Check if vampire
        #if UPTO_20_1 && FABRIC
        if (PlatformUtil.isModLoaded("bewitchment") && BewitchmentAPI.isVampire(player, true)) {
            float bloodCurrent = BWComponents.BLOOD_COMPONENT.get(player).getBlood();
            float bloodMax = BloodComponent.MAX_BLOOD;
            return new BarValues(bloodCurrent, bloodMax, BarValueType.BLOOD, false);
        }
        #endif
        
        // Default to food
        return new BarValues(player.getFoodData().getFoodLevel(), 20f, BarValueType.FOOD, false);
    }
    
    // Clean method to determine fade behavior
    private static boolean shouldBarFade(Player player, BarValues values) {
        switch (values.type) {
            case MOUNT_HEALTH:
                return ModConfigManager.getClient().fadeHealthWhenFull && values.current >= values.max;
            case FOOD:
                return ModConfigManager.getClient().fadeStaminaWhenFull && values.current >= values.max;
            default:
                return false;
        }
    }

    private static void renderBaseBar(GuiGraphics graphics, Player player, float currentStamina, float maxStamina,
                                      ScreenRect barAreaRect,
                                      boolean isRightAnchored) {
        BarType barType = BarType.fromPlayerState(player, currentStamina);
        int totalBarWidth = barAreaRect.width();
        int barHeight = barAreaRect.height();
        float currentStaminaRatio = (maxStamina == 0) ? 0.0f : (currentStaminaAnimated / maxStamina);

        FillDirection fillDirection = ModConfigManager.getClient().staminaFillDirection;

        if (fillDirection == FillDirection.VERTICAL) {
            int partialBarHeight = (int) (barHeight * currentStaminaRatio);
            if (partialBarHeight <= 0 && currentStaminaAnimated > 0) partialBarHeight = 1;
            if (partialBarHeight > barHeight) partialBarHeight = barHeight;

            int barX = barAreaRect.x();
            int barY = barAreaRect.y() + (barHeight - partialBarHeight); // Fill from bottom up
            if (partialBarHeight > 0) {
                graphics.blit(
                        DynamicResourceBars.loc("textures/gui/" + barType.getTexture() + ".png"),
                        barX, barY,
                        0, 0, // Use 0 for U, 0 for V (static texture)
                        totalBarWidth, partialBarHeight, // Use full width, partial height
                        256, 256
                );
            }
        } else { // HORIZONTAL
            drawAsymmetricBarNineSlice(
                graphics,
                DynamicResourceBars.loc("textures/gui/" + barType.getTexture() + ".png"),
                barAreaRect.x(), barAreaRect.y(),
                totalBarWidth, barHeight,
                BAR_SOURCE_TEXTURE_WIDTH, BAR_SOURCE_TEXTURE_HEIGHT,
                CUSTOM_STAMINA_BAR_MAIN_PADDING, // Left cap width
                0,  // Right cap width (no right padding)
                currentStaminaRatio
            );
        }
    }

    private static boolean shouldRenderStaminaText(float currentValue, float maxValue, Player player, boolean isMounted) {
        TextBehavior textBehavior = isMounted ? 
            ModConfigManager.getClient().showHealthText : 
            ModConfigManager.getClient().showStaminaText;

        if (EditModeManager.isEditModeEnabled()) {
            if (textBehavior == TextBehavior.ALWAYS || textBehavior == TextBehavior.WHEN_NOT_FULL) {
                return true;
            }
        }
        if (textBehavior == TextBehavior.NEVER) {
            return false;
        }
        if (textBehavior == TextBehavior.ALWAYS) {
            return true;
        }
        
        // WHEN_NOT_FULL logic
        if (isMounted) {
            // Handle mount health separately
            boolean isFull = currentValue >= maxValue;
            if (isFull) {
                // Check if just became full or values changed
                if (lastMountHealth < maxValue || lastMountMaxHealth != maxValue || lastMountHealth == -1) {
                    fullMountHealthStartTime = System.currentTimeMillis();
                }
                lastMountHealth = currentValue;
                lastMountMaxHealth = maxValue;
                // Show for a short duration after becoming full
                return (System.currentTimeMillis() - fullMountHealthStartTime) < RenderUtil.TEXT_DISPLAY_DURATION;
            } else {
                lastMountHealth = currentValue;
                lastMountMaxHealth = maxValue;
                return true; // Not full, so show
            }
        } else {
            // Handle stamina normally
            boolean isFull = currentValue >= maxValue;
            if (isFull) {
                if (lastStamina < maxValue || lastStamina == -1) { // Just became full or first check
                    fullStaminaStartTime = System.currentTimeMillis();
                }
                lastStamina = currentValue;
                // Show for a short duration after becoming full
                return (System.currentTimeMillis() - fullStaminaStartTime) < RenderUtil.TEXT_DISPLAY_DURATION;
            } else {
                lastStamina = currentValue;
                return true; // Not full, so show
            }
        }
    }

    private static int getStaminaTextColor(float currentValue, float maxValue, boolean isMounted) {
        TextBehavior textBehavior = isMounted ? 
            ModConfigManager.getClient().showHealthText : 
            ModConfigManager.getClient().showStaminaText;
        ClientConfig config = ModConfigManager.getClient();
        int baseColor;
        int alpha;

        if (isMounted) {
            baseColor = config.healthTextColor & 0xFFFFFF;
            alpha = config.healthTextOpacity;
        } else {
            baseColor = config.staminaTextColor & 0xFFFFFF;
            alpha = config.staminaTextOpacity;
        }

        if (textBehavior == TextBehavior.WHEN_NOT_FULL && currentValue >= maxValue) {
            long timeSinceFull;
            if (isMounted) {
                timeSinceFull = System.currentTimeMillis() - fullMountHealthStartTime;
            } else {
                timeSinceFull = System.currentTimeMillis() - fullStaminaStartTime;
            }
            alpha = (int)(alpha * (RenderUtil.calculateTextAlpha(timeSinceFull) / (float)RenderUtil.BASE_TEXT_ALPHA));
        }

        alpha = (int) (alpha * getStaminaBarAlpha()); // Modulate with bar alpha
        alpha = Math.max(10, Math.min(255, alpha)); // Ensure minimum visibility
        return (alpha << 24) | baseColor;
    }

    // New helper methods for bar visibility and alpha
    private static void setStaminaBarVisibility(boolean visible) {
        if (staminaBarSetVisible != visible) {
            if (!visible) {
                staminaBarDisabledStartTime = System.currentTimeMillis();
            }
            staminaBarSetVisible = visible;
        }
    }

    private static boolean isStaminaBarVisible() {
        return staminaBarSetVisible;
    }

    private static float getStaminaBarAlpha() {
        if (isStaminaBarVisible()) {
            return 1.0f;
        }
        long timeSinceDisabled = System.currentTimeMillis() - staminaBarDisabledStartTime;
        if (timeSinceDisabled >= RenderUtil.BAR_FADEOUT_DURATION) {
            return 0.0f;
        }
        return Math.max(0.0f, 1.0f - (timeSinceDisabled / (float) RenderUtil.BAR_FADEOUT_DURATION));
    }

    private static void renderSaturationOverlay(GuiGraphics graphics, Player player, ScreenRect barRect, boolean isRightAnchored) {
        if (!PlatformUtil.isModLoaded("appleskin")) {
            return;
        }

        float saturation = player.getFoodData().getSaturationLevel();
        if (saturation <= 0) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        float saturationPercent = Math.min(1.0f, saturation / 20f);
        FillDirection fillDirection = ModConfigManager.getClient().staminaFillDirection;

        // Use pulsing opacity instead of frame animation
        float pulseAlpha = 0.5f + (TickHandler.getOverlayFlashAlpha() * 0.5f); // Range from 0.5 to 1.0
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, pulseAlpha);

        if (fillDirection == FillDirection.VERTICAL) {
            int overlayHeight = (int) (barRect.height() * saturationPercent);
            if (overlayHeight > 0) {
                graphics.blit(
                        DynamicResourceBars.loc("textures/gui/protection_overlay.png"), // Placeholder texture
                        barRect.x(),
                        barRect.y() + (barRect.height() - overlayHeight),
                        0, 0,
                        barRect.width(), overlayHeight,
                        256, 256
                );
            }
        } else { // HORIZONTAL
            int overlayWidth = (int) (barRect.width() * saturationPercent);
            if (overlayWidth > 0) {
                int xPos;
                int uTexOffset;
                if (isRightAnchored) {
                    xPos = barRect.x() + barRect.width() - overlayWidth;
                    uTexOffset = barRect.width() - overlayWidth; // Sample rightmost part of the texture
                } else {
                    xPos = barRect.x();
                    uTexOffset = 0; // Sample leftmost part of the texture
                }
                if (uTexOffset < 0) uTexOffset = 0;

                graphics.blit(
                        DynamicResourceBars.loc("textures/gui/protection_overlay.png"), // Placeholder texture
                        xPos, barRect.y(),
                        uTexOffset, 0, // Use calculated uTexOffset, vOffset usually 0 for horizontal overlays unless animated differently
                        overlayWidth, barRect.height(),
                        256, 256
                );
            }
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    private static void renderHungerRestoredOverlay(GuiGraphics graphics, Player player, ItemStack heldFood,
                                                    ScreenRect barRect, float partialTicks, boolean isRightAnchored) {
        if (!PlatformUtil.isModLoaded("appleskin")) {
            return;
        }

        AppleSkinCompat.FoodData foodData = AppleSkinCompat.getFoodValues(heldFood, player);
        if (foodData.isEmpty()) {
            return;
        }

        float currentHunger = player.getFoodData().getFoodLevel();
        float restoredHunger = Math.min(20f, currentHunger + foodData.hunger);

        if (restoredHunger <= currentHunger) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, TickHandler.getOverlayFlashAlpha());

        FillDirection fillDirection = ModConfigManager.getClient().staminaFillDirection;
        // Use the bar type that would apply at the restored hunger level
        BarType barType = BarType.fromPlayerState(player, restoredHunger);

        if (fillDirection == FillDirection.VERTICAL) {
            int currentHeight = (int) (barRect.height() * (currentHunger / 20f));
            int restoredHeight = (int) (barRect.height() * (restoredHunger / 20f));
            int overlayHeight = restoredHeight - currentHeight;

            if (overlayHeight > 0) {
                int yPos = barRect.y() + (barRect.height() - restoredHeight);

                graphics.blit(
                        DynamicResourceBars.loc("textures/gui/" + barType.getTexture() + ".png"),
                        barRect.x(), yPos,
                        0, 0, // Static texture, no animation
                        barRect.width(), overlayHeight,
                        256, 256
                );
            }
        } else { // HORIZONTAL
            int currentWidth = (int) (barRect.width() * (currentHunger / 20f));
            int restoredWidth = (int) (barRect.width() * (restoredHunger / 20f));
            int overlayWidth = restoredWidth - currentWidth;

            if (overlayWidth > 0) {
                int xDrawPos;
                int uTexOffset;

                if (isRightAnchored) {
                    xDrawPos = barRect.x() + barRect.width() - restoredWidth;
                    uTexOffset = barRect.width() - restoredWidth; 
                } else {
                    xDrawPos = barRect.x() + currentWidth;
                    uTexOffset = currentWidth;
                }
                if (uTexOffset < 0) uTexOffset = 0;

                graphics.blit(
                        DynamicResourceBars.loc("textures/gui/" + barType.getTexture() + ".png"),
                        xDrawPos, barRect.y(),
                        uTexOffset, 0, // Use the calculated uTexOffset, static texture
                        overlayWidth, barRect.height(),
                        256, 256
                );
            }
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    private static ItemStack getHeldFood(Player player) {
        ItemStack mainHand = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (AppleSkinCompat.canConsume(mainHand, player)) {
            return mainHand;
        }

        ItemStack offHand = player.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND);
        if (AppleSkinCompat.canConsume(offHand, player)) {
            return offHand;
        }

        return ItemStack.EMPTY;
    }

    private static boolean hasNourishmentEffect(Player player) {
            #if UPTO_20_1
                // 1.20.1 Forge: RegistryObject<MobEffect>
                // 1.20.1 Fabric: Supplier<MobEffect>
            var nourishmentEffect = ModEffects.NOURISHMENT.get();
            return player.hasEffect(nourishmentEffect);
            #elif NEWER_THAN_20_1
                // 1.21.1 Fabric/NeoForge - Holder<MobEffect>
            var nourishmentEffect = ModEffects.NOURISHMENT;
            return player.hasEffect(nourishmentEffect);
            #else
        return false;
            #endif
    }

    public static boolean isVampire(Player player) {
        #if UPTO_20_1 && FABRIC
            if (PlatformUtil.isModLoaded("bewitchment")) {
                return BewitchmentAPI.isVampire(player, true);
            }
        #endif
            // TODO: More vampire transformation mods here
        return false;
    }

    /**
     * Nine-slice rendering method for asymmetric bars (left padding only)
     */
    private static void drawAsymmetricBarNineSlice(GuiGraphics graphics, ResourceLocation texture, int x, int y, int destWidth, int destHeight, int sourceTextureWidth, int sourceTextureHeight, int leftPadding, int rightPadding, float fillRatio) {
        int filledWidth = (int)(destWidth * fillRatio);
        if (filledWidth <= 0) return;
        
        // Handle case where bar is smaller than left padding
        if (filledWidth <= leftPadding) {
            int leftWidth = filledWidth;
            for (int tileY = 0; tileY < destHeight; tileY += sourceTextureHeight) {
                int drawHeight = Math.min(sourceTextureHeight, destHeight - tileY);
                graphics.blit(texture, x, y + tileY, 0, 0, leftWidth, drawHeight, sourceTextureWidth, sourceTextureHeight);
            }
            return;
        }
        
        // Draw left padding (tile vertically)
        if (leftPadding > 0) {
            int leftWidth = leftPadding;
            for (int tileY = 0; tileY < destHeight; tileY += sourceTextureHeight) {
                int drawHeight = Math.min(sourceTextureHeight, destHeight - tileY);
                graphics.blit(texture, x, y + tileY, 0, 0, leftWidth, drawHeight, sourceTextureWidth, sourceTextureHeight);
            }
        }
        
        // Draw tiled middle (horizontally and vertically)
        int sourceMiddleWidth = sourceTextureWidth - leftPadding - rightPadding;
        int destMiddleWidth = filledWidth - leftPadding - rightPadding;
        
        // Only draw middle if there's space for it
        if (destMiddleWidth > 0 && sourceMiddleWidth > 0) {
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
        }
        
        // Draw right padding (tile vertically) - only if there's enough filled width and right padding exists
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

    /**
     * Nine-slice rendering method with vertical offset for animated bars
     */
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