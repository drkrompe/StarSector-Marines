package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.allied.AlliedCommand;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
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
import java.util.Random;

/**
 * The owner's design questions on how the Cordon's two commanders use the allies
 * ({@code cordon-allied-command.md}), each option staged as a still on a real Cordon map. Nothing is played:
 * each picture places the company's squads (outlined in white) and the allies' machines (outlined in the
 * allied colour) where the option's rule would have them, with arrows for who was sent where.
 *
 * <ul>
 *   <li>{@code answer-*}: a rural Line, redoubt 2 calling with a pack at its wall. {@code whole}: every squad
 *   goes (today's commander). {@code detachment}: two squads go, the reserve holds. {@code allies-first}: the
 *   sector's armour and a carrier answer, the company waits.</li>
 *   <li>{@code hammer-*}: an urban Line, the whole line. Where the company waits: {@code one} hammer behind the
 *   middle, {@code two} behind redoubts 1-2 and 3-4, or {@code spread} behind every redoubt.</li>
 *   <li>{@code escort-*}: a rural Line, beat L2's ammunition truck on its supply road. {@code company}: two
 *   squads walk with it (today). {@code armour}: two hulls of the sector's armour. {@code both}.</li>
 * </ul>
 *
 * <p>{@code -Dsnapshot.cordonCommand.seed=3} picks the map (rural pooled, urban rolled). Opt-in: a design
 * question, not part of {@code all}.
 */
public final class CordonCommandLookSuite implements SnapshotSuite {

    static final int W = 1920, H = 1080;
    /** Cells behind the line the company's reserve stands ({@code CordonSetup.RESERVE_DEPTH}). */
    static final int RESERVE_DEPTH = 24;
    /** Cells of margin round what a frame has to show. */
    static final float MARGIN = 14f;
    /** Squads in the company: the evidence fixture's six boats of six. */
    static final int SQUADS = 6;
    private static final String ISSUER = "cordon-command-look";

    @Override public String id() { return "cordon-command-look"; }

    @Override public String label() { return "Cordon command: answering a call, the urban hammer, escorting the ammo truck"; }

    @Override public boolean inAll() { return false; }

    /** What a frame must show and the marks that name it. */
    private static final class Stage {
        final ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;

        void include(float x, float y) {
            x0 = Math.min(x0, x);
            y0 = Math.min(y0, y);
            x1 = Math.max(x1, x);
            y1 = Math.max(y1, y);
        }
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        long seed = Long.parseLong(System.getProperty("snapshot.cordonCommand.seed", "3").trim());
        List<SnapshotArtifact> out = new ArrayList<>();
        for (String option : List.of("whole", "detachment", "allies-first")) {
            out.add(answer(context, seed, option));
        }
        for (String option : List.of("one", "two", "spread")) {
            out.add(hammer(context, seed, option));
        }
        for (String option : List.of("company", "armour", "both")) {
            out.add(escort(context, seed, option));
        }
        return out;
    }

    // ---------------------------------------------------------------- answering a call

