package dev.muon.dynamic_resource_bars.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.muon.dynamic_resource_bars.DynamicResourceBars;
import dev.muon.dynamic_resource_bars.config.ModConfigManager;
import dev.muon.dynamic_resource_bars.config.ClientConfig;
import dev.muon.dynamic_resource_bars.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import dev.muon.dynamic_resource_bars.compat.AppleSkinCompat;
import net.minecraft.util.Mth; // Import for MathHelper.lerp

#if NEWER_THAN_20_1
import net.minecraft.client.DeltaTracker;
#endif

import vectorwing.farmersdelight.common.registry.ModEffects;
import net.minecraft.resources.ResourceLocation;

public class HealthBarRenderer {

    // --- Dynamic Sizing Constants ---
    // 1 Heart = 2 Health Points. This constant defines how many pixels 1 Health Point represents.
    private static final float PIXELS_PER_HEALTH_POINT = 4.0f; // 4 pixels per health point (e.g., 20 max HP = 80px bar)
    private static final int MIN_BASE_BAR_WIDTH = 40; // Minimum width for the main health bar (e.g., if max health is very low)
    private static final int MAX_BASE_BAR_WIDTH = 400; // Maximum width to prevent bars from becoming too large
    private static final int MAX_ABSORPTION_HEALTH_POINTS = 20; // Default max absorption is 16, but sometimes mods add more. Using a slightly higher default.
    
    // --- Smart Scaling Constants ---
    private static final float SCALING_BASE = 1.5f; // Base for logarithmic scaling
    private static final float SCALING_FACTOR = 0.8f; // Controls how quickly the scaling curve flattens

    // --- 9-Slice Constants for Background and Foreground Textures ---
    // !!! IMPORTANT: These must match the actual pixel dimensions of your textures !!!
    // --- Texture sizes for each element ---
    private static final int BACKGROUND_SOURCE_TEXTURE_WIDTH = 182;
    private static final int BACKGROUND_SOURCE_TEXTURE_HEIGHT = 5;
    private static final int FOREGROUND_SOURCE_TEXTURE_WIDTH = 98;
    private static final int FOREGROUND_SOURCE_TEXTURE_HEIGHT = 30;
    private static final int BAR_SOURCE_TEXTURE_WIDTH = 182;
    private static final int BAR_SOURCE_TEXTURE_HEIGHT = 10;
    private static final int HITBAR_SOURCE_TEXTURE_WIDTH = 182; // You can change this if your hit bar texture is different
    private static final int HITBAR_SOURCE_TEXTURE_HEIGHT = 5;
    private static final int HORIZONTAL_SLICE_PADDING = 22; // User specified 22 pixels for each horizontal end padding
    private static final int BACKGROUND_FOREGROUND_TOTAL_PADDING = HORIZONTAL_SLICE_PADDING * 2; // Total width consumed by both end paddings (44 pixels)

    // --- Nine-slice paddings for each element ---
    private static final int CUSTOM_HEALTH_BAR_BACKGROUND_PADDING = 42; // For background
    private static final int CUSTOM_HEALTH_BAR_FOREGROUND_PADDING = 46; // For foreground
    private static final int CUSTOM_HEALTH_BAR_MAIN_PADDING = 30; // For main bar
    private static final int CUSTOM_HEALTH_BAR_MAIN_SHRINK = 26; // For main bar
    private static final int CUSTOM_HIT_BAR_PADDING = 4; // For hit bar

    private static final float DAMPING_FACTOR = 0.85f; // Controls animation smoothness for *healing* and delayed bar follow
    private static float currentHealthAnimated = -1.0f; // Animated health for the main bar
    private static float currentAbsorptionAnimated = -1.0f; // Animated absorption for the main bar

    private static float delayedHealthCurrent = -1.0f; // Animated health for the hit indicator bar (smoothly updates)
    private static long lastHitTime = 0L; // Timestamp of when damage was *last detected* (initiates a hold)
    private static float healthAtLastHit = -1.0f; // Stores the animated health value *right before* the main bar instantly drops due to damage

    private static float lastHealth = -1; // Used for WHEN_NOT_FULL text behavior
    private static long fullHealthStartTime = 0; // Used for WHEN_NOT_FULL text fade
    private static boolean healthBarSetVisible = true; // Default to visible, used for bar fade
    private static long healthBarDisabledStartTime = 0L; // Used for bar fade timing

    private static float regenGradientFade = 0.0f;
    private static long lastRegenEffectStateChange = 0L;
    private static boolean lastHadRegen = false;
    private static final long REGEN_GRADIENT_FADE_DURATION_MS = 350L;

    // For the new atlas: main bar is top half, overlay is bottom half
    private static final int ATLAS_MAIN_BAR_HEIGHT = BAR_SOURCE_TEXTURE_HEIGHT / 2;
    private static final int ATLAS_OVERLAY_HEIGHT = BAR_SOURCE_TEXTURE_HEIGHT / 2;
    private static final int ATLAS_TOTAL_HEIGHT = BAR_SOURCE_TEXTURE_HEIGHT;

    /**
     * Calculates a smart-scaled width that prevents bars from becoming too large.
     * Uses logarithmic scaling to provide good visual distinction while limiting maximum size.
     */
    private static int calculateSmartScaledWidth(float maxValue, float pixelsPerPoint) {
        // Linear scaling for small values (up to 40 points)
        if (maxValue <= 30.0f) {
            return Math.max(MIN_BASE_BAR_WIDTH, (int)(maxValue * pixelsPerPoint));
        }
        
        // Logarithmic scaling for larger values to prevent excessive growth
        float logValue = (float) Math.log(maxValue / 30.0f) / (float) Math.log(SCALING_BASE);
        float scaledValue = 30.0f + (logValue * SCALING_FACTOR * 30.0f);
        int scaledWidth = (int)(scaledValue * pixelsPerPoint);
        
        // Apply maximum width limit
        return Math.min(MAX_BASE_BAR_WIDTH, Math.max(MIN_BASE_BAR_WIDTH, scaledWidth));
    }

    /**
     * Returns the full width for the health bar and overlays, including absorption if present
     */
    private static int getFullBarWidth(Player player, int absorptionAmount) {
        int baseWidth = calculateSmartScaledWidth(player.getMaxHealth(), PIXELS_PER_HEALTH_POINT);
        int absorptionWidth = (player.getMaxHealth() == 0) ? 0 : (int)(baseWidth * (absorptionAmount / (float)player.getMaxHealth()));
        return baseWidth + absorptionWidth + BACKGROUND_FOREGROUND_TOTAL_PADDING;
    }

    /**
     * Helper to get the dynamic width for the main bar (health only, no absorption, no caps)
     */
    private static int getMainBarWidth(Player player) {
        ClientConfig config = ModConfigManager.getClient();
        int baseWidth = calculateSmartScaledWidth(player.getMaxHealth(), PIXELS_PER_HEALTH_POINT);
        int percent = Math.max(0, Math.min(100, config.healthBarWidthModifier));
        int globalPercent = Math.max(0, Math.min(100, config.globalBarWidthModifier));
        int scaledWidth = Math.round(baseWidth * (percent / 100.0f) * (globalPercent / 100.0f));
        return Math.max(MIN_BASE_BAR_WIDTH, scaledWidth);
    }
    // Helper to get the absorption width (for background/foreground extension)
    private static int getAbsorptionWidth(Player player, int absorptionAmount) {
        int baseWidth = getMainBarWidth(player);
        return (player.getMaxHealth() == 0) ? 0 : (int)(baseWidth * (absorptionAmount / (float)player.getMaxHealth()));
    }

