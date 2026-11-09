package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
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
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Picture cards for the owner pick {@code ruins-vault-access} ({@code ruins-works.md}): what the player does to open
 * the vault, on an empty scattered site through the look preset at 1920x1080, roofs lifted. Each card frames the
 * whole chain from the power hall to the vault's door, numbers its steps on the ground, draws the power's path, and
 * shows in a panel what the door's prompt tells the player, with the door itself inset at play zoom. Options: (a) power
 * only, made legible; (b) the records' code at a keypad by the door; (c) two keys turned at once, one at the records
 * console and one at the door. The options are staged for the picture, not built: badges, lines, panels and the inset
 * frame are a review overlay. Opt-in: {@code -Psnapshot=ruins-vault-access-picks}.
 */
public final class RuinsVaultAccessPicksSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    private static final Color POWER = new Color(255, 196, 64);
    private static final Color STEP = new Color(70, 170, 255);
    private static final Color PAIR = new Color(120, 230, 140);

    @Override public String id() { return "ruins-vault-access-picks"; }

    @Override public String label() { return "Ruins works: picture cards for the vault access pick"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        long seed = Long.getLong("snapshot.ruinsVaultAccess.seed", 31L);
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = BattleSetup.createRuins(seed, BattleSetup.defaultManifest(), false,
                OperationTier.FIRST_CONTRACT, RiskLevel.MEDIUM, profile(), FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                RuinsGrade.SCATTERED, RuinsOccupant.NOBODY)) {
            for (int t = 0; t < Math.round(5f / BattleSimulation.TICK_DT); t++) sim.advance(BattleSimulation.TICK_DT);
            RuinsWorks works = sim.getRuinsWorks();
            SealDoor seal = null;
            for (SealDoor door : works.doors()) if (door.id().equals(RuinsWorks.VAULT_SEAL)) seal = door;
            if (seal == null) throw new IllegalStateException("the site's works have no vault seal");
            RuinsWorks.Site hall = works.hall(), records = works.records();
            RuinsWorks.Site junction = works.junctions().get(works.junctions().size() - 1);
            float[] door = {seal.middle()[0] + .5f, seal.middle()[1] + .5f};
            // The keypad stands on the antechamber's floor in front of the vault's door.
            float[] keypad = {door[0] - 1.5f, door[1] - 1f};

            float[][] points = {{hall.x() + .5f, hall.y() + .5f}, {junction.x() + .5f, junction.y() + .5f}, door,
                    records == null ? door : new float[]{records.x() + .5f, records.y() + .5f}};
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (float[] p : points) {
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minY = Math.min(minY, p[1]);
                maxY = Math.max(maxY, p[1]);
            }
            float cx = (minX + maxX) / 2f, cy = (minY + maxY) / 2f - 4f;
            float zoom = Math.max(maxX - minX + 30f, (maxY - minY + 34f) * W / H);
            BattleReviewFrameRenderer wide = BattleReviewFrameRenderer.look(context.modRoot(), W, H, cx, cy, zoom)
                    .withoutRoofs();
            BufferedImage base = wide.render(sim, "");
            BattleCamera camera = wide.cameraOf(sim);
            BufferedImage inset = BattleReviewFrameRenderer.look(context.modRoot(), 480, 300, door[0], door[1],
                    BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 0.3f).withoutRoofs().render(sim, "");

            float[] h = {hall.x() + .5f, hall.y() + .5f}, j = {junction.x() + .5f, junction.y() + .5f};
            float[] r = records == null ? null : new float[]{records.x() + .5f, records.y() + .5f};

            Card a = new Card(base, camera, inset, "Option A: power only, made legible");
            a.power(h, j, door);
            a.step(h, 1, "Restart the power hall", "hold 6 s");
            a.step(j, 2, junction.name() + ": feed the way on", "hold 2 s");
            a.step(door, 3, "Vault door opens", "powered breach");
            a.panel("Vault door  LOCKED: POWER ONLY",
                    "Needs power: restart the hall, feed the way on at " + junction.name() + ".",
                    "Lamp: red no power, green open.  Junction marker: FEEDS VAULT DOOR.");
            out.add(new SnapshotArtifact("ruins-vault-access/a-power-only.png", a.done()));

            Card b = new Card(base, camera, inset, "Option B: the records' code at the vault keypad");
            b.power(h, j, door);
            if (r != null) b.step(r, 1, "Read the records: the vault code", "hold 4 s");
            b.step(h, 2, "Restart the power hall", "hold 6 s");
            b.step(j, 3, junction.name() + ": feed the way on", "hold 2 s");
            b.step(keypad, 4, "Enter the code at the keypad", "hold 3 s, powered");
            b.panel("Vault door  LOCKED",
                    "Needs: power [ ]   the vault code [ ] (read the records)",
                    "Lamp: red no power, amber powered and coded lock, green open.");
            out.add(new SnapshotArtifact("ruins-vault-access/b-records-code.png", b.done()));

            Card c = new Card(base, camera, inset, "Option C: two keys at once, records console and vault door");
            c.power(h, j, door);
            c.step(h, 1, "Restart the power hall", "hold 6 s");
            c.step(j, 2, junction.name() + ": feed the way on", "hold 2 s");
            if (r != null) {
                c.pair(r, keypad);
                c.step(r, 3, "Squad 1: key at the records", "hold together 3 s");
            }
            c.step(keypad, 3, "Squad 2: key at the vault door", "hold together 3 s");
            c.panel("Vault door  LOCKED: TWO KEYS",
                    "Needs: power [ ]   keys turned together 0/2 (records console, vault door)",
                    "Lamp: red no power, amber powered awaiting keys, green open.");
            out.add(new SnapshotArtifact("ruins-vault-access/c-two-keys.png", c.done()));
        }
        return out;
    }

    /** One annotated card over the wide frame. */
    private static final class Card {
        private final BufferedImage image;
        private final Graphics2D g;
        private final BattleCamera camera;
        private final List<Runnable> badges = new ArrayList<>();

        Card(BufferedImage base, BattleCamera camera, BufferedImage inset, String title) {
            this.image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            this.camera = camera;
            g = image.createGraphics();
            g.drawImage(base, 0, 0, null);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            label(24, 24, title, 30, new Color(0, 0, 0, 185));
            int ix = 24, iy = 92;
            g.setColor(new Color(0, 0, 0, 200));
            g.fillRect(ix - 6, iy - 6, inset.getWidth() + 12, inset.getHeight() + 40);
            g.drawImage(inset, ix, iy, null);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
            g.setColor(Color.WHITE);
            g.drawString("The vault door at play zoom", ix + 6, iy + inset.getHeight() + 26);
        }

        int sx(float[] p) { return Math.round(camera.cellToScreenX(p[0])); }
        int sy(float[] p) { return Math.round(H - camera.cellToScreenY(p[1])); }

        /** The power's path: hall to junction to door, a dashed cable. */
        void power(float[]... path) {
            g.setColor(POWER);
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
                    new float[]{16f, 12f}, 0f));
            for (int i = 1; i < path.length; i++) g.drawLine(sx(path[i - 1]), sy(path[i - 1]), sx(path[i]), sy(path[i]));
        }

        /** Two places held at the same moment. */
        void pair(float[] p, float[] q) {
            g.setColor(PAIR);
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
                    new float[]{4f, 10f}, 0f));
            g.drawLine(sx(p), sy(p), sx(q), sy(q));
            int mx = (sx(p) + sx(q)) / 2, my = (sy(p) + sy(q)) / 2;
            badges.add(() -> label(mx - 110, my - 18, "AT THE SAME TIME", 20, new Color(20, 90, 40, 220)));
        }

        /** A numbered step at a place, drawn above the lines. */
        void step(float[] at, int n, String what, String how) {
            int x = sx(at), y = sy(at);
            badges.add(() -> {
                g.setColor(new Color(0, 0, 0, 160));
                g.fillOval(x - 21, y - 21, 42, 42);
                g.setColor(STEP);
                g.setStroke(new BasicStroke(4f));
                g.drawOval(x - 19, y - 19, 38, 38);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
                FontMetrics m = g.getFontMetrics();
                g.setColor(Color.WHITE);
                String s = Integer.toString(n);
                g.drawString(s, x - m.stringWidth(s) / 2, y + 9);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
                int wide = g.getFontMetrics().stringWidth(what) + 20;
                int lx = x + 28 + wide > W - 8 ? x - 28 - wide : x + 28;
                label(lx, y - 30, what, 22, new Color(0, 0, 0, 190));
                label(lx, y + 6, how, 18, new Color(20, 40, 70, 200));
            });
        }

        /** What the door's prompt and lamp tell the player, in a panel along the bottom. */
        void panel(String head, String needs, String lamp) {
            badges.add(() -> {
                int x = 24, y = H - 150, w = 900, h = 126;
                g.setColor(new Color(10, 14, 20, 215));
                g.fillRoundRect(x, y, w, h, 16, 16);
                g.setColor(new Color(255, 255, 255, 60));
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(x, y, w, h, 16, 16);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
                g.setColor(new Color(150, 200, 255));
                g.drawString("What the player sees at the door", x + 18, y + 32);
                g.setColor(Color.WHITE);
                g.drawString(head, x + 18, y + 62);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
                g.drawString(needs, x + 18, y + 90);
                g.setColor(new Color(210, 210, 210));
                g.drawString(lamp, x + 18, y + 115);
            });
        }

        private void label(int x, int y, String text, int size, Color back) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
            FontMetrics m = g.getFontMetrics();
            g.setColor(back);
            g.fillRoundRect(x, y, m.stringWidth(text) + 20, size + 14, 10, 10);
            g.setColor(Color.WHITE);
            g.drawString(text, x + 10, y + size + 3);
        }

        BufferedImage done() {
            for (Runnable badge : badges) badge.run();
            g.dispose();
            return image;
        }
    }

    private static TargetProfile profile() {
        return new TargetProfile(0, 0, 0, 0, "", EnumSet.noneOf(EconomicFunction.class), SurfacePalette.ARID,
                SettlementLink.NONE, 0f, 0f, false, false, false, GroundDefences.NONE);
    }
}
