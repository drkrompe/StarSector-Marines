package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.DrawCommand;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Deterministic evidence for the durability bar, in two sheets. The ownership
 * sheet holds allegiance against damage state at one profile; the magnitude sheet
 * runs the mod's real authored durability profiles side by side, which is the only
 * way to judge whether segment density actually separates a militiaman from a
 * heavy mech.
 *
 * <p>This suite drains {@link DurabilityBarDecor} directly rather than a battle
 * frame. The bar is pure {@code SOLID_RECT} geometry in screen pixels, so a
 * Java2D replay of its draw list is the same picture the live drain paints —
 * without a simulation, an asset load, or an OpenGL context. Judging the design is
 * what this evidence is for; the geometry contract is pinned separately by
 * {@code DurabilityBarDecorTest}.
 */
public final class DurabilityBarSnapshotSuite implements SnapshotSuite {

    /** Magnification for the audit column. Integer, so every bar pixel stays square. */
    private static final int ZOOM = 5;

    private static final Color BACKDROP = new Color(0x11, 0x16, 0x1D);
    private static final Color PANEL = new Color(0x18, 0x1F, 0x28);
    private static final Color RULE = new Color(0x2A, 0x35, 0x42);
    private static final Color TEXT = new Color(0xD9, 0xE8, 0xF1);
    private static final Color TEXT_DIM = new Color(0x8A, 0x9B, 0xAA);
    /** Stand-in body under each true-scale bar, so placement above a unit is visible. */
    private static final Color BODY = new Color(0x3A, 0x46, 0x54);

    /**
     * One entity's authored durability and the bar width its body earns. Widths
     * follow the sweep's rule — a cell for infantry, render scale for a mech, the
     * structure's visual extent for an emplacement — at a 34px cell.
     */
    private record Profile(String label, float maxStructure, float maxArmor, float width) {

        float total() {
            return maxStructure + maxArmor;
        }
    }

    /** A fraction of total durability remaining, drained armor-first. */
    private record Drain(String label, float remaining) {
    }

    /** The emplacement profile the ownership sheet uses — armor heavier than structure. */
    private static final Profile EMPLACEMENT =
            new Profile("Hephaestus emplacement", 90f, 145f, 68f);

    private static final List<Profile> PROFILES = List.of(
            new Profile("Militia + Ward kit", 15f, 5f, 34f),
            new Profile("Marine + combat armor", 25f, 9f, 34f),
            new Profile("Marine + Foundry Breaker", 25f, 27f, 34f),
            new Profile("Vulcan emplacement", 60f, 80f, 68f),
            EMPLACEMENT,
            new Profile("Sirocco mech", 250f, 400f, 54f),
            new Profile("Bulwark mech", 550f, 950f, 54f));

    private static final List<Drain> DRAINS = List.of(
            new Drain("intact", 1f),
            new Drain("armor half gone", 0.75f),
            new Drain("armor stripped", 0.42f),
            new Drain("structure failing", 0.18f),
            new Drain("critical", 0.03f));

    private static final int LABEL_W = 176;
    private static final int ROW_H = 108;
    private static final int HEADER_H = 76;
    private static final int MARGIN = 22;

    @Override
    public String id() {
        return "durability-bars";
    }

    @Override
    public String label() {
        return "Durability bars";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) {
        return List.of(
                new SnapshotArtifact("durability-bar-ownership.png", ownershipSheet()),
                new SnapshotArtifact("durability-bar-magnitude.png", magnitudeSheet()));
    }

    /** Allegiance down, damage state across, at one emplacement profile. */
    private static BufferedImage ownershipSheet() {
        int cellW = (int) (EMPLACEMENT.width() * ZOOM) + 40;
        int width = MARGIN * 2 + LABEL_W + cellW * DRAINS.size();
        int height = MARGIN * 2 + HEADER_H + ROW_H * Allegiance.values().length;
        BufferedImage sheet = blank(width, height);
        Graphics2D g = sheet.createGraphics();
        configure(g);
        paintChrome(g, width, height, "Durability bars — ownership coding",
                "one band, armor draining ahead of structure; "
                        + EMPLACEMENT.label() + " at " + ZOOM + "x over true scale");
        paintColumnHeads(g, cellW, DRAINS.stream().map(Drain::label).toList());

        for (int row = 0; row < Allegiance.values().length; row++) {
            Allegiance owner = Allegiance.values()[row];
            int rowTop = paintRowBand(g, row, width, owner.name(),
                    owner.friendly() ? "friendly" : "not friendly");
            for (int column = 0; column < DRAINS.size(); column++) {
                int cellX = MARGIN + LABEL_W + column * cellW + 6;
                paintPair(g, owner, EMPLACEMENT, DRAINS.get(column).remaining(), cellX, rowTop);
            }
        }
        g.dispose();
        return sheet;
    }

