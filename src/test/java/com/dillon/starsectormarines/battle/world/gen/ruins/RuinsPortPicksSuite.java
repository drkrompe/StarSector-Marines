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
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Concept cards for the owner's {@code ruins-port} pick ({@code ruins-expedition.md}, Part of the dead town is a
 * port): where the old colony's port lies in a ruins site's dead town. Each card is today's extensive ground through
 * the look preset at 1920x1080, roofs off, with the option's port drawn over it as a labelled plan (pad fields,
 * cargo yards, the terminal, fallen gantries). The overlay is a concept the game never draws. Opt-in:
 * {@code -Psnapshot=ruins-port-picks}.
 */
public final class RuinsPortPicksSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    private static final Color PADS = new Color(90, 170, 230), YARD = new Color(240, 170, 60),
            TERMINAL = new Color(230, 80, 80), GANTRY = new Color(220, 220, 220), PLANT = new Color(120, 200, 120);

    @Override public String id() { return "ruins-port-picks"; }

    @Override public String label() { return "Ruins port: concept cards for the ruins-port pick"; }

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
            int width = sim.getGrid().getWidth(), height = sim.getGrid().getHeight();
            BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), W, H,
                    width / 2f, height / 2f, width * 1.04f).withoutRoofs();
            BufferedImage base = look.render(sim, "");
            BattleCamera camera = look.cameraOf(sim);
            out.add(new SnapshotArtifact("a-port-round-the-landing.png", landing(base, camera, delve, height)));
            out.add(new SnapshotArtifact("b-freight-port-at-the-hull.png", outfeed(base, camera, delve, height)));
            out.add(new SnapshotArtifact("c-port-across-a-layer.png", layer(base, camera, delve, height)));
            out.add(new SnapshotArtifact("today.png", today(base, camera, delve)));
        }
        return out;
    }

    /** (a) The landing is the old port's surviving pad field; terminal and yards stand between it and layer 1. */
    private static BufferedImage landing(BufferedImage base, BattleCamera camera, RuinsDelve delve, int height) {
        BufferedImage card = copy(base);
        Graphics2D g = graphics(card);
        RuinsDelve.Area land = delve.landingGround();
        int portRight = land.right() + 70;
        int cy = (land.bottom() + land.top()) / 2;
        padField(g, camera, 4, Math.max(6, cy - 56), land.right() + 4, Math.min(height - 6, cy + 56));
        box(g, camera, land.right() + 8, cy - 12, land.right() + 34, cy + 12, TERMINAL, "TERMINAL");
        yard(g, camera, land.right() + 8, cy + 17, portRight, Math.min(height - 6, cy + 80), "CARGO YARD");
        yard(g, camera, land.right() + 8, Math.max(6, cy - 80), portRight, cy - 17, "CARGO YARD");
        gantry(g, camera, land.right() + 40, cy + 72, portRight - 2);
        gantry(g, camera, land.right() + 40, cy - 72, portRight - 2);
        plant(g, camera, delve);
        caption(g, "a. Port round the landing (recommended): land on the old pads, fight through its yards");
        g.dispose();
        return card;
    }

    /** (b) A freight port against the plant's hull, either side of the entrances: yards and gantries at its outfeed. */
    private static BufferedImage outfeed(BufferedImage base, BattleCamera camera, RuinsDelve delve, int height) {
        BufferedImage card = copy(base);
        Graphics2D g = graphics(card);
        RuinsDelve.Area in = delve.interior().footprint();
        int x1 = in.left() - 3, x0 = x1 - 44;
        int mid = height / 2;
        yard(g, camera, x0, 8, x1, mid - 24, "FREIGHT YARD");
        yard(g, camera, x0, mid + 24, x1, height - 8, "FREIGHT YARD");
        gantry(g, camera, x0 + 4, mid - 40, x1 - 2);
        gantry(g, camera, x0 + 4, mid + 40, x1 - 2);
        box(g, camera, x0 + 4, mid - 8, x0 + 22, mid + 8, TERMINAL, "FREIGHT OFFICE");
        RuinsDelve.Area land = delve.landingGround();
        padField(g, camera, land.left(), land.bottom(), land.right(), land.top());
        plant(g, camera, delve);
        caption(g, "b. Freight port at the plant's outfeed: yards along the hull face");
        g.dispose();
        return card;
    }

    /** (c) A whole outside layer is the port, across the map's height. */
    private static BufferedImage layer(BufferedImage base, BattleCamera camera, RuinsDelve delve, int height) {
        BufferedImage card = copy(base);
        Graphics2D g = graphics(card);
        RuinsDelve.Area in = delve.interior().footprint();
        RuinsDelve.Layer port = delve.layers().get(0);
        for (RuinsDelve.Layer l : delve.layers()) {
            if (l.ground().right() < in.left() && l.ground().width() > port.ground().width()) port = l;
        }
        RuinsDelve.Area ground = port.ground();
        int x0 = ground.left() + 3, x1 = Math.min(ground.right(), in.left() - 1) - 3, w = x1 - x0;
        padField(g, camera, x0, height * 2 / 3 + 3, x0 + w / 2, height - 8);
        padField(g, camera, x0, 8, x0 + w / 2, height / 3 - 3);
        box(g, camera, x0 + w / 2 - 10, height / 2 - 10, x0 + w / 2 + 10, height / 2 + 10, TERMINAL, "TERMINAL");
        yard(g, camera, x0 + w / 2 + 4, 8, x1, height / 2 - 14, "CARGO YARD");
        yard(g, camera, x0 + w / 2 + 4, height / 2 + 14, x1, height - 8, "CARGO YARD");
        gantry(g, camera, x0 + w / 2 + 6, height / 4, x1 - 2);
        gantry(g, camera, x0 + w / 2 + 6, height * 3 / 4, x1 - 2);
        plant(g, camera, delve);
        caption(g, "c. One outside layer is all port, across the map's height");
        g.dispose();
        return card;
    }

    /** Today: the landing and the plant, the dead town between them all ruined lots. */
    private static BufferedImage today(BufferedImage base, BattleCamera camera, RuinsDelve delve) {
        BufferedImage card = copy(base);
        Graphics2D g = graphics(card);
        RuinsDelve.Area land = delve.landingGround();
        box(g, camera, land.left(), land.bottom(), land.right(), land.top(), PADS, "LANDING");
        plant(g, camera, delve);
        caption(g, "Today: the dead town outside is all ruined lots");
        g.dispose();
        return card;
    }

    /** The plant, outlined for reference. */
    private static void plant(Graphics2D g, BattleCamera camera, RuinsDelve delve) {
        RuinsDelve.Area in = delve.interior().footprint();
        int[] r = rect(camera, in.left(), in.bottom(), in.right(), in.top());
        g.setColor(PLANT);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{14f, 10f}, 0f));
        g.drawRect(r[0], r[1], r[2], r[3]);
        tag(g, "THE PLANT", r[0] + r[2] / 2, r[1] + 40);
    }

    /** Ship pads: rings in a grid, open ground. */
    private static void padField(Graphics2D g, BattleCamera camera, int left, int bottom, int right, int top) {
        int[] r = rect(camera, left, bottom, right, top);
        g.setColor(new Color(PADS.getRed(), PADS.getGreen(), PADS.getBlue(), 50));
        g.fillRect(r[0], r[1], r[2], r[3]);
        float cell = camera.cellPxSize();
        int pad = Math.round(10 * cell), step = Math.round(14 * cell);
        g.setColor(new Color(PADS.getRed(), PADS.getGreen(), PADS.getBlue(), 200));
        g.setStroke(new BasicStroke(3f));
        for (int y = r[1] + step / 2 - pad / 2; y + pad <= r[1] + r[3]; y += step) {
            for (int x = r[0] + step / 2 - pad / 2; x + pad <= r[0] + r[2]; x += step) g.drawOval(x, y, pad, pad);
        }
        g.setColor(PADS);
        g.setStroke(new BasicStroke(4f));
        g.drawRect(r[0], r[1], r[2], r[3]);
        tag(g, "PAD FIELD", r[0] + r[2] / 2, r[1] + r[3] / 2);
    }

    /** A cargo yard: container rows with lanes between. */
    private static void yard(Graphics2D g, BattleCamera camera, int left, int bottom, int right, int top, String label) {
        int[] r = rect(camera, left, bottom, right, top);
        g.setColor(new Color(YARD.getRed(), YARD.getGreen(), YARD.getBlue(), 50));
        g.fillRect(r[0], r[1], r[2], r[3]);
        float cell = camera.cellPxSize();
        g.setColor(new Color(YARD.getRed(), YARD.getGreen(), YARD.getBlue(), 170));
        int row = Math.round(2 * cell), lane = Math.round(3 * cell), len = Math.round(6 * cell), gap = Math.round(2 * cell);
        for (int y = r[1] + lane; y + row <= r[1] + r[3] - lane; y += row + lane) {
            for (int x = r[0] + gap; x + len <= r[0] + r[2] - gap; x += len + gap) g.fillRect(x, y, len, row);
        }
        g.setColor(YARD);
        g.setStroke(new BasicStroke(4f));
        g.drawRect(r[0], r[1], r[2], r[3]);
        tag(g, label, r[0] + r[2] / 2, r[1] + r[3] / 2);
    }

    /** A fallen gantry span: a long low line across a yard. */
    private static void gantry(Graphics2D g, BattleCamera camera, int x0, int y, int x1) {
        int[] r = rect(camera, x0, y, x1, y);
        g.setColor(GANTRY);
        g.setStroke(new BasicStroke(5f));
        g.drawLine(r[0], r[1] + r[3] / 2, r[0] + r[2], r[1] + r[3] / 2);
        for (int x = r[0]; x <= r[0] + r[2]; x += 24) g.drawLine(x, r[1] - 4, x + 12, r[1] + r[3] + 4);
    }

    private static void box(Graphics2D g, BattleCamera camera, int left, int bottom, int right, int top, Color colour,
                            String label) {
        int[] r = rect(camera, left, bottom, right, top);
        g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 80));
        g.fillRect(r[0], r[1], r[2], r[3]);
        g.setColor(colour);
        g.setStroke(new BasicStroke(4f));
        g.drawRect(r[0], r[1], r[2], r[3]);
        tag(g, label, r[0] + r[2] / 2, r[1] + r[3] / 2);
    }

    /** Screen rectangle {x, y, w, h} for a cell box. */
    private static int[] rect(BattleCamera camera, int left, int bottom, int right, int top) {
        float x0 = camera.cellToScreenX(left), x1 = camera.cellToScreenX(right + 1);
        float y0 = H - camera.cellToScreenY(top + 1), y1 = H - camera.cellToScreenY(bottom);
        return new int[]{Math.round(x0), Math.round(y0), Math.round(x1 - x0), Math.round(y1 - y0)};
    }

    private static void tag(Graphics2D g, String label, int cx, int cy) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        int tw = g.getFontMetrics().stringWidth(label);
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRoundRect(cx - tw / 2 - 8, cy - 18, tw + 16, 28, 10, 10);
        g.setColor(Color.WHITE);
        g.drawString(label, cx - tw / 2, cy + 3);
    }

    private static void caption(Graphics2D g, String text) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        int tw = g.getFontMetrics().stringWidth(text);
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRoundRect(24, 18, tw + 40, 54, 14, 14);
        g.setColor(Color.WHITE);
        g.drawString(text, 44, 55);
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
