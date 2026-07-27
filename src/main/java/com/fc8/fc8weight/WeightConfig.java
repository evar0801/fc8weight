package com.fc8.fc8weight;

import java.util.List;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Common config for the weight + strength-gate systems. All tunables live here so balance can be
 * iterated without recompiling. Values are cached only transitively (ForgeConfigSpec caches the
 * parsed value); {@link #invalidate()} exists for symmetry with the (re)load events.
 */
public final class WeightConfig {

    public static final ForgeConfigSpec SPEC;

    // --- master ---
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue SKIP_CREATIVE;

    // --- weight (encumbrance → movement slowdown) ---
    public static final ForgeConfigSpec.BooleanValue WEIGHT_ENABLED;
    public static final ForgeConfigSpec.DoubleValue FREE_WEIGHT;
    public static final ForgeConfigSpec.BooleanValue STR_ALLOWANCE_ENABLED;
    public static final ForgeConfigSpec.DoubleValue STR_ALLOWANCE_PER_POINT;
    public static final ForgeConfigSpec.DoubleValue STR_ALLOWANCE_BASE;

    // --- ECM class efficiency (alloc_str -> gate strength / carry allowance conversion rate) ---
    public static final ForgeConfigSpec.DoubleValue CLASS_EFFICIENCY_WARRIOR;
    public static final ForgeConfigSpec.DoubleValue CLASS_EFFICIENCY_BERSERKER;
    public static final ForgeConfigSpec.DoubleValue CLASS_EFFICIENCY_ARCHER;
    public static final ForgeConfigSpec.DoubleValue CLASS_EFFICIENCY_REAPER;
    public static final ForgeConfigSpec.DoubleValue CLASS_EFFICIENCY_SORCERER;
    public static final ForgeConfigSpec.DoubleValue CLASS_EFFICIENCY_PALADIN;
    public static final ForgeConfigSpec.DoubleValue CLASS_EFFICIENCY_DEFAULT;
    public static final ForgeConfigSpec.DoubleValue WEAPON_WEIGHT_FACTOR;
    public static final ForgeConfigSpec.DoubleValue SLOW_PER_WEIGHT;
    public static final ForgeConfigSpec.DoubleValue MAX_WEIGHT_SLOW;
    public static final ForgeConfigSpec.DoubleValue TOTAL_MAX_MOVE_SLOW;

    // --- strength gate ---
    public static final ForgeConfigSpec.BooleanValue GATE_ENABLED;
    public static final ForgeConfigSpec.DoubleValue BASE_STRENGTH;
    public static final ForgeConfigSpec.DoubleValue WARRIOR_BASE_STRENGTH;
    public static final ForgeConfigSpec.DoubleValue BERSERKER_BASE_STRENGTH;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GATE_EXEMPT_ITEMS;
    public static final ForgeConfigSpec.DoubleValue MIN_WEAPON_DAMAGE_TO_GATE;
    public static final ForgeConfigSpec.BooleanValue CANCEL_WHEN_UNDERPOWERED;

    // --- tier-table strength requirement (2026-07-27 model) ---
    public static final ForgeConfigSpec.DoubleValue REQ_IRON_SWORD;
    public static final ForgeConfigSpec.DoubleValue REQ_GOLD_SWORD;
    public static final ForgeConfigSpec.DoubleValue REQ_DIAMOND_SWORD;
    public static final ForgeConfigSpec.DoubleValue REQ_NETHERITE_SWORD;
    public static final ForgeConfigSpec.DoubleValue REQ_MULT_IRON;
    public static final ForgeConfigSpec.DoubleValue REQ_MULT_GOLD;
    public static final ForgeConfigSpec.DoubleValue REQ_MULT_DIAMOND;
    public static final ForgeConfigSpec.DoubleValue REQ_MULT_NETHERITE;
    public static final ForgeConfigSpec.DoubleValue REQ_SIZE_LIGHT;
    public static final ForgeConfigSpec.DoubleValue REQ_SIZE_MEDIUM;
    public static final ForgeConfigSpec.DoubleValue REQ_SIZE_HEAVY;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_FROM_STRENGTH_FACTOR;

    // --- weaponmaster stamina gate (DawnCraft-style: their vanilla sweep costs EF stamina) ---
    public static final ForgeConfigSpec.BooleanValue WM_STAMINA_ENABLED;
    public static final ForgeConfigSpec.DoubleValue WM_STAMINA_COST;
    public static final ForgeConfigSpec.IntValue WM_STAMINA_REGEN_DELAY;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WM_NO_VANILLA_ITEMS;

    // --- held-weapon tier movement penalty (task3) ---
    public static final ForgeConfigSpec.BooleanValue HELD_WEAPON_SLOW_ENABLED;
    public static final ForgeConfigSpec.DoubleValue HELD_HEAVY_SLOW;
    public static final ForgeConfigSpec.DoubleValue HELD_MEDIUM_SLOW;
    public static final ForgeConfigSpec.DoubleValue HELD_LIGHT_SLOW;
    public static final ForgeConfigSpec.DoubleValue HELD_HEAVY_DAMAGE_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue HELD_MEDIUM_DAMAGE_THRESHOLD;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> HELD_HEAVY_CATEGORIES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> HELD_MEDIUM_CATEGORIES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> HELD_LIGHT_CATEGORIES;

    // --- backpack over-carry penalty (Sophisticated Backpacks: too many backpacks slow you) ---
    public static final ForgeConfigSpec.BooleanValue BACKPACK_ENABLED;
    public static final ForgeConfigSpec.IntValue BACKPACK_FREE_COUNT;
    public static final ForgeConfigSpec.DoubleValue BACKPACK_SLOW_PER_EXTRA;
    public static final ForgeConfigSpec.DoubleValue BACKPACK_MAX_SLOW;
    public static final ForgeConfigSpec.BooleanValue BACKPACK_COUNT_QIO;
    public static final ForgeConfigSpec.BooleanValue BACKPACK_COUNT_CURIOS;
    public static final ForgeConfigSpec.BooleanValue BACKPACK_COUNT_MEK_STORAGE;
    public static final ForgeConfigSpec.IntValue BACKPACK_NOTIFY_TICKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BACKPACK_ITEMS;

    // --- armor-defense movement penalty (heavier defense = slower; linear 1%/pt, buffs still scale) ---
    public static final ForgeConfigSpec.BooleanValue ARMOR_DEF_SLOW_ENABLED;
    public static final ForgeConfigSpec.DoubleValue ARMOR_DEF_SLOW_PER_POINT;
    public static final ForgeConfigSpec.DoubleValue ARMOR_DEF_MAX_SLOW;

    // --- armor strength gate (③: strength < total defense -> heavy fixed movement penalty) ---
    public static final ForgeConfigSpec.BooleanValue ARMOR_STR_GATE_ENABLED;
    public static final ForgeConfigSpec.DoubleValue ARMOR_STR_GATE_DEFENSE_FACTOR;
    public static final ForgeConfigSpec.DoubleValue ARMOR_STR_GATE_SLOW;

    // --- reality damage override (MVP: vanilla swords) ---
    public static final ForgeConfigSpec.BooleanValue DAMAGE_OVERRIDE_ENABLED;

    // --- tooltip (client-side visibility) ---
    public static final ForgeConfigSpec.BooleanValue TOOLTIP_ENABLED;

    /** weaponmaster melee weapons whose vanilla (non-EF-combo) sweep damage is removed. */
    private static final List<String> DEFAULT_WM_NO_VANILLA = List.of(
            "weaponmaster:wm_broadsword",
            "weaponmaster:wm_broadswordlarge",
            "weaponmaster:wm_rapier",
            "weaponmaster:wm_rapierlarge");

    /** Epic Fight weapon categories (WeaponCategories enum name) classed as HEAVY (~-40% held slow). */
    private static final List<String> DEFAULT_HEAVY_CATEGORIES = List.of(
            "GREATSWORD");
    /** Epic Fight weapon categories classed as MEDIUM (~-25% held slow). */
    private static final List<String> DEFAULT_MEDIUM_CATEGORIES = List.of(
            "SWORD", "TACHI", "UCHIGATANA", "LONGSWORD", "SPEAR", "AXE", "TRIDENT");
    /** Epic Fight weapon categories classed as LIGHT (~-10% held slow). */
    private static final List<String> DEFAULT_LIGHT_CATEGORIES = List.of(
            "DAGGER", "FIST");

    /** Sophisticated Backpacks backpack item ids counted toward the over-carry penalty (4 tiers). */
    private static final List<String> DEFAULT_BACKPACK_ITEMS = List.of(
            "sophisticatedbackpacks:copper_backpack",
            "sophisticatedbackpacks:iron_backpack",
            "sophisticatedbackpacks:gold_backpack",
            "sophisticatedbackpacks:diamond_backpack");

    /** EpicClassMod job starter weapons (stone, ChooseClassPacket) + first quest rewards (iron, ClassWeaponRewards). */
    private static final List<String> DEFAULT_GATE_EXEMPT = List.of(
            "epicfight:stone_tachi",       // WARRIOR starter
            "epicfight:stone_greatsword",  // BERSERKER starter — stone is requirement 0 since the tier
                                           // rebuild, so this entry (and every stone one) is now redundant.
                                           // Kept because the list is what a pack author edits, not code:
                                           // deleting it would silently break any pack that re-tiers stone.
            "epicfight:stone_dagger",      // REAPER starter
            "minecraft:stone_sword",       // PALADIN starter
            "epicfight:iron_tachi",        // WARRIOR quest reward
            "epicfight:iron_greatsword",   // BERSERKER quest reward (tier table: requirement 16)
            "epicfight:iron_dagger",       // REAPER quest reward
            "epicfight:iron_longsword");   // PALADIN quest reward
    public static final ForgeConfigSpec.DoubleValue OVERBURDEN_MOVE_SLOW_PER_DEFICIT;
    public static final ForgeConfigSpec.DoubleValue OVERBURDEN_MAX_MOVE_SLOW;
    public static final ForgeConfigSpec.DoubleValue OVERBURDEN_ATTACK_SLOW_PER_DEFICIT;
    public static final ForgeConfigSpec.DoubleValue OVERBURDEN_MAX_ATTACK_SLOW;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Weight & Strength settings").push("general");

        ENABLED = b.comment("Master toggle for both the weight and strength-gate systems.")
                .define("enabled", true);
        SKIP_CREATIVE = b.comment("If true, creative-mode players are never slowed or gated.")
                .define("skipCreative", true);

        b.pop();
        b.comment("Encumbrance: equipped armor defense + held weapon base weight slow movement.")
                .push("weight");

        WEIGHT_ENABLED = b.comment("Toggle the movement-slowing weight system.")
                .define("weightEnabled", true);
        FREE_WEIGHT = b
                .comment("Total weight (armor defense + weapon weight) carried with NO penalty.",
                        "Reference: iron set total armor = 15, diamond = 20, fantasy_armor = 44.")
                .defineInRange("freeWeight", 15.0D, 0.0D, 1000.0D);
        STR_ALLOWANCE_ENABLED = b
                .comment("If true, the no-penalty weight allowance is derived from EpicClassMod alloc_str",
                        "instead of the static freeWeight above:",
                        "allowance = alloc_str * classEfficiency(job) * strAllowancePerPoint + strAllowanceBase.")
                .define("strAllowanceEnabled", true);
        STR_ALLOWANCE_PER_POINT = b
                .comment("Allowance granted per (alloc_str * classEfficiency) point (1.0 = 力の値そのまま).",
                        "Per-class scaling now lives entirely in the [classEfficiency] section below —",
                        "the old berserkerAllowancePerPoint/berserkerAllowanceBase keys are gone (job",
                        "difference is classEfficiency itself, applied identically to gate strength and",
                        "carry allowance).")
                .defineInRange("strAllowancePerPoint", 1.0D, 0.0D, 100.0D);
        STR_ALLOWANCE_BASE = b
                .comment("Flat allowance added on top of the STR-based allowance (safety floor, 0 = none),",
                        "applied identically to every class.",
                        "D-103 pending: 15.0 proposed (without it, STR0 players get -7.5% speed from iron armor).")
                .defineInRange("strAllowanceBase", 0.0D, 0.0D, 1000.0D);
        WEAPON_WEIGHT_FACTOR = b
                .comment("How much the held weapon's base attack damage counts toward weight (1.0 = 1:1).")
                .defineInRange("weaponWeightFactor", 1.0D, 0.0D, 100.0D);
        SLOW_PER_WEIGHT = b
                .comment("Fraction of movement speed lost per point of weight above freeWeight.",
                        "0.005 = 0.5% per point (e.g. 20 weight over free = -10%).")
                .defineInRange("slowPerWeight", 0.005D, 0.0D, 1.0D);
        MAX_WEIGHT_SLOW = b
                .comment("Cap on the weight movement penalty (0.99 = down to 1% of normal speed).",
                        "Effectively uncapped: modpack speed buffs reach several hundred percent, so a",
                        "low cap made heavy armor free. 0.99 rather than 1.0 is deliberate — the penalty",
                        "is a MULTIPLY_TOTAL modifier, and -1.0 multiplies speed to a hard 0 that no buff",
                        "can ever recover from (0 * anything = 0), stranding the player permanently.")
                .defineInRange("maxWeightSlow", 0.99D, 0.0D, 0.99D);
        TOTAL_MAX_MOVE_SLOW = b
                .comment("Cap on ALL of this mod's movement penalties combined (0.99 = down to 1% of normal).",
                        "Each system (weight / overburden / held weapon / backpack / armor defense / armor",
                        "strength gate) is capped on its own, but MULTIPLY_TOTAL modifiers compose by",
                        "multiplication: six systems at their individual caps reach 0.0000084x, which is a",
                        "standstill in everything but name. Per-modifier caps cannot express a limit on their",
                        "product, so the six are composed first and this single cap is applied to the result.")
                .defineInRange("totalMaxMoveSlow", 0.99D, 0.0D, 0.99D);

        b.pop();
        b.comment("Strength gate: heavy weapons require gate strength (classBaseStrength + alloc_str *",
                        "classEfficiency, see [classEfficiency] below) to wield.")
                .push("strength");

        GATE_ENABLED = b.comment("Toggle the strength-gate system.")
                .define("gateEnabled", true);
        // strRequirementFactor was removed in the 2026-07-27 tier-table rewrite: the requirement is no
        // longer "attack damage × a factor", so there is nothing left for the factor to multiply.
        // See docs/DESIGN_LOG.md for why the old model was replaced rather than tuned.
        BASE_STRENGTH = b
                .comment("Innate strength every player has for FREE, added on top of the alloc_str-derived",
                        "strength: gateStrength = classBaseStrength + alloc_str * classEfficiency(job).",
                        "(ECM's own attack_damage bonus from STR was removed on the ECM side; the gate no",
                        "longer reads attack_damage at all — see classEfficiency below.)",
                        "3.0 → at zero allocation only the wood/stone tier (requirement 0) is wieldable;",
                        "an iron sword (requirement 10) needs 7 more points. Early characters are meant to be",
                        "unable to equip metal — that is what makes gear worth investing strength into.",
                        "This is the DEFAULT for classes without a class-specific override below (paladin/reaper/sorcerer/archer/none).")
                .defineInRange("baseStrength", 3.0D, 0.0D, 1000.0D);
        WARRIOR_BASE_STRENGTH = b
                .comment("Innate FREE strength for WARRIOR-class players (ecm_class_name = WARRIOR).",
                        "Replaces baseStrength for warriors only. 5.0 at classEfficiency 1.0 → an iron sword",
                        "(requirement 10) opens at 5 allocated points, a gold sword (20) at 15. The generalist:",
                        "steady, unremarkable progress up the material ladder.",
                        "Applies server-side to the gate (authoritative); the client tooltip reads the same class",
                        "via EpicClassMod's own client-synced state, so it agrees even on a dedicated server.")
                .defineInRange("warriorBaseStrength", 5.0D, 0.0D, 1000.0D);
        BERSERKER_BASE_STRENGTH = b
                .comment("Innate FREE strength for BERSERKER-class players (ecm_class_name = BERSERKER).",
                        "Replaces baseStrength for berserkers only. 9.0 at classEfficiency 2.0 → an iron sword",
                        "(requirement 10) opens at 1 point and an iron greatsword (16) at 4 — the class-leading",
                        "heavy-weapon wielder. Diamond (50) and netherite (100) still demand real growth.",
                        "Same server-side/tooltip agreement note as warriorBaseStrength.")
                .defineInRange("berserkerBaseStrength", 9.0D, 0.0D, 1000.0D);

        b.pop();
        b.comment("Class efficiency: how efficiently each EpicClassMod job converts alloc_str points into",
                        "gate strength AND carry allowance (the same multiplier drives both — see",
                        "docs/DESIGN_LOG.md). gateStrength = classBaseStrength + alloc_str * classEfficiency;",
                        "carryAllowance = alloc_str * classEfficiency * strAllowancePerPoint + strAllowanceBase.",
                        "Read from ecm_class_name (persistent NBT), same source as classBaseStrength above.")
                .push("classEfficiency");

        CLASS_EFFICIENCY_WARRIOR = b
                .comment("STR efficiency for WARRIOR (ecm_class_name = WARRIOR).")
                .defineInRange("warrior", 1.0D, 0.0D, 100.0D);
        CLASS_EFFICIENCY_BERSERKER = b
                .comment("STR efficiency for BERSERKER — converts strength into gate power (and carry capacity)",
                        "at double rate, the class-leading heavy-weapon/heavy-armor wielder.")
                .defineInRange("berserker", 2.0D, 0.0D, 100.0D);
        CLASS_EFFICIENCY_ARCHER = b
                .comment("STR efficiency for ARCHER (レンジャー/精密軸).")
                .defineInRange("archer", 0.75D, 0.0D, 100.0D);
        CLASS_EFFICIENCY_REAPER = b
                .comment("STR efficiency for REAPER (双剣/連撃軸).")
                .defineInRange("reaper", 0.75D, 0.0D, 100.0D);
        CLASS_EFFICIENCY_SORCERER = b
                .comment("STR efficiency for SORCERER (魔導寄り、装備は軽量前提).")
                .defineInRange("sorcerer", 0.5D, 0.0D, 100.0D);
        CLASS_EFFICIENCY_PALADIN = b
                .comment("STR efficiency for PALADIN.")
                .defineInRange("paladin", 1.0D, 0.0D, 100.0D);
        CLASS_EFFICIENCY_DEFAULT = b
                .comment("STR efficiency for NONE / unrecognized classes (fallback).")
                .defineInRange("otherDefault", 1.0D, 0.0D, 100.0D);

        b.pop();
        b.push("strength");

        GATE_EXEMPT_ITEMS = b
                .comment("Item ids fully exempt from the strength gate (no attack cancel, no overburden penalty).",
                        "Default: EpicClassMod job starter weapons (stone tier) and first quest reward weapons (iron tier).",
                        "The weight/encumbrance system still applies to these items.")
                .defineListAllowEmpty(List.of("gateExemptItems"), () -> DEFAULT_GATE_EXEMPT,
                        o -> o instanceof String s && s.contains(":"));
        MIN_WEAPON_DAMAGE_TO_GATE = b
                .comment("Weapons whose base attack damage is below this are never gated",
                        "(keeps low-damage tools like pickaxe/shovel/hoe unaffected).")
                .defineInRange("minWeaponDamageToGate", 3.0D, 0.0D, 1000.0D);
        CANCEL_WHEN_UNDERPOWERED = b
                .comment("If true, EpicFight basic attacks are canceled while strength < requirement.",
                        "If false, only the holding movement/attack-speed penalty applies (soft gate).")
                .define("cancelWhenUnderpowered", true);

        // Tier table: requirement = <material>Sword + (sizeBonus - reqSizeLight) * mult<material>.
        // A plain sword IS the light class, so each *Sword value below is literally that material's
        // sword price; iron 10 anchors the whole ladder (including modded iron-tier weapons).
        REQ_IRON_SWORD = b.comment("Strength to wield a vanilla iron sword. Anchor of the whole ladder.")
                .defineInRange("reqIronSword", 10.0D, 0.0D, 10000.0D);
        REQ_GOLD_SWORD = b.comment("Strength to wield a vanilla golden sword.")
                .defineInRange("reqGoldSword", 20.0D, 0.0D, 10000.0D);
        REQ_DIAMOND_SWORD = b.comment("Strength to wield a vanilla diamond sword.")
                .defineInRange("reqDiamondSword", 50.0D, 0.0D, 10000.0D);
        REQ_NETHERITE_SWORD = b.comment("Strength to wield a vanilla netherite sword.")
                .defineInRange("reqNetheriteSword", 100.0D, 0.0D, 10000.0D);
        REQ_MULT_IRON = b.comment("How hard the size axis bites on iron-tier materials.")
                .defineInRange("reqMultIron", 1.0D, 0.0D, 100.0D);
        REQ_MULT_GOLD = b.comment("How hard the size axis bites on gold.")
                .defineInRange("reqMultGold", 1.0D, 0.0D, 100.0D);
        REQ_MULT_DIAMOND = b.comment("How hard the size axis bites on diamond-tier materials.")
                .defineInRange("reqMultDiamond", 2.0D, 0.0D, 100.0D);
        REQ_MULT_NETHERITE = b.comment("How hard the size axis bites on netherite.")
                .defineInRange("reqMultNetherite", 3.0D, 0.0D, 100.0D);
        REQ_SIZE_LIGHT = b.comment("Size bonus for one-hand sword class weapons (the anchor size).")
                .defineInRange("reqSizeLight", 2.0D, 0.0D, 1000.0D);
        REQ_SIZE_MEDIUM = b.comment("Size bonus for katana/axe/spear class weapons.")
                .defineInRange("reqSizeMedium", 4.0D, 0.0D, 1000.0D);
        REQ_SIZE_HEAVY = b.comment("Size bonus for greatsword/hammer/polearm class weapons.")
                .defineInRange("reqSizeHeavy", 8.0D, 0.0D, 1000.0D);

        OVERBURDEN_MOVE_SLOW_PER_DEFICIT = b
                .comment("Extra movement slow per point of strength shortfall while holding a too-heavy weapon.")
                .defineInRange("overburdenMoveSlowPerDeficit", 0.02D, 0.0D, 1.0D);
        OVERBURDEN_MAX_MOVE_SLOW = b
                .comment("Cap on the over-heavy-weapon movement penalty (stacks with weight system).")
                .defineInRange("overburdenMaxMoveSlow", 0.30D, 0.0D, 0.90D);
        OVERBURDEN_ATTACK_SLOW_PER_DEFICIT = b
                .comment("Attack-speed slow per point of strength shortfall while holding a too-heavy weapon.")
                .defineInRange("overburdenAttackSlowPerDeficit", 0.03D, 0.0D, 1.0D);
        OVERBURDEN_MAX_ATTACK_SLOW = b
                .comment("Cap on the over-heavy-weapon attack-speed penalty.")
                .defineInRange("overburdenMaxAttackSlow", 0.50D, 0.0D, 0.90D);

        b.pop();
        b.comment("Weaponmaster stamina gate (DawnCraft-style). javap of weaponmaster's SweeperWeapon",
                        "confirmed its four melee weapons deal ALL damage through their own AoE sweep hitscan",
                        "with a plain minecraft:player_attack DamageSource (SweeperWeapon#processHits →",
                        "LivingEntity#hurt), bypassing Epic Fight's BASIC_ATTACK_EVENT entirely. That means",
                        "efstamina's stamina cost AND the strength gate above never see these swings — they",
                        "can be spam-swung for full damage at zero stamina (trivialises trap-tower farming).",
                        "This section charges Epic Fight stamina per weaponmaster swing (once per swing, not",
                        "once per victim hit by the AoE); when stamina is insufficient the swing is canceled.")
                .push("weaponmaster");

        WM_STAMINA_ENABLED = b
                .comment("Toggle charging Epic Fight stamina for weaponmaster weapon swings.")
                .define("weaponmasterStaminaEnabled", true);
        WM_STAMINA_COST = b
                .comment("Stamina spent per weaponmaster swing (the whole AoE sweep, charged once).",
                        "Reference efstamina costs: SWORD=4, LONGSWORD=5, GREATSWORD=8. These are high-damage",
                        "AoE cleave weapons, so 8.0 (greatsword-tier) discourages spam. 0 = free (gate off).")
                .defineInRange("weaponmasterStaminaCost", 8.0D, 0.0D, 1000.0D);
        WM_STAMINA_REGEN_DELAY = b
                .comment("Ticks before stamina begins regenerating after a weaponmaster swing (efstamina uses 10).")
                .defineInRange("weaponmasterStaminaRegenDelayTicks", 10, 0, 200);
        WM_NO_VANILLA_ITEMS = b
                .comment("weaponmaster item ids whose swing costs stamina.",
                        "Default: the four weaponmaster melee weapons (broadsword / broadswordlarge / rapier / rapierlarge).")
                .defineListAllowEmpty(List.of("weaponmasterStaminaItems"), () -> DEFAULT_WM_NO_VANILLA,
                        o -> o instanceof String s && s.contains(":"));

        b.pop();
        b.comment("Held-weapon tier slow: holding a weapon slows movement by an amount set by its tier.",
                        "Independent of the encumbrance 'weight' system (a separate MULTIPLY_TOTAL modifier).",
                        "Tier is resolved by Epic Fight weapon category first, then by attack-damage threshold.")
                .push("heldWeaponSlow");

        HELD_WEAPON_SLOW_ENABLED = b
                .comment("Toggle the held-weapon tier movement penalty.")
                .define("heldWeaponSlowEnabled", true);
        HELD_HEAVY_SLOW = b
                .comment("Movement slow while holding a HEAVY weapon (0.40 = -40%). 大剣/大鎌/大槍/大槌 系.")
                .defineInRange("heldHeavySlow", 0.40D, 0.0D, 0.90D);
        HELD_MEDIUM_SLOW = b
                .comment("Movement slow while holding a MEDIUM weapon (0.25 = -25%). 太刀/長剣/槍/斧 系.")
                .defineInRange("heldMediumSlow", 0.25D, 0.0D, 0.90D);
        HELD_LIGHT_SLOW = b
                .comment("Movement slow while holding a LIGHT weapon (0.10 = -10%). 短剣/拳 系.")
                .defineInRange("heldLightSlow", 0.10D, 0.0D, 0.90D);
        HELD_HEAVY_DAMAGE_THRESHOLD = b
                .comment("Fallback tiering when the Epic Fight category is unknown/absent:",
                        "weapon base attack damage >= this → HEAVY.")
                .defineInRange("heldHeavyDamageThreshold", 11.0D, 0.0D, 1000.0D);
        HELD_MEDIUM_DAMAGE_THRESHOLD = b
                .comment("Fallback: weapon base attack damage >= this (and < heavy threshold) → MEDIUM;",
                        "below this (but >= minWeaponDamageToGate) → LIGHT.")
                .defineInRange("heldMediumDamageThreshold", 7.0D, 0.0D, 1000.0D);
        HELD_HEAVY_CATEGORIES = b
                .comment("Epic Fight WeaponCategories enum names treated as HEAVY.",
                        "大鎌/大槍/大槌 がアドオン独自カテゴリで拾えない場合はダメージしきい値の fallback が効く。")
                .defineListAllowEmpty(List.of("heldHeavyCategories"), () -> DEFAULT_HEAVY_CATEGORIES,
                        o -> o instanceof String);
        HELD_MEDIUM_CATEGORIES = b
                .comment("Epic Fight WeaponCategories enum names treated as MEDIUM.")
                .defineListAllowEmpty(List.of("heldMediumCategories"), () -> DEFAULT_MEDIUM_CATEGORIES,
                        o -> o instanceof String);
        HELD_LIGHT_CATEGORIES = b
                .comment("Epic Fight WeaponCategories enum names treated as LIGHT.")
                .defineListAllowEmpty(List.of("heldLightCategories"), () -> DEFAULT_LIGHT_CATEGORIES,
                        o -> o instanceof String);

        b.pop();
        b.comment("Backpack over-carry (Sophisticated Backpacks): carrying more than one backpack slows",
                        "movement by a per-extra fraction. Counts backpacks anywhere the player can reach:",
                        "main inventory + nested item handlers (backpack-in-backpack, Curios, etc.) + ender",
                        "chest + the player's PRIVATE Mekanism QIO network (public/shared QIO frequencies are",
                        "out of scope). A separate MULTIPLY_TOTAL modifier that composes with the weight system.")
                .push("backpack");

        BACKPACK_ENABLED = b.comment("Toggle the backpack over-carry movement penalty.")
                .define("backpackEnabled", true);
        BACKPACK_FREE_COUNT = b
                .comment("Number of backpacks carried with NO penalty (1 = only the 2nd+ backpack slows you).")
                .defineInRange("backpackFreeCount", 1, 0, 64);
        BACKPACK_SLOW_PER_EXTRA = b
                .comment("Movement slow added per backpack beyond backpackFreeCount (0.15 = -15% each).")
                .defineInRange("backpackSlowPerExtra", 0.15D, 0.0D, 0.90D);
        BACKPACK_MAX_SLOW = b
                .comment("Hard cap on the backpack over-carry penalty (0.90 = at most -90% move speed).")
                .defineInRange("backpackMaxSlow", 0.90D, 0.0D, 0.90D);
        BACKPACK_COUNT_QIO = b
                .comment("If true and Mekanism is installed, also count backpacks stored in the player's",
                        "private QIO network (closes the 'hide spare backpacks in QIO' loophole).")
                .define("backpackCountQio", true);
        BACKPACK_COUNT_CURIOS = b
                .comment("If true and Curios is installed, also count backpacks equipped in Curios slots",
                        "(closes the 'wear a spare backpack in the back slot' loophole).")
                .define("backpackCountCurios", true);
        BACKPACK_COUNT_MEK_STORAGE = b
                .comment("If true and Mekanism is installed, also count backpacks stored inside Mekanism",
                        "Personal Chest / Personal Barrel items (their 54-slot inventory lives in server",
                        "saved data, not the item NBT, so it is otherwise invisible to the weight system —",
                        "closes the 'hide spare backpacks in a portable personal chest' loophole).")
                .define("backpackCountMekPersonalStorage", true);
        BACKPACK_NOTIFY_TICKS = b
                .comment("How long (ticks) the action-bar over-carry message stays after the count changes.",
                        "200 = 10 seconds. Only shown when the penalty step changes, then fades.")
                .defineInRange("backpackNotifyTicks", 200, 0, 6000);
        BACKPACK_ITEMS = b
                .comment("Backpack item ids counted toward the penalty. Default: the 4 SBP backpack tiers.")
                .defineListAllowEmpty(List.of("backpackItems"), () -> DEFAULT_BACKPACK_ITEMS,
                        o -> o instanceof String s && s.contains(":"));

        b.pop();
        b.comment("Armor-defense movement penalty: every point of defense (Attributes.ARMOR — armor slots",
                        "AND Curios accessories that grant armor, summed via getAttributeValue) slows movement",
                        "by armorDefenseSlowPerPoint (LINEAR). Composes multiplicatively with weight/held/backpack",
                        "(all MULTIPLY_TOTAL). Design intent: heavy defenders are slow ON PURPOSE and lean on",
                        "temporary speed buffs — so this is deliberately NOT capped low. The only clamp",
                        "(armorDefenseMaxSlow, default 0.99) exists to keep at least 1% speed: a literal 0 would",
                        "freeze the player AND make speed buffs useless (0 * buff = 0).")
                .push("armorDefense");
        ARMOR_DEF_SLOW_ENABLED = b.comment("Toggle the armor-defense movement penalty.")
                .define("armorDefenseSlowEnabled", true);
        ARMOR_DEF_SLOW_PER_POINT = b
                .comment("Movement slow per point of total defense (0.01 = -1% per armor point, LINEAR).",
                        "e.g. defense 20 -> -20%, 50 -> -50%, 80 -> -80% (before the armorDefenseMaxSlow clamp).")
                .defineInRange("armorDefenseSlowPerPoint", 0.01D, 0.0D, 1.0D);
        ARMOR_DEF_MAX_SLOW = b
                .comment("Safety clamp so movement never reaches 0 (which would freeze the player and nullify",
                        "speed buffs). 0.99 = keep at least 1% speed. This is a floor-guard, NOT a balance cap.")
                .defineInRange("armorDefenseMaxSlow", 0.99D, 0.0D, 0.99D);

        ARMOR_STR_GATE_ENABLED = b
                .comment("③ Armor strength gate: if the player's gate strength (classBaseStrength + alloc_str *",
                        "classEfficiency, same value as the weapon gate) is LESS than their total defense",
                        "(Attributes.ARMOR * armorStrGateDefenseFactor), apply a heavy FIXED movement penalty",
                        "(armorStrGateSlow) so the armor is effectively 'too heavy to wear'. Composes with the",
                        "linear armorDefenseSlow above. Note: Attributes.ARMOR includes ALL defense sources",
                        "(armor slots + Curios + any mod/effect that grants ARMOR, incl. ECM 堅牢/DEF if it adds",
                        "ARMOR) — tune armorStrGateDefenseFactor if that coupling feels too strict.")
                .define("armorStrGateEnabled", true);
        ARMOR_STR_GATE_DEFENSE_FACTOR = b
                .comment("Strength requirement = total defense * this factor. 1.0 = need strength >= defense",
                        "(e.g. diamond set defense 20 needs strength 20; iron 15 needs 15; leather 7 needs 7).",
                        "Lower it to loosen the gate (0.5 = need strength >= half the defense value).")
                .defineInRange("armorStrGateDefenseFactor", 1.0D, 0.0D, 100.0D);
        ARMOR_STR_GATE_SLOW = b
                .comment("Fixed movement slow applied while under-strength for the worn armor (0.80 = -80%).",
                        "Deliberately harsh: the intent is 'you physically can't move well in armor too heavy",
                        "for you', not a mild tax. 0 disables the effect (same as armorStrGateEnabled=false).")
                .defineInRange("armorStrGateSlow", 0.80D, 0.0D, 0.99D);

        b.pop();
        b.comment("Reality-based attack damage override (MVP: vanilla swords only, the tier anchor).")
                .push("damage");
        DAMAGE_OVERRIDE_ENABLED = b
                .comment("Override vanilla sword attack damage to the reality anchor",
                        "(wood3 / stone4 / iron5 / gold4 / diamond6 / netherite7). MVP scope = vanilla swords",
                        "only; modded weapons keep their own damage until per-item entries are added.")
                .define("damageOverrideEnabled", true);
        DAMAGE_FROM_STRENGTH_FACTOR = b
                .comment("Bonus attack damage granted to a weapon = its strength requirement * this.",
                        "0.5 means the iron sword (requirement 10) gains +5 attack damage.",
                        "Set to 0 to disable the bonus without touching the requirement table.")
                .defineInRange("damageFromStrengthFactor", 0.5D, 0.0D, 10.0D);

        b.pop();
        b.comment("Item tooltip: show weapon weight, required strength and the player's own strength.")
                .push("tooltip");

        TOOLTIP_ENABLED = b.comment("Append '必要筋骨 / あなたの筋骨' lines to weapon tooltips (client side).")
                .define("tooltipEnabled", true);

        b.pop();
        SPEC = b.build();
    }

    private WeightConfig() {
    }

    /** Drop the resolved-backpack-item cache so a config reload re-resolves the id list. */
    public static void invalidate() {
        // ForgeConfigSpec values are read live via .get(); only the backpack-id → Item cache needs a drop.
        BackpackCounter.invalidate();
    }
}
