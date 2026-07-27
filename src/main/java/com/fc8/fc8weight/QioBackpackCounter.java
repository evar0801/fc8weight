package com.fc8.fc8weight;

import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.lib.frequency.FrequencyManager;
import mekanism.common.lib.frequency.FrequencyType;

/**
 * Mekanism QIO backpack counting, kept in its OWN class so the Mekanism types above are only
 * class-loaded when {@link WeightHandler} calls it behind a {@code ModList.isLoaded("mekanism")} guard.
 * On a Mekanism-less install this class is never referenced, so its Mekanism imports never trigger a
 * {@link NoClassDefFoundError}.
 *
 * <p>Scope: the player's PRIVATE QIO frequencies only ({@code FrequencyType.QIO.getManager(playerUuid)}).
 * Public/shared frequencies live in a different manager and are intentionally not counted.
 *
 * <p>Counting is NBT-agnostic on purpose: {@code QIOFrequency.getStored(ItemStack)} keys on a
 * NBT-exact {@code HashedItem}, so a spawn-fresh empty backpack stack would miss the (NBT-bearing,
 * contents-carrying) backpacks a player actually stashes. {@code getStacksByItem(Item)} returns every
 * NBT variant of that item, whose counts we sum — catching backpacks regardless of their contents.
 */
final class QioBackpackCounter {

    private QioBackpackCounter() {
    }

    static int count(ServerPlayer player, Set<Item> backpacks) {
        if (backpacks.isEmpty()) {
            return 0;
        }
        long total = 0L;
        try {
            UUID owner = player.getUUID();
            FrequencyManager<QIOFrequency> manager = FrequencyType.QIO.getManager(owner);
            if (manager == null) {
                return 0;
            }
            for (QIOFrequency freq : manager.getFrequencies()) {
                for (Item bp : backpacks) {
                    for (long n : freq.getStacksByItem(bp).values()) {
                        total += n;
                    }
                }
            }
        } catch (Throwable t) {
            // Never let QIO counting (a best-effort loophole-closer) break the server tick.
            return 0;
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }
}
