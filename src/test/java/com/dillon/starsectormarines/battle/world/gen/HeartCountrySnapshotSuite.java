package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.creep.CreepSnapshot;
import com.dillon.starsectormarines.battle.creep.CreepVegetation;
import com.dillon.starsectormarines.battle.evacuation.HeartBores;
import com.dillon.starsectormarines.battle.setup.BloomHeartSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.precinct.Landscape;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The heart's overgrown country as generated (`bloom-mission-arc.md`, the
 * heart): both forms at tick zero, bores open and their creep matured as weeks
 * old, on several seeds. Whole map, then play zoom (32 px a cell) on the
 * creep front ahead of the start line, a dead wood, the dark settlement, a
 * satellite bore, a mother bore, the heart 140 cells across and, where there is
 * one, the brook at a ford. Look evidence, 1920x1080.
 *
 * <p>{@code -Dsnapshot.heartCountry.seeds=101,202,303} picks the seeds and
 * {@code -Dsnapshot.heartCountry.forms=BROAD_FRONT,THE_BURN} the forms.
 */
public final class HeartCountrySnapshotSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    /** 1920 px over sixty cells: 32 px a cell, the play zoom. */
    private static final float PLAY_ZOOM_CELLS = 60f;

    @Override public String id() { return "heart-country"; }

    @Override public String label() { return "Heart country: the bloom's overgrown farmland, dead woods and mother bores"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        BattleReviewFrameRenderer whole = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H);
        BattleReviewFrameRenderer near = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H, 0f, 0f, 1f);
        List<SnapshotArtifact> out = new ArrayList<>();
        for (String formName : System.getProperty("snapshot.heartCountry.forms", "BROAD_FRONT,THE_BURN").split(",")) {
            Form form = Form.valueOf(formName.trim());
            String slug = form == Form.BROAD_FRONT ? "broad-front" : "the-burn";
            for (String token : System.getProperty("snapshot.heartCountry.seeds", "101,202,303").split(",")) {
                long seed = Long.parseLong(token.trim());
                BloomHeartSetup.Built built = BloomHeartSetup.country(form, seed);
                try (BattleSimulation sim = built.sim()) {
                    String tag = slug + "-seed" + seed + "-";
                    String head = slug + " seed " + seed + " | " + facts(sim);
                    out.add(new SnapshotArtifact(tag + "01-whole-map.png", whole.render(sim, head + " | whole map")));
                    int[] front = creepFront(sim, form, built.plan().landingPlace());
                    view(out, near, sim, tag + "02-creep-front.png", front[0], front[1], head + " | where the bloom begins, ahead of the start line");
                    int[] wood = deadWood(sim);
                    if (wood != null) view(out, near, sim, tag + "03-dead-wood.png", wood[0], wood[1], head + " | a dead wood");
                    Precinct settlement = HeartCountry.place(built.plan(), HeartCountry.SETTLEMENT);
                    view(out, near, sim, tag + "04-settlement.png", settlement.seedX(), settlement.seedY(), head + " | the dark settlement");
                    HeartBores.Site satellite = built.heart().satellites().get(0);
                    view(out, near, sim, tag + "05-satellite.png", satellite.x(), satellite.y(), head + " | a satellite bore at maw");
                    HeartBores.Site mother = built.heart().mothers().get(0);
                    view(out, near, sim, tag + "06-mother.png", mother.x(), mother.y(), head + " | a mother bore at the heart");
                    Precinct workings = HeartCountry.place(built.plan(), HeartCountry.WORKINGS);
                    near.refocus(workings.seedX() + 0.5f, workings.seedY() + 0.5f, 140f);
                    out.add(new SnapshotArtifact(tag + "07-heart.png", near.render(sim, head + " | the heart, 140 cells across")));
                    for (Landscape.Brook brook : built.plan().landscape().brooks()) {
                        int[] ford = ford(sim, brook);
                        if (ford != null) view(out, near, sim, tag + "08-brook-ford.png", ford[0], ford[1], head + " | the brook at a ford");
                    }
                }
            }
        }
        return out;
    }

    private static void view(List<SnapshotArtifact> out, BattleReviewFrameRenderer near, BattleSimulation sim,
                             String name, int x, int y, String caption) throws Exception {
        near.refocus(x + 0.5f, y + 0.5f, PLAY_ZOOM_CELLS);
        out.add(new SnapshotArtifact(name, near.render(sim, caption + ", play zoom")));
    }

    /** The first creeped cell walking from the landing toward the heart, or the landing itself. */
    private static int[] creepFront(BattleSimulation sim, Form form, Precinct landing) {
        float across = form.across(landing.seedX(), landing.seedY());
        for (float depth = form.depth(landing.seedX(), landing.seedY()); depth <= 1f; depth += 0.005f) {
            int[] cell = form.cellAt(depth, across);
            if (sim.creepField().isCreeped(cell[0], cell[1])) return cell;
        }
        return new int[]{landing.seedX(), landing.seedY()};
    }

    /** A ford's middle: the first sand cell on the brook's line with water either side along it, or {@code null}. */
    private static int[] ford(BattleSimulation sim, Landscape.Brook brook) {
        for (int i = 0; i + 1 < brook.path().size(); i++) {
            int[] a = brook.path().get(i), b = brook.path().get(i + 1);
            int steps = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
            for (int k = 0; k <= steps; k++) {
                int x = Math.round(a[0] + (b[0] - a[0]) * k / (float) Math.max(1, steps));
                int y = Math.round(a[1] + (b[1] - a[1]) * k / (float) Math.max(1, steps));
                if (y < 30) continue;
                if (sim.getTopology().getGroundKind(x, y) == CellTopology.GroundKind.SAND
                        && sim.getTopology().isWater(x, y - 6) && sim.getTopology().isWater(x, y + 6)) return new int[]{x, y};
            }
        }
        return null;
    }

    private static String facts(BattleSimulation sim) {
        CreepSnapshot creep = sim.creepField().snapshot();
        int cells = sim.getGrid().getWidth() * sim.getGrid().getHeight();
        return String.format(Locale.ROOT, "%d bores | creep %.0f%% of map | %s",
                sim.swarmBores().livingCount(), 100f * creep.coveredCells() / cells, sim.creepVegetation().summary());
    }

    /** The centre of the dead tree with the most dead trees round it, or {@code null}. */
    private static int[] deadWood(BattleSimulation sim) {
        CreepVegetation vegetation = sim.creepVegetation();
        List<Doodad> dead = new ArrayList<>();
        for (Doodad d : sim.getDoodads()) {
            if (CreepVegetation.canopy(d.definitionId()) == null) continue;
            if (vegetation.diedAt(d.cellX, d.cellY) >= 0) dead.add(d);
        }
        int[] best = null;
        int bestCount = -1;
        for (Doodad d : dead) {
            int count = 0;
            for (Doodad o : dead) if (Math.abs(o.cellX - d.cellX) <= 12 && Math.abs(o.cellY - d.cellY) <= 12) count++;
            if (count > bestCount) {
                bestCount = count;
                best = new int[]{d.cellX + d.footprintCellsX / 2, d.cellY + d.footprintCellsY / 2};
            }
        }
        return best;
    }
}
