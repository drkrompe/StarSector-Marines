package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.allied.AlliedCommand;
import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.mech.FactionMechLoadouts;
import com.dillon.starsectormarines.battle.mech.MechSpawnPlacement;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The urban Cordon's allied backbone as the owner would see it ({@code cordon-allied-forces.md}): three
 * options staged on a real urban Cordon map in front of redoubt 2, each against the same swarm pack.
 * {@code mechs}: a pair of allied Hounds in the street mouth the pack comes through, a rifle team behind them.
 * {@code infantry}: two rifle teams along the house fronts either side of that mouth and a mortar team in
 * the redoubt's yard. {@code boulevard}: a technical and a tank on the lateral street outside the redoubt's
 * side walls, with a rifle team in the mouth. The sector's default armour is taken off the field first, so each frame shows only its option.
 *
 * <p>The mouth is found, not guessed: a dry run plays the same pack with no option on the field and takes
 * where it crosses a line eight cells in front of the redoubt. Every unit an option places is boxed and
 * named ({@link ReviewStyle#ALLY}). Stills every {@link #STILL_EVERY} ticks, framed on the option and the
 * mouth; {@code <option>-pick.png} is the still whose last few ticks the option's own units fired most in,
 * with all of them alive and the pack within {@link #CONTACT_CELLS} of them. {@code -Dsnapshot.cordonAllies.seed=3} picks the map. Opt-in: a design question, not part of
 * {@code all}.
 */
public final class CordonAlliesLookSuite implements SnapshotSuite {

    static final int W = 1920, H = 1080;
    static final int TICKS = 600, STILL_EVERY = 30;
    /** Cells in front of redoubt 2's front wall the pack starts. */
    static final int PACK_OUT = 26;
    /** Cells in front of the front wall at which the dry run reads where the pack crosses. */
    static final int MOUTH_OUT = 8;
    /** The narrowest framing: a Hound reads as a mech at this many cells across. */
    static final float FRAME_CELLS = 42f;
    /** Ticks before a still whose rounds count toward choosing it: their tracers are what it shows. */
    static final int RECENT_TICKS = 8;
    /** Cells from a team's middle beyond which a straggler is left out of its outline. */
    static final float COHESION = 4f;
    /** A still is picked only while the pack is within this many cells of the option. */
    static final float CONTACT_CELLS = 9f;
    private static final String ISSUER = "cordon-allies-look";

    @Override public String id() { return "cordon-allies-look"; }

    @Override public String label() { return "Cordon urban allies: mech lance, infantry and mortars, armour on the boulevard"; }

    @Override public boolean inAll() { return false; }

    /** A thing the option placed: its ids (a team's members, or one machine) and the words its box carries. */
    private record Placed(String label, List<Long> ids) { }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        long seed = Long.parseLong(System.getProperty("snapshot.cordonAllies.seed", "3").trim());
        int[] mouth = mouth(seed);
        List<SnapshotArtifact> out = new ArrayList<>();
        for (String option : List.of("mechs", "infantry", "boulevard")) {
            CordonSetup.CordonMap cordon = CordonSetup.rolled(CordonVariant.LINE, CordonPlan.Density.URBAN,
                    SurfacePalette.VERDANT, seed);
            int f = CordonFront.frontward(cordon.plan());
            try (BattleSimulation sim = CordonSetup.create(cordon, CordonVariant.LINE, List.of(),
                    CordonSequence.Timetable.STANDARD)) {
                CordonRedoubt r = sim.getCordon().redoubts().get(1);
                int frontY = frontY(r, f), rearY = f > 0 ? Math.min(r.top(), r.bottom()) : Math.max(r.top(), r.bottom());
                clearSector(sim, r);
                int mx = mouth[0], my = mouth[1];
                Random rng = new Random(seed);
                AlliedCommand command = new AlliedCommand(ISSUER);
                List<Placed> placed = new ArrayList<>();
                float backmost = my;
                switch (option) {
                    case "mechs" -> {
                        long west = mech(sim, mx - 3, my - f * 2, "west");
                        long east = mech(sim, mx + 3, my - f * 2, "east");
                        int hounds = (west != 0L ? 1 : 0) + (east != 0L ? 1 : 0), n = 0;
                        for (long m : new long[]{west, east}) {
                            if (m == 0L) continue;
                            command.stationMech(sim, m, Math.round(sim.world().x(m)), Math.round(sim.world().y(m)));
                            placed.add(new Placed("HOUND " + (++n) + " of " + hounds, List.of(m)));
                        }
                        placed.add(new Placed("RIFLE TEAM", team(sim, mx, my - f * 6, "Rifle team", rng, false)));
                        backmost = my - f * 7;
                    }
                    case "infantry" -> {
                        placed.add(new Placed("RIFLE TEAM", team(sim, mx - 6, my - f * 2, "House front west", rng, false)));
                        placed.add(new Placed("RIFLE TEAM", team(sim, mx + 6, my - f * 2, "House front east", rng, false)));
                        int yardY = (frontY + rearY) / 2;
                        placed.add(new Placed("MORTAR TEAM", team(sim, Math.max(r.left() + 3, Math.min(r.right() - 3, mx)),
                                yardY, "Mortar team", rng, true)));
                        backmost = yardY - f;
                    }
                    default -> {
                        // On the lateral street just outside the redoubt's side walls: the boulevard the line runs along.
                        int row = boulevard(sim, r, frontY, rearY);
                        float facing = f > 0 ? 0f : 180f;
                        long tech = command.deploy(sim, VehicleType.MILITIA_TECHNICAL, r.left() - 4, row, facing);
                        long tank = command.deploy(sim, VehicleType.BREAKTHROUGH_TANK, r.right() + 4, row, facing);
                        command.station(sim, tech, r.left() - 4, row);
                        command.station(sim, tank, r.right() + 4, row);
                        placed.add(new Placed("TECHNICAL", List.of(tech)));
                        placed.add(new Placed("TANK", List.of(tank)));
                        placed.add(new Placed("RIFLE TEAM", team(sim, mx, my - f * 3, "Rifle team", rng, false)));
                        backmost = row - f * 2;
                    }
                }
                Set<Long> own = new HashSet<>();
                for (Placed p : placed) own.addAll(p.ids());
                for (Placed p : placed) {
                    System.out.printf("    [cordon-allies-look/%s] %s: %d placed%n", option, p.label(), p.ids().size());
                }
                int packSquad = pack(sim, r.centreX(), frontY + f * PACK_OUT, r.centreX(), r.centreY());
                // On the option and the mouth the pack comes through, a Hound's width readable.
                float x0 = mx - 6, x1 = mx + 6, y0 = Math.min(backmost, my + f * 10), y1 = Math.max(backmost, my + f * 10);
                for (Placed p : placed) for (long id : p.ids()) {
                    float[] at = where(sim, id);
                    if (at == null) continue;
                    x0 = Math.min(x0, at[0] - 3);
                    x1 = Math.max(x1, at[0] + 3);
                    y0 = Math.min(y0, at[1] - 3);
                    y1 = Math.max(y1, at[1] + 3);
                }
                float across = Math.max(FRAME_CELLS, Math.max(x1 - x0, (y1 - y0) * 16f / 9f));
                BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(context.modRoot(), W, H,
                        (x0 + x1) / 2f, (y0 + y1) / 2f, across).withImpactEffects().withUnitShadows();
                ReviewImpactFx fx = new ReviewImpactFx(context.modRoot());
                int fired = 0, recent = 0, bestFired = -1, bestTick = -1, anyFired = -1, anyTick = -1;
                BufferedImage best = null, any = null;
                for (int tick = 1; tick <= TICKS; tick++) {
                    command.advance(sim);
                    sim.advance(BattleSimulation.TICK_DT);
                    fx.feed(sim);
                    for (ShotEvent shot : sim.getShotsThisFrame()) {
                        if (!own.contains(shot.shooterId)) continue;
                        fired++;
                        // Rounds in the last few ticks are the ones whose tracers the still shows.
                        if (tick % STILL_EVERY > STILL_EVERY - RECENT_TICKS || tick % STILL_EVERY == 0) recent++;
                    }
                    if (tick % STILL_EVERY != 0) continue;
                    int alive = alive(sim, placed);
                    ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
                    CordonReviewAnnotations.add(marks, sim);
                    boxes(marks, sim, placed);
                    BufferedImage frame = renderer.render(sim, "urban cordon, seed " + seed + ", redoubt 2's street mouth: "
                            + option + ", tick " + tick, marks.build(), fx);
                    out.add(new SnapshotArtifact(String.format("%s-t%04d.png", option, tick), frame));
                    System.out.printf("    [cordon-allies-look/%s] tick %d: %d of %d placed alive, %d rounds fired (%d in the last %d ticks)%n",
                            option, tick, alive, own.size(), fired, recent, RECENT_TICKS);
                    float contact = contact(sim, placed, packSquad);
                    if (alive == countPlaced(placed) && recent > anyFired) {
                        anyFired = recent;
                        anyTick = tick;
                        any = frame;
                    }
                    // The pick wants the pack at the option, not a long shot at it.
                    if (alive == countPlaced(placed) && contact <= CONTACT_CELLS && recent > bestFired) {
                        bestFired = recent;
                        bestTick = tick;
                        best = frame;
                    }
                    fired = 0;
                    recent = 0;
                }
                if (best == null) {
                    best = any;
                    bestTick = anyTick;
                    bestFired = anyFired;
                }
                if (best != null) {
                    out.add(new SnapshotArtifact(option + "-pick.png", best));
                    System.out.printf("    [cordon-allies-look/%s] pick: tick %d (%d rounds in its window)%n",
                            option, bestTick, bestFired);
                }
            }
        }
        return out;
    }

    private static int frontY(CordonRedoubt r, int f) {
        return f > 0 ? Math.max(r.top(), r.bottom()) : Math.min(r.top(), r.bottom());
    }

    /** The sector's default armour off the field, so a frame shows only its option. */
    private static void clearSector(BattleSimulation sim, CordonRedoubt r) {
        for (long hull : sim.getCordon().armour().command().hulls()) {
            if (sim.convoy().body(hull).distanceTo(r.centreX(), r.centreY()) < 60f) sim.convoy().despawn(hull);
        }
    }

    /**
     * Where the pack comes through the ground in front of redoubt 2: the same pack played with no option on
     * the field, read where its body first crosses {@link #MOUTH_OUT} cells in front of the wall.
     */
    private static int[] mouth(long seed) throws Exception {
        CordonSetup.CordonMap cordon = CordonSetup.rolled(CordonVariant.LINE, CordonPlan.Density.URBAN,
                SurfacePalette.VERDANT, seed);
        int f = CordonFront.frontward(cordon.plan());
        try (BattleSimulation sim = CordonSetup.create(cordon, CordonVariant.LINE, List.of(),
                CordonSequence.Timetable.STANDARD)) {
            CordonRedoubt r = sim.getCordon().redoubts().get(1);
            int frontY = frontY(r, f);
            clearSector(sim, r);
            int squad = pack(sim, r.centreX(), frontY + f * PACK_OUT, r.centreX(), r.centreY());
            for (int tick = 1; tick <= TICKS; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                float sx = 0f, sy = 0f;
                int n = 0;
                for (int i = 0, c = sim.liveUnitCount(); i < c; i++) {
                    long unit = sim.liveUnitAt(i);
                    Squad s = sim.squadOf(unit);
                    if (s == null || s.id != squad) continue;
                    sx += sim.world().x(unit);
                    sy += sim.world().y(unit);
                    n++;
                }
                if (n == 0) break;
                if ((sy / n - frontY) * f <= MOUTH_OUT) {
                    System.out.printf("    [cordon-allies-look] mouth at %.0f,%.0f (tick %d)%n", sx / n, sy / n, tick);
                    return new int[]{Math.round(sx / n), Math.round(sy / n)};
                }
            }
            return new int[]{r.centreX(), frontY + f * MOUTH_OUT};
        }
    }

    /** An allied Hound set down on standable ground near the cell, under the allies' own authority. */
    private static long mech(BattleSimulation sim, int x, int y, String name) {
        MechSpawnPlacement.Point at = null;
        for (int r = 0; at == null && r <= 10; r++)
            for (int dy = -r; at == null && dy <= r; dy++) for (int dx = -r; at == null && dx <= r; dx++)
                if (Math.max(Math.abs(dx), Math.abs(dy)) == r)
                    at = MechSpawnPlacement.nearCell(sim.getGrid(), MechVariant.HOUND.radius, x + dx, y + dy,
                            (px, py) -> true, (px, py) -> true);
        if (at == null) {
            System.out.printf("    [cordon-allies-look] no standable ground for a Hound near %d,%d%n", x, y);
            return 0L;
        }
        int squad = sim.mintSquad(Faction.ALLY, UnitType.HEAVY_MECH);
        long mech = sim.spawn(new EntitySpec("Allied Hound " + name, Faction.ALLY, UnitType.HEAVY_MECH,
                at.cellX(), at.cellY()).atPosition(at.x(), at.y()).mechVariant(MechVariant.HOUND).squad(squad));
        sim.world().attachMechLoadout(mech, FactionMechLoadouts.create(MechVariant.HOUND, MechVariant.HOUND.defaultRole, "independent"));
        Squad s = sim.getSquad(squad);
        s.leaderId = mech;
        s.originalSize = 1;
        sim.claimSquadCommand(squad, CommandAuthority.GARRISON, ISSUER, "allied walker");
        return mech;
    }

    /**
     * An allied militia team stood up round the cell, holding there; a mortar team's first man carries the tube.
     * Returns its members.
     */
    private static List<Long> team(BattleSimulation sim, int x, int y, String name, Random rng, boolean mortar) {
        List<int[]> cells = CordonGround.clusterNear(sim, x, y, ColonyMilitia.POST_SIZE);
        if (cells.isEmpty()) return List.of();
        int squad = ColonyMilitia.seatTeam(sim, cells, name, RiskLevel.HIGH, rng);
        if (mortar) {
            SpecialEquipmentDef tube = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FIELD_MORTAR_ID);
            sim.world().attachSpecialEquipment(sim.getSquad(squad).leaderId, tube, tube.startingAmmo());
        }
        sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, cells.get(0)[0], cells.get(0)[1]),
                CommandAuthority.GARRISON, ISSUER, name + " holds");
        List<Long> members = new ArrayList<>();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            Squad s = sim.squadOf(unit);
            if (s != null && s.id == squad) members.add(unit);
        }
        return members;
    }

    /** A swarm pack: fourteen runners and three brutes attack-moving on the redoubt. Returns the runners' squad. */
    private static int pack(BattleSimulation sim, int x, int y, int towardX, int towardY) {
        int runners = spawnSwarm(sim, UnitType.SWARM_RUNNER, 14, x, y, towardX, towardY);
        spawnSwarm(sim, UnitType.SWARM_BRUTE, 3, x + 5, y, towardX, towardY);
        return runners;
    }

    private static int spawnSwarm(BattleSimulation sim, UnitType type, int count, int x, int y, int towardX, int towardY) {
        List<int[]> cells = CordonGround.clusterNear(sim, x, y, count);
        int squad = sim.mintSquad(Faction.DEFENDER, type);
        if (cells.isEmpty()) return squad;
        for (int i = 0; i < cells.size(); i++) {
            long id = sim.spawn(new EntitySpec("look pack " + type.name().toLowerCase() + " " + i, Faction.DEFENDER, type,
                    cells.get(i)[0], cells.get(i)[1]).squad(squad));
            if (sim.getSquad(squad).leaderId == 0L) sim.getSquad(squad).leaderId = id;
        }
        sim.getSquad(squad).originalSize = cells.size();
        sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, towardX, towardY),
                CommandAuthority.SCRIPTED, ISSUER, "look pack");
        return squad;
    }

    /**
     * The row through the redoubt's depth whose streets run furthest either side of it: the lateral road the
     * line runs along, where armour held to the roads stands.
     */
    private static int boulevard(BattleSimulation sim, CordonRedoubt r, int frontY, int rearY) {
        int best = (frontY + rearY) / 2, bestRun = -1;
        for (int row = Math.min(frontY, rearY); row <= Math.max(frontY, rearY); row++) {
            int run = 0;
            for (int d = 2; d <= 12; d++) {
                for (int cx : new int[]{r.left() - d, r.right() + d}) {
                    if (!sim.getGrid().inBounds(cx, row) || !sim.getGrid().isWalkable(cx, row)) continue;
                    if (sim.getTopology().getGroundKind(cx, row) == CellTopology.GroundKind.STREET) run++;
                }
            }
            if (run > bestRun) {
                bestRun = run;
                best = row;
            }
        }
        return best;
    }

    /** Cells from the nearest placed unit to the nearest runner of the pack. */
    private static float contact(BattleSimulation sim, List<Placed> placed, int packSquad) {
        float best = Float.MAX_VALUE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            Squad s = sim.squadOf(unit);
            if (s == null || s.id != packSquad) continue;
            float ux = sim.world().x(unit), uy = sim.world().y(unit);
            for (Placed p : placed) for (long id : p.ids()) {
                float[] at = where(sim, id);
                if (at != null) best = Math.min(best, (float) Math.hypot(at[0] - ux, at[1] - uy));
            }
        }
        return best;
    }

    private static int countPlaced(List<Placed> placed) {
        int n = 0;
        for (Placed p : placed) n += p.ids().size();
        return n;
    }

    private static int alive(BattleSimulation sim, List<Placed> placed) {
        int n = 0;
        for (Placed p : placed) for (long id : p.ids()) if (where(sim, id) != null) n++;
        return n;
    }

    /**
     * Each placed thing outlined round its living members (a team's stragglers more than {@link #COHESION}
     * cells from its middle left out) and named. An outline rather than a box: a box's wash would tint the
     * very bodies it points at.
     */
    private static void boxes(ReviewAnnotations.Builder marks, BattleSimulation sim, List<Placed> placed) {
        for (Placed p : placed) {
            List<float[]> at = new ArrayList<>();
            for (long id : p.ids()) {
                float[] w = where(sim, id);
                if (w != null) at.add(w);
            }
            if (at.isEmpty()) continue;
            float[] mid = median(at);
            float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
            for (float[] w : at) {
                if (Math.hypot(w[0] - mid[0], w[1] - mid[1]) > COHESION) continue;
                x0 = Math.min(x0, w[0]);
                y0 = Math.min(y0, w[1]);
                x1 = Math.max(x1, w[0]);
                y1 = Math.max(y1, w[1]);
            }
            x0 -= 2f;
            y0 -= 2f;
            x1 += 1.5f;
            y1 += 1.5f;
            float xm = (x0 + x1) / 2f;
            // The label sits at the outline's middle point, so the walk puts the top edge's middle there.
            marks.polyline(List.of(new ReviewAnnotation.Point(x0, y1), new ReviewAnnotation.Point(x1, y1),
                    new ReviewAnnotation.Point(x1, y0), new ReviewAnnotation.Point(xm, y0),
                    new ReviewAnnotation.Point(x0, y0), new ReviewAnnotation.Point(x0, y1)), p.label(), ReviewStyle.ALLY);
        }
    }

    private static float[] median(List<float[]> at) {
        float[] xs = new float[at.size()], ys = new float[at.size()];
        for (int i = 0; i < at.size(); i++) {
            xs[i] = at.get(i)[0];
            ys[i] = at.get(i)[1];
        }
        Arrays.sort(xs);
        Arrays.sort(ys);
        return new float[]{xs[xs.length / 2], ys[ys.length / 2]};
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
