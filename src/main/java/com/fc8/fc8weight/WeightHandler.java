package com.fc8.fc8weight;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.Multimap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.WeaponCategory;
import yesman.epicfight.world.entity.eventlistener.BasicAttackEvent;
import yesman.epicfight.world.entity.eventlistener.PlayerEventListener.EventType;

/**
 * Server-side handler for the weight + strength-gate systems.
 *
 * <ul>
 *   <li>{@link #onEntityJoin} registers a {@code BASIC_ATTACK_EVENT} listener on each player's
 *       EpicFight patch (same join-registration pattern as efstamina) → hard attack cancel when
 *       under-strength.</li>
 *   <li>{@link #onPlayerTick} continuously maintains transient attribute modifiers for the
 *       encumbrance slowdown and the over-heavy-weapon penalty. Transient modifiers are NOT
 *       persisted to NBT, so they are simply recomputed every tick and never leak into the save.</li>
 * </ul>
 */
public final class WeightHandler {

    /** Stable id so re-registration on every join overwrites instead of stacking. */
    private static final UUID GATE_LISTENER_ID = UUID.fromString("4d2b9f10-6c3e-4a18-9b77-2c1ad0e9f455");

    // Stable modifier UUIDs (distinct per effect so they coexist with PST/other modifiers).
    // The six movement penalties share ONE modifier: MULTIPLY_TOTAL composes by multiplication, so
    // six separately-capped modifiers still multiply out to a standstill. Only a single modifier can
    // carry a cap on their product. Attack speed keeps its own — different attribute, no interaction.
    private static final UUID COMBINED_MOVE_UUID = UUID.fromString("a1b2c3d4-0001-4000-8000-000000000001");
    private static final UUID OVERBURDEN_ATTACK_UUID = UUID.fromString("a1b2c3d4-0003-4000-8000-000000000003");

    private static final String COMBINED_MOVE_NAME = "fc8weight movement load";
    private static final String OVERBURDEN_ATTACK_NAME = "fc8weight overburden attack";

    /** True once, at class load, if Mekanism is present → gates the QIO counting (see QioBackpackCounter). */
    private static final boolean MEKANISM_LOADED = ModList.get().isLoaded("mekanism");
    /** True once, at class load, if Curios is present → gates the equipped-curios counting. */
    private static final boolean CURIOS_LOADED = ModList.get().isLoaded("curios");
    /** True once, at class load, if EpicClassMod is present → gates the client-side sync bridge below. */
    private static final boolean ECM_LOADED = ModList.get().isLoaded("epicclassmod");

    /** Per-player gametime of the last "too heavy" action-bar message (throttle). */
    private static final ConcurrentHashMap<UUID, Long> lastWarn = new ConcurrentHashMap<>();
    private static final long WARN_INTERVAL_TICKS = 20L;

    /** Per-player last backpack over-carry step (extra beyond free) — drives the "changed" notify trigger. */
    private static final ConcurrentHashMap<UUID, Integer> lastBackpackExtra = new ConcurrentHashMap<>();
    /** Per-player gametime until which the backpack action-bar message keeps being re-sent (10s window). */
    private static final ConcurrentHashMap<UUID, Long> backpackNotifyUntil = new ConcurrentHashMap<>();

    private WeightHandler() {
    }

