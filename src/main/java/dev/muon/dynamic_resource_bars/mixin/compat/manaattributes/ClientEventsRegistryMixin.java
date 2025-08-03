package dev.muon.dynamic_resource_bars.mixin.compat.manaattributes;


import dev.muon.dynamic_resource_bars.util.ManaBarBehavior;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import dev.muon.dynamic_resource_bars.config.ModConfigManager;
import dev.muon.dynamic_resource_bars.util.ManaSource;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
#if FABRIC && NEWER_THAN_20_1
import net.minecraft.client.DeltaTracker;
import com.github.theredbrain.manaattributes.registry.ClientEventsRegistry;
import net.minecraft.client.gui.GuiGraphics;

@Mixin(value = ClientEventsRegistry.class, remap = false)
#else
@Mixin(Minecraft.class)
#endif
public class ClientEventsRegistryMixin {
    #if FABRIC && NEWER_THAN_20_1
    @Inject(method = "lambda$initializeClientEvents$0", at = @At("HEAD"), cancellable = true)
    private static void cancelManaOverlay(GuiGraphics matrixStack, DeltaTracker delta, CallbackInfo ci) {
        var config = ModConfigManager.getClient();
        if (config.manaBarBehavior == ManaBarBehavior.MANA_ATTRIBUTES) {
            ci.cancel();
            // Mana bar is now handled by the centralized BarRenderManager
            // No need to render here as it will be rendered in the correct order
        }
    }
    #endif
} 