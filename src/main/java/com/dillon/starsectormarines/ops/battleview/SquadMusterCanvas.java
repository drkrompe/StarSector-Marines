package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.Rect;

import java.awt.Color;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Three fire teams of four current billet holders, each drawn as the battle draws it: standing in
 * its own kit, lying with a wound, and a vacant place left empty and crossed, so what a squad has
 * lost reads from the shape of its ranks.
 */
public final class SquadMusterCanvas implements CanvasProducer {

    public static final int SURFACE_WIDTH = 216;
    public static final int SURFACE_HEIGHT = 96;

    private static final String[] TEAM_NAMES = {"ALPHA", "BRAVO", "CHARLIE"};
    private static final String WIDEST_TEAM_NAME = "CHARLIE";
    /** Room beside the longest name, so neighbouring teams' names never read as one. */
    private static final float NAME_MARGIN = 6f;
    private static final float NAMED_HEADER_HEIGHT = 18f;
    /** The standing figure's shoulder width, as a share of its place's height and, where that is the tighter, width. */
    private static final float SHOULDER_OF_HEIGHT = 1f;
    private static final float SHOULDER_OF_WIDTH = 0.72f;

    public record Palette(Color lost, Color medical, Color caption, Color panel, Color rule, Color medic) { }
    public static final Palette SHIPBOARD = new Palette(new Color(0xC8, 0x55, 0x4A),
            new Color(0xE3, 0xBB, 0x71), Color.WHITE, new Color(0x0C, 0x1B, 0x27),
            new Color(0x24, 0x42, 0x54), new Color(0x73, 0xF2, 0x8C));
    private final Palette palette;

    private final Supplier<SquadMuster> muster;
    private final ArmoryLoadoutPreviewComposer.Assets assets;
    private FireTeamBillet standIn;

    public SquadMusterCanvas(Supplier<SquadMuster> muster, Palette palette,
                             ArmoryLoadoutPreviewComposer.Assets assets) {
        this.muster = Objects.requireNonNull(muster, "squad muster supplier");
        this.palette = Objects.requireNonNull(palette, "palette");
        this.assets = Objects.requireNonNull(assets, "marine sprites");
    }

    @Override
    public void draw(CanvasContext context) {
        SquadMuster projection = muster.get();
        if (projection == null) return;
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        float teamWidth = teamWidth(width);
        BitmapFont font = nameFont(width);
        for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
            float x = team * (teamWidth + teamGap(width));
            context.fillRect(x, 0f, teamWidth, height, palette.panel());
            String name = TEAM_NAMES[team];
            context.text(font, name, x + (teamWidth - font.measureWidth(name)) * 0.5f, 1f, palette.caption());
            float rule = headerHeight(width) - 1f;
            context.line(x + 5f, rule, x + teamWidth - 5f, rule, palette.rule(), 1f);
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
        float header = headerHeight(width);
        float cellHeight = Math.max(0f, height - header) / 2f;
        return new Rect(team * (teamWidth + teamGap(width)) + (teamBillet % 2) * cellWidth,
                header + (teamBillet / 2) * cellHeight, cellWidth, cellHeight);
    }

    /** The readable face where the longest name fits a team's width; a small muster keeps the narrow one. */
    private static BitmapFont nameFont(float width) {
        return Fonts.INSIGNIA_15_AA.measureWidth(WIDEST_TEAM_NAME) + NAME_MARGIN <= teamWidth(width)
                ? Fonts.INSIGNIA_15_AA : Fonts.ORBITRON_10;
    }

    /** The names and the rule under them; the same face on every surface of a width. */
    private static float headerHeight(float width) {
        return nameFont(width) == Fonts.ORBITRON_10 ? 14f : NAMED_HEADER_HEIGHT;
    }

    private void drawBillet(CanvasContext context, SquadMuster.Billet billet, Rect slot) {
        float centerX = slot.x() + slot.width() * 0.5f;
        float centerY = slot.y() + slot.height() * 0.5f;
        float shoulder = Math.min(slot.height() * SHOULDER_OF_HEIGHT, slot.width() * SHOULDER_OF_WIDTH);
        float surfaceHeight = context.metrics().surfaceHeight();
        var sink = new ArmoryFireTeamPreviewCanvas.CanvasSink(context);
        switch (billet.state()) {
            case READY -> ArmoryLoadoutPreviewComposer.composeStanding(sink, assets, kit(billet),
                    centerX, centerY, shoulder, surfaceHeight);
            case VACANT -> {
                float reach = slot.height() * 0.2f;
                context.line(centerX - reach, centerY - reach, centerX + reach, centerY + reach, palette.lost(), 1.6f);
                context.line(centerX + reach, centerY - reach, centerX - reach, centerY + reach, palette.lost(), 1.6f);
            }
        }
        if (billet.medic()) {
            // A filled plate with the cross cut out, top right: the squad medic,
            // never to be read as the bare amber cross of a wound.
            float plateX = slot.right() - 9f;
            float plateY = slot.y() + 2f;
            context.fillRect(plateX, plateY, 7f, 7f, palette.medic());
            context.fillRect(plateX + 2.6f, plateY + 1f, 1.8f, 5f, palette.panel());
            context.fillRect(plateX + 1f, plateY + 2.6f, 5f, 1.8f, palette.panel());
        }
        if (billet.leader()) {
            float chevronX = slot.x() + 4f;
            context.line(chevronX, slot.y() + 6f, chevronX + 2.5f,
                    slot.y() + 3.5f, palette.caption(), 1.2f);
            context.line(chevronX + 2.5f, slot.y() + 3.5f, chevronX + 5f,
                    slot.y() + 6f, palette.caption(), 1.2f);
        }
    }

    /** A billet standing in for no marine wears what a new recruit is issued. */
    private FireTeamBillet kit(SquadMuster.Billet billet) {
        if (billet.kit() != null) return billet.kit();
        if (standIn == null) {
            standIn = new FireTeamBillet(billet.name(), WeaponRegistry.STARTER_PRIMARY_ID,
                    EquipmentGrade.SERVICE, null, MarineArmorPattern.ARMORLESS.id);
        }
        return standIn;
    }

    private static float teamGap(float width) { return width / 36f; }

    private static float teamWidth(float width) {
        return (width - teamGap(width) * 2f) / MarineSquad.TEAMS_PER_SQUAD;
    }
}