    private SnapshotArtifact answer(SnapshotContext context, long seed, String option) throws Exception {
        CordonSetup.CordonMap cordon = CordonSetup.map(CordonVariant.LINE, SurfacePalette.VERDANT, seed);
        int f = CordonFront.frontward(cordon.plan());
        try (BattleSimulation sim = CordonSetup.create(cordon, CordonVariant.LINE, List.of(),
                CordonSequence.Timetable.STANDARD)) {
            clearArmour(sim);
            CordonArmour armour = sim.getCordon().armour();
            CordonRedoubt r = sim.getCordon().redoubts().get(1);
            r.setState(CordonRedoubt.State.CALLING);
            Stage stage = new Stage();
            CordonReviewAnnotations.add(stage.marks, sim);
            Random rng = new Random(seed);
            AlliedCommand allies = new AlliedCommand(ISSUER);

            // The pack at the wall that makes it call.
            int frontY = f > 0 ? Math.max(r.top(), r.bottom()) : Math.min(r.top(), r.bottom());
            List<Long> pack = swarm(sim, UnitType.SWARM_RUNNER, 14, r.centreX(), frontY + f * 3);
            pack.addAll(swarm(sim, UnitType.SWARM_BRUTE, 3, r.centreX() + 6, frontY + f * 5));
            outline(stage, sim, pack, "SWARM PACK AT THE WALL", ReviewStyle.KEEP);

            // The sector's own armour on its flanks, in every option: the allies are already there.
            for (int rank = 0; rank < 2; rank++) {
                int[] post = armour.flank(r, rank);
                long hull = hull(sim, allies, rank == 0 ? VehicleType.MILITIA_TECHNICAL : VehicleType.SCOUT_CAR,
                        post[0], post[1], f > 0 ? 0f : 180f);
                if (hull != 0L) outline(stage, sim, List.of(hull), "SECTOR ARMOUR", ReviewStyle.ALLY);
            }
            int[] behind = armour.behind(r);
            if (option.equals("allies-first")) {
                long carrier = hull(sim, allies, VehicleType.HEAVY_APC, behind[0] + 10, behind[1] - f * 3, f > 0 ? 0f : 180f);
                if (carrier != 0L) outline(stage, sim, List.of(carrier), "CARRIER · a squad up from the base", ReviewStyle.ALLY);
            }

            int reserveX = CordonSetup.WIDTH / 2;
            int reserveY = Math.round(CordonFront.lineY(cordon.plan()) - f * RESERVE_DEPTH);
            int sent = switch (option) {
                case "whole" -> SQUADS;
                case "detachment" -> 2;
                default -> 0;
            };
            for (int i = 0; i < SQUADS; i++) {
                int x, y;
                String name;
                if (i < sent) {
                    // Behind the rear wall, spread across it: where a relief stands to fight through the gate.
                    x = behind[0] + (i - (sent - 1) / 2) * 7 - (sent % 2 == 0 ? 3 : 0);
                    y = behind[1] - f * (i % 2 == 0 ? 0 : 5);
                    name = "SQUAD " + (i + 1) + " · relief";
                } else {
                    x = reserveX + (i - (SQUADS - 1) / 2) * CordonCommand.RESERVE_SPACING;
                    y = reserveY;
                    name = "SQUAD " + (i + 1);
                }
                List<Long> squad = marines(sim, x, y, "Squad " + (i + 1), rng);
                outline(stage, sim, squad, name, ReviewStyle.APPROACH);
                if (i < sent) {
                    float[] at = centre(sim, squad);
                    if (at != null) {
                        int slot = reserveX + (i - (SQUADS - 1) / 2) * CordonCommand.RESERVE_SPACING;
                        stage.marks.arrow(slot, reserveY, at[0], at[1], "", ReviewStyle.APPROACH);
                    }
                }
            }
            float rx0 = reserveX - (SQUADS / 2f) * CordonCommand.RESERVE_SPACING - 3;
            float rx1 = reserveX + (SQUADS / 2f) * CordonCommand.RESERVE_SPACING + 3;
            String reserveWord = switch (option) {
                case "whole" -> "RESERVE · empty: all six went";
                case "detachment" -> "RESERVE · four squads hold";
                default -> "RESERVE · all six wait: the allies answer first";
            };
            stage.marks.box(Math.round(rx0), reserveY - 5, Math.round(rx1), reserveY + 5, reserveWord, ReviewStyle.NOTE);
            stage.include(rx0, reserveY - 5);
            stage.include(rx1, reserveY + 5);
            stage.include(r.left() - 8, Math.min(r.top(), r.bottom()));
            stage.include(r.right() + 8, frontY + f * 10);
            String caption = switch (option) {
                case "whole" -> "the whole company answers (today)";
                case "detachment" -> "a two-squad relief, the reserve holds";
                default -> "the allies answer, the company waits";
            };
            return frame(context, sim, stage, "answer-" + option,
                    "rural line, seed " + seed + ", redoubt 2 calling: " + caption);
        }
    }

    // ---------------------------------------------------------------- the urban hammer

