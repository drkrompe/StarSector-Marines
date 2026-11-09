package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective;
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
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Picture cards for two owner picks on the ruins works ({@code ruins-works.md}), through the look preset at 1920x1080
 * with roofs lifted. {@code ruins-court-power}: an empty site's nearest landing court, unpowered beside powered with
 * its lift marker (option a), and always a lift point (option b). {@code ruins-occupied-locks}: an occupied scattered
 * site's vault antechamber with the bunker door and the vault's door both open (a), the bunker open and the vault
 * locked (b), and both locked as now (c). The options are staged for the picture, not built: the lift ring and the
 * state labels are a review overlay the game never draws. Opt-in: {@code -Psnapshot=ruins-works-picks}.
 */
public final class RuinsWorksPicksSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    private static final float ZOOM = BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 0.75f;
    private static final float LIFT_RADIUS_CELLS = 6f;

    @Override public String id() { return "ruins-works-picks"; }

    @Override public String label() { return "Ruins works: picture cards for the court-power and occupied-locks picks"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        courtCards(context, Long.getLong("snapshot.ruinsWorksPicks.courtSeed", 31L), out);
        lockCards(context, Long.getLong("snapshot.ruinsWorksPicks.lockSeed", 31L), out);
        return out;
    }

    private static void courtCards(SnapshotContext context, long seed, List<SnapshotArtifact> out) throws Exception {
        try (BattleSimulation sim = site(seed, RuinsGrade.WIDESPREAD, RuinsOccupant.NOBODY)) {
            RuinsVaultObjective vault = vault(sim);
            RuinsVaultObjective.LiftPoint court = null;
            for (RuinsVaultObjective.LiftPoint point : vault.liftPoints()) {
                if (point.kind() == RuinsVaultObjective.LiftPoint.Kind.COURT
                        && (court == null || point.route().length < court.route().length)) {
                    court = point;
                }
            }
            if (court == null) throw new IllegalStateException("the widespread site has no landing court");
            int[] route = court.route();
            float cx = route[route.length - 2] + .5f, cy = route[route.length - 1] + .5f;

            BufferedImage half = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = half.createGraphics();
            g.drawImage(lifted(context, sim, W / 2, H, cx, cy, ZOOM / 2f, false,
                    "Unpowered court: no lift point"), 0, 0, null);
            g.drawImage(lifted(context, sim, W / 2, H, cx, cy, ZOOM / 2f, true,
                    "Powered court: lift point"), W / 2, 0, null);
            g.setColor(Color.BLACK);
            g.fillRect(W / 2 - 2, 0, 4, H);
            g.dispose();
            out.add(new SnapshotArtifact("ruins-court-power/a-unpowered-court-no-lift.png", half));
            out.add(new SnapshotArtifact("ruins-court-power/b-court-always-a-lift.png",
                    lifted(context, sim, W, H, cx, cy, ZOOM, true, "Court: lift point, powered or not")));
        }
    }

    private static void lockCards(SnapshotContext context, long seed, List<SnapshotArtifact> out) throws Exception {
        try (BattleSimulation sim = site(seed, RuinsGrade.SCATTERED, RuinsOccupant.SCAVENGERS)) {
            RuinsWorks works = sim.getRuinsWorks();
            SealDoor seal = null;
            List<SealDoor> bunker = new ArrayList<>();
            for (SealDoor door : works.doors()) {
                if (door.id().equals(RuinsWorks.VAULT_SEAL)) seal = door;
                if (door.id().startsWith("bunker-")) bunker.add(door);
            }
            if (seal == null) throw new IllegalStateException("the site's works have no vault seal");
            float cx = seal.middle()[0] + .5f, cy = seal.middle()[1] + .5f;
            BattleCamera camera = BattleReviewFrameRenderer.look(context.modRoot(), W, H, cx, cy, ZOOM).withoutRoofs()
                    .cameraOf(sim);
            List<float[]> spots = new ArrayList<>();
            spots.add(new float[]{seal.middle()[0], seal.middle()[1]});
            for (SealDoor door : bunker) spots.add(new float[]{door.middle()[0], door.middle()[1]});
            List<String> names = new ArrayList<>();
            names.add("vault door");
            for (int i = 0; i < bunker.size(); i++) names.add("bunker door");
            BufferedImage locked = labelled(tagged(frame(context, sim, W, H, cx, cy, ZOOM), camera, spots, names),
                    "Bunker door locked, vault door locked (hardened)");
            // (b): only the bunker's door stands open, as if its lock were lifted; the vault's stays shut.
            for (SealDoor door : bunker) {
                for (int[] c : door.cells()) sim.mapEditor().openPassage(c[0], c[1], door.likeX(), door.likeY());
            }
            BufferedImage vaultOnly = labelled(tagged(frame(context, sim, W, H, cx, cy, ZOOM), camera, spots, names),
                    "Bunker door open, vault door locked (hardened)");
            works.graph().restart();
            sim.advance(BattleSimulation.TICK_DT);
            BufferedImage open = labelled(tagged(frame(context, sim, W, H, cx, cy, ZOOM), camera, spots, names),
                    "Bunker door open, vault door open");
            out.add(new SnapshotArtifact("ruins-occupied-locks/a-all-open-stores-sealed.png", open));
            out.add(new SnapshotArtifact("ruins-occupied-locks/b-vault-locked-gates-open.png", vaultOnly));
            out.add(new SnapshotArtifact("ruins-occupied-locks/c-all-locked-as-now.png", locked));
        }
    }

    private static BattleSimulation site(long seed, RuinsGrade grade, RuinsOccupant occupant) {
        BattleSimulation sim = BattleSetup.createRuins(seed, BattleSetup.defaultManifest(), false,
                OperationTier.FIRST_CONTRACT, RiskLevel.MEDIUM, profile(), FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                grade, occupant);
        for (int t = 0; t < Math.round(5f / BattleSimulation.TICK_DT); t++) sim.advance(BattleSimulation.TICK_DT);
        return sim;
    }

    private static RuinsVaultObjective vault(BattleSimulation sim) {
        for (Objective objective : sim.getObjectives()) {
            if (objective instanceof RuinsVaultObjective found) return found;
        }
        throw new IllegalStateException("a ruins battle without its vault objective");
    }

    private static BufferedImage frame(SnapshotContext context, BattleSimulation sim, int w, int h, float cx, float cy,
                                       float zoom) throws Exception {
        return BattleReviewFrameRenderer.look(context.modRoot(), w, h, cx, cy, zoom).withoutRoofs().render(sim, "");
    }

    /** A frame on the court with its lift ring: live when it is a lift point, struck through when it is not. */
    private static BufferedImage lifted(SnapshotContext context, BattleSimulation sim, int w, int h, float cx, float cy,
                                        float zoom, boolean live, String label) throws Exception {
        BufferedImage frame = frame(context, sim, w, h, cx, cy, zoom);
        BattleCamera camera = BattleReviewFrameRenderer.look(context.modRoot(), w, h, cx, cy, zoom).withoutRoofs()
                .cameraOf(sim);
        BufferedImage out = copy(frame);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float px = camera.cellToScreenX(cx), py = h - camera.cellToScreenY(cy);
        float r = LIFT_RADIUS_CELLS * camera.cellPxSize();
        int x0 = Math.round(px - r), y0 = Math.round(py - r), d = Math.round(2 * r);
        if (live) {
            g.setColor(new Color(90, 220, 120, 60));
            g.fillOval(x0, y0, d, d);
            g.setColor(new Color(90, 230, 120));
            g.setStroke(new BasicStroke(5f));
        } else {
            g.setColor(new Color(150, 150, 150));
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    new float[]{18f, 14f}, 0f));
        }
        g.drawOval(x0, y0, d, d);
        if (!live) {
            g.setColor(new Color(220, 60, 60));
            g.setStroke(new BasicStroke(6f));
            float k = r * 0.5f;
            g.drawLine(Math.round(px - k), Math.round(py - k), Math.round(px + k), Math.round(py + k));
            g.drawLine(Math.round(px - k), Math.round(py + k), Math.round(px + k), Math.round(py - k));
        }
        g.dispose();
        return labelled(out, label);
    }

    /** The frame with a ring and a name on each door cell in {@code spots}. */
    private static BufferedImage tagged(BufferedImage frame, BattleCamera camera, List<float[]> spots,
                                        List<String> names) {
        BufferedImage out = copy(frame);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        float r = 1.6f * camera.cellPxSize();
        for (int i = 0; i < spots.size(); i++) {
            float px = camera.cellToScreenX(spots.get(i)[0] + .5f);
            float py = frame.getHeight() - camera.cellToScreenY(spots.get(i)[1] + .5f);
            g.setColor(new Color(255, 210, 60));
            g.setStroke(new BasicStroke(4f));
            g.drawOval(Math.round(px - r), Math.round(py - r), Math.round(2 * r), Math.round(2 * r));
            String name = names.get(i);
            int tw = g.getFontMetrics().stringWidth(name);
            int tx = Math.round(px - tw / 2f), ty = Math.round(py - r - 12);
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRoundRect(tx - 8, ty - 22, tw + 16, 30, 10, 10);
            g.setColor(Color.WHITE);
            g.drawString(name, tx, ty);
        }
        g.dispose();
        return out;
    }

    /** The frame with a short state label in its top-left corner. */
    private static BufferedImage labelled(BufferedImage frame, String label) {
        BufferedImage out = copy(frame);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        int tw = g.getFontMetrics().stringWidth(label);
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRoundRect(24, 24, tw + 40, 56, 14, 14);
        g.setColor(Color.WHITE);
        g.drawString(label, 44, 63);
        g.dispose();
        return out;
    }

    private static BufferedImage copy(BufferedImage frame) {
        BufferedImage out = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(frame, 0, 0, null);
        g.dispose();
        return out;
    }

    private static TargetProfile profile() {
        return new TargetProfile(0, 0, 0, 0, "", EnumSet.noneOf(EconomicFunction.class), SurfacePalette.ARID,
                SettlementLink.NONE, 0f, 0f, false, false, false, GroundDefences.NONE);
    }
}