    /**
     * Calculates the dynamic width for the background element, which includes padding around the main bar
     * determined by the 9-slice cap widths.
     */
    private static int getDynamicBackgroundWidth(Player player) {
        return getFullBarWidth(player, 0); // No absorption for this calculation
    }

    /**
     * Calculates the dynamic width for the foreground/overlay element.
     * This is designed to be the same width as the main bar itself, as it's an overlay *on* the bar.
     */
    private static int getDynamicOverlayWidth(Player player) {
        return getFullBarWidth(player, 0); // No absorption for this calculation
    }

    /**
     * Helper to draw a stretched/tiled bar with no cap logic
     */
    private static void drawFullWidthBar(GuiGraphics graphics, ResourceLocation texture, int x, int y, int destWidth, int destHeight, int sourceTextureWidth, int sourceTextureHeight, float fillRatio) {
        int filledWidth = (int)(destWidth * fillRatio);
        if (filledWidth <= 0) return;
        int tiledX = x;
        int remaining = filledWidth;
        while (remaining > 0) {
            int tileWidth = Math.min(sourceTextureWidth, remaining);
            for (int tileY = 0; tileY < destHeight; tileY += sourceTextureHeight) {
                int drawHeight = Math.min(sourceTextureHeight, destHeight - tileY);
                graphics.blit(texture, tiledX, y + tileY, 0, 0, tileWidth, drawHeight, sourceTextureWidth, sourceTextureHeight);
            }
            tiledX += tileWidth;
            remaining -= tileWidth;
        }
    }



    /**
     * Updates the animated health and absorption values according to the new behavior:
     * Main bar: instant snap down on damage, smooth animation up on healing.
     * Delayed bar: holds position after damage, then smoothly follows the main bar.
     * @param player The player entity.
     * @param partialTicks The fraction of the current tick that has passed, for frame-rate independent animation.
     * @param actualHealth The player's current health.
     * @param absorptionAmount The player's current absorption.
     */
    private static void updateAnimatedValues(Player player, float partialTicks, float actualHealth, int absorptionAmount) {
        // --- Initialization and Reset on Major State Change ---
        // These values need to be synced instantly on first run, or when player health becomes effectively zero (e.g., after death/respawn)
        if (currentHealthAnimated < 0.0f || (actualHealth < 0.01f && currentHealthAnimated > 0.01f)) {
            currentHealthAnimated = actualHealth;
            currentAbsorptionAnimated = absorptionAmount;
            delayedHealthCurrent = actualHealth + absorptionAmount;
            healthAtLastHit = actualHealth + absorptionAmount; // Ensure consistent initial state
            lastHitTime = 0L;
        }

        // Capture the animated values from the *previous frame* (before current tick's updates)
        float previousFrameAnimatedHealth = currentHealthAnimated;
        float previousFrameAnimatedAbsorption = currentAbsorptionAnimated;

        // Calculate interpolation factor for smooth animation (primarily for healing/delayed bar follow)
        float interpolationFactor = 1.0f - (float) Math.pow(DAMPING_FACTOR, partialTicks);
        interpolationFactor = Mth.clamp(interpolationFactor, 0.0f, 1.0f);

        // --- Update Main Health Bar (`currentHealthAnimated`, `currentAbsorptionAnimated`) ---
        if (actualHealth < previousFrameAnimatedHealth - 0.01f) {
            // Health decreased significantly (took damage) -> instant snap down
            currentHealthAnimated = actualHealth;
        } else if (actualHealth > previousFrameAnimatedHealth + 0.01f) {
            // Health increased significantly (healing) -> smooth animation up
            currentHealthAnimated = Mth.lerp(interpolationFactor, previousFrameAnimatedHealth, actualHealth);
        } else {
            // Health is very close or same -> snap to actual value to prevent micro-fluctuations
            currentHealthAnimated = actualHealth;
        }

        if (absorptionAmount < previousFrameAnimatedAbsorption - 0.01f) {
            // Absorption decreased significantly -> instant snap down
            currentAbsorptionAnimated = absorptionAmount;
        } else if (absorptionAmount > previousFrameAnimatedAbsorption + 0.01f) {
            // Absorption increased significantly -> smooth animation up
            currentAbsorptionAnimated = Mth.lerp(interpolationFactor, previousFrameAnimatedAbsorption, absorptionAmount);
        } else {
            // Absorption is very close or same -> snap to actual value
            currentAbsorptionAnimated = absorptionAmount;
        }

        // Calculate total health for simpler comparisons (main bar's combined value)
        float actualTotalHealth = actualHealth + absorptionAmount;
        float currentAnimatedTotalHealth = currentHealthAnimated + currentAbsorptionAnimated;
        float previousAnimatedTotalHealth = previousFrameAnimatedHealth + previousFrameAnimatedAbsorption;

        // --- Damage Detection & Hit Indicator State Management ---
        // Trigger `lastHitTime` and update `healthAtLastHit` when actual total health drops
        if (actualTotalHealth < previousAnimatedTotalHealth - 0.01f) {
            // Damage taken: Update `healthAtLastHit` to be the peak before this damage.
            // This is crucial for seamless multi-hits: `healthAtLastHit` represents the highest point
            // from which the *current sequence of damage* is originating, or the highest point the delayed bar has been at.
            healthAtLastHit = Math.max(delayedHealthCurrent, previousAnimatedTotalHealth);
            lastHitTime = System.currentTimeMillis(); // Reset timer for this new damage event
        }
        // IMPORTANT: Healing/no change does NOT reset `healthAtLastHit` here.
        // It only gets reset when the `currentAnimatedTotalHealth` catches up to it.

        // --- Animate Delayed Health Bar (`delayedHealthCurrent`) ---
        float targetForDelayedBarLerp; // The immediate target for the delayed bar's smooth animation

        if (EditModeManager.isEditModeEnabled()) {
            // In edit mode, the delayed bar always mirrors the main bar for visual setup
            targetForDelayedBarLerp = currentAnimatedTotalHealth;
            // Sync all related state variables to ensure consistent behavior in edit mode
            healthAtLastHit = currentAnimatedTotalHealth;
            lastHitTime = System.currentTimeMillis(); // Keep active for visualization
        } else if (currentAnimatedTotalHealth >= healthAtLastHit - 0.01f) {
            // If the main bar has caught up to or surpassed the damage peak (due to healing or animation completion),
            // then the hit indicator should sync up and become inactive.
            targetForDelayedBarLerp = currentAnimatedTotalHealth;
            healthAtLastHit = currentAnimatedTotalHealth; // Collapse the "damage peak"
            lastHitTime = 0L; // Deactivate hit indicator (no hold/animation needed)
        } else { // An active damage event is in progress (hold or animate phase)
            long timeSinceHitRegistered = System.currentTimeMillis() - lastHitTime;

            if (timeSinceHitRegistered <= RenderUtil.DAMAGE_INDICATOR_HOLD_MS) {
                // Hold phase: Target is the `healthAtLastHit` (the peak before this damage sequence)
                targetForDelayedBarLerp = healthAtLastHit;
            } else {
                // Animation phase: Smoothly animate towards `currentAnimatedTotalHealth`.
                // The animation progress is calculated from the start of this phase.
                float animationProgress = (float)(timeSinceHitRegistered - RenderUtil.DAMAGE_INDICATOR_HOLD_MS) / RenderUtil.DAMAGE_INDICATOR_ANIM_DURATION_MS;
                animationProgress = Mth.clamp(animationProgress, 0.0f, 1.0f);

                // The delayed bar smoothly transitions from `healthAtLastHit` towards `currentAnimatedTotalHealth`.
                // If `currentAnimatedTotalHealth` changes mid-animation (e.g., another hit),
                // the `Mth.lerp` will dynamically re-target its trajectory.
                targetForDelayedBarLerp = Mth.lerp(animationProgress, healthAtLastHit, currentAnimatedTotalHealth);

                // If the animation is virtually complete and has reached the main bar's current position, deactivate.
                if (Math.abs(delayedHealthCurrent - currentAnimatedTotalHealth) < 0.01f) {
                    lastHitTime = 0L; // Animation is essentially done and caught up
                    healthAtLastHit = currentAnimatedTotalHealth; // Sync peak for future hits
                }
            }
        }

        // Perform the smooth interpolation for `delayedHealthCurrent` towards its calculated `targetForDelayedBarLerp`.
        delayedHealthCurrent = Mth.lerp(interpolationFactor, delayedHealthCurrent, targetForDelayedBarLerp);

        // Final Clamp for `delayedHealthCurrent`:
        // It should never go below the current main bar's animated total health (ensuring the damage gap is visible).
        // It should not exceed the max possible health + absorption (approx. 20 absorption default).
        delayedHealthCurrent = Mth.clamp(delayedHealthCurrent, currentAnimatedTotalHealth, player.getMaxHealth() + MAX_ABSORPTION_HEALTH_POINTS);
    }

