package com.fc8.fc8weight.client;

import org.joml.Matrix4f;

import com.fc8.fc8weight.Fc8Weight;
import com.fc8.fc8weight.GateTooltipData;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Icon-and-text tooltip row for the strength gate, replacing the old
 * 「必要筋骨: 10.1 / あなたの筋骨: 3.0」 line whose slash read as a division.
 *
 * <p>Three signals fire together when the player is under-strength, so the state is legible without
 * parsing any numbers: a ⚠ badge appears, the anvil (the same icon EpicClassMod's class book uses for
 * 筋骨) swaps to a cracked, desaturated variant, and both icons and text pulse red. When the player
 * is strong enough the row is a single intact anvil and calm green text.
 *
 * <p>The pulse is driven off wall-clock time rather than game ticks because tooltips render while the
 * game is paused in a single-player inventory screen, where the tick counter stops advancing.
 */
public final class GateTooltipRenderer implements ClientTooltipComponent {

    private static final ResourceLocation ANVIL =
            new ResourceLocation(Fc8Weight.MODID, "textures/gui/strength_anvil.png");
    private static final ResourceLocation ANVIL_CRACKED =
            new ResourceLocation(Fc8Weight.MODID, "textures/gui/strength_anvil_cracked.png");
    private static final ResourceLocation WARNING =
            new ResourceLocation(Fc8Weight.MODID, "textures/gui/strength_warning.png");

    private static final int ICON = 16;
    private static final int GAP = 2;
    private static final int ROW_HEIGHT = 18;

    /** One full bright→dim→bright cycle, slow enough to read as a pulse rather than a flicker. */
    private static final float PULSE_PERIOD_MS = 1400.0f;

    private static final int COLOR_OK = 0xFF55FF55;
    private static final int PULSE_BRIGHT = 0xFFFF6B6B;
    private static final int PULSE_DIM = 0xFF9B1C1C;

    private final GateTooltipData data;

    public GateTooltipRenderer(GateTooltipData data) {
        this.data = data;
    }

    @Override
    public int getHeight() {
        return ROW_HEIGHT;
    }

    @Override
    public int getWidth(Font font) {
        return iconStripWidth() + font.width(text());
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        if (data.sufficient()) {
            graphics.blit(ANVIL, x, y + 1, 0, 0, ICON, ICON, ICON, ICON);
            return;
        }
        // Dim the icons in step with the text so the whole row breathes as one unit.
        float shade = 0.6f + 0.4f * pulse();
        graphics.setColor(1.0f, shade, shade, 1.0f);
        graphics.blit(WARNING, x, y + 1, 0, 0, ICON, ICON, ICON, ICON);
        graphics.blit(ANVIL_CRACKED, x + ICON + GAP, y + 1, 0, 0, ICON, ICON, ICON, ICON);
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource buffer) {
        int color = data.sufficient() ? COLOR_OK : lerpColor(PULSE_DIM, PULSE_BRIGHT, pulse());
        font.drawInBatch(text(), (float) (x + iconStripWidth()), (float) (y + 5), color, false, matrix, buffer,
                Font.DisplayMode.NORMAL, 0, 15728880);
    }

    private int iconStripWidth() {
        return (data.sufficient() ? ICON : ICON * 2 + GAP) + GAP + 2;
    }

    /**
     * Row text. Deliberately avoids a slash between the two figures — the previous
     * 「必要筋骨: A / あなたの筋骨: B」 form was being read as a fraction. Each figure now carries its
     * own label and the shortfall is spelled out rather than left for the reader to subtract.
     */
    private String text() {
        String req = num(data.requirement());
        String has = num(data.strength());
        if (data.sufficient()) {
            return "必要筋骨 " + req + "　→　あなた " + has + "　装備できる";
        }
        return "必要筋骨 " + req + "　→　あなた " + has + "　あと " + num(-data.margin()) + " 足りない";
    }

    /** Whole numbers render without a decimal point; the tier table produces integers by design. */
    private static String num(double v) {
        return Math.abs(v - Math.rint(v)) < 0.05 ? String.valueOf((int) Math.rint(v)) : String.format("%.1f", v);
    }

    /** 0→1→0 over {@link #PULSE_PERIOD_MS}, shaped as a sine so it eases at both ends. */
    private static float pulse() {
        float phase = (System.currentTimeMillis() % (long) PULSE_PERIOD_MS) / PULSE_PERIOD_MS;
        return 0.5f + 0.5f * Mth.sin(phase * ((float) Math.PI * 2.0f));
    }

    private static int lerpColor(int from, int to, float t) {
        int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
