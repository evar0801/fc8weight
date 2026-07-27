package com.fc8.fc8weight;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Payload for the strength-gate tooltip row: what the weapon demands vs. what the player has.
 *
 * <p>Lives in common code because {@code RenderTooltipEvent.GatherComponents} takes the common
 * {@link TooltipComponent} interface; the client-only renderer that consumes it is registered
 * separately (see {@code com.fc8.fc8weight.Fc8WeightClientTooltip}).
 */
public record GateTooltipData(double requirement, double strength) implements TooltipComponent {

    public boolean sufficient() {
        return strength + 1.0e-4 >= requirement;
    }

    /** Signed slack: positive when the player has room to spare, negative when short. */
    public double margin() {
        return strength - requirement;
    }
}