    private SnapshotArtifact hammer(SnapshotContext context, long seed, String option) throws Exception {
        CordonSetup.CordonMap cordon = CordonSetup.rolled(CordonVariant.LINE, CordonPlan.Density.URBAN,
                SurfacePalette.VERDANT, seed);
        int f = CordonFront.frontward(cordon.plan());
        try (BattleSimulation sim = CordonSetup.create(cordon, CordonVariant.LINE, List.of(),
                CordonSequence.Timetable.STANDARD)) {
            clearArmour(sim);
            List<CordonRedoubt> line = sim.getCordon().redoubts();
            CordonArmour armour = sim.getCordon().armour();
            Stage stage = new Stage();
            CordonReviewAnnotations.add(stage.marks, sim);
            Random rng = new Random(seed);
            int row = Math.round(CordonFront.lineY(cordon.plan()) - f * RESERVE_DEPTH);
            // Each group: where it waits, how many squads, and the redoubts it answers.
            List<int[]> groups = new ArrayList<>();
            List<int[]> covers = new ArrayList<>();
            switch (option) {
                case "one" -> {
                    groups.add(new int[]{CordonSetup.WIDTH / 2, row, SQUADS});
                    covers.add(new int[]{0, 1, 2, 3});
                }
                case "two" -> {
                    groups.add(new int[]{(line.get(0).centreX() + line.get(1).centreX()) / 2, row, SQUADS / 2});
                    groups.add(new int[]{(line.get(2).centreX() + line.get(3).centreX()) / 2, row, SQUADS / 2});
                    covers.add(new int[]{0, 1});
                    covers.add(new int[]{2, 3});
                }
                default -> {
                    int[] counts = {1, 2, 2, 1};
                    for (int i = 0; i < line.size(); i++) {
                        int[] b = armour.behind(line.get(i));
                        groups.add(new int[]{b[0], b[1] - f * 4, counts[i]});
                        covers.add(new int[]{i});
                    }
                }
            }
            int n = 0;
            for (int g = 0; g < groups.size(); g++) {
                int[] at = groups.get(g);
                List<Long> members = new ArrayList<>();
                for (int k = 0; k < at[2]; k++) {
                    int x = at[0] + (k - (at[2] - 1) / 2) * CordonCommand.RESERVE_SPACING - (at[2] % 2 == 0 ? 4 : 0);
                    members.addAll(marines(sim, x, at[1], "Squad " + (++n), rng));
                }
                String word = groups.size() == 1 ? "HAMMER · all six squads"
                        : option.equals("two") ? "HAMMER · three squads" : (at[2] == 1 ? "ONE SQUAD" : "TWO SQUADS");
                outline(stage, sim, members, word, ReviewStyle.APPROACH);
                float[] mid = centre(sim, members);
                if (mid == null) continue;
                for (int ri : covers.get(g)) {
                    CordonRedoubt r = line.get(ri);
                    int[] b = armour.behind(r);
                    float d = (float) Math.hypot(b[0] - mid[0], b[1] - mid[1]);
                    if (d < 6f) continue;
                    stage.marks.arrow(mid[0], mid[1], b[0], b[1],
                            String.format(Locale.ROOT, "%.0f cells", d), ReviewStyle.APPROACH);
                }
            }
            stage.include(4, Math.min(row, CordonFront.lineY(cordon.plan())) - 30);
            stage.include(CordonSetup.WIDTH - 4, Math.max(row, CordonFront.lineY(cordon.plan())) + 30);
            String caption = switch (option) {
                case "one" -> "one hammer behind the middle";
                case "two" -> "two hammers, behind redoubts 1-2 and 3-4";
                default -> "spread: squads behind every redoubt";
            };
            return frame(context, sim, stage, "hammer-" + option,
                    "urban line, seed " + seed + ", where the company waits: " + caption);
        }
    }

    // ---------------------------------------------------------------- escorting the ammo truck

