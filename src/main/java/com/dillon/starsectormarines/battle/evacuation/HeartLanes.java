package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.HeartLane;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.RescueCommandFacts.KnownBore;
import com.dillon.starsectormarines.battle.cordon.AlliedColumns;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.RiskLevel;

import com.dillon.starsectormarines.battle.command.BarrageScreen;

import com.dillon.starsectormarines.battle.mech.FactionMechLoadouts;
import com.dillon.starsectormarines.battle.mech.MechSpawnPlacement;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.TerrainCostField;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleRoutePlanner;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Broad Front's allied lanes (`heart-nouns.md`, Lane): a column of militia at
 * the foot of each lane, sent up it together at the opening, each taking the
 * satellites in its own lane and then converging on the nearest living mother.
 *
 * <p>A column comes on and marches to its lane's first bore as the Cordon's
 * scripted columns do ({@link AlliedColumns}). Once it has arrived it is the
 * lane's: every second it is sent on to the lane's next bore when the one it is
 * on falls. The columns fight under garrison authority; they never end the
 * battle, and a lane broken below a third of its strength is a cost on the
 * outcome, not a loss.
 */
public final class HeartLanes {

    public static final String ISSUER = "heart-lanes";
    /** Militia teams in each column. */
    static final int TEAMS = 3;
    /** Seconds after the battle opens that the columns step off, together. */
    static final int STEP_OFF_SECONDS = 2;
    /** A member this close to its bore has arrived at it. */
    static final int ARRIVE_CELLS = 12;
    static final int RETARGET_TICKS = Math.round(1f / BattleSimulation.TICK_DT);

    private final class Lane implements AlliedColumns.Orders {
        final int number;
        final HeartLane ground;
        final int entryX, entryY;
        final AlliedColumns column;
        List<Integer> squads;
        int targetX = -1, targetY = -1;
        boolean broken;
        /** The lane's armour (The Burn's battlegroups): hulls driven on planned routes, mechs ordered as squads. */
        final List<Long> hulls = new ArrayList<>();
        final List<Integer> mechs = new ArrayList<>();
        int armourAimX = Integer.MIN_VALUE, armourAimY = Integer.MIN_VALUE;

        Lane(int number, HeartLane ground, int entryX, int entryY, RiskLevel risk, Random rng) {
            this.number = number;
            this.ground = ground;
            this.entryX = entryX;
            this.entryY = entryY;
            this.column = new AlliedColumns(List.of(new AlliedColumns.Departure(STEP_OFF_SECONDS, entryX, entryY)),
                    TEAMS, ISSUER, risk, rng);
        }

        @Override public int[] destination(BattleSimulation sim) {
            KnownBore bore = ground.target(known(sim), entryX, entryY);
            return bore == null ? null : aim(sim, ground, bore);
        }

        @Override public boolean stillNeeded(BattleSimulation sim, int x, int y) {
            return screen != null || livingBoreAt(sim, x, y);
        }

        @Override public boolean arrived(int x, int y, int destX, int destY) {
            return Math.max(Math.abs(x - destX), Math.abs(y - destY)) <= ARRIVE_CELLS;
        }

        @Override public void joined(BattleSimulation sim, int ignored, List<Integer> joined, int destX, int destY) {
            squads = new ArrayList<>(joined);
            targetX = destX;
            targetY = destY;
        }
    }

    private final List<Lane> lanes = new ArrayList<>();
    private BarrageScreen screen;

    /** Columns that follow a walking barrage: short of the firing line while their bore lies past it. */
    public HeartLanes following(BarrageScreen screen) {
        this.screen = screen;
        return this;
    }

