package com.alexlogvin.blockieseconomy.fabric.mixin;

import com.alexlogvin.blockieseconomy.fabric.AdvancementBridge;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Supplies the advancement-earned event Fabric API lacks (the Minecraft 1.20.5 era).
 *
 * <p>Advancement identity moved out of {@code Advancement} into {@code AdvancementHolder}
 * in 1.20.2, which is why this differs from the 1.20.1 variant and nothing else does.
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {

    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At("RETURN"))
    private void blockiesEconomy$onAward(AdvancementHolder advancement, String criterion,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        AdvancementProgress progress =
                ((PlayerAdvancements) (Object) this).getOrStartProgress(advancement);
        if (progress.isDone()) {
            AdvancementBridge.fire(player, advancement.id().toString());
        }
    }
}
