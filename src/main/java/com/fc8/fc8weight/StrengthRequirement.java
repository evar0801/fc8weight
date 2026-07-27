package com.fc8.fc8weight;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Tier-table strength requirement for weapons (2026-07-27 model, replaces weight×factor).
 *
 * <p>The vanilla iron sword is the anchor of the whole ladder: it costs exactly 10 strength, and
 * every other weapon is priced relative to it by two independent axes.
 *
 * <ul>
 *   <li><b>Material tier</b> fixes that material's <i>sword</i> price and how hard the size axis
 *       bites: iron 10 (×1) → gold 20 (×1) → diamond 50 (×2) → netherite 100 (×3). Wood/stone-class
 *       materials are free (requirement 0) so early game is never gated.</li>
 *   <li><b>Size class</b> adds on top: light 2 / medium 4 / heavy 8. A plain sword IS the light
 *       class, which is what makes the anchor land exactly on the stated 10/20/50/100.</li>
 * </ul>
 *
 * <p>{@code requirement = swordAnchor + (sizeBonus - lightBonus) * sizeMultiplier}, so e.g. an iron
 * greatsword is 10 + (8-2)×1 = 16 and a netherite greatsword is 100 + (8-2)×3 = 118.
 *
 * <p>Unlike the legacy model this never reads the weapon's attack damage, which is what lets
 * {@link WeightHandler#onItemAttribute} safely grant a requirement-scaled damage bonus without
 * recursing back into {@code getAttributeModifiers}.
 */
public final class StrengthRequirement {

    /** A material tier: the price of that material's plain sword, and its size-axis multiplier. */
    private record Tier(double swordAnchor, double sizeMultiplier) {
    }

    private static final Tier FREE = new Tier(0.0, 0.0);

    private StrengthRequirement() {
    }

    /**
     * Strength required to wield the stack, or 0 when the material is free (wood/stone class) or
     * unrecognized. Unrecognized materials return 0 on purpose: an unknown modded weapon should stay
     * usable rather than become silently unwieldable.
     */
    static double of(ItemStack stack) {
        if (isMiningTool(stack)) {
            return 0.0;
        }
        String id = MaterialWeight.idOf(stack);
        if (id == null) {
            return 0.0;
        }
        Tier tier = tierOf(id);
        if (tier == null || tier.swordAnchor() <= 0.0) {
            return 0.0;
        }
        double light = WeightConfig.REQ_SIZE_LIGHT.get();
        return tier.swordAnchor() + (sizeBonus(id) - light) * tier.sizeMultiplier();
    }

    /**
     * Encumbrance weight of a held weapon on the same tier ladder as {@link #of}: an iron sword
     * weighs 10, a netherite greatsword 118, and anything wood/stone-class weighs nothing at all.
     *
     * <p>Returns {@link Double#NaN} — rather than 0 — when the material is unrecognized, so
     * {@link WeightHandler#weaponWeight} can tell "this is genuinely weightless starter gear" apart
     * from "I have no idea what this is" and fall back to the density model for the latter.
     */
    static double weightOf(ItemStack stack) {
        if (isMiningTool(stack)) {
            return Double.NaN; // let the density model price a pickaxe's bulk instead
        }
        String id = MaterialWeight.idOf(stack);
        if (id == null) {
            return Double.NaN;
        }
        Tier tier = tierOf(id);
        if (tier == null) {
            return Double.NaN;
        }
        return of(stack);
    }

    /**
     * Encumbrance weight of one worn armor piece: the material's tier anchor × the slot's bulk
     * share, so a full four-piece set weighs exactly that material's sword (iron 10, gold 20,
     * diamond 50, netherite 100). {@link Double#NaN} when the material is unrecognized.
     *
     * <p>Armor carries no size keyword, so the size axis is deliberately not applied here — the
     * anchor alone prices it.
     */
    static double armorPieceWeightOf(ItemStack stack, EquipmentSlot slot) {
        String id = MaterialWeight.idOf(stack);
        if (id == null) {
            return Double.NaN;
        }
        Tier tier = tierOf(id);
        if (tier == null) {
            return Double.NaN;
        }
        return tier.swordAnchor() * MaterialWeight.pieceFactor(slot);
    }

    /**
     * Material tier for the id, or null when no material keyword matches. Order matters: more
     * specific keywords are tested before their substrings (netherite before "ite", golden before
     * gold). Mirrors {@link MaterialWeight#materialFactor}'s keyword set so the two models agree on
     * what counts as "iron" or "diamond".
     */
    private static Tier tierOf(String id) {
        if (has(id, "netherite")) {
            return new Tier(WeightConfig.REQ_NETHERITE_SWORD.get(), WeightConfig.REQ_MULT_NETHERITE.get());
        }
        if (has(id, "diamond") || has(id, "emerald") || has(id, "mithril")) {
            return new Tier(WeightConfig.REQ_DIAMOND_SWORD.get(), WeightConfig.REQ_MULT_DIAMOND.get());
        }
        if (has(id, "golden") || has(id, "gold")) {
            return new Tier(WeightConfig.REQ_GOLD_SWORD.get(), WeightConfig.REQ_MULT_GOLD.get());
        }
        if (has(id, "copper") || has(id, "silver") || has(id, "lead")
                || has(id, "steel") || has(id, "iron")) {
            return new Tier(WeightConfig.REQ_IRON_SWORD.get(), WeightConfig.REQ_MULT_IRON.get());
        }
        if (has(id, "obsidian") || has(id, "stone") || has(id, "flint") || has(id, "cobbled")
                || has(id, "deepslate") || has(id, "blackstone")
                || has(id, "wooden") || has(id, "wood") || has(id, "bamboo") || has(id, "plank")
                || has(id, "leather")) {
            return FREE;
        }
        return null;
    }

    /**
     * Size-class bonus for the id: heavy (two-handed bulk) / medium (hafted, curved, one-and-a-half)
     * / light (one-hand sword class, the anchor). Great* variants are tested before their base word
     * so "greatsword" is not caught by "sword". Unrecognized shapes fall back to light, matching
     * {@link MaterialWeight#sizeFactor}'s "treat like a one-hand sword" default.
     */
    private static double sizeBonus(String id) {
        // "pickaxe" contains "axe", so a modded pickaxe that is not a DiggerItem would otherwise be
        // priced as a medium-size battle axe. Strip the word before any keyword is tested.
        if (id.contains("pickaxe") || id.contains("pick_axe")) {
            return WeightConfig.REQ_SIZE_LIGHT.get();
        }
        if (hasAny(id, "greathammer", "great_hammer", "warhammer", "war_hammer", "greatmaul", "great_maul",
                "greatsword", "great_sword", "claymore", "zweihander", "greataxe", "great_axe", "greatblade",
                "halberd", "glaive", "naginata", "warglaive", "poleaxe", "polearm", "guandao",
                "hammer", "maul", "nodachi", "scythe")) {
            return WeightConfig.REQ_SIZE_HEAVY.get();
        }
        if (hasAny(id, "mace", "flail", "morningstar", "battle_axe", "battleaxe", "axe", "sickle",
                "spear", "lance", "pike", "trident", "javelin", "yari",
                "katana", "tachi", "uchigatana", "longsword", "long_sword",
                "saber", "sabre", "machete")) {
            return WeightConfig.REQ_SIZE_MEDIUM.get();
        }
        return WeightConfig.REQ_SIZE_LIGHT.get();
    }

    /**
     * True for mining tools, which are exempt from the whole ladder.
     *
     * <p>A vanilla pickaxe carries a positive ADDITION attack_damage (iron 3.0, diamond 4.0), enough
     * to clear {@code minWeaponDamageToGate} — so without this check a diamond pickaxe would demand
     * 50 strength to swing and collect the matching +25 damage bonus, turning a mining tool into a
     * 30-damage weapon nobody can lift.
     *
     * <p>Tested against {@link DiggerItem} rather than the three concrete classes so modded paxels
     * and drills inherit the exemption. {@link AxeItem} is deliberately excluded from the exemption:
     * axes are real weapons in both vanilla and Epic Fight.
     */
    private static boolean isMiningTool(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof DiggerItem && !(item instanceof AxeItem);
    }

    private static boolean has(String id, String kw) {
        return id.indexOf(kw) >= 0;
    }

    private static boolean hasAny(String id, String... kws) {
        for (String kw : kws) {
            if (id.indexOf(kw) >= 0) {
                return true;
            }
        }
        return false;
    }
}
