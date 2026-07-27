package com.fc8.fc8weight;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.damagesource.EpicFightDamageSource;

/**
 * DawnCraft-style Epic Fight stamina gate for weaponmaster's melee weapons.
 *
 * <p><b>Why this exists.</b> weaponmaster's four sweeper weapons deal ALL of their damage through
 * their own custom AoE hitscan ({@code SweeperWeapon#processHits} → {@code LivingEntity#hurt} with a
 * plain {@code minecraft:player_attack} {@link DamageSource}; javap confirmed 7 hit sites, every one
 * {@code DamageSources#playerAttack}). They never route through Epic Fight's {@code BASIC_ATTACK_EVENT}.
 * Because both efstamina (stamina cost) and the fc8weight strength gate hook that EF event, these
 * weapons bypass both entirely: they can be spam-swung for full damage at zero stamina, which
 * trivialises trap-tower farming. This is the "バニラ殴りがまたダメージ入っている" report.
 *
 * <p><b>What it does.</b> Intercepts the weaponmaster {@code player_attack} hit and charges Epic Fight
 * stamina for it (same pool efstamina uses), cancelling the hit when stamina is insufficient — so a
 * weaponmaster swing costs stamina just like any other EF weapon. The weapon's own EF motion
 * capability ({@code data/weaponmaster/capabilities/weapons/*.json}, {@code type
 * simplyswords:longsword}) is animation-only and never produces an {@link EpicFightDamageSource}, so
 * there is no separate "EF combo" damage to exempt — every hit is the same vanilla sweep.
 *
 * <p><b>AoE = charge once per swing.</b> One swing's sweep calls {@code hurt()} once per victim, each
 * firing its own {@link LivingHurtEvent} in the same server tick. Charging per event would multiply
 * the cost by the number of enemies hit and could let a sweep half-connect when stamina runs out
 * mid-loop. So the FIRST weaponmaster hit of a given player-tick makes the single charge/allow
 * decision and every later hit of that same tick reuses it. A player attacks at most once per tick,
 * so the tick is a safe per-swing key.
 *
 * <p>Server-side only ({@link ServerPlayer}); the strength gate, encumbrance and overburden systems
 * are untouched. Cancelling zeroes the damage only — weaponmaster applies knockback separately.
 */
public final class WeaponmasterVanillaGuard {

    /** Per-player tick of the last swing whose stamina decision was made. */
    private static final ConcurrentHashMap<UUID, Long> lastSwingTick = new ConcurrentHashMap<>();
    /** Whether that last swing was allowed (had stamina) — reused for every AoE victim of the swing. */
    private static final ConcurrentHashMap<UUID, Boolean> lastSwingAllowed = new ConcurrentHashMap<>();
    /** Per-player gametime of the last "out of stamina" action-bar message (throttle). */
    private static final ConcurrentHashMap<UUID, Long> lastWarn = new ConcurrentHashMap<>();
    private static final long WARN_INTERVAL_TICKS = 20L;

    private WeaponmasterVanillaGuard() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!WeightConfig.ENABLED.get() || !WeightConfig.WM_STAMINA_ENABLED.get()) {
            return;
        }
        DamageSource src = event.getSource();
        // Genuine Epic Fight combo/motion damage is never touched (weaponmaster never produces it,
        // but keep the guard so this can never eat another mod's real EF hit).
        if (src instanceof EpicFightDamageSource) {
            return;
        }
        Entity attacker = src.getEntity();
        if (!(attacker instanceof ServerPlayer player)) {
            return; // server-side only; also filters non-player sources
        }
        ItemStack main = player.getMainHandItem();
        if (main.isEmpty()) {
            return;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(main.getItem());
        if (id == null || !"weaponmaster".equals(id.getNamespace()) || !isGatedWeapon(id.toString())) {
            return;
        }
        if ((player.isCreative() || player.isSpectator()) && WeightConfig.SKIP_CREATIVE.get()) {
            return;
        }

        ServerPlayerPatch patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        if (patch == null || patch.getMaxStamina() <= 0.0F) {
            return; // no EF stamina pool → don't lock the weapon out entirely
        }

        UUID uuid = player.getUUID();
        long tick = player.tickCount;
        Long prevTick = lastSwingTick.get(uuid);
        boolean allowed;
        if (prevTick == null || prevTick.longValue() != tick) {
            // First hit of this swing → make the charge/allow decision exactly once.
            float cost = WeightConfig.WM_STAMINA_COST.get().floatValue();
            if (cost <= 0.0F || patch.hasStamina(cost)) {
                if (cost > 0.0F) {
                    patch.setStamina(patch.getStamina() - cost);
                    patch.setStaminaRegenAwaitTicks(WeightConfig.WM_STAMINA_REGEN_DELAY.get());
                }
                allowed = true;
            } else {
                allowed = false;
                warnNoStamina(player);
            }
            lastSwingTick.put(uuid, tick);
            lastSwingAllowed.put(uuid, allowed);
        } else {
            allowed = Boolean.TRUE.equals(lastSwingAllowed.get(uuid));
        }

        if (!allowed) {
            event.setCanceled(true); // out of stamina: whole sweep deals no damage
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        lastSwingTick.remove(uuid);
        lastSwingAllowed.remove(uuid);
        lastWarn.remove(uuid);
    }

    private static boolean isGatedWeapon(String id) {
        for (String target : WeightConfig.WM_NO_VANILLA_ITEMS.get()) {
            if (id.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static void warnNoStamina(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long prev = lastWarn.get(player.getUUID());
        if (prev != null && now - prev < WARN_INTERVAL_TICKS) {
            return;
        }
        lastWarn.put(player.getUUID(), now);
        player.displayClientMessage(Component.literal("スタミナ切れ… 息を整えろ"), true);
    }
}