    /**
     * Calculates the overall bounding rectangle for the health bar and its sub-elements,
     * based on the configured anchor and total offsets.
     */
    public static ScreenRect getScreenRect(Player player, int absorptionAmount) {
        if (player == null) return new ScreenRect(0, 0, 0, 0);
        ClientConfig config = ModConfigManager.getClient();
        int width = getMainBarWidth(player) + getAbsorptionWidth(player, absorptionAmount) + CUSTOM_HEALTH_BAR_MAIN_PADDING * 2;
        int height = config.healthBackgroundHeight;
        ScreenRect parentBox = new ScreenRect(0, 0, width, height);
        Position anchorPos = HUDPositioning.alignBoundingBoxToAnchor(parentBox, config.healthBarAnchor);
        Position finalPos = anchorPos.offset(config.healthTotalXOffset, config.healthTotalYOffset);
        return new ScreenRect(finalPos.x(), finalPos.y(), width, height);
    }

    /**
     * Returns the bounding rectangle for a specific sub-element (e.g., background, main bar, text).
     * Dimensions are taken from dynamic calculations or client configuration (for height and offsets).
     */
    public static ScreenRect getSubElementRect(SubElementType type, Player player, int absorptionAmount) {
        ScreenRect complexRect = getScreenRect(player, absorptionAmount);
        if (complexRect == null || (complexRect.width() == 0 && complexRect.height() == 0))
            return new ScreenRect(0, 0, 0, 0);
        ClientConfig config = ModConfigManager.getClient();
        int x = complexRect.x();
        int y = complexRect.y();
        int baseWidth = getMainBarWidth(player);
        int absorptionWidth = getAbsorptionWidth(player, absorptionAmount);
        switch (type) {
            case BACKGROUND:
                return new ScreenRect(x + config.healthBackgroundXOffset, y + config.healthBackgroundYOffset, baseWidth + absorptionWidth + CUSTOM_HEALTH_BAR_MAIN_SHRINK * 2, config.healthBackgroundHeight);
            case BAR_MAIN:
                return new ScreenRect(x + config.healthBarXOffset + CUSTOM_HEALTH_BAR_BACKGROUND_PADDING, y + config.healthBarYOffset, baseWidth, config.healthBarHeight);
            case FOREGROUND_DETAIL:
                return new ScreenRect(x + config.healthOverlayXOffset, y + config.healthOverlayYOffset, baseWidth + absorptionWidth + CUSTOM_HEALTH_BAR_MAIN_SHRINK * 2, config.healthOverlayHeight);
            case TEXT:
                return new ScreenRect(x + config.healthTextXOffset + CUSTOM_HEALTH_BAR_BACKGROUND_PADDING, y + config.healthTextYOffset, baseWidth, config.healthBarHeight);
            case ABSORPTION_TEXT:
                return new ScreenRect(x + config.healthAbsorptionTextXOffset + CUSTOM_HEALTH_BAR_BACKGROUND_PADDING, y + config.healthAbsorptionTextYOffset, 50, config.healthBarHeight);
            case TRAILING_ICON:
                // Fixed position at the end of the bar progress, using bar height
                float healthRatio = (player.getMaxHealth() == 0) ? 0 : (currentHealthAnimated / player.getMaxHealth());
                healthRatio = Mth.clamp(healthRatio, 0.0f, 1.0f);
                int iconX = x + config.healthBarXOffset + CUSTOM_HEALTH_BAR_BACKGROUND_PADDING + (int)(baseWidth * healthRatio);
                int iconY = y + config.healthBarYOffset;
                return new ScreenRect(iconX, iconY, BACKGROUND_SOURCE_TEXTURE_HEIGHT, BACKGROUND_SOURCE_TEXTURE_HEIGHT);
            default:
                return new ScreenRect(0, 0, 0, 0);
        }
    }

    public static void render(GuiGraphics graphics, Player player, float maxHealth, float actualHealth, int absorptionAmount,
            #if NEWER_THAN_20_1 DeltaTracker deltaTracker #else float partialTicks #endif) {

        ClientConfig config = ModConfigManager.getClient();
        float currentPartialTicks = #if NEWER_THAN_20_1 deltaTracker.getGameTimeDeltaTicks() #else partialTicks #endif;

        // Update animated values using partialTicks for smooth animation
        updateAnimatedValues(player, currentPartialTicks, actualHealth, absorptionAmount);

        // Override hideWhenFull if player has absorption or is in edit mode
        boolean shouldFade = config.fadeHealthWhenFull && actualHealth >= maxHealth && absorptionAmount == 0;
        setHealthBarVisibility(!shouldFade || EditModeManager.isEditModeEnabled());

        // Don't render if fully faded and not in edit mode
        if (!isHealthBarVisible() && !EditModeManager.isEditModeEnabled() && (System.currentTimeMillis() - healthBarDisabledStartTime) > RenderUtil.BAR_FADEOUT_DURATION) {
            return;
        }

        if (!Minecraft.getInstance().gameMode.canHurtPlayer() && !EditModeManager.isEditModeEnabled()) {
            return;
        }

        float currentAlphaForRender = getHealthBarAlpha();
        if (EditModeManager.isEditModeEnabled() && !isHealthBarVisible()) {
            currentAlphaForRender = 1.0f; // Show fully if in edit mode, even if normally faded
        }

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, currentAlphaForRender);

