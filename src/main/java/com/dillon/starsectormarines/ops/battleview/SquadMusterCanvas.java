package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.Rect;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.awt.Color;
import java.util.Objects;
import java.util.function.Supplier;

/** Three fire teams of four current billet holders, rendered from one scalable SVG cutout. */
public final class SquadMusterCanvas implements CanvasProducer {

    public static final int SURFACE_WIDTH = 216;
    public static final int SURFACE_HEIGHT = 96;

    private static final String[] TEAM_NAMES = {"ALPHA", "BRAVO", "CHARLIE"};
    private static final Color READY = new Color(0x80, 0xD0, 0xE6);
    private static final Color WOUNDED = new Color(0xA2, 0xB2, 0xBE);
    private static final Color MEDICAL = new Color(0xF3, 0xBC, 0x66);
    private static final Color VACANT = new Color(0x65, 0x7E, 0x91);
    private static final Color CAPTION = new Color(0x8C, 0xAC, 0xBC);
    private static final Color PANEL = new Color(0x0C, 0x1B, 0x27);
    private static final Color RULE = new Color(0x24, 0x42, 0x54);

    public record Palette(Color ready, Color wounded, Color medical, Color vacant,
                          Color caption, Color panel, Color rule) { }
    public static final Palette SHIPBOARD = new Palette(new Color(0xBF, 0xCA, 0xAF),
            new Color(0x98, 0x9E, 0x91), new Color(0xE3, 0xBB, 0x71), new Color(0x68, 0x72, 0x64),
            new Color(0xA5, 0xB0, 0xA0), new Color(0x16, 0x20, 0x1A), new Color(0x39, 0x46, 0x37));
    private final Palette palette;

    private final Supplier<SquadMuster> muster;
    private final SvgAsset marine;
    private final SvgAsset vacancy;

    public SquadMusterCanvas(Supplier<SquadMuster> muster) {
        this(muster, new Palette(READY, WOUNDED, MEDICAL, VACANT, CAPTION, PANEL, RULE));
    }

    public SquadMusterCanvas(Supplier<SquadMuster> muster, Palette palette) {
        this.muster = Objects.requireNonNull(muster, "squad muster supplier");
        this.palette = Objects.requireNonNull(palette, "palette");
        marine = SvgAsset.load("graphics/ui/formation/marine-cutout.svg");
        vacancy = SvgAsset.load("graphics/ui/formation/marine-cutout-vacant.svg");
    }

    @Override
    public void draw(CanvasContext context) {
        SquadMuster projection = muster.get();
        if (projection == null) return;
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        float teamWidth = teamWidth(width);
        for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
            float x = team * (teamWidth + teamGap(width));
            context.fillRect(x, 0f, teamWidth, height, palette.panel());
            String name = TEAM_NAMES[team];
            context.text(Fonts.ORBITRON_10, name,
                    x + (teamWidth - Fonts.ORBITRON_10.measureWidth(name)) * 0.5f,
                    1f, palette.caption());
            context.line(x + 5f, 13f, x + teamWidth - 5f, 13f, palette.rule(), 1f);
        }
        for (SquadMuster.Billet billet : projection.billets()) {
            drawBillet(context, billet, billetBounds(billet.index(), width, height));
        }
    }

    /**
     * Shared local geometry for optional retained hover targets. Team membership
     * follows consecutive roster billets; each team is shown as a two-by-two group.
     */
    public static Rect billetBounds(int index, float width, float height) {
        if (index < 0 || index >= MarineSquad.CAPACITY) return Rect.EMPTY;
        int team = index / MarineSquad.TEAM_SIZE;
        int teamBillet = index % MarineSquad.TEAM_SIZE;
        float teamWidth = teamWidth(width);
        float cellWidth = teamWidth / 2f;
        float cellHeight = Math.max(0f, height - 14f) / 2f;
        return new Rect(team * (teamWidth + teamGap(width)) + (teamBillet % 2) * cellWidth,
                14f + (teamBillet / 2) * cellHeight, cellWidth, cellHeight);
    }

    private void drawBillet(CanvasContext context, SquadMuster.Billet billet, Rect slot) {
        float iconHeight = Math.max(0f, slot.height() - 3f);
        float iconWidth = iconHeight * 2f / 3f;
        float x = slot.x() + (slot.width() - iconWidth) * 0.5f;
        float y = slot.y() + 1f;
        Color tint = switch (billet.state()) {
            case READY -> palette.ready();
            case WOUNDED -> palette.wounded();
            case VACANT -> palette.vacant();
        };
        context.svg(billet.state() == SquadMuster.State.VACANT ? vacancy : marine,
                x, y, iconWidth, iconHeight, tint);
        context.line(slot.x() + 6f, slot.bottom() - 1f,
                slot.right() - 6f, slot.bottom() - 1f, palette.rule(), 1f);

        if (billet.state() == SquadMuster.State.WOUNDED) {
            // This stripe and the cross make WIA distinct even without color.
            context.line(x + iconWidth * 0.22f, y + iconHeight * 0.52f,
                    x + iconWidth * 0.76f, y + iconHeight * 0.30f, palette.medical(), 2.2f);
            float badgeX = slot.right() - 8f;
            float badgeY = slot.bottom() - 10f;
            context.fillRect(badgeX - 1f, badgeY - 1f, 7f, 7f, palette.panel());
            context.fillRect(badgeX + 2f, badgeY, 1.8f, 5f, palette.medical());
            context.fillRect(badgeX, badgeY + 1.6f, 5.8f, 1.8f, palette.medical());
        }
        if (billet.leader()) {
            float chevronX = slot.x() + 4f;
            context.line(chevronX, slot.y() + 6f, chevronX + 2.5f,
                    slot.y() + 3.5f, palette.caption(), 1.2f);
            context.line(chevronX + 2.5f, slot.y() + 3.5f, chevronX + 5f,
                    slot.y() + 6f, palette.caption(), 1.2f);
        }
    }

    private static float teamGap(float width) { return width / 36f; }

    private static float teamWidth(float width) {
        return (width - teamGap(width) * 2f) / MarineSquad.TEAMS_PER_SQUAD;
    }
}
