package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.MedicMarkerRenderSystem;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The squad medic's mark and its energy pool on a real Raid map at play zoom
 * ({@code squad-medic.md}): one medic with a partly drained pool, one below the
 * start gate. Each medic also gets a magnified inset, cut from the same frame,
 * because at play zoom the mark is about eleven pixels across.
 */
public final class MedicPoolSnapshotSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920;
    private static final int FRAME_H = 1080;
    /** 1920 px over sixty cells: 32 px a cell, the play zoom. */
    private static final float PLAY_ZOOM_CELLS = 60f;
    private static final long SEED = 15311L;
    private static final int MAX_LANDING_TICKS = 3600;
    /** Long enough after the drop for the squads to have walked off the landing pads. */
    private static final int LANDED_TICKS = 1200;
    /** The partly drained medic: over the start gate. */
    private static final float DRAINED_SHARE = 0.55f;
    /** The spent medic: under the start gate. */
    private static final float SPENT_SHARE = 0.12f;
    private static final int INSET_SRC = 64;
    private static final int INSET_ZOOM = 5;

    @Override public String id() { return "medic-pool"; }

    @Override public String label() { return "Squad medic: the mark and its energy pool at play zoom"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        DiskRegistries.installMapGeneration(root);
        TileRegistryTestInstaller.install(root);
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = BattleSetup.createRaid(SEED)) {
            List<Squad> squads = landedMarineSquads(sim);
            if (squads.size() < 2) throw new IllegalStateException("fewer than two marine squads landed");
            for (Squad squad : squads) squad.medic = new SquadMedic(SquadMedicine.forRank(1));
            for (int i = 0; i < 3; i++) sim.advance(BattleSimulation.TICK_DT);
            Squad drained = squads.get(0);
            Squad spent = nearestOther(sim, squads, drained);
            setPool(drained.medic, DRAINED_SHARE);
            setPool(spent.medic, SPENT_SHARE);
            long a = sim.resolveUnit(drained.medic.medicId());
            long b = sim.resolveUnit(spent.medic.medicId());
            float ax = sim.world().renderX(a), ay = sim.world().renderY(a);
            float bx = sim.world().renderX(b), by = sim.world().renderY(b);
            float fx = (ax + bx) / 2f, fy = (ay + by) / 2f;
            BattleReviewFrameRenderer near = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H,
                    fx, fy, PLAY_ZOOM_CELLS).withUnitShadows().withRoofs();
            MedicMarkerRenderSystem.PoolStyle before = MedicMarkerRenderSystem.poolStyle;
            try {
                int n = 1;
                for (MedicMarkerRenderSystem.PoolStyle style : MedicMarkerRenderSystem.PoolStyle.values()) {
                    MedicMarkerRenderSystem.poolStyle = style;
                    String name = style.name().toLowerCase(Locale.ROOT);
                    BufferedImage frame = near.render(sim, String.format(Locale.ROOT,
                            "medic pool: %s  |  Raid seed %d, play zoom (32 px/cell)  |  left inset %.0f/%.0f energy,"
                                    + " right inset %.1f/%.0f (below start gate %.1f)",
                            name, SEED, drained.medic.energy(), drained.medic.medicine.energyMax(),
                            spent.medic.energy(), spent.medic.medicine.energyMax(),
                            spent.medic.medicine.minStartEnergy()));
                    int[] pa = screen(sim, fx, fy, ax, ay);
                    int[] pb = screen(sim, fx, fy, bx, by);
                    BufferedImage copy = copy(frame);
                    inset(frame, copy, pa, FRAME_W - 2 * (INSET_SRC * INSET_ZOOM + 24), "pool 55%: can treat");
                    inset(frame, copy, pb, FRAME_W - (INSET_SRC * INSET_ZOOM + 24), "pool 12%: below start gate");
                    out.add(new SnapshotArtifact(String.format(Locale.ROOT, "%02d-%s.png", n++, name), frame));
                }
            } finally {
                MedicMarkerRenderSystem.poolStyle = before;
            }
        }
        return out;
    }

    /** Steps the battle until two marine squads stand on the map, and returns them. */
    private static List<Squad> landedMarineSquads(BattleSimulation sim) {
        for (int t = 0; t < MAX_LANDING_TICKS; t++) {
            List<Squad> found = new ArrayList<>();
            for (Squad squad : sim.getSquads()) {
                if (squad.faction == Faction.MARINE && squad.aliveMembers >= 4) found.add(squad);
            }
            if (found.size() >= 2 && t > LANDED_TICKS) return found;
            sim.advance(BattleSimulation.TICK_DT);
        }
        List<Squad> found = new ArrayList<>();
        for (Squad squad : sim.getSquads()) if (squad.faction == Faction.MARINE && squad.aliveMembers > 0) found.add(squad);
        return found;
    }

    private static Squad nearestOther(BattleSimulation sim, List<Squad> squads, Squad from) {
        long a = sim.resolveUnit(from.medic.medicId());
        Squad best = null;
        double bestD = Double.MAX_VALUE;
        for (Squad squad : squads) {
            if (squad == from || squad.medic == null) continue;
            long b = sim.resolveUnit(squad.medic.medicId());
            if (b == 0L) continue;
            double d = Math.hypot(sim.world().x(a) - sim.world().x(b), sim.world().y(a) - sim.world().y(b));
            if (d < bestD) {
                bestD = d;
                best = squad;
            }
        }
        return best;
    }

    private static void setPool(SquadMedic medic, float share) {
        medic.energy = medic.medicine.energyMax() * share;
    }

    /**
     * Where cell {@code (x, y)} lands in the review frame: the renderer's own close-view fit, below its caption
     * band, with image y running down.
     */
    private static int[] screen(BattleSimulation sim, float fx, float fy, float x, float y) {
        int header = BattleReviewFrameRenderer.HEADER_HEIGHT;
        BattleCamera camera = new BattleCamera(sim.getGrid().getWidth(), sim.getGrid().getHeight());
        camera.setViewport(0f, 0f, FRAME_W, FRAME_H - header, (float) Math.floor(FRAME_W / PLAY_ZOOM_CELLS));
        camera.centerOn(fx, fy);
        return new int[] {Math.round(camera.cellToScreenX(x)), Math.round(FRAME_H - camera.cellToScreenY(y))};
    }

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage c = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = c.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return c;
    }

    private static void inset(BufferedImage frame, BufferedImage source, int[] at, int left, String label) {
        int sx = Math.max(0, Math.min(FRAME_W - INSET_SRC, at[0] - INSET_SRC / 2));
        int sy = Math.max(0, Math.min(FRAME_H - INSET_SRC, at[1] - INSET_SRC / 2));
        int size = INSET_SRC * INSET_ZOOM;
        int top = FRAME_H - size - 24;
        Graphics2D g = frame.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.drawImage(source.getSubimage(sx, sy, INSET_SRC, INSET_SRC), left, top, size, size, null);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f));
        g.drawRect(left, top, size, size);
        g.drawRect(sx, sy, INSET_SRC, INSET_SRC);
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(left, top - 22, size, 22);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(label + "  (x" + INSET_ZOOM + ")", left + 6, top - 6);
        g.dispose();
    }
}
