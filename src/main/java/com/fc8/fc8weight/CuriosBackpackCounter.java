package com.fc8.fc8weight;

import java.util.Set;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.items.IItemHandlerModifiable;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

/**
 * Counts SBP backpacks equipped in the player's Curios slots (e.g. the back slot the SBP↔Curios
 * integration exposes). Isolated so the Curios types above are only class-loaded when
 * {@link WeightHandler} calls it behind a {@code ModList.isLoaded("curios")} guard — on a Curios-less
 * install this class is never referenced, so its imports never trigger a {@link NoClassDefFoundError}.
 *
 * <p>{@code getEquippedCurios()} is a plain {@link IItemHandlerModifiable} of every equipped curio
 * stack, so {@link BackpackCounter#countHandler} scans it exactly like any other item handler —
 * catching a worn backpack and any backpacks nested inside it.
 */
final class CuriosBackpackCounter {

    private CuriosBackpackCounter() {
    }

    static int count(ServerPlayer player, Set<Item> backpacks) {
        if (backpacks.isEmpty()) {
            return 0;
        }
        try {
            ICuriosItemHandler inv = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
            if (inv == null) {
                return 0;
            }
            IItemHandlerModifiable equipped = inv.getEquippedCurios();
            if (equipped == null) {
                return 0;
            }
            return BackpackCounter.countHandler(equipped, backpacks);
        } catch (Throwable t) {
            // Never let Curios counting break the server tick.
            return 0;
        }
    }
}
