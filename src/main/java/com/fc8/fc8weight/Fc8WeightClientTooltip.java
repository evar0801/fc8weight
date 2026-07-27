package com.fc8.fc8weight;

import com.fc8.fc8weight.client.GateTooltipRenderer;
import com.mojang.datafixers.util.Either;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only wiring for the icon-based strength-gate tooltip row.
 *
 * <p>Both halves are annotated {@link Dist#CLIENT}, so neither is class-loaded on a dedicated server
 * and the client-only {@link GateTooltipRenderer} import never reaches it.
 *
 * <p>The plain-text 「必要筋骨 …」 line was removed from {@link WeightTooltip} in favour of this row —
 * the two would otherwise state the same figures twice.
 */
public final class Fc8WeightClientTooltip {

    private Fc8WeightClientTooltip() {
    }

    /** Maps the common-code {@link GateTooltipData} payload to its client renderer. */
    @Mod.EventBusSubscriber(modid = Fc8Weight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Factories {

        private Factories() {
        }

        @SubscribeEvent
        public static void onRegisterFactories(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(GateTooltipData.class, GateTooltipRenderer::new);
        }
    }

    /**
     * Appends the gate row to weapon tooltips. Mirrors {@link WeightTooltip}'s conditions exactly so
     * the icon row and the 重さ line appear and disappear together.
     */
    @Mod.EventBusSubscriber(modid = Fc8Weight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Gather {

        private Gather() {
        }

        @SubscribeEvent
        public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
            if (!WeightConfig.ENABLED.get() || !WeightConfig.TOOLTIP_ENABLED.get()
                    || !WeightConfig.GATE_ENABLED.get()) {
                return;
            }
            ItemStack stack = event.getItemStack();
            if (WeightHandler.weaponBaseDamage(stack) < WeightConfig.MIN_WEAPON_DAMAGE_TO_GATE.get()) {
                return;
            }
            if (WeightHandler.isGateExempt(stack)) {
                return; // starter/reward weapon: WeightTooltip already says 「なし（初期装備）」
            }
            double requirement = StrengthRequirement.of(stack);
            if (requirement <= 0.0) {
                return; // wood/stone class: free to wield, nothing worth a row
            }
            Player player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            event.getTooltipElements().add(
                    Either.right(new GateTooltipData(requirement, WeightHandler.strengthOf(player))));
        }
    }
}
