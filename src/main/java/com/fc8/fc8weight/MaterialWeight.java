package com.fc8.fc8weight;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Reality-based material weight + weapon size model (MVP, 2026-07-08).
 *
 * <p>Design (author: "リアリティ追求、数値は任せる"):
 * <ul>
 *   <li><b>Weight = material density × item size.</b> Density is scaled to real-world values with
 *       iron = 10 (density 7.87 g/cm³). Notable real consequences kept on purpose: copper(11) &gt;
 *       iron(10); gold(24) is the heaviest yet makes the worst weapons; diamond(4) is as light as
 *       stone; netherite(16) is heavy but lighter than gold.</li>
 *   <li><b>Size</b> is the weapon's physical bulk (metal volume; hafted weapons discounted for their
 *       wooden shaft): dagger 0.3 … greathammer 1.8.</li>
 *   <li><b>Armor</b> weight = material × piece factor (head .2 / chest .4 / legs .3 / feet .1), so a
 *       full set weighs the material's density (iron set = 10, diamond = 4, netherite = 16).</li>
 * </ul>
 *
 * <p>Resolution is by registry-id substring (+ Epic Fight category for size when available). This
 * auto-covers most vanilla and sensibly-named modded gear (e.g. {@code simplyswords:iron_longsword}
 * → iron × longsword). When the material cannot be resolved the methods return {@link Double#NaN};
 * {@link WeightHandler} then falls back to the legacy attack-damage / defense proxy so nothing
 * breaks for oddly-named endgame weapons — those get a per-id override entry over time.
 */
public final class MaterialWeight {

    private MaterialWeight() {
    }

    /** Registry id string (namespace:path) for a stack, or null. */
    static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? null : key.toString();
    }

    /**
     * Material density factor (iron = 10, real-world density scaled). Returns {@link Double#NaN}
     * when the id carries no recognizable material keyword (caller falls back to the legacy proxy).
     * Order matters: more-specific keywords are tested before their substrings.
     */
    static double materialFactor(String id) {
        if (id == null) {
            return Double.NaN;
        }
        // more specific first
        if (has(id, "netherite")) return 16.0;
        if (has(id, "copper"))    return 11.0;
        if (has(id, "golden") || has(id, "gold")) return 24.0; // heaviest, but softest (see damage)
        if (has(id, "silver"))    return 13.0;
        if (has(id, "lead"))      return 14.0;
        if (has(id, "diamond"))   return 4.0;  // as light as stone, hardest
        if (has(id, "emerald"))   return 4.0;
        if (has(id, "mithril"))   return 6.0;  // legendary light metal
        if (has(id, "steel"))     return 10.0;
        if (has(id, "iron"))      return 10.0;
        if (has(id, "obsidian"))  return 3.0;
        if (has(id, "stone") || has(id, "flint") || has(id, "cobbled")
                || has(id, "deepslate") || has(id, "blackstone")) return 4.0;
        if (has(id, "wooden") || has(id, "wood") || has(id, "bamboo") || has(id, "plank")) return 1.0;
        if (has(id, "leather")) return 1.0;
        return Double.NaN;
    }

    /**
     * Weapon size factor (physical bulk → both weight and one-hit damage). Resolved from the id;
     * hafted weapons (spear/scythe/polearm) are discounted for their wooden shaft. Falls back to
     * 0.6 (one-hand sword) for unrecognized weapon shapes. Great* variants are tested before their
     * base word so "greatsword" is not caught by "sword".
     */
    static double sizeFactor(String id) {
        if (id == null) {
            return 0.6;
        }
        if (hasAny(id, "greathammer", "great_hammer", "warhammer", "war_hammer", "greatmaul", "great_maul")) return 1.8;
        if (hasAny(id, "greatsword", "great_sword", "claymore", "zweihander", "greataxe", "great_axe", "greatblade")) return 1.5;
        if (hasAny(id, "halberd", "glaive", "naginata", "warglaive", "poleaxe", "polearm", "polearm", "guandao")) return 1.0;
        if (hasAny(id, "hammer", "maul"))                 return 1.2;
        if (hasAny(id, "mace", "flail", "morningstar"))   return 0.9;
        if (hasAny(id, "battle_axe", "battleaxe", "axe")) return 0.8;
        if (hasAny(id, "scythe", "sickle"))               return 0.8;
        if (hasAny(id, "spear", "lance", "pike", "trident", "javelin", "yari")) return 0.7;
        if (hasAny(id, "katana", "tachi", "uchigatana", "longsword", "long_sword",
                "saber", "sabre", "machete", "nodachi")) return 0.7;
        if (hasAny(id, "rapier", "estoc"))                return 0.5;
        if (hasAny(id, "dagger", "knife", "sai", "claw", "kunai", "tanto", "shortsword", "short_sword")) return 0.3;
        if (hasAny(id, "sword", "blade", "cutlass"))      return 0.6;
        return 0.6; // default: treat like a one-hand sword
    }

    /** Armor slot bulk factor. Full 4-piece set sums to 1.0 (so a set weighs its material density). */
    static double pieceFactor(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 0.2;
            case CHEST -> 0.4;
            case LEGS -> 0.3;
            case FEET -> 0.1;
            default -> 0.0;
        };
    }

    /**
     * Reality weight of a held weapon = material × size, or {@link Double#NaN} when the material is
     * unresolved (caller falls back). Caller is responsible for the "is this actually a weapon"
     * check (so pickaxes/shovels never reach here as weapons).
     */
    static double weaponWeight(ItemStack stack) {
        String id = idOf(stack);
        if (id == null) {
            return Double.NaN;
        }
        double mat = materialFactor(id);
        if (Double.isNaN(mat)) {
            return Double.NaN;
        }
        return mat * sizeFactor(id);
    }

    /**
     * Reality weight of one worn armor piece = material × piece factor, or {@link Double#NaN} when
     * the material is unresolved (caller falls back to that piece's defense proxy).
     */
    static double armorPieceWeight(ItemStack stack, EquipmentSlot slot) {
        String id = idOf(stack);
        if (id == null) {
            return Double.NaN;
        }
        double mat = materialFactor(id);
        if (Double.isNaN(mat)) {
            return Double.NaN;
        }
        return mat * pieceFactor(slot);
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