    // ---------------------------------------------------------------- strength gate (attack cancel)

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ServerPlayerPatch patch = EpicFightCapabilities.getEntityPatch(serverPlayer, ServerPlayerPatch.class);
        if (patch == null) {
            return;
        }
        // addEventListener removes the same UUID+priority before adding → no double registration.
        patch.getEventListener().addEventListener(
                EventType.BASIC_ATTACK_EVENT, GATE_LISTENER_ID, WeightHandler::onBasicAttack);
    }

    private static void onBasicAttack(BasicAttackEvent attack) {
        if (!WeightConfig.ENABLED.get() || !WeightConfig.GATE_ENABLED.get()) {
            return;
        }
        if (!WeightConfig.CANCEL_WHEN_UNDERPOWERED.get()) {
            return; // soft gate: penalty only, handled in tick
        }
        ServerPlayerPatch patch = attack.getPlayerPatch();
        if (patch == null) {
            return;
        }
        ServerPlayer player = patch.getOriginal();
        if (player == null || isExempt(player)) {
            return;
        }

        ItemStack weapon = player.getMainHandItem();
        double weaponDamage = weaponBaseDamage(weapon);
        if (weaponDamage < WeightConfig.MIN_WEAPON_DAMAGE_TO_GATE.get()) {
            return; // unarmed / tool / non-weapon: not gated
        }
        if (isGateExempt(weapon)) {
            return; // job starter / reward weapon: always usable
        }
        double requirement = StrengthRequirement.of(weapon);
        double strength = strengthOf(player);

        if (strength + 1.0e-4 < requirement) {
            attack.setCanceled(true);
            warnTooHeavy(player, strength, requirement);
        }
    }

    // ---------------------------------------------------------------- reality damage (vanilla swords)

    /** Vanilla sword reality anchor: displayed attack damage (player base 1 + item ADDITION). */
    private static final java.util.Map<String, Double> VANILLA_SWORD_DAMAGE = java.util.Map.of(
            "minecraft:wooden_sword", 3.0,
            "minecraft:stone_sword", 4.0,
            "minecraft:iron_sword", 5.0,
            "minecraft:golden_sword", 4.0,
            "minecraft:diamond_sword", 6.0,
            "minecraft:netherite_sword", 7.0);

    /** Vanilla BASE_ATTACK_DAMAGE_UUID value: reusing it makes the tooltip render as the item's base. */
    private static final UUID SWORD_DAMAGE_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");

    /**
     * Reality-based attack-damage override (MVP scope = vanilla swords only, the tier anchor). Fires
     * on every {@link ItemStack#getAttributeModifiers} for the MAIN_HAND; cheap early-outs keep it
     * off the hot path for everything else. Replaces the ATTACK_DAMAGE modifier only (attack speed
     * and every other modifier are left intact), using the vanilla base UUID so the tooltip renders
     * it as the item's own base damage.
     */
    @SubscribeEvent
    public static void onItemAttribute(ItemAttributeModifierEvent event) {
        if (!WeightConfig.ENABLED.get() || !WeightConfig.DAMAGE_OVERRIDE_ENABLED.get()) {
            return;
        }
        if (event.getSlotType() != EquipmentSlot.MAINHAND) {
            return;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
        if (key == null) {
            return;
        }
        Double display = VANILLA_SWORD_DAMAGE.get(key.toString());
        if (display != null) {
            for (AttributeModifier m : new java.util.ArrayList<>(event.getModifiers().get(Attributes.ATTACK_DAMAGE))) {
                event.removeModifier(Attributes.ATTACK_DAMAGE, m);
            }
            event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                    SWORD_DAMAGE_UUID, "fc8weight sword damage", display - 1.0,
                    AttributeModifier.Operation.ADDITION));
        }
        applyStrengthDamageBonus(event);
    }

    /** Stable id for the requirement-scaled damage bonus, distinct from the sword base override. */
    private static final UUID STR_DAMAGE_UUID = UUID.fromString("a1b2c3d4-0008-4000-8000-000000000008");

    /**
     * Grant a weapon bonus attack damage proportional to the strength it demands, so the tier ladder
     * pays for itself: an iron sword (requirement 10) gains +5, a netherite greatsword (118) gains
     * +59. Only items that already carry a positive ADDITION attack_damage are touched, which keeps
     * pickaxes, armor and trinkets out of it.
     *
     * <p>Reads the requirement from {@link StrengthRequirement}, which resolves purely from the
     * registry id — calling {@code weaponBaseDamage} here instead would re-enter
     * {@link ItemStack#getAttributeModifiers} and fire this very event again.
     */
    private static void applyStrengthDamageBonus(ItemAttributeModifierEvent event) {
        double factor = WeightConfig.DAMAGE_FROM_STRENGTH_FACTOR.get();
        if (factor <= 0.0) {
            return;
        }
        double requirement = StrengthRequirement.of(event.getItemStack());
        if (requirement <= 0.0) {
            return;
        }
        double existing = 0.0;
        for (AttributeModifier m : event.getModifiers().get(Attributes.ATTACK_DAMAGE)) {
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) {
                existing += m.getAmount();
            }
        }
        // Count Epic Fight's own capability damage too, and test against the same threshold the gate
        // uses. Without both, a weapon whose damage comes entirely from EF's damage_bonus would be
        // gated by onBasicAttack yet skipped here — strength demanded, no strength reward given.
        existing += efDamageBonus(event.getItemStack());
        if (existing < WeightConfig.MIN_WEAPON_DAMAGE_TO_GATE.get()) {
            return; // not a weapon by this mod's own definition
        }
        event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                STR_DAMAGE_UUID, "fc8weight strength damage", requirement * factor,
                AttributeModifier.Operation.ADDITION));
    }

    // ---------------------------------------------------------------- weight + overburden (tick)

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        // Recompute a few times a second, not every tick: getAttributeModifiers builds a Multimap
        // per call and the penalty needs no sub-0.25s precision. Idempotent apply handles the gaps.
        if (player.tickCount % 5 != 0) {
            return;
        }

        boolean active = WeightConfig.ENABLED.get() && !isExempt(player);

        // --- weight encumbrance (movement) ---
        double weightSlow = 0.0;
        if (active && WeightConfig.WEIGHT_ENABLED.get()) {
            double totalWeight = armorWeight(player) + weaponWeight(player.getMainHandItem());
            double allowance;
            if (WeightConfig.STR_ALLOWANCE_ENABLED.get()) {
                allowance = ecmStrAlloc(player) * classEfficiency(player) * WeightConfig.STR_ALLOWANCE_PER_POINT.get()
                        + WeightConfig.STR_ALLOWANCE_BASE.get();
            } else {
                allowance = WeightConfig.FREE_WEIGHT.get();
            }
            double excess = Math.max(0.0, totalWeight - allowance);
            weightSlow = Math.min(WeightConfig.MAX_WEIGHT_SLOW.get(), excess * WeightConfig.SLOW_PER_WEIGHT.get());
        }

        // --- over-heavy-weapon penalty (movement + attack speed) ---
        double overMoveSlow = 0.0;
        double overAttackSlow = 0.0;
        if (active && WeightConfig.GATE_ENABLED.get()) {
            ItemStack weapon = player.getMainHandItem();
            double weaponDamage = weaponBaseDamage(weapon);
            if (weaponDamage >= WeightConfig.MIN_WEAPON_DAMAGE_TO_GATE.get() && !isGateExempt(weapon)) {
                double requirement = StrengthRequirement.of(weapon);
                double deficit = requirement - strengthOf(player);
                if (deficit > 0.0) {
                    overMoveSlow = Math.min(WeightConfig.OVERBURDEN_MAX_MOVE_SLOW.get(),
                            deficit * WeightConfig.OVERBURDEN_MOVE_SLOW_PER_DEFICIT.get());
                    overAttackSlow = Math.min(WeightConfig.OVERBURDEN_MAX_ATTACK_SLOW.get(),
                            deficit * WeightConfig.OVERBURDEN_ATTACK_SLOW_PER_DEFICIT.get());
                }
            }
        }

        // --- held-weapon tier movement penalty (task3) ---
        // Independent of the encumbrance weightSlow above: this is a separate MULTIPLY_TOTAL modifier
        // keyed only on the held weapon's tier (heavy/medium/light). MULTIPLY_TOTAL modifiers compose
        // multiplicatively, so if both apply the effective slow is (1-weightSlow)*(1-heldSlow). This is
        // intentional (holding a heavy weapon slows you on top of encumbrance). If it feels excessive,
        // tune heldHeavySlow/heldMediumSlow/heldLightSlow (or disable heldWeaponSlowEnabled) in config.
        double heldSlow = 0.0;
        if (active && WeightConfig.HELD_WEAPON_SLOW_ENABLED.get()) {
            heldSlow = heldWeaponSlow(player.getMainHandItem());
        }

        // --- backpack over-carry penalty (Sophisticated Backpacks) ---
        // Counts backpacks in inventory + nested handlers + ender chest (+ private QIO when Mekanism is
        // present). Each backpack beyond backpackFreeCount adds backpackSlowPerExtra, capped by backpackMaxSlow.
        double backpackSlow = 0.0;
        int backpackExtra = 0;
        if (active && WeightConfig.BACKPACK_ENABLED.get()) {
            Set<Item> backpacks = BackpackCounter.backpackItems();
            if (!backpacks.isEmpty()) {
                int count = BackpackCounter.countCarried(player, backpacks);
                if (CURIOS_LOADED && WeightConfig.BACKPACK_COUNT_CURIOS.get()) {
                    count += CuriosBackpackCounter.count(player, backpacks);
                }
                if (MEKANISM_LOADED && WeightConfig.BACKPACK_COUNT_QIO.get()) {
                    count += QioBackpackCounter.count(player, backpacks);
                }
                if (MEKANISM_LOADED && WeightConfig.BACKPACK_COUNT_MEK_STORAGE.get()) {
                    count += MekPersonalStorageBackpackCounter.count(player, backpacks);
                }
                backpackExtra = Math.max(0, count - WeightConfig.BACKPACK_FREE_COUNT.get());
                backpackSlow = Math.min(WeightConfig.BACKPACK_MAX_SLOW.get(),
                        backpackExtra * WeightConfig.BACKPACK_SLOW_PER_EXTRA.get());
            }
        }

        // --- armor-defense movement penalty (heavier defense = slower; LINEAR, buffs still scale) ---
        // Reads the total ARMOR attribute value, which already sums equipped armor slots AND any Curios
        // accessories that grant armor (they apply ARMOR modifiers when equipped) — so "防具＋アクセ の
        // 追加防御力" is captured without walking slots. Player base ARMOR is 0, so this == added defense.
        // MULTIPLY_TOTAL, composes with the other slows. Clamped to armorDefenseMaxSlow (0.99) purely so
        // the factor never becomes 0 (a frozen player can't be rescued by a speed buff: 0 * buff = 0).
        double armorDefSlow = 0.0;
        if (active && WeightConfig.ARMOR_DEF_SLOW_ENABLED.get()) {
            double defense = player.getAttributeValue(Attributes.ARMOR);
            armorDefSlow = Math.min(WeightConfig.ARMOR_DEF_MAX_SLOW.get(),
                    defense * WeightConfig.ARMOR_DEF_SLOW_PER_POINT.get());
        }

        // --- armor strength gate (③): strength < total defense * factor -> heavy FIXED movement slow ---
        // Design intent (author): armor whose defense exceeds your strength is "too heavy to wear" — a flat
        // -80% (config armorStrGateSlow), NOT proportional, so under-equipping is a hard practical wall.
        // Uses the same gate strength as the weapon gate (strengthOf) and the same total-defense source as
        // armorDefSlow (Attributes.ARMOR). Composes multiplicatively with the linear armorDefSlow above.
        double armorStrGateSlow = 0.0;
        if (active && WeightConfig.ARMOR_STR_GATE_ENABLED.get()) {
            double defense = player.getAttributeValue(Attributes.ARMOR);
            double requirement = defense * WeightConfig.ARMOR_STR_GATE_DEFENSE_FACTOR.get();
            if (requirement > 0.0 && strengthOf(player) < requirement) {
                armorStrGateSlow = WeightConfig.ARMOR_STR_GATE_SLOW.get();
            }
        }

        // Compose the six movement penalties the way the game would have, then cap the product once.
        // Each term is already capped on its own; what no per-term cap can express is that six of them
        // multiply to 0.0000084x — technically not zero, indistinguishable from frozen in play.
        double retained = (1.0 - weightSlow)
                * (1.0 - overMoveSlow)
                * (1.0 - heldSlow)
                * (1.0 - backpackSlow)
                * (1.0 - armorDefSlow)
                * (1.0 - armorStrGateSlow);
        double combinedSlow = Math.min(WeightConfig.TOTAL_MAX_MOVE_SLOW.get(), 1.0 - retained);

        applyPenalty(player, Attributes.MOVEMENT_SPEED, COMBINED_MOVE_UUID, COMBINED_MOVE_NAME, combinedSlow);
        applyPenalty(player, Attributes.ATTACK_SPEED, OVERBURDEN_ATTACK_UUID, OVERBURDEN_ATTACK_NAME, overAttackSlow);
        notifyBackpack(player, backpackExtra, backpackSlow);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        lastWarn.remove(uuid);
        lastBackpackExtra.remove(uuid);
        backpackNotifyUntil.remove(uuid);
    }

    /**
     * Action-bar notice for the backpack over-carry penalty. Non-intrusive by design: it only (re)starts
     * a 10-second (config: backpackNotifyTicks) window when the penalty step ({@code extra}) CHANGES, then
     * re-sends the action bar each handler run (every 5 ticks) until the window closes — so picking up /
     * dropping a spare backpack flashes a message for 10s and then fades, instead of nagging continuously.
     */
    private static void notifyBackpack(ServerPlayer player, int extra, double slow) {
        UUID uuid = player.getUUID();
        long now = player.level().getGameTime();
        Integer prev = lastBackpackExtra.get(uuid);
        if (prev == null || prev.intValue() != extra) {
            lastBackpackExtra.put(uuid, extra);
            if (extra > 0) {
                backpackNotifyUntil.put(uuid, now + WeightConfig.BACKPACK_NOTIFY_TICKS.get());
            } else {
                backpackNotifyUntil.remove(uuid);
            }
        }
        Long until = backpackNotifyUntil.get(uuid);
        if (until == null) {
            return;
        }
        if (now >= until || extra <= 0) {
            backpackNotifyUntil.remove(uuid);
            return;
        }
        int pct = (int) Math.round(slow * 100.0);
        player.displayClientMessage(
                Component.literal(String.format(
                        "バックパックの持ちすぎ… 移動速度 -%d%%（余分 %d 個）", pct, extra)),
                true);
    }

    // ---------------------------------------------------------------- helpers

    private static boolean isExempt(ServerPlayer player) {
        return (player.isCreative() || player.isSpectator()) && WeightConfig.SKIP_CREATIVE.get();
    }

    /** True when the item id is in the gateExemptItems config list (starter/reward weapons). */
    static boolean isGateExempt(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) {
            return false;
        }
        String id = key.toString();
        for (String exempt : WeightConfig.GATE_EXEMPT_ITEMS.get()) {
            if (id.equals(exempt)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sum of ADDITION attack_damage modifiers the main-hand item grants (its "base weight"),
     * including both the vanilla item-stack modifiers AND any Epic Fight weapon-capability
     * {@code damage_bonus} (e.g. simplyswords:dormant_relic at -2.4/-2.5). Used purely as the
     * weapon's own "weight" figure — the requirement (weaponDamage * strRequirementFactor), the
     * weight-encumbrance contribution, and the tooltip's 重さ line. {@link #strengthOf} no longer
     * derives from this value at all (gate strength is alloc_str-based only, per the strength
     * system in {@code docs/DESIGN_LOG.md}), so there is nothing left to "leak" into the player's
     * displayed strength.
     */
    static double weaponBaseDamage(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0;
        }
        Multimap<Attribute, AttributeModifier> mods = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        double sum = 0.0;
        for (AttributeModifier m : mods.get(Attributes.ATTACK_DAMAGE)) {
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) {
                sum += m.getAmount();
            }
        }
        return sum + efDamageBonus(stack);
    }

    // --- reality material weight (2026-07-08) ---

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    /**
     * Reality weight of the held weapon: material×size when resolvable (see {@link MaterialWeight}),
     * else the legacy attack-damage proxy (× weaponWeightFactor). The material path is used only for
     * actual weapons (base damage ≥ minWeaponDamageToGate) so tools/trinkets keep the light proxy.
     */
    static double weaponWeight(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0;
        }
        // Guard first: every model below resolves the material from the registry id alone, so without
        // this an iron ingot, an emerald or a lead would each weigh as much as the sword of the same
        // material simply for being held. Only things that hit like a weapon carry weapon weight.
        double dmg = weaponBaseDamage(stack);
        if (dmg < WeightConfig.MIN_WEAPON_DAMAGE_TO_GATE.get()) {
            return dmg * WeightConfig.WEAPON_WEIGHT_FACTOR.get();
        }
        double tier = StrengthRequirement.weightOf(stack);
        if (!Double.isNaN(tier)) {
            return tier;
        }
        // Unknown modded material: the density model still knows most sensibly-named gear.
        double mat = MaterialWeight.weaponWeight(stack);
        if (!Double.isNaN(mat)) {
            return mat;
        }
        return dmg * WeightConfig.WEAPON_WEIGHT_FACTOR.get();
    }

    /**
     * Encumbrance weight of all worn armor on the tier ladder (a full iron set = 10, netherite =
     * 100), falling back to the density model and then to the piece's own defense value for
     * materials neither model recognizes.
     */
    private static double armorWeight(ServerPlayer player) {
        double sum = 0.0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            double w = StrengthRequirement.armorPieceWeightOf(stack, slot);
            if (Double.isNaN(w)) {
                w = MaterialWeight.armorPieceWeight(stack, slot);
            }
            if (Double.isNaN(w)) {
                w = slotArmorAddition(stack, slot); // fallback: this piece's defense value
            }
            sum += w;
        }
        return sum;
    }

    /** Sum of ADDITION armor modifiers a single piece grants in its slot (defense-proxy fallback). */
    private static double slotArmorAddition(ItemStack stack, EquipmentSlot slot) {
        double sum = 0.0;
        Multimap<Attribute, AttributeModifier> mods = stack.getAttributeModifiers(slot);
        for (AttributeModifier m : mods.get(Attributes.ARMOR)) {
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) {
                sum += m.getAmount();
            }
        }
        return sum;
    }

    /**
     * Sum of ADDITION attack_damage modifiers Epic Fight's weapon-capability data (the
     * {@code damage_bonus} field in {@code capabilities/weapons/<id>.json}) grants for this item,
     * across every style the capability defines. 0.0 when EF has no capability for the item, or on
     * any failure (missing/incompatible EF) — never throws.
     */
    private static double efDamageBonus(ItemStack stack) {
        try {
            CapabilityItem cap = EpicFightCapabilities.getItemStackCapability(stack);
            if (cap == null) {
                return 0.0;
            }
            Multimap<Attribute, AttributeModifier> efMods = cap.getAllAttributeModifiers(EquipmentSlot.MAINHAND);
            double sum = 0.0;
            for (AttributeModifier m : efMods.get(Attributes.ATTACK_DAMAGE)) {
                if (m.getOperation() == AttributeModifier.Operation.ADDITION) {
                    sum += m.getAmount();
                }
            }
            return sum;
        } catch (Throwable t) {
            return 0.0;
        }
    }

    /**
     * EpicClassMod STR allocation, read from the player's persistent NBT
     * ({@code ecm_leveling.alloc_str}). 0 when ECM is absent or nothing is allocated. Accepts any
     * {@link Player} so the client-side tooltip can reuse the exact same read. On the logical client
     * this NBT is server-only and never synced, so we prefer ECM's own client-synced value
     * ({@link EcmClientBridge#allocStr()}) first and only fall back to the (empty) NBT read if that
     * bridge is unavailable — see {@link EcmClientBridge} for why.
     */
    static int ecmStrAlloc(Player player) {
        if (ECM_LOADED && player.level().isClientSide()) {
            Integer clientAlloc = EcmClientBridge.allocStr();
            if (clientAlloc != null) {
                return Math.max(0, clientAlloc);
            }
        }
        try {
            CompoundTag tag = player.getPersistentData();
            if (!tag.contains("ecm_leveling")) {
                return 0;
            }
            return Math.max(0, tag.getCompound("ecm_leveling").getInt("alloc_str"));
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Raw EpicClassMod job name, preferring ECM's client-synced class (see {@link EcmClientBridge})
     * on the logical client, falling back to the player's persistent NBT ({@code ecm_class_name}, "" when
     * ECM is absent / no class chosen / on any read failure).
     */
    private static String ecmClassName(Player player) {
        if (ECM_LOADED && player.level().isClientSide()) {
            String clientCls = EcmClientBridge.className();
            if (clientCls != null) {
                return clientCls;
            }
        }
        try {
            return player.getPersistentData().getString("ecm_class_name");
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * Reflection-only bridge to EpicClassMod's client-synced state
     * ({@code com.example.epicclassmod.client.ClientLevelState.allocStr} /
     * {@code ClientClassState.selectedType}), used ONLY on the logical client.
     *
     * <p>fc8weight has no compile-time dependency on ECM (soft-linked like Mekanism/Curios elsewhere
     * in this file), so this reads the two static fields by reflection, resolved once and cached.
     *
     * <p>Why this exists: {@link #ecmStrAlloc}/{@link #ecmClassName} normally read
     * {@code ecm_leveling}/{@code ecm_class_name} from the player's persistent NBT, but that NBT is
     * SERVER-ONLY and never synced to the client. On a dedicated server this made the "あなたの筋骨"
     * tooltip always show classBaseStrength+0 no matter how many points were actually allocated —
     * the reported "筋骨表記が実際のステータスに反映されない" bug. ECM already broadcasts the real
     * alloc_str/class via SyncLevelPacket/SyncClassPacket into these two static holders for its own
     * class-book UI; reading them here makes the tooltip agree with the class book and the (always
     * correct, server-authoritative) gate itself.
     */
    private static final class EcmClientBridge {
        private static boolean resolved = false;
        private static java.lang.reflect.Field allocStrField;
        private static java.lang.reflect.Field selectedTypeField;

        private static void resolve() {
            if (resolved) {
                return;
            }
            resolved = true;
            try {
                Class<?> levelState = Class.forName("com.example.epicclassmod.client.ClientLevelState");
                allocStrField = levelState.getField("allocStr");
                Class<?> classState = Class.forName("com.example.epicclassmod.client.ClientClassState");
                selectedTypeField = classState.getField("selectedType");
            } catch (Throwable t) {
                allocStrField = null;
                selectedTypeField = null;
            }
        }

        static Integer allocStr() {
            resolve();
            if (allocStrField == null) {
                return null;
            }
            try {
                return (Integer) allocStrField.get(null);
            } catch (Throwable t) {
                return null;
            }
        }

        /** @return the selected class's enum name (e.g. "WARRIOR"), or null if unresolved/unset. */
        static String className() {
            resolve();
            if (selectedTypeField == null) {
                return null;
            }
            try {
                Object type = selectedTypeField.get(null);
                return type == null ? null : type.toString();
            } catch (Throwable t) {
                return null;
            }
        }
    }

    /**
     * How efficiently the player's EpicClassMod job converts alloc_str points into gate strength AND
     * carry allowance (see {@code docs/DESIGN_LOG.md}). Read from {@code ecm_class_name}, same source
     * as {@link #classBaseStrength}/{@link #ecmStrAlloc}; every value is config-tunable.
     */
    static double classEfficiency(Player player) {
        String cls = ecmClassName(player);
        return switch (cls) {
            case "WARRIOR" -> WeightConfig.CLASS_EFFICIENCY_WARRIOR.get();
            case "BERSERKER" -> WeightConfig.CLASS_EFFICIENCY_BERSERKER.get();
            case "ARCHER" -> WeightConfig.CLASS_EFFICIENCY_ARCHER.get();
            case "REAPER" -> WeightConfig.CLASS_EFFICIENCY_REAPER.get();
            case "SORCERER" -> WeightConfig.CLASS_EFFICIENCY_SORCERER.get();
            case "PALADIN" -> WeightConfig.CLASS_EFFICIENCY_PALADIN.get();
            default -> WeightConfig.CLASS_EFFICIENCY_DEFAULT.get(); // NONE / unrecognized
        };
    }

    /**
     * Class-specific innate FREE strength (the "grace" added in {@link #strengthOf}). WARRIOR and
     * BERSERKER get their own higher bases so a warrior comfortably wields any one-handed sword and
     * a berserker swings early greatswords from the start; every other class (and NONE) uses the
     * default {@code baseStrength}. The class comes from {@link #ecmClassName}, exactly like
     * {@link #classEfficiency}/{@link #ecmStrAlloc} — on the client that reads ECM's synced
     * {@link EcmClientBridge} value (matching what the class book shows), on the server the
     * authoritative persistent NBT.
     */
    static double classBaseStrength(Player player) {
        String cls = ecmClassName(player);
        if ("WARRIOR".equals(cls)) {
            return WeightConfig.WARRIOR_BASE_STRENGTH.get();
        }
        if ("BERSERKER".equals(cls)) {
            return WeightConfig.BERSERKER_BASE_STRENGTH.get();
        }
        return WeightConfig.BASE_STRENGTH.get();
    }

    /**
     * Gate strength = classBaseStrength(free grace, config) + alloc_str * classEfficiency(job).
     * ECM's own attack_damage bonus from STR was removed on the ECM side (筋骨=装備ゲート専用), so
     * this no longer reads the attack_damage attribute at all — alloc_str is the sole scaling input.
     * Accepts any Player so the client-side tooltip can reuse the exact same formula: on the client
     * {@link #ecmStrAlloc}/{@link #classEfficiency}/{@link #classBaseStrength} read ECM's synced
     * {@link EcmClientBridge} state (same numbers as the class book), and the gate itself always runs
     * server-side reading the authoritative persistent NBT directly.
     */
    static double strengthOf(Player player) {
        return classBaseStrength(player) + ecmStrAlloc(player) * classEfficiency(player);
    }

    /**
     * Movement slow fraction for the held weapon's tier (task3). Resolves the tier by Epic Fight
     * weapon category first (via {@link EpicFightCapabilities#getItemStackCapability}); if the item
     * has no EF capability or its category matches none of the configured lists, falls back to the
     * weapon's base attack damage against the two damage thresholds. Non-weapons (base damage below
     * {@code minWeaponDamageToGate}) return 0. Config-agnostic to the strength gate — a held-tier
     * slow applies even to gate-exempt weapons (a heavy weapon is heavy to carry regardless).
     */
    static double heldWeaponSlow(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0;
        }
        double baseDamage = weaponBaseDamage(stack);
        if (baseDamage < WeightConfig.MIN_WEAPON_DAMAGE_TO_GATE.get()) {
            return 0.0; // tools / trinkets / very light items: no held-tier slow
        }

        // 1) Epic Fight weapon category (matched by enum name; robust to addon-registered categories).
        String category = efWeaponCategoryName(stack);
        if (category != null) {
            if (listContains(WeightConfig.HELD_HEAVY_CATEGORIES.get(), category)) {
                return WeightConfig.HELD_HEAVY_SLOW.get();
            }
            if (listContains(WeightConfig.HELD_MEDIUM_CATEGORIES.get(), category)) {
                return WeightConfig.HELD_MEDIUM_SLOW.get();
            }
            if (listContains(WeightConfig.HELD_LIGHT_CATEGORIES.get(), category)) {
                return WeightConfig.HELD_LIGHT_SLOW.get();
            }
            // category present but unclassified → fall through to the damage-threshold fallback
        }

        // 2) Fallback: attack-damage thresholds.
        if (baseDamage >= WeightConfig.HELD_HEAVY_DAMAGE_THRESHOLD.get()) {
            return WeightConfig.HELD_HEAVY_SLOW.get();
        }
        if (baseDamage >= WeightConfig.HELD_MEDIUM_DAMAGE_THRESHOLD.get()) {
            return WeightConfig.HELD_MEDIUM_SLOW.get();
        }
        return WeightConfig.HELD_LIGHT_SLOW.get();
    }

    /**
     * Epic Fight weapon-category name for the stack, or null when EF has no capability for it. Uses
     * {@code getWeaponCategory().toString()} — for the built-in {@code WeaponCategories} enum this is
     * the constant name (GREATSWORD, SWORD, DAGGER, …); addon-registered categories return their own
     * name, which config lists can also target. Guarded so a missing/incompatible EF never throws.
     */
    private static String efWeaponCategoryName(ItemStack stack) {
        try {
            CapabilityItem cap = EpicFightCapabilities.getItemStackCapability(stack);
            if (cap == null) {
                return null;
            }
            WeaponCategory wc = cap.getWeaponCategory();
            return wc == null ? null : wc.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean listContains(java.util.List<? extends String> list, String value) {
        for (String s : list) {
            if (value.equals(s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ensure {@code attr} carries exactly the desired MULTIPLY_TOTAL penalty (a negative fraction,
     * e.g. 0.10 → -10%). Idempotent: only touches the attribute when the value actually changes,
     * and removes the modifier entirely when {@code slowFraction <= 0}.
     */
    private static void applyPenalty(ServerPlayer player, Attribute attr, UUID id, String name, double slowFraction) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) {
            return;
        }
        AttributeModifier existing = inst.getModifier(id);
        double desired = -slowFraction;
        if (slowFraction <= 1.0e-4) {
            if (existing != null) {
                inst.removeModifier(id);
            }
            return;
        }
        if (existing != null) {
            if (Math.abs(existing.getAmount() - desired) <= 1.0e-4) {
                return; // already correct
            }
            inst.removeModifier(id);
        }
        inst.addTransientModifier(
                new AttributeModifier(id, name, desired, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static void warnTooHeavy(ServerPlayer player, double strength, double requirement) {
        long now = player.level().getGameTime();
        Long prev = lastWarn.get(player.getUUID());
        if (prev != null && now - prev < WARN_INTERVAL_TICKS) {
            return;
        }
        lastWarn.put(player.getUUID(), now);
        player.displayClientMessage(
                Component.literal(String.format(
                        "武器が重すぎる… 筋骨 %.1f / 必要 %.1f", strength, requirement)),
                true);
    }
}
