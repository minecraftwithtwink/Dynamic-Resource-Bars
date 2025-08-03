package dev.muon.dynamic_resource_bars.config.gui;

import dev.muon.dynamic_resource_bars.DynamicResourceBars; // Added import for logging
import dev.muon.dynamic_resource_bars.config.ModConfigManager;
import dev.muon.dynamic_resource_bars.config.ClientConfig;
import dev.muon.dynamic_resource_bars.render.HealthBarRenderer; // Added import for HealthBarRenderer
import dev.muon.dynamic_resource_bars.util.DraggableElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

#if (!NEWER_THAN_20_1)
import dev.muon.dynamic_resource_bars.util.ScreenRect;
#endif

public class ResizeElementScreen extends Screen {

    private final Screen parentScreen;
    private final DraggableElement elementToResize;

    // These EditBoxes are declared here, their visibility and data handling
    // will depend on the `elementToResize` type.
    private EditBox bgWidthBox;
    private EditBox bgHeightBox;
    private EditBox bgXOffsetBox;
    private EditBox bgYOffsetBox;
    private EditBox barWidthBox;
    private EditBox barHeightBox;
    private EditBox overlayWidthBox;
    private EditBox overlayHeightBox;

    public ResizeElementScreen(Screen parent, DraggableElement element) {
        super(Component.translatable("gui.dynamic_resource_bars.resize.title_format", getFriendlyElementName(element)));
        this.parentScreen = parent;
        this.elementToResize = element;
    }

