package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.HeartLane;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.RescueCommandFacts.KnownBore;
import com.dillon.starsectormarines.battle.cordon.AlliedColumns;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.RiskLevel;

import com.dillon.starsectormarines.battle.command.BarrageScreen;
import com.dillon.starsectormarines.battle.command.allied.AlliedCommand;

import com.dillon.starsectormarines.battle.mech.FactionMechLoadouts;
import com.dillon.starsectormarines.battle.mech.MechSpawnPlacement;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
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
    public static final int STEP_OFF_SECONDS = 2;
    /** A member this close to its bore has arrived at it. */
    static final int ARRIVE_CELLS = 12;
    /** A marching column's destination this far from its lane's current aim is stale, and it is sent on. */
    static final int REAIM_CELLS = 3;
    static final int RETARGET_TICKS = Math.round(1f / BattleSimulation.TICK_DT);

    private final class Lane implements AlliedColumns.Orders {
        final int number;
        final HeartLane ground;
        final int entryX, entryY;
        final AlliedColumns column;
        List<Integer> squads;
        /** The militia the lane's arrived waves were raised with: what "broken" is measured against. */
        int joinedMembers;
        final int teams;
        int targetX = -1, targetY = -1;
        boolean broken;
        /** The lane's armour (The Burn's battlegroups): hulls and mechs both stationed by the allied command. */
        final List<Long> hulls = new ArrayList<>();
        final List<Long> mechs = new ArrayList<>();

        Lane(int number, HeartLane ground, int entryX, int entryY, int teams, int[] waveSeconds, RiskLevel risk, Random rng) {
            this.number = number;
            this.ground = ground;
            this.entryX = entryX;
            this.entryY = entryY;
            this.teams = teams;
            List<AlliedColumns.Departure> departures = new ArrayList<>();
            for (int seconds : waveSeconds) departures.add(new AlliedColumns.Departure(seconds, entryX, entryY));
            this.column = new AlliedColumns(departures, teams, ISSUER, risk, rng);
        }

        @Override public int[] destination(BattleSimulation sim) {
            KnownBore bore = ground.target(known(sim), entryX, entryY);
            return bore == null ? null : aim(sim, ground, bore);
        }

        @Override public boolean stillNeeded(BattleSimulation sim, int x, int y) {
            if (screen == null) return livingBoreAt(sim, x, y);
            // Following the guns, a column marches to where its lane is aimed now: the hold moves up as each
            // line lifts, and the bore it was sent for may have fallen.
            int[] now = destination(sim);
            return now != null && Math.max(Math.abs(now[0] - x), Math.abs(now[1] - y)) <= REAIM_CELLS;
        }

        @Override public boolean arrived(int x, int y, int destX, int destY) {
            return Math.max(Math.abs(x - destX), Math.abs(y - destY)) <= ARRIVE_CELLS;
        }

        @Override public void joined(BattleSimulation sim, int ignored, List<Integer> joined, int destX, int destY) {
            // A later wave joins the lane where it stands: the lane's own retarget sends it on with the rest.
            if (squads == null) {
                squads = new ArrayList<>();
                targetX = destX;
                targetY = destY;
            }
            squads.addAll(joined);
            joinedMembers += teams * ColonyMilitia.POST_SIZE;
        }
    }

    private final List<Lane> lanes = new ArrayList<>();
    private BarrageScreen screen;

    /** Each lane's ground across the front, in lane order. */
    public List<HeartLane> grounds() {
        List<HeartLane> out = new ArrayList<>();
        for (Lane lane : lanes) out.add(lane.ground);
        return out;
    }

    /** The company's own sector on The Burn's front, recorded so a review can show it; null without a front. */
    public HeartLane companySector() {
        return companySector;
    }

    public HeartLanes companySector(HeartLane sector) {
        companySector = sector;
        return this;
    }

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
        this(grounds, entries, TEAMS, new int[]{STEP_OFF_SECONDS}, risk, seed);
    }

    /**
     * @param teams       militia teams in each wave of each lane
     * @param waveSeconds when each wave steps off, in seconds from the opening; the first is the lane's column
     */
    public HeartLanes(List<HeartLane> grounds, List<int[]> entries, int teams, int[] waveSeconds, RiskLevel risk, long seed) {
        Random rng = new Random(seed);
        for (int i = 0; i < grounds.size(); i++) {
            lanes.add(new Lane(i + 1, grounds.get(i), entries.get(i)[0], entries.get(i)[1], teams, waveSeconds, risk, rng));
        }
    }

    /** One tick; the lanes' beats go into {@code beats}. */
    public void tick(BattleSimulation sim, List<String> beats) {
        int tick = sim.getSimTickIndex();
        allied.advance(sim);
        for (Lane lane : lanes) {
            if (tick % RETARGET_TICKS == 0) driveArmour(sim, lane);
            lane.column.advance(sim, tick, lane, (beat, observerOnly) -> {
                if (beat.equals("column_arrived_1")) beats.add("lane_arrived_" + lane.number);
            });
            if (lane.squads == null) continue;
            lane.squads.removeIf(squad -> sim.squadMemberCount(squad) == 0);
            int alive = 0;
            for (int squad : lane.squads) alive += sim.squadMemberCount(squad);
            if (!lane.broken && alive * 3 < lane.joinedMembers) {
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
            lane.mechs.add(mech);
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
        lane.hulls.add(allied.deploy(sim, type, cell[0], cell[1], facing));
        return true;
    }

    /** Moves a lane's armour up to its aim when the aim has moved: each machine to its own cell across the lane. */
    private void driveArmour(BattleSimulation sim, Lane lane) {
        if (lane.hulls.isEmpty() && lane.mechs.isEmpty()) return;
        KnownBore bore = lane.ground.target(known(sim), lane.entryX, lane.entryY);
        if (bore == null) return;
        int[] aim = aim(sim, lane.ground, bore);
        boolean alongY = lane.ground.advancesAlongY();
        int toward = screen == null ? 0 : screen.sign();
        int count = lane.hulls.size() + lane.mechs.size(), i = 0;
        // The allied command drives each machine to its post and fights it from there; it re-orders only
        // when a post has moved, and pulls a goal the woods bar back toward the machine.
        for (long hull : lane.hulls) {
            int[] at = station(aim, alongY, toward, i++, count);
            if (sim.convoy().structure(hull) <= 0f) continue;
            allied.station(sim, hull, at[0], at[1]);
        }
        for (long mech : lane.mechs) {
            int[] at = station(aim, alongY, toward, i++, count);
            allied.stationMech(sim, mech, at[0], at[1]);
        }
    }

    private static int[] station(int[] aim, boolean alongY, int toward, int i, int count) {
        int across = Math.round((i - (count - 1) / 2f) * ARMOUR_SPACING), back = -toward * ARMOUR_BEHIND;
        return alongY ? new int[]{aim[0] + across, aim[1] + back} : new int[]{aim[0] + back, aim[1] + across};
    }

    /** Where armour has been set down, so no two machines start on each other. */
    private final List<float[]> placed = new ArrayList<>();
    static final float ARMOUR_SEPARATION = 4f;

    private boolean clearOfArmour(float x, float y) {
        for (float[] p : placed) if (Math.hypot(p[0] - x, p[1] - y) < ARMOUR_SEPARATION) return false;
        return true;
    }

    private final Map<Integer, VehicleClearance> clearances = new HashMap<>();
    /** Drives and fights the lanes' hulls (`allied-command.md`). */
    private final AlliedCommand allied = new AlliedCommand(ISSUER);
    private HeartLane companySector;

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
