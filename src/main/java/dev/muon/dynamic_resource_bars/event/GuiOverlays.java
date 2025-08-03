package dev.muon.dynamic_resource_bars.event;

import dev.muon.dynamic_resource_bars.config.ModConfigManager;
import dev.muon.dynamic_resource_bars.render.BarRenderManager;
#if FORGE
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

// 1.20.1 Forge Only. See CommonEvents for 1.21.1, GuiMixin for 1.20.1 Fabric
public class GuiOverlays {
    public static final IGuiOverlay RESOURCE_BARS = (ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) -> {
        Minecraft minecraft = gui.getMinecraft();
        if (minecraft.options.hideGui) return;
        if (!gui.shouldDrawSurvivalElements()) return;

        var player = minecraft.player;
        if (player == null) return;

        // Use the centralized render manager to handle all bars in correct order
        BarRenderManager.renderAllBars(graphics, player, partialTick);

        // Update GUI heights for proper spacing
        var config = ModConfigManager.getClient();
        if (config.enableHealthBar) {
            gui.leftHeight += config.healthBackgroundHeight + 1;
        }
        if (config.enableStaminaBar) {
            gui.rightHeight += config.staminaBackgroundHeight + 1;
        }
        if (config.armorBarBehavior == dev.muon.dynamic_resource_bars.util.BarRenderBehavior.CUSTOM) {
            gui.leftHeight += config.armorBackgroundHeight + 1;
        }
        if (config.airBarBehavior == dev.muon.dynamic_resource_bars.util.BarRenderBehavior.CUSTOM) {
            gui.rightHeight += config.airBackgroundHeight + 1;
        }
    };
}
#endif