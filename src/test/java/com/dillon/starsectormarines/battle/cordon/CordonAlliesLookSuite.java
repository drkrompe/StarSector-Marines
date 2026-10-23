package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.allied.AlliedCommand;
import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.mech.FactionMechLoadouts;
import com.dillon.starsectormarines.battle.mech.MechSpawnPlacement;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The urban Cordon's allied backbone as the owner would see it ({@code cordon-allied-forces.md}): three
 * options staged on a real urban Cordon map at redoubt 2's sector, each against the same swarm pack coming
 * in from the front. {@code mechs}: a pair of allied Hounds in the street mouths beside the redoubt and a
 * walking rifle team. {@code infantry}: two rifle teams in the house fronts either side and a mortar team in
 * the redoubt's yard. {@code boulevard}: a technical and a tank on the widest street behind the line, with
 * riflemen. The sector's default armour is taken off the field first, so each frame shows only its option.
 *
 * <p>Stills every {@link #STILL_EVERY} ticks at play zoom, with the Cordon's own review annotations.
 * {@code -Dsnapshot.cordonAllies.seed=3} picks the map. Opt-in: a design question, not part of {@code all}.
 */
public final class CordonAlliesLookSuite implements SnapshotSuite {

    static final int W = 1920, H = 1080;
    static final int TICKS = 600, STILL_EVERY = 60;
    /** The camera: centred this many cells in front of redoubt 2's front wall, this many cells across. */
    static final int FRAME_OUT = 11;
    static final float FRAME_CELLS = 64f;
    /** Cells in front of redoubt 2's front wall the pack starts. */
    static final int PACK_OUT = 26;
    private static final String ISSUER = "cordon-allies-look";

    @Override public String id() { return "cordon-allies-look"; }

    @Override public String label() { return "Cordon urban allies: mech lance, infantry and mortars, armour on the boulevard"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        long seed = Long.parseLong(System.getProperty("snapshot.cordonAllies.seed", "3").trim());
        List<SnapshotArtifact> out = new ArrayList<>();
        for (String option : List.of("mechs", "infantry", "boulevard")) {
            CordonSetup.CordonMap cordon = CordonSetup.rolled(CordonVariant.LINE, CordonPlan.Density.URBAN,
                    SurfacePalette.VERDANT, seed);
            int f = CordonFront.frontward(cordon.plan());
            try (BattleSimulation sim = CordonSetup.create(cordon, CordonVariant.LINE, List.of(),
                    CordonSequence.Timetable.STANDARD)) {
                CordonRedoubt r = sim.getCordon().redoubts().get(1);
                int frontY = f > 0 ? Math.max(r.top(), r.bottom()) : Math.min(r.top(), r.bottom());
                int rearY = f > 0 ? Math.min(r.top(), r.bottom()) : Math.max(r.top(), r.bottom());
                // Only the option on the field: the sector's default armour leaves.
                for (long hull : sim.getCordon().armour().command().hulls()) {
                    if (sim.convoy().body(hull).distanceTo(r.centreX(), r.centreY()) < 60f) sim.convoy().despawn(hull);
                }
                Random rng = new Random(seed);
                switch (option) {
                    case "mechs" -> {
                        AlliedCommand command = new AlliedCommand(ISSUER);
                        long west = mech(sim, r.left() - 5, frontY, "west");
                        long east = mech(sim, r.right() + 5, frontY, "east");
                        if (west != 0L) command.stationMech(sim, west, r.left() - 5, frontY + f * 3);
                        if (east != 0L) command.stationMech(sim, east, r.right() + 5, frontY + f * 3);
                        team(sim, r.right() + 9, frontY - f, "Rifle team", rng, false);
                    }
                    case "infantry" -> {
                        team(sim, r.left() - 9, frontY, "House front west", rng, false);
                        team(sim, r.right() + 9, frontY, "House front east", rng, false);
                        team(sim, r.centreX(), (frontY + rearY) / 2, "Mortar team", rng, true);
                    }
                    default -> {
                        AlliedCommand command = new AlliedCommand(ISSUER);
                        int row = boulevard(sim, r, frontY, rearY, f);
                        float facing = f > 0 ? 0f : 180f;
                        long tech = command.deploy(sim, VehicleType.MILITIA_TECHNICAL, r.left() - 7, row, facing);
                        long tank = command.deploy(sim, VehicleType.BREAKTHROUGH_TANK, r.right() + 7, row, facing);
                        command.station(sim, tech, r.left() - 7, row);
                        command.station(sim, tank, r.right() + 7, row);
                        team(sim, r.right() + 9, frontY - f, "Rifle team", rng, false);
                    }
                }
                pack(sim, r.centreX(), frontY + f * PACK_OUT, r.centreX(), r.centreY());
                // One camera for every option, so the three read side by side.
                BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(context.modRoot(), W, H,
                        r.centreX() + 0.5f, frontY + f * FRAME_OUT + 0.5f, FRAME_CELLS).withImpactEffects().withUnitShadows();
                ReviewImpactFx fx = new ReviewImpactFx(context.modRoot());
                for (int tick = 0; tick <= TICKS; tick++) {
                    if (tick > 0) {
                        sim.advance(BattleSimulation.TICK_DT);
                        fx.feed(sim);
                    }
                    if (tick == 0 || tick % STILL_EVERY != 0) continue;
                    out.add(new SnapshotArtifact(String.format("%s-t%04d.png", option, tick),
                            renderer.render(sim, "urban cordon, seed " + seed + ", redoubt 2: " + option + ", tick " + tick,
                                    CordonReviewAnnotations.forBattle(sim), fx)));
                }
            }
        }
        return out;
    }

    /** An allied Hound set down on standable ground near the cell, under the allies' own authority. */
    private static long mech(BattleSimulation sim, int x, int y, String name) {
        MechSpawnPlacement.Point at = null;
        for (int r = 0; at == null && r <= 10; r++)
            for (int dy = -r; at == null && dy <= r; dy++) for (int dx = -r; at == null && dx <= r; dx++)
                if (Math.max(Math.abs(dx), Math.abs(dy)) == r)
                    at = MechSpawnPlacement.nearCell(sim.getGrid(), MechVariant.HOUND.radius, x + dx, y + dy,
                            (px, py) -> true, (px, py) -> true);
        if (at == null) return 0L;
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

    /** An allied militia team stood up round the cell, holding there; a mortar team's first man carries the tube. */
    private static void team(BattleSimulation sim, int x, int y, String name, Random rng, boolean mortar) {
        List<int[]> cells = CordonGround.clusterNear(sim, x, y, ColonyMilitia.POST_SIZE);
        if (cells.isEmpty()) return;
        int squad = ColonyMilitia.seatTeam(sim, cells, name, RiskLevel.HIGH, rng);
        if (mortar) {
            SpecialEquipmentDef tube = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FIELD_MORTAR_ID);
            long first = sim.getSquad(squad).leaderId;
            sim.world().attachSpecialEquipment(first, tube, tube.startingAmmo());
        }
        sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, cells.get(0)[0], cells.get(0)[1]),
                CommandAuthority.GARRISON, ISSUER, name + " holds");
    }

    /** A swarm pack: fourteen runners and three brutes attack-moving on the redoubt. */
    private static void pack(BattleSimulation sim, int x, int y, int towardX, int towardY) {
        spawnSwarm(sim, UnitType.SWARM_RUNNER, 14, x, y, towardX, towardY);
        spawnSwarm(sim, UnitType.SWARM_BRUTE, 3, x + 5, y, towardX, towardY);
    }

    private static void spawnSwarm(BattleSimulation sim, UnitType type, int count, int x, int y, int towardX, int towardY) {
        List<int[]> cells = CordonGround.clusterNear(sim, x, y, count);
        if (cells.isEmpty()) return;
        int squad = sim.mintSquad(Faction.DEFENDER, type);
        for (int i = 0; i < cells.size(); i++) {
            long id = sim.spawn(new EntitySpec("look pack " + type.name().toLowerCase() + " " + i, Faction.DEFENDER, type,
                    cells.get(i)[0], cells.get(i)[1]).squad(squad));
            if (sim.getSquad(squad).leaderId == 0L) sim.getSquad(squad).leaderId = id;
        }
        sim.getSquad(squad).originalSize = cells.size();
        sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, towardX, towardY),
                CommandAuthority.SCRIPTED, ISSUER, "look pack");
    }

    /**
     * The row through the redoubt's depth whose streets run furthest either side of it: the boulevard the
     * line's lateral road makes, where armour held to the roads would stand.
     */
    private static int boulevard(BattleSimulation sim, CordonRedoubt r, int frontY, int rearY, int f) {
        int best = frontY - f * 2, bestRun = -1;
        for (int row = Math.min(frontY, rearY); row <= Math.max(frontY, rearY); row++) {
            int run = 0;
            for (int d = 3; d <= 30; d++) {
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
}