    /** Real authored profiles down, damage state across, all read as hostile. */
    private static BufferedImage magnitudeSheet() {
        int widest = PROFILES.stream().mapToInt(p -> (int) p.width()).max().orElse(34);
        int cellW = widest * ZOOM + 40;
        int width = MARGIN * 2 + LABEL_W + cellW * 3;
        int height = MARGIN * 2 + HEADER_H + ROW_H * PROFILES.size();
        BufferedImage sheet = blank(width, height);
        Graphics2D g = sheet.createGraphics();
        configure(g);
        paintChrome(g, width, height, "Durability bars — magnitude by segment density",
                "one divider per " + (int) DurabilityBarDecor.SEGMENT_UNIT
                        + " durability, full height every "
                        + DurabilityBarDecor.MAJOR_EVERY_SEGMENTS
                        + "; the scale is the same for every unit");
        paintColumnHeads(g, cellW, List.of("intact", "armor stripped", "structure failing"));

        float[] states = {1f, 0.42f, 0.18f};
        for (int row = 0; row < PROFILES.size(); row++) {
            Profile profile = PROFILES.get(row);
            int rowTop = paintRowBand(g, row, width, profile.label(),
                    (int) profile.maxStructure() + " + " + (int) profile.maxArmor()
                            + " = " + (int) profile.total());
            for (int column = 0; column < states.length; column++) {
                int cellX = MARGIN + LABEL_W + column * cellW + 6;
                paintPair(g, Allegiance.ENEMY, profile, states[column], cellX, rowTop);
            }
        }
        g.dispose();
        return sheet;
    }

    /** Magnified bar above, true-scale bar with a stand-in body below. */
    private static void paintPair(Graphics2D g, Allegiance owner, Profile profile,
                                  float remaining, int cellX, int rowTop) {
        paintBar(g, owner, profile, remaining, cellX, rowTop + 62, ZOOM);
        g.setColor(BODY);
        g.fillRect(cellX + 8, rowTop + 90, (int) profile.width() - 16, 12);
        paintBar(g, owner, profile, remaining, cellX, rowTop + 88, 1);
    }

    /**
     * Replays the bar's own draw list. The render pipeline's screen space has +Y
     * up and the bar grows from {@code baseY}, so a row at bar-local {@code y}
     * lands {@code y * scale} pixels above the device baseline.
     */
    private static void paintBar(Graphics2D g, Allegiance owner, Profile profile,
                                 float remaining, int originX, int baselineY, int scale) {
        // Damage takes armor before structure, which is what the bar draws.
        float pool = profile.total() * remaining;
        float armor = Math.min(profile.maxArmor(), Math.max(0f, pool - profile.maxStructure()));
        float structure = Math.max(0f, pool - armor);

        DrawList out = new DrawList();
        DurabilityBarDecor.emit(out, RenderLayer.UNITS, owner,
                profile.width() / 2f, 0f, profile.width(),
                structure, profile.maxStructure(), armor, profile.maxArmor(), 1f);

        Object antialias = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        DrawCommand[] buffer = out.buffer(RenderLayer.UNITS);
        for (int i = 0; i < out.count(RenderLayer.UNITS); i++) {
            DrawCommand command = buffer[i];
            int x0 = originX + Math.round(command.centerX() * scale);
            int x1 = originX + Math.round(command.width() * scale);
            int y0 = baselineY - Math.round(command.height() * scale);
            int y1 = baselineY - Math.round(command.centerY() * scale);
            g.setColor(new Color(command.red(), command.green(), command.blue(), command.alpha()));
            g.fillRect(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias);
    }

    private static BufferedImage blank(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    private static void configure(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static void paintChrome(Graphics2D g, int width, int height,
                                    String title, String subtitle) {
        g.setColor(BACKDROP);
        g.fillRect(0, 0, width, height);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        g.setColor(TEXT);
        g.drawString(title, MARGIN, MARGIN + 18);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.setColor(TEXT_DIM);
        g.drawString(subtitle, MARGIN, MARGIN + 38);
    }

    private static void paintColumnHeads(Graphics2D g, int cellW, List<String> heads) {
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.setColor(TEXT_DIM);
        for (int column = 0; column < heads.size(); column++) {
            g.drawString(heads.get(column),
                    MARGIN + LABEL_W + column * cellW + 6, MARGIN + HEADER_H - 8);
        }
    }

    /** Paints one row's striped band and label; returns the row's top edge. */
    private static int paintRowBand(Graphics2D g, int row, int width,
                                    String name, String detail) {
        int rowTop = MARGIN + HEADER_H + row * ROW_H;
        g.setColor(row % 2 == 0 ? PANEL : BACKDROP);
        g.fillRect(MARGIN, rowTop, width - MARGIN * 2, ROW_H);
        g.setColor(RULE);
        g.drawLine(MARGIN, rowTop, width - MARGIN, rowTop);
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.drawString(name, MARGIN + 10, rowTop + 28);
        g.setColor(TEXT_DIM);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.drawString(detail, MARGIN + 10, rowTop + 46);
        return rowTop;
    }
}