    private SnapshotArtifact escort(SnapshotContext context, long seed, String option) throws Exception {
        CordonSetup.CordonMap cordon = CordonSetup.map(CordonVariant.LINE, SurfacePalette.VERDANT, seed);
        int f = CordonFront.frontward(cordon.plan());
        try (BattleSimulation sim = CordonSetup.create(cordon, CordonVariant.LINE, List.of(),
                CordonSequence.Timetable.STANDARD)) {
            clearArmour(sim);
            Stage stage = new Stage();
            CordonReviewAnnotations.add(stage.marks, sim);
            Random rng = new Random(seed);
            AlliedCommand allies = new AlliedCommand(ISSUER);
            CordonRedoubt to = null;
            Laid road = null;
            for (CordonRedoubt r : sim.getCordon().redoubts()) {
                Laid l = cordon.map().layout.find(CordonPlan.SUPPLY_ROAD + r.number());
                if (l != null && l.isLaid() && l.gaps().size() >= 2 && !l.cells().isEmpty()) {
                    // The middle redoubts first: their roads are the long ones.
                    if (to == null || r.number() == 2) {
                        to = r;
                        road = l;
                    }
                }
            }
            if (road == null) throw new IllegalStateException("no supply road on seed " + seed);
            int[] a = road.gaps().get(0), b = road.gaps().get(1);
            int[] base = Math.hypot(a[0] - to.centreX(), a[1] - to.centreY())
                    > Math.hypot(b[0] - to.centreX(), b[1] - to.centreY()) ? a : b;
            int[] gate = base == a ? b : a;
            // The truck half way up, on the road cell nearest that point.
            float px = base[0] + (gate[0] - base[0]) * 0.55f, py = base[1] + (gate[1] - base[1]) * 0.55f;
            int[] onRoad = road.cells().get(0);
            for (int[] c : road.cells()) {
                if (Math.hypot(c[0] - px, c[1] - py) < Math.hypot(onRoad[0] - px, onRoad[1] - py)) onRoad = c;
            }
            float facing = (float) Math.toDegrees(Math.atan2(gate[1] - onRoad[1], gate[0] - onRoad[0])) - 90f;
            long truck = hull(sim, allies, VehicleType.SUPPLY_TRUCK, onRoad[0], onRoad[1], facing);
            if (truck != 0L) outline(stage, sim, List.of(truck), "AMMO TRUCK (L2) · to redoubt " + to.number(), ReviewStyle.ALLY);
            stage.include(base[0], base[1]);
            stage.include(onRoad[0], onRoad[1]);
            // Across the road and ahead along it.
            double len = Math.hypot(gate[0] - base[0], gate[1] - base[1]);
            float ux = (float) ((gate[0] - base[0]) / len), uy = (float) ((gate[1] - base[1]) / len);
            float sx = -uy, sy = ux;
            boolean hulls = !option.equals("company"), squads = !option.equals("armour");
            if (hulls) {
                VehicleType[] types = {VehicleType.MILITIA_TECHNICAL, VehicleType.SCOUT_CAR};
                for (int k = 0; k < 2; k++) {
                    int side = k == 0 ? 1 : -1;
                    long h = hull(sim, allies, types[k], Math.round(onRoad[0] + sx * 6 * side + ux * 7),
                            Math.round(onRoad[1] + sy * 6 * side + uy * 7), facing);
                    if (h != 0L) outline(stage, sim, List.of(h), "ESCORT · redoubt " + to.number() + "'s armour", ReviewStyle.ALLY);
                }
            }
            if (squads) {
                int reserveX = CordonSetup.WIDTH / 2;
                int reserveY = Math.round(CordonFront.lineY(cordon.plan()) - f * RESERVE_DEPTH);
                for (int k = 0; k < 2; k++) {
                    int side = k == 0 ? -1 : 1;
                    float off = hulls ? 11f : 5f;
                    List<Long> squad = marines(sim, Math.round(onRoad[0] + sx * off * side - ux * 3),
                            Math.round(onRoad[1] + sy * off * side - uy * 3), "Escort " + (k + 1), rng);
                    outline(stage, sim, squad, "SQUAD " + (k + 1) + " · off the line", ReviewStyle.APPROACH);
                    float[] at = centre(sim, squad);
                    if (at != null && Math.hypot(at[0] - reserveX, at[1] - reserveY) < 90) {
                        stage.marks.arrow(reserveX, reserveY, at[0], at[1], "from the reserve", ReviewStyle.APPROACH);
                        stage.include(reserveX, reserveY);
                    }
                }
            }
            stage.include(onRoad[0] - 20, onRoad[1] - 14);
            stage.include(onRoad[0] + 20, onRoad[1] + 14);
            String caption = switch (option) {
                case "company" -> "two squads walk with it (today)";
                case "armour" -> "the sector's armour escorts it";
                default -> "armour and two squads";
            };
            return frame(context, sim, stage, "escort-" + option,
                    "rural line, seed " + seed + ", L2's ammo truck on supply road " + to.number() + ": " + caption);
        }
    }

    // ---------------------------------------------------------------- shared

    private static SnapshotArtifact frame(SnapshotContext context, BattleSimulation sim, Stage stage, String name,
                                          String caption) throws Exception {
        float cx = (stage.x0 + stage.x1) / 2f, cy = (stage.y0 + stage.y1) / 2f;
        float across = Math.max(stage.x1 - stage.x0 + 2 * MARGIN, (stage.y1 - stage.y0 + 2 * MARGIN) * W / (float) H);
        BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(context.modRoot(), W, H, cx, cy, across)
                .withUnitShadows();
        System.out.printf("    [cordon-command-look/%s] framed %.0f cells across at %.0f,%.0f%n", name, across, cx, cy);
        return new SnapshotArtifact(name + ".png", renderer.render(sim, caption, stage.marks.build()));
    }

    /** The default armour off the field, so a frame shows only what its option placed. */
    private static void clearArmour(BattleSimulation sim) {
        for (long hull : new ArrayList<>(sim.getCordon().armour().command().hulls())) {
            if (sim.getCordon().logistics() != null && sim.getCordon().logistics().carriers().contains(hull)) continue;
            sim.convoy().despawn(hull);
        }
    }

