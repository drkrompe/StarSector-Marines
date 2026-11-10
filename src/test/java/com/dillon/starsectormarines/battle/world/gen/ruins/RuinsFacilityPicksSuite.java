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
 * Concept cards for the owner's {@code ruins-vault-facility} pick ({@code ruins-expedition.md}, The plant): how a
 * ruins site's vault grows from one small room into the Domain-era plant that made its prize. Each card is today's
 * extensive ground through the look preset at 1920x1080, roofs off, with the option's plant drawn over it as a
 * labelled plan. The overlay is a concept the game never draws. Opt-in: {@code -Psnapshot=ruins-facility-picks}.
 */
public final class RuinsFacilityPicksSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    private static final Color INTAKE = new Color(90, 170, 230), LINE = new Color(240, 170, 60),
            STORE = new Color(120, 200, 120), SECURE = new Color(230, 80, 80), YARD = new Color(200, 200, 120);

    @Override public String id() { return "ruins-facility-picks"; }

    @Override public String label() { return "Ruins vault as a plant: concept cards for the ruins-vault-facility pick"; }

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
            RuinsDelve.Area inside = delve.interior().footprint();
            RuinsDelve.Area vault = delve.vault().footprint();
            out.add(new SnapshotArtifact("a-chain-of-plant-halls.png", chain(base, camera, inside, vault)));
            out.add(new SnapshotArtifact("b-one-great-hall.png", greatHall(base, camera, delve, inside, vault)));
            out.add(new SnapshotArtifact("c-plant-takes-the-site.png", wholeSite(base, camera, delve, inside, vault,
                    width, height)));
            out.add(new SnapshotArtifact("today-vault-room.png", today(base, camera, vault)));
        }
        return out;
    }

    /** (a) The interior carved into linked halls in the plant's order, intake to secure store. */
    private static BufferedImage chain(BufferedImage base, BattleCamera camera, RuinsDelve.Area in,
                                       RuinsDelve.Area vault) {
        BufferedImage card = copy(base);
        Graphics2D g = graphics(card);
        halls(g, camera, in, vault);
        caption(g, "a. Chain of plant halls (recommended): intake, production, storage, secure store");
        g.dispose();
        return card;
    }

    /** Option a's halls over the interior. */
    private static void halls(Graphics2D g, BattleCamera camera, RuinsDelve.Area in, RuinsDelve.Area vault) {
        int w = in.width(), x0 = in.left(), b = in.bottom(), t = in.top(), h = in.height();
        int stage = w / 4;
        // Intake: docks along the hull's west face, one per entrance band.
        hall(g, camera, x0 + 2, b + h / 8, x0 + stage - 3, b + h * 3 / 8, INTAKE, "INTAKE DOCKS");
        hall(g, camera, x0 + 2, b + h * 5 / 8, x0 + stage - 3, b + h * 7 / 8, INTAKE, "INTAKE DOCKS");
        // Production: two line halls, line modules drawn as stripes.
        lineHall(g, camera, x0 + stage, b + 6, x0 + 2 * stage - 3, b + h / 2 - 3, "PRODUCTION LINE");
        lineHall(g, camera, x0 + stage, b + h / 2 + 3, x0 + 2 * stage - 3, t - 6, "PRODUCTION LINE");
        // Storage bays.
        hall(g, camera, x0 + 2 * stage, b + 8, x0 + 3 * stage - 3, b + h / 3, STORE, "STORAGE BAY");
        hall(g, camera, x0 + 2 * stage, b + h / 3 + 4, x0 + 3 * stage - 3, b + 2 * h / 3 - 4, STORE, "STORAGE BAY");
        hall(g, camera, x0 + 2 * stage, b + 2 * h / 3, x0 + 3 * stage - 3, t - 8, STORE, "STORAGE BAY");
        // Secure store: the cradle hall the prize survives in.
        int sy = (vault.bottom() + vault.top()) / 2;
        hall(g, camera, x0 + 3 * stage, sy - 14, Math.min(in.right() - 3, x0 + 3 * stage + 26), sy + 14, SECURE,
                "SECURE STORE");
        spine(g, camera, x0 + 2, (b + t) / 2, in.right() - 3);
    }

    /** (b) Each interior band one great production floor, the secure store at its far end. */
    private static BufferedImage greatHall(BufferedImage base, BattleCamera camera, RuinsDelve delve,
                                           RuinsDelve.Area in, RuinsDelve.Area vault) {
        BufferedImage card = copy(base);
        Graphics2D g = graphics(card);
        int sy = (vault.bottom() + vault.top()) / 2;
        int secureLeft = in.right() - 34;
        lineHall(g, camera, in.left() + 3, in.bottom() + 4, secureLeft - 4, in.top() - 4, "ONE GREAT PRODUCTION HALL");
        hall(g, camera, secureLeft, sy - 14, in.right() - 3, sy + 14, SECURE, "SECURE STORE");
        caption(g, "b. One great hall: the whole interior is a single production floor");
        g.dispose();
        return card;
    }

    /** (c) The plant is the whole site: yards and tank farms outside, the plant inside. */
    private static BufferedImage wholeSite(BufferedImage base, BattleCamera camera, RuinsDelve delve,
                                           RuinsDelve.Area in, RuinsDelve.Area vault, int width, int height) {
        BufferedImage card = copy(base);
        Graphics2D g = graphics(card);
        int landingRight = delve.layers().get(0).ground().left();
        hall(g, camera, landingRight + 4, 6, in.left() - 4, height / 2 - 3, YARD, "LOADING YARDS");
        hall(g, camera, landingRight + 4, height / 2 + 3, in.left() - 4, height - 6, YARD, "TANK FARM / STOCKYARD");
        halls(g, camera, in, vault);
        caption(g, "c. The plant takes the site: the dead town becomes its yards");
        g.dispose();
        return card;
    }

    /** Today: the one small vault room, ringed. */
    private static BufferedImage today(BufferedImage base, BattleCamera camera, RuinsDelve.Area vault) {
        BufferedImage card = copy(base);
        Graphics2D g = graphics(card);
        hall(g, camera, vault.left(), vault.bottom(), vault.right(), vault.top(), SECURE, "VAULT TODAY (11x9)");
        caption(g, "Today: the vault is one 11x9 room at the end of the megastructure");
        g.dispose();
        return card;
    }

    private static void hall(Graphics2D g, BattleCamera camera, int left, int bottom, int right, int top, Color colour,
                             String label) {
        int[] r = rect(camera, left, bottom, right, top);
        g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 70));
        g.fillRect(r[0], r[1], r[2], r[3]);
        g.setColor(colour);
        g.setStroke(new BasicStroke(4f));
        g.drawRect(r[0], r[1], r[2], r[3]);
        tag(g, label, r[0] + r[2] / 2, r[1] + r[3] / 2);
    }

    /** A hall with its line modules: stripes along its length. */
    private static void lineHall(Graphics2D g, BattleCamera camera, int left, int bottom, int right, int top,
                                 String label) {
        int[] r = rect(camera, left, bottom, right, top);
        g.setColor(new Color(LINE.getRed(), LINE.getGreen(), LINE.getBlue(), 60));
        g.fillRect(r[0], r[1], r[2], r[3]);
        g.setColor(new Color(LINE.getRed(), LINE.getGreen(), LINE.getBlue(), 150));
        float cell = camera.cellPxSize();
        for (int k = 1; k * 9 < top - bottom; k++) {
            int y = r[1] + Math.round(k * 9 * cell);
            g.fillRect(r[0] + Math.round(3 * cell), y - Math.round(1.5f * cell), r[2] - Math.round(6 * cell),
                    Math.round(3 * cell));
        }
        g.setColor(LINE);
        g.setStroke(new BasicStroke(4f));
        g.drawRect(r[0], r[1], r[2], r[3]);
        tag(g, label, r[0] + r[2] / 2, r[1] + r[3] / 2);
    }

    private static void spine(Graphics2D g, BattleCamera camera, int x0, int y, int x1) {
        int[] r = rect(camera, x0, y - 1, x1, y + 1);
        g.setColor(new Color(255, 255, 255, 120));
        g.fillRect(r[0], r[1], r[2], r[3]);
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