    /** Where a lane goes for {@code bore}: the bore, or its line's cell short of the guns while they are on its ground. */
    private int[] aim(BattleSimulation sim, HeartLane ground, KnownBore bore) {
        int tick = sim.getSimTickIndex();
        if (screen == null || !screen.beyond(screen.along(bore.x(), bore.y()), tick)) return new int[]{bore.x(), bore.y()};
        // Held short of the guns, a lane keeps its own ground: its line across, not its bore's.
        int hold = screen.holdAlong(tick);
        int x = screen.advancesAlongY() ? ground.across() : hold, y = screen.advancesAlongY() ? hold : ground.across();
        for (int r = 0; r <= 8; r++)
            for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++)
                if (sim.getGrid().inBounds(x + dx, y + dy) && sim.getGrid().isWalkable(x + dx, y + dy)) return new int[]{x + dx, y + dy};
        return new int[]{bore.x(), bore.y()};
    }

    /**
     * @param grounds each lane's band, west to east
     * @param entries each lane's foot on the start line, {@code [x, y]}, in the same order
     */
    public HeartLanes(List<HeartLane> grounds, List<int[]> entries, RiskLevel risk, long seed) {
        Random rng = new Random(seed);
        for (int i = 0; i < grounds.size(); i++) {
            lanes.add(new Lane(i + 1, grounds.get(i), entries.get(i)[0], entries.get(i)[1], risk, rng));
        }
    }

    /** One tick; the lanes' beats go into {@code beats}. */
    public void tick(BattleSimulation sim, List<String> beats) {
        int tick = sim.getSimTickIndex();
        for (Lane lane : lanes) {
            if (tick % RETARGET_TICKS == 0) driveArmour(sim, lane);
            if (lane.squads == null) {
                lane.column.advance(sim, tick, lane, (beat, observerOnly) -> {
                    if (beat.startsWith("column_arrived_")) beats.add("lane_arrived_" + lane.number);
                });
                continue;
            }
            lane.squads.removeIf(squad -> sim.squadMemberCount(squad) == 0);
            int alive = 0;
            for (int squad : lane.squads) alive += sim.squadMemberCount(squad);
            if (!lane.broken && alive * 3 < TEAMS * ColonyMilitia.POST_SIZE) {
                lane.broken = true;
                beats.add("lane_broken_" + lane.number);
            }
            if (lane.squads.isEmpty() || tick % RETARGET_TICKS != 0) continue;
            if (screen == null && livingBoreAt(sim, lane.targetX, lane.targetY)) continue;
            KnownBore next = lane.ground.target(known(sim), lane.targetX, lane.targetY);
            if (next == null) continue;
            int[] aim = aim(sim, lane.ground, next);
            if (aim[0] == lane.targetX && aim[1] == lane.targetY) continue;
            lane.targetX = aim[0];
            lane.targetY = aim[1];
            for (int squad : lane.squads) {
                sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, aim[0], aim[1]),
                        CommandAuthority.GARRISON, ISSUER, "lane " + lane.number + " goes on to " + next.key());
            }
        }
    }

    /** Cells between neighbouring machines across a lane, and how far behind the lane's aim they stand. */
    static final int ARMOUR_SPACING = 5, ARMOUR_BEHIND = 4;

    /**
     * Sets a machine down on lane {@code index}'s ground near {@code (x, y)}, facing the advance: a hull
     * (a {@link VehicleType}) or a Hound. Returns false when no ground near takes it.
     */
    public boolean addArmour(BattleSimulation sim, int index, VehicleType type, int x, int y, float facing) {
        Lane lane = lanes.get(index);
        if (type == null) {
            MechSpawnPlacement.Point at = null;
            for (int r = 0; at == null && r <= 8; r++)
                for (int dy = -r; at == null && dy <= r; dy++) for (int dx = -r; at == null && dx <= r; dx++)
                    if (Math.max(Math.abs(dx), Math.abs(dy)) == r)
                        at = MechSpawnPlacement.nearCell(sim.getGrid(), MechVariant.HOUND.radius, x + dx, y + dy,
                                (px, py) -> true, this::clearOfArmour);
            if (at == null) return false;
            placed.add(new float[]{at.x(), at.y()});
            int squad = sim.mintSquad(Faction.ALLY, UnitType.HEAVY_MECH);
            long mech = sim.spawn(new EntitySpec("Lane " + lane.number + " walker " + (lane.mechs.size() + 1), Faction.ALLY,
                    UnitType.HEAVY_MECH, at.cellX(), at.cellY()).atPosition(at.x(), at.y()).mechVariant(MechVariant.HOUND).squad(squad));
            sim.world().attachMechLoadout(mech, FactionMechLoadouts.create(MechVariant.HOUND, MechVariant.HOUND.defaultRole, "independent"));
            Squad s = sim.getSquad(squad);
            s.leaderId = mech;
            s.originalSize = 1;
            sim.claimSquadCommand(squad, CommandAuthority.GARRISON, ISSUER, "lane " + lane.number + " walker");
            lane.mechs.add(squad);
            return true;
        }
        VehicleClearance clearance = clearance(sim, type);
        int[] cell = null;
        for (int r = 0; cell == null && r <= 10; r++)
            for (int dy = -r; cell == null && dy <= r; dy++) for (int dx = -r; cell == null && dx <= r; dx++)
                if (Math.max(Math.abs(dx), Math.abs(dy)) == r && clearance.isPassable(x + dx, y + dy)
                        && clearOfArmour(x + dx + .5f, y + dy + .5f)) cell = new int[]{x + dx, y + dy};
        if (cell == null) return false;
        placed.add(new float[]{cell[0] + .5f, cell[1] + .5f});
        long id = sim.convoy().spawn(type, Faction.ALLY, VehicleMission.deployed(cell[0] + .5f, cell[1] + .5f));
        sim.convoy().body(id).teleport(cell[0] + .5f, cell[1] + .5f, facing);
        lane.hulls.add(id);
        return true;
    }

    /** Moves a lane's armour up to its aim when the aim has moved: each machine to its own cell across the lane. */
    private void driveArmour(BattleSimulation sim, Lane lane) {
        if (lane.hulls.isEmpty() && lane.mechs.isEmpty()) return;
        KnownBore bore = lane.ground.target(known(sim), lane.entryX, lane.entryY);
        if (bore == null) return;
        int[] aim = aim(sim, lane.ground, bore);
        if (aim[0] == lane.armourAimX && aim[1] == lane.armourAimY) return;
        boolean alongY = lane.ground.advancesAlongY();
        int toward = screen == null ? 0 : screen.sign();
        int count = lane.hulls.size() + lane.mechs.size(), i = 0;
        boolean routed = true;
        for (long hull : lane.hulls) {
            int[] at = station(aim, alongY, toward, i++, count);
            if (sim.convoy().structure(hull) <= 0f) continue;
            routed &= route(sim, hull, at[0], at[1], alongY, toward);
        }
        // An aim is kept only once every hull has a route to it; one that found none tries again next time.
        if (routed) {
            lane.armourAimX = aim[0];
            lane.armourAimY = aim[1];
        }
        for (int squad : lane.mechs) {
            int[] at = station(aim, alongY, toward, i++, count);
            if (sim.squadMemberCount(squad) == 0) continue;
            int[] stand = standable(sim, at, alongY, toward);
            if (stand == null) continue;
            sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, stand[0], stand[1]),
                    CommandAuthority.GARRISON, ISSUER, "lane " + lane.number + " walker comes up");
        }
    }

    /** The nearest ground a Hound can stand on at {@code at}, pulled back along the advance where the woods bar it. */
    private static int[] standable(BattleSimulation sim, int[] at, boolean alongY, int toward) {
        for (int k = 0; k <= FALLBACK_STEPS; k++) {
            int back = toward * FALLBACK_STEP * k;
            int gx = alongY ? at[0] : at[0] - back, gy = alongY ? at[1] - back : at[1];
            MechSpawnPlacement.Point p = MechSpawnPlacement.nearCell(sim.getGrid(), MechVariant.HOUND.radius, gx, gy,
                    (px, py) -> true, (px, py) -> true);
            if (p != null) return new int[]{p.cellX(), p.cellY()};
        }
        return null;
    }

    private static int[] station(int[] aim, boolean alongY, int toward, int i, int count) {
        int across = Math.round((i - (count - 1) / 2f) * ARMOUR_SPACING), back = -toward * ARMOUR_BEHIND;
        return alongY ? new int[]{aim[0] + across, aim[1] + back} : new int[]{aim[0] + back, aim[1] + across};
    }

    /** Plans a hull's drive to {@code (x, y)} round what it cannot cross; a hull with no route holds. */
    /** How far a hull's goal is pulled back toward the start line, a step at a time, when the woods bar it. */
    static final int FALLBACK_STEP = 6, FALLBACK_STEPS = 12;

    /**
     * Drives a hull to {@code (x, y)}, or as far toward it as it can: a goal it has no route to is pulled
     * back along the advance a step at a time, since the forest's crowns bar hulls and its rides and
     * clearings do not. False when no step finds a route.
     */
    private boolean route(BattleSimulation sim, long hull, int x, int y, boolean alongY, int toward) {
        GroundBody body = sim.convoy().body(hull);
        float here = alongY ? body.y : body.x;
        for (int k = 0; k <= FALLBACK_STEPS; k++) {
            int back = toward * FALLBACK_STEP * k;
            int gx = alongY ? x : x - back, gy = alongY ? y - back : y;
            // Never back off: a goal no further on than the hull stands is where it already is.
            if (k > 0 && toward * ((alongY ? gy : gx) - here) <= 0) return true;
            if (routeTo(sim, hull, gx, gy)) return true;
        }
        return false;
    }

    private boolean routeTo(BattleSimulation sim, long hull, int x, int y) {
        VehicleType type = sim.convoy().vehicleType(hull);
        VehicleClearance clearance = clearance(sim, type);
        GroundBody body = sim.convoy().body(hull);
        int[] from = VehicleRoutePlanner.snapToMask(clearance, (int) body.x, (int) body.y, 6);
        int[] to = VehicleRoutePlanner.snapToMask(clearance, x, y, 8);
        if (from == null || to == null) return false;
        if (Math.abs(from[0] - to[0]) <= 1 && Math.abs(from[1] - to[1]) <= 1) return true;
        if (costs == null) costs = TerrainCostField.from(sim.getTopology());
        float[][] path = VehicleRoutePlanner.routeDrivable(from[0], from[1], to[0], to[1], sim.getGrid(), costs, clearance, type);
        if (path == null) return false;
        VehicleMission mission = sim.convoy().mission(hull);
        mission.authoredRouteX = path[0];
        mission.authoredRouteY = path[1];
        mission.authoredRoutePaused = false;
        return true;
    }

    /** Where armour has been set down, so no two machines start on each other. */
    private final List<float[]> placed = new ArrayList<>();
    static final float ARMOUR_SEPARATION = 4f;

    private boolean clearOfArmour(float x, float y) {
        for (float[] p : placed) if (Math.hypot(p[0] - x, p[1] - y) < ARMOUR_SEPARATION) return false;
        return true;
    }

    private final Map<Integer, VehicleClearance> clearances = new HashMap<>();
    private TerrainCostField costs;

    private VehicleClearance clearance(BattleSimulation sim, VehicleType type) {
        int radius = VehicleClearance.radiusForWidth(type.visualWidthCells);
        return clearances.computeIfAbsent(radius, r -> VehicleClearance.erode(sim.getGrid(), r));
    }

    private static List<KnownBore> known(BattleSimulation sim) {
        List<KnownBore> out = new ArrayList<>();
        for (SwarmBore bore : sim.swarmBores().bores()) {
            out.add(new KnownBore(bore.beatKey(), bore.mother(), bore.x(), bore.y(), !bore.collapsed()));
        }
        return out;
    }

    private static boolean livingBoreAt(BattleSimulation sim, int x, int y) {
        for (SwarmBore bore : sim.swarmBores().bores()) {
            if (!bore.collapsed() && bore.x() == x && bore.y() == y) return true;
        }
        return false;
    }
}
