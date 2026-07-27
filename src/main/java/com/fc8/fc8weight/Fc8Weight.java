package com.fc8.fc8weight;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Weight &amp; Strength — encumbrance + strength gating companion mod for Epic Fight.
 *
 * <p>Two server-side systems, both pure event-driven (no Mixin, EpicFight jar untouched):
 * <ul>
 *   <li><b>Weight</b>: worn armor + held weapon are priced on the same tier ladder as the gate, and
 *       whatever exceeds the player's strength-derived allowance slows movement. Six such penalties
 *       are composed and applied as ONE capped {@code MULTIPLY_TOTAL} modifier on
 *       {@code generic.movement_speed} — see {@link WeightHandler#onPlayerTick}.</li>
 *   <li><b>Strength gate</b>: a weapon's requirement comes from {@link StrengthRequirement}'s
 *       material-tier table (iron sword 10 … netherite sword 100), resolved from the registry id
 *       alone — never from attack damage. Below the requirement, EpicFight basic attacks are
 *       canceled and the player suffers a deficit-scaled movement / attack-speed penalty. Above it,
 *       the same figure is paid back as bonus attack damage.</li>
 * </ul>
 *
 * See {@code docs/DESIGN_LOG.md} for the design rationale behind both systems.
 */
@Mod(Fc8Weight.MODID)
public class Fc8Weight {

    public static final String MODID = "fc8weight";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Fc8Weight() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, WeightConfig.SPEC);
        modBus.addListener(this::onConfigLoad);
        modBus.addListener(this::onConfigReload);

        // Gameplay handler subscribes to the FORGE bus (player join + player tick).
        MinecraftForge.EVENT_BUS.register(WeightHandler.class);
        // Weaponmaster stamina gate (LivingHurtEvent): weaponmaster's weapons deal their AoE sweep via
        // a plain player_attack DamageSource that bypasses Epic Fight's BASIC_ATTACK_EVENT (so efstamina
        // and the strength gate miss them). This charges EF stamina per swing and cancels when empty.
        MinecraftForge.EVENT_BUS.register(WeaponmasterVanillaGuard.class);
        // Tooltip lines (必要筋骨/あなたの筋骨). ItemTooltipEvent is common code that only fires
        // client-side, so this registration is harmless on a dedicated server.
        MinecraftForge.EVENT_BUS.register(WeightTooltip.class);

        LOGGER.info("[fc8weight] Weight & Strength loaded (event-driven, no mixin).");
    }

    private void onConfigLoad(final ModConfigEvent.Loading event) {
        WeightConfig.invalidate();
    }

    private void onConfigReload(final ModConfigEvent.Reloading event) {
        WeightConfig.invalidate();
    }
}
