package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.fixture.RuinsBattleFixture;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.campaign.ruins.RuinsOccupant;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Concept cards for the owner's {@code ruins-forge-area} pick ({@code ruins-expedition.md}, A forge works has a
 * foundry): the forge theme's foundry, crucibles, pour channels of melt and casting beds, laid as plans over today's
 * ground through the look preset at 1920x1080, roofs off. The overlay is a concept the game never draws. Opt-in:
 * {@code -Psnapshot=ruins-forge-picks}.
 */
public final class RuinsForgePicksSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    private static final Color MELT = new Color(255, 120, 20), MELT_HOT = new Color(255, 220, 90),
            CRUCIBLE = new Color(70, 60, 55), CROSSING = new Color(150, 160, 170), COVER = new Color(60, 200, 120),
            WALL = new Color(235, 235, 235), SIGHT = new Color(255, 255, 255, 170);

    @Override public String id() { return "ruins-forge-picks"; }

    @Override public String label() { return "Ruins foundry: concept cards for the ruins-forge-area pick"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        RuinsBattleFixture fixture = RuinsBattleFixture.fromFactoryInputs(7L, BattleSetup.defaultManifest(), false,
                OperationTier.VETERAN, RiskLevel.MEDIUM, RuinsGroundEvidence.profile(SurfacePalette.ARID),
                FlybyRoster.EMPTY, FlybyRoster.EMPTY, RuinsGrade.EXTENSIVE, RuinsOccupant.SCAVENGERS, List.of(), null);
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = fixture.build()) {
            RuinsDelve delve = sim.getRuinsWorks().delve();
            RuinsDelve.Area hall = null;
            for (RuinsDelve.PlantHall h : delve.interior().halls()) {
                if (h.stage() == RuinsDelve.PlantStage.PRODUCTION) {
                    hall = h.footprint();
                    break;
                }
            }
            if (hall == null) throw new IllegalStateException("no line hall on the fixture's ground");
            int cx = hall.centreX(), cy = hall.centreY();
            BattleReviewFrameRenderer near = BattleReviewFrameRenderer.look(context.modRoot(), W, H, cx + 0.5f,
                    cy + 0.5f, 62f).withoutRoofs();
            BufferedImage base = near.render(sim, "");
            BattleCamera camera = near.cameraOf(sim);
            out.add(new SnapshotArtifact("a-one-foundry-hall.png", oneHall(base, camera, cx - 17, cy - 11)));
            out.add(new SnapshotArtifact("b-run-of-three-halls.png", threeHalls(base, camera, cx - 28, cy - 8)));
            RuinsDelve.Area in = delve.interior().footprint();
            BattleReviewFrameRenderer wide = BattleReviewFrameRenderer.look(context.modRoot(), W, H,
                    in.centreX() + 0.5f, in.centreY() + 0.5f, in.width() * 1.1f).withoutRoofs();
            BufferedImage whole = wide.render(sim, "");
            out.add(new SnapshotArtifact("c-melt-trunk-down-the-spine.png", trunk(whole, wide.cameraOf(sim), in)));
            out.add(new SnapshotArtifact("today-line-hall.png", today(base, camera, hall)));
        }
        return out;
    }

    /** (a) One 34x22 foundry hall: crucible bank north, three channels south to three beds, staggered crossings. */
    private static BufferedImage oneHall(BufferedImage base, BattleCamera c, int ox, int oy) {
        BufferedImage card = copy(base);
        Graphics2D g = graphics(card);
        dim(g);
        wall(g, c, ox, oy, ox + 33, oy + 21, "FOUNDRY HALL 34x22");
        int[] xs = {4, 15, 26};
        int[] crossY = {9, 13, 9};
        for (int k = 0; k < 3; k++) {
            int x = ox + xs[k];
            crucible(g, c, x, oy + 17, k == 2);
            melt(g, c, x + 1, oy + 7, x + 2, oy + 16);
            melt(g, c, x - 2, oy + 2, x + 5, oy + 6);
            tag(g, "CASTING BED", c, x + 1.5f, oy + 4f);
            crossing(g, c, x + 1, oy + crossY[k], x + 2, oy + crossY[k] + 1);
            cover(g, c, x - 1, oy + crossY[k], x, oy + crossY[k] + 1);
            cover(g, c, x + 3, oy + crossY[k] + 2, x + 4, oy + crossY[k] + 3);
        }
        cover(g, c, ox + 8, oy + 19, ox + 13, oy + 20);
        cover(g, c, ox + 19, oy + 19, ox + 24, oy + 20);
        tag(g, "COOLING RACK", c, ox + 16.5f, oy + 21.5f);
        cover(g, c, ox + 31, oy + 18, ox + 32, oy + 19);
        tag(g, "PULPIT", c, ox + 32f, oy + 17f);
        cover(g, c, ox + 11, oy + 14, ox + 12, oy + 15);
        tag(g, "FALLEN LADLE", c, ox + 11.5f, oy + 13.3f);
        sight(g, c, ox + 6.5f, oy + 21f, ox + 6.5f, oy + 2f);
        sight(g, c, ox + 0.5f, oy + 11.5f, ox + 33.5f, oy + 11.5f);
        legend(g);
        caption(g, "a. One foundry hall (recommended): crucibles north, channels to casting beds, staggered crossings");
        g.dispose();
        return card;
    }

    /** (b) A run of three halls joined by doors: melt shop, pour hall, casting hall. */
    private static BufferedImage threeHalls(BufferedImage base, BattleCamera c, int ox, int oy) {
        BufferedImage card = copy(base);
        Graphics2D g = graphics(card);
        dim(g);
        wall(g, c, ox, oy, ox + 13, oy + 15, "MELT SHOP");
        wall(g, c, ox + 16, oy, ox + 35, oy + 15, "POUR HALL");
        wall(g, c, ox + 38, oy, ox + 51, oy + 15, "CASTING HALL");
        crucible(g, c, ox + 3, oy + 10, false);
        crucible(g, c, ox + 8, oy + 10, true);
        cover(g, c, ox + 4, oy + 4, ox + 5, oy + 5);
        cover(g, c, ox + 9, oy + 3, ox + 10, oy + 4);
        // The pour hall's one channel winds across it, west to east, three crossings.
        melt(g, c, ox + 16, oy + 11, ox + 23, oy + 12);
        melt(g, c, ox + 22, oy + 4, ox + 23, oy + 12);
        melt(g, c, ox + 22, oy + 4, ox + 29, oy + 5);
        melt(g, c, ox + 28, oy + 4, ox + 29, oy + 12);
        melt(g, c, ox + 28, oy + 11, ox + 35, oy + 12);
        crossing(g, c, ox + 19, oy + 11, ox + 20, oy + 12);
        crossing(g, c, ox + 22, oy + 7, ox + 23, oy + 8);
        crossing(g, c, ox + 28, oy + 7, ox + 29, oy + 8);
        cover(g, c, ox + 18, oy + 6, ox + 19, oy + 7);
        cover(g, c, ox + 25, oy + 9, ox + 26, oy + 10);
        cover(g, c, ox + 32, oy + 6, ox + 33, oy + 7);
        for (int k = 0; k < 4; k++) {
            int bx = ox + 40 + (k % 2) * 6, by = oy + 2 + (k / 2) * 7;
            melt(g, c, bx, by, bx + 3, by + 3);
        }
        cover(g, c, ox + 44, oy + 6, ox + 45, oy + 8);
        door(g, c, ox + 14, oy + 7);
        door(g, c, ox + 36, oy + 7);
        sight(g, c, ox + 16f, oy + 2.5f, ox + 36f, oy + 2.5f);
        legend(g);
        caption(g, "b. A run of three halls: melt shop, pour hall, casting hall, joined by doors");
        g.dispose();
        return card;
    }

    /** (c) One melt trunk down the plant's spine, crucibles at its head, beds off it in the side halls. */
    private static BufferedImage trunk(BufferedImage base, BattleCamera c, RuinsDelve.Area in) {
        BufferedImage card = copy(base);
        Graphics2D g = graphics(card);
        dim(g);
        int y = in.centreY();
        int x0 = in.left() + 10, x1 = in.left() + in.width() * 3 / 4;
        for (int k = 0; k < 3; k++) crucible(g, c, in.left() + 3, y - 13 + k * 9, k == 1);
        melt(g, c, x0, y - 1, x1, y + 1);
        for (int x = x0 + 12; x < x1 - 6; x += 22) {
            melt(g, c, x, y + 2, x + 1, y + 14);
            melt(g, c, x - 4, y + 15, x + 5, y + 20);
            melt(g, c, x + 8, y - 14, x + 9, y - 2);
            melt(g, c, x + 4, y - 20, x + 13, y - 15);
            crossing(g, c, x - 6, y - 1, x - 5, y + 1);
        }
        tag(g, "MELT TRUNK", c, (x0 + x1) / 2f, y + 3f);
        tag(g, "CRUCIBLE BANK", c, in.left() + 4.5f, y + 19f);
        legend(g);
        caption(g, "c. One melt trunk down the spine, casting beds off it in the side halls");
        g.dispose();
        return card;
    }

    /** Today: the line hall whose slot the foundry takes. */
    private static BufferedImage today(BufferedImage base, BattleCamera c, RuinsDelve.Area hall) {
        BufferedImage card = copy(base);
        Graphics2D g = graphics(card);
        wall(g, c, hall.left(), hall.bottom(), hall.right(), hall.top(), "LINE HALL TODAY");
        caption(g, "Today: the outer band's first line hall, the slot the foundry takes");
        g.dispose();
        return card;
    }

    private static void melt(Graphics2D g, BattleCamera c, int left, int bottom, int right, int top) {
        int[] r = rect(c, left, bottom, right, top);
        g.setPaint(new GradientPaint(r[0], r[1], MELT_HOT, r[0] + r[2], r[1] + r[3], MELT));
        g.fillRect(r[0], r[1], r[2], r[3]);
        g.setPaint(null);
        g.setColor(new Color(60, 40, 30));
        g.setStroke(new BasicStroke(3f));
        g.drawRect(r[0], r[1], r[2], r[3]);
    }

    private static void crucible(Graphics2D g, BattleCamera c, int x, int y, boolean tipped) {
        int[] r = rect(c, x, y, x + 2, y + 2);
        g.setColor(CRUCIBLE);
        g.fillRect(r[0], r[1], r[2], r[3]);
        g.setColor(tipped ? new Color(140, 70, 30) : MELT_HOT);
        int inset = r[2] / 5;
        g.fillOval(r[0] + inset, r[1] + inset, r[2] - 2 * inset, r[3] - 2 * inset);
        g.setColor(MELT);
        g.setStroke(new BasicStroke(3f));
        g.drawRect(r[0], r[1], r[2], r[3]);
        tag(g, tipped ? "CRUCIBLE (TIPPED)" : "CRUCIBLE", c, x + 1.5f, y + 3.6f);
    }

    private static void crossing(Graphics2D g, BattleCamera c, int left, int bottom, int right, int top) {
        int[] r = rect(c, left - 1, bottom, right + 1, top);
        g.setColor(CROSSING);
        g.fillRect(r[0], r[1], r[2], r[3]);
        g.setColor(new Color(80, 90, 100));
        g.setStroke(new BasicStroke(2f));
        for (int x = r[0]; x < r[0] + r[2]; x += 6) g.drawLine(x, r[1], x, r[1] + r[3]);
        g.drawRect(r[0], r[1], r[2], r[3]);
    }

    private static void cover(Graphics2D g, BattleCamera c, int left, int bottom, int right, int top) {
        int[] r = rect(c, left, bottom, right, top);
        g.setColor(new Color(COVER.getRed(), COVER.getGreen(), COVER.getBlue(), 120));
        g.fillRect(r[0], r[1], r[2], r[3]);
        g.setColor(COVER);
        g.setStroke(new BasicStroke(3f));
        g.drawRect(r[0], r[1], r[2], r[3]);
    }

    private static void wall(Graphics2D g, BattleCamera c, int left, int bottom, int right, int top, String label) {
        int[] r = rect(c, left, bottom, right, top);
        g.setColor(WALL);
        g.setStroke(new BasicStroke(5f));
        g.drawRect(r[0], r[1], r[2], r[3]);
        tag(g, label, r[0] + r[2] / 2, r[1] - 18);
    }

    private static void door(Graphics2D g, BattleCamera c, int x, int y) {
        int[] r = rect(c, x, y, x + 1, y + 1);
        g.setColor(new Color(40, 40, 40));
        g.fillRect(r[0], r[1], r[2], r[3]);
        g.setColor(WALL);
        g.setStroke(new BasicStroke(2f));
        g.drawRect(r[0], r[1], r[2], r[3]);
    }

    private static void sight(Graphics2D g, BattleCamera c, float x0, float y0, float x1, float y1) {
        g.setColor(SIGHT);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{12f, 10f}, 0f));
        g.drawLine(Math.round(c.cellToScreenX(x0)), Math.round(H - c.cellToScreenY(y0)),
                Math.round(c.cellToScreenX(x1)), Math.round(H - c.cellToScreenY(y1)));
    }

    /** Darkens the frame so the plan reads over it. */
    private static void dim(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 90));
        g.fillRect(0, 0, W, H);
    }

    private static void legend(Graphics2D g) {
        int x = 1470, y = 900;
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRoundRect(x - 16, y - 28, 440, 170, 14, 14);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        Object[][] rows = {{MELT, "Melt (lava void): no walk, no cover"}, {CROSSING, "Grate crossing"},
                {COVER, "Cover"}, {SIGHT, "Sight line"}, {CRUCIBLE, "Crucible (heavy cover)"}};
        for (int k = 0; k < rows.length; k++) {
            g.setColor((Color) rows[k][0]);
            g.fillRect(x, y - 16 + k * 28, 22, 18);
            g.setColor(Color.WHITE);
            g.drawString((String) rows[k][1], x + 34, y + k * 28);
        }
    }

    /** Screen rectangle {x, y, w, h} for a cell box. */
    private static int[] rect(BattleCamera c, int left, int bottom, int right, int top) {
        float x0 = c.cellToScreenX(left), x1 = c.cellToScreenX(right + 1);
        float y0 = H - c.cellToScreenY(top + 1), y1 = H - c.cellToScreenY(bottom);
        return new int[]{Math.round(x0), Math.round(y0), Math.round(x1 - x0), Math.round(y1 - y0)};
    }

    private static void tag(Graphics2D g, String label, BattleCamera c, float x, float y) {
        tag(g, label, Math.round(c.cellToScreenX(x)), Math.round(H - c.cellToScreenY(y)));
    }

    private static void tag(Graphics2D g, String label, int cx, int cy) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        int tw = g.getFontMetrics().stringWidth(label);
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRoundRect(cx - tw / 2 - 7, cy - 16, tw + 14, 24, 10, 10);
        g.setColor(Color.WHITE);
        g.drawString(label, cx - tw / 2, cy + 2);
    }

    private static void caption(Graphics2D g, String text) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        int tw = g.getFontMetrics().stringWidth(text);
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRoundRect(24, 18, tw + 40, 52, 14, 14);
        g.setColor(Color.WHITE);
        g.drawString(text, 44, 53);
    }

    private static Graphics2D graphics(BufferedImage card) {
        Graphics2D g = card.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    private static BufferedImage copy(BufferedImage frame) {
        BufferedImage out = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(frame, 0, 0, null);
        g.dispose();
        return out;
    }
}
