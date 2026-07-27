package com.fc8.fc8weight;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import mekanism.api.inventory.IInventorySlot;
import mekanism.common.lib.inventory.personalstorage.AbstractPersonalStorageItemInventory;
import mekanism.common.lib.inventory.personalstorage.PersonalStorageManager;

/**
 * Counts SBP backpacks stashed inside Mekanism Personal Chest / Personal Barrel items.
 *
 * <p>Those items hold a 54-slot inventory that is <b>NOT</b> serialized into the ItemStack NBT, so
 * {@link BackpackCounter}'s generic {@code IItemHandler} capability recursion misses it entirely (the
 * loophole ③ reported: "put a backpack inside a Mekanism portable storage and its weight vanishes").
 * Mekanism keeps that inventory in server-side saved data ({@code PersonalStorageManager}) keyed by an
 * id stored in the stack's NBT. We resolve it via the Mekanism API and count backpacks in it, reusing
 * {@link BackpackCounter#countStack} so a backpack nested inside a backpack inside a personal chest
 * still counts.
 *
 * <p>Kept in its OWN class with the Mekanism imports isolated here, only touched behind
 * {@link WeightHandler}'s {@code ModList.isLoaded("mekanism")} guard — same pattern as
 * {@link QioBackpackCounter}, so a Mekanism-less install never triggers a {@link NoClassDefFoundError}.
 */
final class MekPersonalStorageBackpackCounter {

    private MekPersonalStorageBackpackCounter() {
    }

    static int count(ServerPlayer player, Set<Item> backpacks) {
        if (backpacks.isEmpty()) {
            return 0;
        }
        int count = 0;
        try {
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                count += countInStack(inv.getItem(i), backpacks);
            }
            Container ender = player.getEnderChestInventory();
            for (int i = 0; i < ender.getContainerSize(); i++) {
                count += countInStack(ender.getItem(i), backpacks);
            }
        } catch (Throwable t) {
            // Never let personal-storage counting (a best-effort loophole-closer) break the server tick.
            return 0;
        }
        return count;
    }

    /** If the stack is a Mekanism personal-storage item with a present inventory, count backpacks inside it. */
    private static int countInStack(ItemStack stack, Set<Item> backpacks) {
        if (stack.isEmpty()) {
            return 0;
        }
        // getInventoryIfPresent (not getInventoryFor) so we never create an inventory as a side effect.
        Optional<AbstractPersonalStorageItemInventory> opt = PersonalStorageManager.getInventoryIfPresent(stack);
        if (opt.isEmpty()) {
            return 0;
        }
        int count = 0;
        List<IInventorySlot> slots = opt.get().getInventorySlots(null);
        for (IInventorySlot slot : slots) {
            // countStack also recurses the contained stack's IItemHandler → backpack-in-backpack inside
            // the personal chest is caught too.
            count += BackpackCounter.countStack(slot.getStack(), backpacks, 0);
        }
        return count;
    }
}
