package com.fc8.fc8weight;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Weapon tooltip lines for the weight + strength-gate systems (visibility fix).
 *
 * <p>{@link ItemTooltipEvent} lives in Forge common code and only ever fires on the client, so
 * registering this class is safe on a dedicated server (no client-only classes referenced).
 *
 * <p>This class now emits only the lines the icon row cannot carry:
 * <ul>
 *   <li>重さ — armor only. Since the tier rebuild a weapon's weight equals its 必要筋骨, which the
 *       icon row already shows; armor has no gate row of its own, so this is the only place its
 *       movement-penalty figure is visible.</li>
 *   <li>必要筋骨: なし（初期装備） — for weapons on the gate-exempt list.</li>
 *   <li>The hint pointing at EpicClassMod's class book, shown only while the player is short.</li>
 * </ul>
 *
 * <p>The player's strength uses the exact same formula as the server-side gate
 * ({@link WeightHandler#strengthOf}). On a dedicated server the client's own persistent NBT
 * (ecm_leveling/ecm_class_name) is never synced, but {@link WeightHandler} reads EpicClassMod's own
 * client-synced state instead in that case (see {@code WeightHandler.EcmClientBridge}), so this
 * tooltip agrees with both the class book and the (always authoritative) server-side gate.
 */
public final class WeightTooltip {

    private WeightTooltip() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!WeightConfig.ENABLED.get() || !WeightConfig.TOOLTIP_ENABLED.get()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        double weaponDamage = WeightHandler.weaponBaseDamage(stack);
        if (weaponDamage <= 0.0) {
            addArmorWeightLine(event, stack);
            return; // not a weapon (no attack_damage ADDITION): keep tooltips clean
        }
        List<Component> lines = event.getToolTip();

        // No 重さ line for weapons: since the tier rebuild it is the same figure as 必要筋骨, which
        // the icon row already shows. Armor still gets one below — it has no gate row of its own.

        if (!WeightConfig.GATE_ENABLED.get()
                || weaponDamage < WeightConfig.MIN_WEAPON_DAMAGE_TO_GATE.get()) {
            return; // below the gate threshold: no strength requirement
        }
        if (WeightHandler.isGateExempt(stack)) {
            lines.add(Component.literal("必要筋骨: なし（初期装備）")
                    .withStyle(ChatFormatting.DARK_GREEN));
            return;
        }

        // The 必要筋骨 figures themselves are rendered by Fc8WeightClientTooltip's icon row, which can
        // show the shortfall without the slash that made this line read as a fraction. Only the hint
        // stays here, and only while the player is actually short.
        double requirement = StrengthRequirement.of(stack);
        Player player = event.getEntity();
        if (player == null || requirement <= 0.0) {
            return;
        }
        if (WeightHandler.strengthOf(player) + 1.0e-4 < requirement) {
            lines.add(Component.literal("（クラスブックの金床アイコン「筋骨」にポイントを振ると筋骨が上がる）")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * 重さ line for armor. Armor has no strength-gate row, so its tier weight — the figure that
     * drives the movement penalty — would otherwise be invisible. A full four-piece set sums to its
     * material's anchor (iron 10 … netherite 100), which is what the line's suffix spells out.
     */
    private static void addArmorWeightLine(ItemTooltipEvent event, ItemStack stack) {
        if (!WeightConfig.WEIGHT_ENABLED.get()) {
            return;
        }
        EquipmentSlot slot = Mob.getEquipmentSlotForItem(stack);
        if (slot.getType() != EquipmentSlot.Type.ARMOR) {
            return;
        }
        double weight = StrengthRequirement.armorPieceWeightOf(stack, slot);
        if (Double.isNaN(weight)) {
            weight = MaterialWeight.armorPieceWeight(stack, slot);
        }
        if (Double.isNaN(weight) || weight <= 0.0) {
            return;
        }
        event.getToolTip().add(Component.literal(String.format("重さ: %.1f（移動速度に影響）", weight))
                .withStyle(ChatFormatting.GRAY));
    }
}