    /** An allied hull on drivable ground nearest the cell, or 0. */
    private static long hull(BattleSimulation sim, AlliedCommand allies, VehicleType type, int x, int y, float facing) {
        int[] cell = sim.getCordon().armour().drivable(sim, type, x, y);
        if (cell == null) {
            System.out.printf("    [cordon-command-look] no ground for a %s near %d,%d%n", type, x, y);
            return 0L;
        }
        return allies.deploy(sim, type, cell[0], cell[1], facing);
    }

    /** A squad of six marines stood up round the cell, holding there. Returns its members. */
    private static List<Long> marines(BattleSimulation sim, int x, int y, String name, Random rng) {
        List<int[]> cells = CordonGround.clusterNear(sim, x, y, SQUADS);
        if (cells.isEmpty()) return new ArrayList<>();
        int squad = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        MarineLoadout[] kit = InfantryLoadoutRolls.playerSquad(cells.size(), rng);
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < cells.size() && i < kit.length; i++) {
            EntitySpec spec = new EntitySpec(name + "-" + (i + 1), Faction.MARINE, UnitType.MARINE,
                    cells.get(i)[0], cells.get(i)[1]).squad(squad);
            kit[i].seedInto(spec);
            long id = sim.spawn(spec);
            if (sim.getSquad(squad).leaderId == 0L) sim.getSquad(squad).leaderId = id;
            members.add(id);
        }
        sim.getSquad(squad).originalSize = members.size();
        sim.assignSquadCommand(ObjectiveAssignment.defendArea(squad, cells.get(0)[0], cells.get(0)[1], 4),
                CommandAuthority.SCRIPTED, ISSUER, name + " holds");
        return members;
    }

    private static List<Long> swarm(BattleSimulation sim, UnitType type, int count, int x, int y) {
        List<int[]> cells = CordonGround.clusterNear(sim, x, y, count);
        int squad = sim.mintSquad(Faction.DEFENDER, type);
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            long id = sim.spawn(new EntitySpec("look " + type.name().toLowerCase(Locale.ROOT) + " " + i, Faction.DEFENDER,
                    type, cells.get(i)[0], cells.get(i)[1]).squad(squad));
            if (sim.getSquad(squad).leaderId == 0L) sim.getSquad(squad).leaderId = id;
            out.add(id);
        }
        sim.getSquad(squad).originalSize = out.size();
        return out;
    }

    /** An outline round the living members and a word on it; also widens the frame to take them in. */
    private static void outline(Stage stage, BattleSimulation sim, List<Long> ids, String label, ReviewStyle style) {
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        int n = 0;
        for (long id : ids) {
            float[] w = where(sim, id);
            if (w == null) continue;
            float pad = sim.convoy().isVehicle(id) ? 2.5f : 0f;
            x0 = Math.min(x0, w[0] - pad);
            y0 = Math.min(y0, w[1] - pad);
            x1 = Math.max(x1, w[0] + pad);
            y1 = Math.max(y1, w[1] + pad);
            n++;
        }
        if (n == 0) return;
        x0 -= 2f;
        y0 -= 2f;
        x1 += 1.5f;
        y1 += 1.5f;
        float xm = (x0 + x1) / 2f;
        stage.marks.polyline(List.of(new ReviewAnnotation.Point(x0, y1), new ReviewAnnotation.Point(x1, y1),
                new ReviewAnnotation.Point(x1, y0), new ReviewAnnotation.Point(xm, y0),
                new ReviewAnnotation.Point(x0, y0), new ReviewAnnotation.Point(x0, y1)), label, style);
        stage.include(x0, y0);
        stage.include(x1, y1);
    }

    private static float[] centre(BattleSimulation sim, List<Long> ids) {
        float sx = 0f, sy = 0f;
        int n = 0;
        for (long id : ids) {
            float[] w = where(sim, id);
            if (w == null) continue;
            sx += w[0];
            sy += w[1];
            n++;
        }
        return n == 0 ? null : new float[]{sx / n, sy / n};
    }

    private static float[] where(BattleSimulation sim, long id) {
        if (sim.convoy().isVehicle(id)) {
            GroundBody body = sim.convoy().body(id);
            return body == null || sim.convoy().structure(id) <= 0f ? null : new float[]{body.x, body.y};
        }
        if (!sim.world().isAlive(id)) return null;
        return new float[]{sim.world().x(id), sim.world().y(id)};
    }
}
