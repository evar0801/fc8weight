package com.fc8.fc8weight;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Counts Sophisticated Backpacks backpack items across everywhere a player can reach WITHOUT touching
 * Mekanism types (QIO counting lives in {@link QioBackpackCounter}, called only when Mekanism is loaded):
 *
 * <ul>
 *   <li>the whole player inventory (main + armor + offhand),</li>
 *   <li>any nested {@code IItemHandler} capability (backpack-in-backpack via SBP's Inception Upgrade,
 *       Curios slots, and other mods' item-handler items), recursed up to {@link #MAX_DEPTH},</li>
 *   <li>the player's ender chest.</li>
 * </ul>
 *
 * The set of backpack {@link Item}s is resolved from the config id list and cached; ids whose mod is
 * absent (e.g. SBP not installed) resolve to nothing and are skipped, so the whole feature no-ops.
 */
public final class BackpackCounter {

    /** Recursion cap for nested item handlers (Inception Upgrade can nest backpacks) — prevents runaway. */
    private static final int MAX_DEPTH = 6;

    private static volatile Set<Item> cache;

    private BackpackCounter() {
    }

    /** Resolved backpack Items (cached). Empty when none of the configured ids exist (SBP absent). */
    static Set<Item> backpackItems() {
        Set<Item> c = cache;
        if (c == null) {
            c = resolve();
            cache = c;
        }
        return c;
    }

    /** Drop the cache (called from {@link WeightConfig#invalidate()} on config (re)load). */
    static void invalidate() {
        cache = null;
    }

    private static Set<Item> resolve() {
        Set<Item> set = new HashSet<>();
        for (String id : WeightConfig.BACKPACK_ITEMS.get()) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) {
                continue;
            }
            // getValue returns AIR (not null) for unknown ids in 1.20.1; confirm the id really exists.
            if (!ForgeRegistries.ITEMS.containsKey(rl)) {
                continue;
            }
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item != null) {
                set.add(item);
            }
        }
        return set;
    }

    /** Backpacks in the inventory (+ nested handlers) and the ender chest. Never throws. */
    static int countCarried(ServerPlayer player, Set<Item> backpacks) {
        if (backpacks.isEmpty()) {
            return 0;
        }
        int count = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            count += countStack(inv.getItem(i), backpacks, 0);
        }
        Container ender = player.getEnderChestInventory();
        for (int i = 0; i < ender.getContainerSize(); i++) {
            count += countStack(ender.getItem(i), backpacks, 0);
        }
        return count;
    }

    /**
     * Count backpacks in an arbitrary {@link IItemHandler} (+ nesting), starting at depth 0. Used by
     * {@link CuriosBackpackCounter} to scan the player's equipped-curios handler (e.g. a backpack worn
     * in a Curios back slot, and any backpacks nested inside it).
     */
    static int countHandler(IItemHandler handler, Set<Item> backpacks) {
        int count = 0;
        for (int s = 0; s < handler.getSlots(); s++) {
            count += countStack(handler.getStackInSlot(s), backpacks, 0);
        }
        return count;
    }

    static int countStack(ItemStack stack, Set<Item> backpacks, int depth) {
        if (stack.isEmpty()) {
            return 0;
        }
        int count = 0;
        if (backpacks.contains(stack.getItem())) {
            count += stack.getCount();
        }
        if (depth < MAX_DEPTH) {
            IItemHandler handler = stack.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            if (handler != null) {
                for (int s = 0; s < handler.getSlots(); s++) {
                    count += countStack(handler.getStackInSlot(s), backpacks, depth + 1);
                }
            }
        }
        return count;
    }
}