        ScreenRect complexRect = getScreenRect(player, absorptionAmount);

        int backgroundHeight = config.healthBackgroundHeight;
        int animationCycles = config.healthBarAnimationCycles;
        int frameHeight = config.healthBarFrameHeight;

        // Render Background (using 9-slice)
        if (config.enableHealthBackground) {
            ScreenRect bgRect = getSubElementRect(SubElementType.BACKGROUND, player, absorptionAmount);
            RenderUtil.drawHorizontalNineSlice(graphics, DynamicResourceBars.loc("textures/gui/health_background.png"),
                    bgRect.x(), bgRect.y(), bgRect.width(), bgRect.height(),
                    BACKGROUND_SOURCE_TEXTURE_WIDTH, BACKGROUND_SOURCE_TEXTURE_HEIGHT,
                    CUSTOM_HEALTH_BAR_BACKGROUND_PADDING, CUSTOM_HEALTH_BAR_BACKGROUND_PADDING,
                    currentAlphaForRender // Pass current bar's alpha
            );
        }

        // Calculate animation offset for the bar texture
        float ticks = player.tickCount + currentPartialTicks;
        int animOffset = (int) ((ticks / 3) % animationCycles) * frameHeight;

        ScreenRect mainBarRect = getSubElementRect(SubElementType.BAR_MAIN, player, absorptionAmount);
        boolean isRightAnchored = config.healthBarAnchor == AnchorPoint.TOP_RIGHT || config.healthBarAnchor == AnchorPoint.CENTER_RIGHT || config.healthBarAnchor == AnchorPoint.BOTTOM_RIGHT;

        // --- Calculate a consistent total maximum for scaling all health-related bars ---
        // This includes player's max health PLUS the maximum possible absorption amount (default 20 absorption hearts)
        float maxPossibleTotalHealth = player.getMaxHealth() + MAX_ABSORPTION_HEALTH_POINTS;
        float currentAnimatedTotalHealth = currentHealthAnimated + currentAbsorptionAnimated;

        // --- Render Delayed Health Bar (Hit Indicator) ---
        renderDelayedBar(graphics, player, maxPossibleTotalHealth, currentAnimatedTotalHealth, delayedHealthCurrent, mainBarRect.x(), mainBarRect.y(), mainBarRect.width(), mainBarRect.height(), animOffset, isRightAnchored);

        // --- Render Main Health Bar ---
        renderBaseBar(graphics, player, maxPossibleTotalHealth, currentHealthAnimated, mainBarRect.x(), mainBarRect.y(), mainBarRect.width(), mainBarRect.height(), 0, 0, animOffset, isRightAnchored);
        
        // --- Render Gradient Overlay ---
        renderGradientOverlay(graphics, player, mainBarRect, currentAlphaForRender);

        // --- Absorption Overlay (Golden Hearts) ---
        if (absorptionAmount > 0) {
            renderAbsorptionOverlay(graphics, player, currentHealthAnimated, currentAbsorptionAnimated, mainBarRect, animOffset, isRightAnchored);
        }

        // --- AppleSkin Health Restoration Overlay ---
        if (PlatformUtil.isModLoaded("appleskin") && config.enableHealthRestorationOverlay) {
            ItemStack heldFood = getHeldFood(player);
            if (!heldFood.isEmpty()) {
                renderHealthRestoredOverlay(graphics, player, heldFood, currentHealthAnimated, maxPossibleTotalHealth, mainBarRect, animOffset, isRightAnchored);
            }
        }

        // --- Custom Health Gradient Overlay ---
        boolean hasRegen = player.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
        long now = System.currentTimeMillis();
        if (hasRegen != lastHadRegen) {
            lastRegenEffectStateChange = now;
            lastHadRegen = hasRegen;
        }
        float fadeTarget = hasRegen ? 1.0f : 0.0f;
        long fadeElapsed = now - lastRegenEffectStateChange;
        if (fadeElapsed < REGEN_GRADIENT_FADE_DURATION_MS) {
            float fadeProgress = fadeElapsed / (float) REGEN_GRADIENT_FADE_DURATION_MS;
            regenGradientFade = hasRegen ? fadeProgress : 1.0f - fadeProgress;
            regenGradientFade = Mth.clamp(regenGradientFade, 0.0f, 1.0f);
        } else {
            regenGradientFade = fadeTarget;
        }
        // (health_gradient_overlay.png overlay removed)

        // --- Render Trailing Icon ---
        if (config.enableHealthTrailingIcon) {
            renderTrailingIcon(graphics, player, currentHealthAnimated, mainBarRect, currentAlphaForRender);
        }

        // Render other bar overlays (absorption, regen, comfort)
        renderBarOverlays(graphics, player, (int) currentAbsorptionAnimated, mainBarRect.x(), mainBarRect.y(), mainBarRect.width(), mainBarRect.height(), 0, 0);

        // Render foreground overlay (using 9-slice)
        if (config.enableHealthForeground) {
            ScreenRect fgRect = getSubElementRect(SubElementType.FOREGROUND_DETAIL, player, absorptionAmount);
            RenderUtil.drawHorizontalNineSlice(graphics, DynamicResourceBars.loc("textures/gui/health_foreground.png"),
                    fgRect.x(), fgRect.y(), fgRect.width(), fgRect.height(),
                    FOREGROUND_SOURCE_TEXTURE_WIDTH, FOREGROUND_SOURCE_TEXTURE_HEIGHT,
                    CUSTOM_HEALTH_BAR_FOREGROUND_PADDING, CUSTOM_HEALTH_BAR_FOREGROUND_PADDING,
                    currentAlphaForRender // Pass current bar's alpha
            );
        }

