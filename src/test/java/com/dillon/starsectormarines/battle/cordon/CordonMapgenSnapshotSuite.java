package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The Cordon's map as generated, both variants ({@code cordon-nouns.md}, The
 * map): the whole map, the line across it, and play zoom on a redoubt, the
 * front and the rear. Look evidence, 1920x1080.
 *
 * <p>{@code -Dsnapshot.cordonMapgen.seeds=7117,3,11} picks the seeds and
 * {@code -Dsnapshot.cordonMapgen.variants=line,road} the variants, and
 * {@code -Dsnapshot.cordonMapgen.density=town} how built-up the country is
 * ({@link CordonPlan.Density}; rural, the default, plays the pools), and
 * {@code -Dsnapshot.cordonMapgen.biome=arid} the world's ground (verdant by default). A seed the density's pool holds
 * for the biome is its pooled map, patch and all, as the campaign plays it. {@code -Dsnapshot.cordonMapgen.redoubts=1,2}
 * picks the redoubts framed at play zoom (redoubt 2 by default), each with its guns and wall teams named, and every
 * redoubt's garrison is printed: its guns' cells and each team's men. A Road map adds the town compound's gate,
 * truck yard and staging area at play zoom, marked and through the look preset.
 */
public final class CordonMapgenSnapshotSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    /** 1920 px over sixty cells: 32 px a cell, the play zoom. */
    private static final float PLAY_ZOOM_CELLS = 60f;

    @Override public String id() { return "cordon-mapgen"; }

    @Override public String label() { return "Cordon mapgen: the line, the front and the rear"; }

    /** What a whole-map frame names: the same marks a played Cordon frame draws ({@link CordonReviewAnnotations}). */
    public static ReviewAnnotations annotations(BattleSimulation sim) {
        return CordonReviewAnnotations.forBattle(sim);
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        BattleReviewFrameRenderer whole = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H);
        BattleReviewFrameRenderer near = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H, 0f, 0f, 1f);
        List<SnapshotArtifact> out = new ArrayList<>();
        CordonPlan.Density density = CordonPlan.Density.valueOf(
                System.getProperty("snapshot.cordonMapgen.density", "rural").trim().toUpperCase());
        String built = density == CordonPlan.Density.RURAL ? "" : density.name().toLowerCase() + "-";
        boolean look = Boolean.getBoolean("snapshot.cordonMapgen.look");
        SurfacePalette biome = SurfacePalette.valueOf(
                System.getProperty("snapshot.cordonMapgen.biome", "verdant").trim().toUpperCase());
        List<Integer> framed = new ArrayList<>();
        for (String r : System.getProperty("snapshot.cordonMapgen.redoubts", "2").split(",")) {
            framed.add(Integer.parseInt(r.trim()));
        }
        for (String v : System.getProperty("snapshot.cordonMapgen.variants", "line,road").split(",")) {
            CordonVariant variant = CordonVariant.valueOf(v.trim().toUpperCase());
            String name = variant.name().toLowerCase();
            for (String token : System.getProperty("snapshot.cordonMapgen.seeds", "7117,3,11").split(",")) {
                long seed = Long.parseLong(token.trim());
                String tag = built + name + (biome == SurfacePalette.VERDANT ? "" : "-" + biome.name().toLowerCase())
                        + "-seed" + seed + "-";
                CordonSetup.CordonMap cordon = CordonSetup.map(variant, density, biome, seed);
                PrecinctPlan plan = cordon.plan();
                try (BattleSimulation sim = CordonSetup.create(cordon, variant, List.of(),
                        CordonSequence.Timetable.STANDARD)) {
                    printGarrisons(sim, tag);
                    if (look) {
                        out.addAll(look(context, sim, tag, framed));
                        continue;
                    }
                    CordonSequence sequence = sim.getCordon();
                    CordonGateYard yard = sequence == null ? null : sequence.yard();
                    if (yard != null) {
                        // Between the gate and the yard's far side, so the road in, the yard and the staging area show.
                        near.refocus(yard.turnX() + 0.5f, yard.turnY() + 0.5f, PLAY_ZOOM_CELLS);
                        out.add(new SnapshotArtifact(tag + "09-play-zoom-town-yard.png",
                                near.render(sim, "the " + name + ", " + biome.name().toLowerCase() + " seed " + seed
                                        + ", the town's gate, truck yard and staging area, play zoom",
                                        CordonReviewAnnotations.townYard(sim))));
                        // The same ground through the look preset, nothing drawn over it: for the owner.
                        BattleReviewFrameRenderer plain = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W,
                                FRAME_H, yard.turnX() + 0.5f, yard.turnY() + 0.5f, PLAY_ZOOM_CELLS);
                        out.add(new SnapshotArtifact("look/" + tag + "town-yard-play-zoom.png", plain.render(sim, "")));
                    }
                    CordonTown town = sequence == null ? null : sequence.town();
                    if (town != null) {
                        // The whole compound: its rooms and their people, its teams, the yard and the staging area.
                        float across = Math.max(PLAY_ZOOM_CELLS, 1.25f * Math.max(town.right() - town.left() + 1,
                                (town.bottom() - town.top() + 1) * FRAME_W / (float) FRAME_H));
                        near.refocus(town.centreX() + 0.5f, town.centreY() + 0.5f, across);
                        out.add(new SnapshotArtifact(tag + "10-town-compound.png",
                                near.render(sim, "the " + name + ", " + biome.name().toLowerCase() + " seed " + seed
                                        + ", Ain Tafrant's compound at setup: rooms, teams, yard",
                                        CordonReviewAnnotations.townYard(sim))));
                        BattleReviewFrameRenderer plain = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W,
                                FRAME_H, town.centreX() + 0.5f, town.centreY() + 0.5f, across);
                        out.add(new SnapshotArtifact("look/" + tag + "town-compound.png", plain.render(sim, "")));
                    }
                    out.add(new SnapshotArtifact(tag + "01-whole-map.png",
                            whole.render(sim, "the " + name + (built.isEmpty() ? "" : ", " + density.name().toLowerCase()) + ", seed " + seed + ", whole map", annotations(sim))));
                    List<Precinct> redoubts = CordonPlan.redoubts(plan);
                    float lineY = 0f;
                    for (Precinct r : redoubts) lineY += r.seedY();
                    lineY /= Math.max(1, redoubts.size());
                    near.refocus(CordonSetup.WIDTH / 2f, lineY, 300f);
                    out.add(new SnapshotArtifact(tag + "02-the-line.png",
                            near.render(sim, "the " + name + ", seed " + seed + ", the line, 300 cells across",
                                    annotations(sim))));
                    if (cordon.corridor() != null) {
                        int[] cross = cordon.corridor().get(0);
                        for (int[] cell : cordon.corridor()) {
                            if (Math.abs(cell[1] - lineY) < Math.abs(cross[1] - lineY)) cross = cell;
                        }
                        near.refocus(cross[0] + 0.5f, cross[1] + 0.5f, 120f);
                        out.add(new SnapshotArtifact(tag + "07-corridor-at-the-line.png",
                                near.render(sim, "the road, seed " + seed + ", where the corridor crosses the line, 120 cells")));
                    }
                    for (int number : framed) {
                        CordonRedoubt r = redoubt(sim, number);
                        if (r == null) continue;
                        near.refocus(r.centreX() + 0.5f, r.centreY() + 0.5f, PLAY_ZOOM_CELLS);
                        out.add(new SnapshotArtifact(tag + "03-play-zoom-redoubt-" + number + ".png",
                                near.render(sim, "the " + name + ", seed " + seed + ", redoubt " + number
                                        + " (" + r.construction().name().toLowerCase() + "), play zoom",
                                        garrisonMarks(sim, r))));
                    }
                    // Each stretch of the picket line with the teams holding it.
                    for (Laid course : cordon.map().layout.pieces()) {
                        if (course.kind() != Laid.Kind.LINE || !course.isLaid() || course.cells().isEmpty()
                                || !course.name().startsWith(CordonPlan.PICKET_LINE)) continue;
                        float cx = 0f, cy = 0f;
                        for (int[] c : course.cells()) {
                            cx += c[0];
                            cy += c[1];
                        }
                        cx /= course.cells().size();
                        cy /= course.cells().size();
                        near.refocus(cx + 0.5f, cy + 0.5f, PICKET_CELLS);
                        out.add(new SnapshotArtifact(tag + "09-" + course.name() + ".png",
                                near.render(sim, "the " + name + ", seed " + seed + ", " + course.name()
                                        + " and its teams, " + (int) PICKET_CELLS + " cells", allyMarks(sim))));
                    }
                    for (Precinct p : plan.precincts()) {
                        if (p.name().equals("colony")) {
                            near.refocus(p.seedX() + 0.5f, p.seedY() + 0.5f, 120f);
                            out.add(new SnapshotArtifact(tag + "04-the-colony.png",
                                    near.render(sim, "the " + name + ", seed " + seed + ", the colony in front, 120 cells")));
                        } else if (p.name().equals(CivilDefenceCompound.PLACE)) {
                            near.refocus(p.seedX() + 0.5f, p.seedY() + 0.5f, PLAY_ZOOM_CELLS);
                            out.add(new SnapshotArtifact(tag + "05-play-zoom-compound.png",
                                    near.render(sim, "the " + name + ", seed " + seed + ", the town's compound, play zoom")));
                        } else if (p.name().equals(CordonPlan.BASE_PLACE)) {
                            // On the shed: the middle of its berths, where there are any.
                            float fx = p.seedX() + 0.5f, fy = p.seedY() + 0.5f;
                            if (!cordon.map().gantries.isEmpty()) {
                                fx = 0f;
                                fy = 0f;
                                for (Gantry g : cordon.map().gantries) {
                                    fx += g.worldCenterX();
                                    fy += g.worldCenterY();
                                }
                                fx /= cordon.map().gantries.size();
                                fy /= cordon.map().gantries.size();
                            }
                            near.refocus(fx, fy, PLAY_ZOOM_CELLS);
                            out.add(new SnapshotArtifact(tag + "08-play-zoom-logistics-base.png",
                                    near.render(sim, "the " + name + ", seed " + seed + ", the logistics base ("
                                            + cordon.map().gantries.size() + " berths), play zoom", annotations(sim))));
                        } else if (p.name().equals("rear-west")) {
                            near.refocus(p.seedX() + 0.5f, p.seedY() + 0.5f, 120f);
                            out.add(new SnapshotArtifact(tag + "06-rear-hamlet.png",
                                    near.render(sim, "the " + name + ", seed " + seed + ", a rear hamlet, 120 cells")));
                        }
                    }
                }
            }
        }
        return out;
    }

    /** Cells across the close look at a redoubt: the redoubt, the crest either side and the ground in front. */
    private static final float REDOUBT_CLOSE_CELLS = 90f;

    /**
     * The look preset's frames ({@code -Dsnapshot.cordonMapgen.look=true}): the whole map and redoubt 2 close and at
     * play zoom, on the real ground with nothing drawn over it, for the owner rather than for a verdict.
     */
    private static List<SnapshotArtifact> look(SnapshotContext context, BattleSimulation sim, String tag,
                                               List<Integer> framed) throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>();
        BattleReviewFrameRenderer renderer = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H,
                CordonSetup.WIDTH / 2f, CordonSetup.HEIGHT / 2f, CordonSetup.WIDTH);
        out.add(new SnapshotArtifact("look/" + tag + "01-whole-map.png", renderer.render(sim, "")));
        for (int number : framed) {
            CordonRedoubt r = redoubt(sim, number);
            if (r == null) continue;
            renderer.refocus(r.centreX() + 0.5f, r.centreY() + 0.5f, REDOUBT_CLOSE_CELLS);
            out.add(new SnapshotArtifact("look/" + tag + "02-redoubt-" + number + "-close.png", renderer.render(sim, "")));
            renderer.refocus(r.centreX() + 0.5f, r.centreY() + 0.5f, PLAY_ZOOM_CELLS);
            out.add(new SnapshotArtifact("look/" + tag + "03-redoubt-" + number + "-play-zoom.png",
                    renderer.render(sim, "")));
        }
        return out;
    }

    /** Cells across a frame on one stretch of the picket line. */
    private static final float PICKET_CELLS = 120f;

    /** Every allied foot soldier named by its squad's place: a redoubt's wall, the picket line or out in front. */
    private static ReviewAnnotations allyMarks(BattleSimulation sim) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        CordonReviewAnnotations.add(marks, sim);
        Map<Integer, List<ReviewAnnotation.Cell>> squads = new TreeMap<>();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.world().hasPosition(unit) || !sim.squad().hasSquad(unit)
                    || sim.identity().faction(unit) != Faction.ALLY) continue;
            squads.computeIfAbsent(sim.squad().squadId(unit), k -> new ArrayList<>())
                    .add(new ReviewAnnotation.Cell(sim.world().cellX(unit), sim.world().cellY(unit)));
        }
        for (Map.Entry<Integer, List<ReviewAnnotation.Cell>> e : squads.entrySet()) {
            marks.cells(e.getValue(), "", ReviewStyle.ALLY);
        }
        return marks.build();
    }

    private static CordonRedoubt redoubt(BattleSimulation sim, int number) {
        for (CordonRedoubt r : sim.getCordon().redoubts()) if (r.number() == number) return r;
        return null;
    }

    /** The cells a squad's living members stand on. */
    private static List<int[]> members(BattleSimulation sim, int squad) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.world().hasPosition(unit) || !sim.squad().hasSquad(unit) || sim.squad().squadId(unit) != squad) continue;
            out.add(new int[]{sim.world().cellX(unit), sim.world().cellY(unit)});
        }
        return out;
    }

    /** Each gun of a redoubt and each of its wall teams' men, named, for the play-zoom frame. */
    private static ReviewAnnotations garrisonMarks(BattleSimulation sim, CordonRedoubt r) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        CordonReviewAnnotations.add(marks, sim);
        int g = 0;
        for (long gun : r.guns()) {
            if (!sim.world().hasPosition(gun)) continue;
            marks.label(sim.world().cellX(gun) + 0.5f, sim.world().cellY(gun) + 1.5f, "gun " + (++g), ReviewStyle.KEEP);
        }
        for (int t = 0; t < r.teams().size(); t++) {
            List<ReviewAnnotation.Cell> cells = new ArrayList<>();
            for (int[] c : members(sim, r.teams().get(t))) cells.add(new ReviewAnnotation.Cell(c[0], c[1]));
            if (!cells.isEmpty()) marks.cells(cells, "team " + (t + 1), ReviewStyle.ALLY);
        }
        return marks.build();
    }

    /** Prints every redoubt's garrison: its construction and bounds, its guns' cells, and each team's men. */
    private static void printGarrisons(BattleSimulation sim, String tag) {
        for (CordonRedoubt r : sim.getCordon().redoubts()) {
            StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "%sredoubt %d %s [%d,%d..%d,%d] guns",
                    tag, r.number(), r.construction().name().toLowerCase(), r.left(), r.top(), r.right(), r.bottom()));
            for (long gun : r.guns()) {
                if (sim.world().hasPosition(gun)) {
                    line.append(String.format(Locale.ROOT, " (%d,%d)", sim.world().cellX(gun), sim.world().cellY(gun)));
                }
            }
            for (int t = 0; t < r.teams().size(); t++) {
                line.append(String.format(Locale.ROOT, "; team %d", t + 1));
                for (int[] c : members(sim, r.teams().get(t))) line.append(String.format(Locale.ROOT, " %d,%d", c[0], c[1]));
            }
            System.out.println(line);
        }
        if (sim.getCordon().outerPicket() != null) {
            System.out.println(tag + "pickets: " + sim.getCordon().outerPicket().readings());
        }
    }
}