    @Override
    protected void init() {
        super.init();
        ClientConfig config = ModConfigManager.getClient();

        int boxWidth = 50;
        int boxHeight = 20;
        int labelWidth = 100;
        int componentBlockWidth = labelWidth + 5 + boxWidth;
        int startX = (this.width / 2) - componentBlockWidth / 2;
        int editBoxX = startX + labelWidth + 5;
        int currentY = 40;
        int rowSpacing = 5;

        // Determine if this is the health bar. Health bar has dynamic width.
        boolean isHealthBar = (elementToResize == DraggableElement.HEALTH_BAR);

        // --- Background Properties ---
        // Width box is only added for non-health bars, as health bar width is dynamic.
        if (!isHealthBar) {
            int bgWidthConf = 0;
            switch (elementToResize) {
                case STAMINA_BAR: bgWidthConf = config.staminaBackgroundWidth; break;
                case MANA_BAR: bgWidthConf = config.manaBackgroundWidth; break;
                case ARMOR_BAR: bgWidthConf = config.armorBackgroundWidth; break;
                case AIR_BAR: bgWidthConf = config.airBackgroundWidth; break;
                default: break; // Should not happen with current logic
            }
            bgWidthBox = createIntEditBox(editBoxX, currentY, boxWidth, boxHeight, bgWidthConf);
            this.addRenderableWidget(bgWidthBox);
        }
        // Adjust Y for the next element (height box) based on whether width box was added.
        currentY += (isHealthBar ? 0 : boxHeight + rowSpacing);

        int bgHeightConf = 0;
        int bgXOffsetConf = 0;
        int bgYOffsetConf = 0;
        switch (elementToResize) {
            case HEALTH_BAR:
                bgHeightConf = config.healthBackgroundHeight;
                bgXOffsetConf = config.healthBackgroundXOffset;
                bgYOffsetConf = config.healthBackgroundYOffset;
                break;
            case STAMINA_BAR:
                bgHeightConf = config.staminaBackgroundHeight;
                bgXOffsetConf = config.staminaBackgroundXOffset;
                bgYOffsetConf = config.staminaBackgroundYOffset;
                break;
            case MANA_BAR:
                bgHeightConf = config.manaBackgroundHeight;
                bgXOffsetConf = config.manaBackgroundXOffset;
                bgYOffsetConf = config.manaBackgroundYOffset;
                break;
            case ARMOR_BAR:
                bgHeightConf = config.armorBackgroundHeight;
                bgXOffsetConf = config.armorBackgroundXOffset;
                bgYOffsetConf = config.armorBackgroundYOffset;
                break;
            case AIR_BAR:
                bgHeightConf = config.airBackgroundHeight;
                bgXOffsetConf = config.airBackgroundXOffset;
                bgYOffsetConf = config.airBackgroundYOffset;
                break;
            default: break;
        }
        bgHeightBox = createIntEditBox(editBoxX, currentY, boxWidth, boxHeight, bgHeightConf);
        this.addRenderableWidget(bgHeightBox);
        currentY += boxHeight + rowSpacing;

        bgXOffsetBox = createIntEditBox(editBoxX, currentY, boxWidth, boxHeight, bgXOffsetConf, Integer.MIN_VALUE, Integer.MAX_VALUE);
        this.addRenderableWidget(bgXOffsetBox);
        currentY += boxHeight + rowSpacing;

        bgYOffsetBox = createIntEditBox(editBoxX, currentY, boxWidth, boxHeight, bgYOffsetConf, Integer.MIN_VALUE, Integer.MAX_VALUE);
        this.addRenderableWidget(bgYOffsetBox);
        currentY += boxHeight + rowSpacing + rowSpacing; // Extra spacing between groups

        // --- Main Bar Properties ---
        // Width box is only added for non-health bars
        if (!isHealthBar) {
            int barWidthConf = 0;
            switch (elementToResize) {
                case STAMINA_BAR: barWidthConf = config.staminaBarWidth; break;
                case MANA_BAR: barWidthConf = config.manaBarWidth; break;
                case ARMOR_BAR: barWidthConf = config.armorBarWidth; break;
                case AIR_BAR: barWidthConf = config.airBarWidth; break;
                default: break;
            }
            barWidthBox = createIntEditBox(editBoxX, currentY, boxWidth, boxHeight, barWidthConf, 0, 256);
            this.addRenderableWidget(barWidthBox);
        }
        currentY += (isHealthBar ? 0 : boxHeight + rowSpacing);

        int barHeightConf = 0;
        switch (elementToResize) {
            case HEALTH_BAR: barHeightConf = config.healthBarHeight; break;
            case STAMINA_BAR: barHeightConf = config.staminaBarHeight; break;
            case MANA_BAR: barHeightConf = config.manaBarHeight; break;
            case ARMOR_BAR: barHeightConf = config.armorBarHeight; break;
            case AIR_BAR: barHeightConf = config.airBarHeight; break;
            default: break;
        }
        barHeightBox = createIntEditBox(editBoxX, currentY, boxWidth, boxHeight, barHeightConf, 0, 32);
        this.addRenderableWidget(barHeightBox);
        currentY += boxHeight + rowSpacing + rowSpacing;

        // --- Overlay Properties (only for Mana/Stamina) ---
        if (elementToResize == DraggableElement.MANA_BAR || elementToResize == DraggableElement.STAMINA_BAR) {
            int overlayWidthConf = 0;
            int overlayHeightConf = 0;
            switch (elementToResize) {
                case MANA_BAR:
                    overlayWidthConf = config.manaOverlayWidth;
                    overlayHeightConf = config.manaOverlayHeight;
                    break;
                case STAMINA_BAR:
                    overlayWidthConf = config.staminaOverlayWidth;
                    overlayHeightConf = config.staminaOverlayHeight;
                    break;
                default: break;
            }
            overlayWidthBox = createIntEditBox(editBoxX, currentY, boxWidth, boxHeight, overlayWidthConf, 0, 256);
            this.addRenderableWidget(overlayWidthBox);
            currentY += boxHeight + rowSpacing;

            overlayHeightBox = createIntEditBox(editBoxX, currentY, boxWidth, boxHeight, overlayHeightConf, 0, 256);
            this.addRenderableWidget(overlayHeightBox);
            currentY += boxHeight + rowSpacing;
        }

        int doneButtonWidth = 100;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.done"),
                        (button) -> this.onClose())
                .bounds((this.width / 2) - (doneButtonWidth / 2), this.height - boxHeight - 20, doneButtonWidth, boxHeight)
                .build());
    }

    private EditBox createIntEditBox(int x, int y, int width, int height, int configIntValue) {
        // Default min value for sizes should be 1 to ensure minimum size for rendering
        return createIntEditBox(x, y, width, height, configIntValue, 1, Integer.MAX_VALUE);
    }

    private EditBox createIntEditBox(int x, int y, int width, int height, int configIntValue, int minValue, int maxValue) {
        EditBox editBox = new EditBox(this.font, x, y, width, height, Component.empty());
        editBox.setValue(String.valueOf(configIntValue));
        editBox.setResponder((text) -> {
            try {
                int value = Integer.parseInt(text);
                if (value >= minValue && value <= maxValue) { // Check against min and max
                    // The actual config values are set in onClose()
                    editBox.setTextColor(0xE0E0E0); // Default color
                } else {
                    editBox.setTextColor(0xFF5555); // Red for out of bounds
                }
            } catch (NumberFormatException e) {
                editBox.setTextColor(0xFF5555); // Red for invalid number
            }
        });
        return editBox;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        #if NEWER_THAN_20_1
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);
        #else
        this.renderBackground(graphics);
        #endif

        super.render(graphics, mouseX, mouseY, partialTicks);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        // Determine if this is the health bar
        boolean isHealthBar = (elementToResize == DraggableElement.HEALTH_BAR);

        // Render labels based on whether it's health bar or other bars
        // Adjust labelX based on the presence of bgWidthBox
        int labelX = (bgWidthBox != null ? bgWidthBox.getX() : (bgHeightBox != null ? bgHeightBox.getX() : 0)) - 5 - 100;
        if (labelX < 0) labelX = 5; // Fallback if no boxes are present, or too far left.

        // Background labels
        if (!isHealthBar && bgWidthBox != null) { // Only show width for non-health bars
            graphics.drawString(this.font, Component.translatable("gui.dynamic_resource_bars.resize.label.background_width"), labelX, bgWidthBox.getY() + (bgWidthBox.getHeight() - this.font.lineHeight) / 2, 0xFFFFFF);
        }
        if (bgHeightBox != null) {
            graphics.drawString(this.font, Component.translatable("gui.dynamic_resource_bars.resize.label.background_height"), labelX, bgHeightBox.getY() + (bgHeightBox.getHeight() - this.font.lineHeight) / 2, 0xFFFFFF);
        }
        if (bgXOffsetBox != null) {
            graphics.drawString(this.font, Component.translatable("gui.dynamic_resource_bars.resize.label.background_x_offset"), labelX, bgXOffsetBox.getY() + (bgXOffsetBox.getHeight() - this.font.lineHeight) / 2, 0xFFFFFF);
        }
        if (bgYOffsetBox != null) {
            graphics.drawString(this.font, Component.translatable("gui.dynamic_resource_bars.resize.label.background_y_offset"), labelX, bgYOffsetBox.getY() + (bgYOffsetBox.getHeight() - this.font.lineHeight) / 2, 0xFFFFFF);
        }

        // Main Bar labels
        if (!isHealthBar && barWidthBox != null) { // Only show width for non-health bars
            graphics.drawString(this.font, Component.translatable("gui.dynamic_resource_bars.resize.label.bar_width"), labelX, barWidthBox.getY() + (barWidthBox.getHeight() - this.font.lineHeight) / 2, 0xFFFFFF);
        }
        if (barHeightBox != null) {
            graphics.drawString(this.font, Component.translatable("gui.dynamic_resource_bars.resize.label.bar_height"), labelX, barHeightBox.getY() + (barHeightBox.getHeight() - this.font.lineHeight) / 2, 0xFFFFFF);
        }

        // Overlay labels (only for Mana/Stamina)
        if ((elementToResize == DraggableElement.MANA_BAR || elementToResize == DraggableElement.STAMINA_BAR) && overlayWidthBox != null) {
            graphics.drawString(this.font, Component.translatable("gui.dynamic_resource_bars.resize.label.overlay_width"), labelX, overlayWidthBox.getY() + (overlayWidthBox.getHeight() - this.font.lineHeight) / 2, 0xFFFFFF);
        }
        if ((elementToResize == DraggableElement.MANA_BAR || elementToResize == DraggableElement.STAMINA_BAR) && overlayHeightBox != null) {
            graphics.drawString(this.font, Component.translatable("gui.dynamic_resource_bars.resize.label.overlay_height"), labelX, overlayHeightBox.getY() + (overlayHeightBox.getHeight() - this.font.lineHeight) / 2, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        boolean unfocusedAny = false;
        for (net.minecraft.client.gui.components.events.GuiEventListener listener : this.children()) {
            if (listener instanceof EditBox) {
                EditBox box = (EditBox) listener;
                #if NEWER_THAN_20_1 // For 1.21.1+ which has containsPoint
                ScreenRectangle vanillaRect = box.getRectangle();
                if (box.isFocused() && !vanillaRect.containsPoint((int)mouseX, (int)mouseY)) {
                    box.setFocused(false);
                    unfocusedAny = true;
                }
                #else // For 1.20.1 (Fabric or Forge)
                // Vanilla ScreenRectangle exists in 1.20.1 but lacks containsPoint.
                // We use its getters to construct our custom ScreenRect for the contains check.
                ScreenRectangle vanillaRect = box.getRectangle();
                ScreenRect customRect =
                        new ScreenRect(vanillaRect.left(), vanillaRect.top(),
                                vanillaRect.width(), vanillaRect.height());
                if (box.isFocused() && !customRect.contains((int)mouseX, (int)mouseY)) {
                    box.setFocused(false);
                    unfocusedAny = true;
                }
                #endif
            }
        }
        return unfocusedAny;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Prioritize focused element for key presses (e.g., typing in EditBox)
        if (this.getFocused() != null && this.getFocused().keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        // Save all values to config before closing
        ClientConfig config = ModConfigManager.getClient();

        try {
            switch (elementToResize) {
                case HEALTH_BAR:
                    // Only save height and offset for Health Bar, as widths are dynamic.
                    if (bgHeightBox != null) config.healthBackgroundHeight = parseIntSafely(bgHeightBox.getValue(), config.healthBackgroundHeight, 1, Integer.MAX_VALUE);
                    if (barHeightBox != null) config.healthBarHeight = parseIntSafely(barHeightBox.getValue(), config.healthBarHeight, 1, 32);
                    if (overlayHeightBox != null) config.healthOverlayHeight = parseIntSafely(overlayHeightBox.getValue(), config.healthOverlayHeight, 1, 256);
                    if (bgXOffsetBox != null) config.healthBackgroundXOffset = parseIntSafely(bgXOffsetBox.getValue(), config.healthBackgroundXOffset, Integer.MIN_VALUE, Integer.MAX_VALUE);
                    if (bgYOffsetBox != null) config.healthBackgroundYOffset = parseIntSafely(bgYOffsetBox.getValue(), config.healthBackgroundYOffset, Integer.MIN_VALUE, Integer.MAX_VALUE);
                    break;
                case STAMINA_BAR:
                    if (bgWidthBox != null) config.staminaBackgroundWidth = parseIntSafely(bgWidthBox.getValue(), config.staminaBackgroundWidth, 1, Integer.MAX_VALUE);
                    if (bgHeightBox != null) config.staminaBackgroundHeight = parseIntSafely(bgHeightBox.getValue(), config.staminaBackgroundHeight, 1, Integer.MAX_VALUE);
                    if (barWidthBox != null) config.staminaBarWidth = parseIntSafely(barWidthBox.getValue(), config.staminaBarWidth, 1, 256);
                    if (barHeightBox != null) config.staminaBarHeight = parseIntSafely(barHeightBox.getValue(), config.staminaBarHeight, 1, 32);
                    if (overlayWidthBox != null) config.staminaOverlayWidth = parseIntSafely(overlayWidthBox.getValue(), config.staminaOverlayWidth, 1, 256);
                    if (overlayHeightBox != null) config.staminaOverlayHeight = parseIntSafely(overlayHeightBox.getValue(), config.staminaOverlayHeight, 1, 256);
                    if (bgXOffsetBox != null) config.staminaBackgroundXOffset = parseIntSafely(bgXOffsetBox.getValue(), config.staminaBackgroundXOffset, Integer.MIN_VALUE, Integer.MAX_VALUE);
                    if (bgYOffsetBox != null) config.staminaBackgroundYOffset = parseIntSafely(bgYOffsetBox.getValue(), config.staminaBackgroundYOffset, Integer.MIN_VALUE, Integer.MAX_VALUE);
                    break;
                case MANA_BAR:
                    if (bgWidthBox != null) config.manaBackgroundWidth = parseIntSafely(bgWidthBox.getValue(), config.manaBackgroundWidth, 1, Integer.MAX_VALUE);
                    if (bgHeightBox != null) config.manaBackgroundHeight = parseIntSafely(bgHeightBox.getValue(), config.manaBackgroundHeight, 1, Integer.MAX_VALUE);
                    if (barWidthBox != null) config.manaBarWidth = parseIntSafely(barWidthBox.getValue(), config.manaBarWidth, 1, 256);
                    if (barHeightBox != null) config.manaBarHeight = parseIntSafely(barHeightBox.getValue(), config.manaBarHeight, 1, 32);
                    if (overlayWidthBox != null) config.manaOverlayWidth = parseIntSafely(overlayWidthBox.getValue(), config.manaOverlayWidth, 1, 256);
                    if (overlayHeightBox != null) config.manaOverlayHeight = parseIntSafely(overlayHeightBox.getValue(), config.manaOverlayHeight, 1, 256);
                    if (bgXOffsetBox != null) config.manaBackgroundXOffset = parseIntSafely(bgXOffsetBox.getValue(), config.manaBackgroundXOffset, Integer.MIN_VALUE, Integer.MAX_VALUE);
                    if (bgYOffsetBox != null) config.manaBackgroundYOffset = parseIntSafely(bgYOffsetBox.getValue(), config.manaBackgroundYOffset, Integer.MIN_VALUE, Integer.MAX_VALUE);
                    break;
                case ARMOR_BAR:
                    if (bgWidthBox != null) config.armorBackgroundWidth = parseIntSafely(bgWidthBox.getValue(), config.armorBackgroundWidth, 1, Integer.MAX_VALUE);
                    if (bgHeightBox != null) config.armorBackgroundHeight = parseIntSafely(bgHeightBox.getValue(), config.armorBackgroundHeight, 1, Integer.MAX_VALUE);
                    if (barWidthBox != null) config.armorBarWidth = parseIntSafely(barWidthBox.getValue(), config.armorBarWidth, 1, 256);
                    if (barHeightBox != null) config.armorBarHeight = parseIntSafely(barHeightBox.getValue(), config.armorBarHeight, 1, 32);
                    if (bgXOffsetBox != null) config.armorBackgroundXOffset = parseIntSafely(bgXOffsetBox.getValue(), config.armorBackgroundXOffset, Integer.MIN_VALUE, Integer.MAX_VALUE);
                    if (bgYOffsetBox != null) config.armorBackgroundYOffset = parseIntSafely(bgYOffsetBox.getValue(), config.armorBackgroundYOffset, Integer.MIN_VALUE, Integer.MAX_VALUE);
                    break;
                case AIR_BAR:
                    if (bgWidthBox != null) config.airBackgroundWidth = parseIntSafely(bgWidthBox.getValue(), config.airBackgroundWidth, 1, Integer.MAX_VALUE);
                    if (bgHeightBox != null) config.airBackgroundHeight = parseIntSafely(bgHeightBox.getValue(), config.airBackgroundHeight, 1, Integer.MAX_VALUE);
                    if (barWidthBox != null) config.airBarWidth = parseIntSafely(barWidthBox.getValue(), config.airBarWidth, 1, 256);
                    if (barHeightBox != null) config.airBarHeight = parseIntSafely(barHeightBox.getValue(), config.airBarHeight, 1, 32);
                    if (bgXOffsetBox != null) config.airBackgroundXOffset = parseIntSafely(bgXOffsetBox.getValue(), config.airBackgroundXOffset, Integer.MIN_VALUE, Integer.MAX_VALUE);
                    if (bgYOffsetBox != null) config.airBackgroundYOffset = parseIntSafely(bgYOffsetBox.getValue(), config.airBackgroundYOffset, Integer.MIN_VALUE, Integer.MAX_VALUE);
                    break;
            }

            // Save config to disk
            config.save();
        } catch (Exception e) {
            // If there's any error parsing, just close without saving
            DynamicResourceBars.LOGGER.error("Failed to save resized config elements for {}: {}", elementToResize.name(), e.getMessage());
        }

        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }

    private int parseIntSafely(String value, int defaultValue, int minValue, int maxValue) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minValue) return minValue;
            if (parsed > maxValue) return maxValue;
            return parsed;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String getFriendlyElementName(DraggableElement element) {
        if (element == null) return "";
        switch (element) {
            case HEALTH_BAR:
                return Component.translatable("gui.dynamic_resource_bars.element.health_bar").getString();
            case MANA_BAR:
                return Component.translatable("gui.dynamic_resource_bars.element.mana_bar").getString();
            case STAMINA_BAR:
                return Component.translatable("gui.dynamic_resource_bars.element.stamina_bar").getString();
            case ARMOR_BAR:
                return Component.translatable("gui.dynamic_resource_bars.element.armor_bar").getString();
            case AIR_BAR:
                return Component.translatable("gui.dynamic_resource_bars.element.air_bar").getString();
            default:
                return element.name();
        }
    }
}