        // Render background overlays (hardcore, wetness) - these are typically full-size overlays on the background, not sliced
        renderBackgroundOverlays(graphics, player, complexRect.x(), complexRect.y(), getDynamicBackgroundWidth(player), backgroundHeight);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f); // Reset shader color to full white after rendering all overlays/backgrounds

        // Update text rendering to use draggable position
        if (shouldRenderHealthText(actualHealth, maxHealth, player)) { // Use actual health for text visibility logic
            ScreenRect textRect = getSubElementRect(SubElementType.TEXT, player, absorptionAmount);
            int textX = textRect.x() + (textRect.width() / 2);
            int textY = textRect.y() + (textRect.height() - Minecraft.getInstance().font.lineHeight) / 2; // Center text vertically

            int color = getHealthTextColor(actualHealth, maxHealth);
            HorizontalAlignment alignment = config.healthTextAlign;

            int baseX = textRect.x();
            if (alignment == HorizontalAlignment.CENTER) {
                baseX = textX;
            } else if (alignment == HorizontalAlignment.RIGHT) {
                baseX = textRect.x() + textRect.width();
            }

            // Remove: RenderUtil.renderText((int)actualHealth, (int)maxHealth, graphics, baseX, textY, color, alignment);
        }

        // Render Absorption Text
        if (absorptionAmount > 0 || EditModeManager.isEditModeEnabled()) {
            // Use currentAbsorptionAnimated for display if animating, otherwise target.
            // For editing mode, use a dummy value if actual absorption is 0
            String absorptionText = "+" + (EditModeManager.isEditModeEnabled() && absorptionAmount == 0 ? "8" : (int)currentAbsorptionAnimated);

            ScreenRect absorptionRect = getSubElementRect(SubElementType.ABSORPTION_TEXT, player, absorptionAmount);
            int absorptionTextX = absorptionRect.x();
            int absorptionTextY = absorptionRect.y() + (absorptionRect.height() - Minecraft.getInstance().font.lineHeight) / 2; // Center vertically

            int baseAbsorptionColor = config.healthTextColor & 0xFFFFFF; // Use global text color for absorption by default
            int absorptionAlpha = (int) (config.healthTextOpacity * currentAlphaForRender);
            absorptionAlpha = Mth.clamp(absorptionAlpha, 10, 255); // Ensure visibility
            int absorptionFinalColor = (absorptionAlpha << 24) | baseAbsorptionColor;
        }

        // Add focus mode outline rendering
        if (EditModeManager.isEditModeEnabled()) {
            DraggableElement currentBarType = DraggableElement.HEALTH_BAR;
            if (EditModeManager.getFocusedElement() == currentBarType) {
                int focusedBorderColor = 0xA0FFFF00; // Yellow for focused element
                ScreenRect bgRect = getSubElementRect(SubElementType.BACKGROUND, player, absorptionAmount);
                graphics.renderOutline(bgRect.x() - 1, bgRect.y() - 1, bgRect.width() + 2, bgRect.height() + 2, focusedBorderColor);

                ScreenRect barRect = getSubElementRect(SubElementType.BAR_MAIN, player, absorptionAmount);
                graphics.renderOutline(barRect.x() - 1, barRect.y() - 1, barRect.width() + 2, barRect.height() + 2, 0xA000FF00); // Green for main bar

                if (config.enableHealthForeground) { // Check config for foreground enabled
                    ScreenRect fgRect = getSubElementRect(SubElementType.FOREGROUND_DETAIL, player, absorptionAmount);
                    graphics.renderOutline(fgRect.x() - 1, fgRect.y() - 1, fgRect.width() + 2, fgRect.height() + 2, 0xA0FF00FF); // Magenta for foreground
                }

                // Outline for absorption text
                ScreenRect absorptionRect = getSubElementRect(SubElementType.ABSORPTION_TEXT, player, absorptionAmount);
                if (absorptionRect != null && absorptionRect.width() > 0 && absorptionRect.height() > 0) {
                    graphics.renderOutline(absorptionRect.x() - 1, absorptionRect.y() - 1, absorptionRect.width() + 2, absorptionRect.height() + 2, 0x60FFFFFF); // Semi-transparent white
                }

                // Outline for trailing icon
                if (config.enableHealthTrailingIcon) {
                    ScreenRect trailingIconRect = getSubElementRect(SubElementType.TRAILING_ICON, player, absorptionAmount);
                    if (trailingIconRect != null && trailingIconRect.width() > 0 && trailingIconRect.height() > 0) {
                        graphics.renderOutline(trailingIconRect.x() - 1, trailingIconRect.y() - 1, trailingIconRect.width() + 2, trailingIconRect.height() + 2, 0x60FFA500); // Semi-transparent orange
                    }
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
     * Renders the base health bar (filled portion)
     * @param maxTotalHealth The maximum value to which the bar should visually scale (e.g., player max health + max absorption).
     */
    private static void renderBaseBar(GuiGraphics graphics, Player player, float maxTotalHealth, float healthToDisplay, int barAbsX, int barAbsY, int barAbsWidth, int barAbsHeight, int barXOffsetWithinTexture, int barYOffsetWithinTexture, int animOffset, boolean isRightAnchored) {
        BarType barType = BarType.fromPlayerState(player);
        float fillRatio = (player.getMaxHealth() == 0) ? 0.0f : (healthToDisplay / player.getMaxHealth());
        fillRatio = Mth.clamp(fillRatio, 0.0f, 1.0f);
        FillDirection fillDirection = ModConfigManager.getClient().healthFillDirection;
        if (fillDirection == FillDirection.VERTICAL) {
            int filledHeight = (int) (barAbsHeight * fillRatio);
            if (healthToDisplay > 0 && filledHeight == 0) filledHeight = 1;
            if (filledHeight > 0) {
                int barRenderY = barAbsY + (barAbsHeight - filledHeight);
                int textureVOffset = ATLAS_MAIN_BAR_HEIGHT - filledHeight;
                graphics.blit(
                        DynamicResourceBars.loc("textures/gui/" + barType.getTexture() + ".png"),
                        barAbsX, barRenderY,
                        0, textureVOffset,
                        barAbsWidth, filledHeight,
                        BAR_SOURCE_TEXTURE_WIDTH, ATLAS_MAIN_BAR_HEIGHT
                );
            }
        } else {
            drawAsymmetricBarNineSliceWithV(
                graphics,
                DynamicResourceBars.loc("textures/gui/" + barType.getTexture() + ".png"),
                barAbsX, barAbsY,
                barAbsWidth, barAbsHeight,
                BAR_SOURCE_TEXTURE_WIDTH, ATLAS_MAIN_BAR_HEIGHT,
                CUSTOM_HEALTH_BAR_MAIN_PADDING, // Left cap width (now configurable)
                0,  // Right cap width (no right padding)
                fillRatio,
                0 // v offset for main bar
            );
        }
    }

    /**
     * Renders the trailing icon that follows the health bar progress.
     * The icon is positioned at the end of the current health fill and moves with the bar.
     * @param graphics GuiGraphics instance.
     * @param player The player entity.
     * @param healthToDisplay The exact same health value used by the main bar for fill ratio calculation.
     * @param mainBarRect The main bar's rectangle for positioning calculations.
     * @param alpha The alpha value for rendering.
     */
    private static void renderTrailingIcon(GuiGraphics graphics, Player player, float healthToDisplay, ScreenRect mainBarRect, float alpha) {
        // Calculate the position using the exact same fill ratio calculation as the main bar
        float fillRatio = (player.getMaxHealth() == 0) ? 0.0f : (healthToDisplay / player.getMaxHealth());
        fillRatio = Mth.clamp(fillRatio, 0.0f, 1.0f);
        
        int iconX = mainBarRect.x() + (int)(mainBarRect.width() * fillRatio);
        int iconY = mainBarRect.y();
        
        // Apply alpha blending
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        
        // Render the trailing icon texture with fixed bar height
        graphics.blit(
            DynamicResourceBars.loc("textures/gui/health_trailing_icon.png"),
            iconX, iconY,
            0, 0,
            BACKGROUND_SOURCE_TEXTURE_HEIGHT, BACKGROUND_SOURCE_TEXTURE_HEIGHT,
            BACKGROUND_SOURCE_TEXTURE_HEIGHT, BACKGROUND_SOURCE_TEXTURE_HEIGHT
        );
        
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    /**
     * Renders the hit indicator (delayed) health bar.
     * It uses a distinct color tint and is is drawn underneath the main bar.
     * Its opacity is constant when visible, and it disappears when the gap is closed.
     * @param graphics GuiGraphics instance.
     * @param player The player entity.
     * @param maxTotalHealth The maximum value to which the bar should visually scale (e.g., player max health + max absorption).
     * @param currentAnimatedTotalHealth The main bar's current animated total health (for comparison).
     * @param healthToDisplayForDelayedBar The health value to display for the delayed bar.
     * @param barAbsX Absolute X position of the main bar.
     * @param barAbsY Absolute Y position of the main bar.
     * @param barAbsWidth Width of the main bar.
     * @param barAbsHeight Height of the main bar.
     * @param animOffset Animation texture offset.
     * @param isRightAnchored True if the bar is right-anchored.
     */
    private static void renderDelayedBar(GuiGraphics graphics, Player player, float maxTotalHealth, float currentAnimatedTotalHealth, float healthToDisplayForDelayedBar, int barAbsX, int barAbsY, int barAbsWidth, int barAbsHeight, int animOffset, boolean isRightAnchored) {
        BarType barType = BarType.fromPlayerState(player);
        ClientConfig config = ModConfigManager.getClient();
        float fillRatio = (player.getMaxHealth() == 0) ? 0.0f : (healthToDisplayForDelayedBar / player.getMaxHealth());
        fillRatio = Mth.clamp(fillRatio, 0.0f, 1.0f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float finalRenderAlpha = 1.0f;
        if (EditModeManager.isEditModeEnabled()) {
            finalRenderAlpha = 1.0f;
        } else {
            if (healthToDisplayForDelayedBar <= currentAnimatedTotalHealth + 0.01f) {
                finalRenderAlpha = 0.0f;
            }
        }
        finalRenderAlpha *= getHealthBarAlpha();
        finalRenderAlpha = Mth.clamp(finalRenderAlpha, 0.0f, 1.0f);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, finalRenderAlpha);
        FillDirection fillDirection = config.healthFillDirection;
        if (fillDirection == FillDirection.VERTICAL) {
            int filledHeight = (int) (barAbsHeight * fillRatio);
            if (healthToDisplayForDelayedBar > 0 && filledHeight == 0) filledHeight = 1;
            if (filledHeight > 0) {
                int barRenderY = barAbsY + (barAbsHeight - filledHeight);
                int textureVOffset = animOffset + (barAbsHeight - filledHeight);
                graphics.blit(
                        DynamicResourceBars.loc("textures/gui/" + BarType.HIT.getTexture() + ".png"),
                        barAbsX, barRenderY,
                        0, textureVOffset,
                        barAbsWidth, filledHeight,
                        HITBAR_SOURCE_TEXTURE_WIDTH, HITBAR_SOURCE_TEXTURE_HEIGHT
                );
            }
        } else {
            drawBarNineSlice(
                graphics,
                DynamicResourceBars.loc("textures/gui/" + BarType.HIT.getTexture() + ".png"),
                barAbsX, barAbsY,
                barAbsWidth, barAbsHeight,
                HITBAR_SOURCE_TEXTURE_WIDTH, HITBAR_SOURCE_TEXTURE_HEIGHT,
                CUSTOM_HIT_BAR_PADDING,
                fillRatio
            );
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }


    /**
     * Renders overlays that appear on top of the health bar itself (e.g., comfort effect).
     */
    private static void renderBarOverlays(GuiGraphics graphics, Player player, int absorptionAmount, int barAbsX, int barAbsY, int barAbsWidth, int barAbsHeight, int barXOffset, int barYOffset) {
        // All overlays have been moved to their specific render methods
        // This method is now empty as requested by user
    }

    /**
     * Renders overlays that appear relative to the overall background.
     * Note: All unused overlays have been removed as requested by user.
     */
    private static void renderBackgroundOverlays(GuiGraphics graphics, Player player, int complexX, int complexY, int backgroundWidth, int backgroundHeight) {
        // All unused overlays have been removed
        // health_foreground.png is now drawn directly in render(), not here.
    }

    /**
     * Checks if the player is in a "frozen" state (vanilla only).
     * @param player The player entity.
     * @return True if frozen, false otherwise.
     */
    private static boolean isFrozen(Player player) {
        return player.isFullyFrozen();
    }

    /**
     * Calculates the color for the health text, including alpha for fading.
     */
    private static int getHealthTextColor(float currentHealth, float maxHealth) {
        TextBehavior behavior = ModConfigManager.getClient().showHealthText;
        ClientConfig config = ModConfigManager.getClient();

        int baseColor = config.healthTextColor & 0xFFFFFF; // Use configured text color
        int alpha = config.healthTextOpacity;

        // Apply fading logic based on text behavior
        if (behavior == TextBehavior.WHEN_NOT_FULL && currentHealth >= maxHealth) {
            long timeSinceFull = System.currentTimeMillis() - fullHealthStartTime;
            alpha = (int) (alpha * (RenderUtil.calculateTextAlpha(timeSinceFull) / (float) RenderUtil.BASE_TEXT_ALPHA));
        }

        alpha = (int) (alpha * getHealthBarAlpha()); // Modulate text alpha with bar's overall alpha
        alpha = Mth.clamp(alpha, 10, 255); // Clamp alpha to ensure visibility

        return (alpha << 24) | baseColor;
    }

    /**
     * Determines if the health text should be rendered based on configuration and player state.
     */
    private static boolean shouldRenderHealthText(float currentHealth, float maxHealth, Player player) {
        TextBehavior behavior = ModConfigManager.getClient().showHealthText;

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
        boolean isFull = currentHealth >= maxHealth;
        if (isFull) {
            if (lastHealth < maxHealth || lastHealth == -1) { // Just became full or first check
                fullHealthStartTime = System.currentTimeMillis(); // Reset timer
            }
            lastHealth = currentHealth;
            // Show for a short duration after becoming full
            return (System.currentTimeMillis() - fullHealthStartTime) < RenderUtil.TEXT_DISPLAY_DURATION;
        } else {
            lastHealth = currentHealth;
            return true; // Not full, so show
        }
    }

    /**
     * Controls the visibility state of the health bar for fading purposes.
     * @param visible True if the bar should currently be considered "visible" (e.g., health not full).
     */
    private static void setHealthBarVisibility(boolean visible) {
        if (healthBarSetVisible != visible) {
            if (!visible) {
                healthBarDisabledStartTime = System.currentTimeMillis(); // Start fade timer
            }
            healthBarSetVisible = visible;
        }
    }

    /**
     * Checks if the health bar is currently considered visible (not fully faded out).
     */
    private static boolean isHealthBarVisible() {
        return healthBarSetVisible;
    }

    /**
     * Calculates the current alpha value for the health bar, for fading in/out effects.
     */
    private static float getHealthBarAlpha() {
        if (isHealthBarVisible()) {
            return 1.0f; // Fully opaque if visible
        }
        long timeSinceDisabled = System.currentTimeMillis() - healthBarDisabledStartTime;
        if (timeSinceDisabled >= RenderUtil.BAR_FADEOUT_DURATION) {
            return 0.0f; // Fully transparent if fade duration passed
        }
        // Calculate fading alpha
        return Mth.clamp(1.0f - (timeSinceDisabled / (float) RenderUtil.BAR_FADEOUT_DURATION), 0.0f, 1.0f);
    }

    /**
     * Gets the ItemStack that the player is holding (main or off-hand) if it's a food item AppleSkin can interpret.
     */
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

    /**
     * Renders a gradient overlay on the health bar for visual enhancement.
     */
    private static void renderGradientOverlay(GuiGraphics graphics, Player player, ScreenRect barRect, float alpha) {
        int gradientWidth = 64; // Gradient extends 64 pixels from each side
        int barWidth = barRect.width();
        int barHeight = barRect.height();
        
        if (barWidth <= 0 || barHeight <= 0) return;
        
        // Calculate the current health fill ratio
        float healthRatio = (player.getMaxHealth() == 0) ? 0 : (currentHealthAnimated / player.getMaxHealth());
        healthRatio = Mth.clamp(healthRatio, 0.0f, 1.0f);
        int filledWidth = (int)(barWidth * healthRatio);
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        // Left side gradient removed - now handled by nine-slice rendering of main bar
        
        // Overlay: use the bottom half of the main bar texture as a right-side overlay
        if (filledWidth > 0) {
            ResourceLocation barTexture = DynamicResourceBars.loc("textures/gui/" + BarType.fromPlayerState(player).getTexture() + ".png");
            int overlayWidth = Math.min(CUSTOM_HEALTH_BAR_MAIN_PADDING, filledWidth); // Use the rightmost N pixels
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
     * Renders the absorption overlay using proper nine-slice rendering with a dedicated absorption texture.
     */
    private static void renderAbsorptionOverlay(GuiGraphics graphics, Player player, float currentHealth, float currentAbsorption, ScreenRect barRect, int animOffset, boolean isRightAnchored) {
        if (currentAbsorption <= 0) return;

        float healthRatio = (player.getMaxHealth() == 0) ? 0 : (currentHealth / player.getMaxHealth());
        float absorptionRatio = (player.getMaxHealth() == 0) ? 0 : (currentAbsorption / player.getMaxHealth());
        
        // Calculate the absorption bar dimensions
        int absorptionBarWidth = (int) (barRect.width() * absorptionRatio);
        if (absorptionBarWidth <= 0) return;

        // Calculate starting position for absorption bar
        int absorptionStartX = barRect.x() + (int) (barRect.width() * healthRatio);
        int absorptionEndX = absorptionStartX + absorptionBarWidth;

        // Use dedicated absorption texture instead of main health bar texture
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f); // No tint needed since we're using a dedicated texture

        FillDirection fillDirection = ModConfigManager.getClient().healthFillDirection;

        if (fillDirection == FillDirection.VERTICAL) {
            // For vertical bars, absorption appears as a separate section
            int absorptionHeight = (int) (barRect.height() * absorptionRatio);
            if (absorptionHeight > 0) {
                int yPos = barRect.y() + (barRect.height() - absorptionHeight);
                graphics.blit(
                    DynamicResourceBars.loc("textures/gui/absorption_bar.png"),
                    barRect.x(), yPos,
                    0, animOffset,
                    barRect.width(), absorptionHeight,
                    BAR_SOURCE_TEXTURE_WIDTH, BAR_SOURCE_TEXTURE_HEIGHT
                );
            }
        } else {
            // For horizontal bars, use nine-slice rendering for absorption
            drawAsymmetricBarNineSlice(
                graphics,
                DynamicResourceBars.loc("textures/gui/absorption_bar.png"),
                absorptionStartX, barRect.y(),
                absorptionBarWidth, barRect.height(),
                BAR_SOURCE_TEXTURE_WIDTH, BAR_SOURCE_TEXTURE_HEIGHT,
                64, // Left cap width
                0,  // Right cap width
                1.0f // Full fill for absorption
            );
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    /**
     * Renders an overlay indicating estimated health restoration from held food (AppleSkin integration).
     * Uses a custom texture for the restoration overlay with enhanced visual effects and smooth alpha fade.
     * Positioned above all other overlays for maximum visibility.
     * Rendered 2 pixels narrower than the main health bar for better visual distinction.
     */
    private static void renderHealthRestoredOverlay(GuiGraphics graphics, Player player, ItemStack heldFood, float currentHealth, float maxTotalHealth, ScreenRect barRect, int animOffset, boolean isRightAnchored) {
        if (!PlatformUtil.isModLoaded("appleskin")) {
            return;
        }

        float healthRestoration = AppleSkinCompat.getEstimatedHealthRestoration(heldFood, player);
        if (healthRestoration <= 0 || currentHealth >= player.getMaxHealth()) {
            return;
        }

        float restoredHealth = Mth.clamp(currentHealth + healthRestoration, 0, player.getMaxHealth());
        float healthRatio = (player.getMaxHealth() == 0) ? 0 : (currentHealth / player.getMaxHealth());
        float restoredRatio = (player.getMaxHealth() == 0) ? 0 : (restoredHealth / player.getMaxHealth());

        // Use full bar height and y position
        int currentWidth = (int) (barRect.width() * healthRatio);
        int restoredWidth = (int) (barRect.width() * restoredRatio);
        int overlayWidth = restoredWidth - currentWidth;
        int overlayHeight = barRect.height();
        int overlayY = barRect.y();

        if (overlayWidth <= 0 || overlayHeight <= 0) return;

        // Enhanced visual effects for better visibility
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        // Use a pulsing effect for alpha only, no color tint
        float baseAlpha = 0.8f; // Higher base alpha for better visibility
        float pulseIntensity = 0.4f; // Stronger pulse intensity
        float flashAlpha = baseAlpha + (TickHandler.getOverlayFlashAlpha() * pulseIntensity);
        flashAlpha = Mth.clamp(flashAlpha, 0.5f, 1.0f); // Ensure good visibility range
        
        // No color tint, just alpha
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, flashAlpha);

        FillDirection fillDirection = ModConfigManager.getClient().healthFillDirection;

        if (fillDirection == FillDirection.VERTICAL) {
            int currentHeight = (int) (overlayHeight * healthRatio);
            int restoredHeight = (int) (overlayHeight * restoredRatio);
            int overlayHeightVertical = restoredHeight - currentHeight;

            if (overlayHeightVertical > 0) {
                int yPos = overlayY + (overlayHeight - restoredHeight);
                graphics.blit(
                    DynamicResourceBars.loc("textures/gui/health_restoration_overlay.png"),
                    barRect.x(), yPos,
                    0, animOffset,
                    barRect.width(), overlayHeightVertical,
                    BAR_SOURCE_TEXTURE_WIDTH, BAR_SOURCE_TEXTURE_HEIGHT
                );
            }
        } else {
            // For horizontal bars, render with smooth alpha fade towards the end
            renderHealthRestorationWithAlphaFade(graphics, barRect, currentWidth, overlayWidth, overlayY, overlayHeight, flashAlpha);
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    /**
     * Renders the health restoration overlay with a smooth alpha fade towards the end.
     * This creates a more natural and smooth appearance for the restoration indicator.
     * Uses the narrower height (2 pixels less than main bar).
     */
    private static void renderHealthRestorationWithAlphaFade(GuiGraphics graphics, ScreenRect barRect, int currentWidth, int overlayWidth, int overlayY, int overlayHeight, float baseAlpha) {
        if (overlayWidth <= 0) return;

        int overlayStartX = barRect.x() + currentWidth;
        
        // Calculate blinking animation using the tick-based system
        float blinkAlpha = 0.3f + (TickHandler.getOverlayFlashAlpha() * 1.4f); // Oscillate between 0.3 and 1.0 alpha
        
        // Apply blinking animation to base alpha
        float animatedBaseAlpha = baseAlpha * blinkAlpha;
        
        // Apply alpha fade across the entire overlay width
        for (int i = 0; i < overlayWidth; i++) {
            float fadeProgress = (float) i / overlayWidth;
            float currentAlpha = animatedBaseAlpha * (1.0f - fadeProgress * fadeProgress); // Quadratic fade for smoother transition
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, currentAlpha);
            drawAsymmetricBarNineSlice(
                graphics,
                DynamicResourceBars.loc("textures/gui/health_restoration_overlay.png"),
                overlayStartX + i, overlayY,
                1, overlayHeight, // Render 1 pixel at a time for smooth fade
                BAR_SOURCE_TEXTURE_WIDTH, BAR_SOURCE_TEXTURE_HEIGHT,
                0, // No left cap for single pixel
                0, // No right cap for single pixel
                1.0f // Full fill
            );
        }
    }

    /**
     * Safely checks for the Farmer's Delight "Comfort" effect via reflection.
     */


    /**
     * Enum to define different health bar textures based on player's status effects.
     */
    private enum BarType {
        NORMAL("health_bar"),
        POISON("health_bar_poisoned"),
        WITHER("health_bar_withered"),
        FROZEN("health_bar_frozen"),
        SCORCHED("health_bar_scorched"),
        HIT("hit_bar"); // New hit bar type

        private final String texture;

        BarType(String texture) {
            this.texture = texture;
        }

        public String getTexture() {
            return texture;
        }

        public static BarType fromPlayerState(Player player) {
            if (player.hasEffect(MobEffects.POISON)) return POISON;
            if (player.hasEffect(MobEffects.WITHER)) return WITHER;
            if (isFrozen(player)) return FROZEN;
            // Remove isScorched (Thermoo) logic
            return NORMAL;
        }
    }

    // Helper to get cap width for background and foreground
    private static int getBackgroundPadding() {
        return HORIZONTAL_SLICE_PADDING;
    }
    private static int getForegroundPadding() {
        return HORIZONTAL_SLICE_PADDING;
    }

    // Helper to draw a nine-slice bar for the main bar and hit bar, stretching vertically
    private static void drawBarNineSlice(GuiGraphics graphics, ResourceLocation texture, int x, int y, int destWidth, int destHeight, int sourceTextureWidth, int sourceTextureHeight, int padding, float fillRatio) {
        int filledWidth = (int)(destWidth * fillRatio);
        if (filledWidth <= 0) return;
        // Draw left padding (tile vertically)
        if (filledWidth > 0 && padding > 0) {
            int leftWidth = Math.min(padding, filledWidth);
            for (int tileY = 0; tileY < destHeight; tileY += sourceTextureHeight) {
                int drawHeight = Math.min(sourceTextureHeight, destHeight - tileY);
                graphics.blit(texture, x, y + tileY, 0, 0, leftWidth, drawHeight, sourceTextureWidth, sourceTextureHeight);
            }
        }
        // Draw tiled middle (horizontally and vertically)
        int sourceMiddleWidth = sourceTextureWidth - padding * 2;
        int destMiddleWidth = filledWidth - padding * 2;
        int tiledX = x + padding;
        int remaining = destMiddleWidth;
        while (remaining > 0) {
            int tileWidth = Math.min(sourceMiddleWidth, remaining);
            for (int tileY = 0; tileY < destHeight; tileY += sourceTextureHeight) {
                int drawHeight = Math.min(sourceTextureHeight, destHeight - tileY);
                graphics.blit(texture, tiledX, y + tileY, padding, 0, tileWidth, drawHeight, sourceTextureWidth, sourceTextureHeight);
            }
            tiledX += tileWidth;
            remaining -= tileWidth;
        }
        // Draw right padding (tile vertically)
        if (filledWidth > padding && padding > 0) {
            int rightStart = x + filledWidth - padding;
            int rightWidth = Math.min(padding, filledWidth - padding);
            if (rightWidth > 0) {
                for (int tileY = 0; tileY < destHeight; tileY += sourceTextureHeight) {
                    int drawHeight = Math.min(sourceTextureHeight, destHeight - tileY);
                    graphics.blit(texture, rightStart, y + tileY, sourceTextureWidth - padding, 0, rightWidth, drawHeight, sourceTextureWidth, sourceTextureHeight);
                }
            }
        }
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
                graphics.blit(texture, rightStart, y + tileY, sourceTextureWidth - rightPadding, vOffset, rightWidth, drawHeight, sourceTextureWidth, ATLAS_TOTAL_HEIGHT);
            }
            return;
        }
        if (filledWidth <= leftPadding) {
            // Only draw the left cap
            int leftWidth = filledWidth;
            for (int tileY = 0; tileY < destHeight; tileY += sourceTextureHeight) {
                int drawHeight = Math.min(sourceTextureHeight, destHeight - tileY);
                graphics.blit(texture, x, y + tileY, 0, vOffset, leftWidth, drawHeight, sourceTextureWidth, ATLAS_TOTAL_HEIGHT);
            }
            return;
        }

        // Draw left padding (tile vertically)
        if (leftPadding > 0) {
            int leftWidth = leftPadding;
            for (int tileY = 0; tileY < destHeight; tileY += sourceTextureHeight) {
                int drawHeight = Math.min(sourceTextureHeight, destHeight - tileY);
                graphics.blit(texture, x, y + tileY, 0, vOffset, leftWidth, drawHeight, sourceTextureWidth, ATLAS_TOTAL_HEIGHT);
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
                graphics.blit(texture, tiledX, y + tileY, leftPadding, vOffset, tileWidth, drawHeight, sourceTextureWidth, ATLAS_TOTAL_HEIGHT);
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
                graphics.blit(texture, rightStart, y + tileY, sourceTextureWidth - rightPadding, vOffset, rightWidth, drawHeight, sourceTextureWidth, ATLAS_TOTAL_HEIGHT);
            }
        }
    }
}
