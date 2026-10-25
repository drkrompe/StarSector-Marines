package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.allied.AdvanceRoute;
import com.dillon.starsectormarines.battle.command.allied.AlliedDepot;
import com.dillon.starsectormarines.battle.command.allied.AlliedRequest;
import com.dillon.starsectormarines.battle.command.allied.AlliedTool;
import com.dillon.starsectormarines.battle.command.allied.AlliedToolShed;
import com.dillon.starsectormarines.battle.command.allied.ScreenedAdvance;
import com.dillon.starsectormarines.battle.command.HeartLane;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.RescueCommandFacts.KnownBore;
import com.dillon.starsectormarines.battle.cordon.AlliedColumns;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
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
import com.dillon.starsectormarines.battle.vehicle.TurnAwareCorridor;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    /** Cells back from a broken lane's bore toward its foot that its gap is taken to be, when none of it stands. */
    static final int GAP_STANDOFF = 24;
    /** Cells from the gap within which a re-aimed wave standing in the broken lane's band has closed it. */
    static final int GAP_REACH = 36;
    static final int RETARGET_TICKS = Math.round(1f / BattleSimulation.TICK_DT);

    private final class Lane implements AlliedColumns.Orders {
        final int number;
        final HeartLane ground;
        final int entryX, entryY;
        final AlliedColumns column;
        List<Integer> squads;
        /** The militia standing in the lane's waves as each arrived: what "broken" is measured against. */
        int joinedMembers;
        final int teams;
        int targetX = -1, targetY = -1;
        boolean broken;
        /** The lane's armour (The Burn's battlegroups): hulls and mechs both stationed by the allied command. */
        final List<Long> hulls = new ArrayList<>();
        final List<Long> mechs = new ArrayList<>();
        /**
         * Hulls the allied command could not serve a slot in the line for, moved back to a second rank one spacing
         * deeper, where the ground differs; forgotten when the line moves to a new aim.
         */
        final Set<Long> rearRank = new HashSet<>();
        int[] rankAim;
        /** The lane's formation onto its next bore, while it is going there; {@code null} when it fights where it is. */
        ScreenedAdvance advance;
        /** Whether the lane's armour screens its militia in the current advance (open ground), or the reverse. */
        boolean armourLeads;
        /** Members already given to the current advance, so later waves and machines join it once. */
        final Set<Integer> formedSquads = new HashSet<>();
        final Set<Long> formedMachines = new HashSet<>();
        /** The most machines the lane has had standing: one fewer standing now is a lane uncovered. */
        int machinesHeld;
        /** How many times the lane has called armour up. */
        int calls;
        /** Contacts the lane's formation has pushed through at the cap with none cleared between: two is stalled. */
        int pushesRunning;
        /** The broken lane whose gap this lane's next wave is re-aimed to close (F2), or {@code null}. */
        Lane reaimedTo;
        /** Where the re-aimed wave goes: the broken lane's ground as it stood when the commander re-aimed. */
        int[] gapAt;
        /** Times the commander has re-aimed a wave at this lane's gap: the count its re-aim beats carry. */
        int reaims;
        /** The lane's assault on its bore (`heart-bore-assault.md`), or {@code null} while it follows the guns. */
        BoreAssault assault;
        /** Squads already given the current assault phase's order. */
        final Set<Integer> assaultOrdered = new HashSet<>();
        /** Whether the lane's last bore fell under its assault, so the next one is taken armour first. */
        boolean afterFall;

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
            int[] gap = gapToClose();
            if (gap != null && !boundTo(gap)) {
                // Asked as a column steps off (or as a marching one finds its bore gone): it goes for the gap.
                laneBeats.add("wave_sent_" + reaimedTo.number + "_from_" + number + "_" + reaimedTo.reaims);
                return gap;
            }
            // A lane that stands is where its next wave goes: the wave joins it and goes on with it, rather than
            // marching past it into the swarm on its own.
            int[] standing = screen == null ? standing(sim) : null;
            if (standing != null) return standing;
            // A lane assaulting its bore takes its waves on its support line, behind its machines.
            if (assault != null) return new int[]{assault.supportX, assault.supportY};
            KnownBore bore = ground.target(known(sim), entryX, entryY);
            return bore == null ? null : aim(sim, ground, bore);
        }

        @Override public boolean stillNeeded(BattleSimulation sim, int x, int y) {
            int[] gap = gapToClose();
            if (gap != null && near(gap, x, y)) return true;
            if (screen == null) {
                int[] standing = standing(sim);
                if (standing != null) return Math.max(Math.abs(standing[0] - x), Math.abs(standing[1] - y)) <= ARRIVE_CELLS;
                return livingBoreAt(sim, x, y);
            }
            // Following the guns, a column marches to where its lane is aimed now: the hold moves up as each
            // line lifts, and the bore it was sent for may have fallen.
            int[] now = destination(sim);
            return now != null && Math.max(Math.abs(now[0] - x), Math.abs(now[1] - y)) <= REAIM_CELLS;
        }

        @Override public boolean arrived(int x, int y, int destX, int destY) {
            // A wave sent to close a gap has closed it once it stands in the broken lane's ground near the gap: the swarm
            // that broke the lane is still there, and an attack-moving column fights its way in rather than arriving.
            if (gapAt != null && reaimedTo != null && near(gapAt, destX, destY)
                    && inBand(reaimedTo.ground, x, y) && Math.hypot(x - destX, y - destY) <= GAP_REACH) return true;
            return Math.max(Math.abs(x - destX), Math.abs(y - destY)) <= ARRIVE_CELLS;
        }

        /**
         * A column held short of its destination by the fight on its own lane's ground has reached the lane's front:
         * one of its squads is engaged inside the band. One stopped anywhere else (lost, or blocked) has not.
         */
        @Override public boolean holdsFront(BattleSimulation sim, List<Integer> squads, int destX, int destY) {
            for (int id : squads) {
                Squad squad = sim.getSquad(id);
                if (squad == null || squad.alertLevel != SquadAlertLevel.ENGAGED) continue;
                long leader = squad.leaderId;
                if (leader != 0L && sim.resolveUnit(leader) != 0L && sim.world().isAlive(leader)
                        && inBand(ground, sim.world().x(leader), sim.world().y(leader))) return true;
            }
            return false;
        }

        /** Where the lane's living militia stand, the middle of them on walkable ground; null while none does. */
        int[] standing(BattleSimulation sim) {
            if (squads == null) return null;
            float x = 0f, y = 0f;
            int n = 0;
            for (int squad : squads) {
                for (int i = 0, m = sim.squadMemberCount(squad); i < m; i++) {
                    long member = sim.squadMemberAt(squad, i);
                    x += sim.world().x(member);
                    y += sim.world().y(member);
                    n++;
                }
            }
            return n == 0 ? null : walkableNear(sim, Math.round(x / n), Math.round(y / n));
        }

        @Override public void joined(BattleSimulation sim, int ignored, List<Integer> joined, int destX, int destY) {
            if (reaimedTo != null && gapAt != null && near(gapAt, destX, destY)) {
                reaimedTo.reinforce(sim, joined);
                closedGaps.add(reaimedTo.number + "_" + reaimedTo.reaims);
                reaimedTo = null;
                gapAt = null;
                return;
            }
            // A later wave joins the lane where it stands: the lane's own retarget sends it on with the rest.
            if (squads == null) {
                squads = new ArrayList<>();
                targetX = destX;
                targetY = destY;
            }
            squads.addAll(joined);
            // Measured as the wave came in, not as it was raised: a column that fought its way up arrives short.
            for (int squad : joined) joinedMembers += sim.squadMemberCount(squad);
            // A wave that joined the lane where it stands goes on with it to its aim (a formation gives it a slot,
            // an assault its phase's order).
            if (assault == null && (targetX != destX || targetY != destY)) {
                for (int squad : joined) {
                    sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, targetX, targetY),
                            CommandAuthority.GARRISON, ISSUER, "a wave joins lane " + number);
                }
            }
        }

        /**
         * Where a broken lane's gap is: where its survivors stand, or, with none, {@link #GAP_STANDOFF} back from its
         * aim toward its foot. Its aim on Broad Front is the bore itself, whose mouth no column walks into.
         */
        int[] gapCell(BattleSimulation sim) {
            int[] standing = standing(sim);
            if (standing != null) return standing;
            if (targetX < 0) return new int[]{entryX, entryY};
            float dx = entryX - targetX, dy = entryY - targetY, d = (float) Math.hypot(dx, dy);
            float t = d <= GAP_STANDOFF ? 1f : GAP_STANDOFF / d;
            return walkableNear(sim, Math.round(targetX + dx * t), Math.round(targetY + dy * t));
        }

        /** The gap this lane's next wave is to close, while the lane it is re-aimed at is still broken. */
        int[] gapToClose() {
            return reaimedTo != null && reaimedTo.broken ? gapAt : null;
        }

        /** Whether one of this lane's columns is already marching for {@code gap}. */
        boolean boundTo(int[] gap) {
            for (int[] dest : column.marching()) if (near(gap, dest[0], dest[1])) return true;
            return false;
        }

        /**
         * Another lane's re-aimed wave has come into this broken lane: its squads are this lane's, sent on to its aim,
         * and the lane is whole again, measured from what stands now.
         */
        void reinforce(BattleSimulation sim, List<Integer> joined) {
            if (squads == null) squads = new ArrayList<>();
            int alive = 0;
            for (int squad : squads) alive += sim.squadMemberCount(squad);
            for (int squad : joined) alive += sim.squadMemberCount(squad);
            squads.addAll(joined);
            joinedMembers = alive;
            broken = false;
            for (int squad : joined) {
                int[] at = targetX >= 0 ? new int[]{targetX, targetY} : new int[]{entryX, entryY};
                sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, at[0], at[1]),
                        CommandAuthority.GARRISON, ISSUER, "a wave closes lane " + number + "'s gap");
            }
        }
    }

    private final List<Lane> lanes = new ArrayList<>();
    /** Broken lanes a re-aimed wave has closed since the beats last went out. */
    private final List<String> closedGaps = new ArrayList<>();
    /** Beats the lanes' orders raised between ticks, for the next tick's beats. */
    private final List<String> laneBeats = new ArrayList<>();
    private BarrageScreen screen;
    /** Where scheduled armour sets out from; {@code null} sets it down at its own lane's foot. */
    private AlliedDepot depot;
    /** Which way the lanes advance along their axis, +1 or -1, for armour that follows no barrage. */
    private int advanceSign;
    /** How far behind its lane's aim the armour stands; negative stands it ahead, leading its infantry. */
    private int armourBehind = ARMOUR_BEHIND;

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

    /**
     * Adds armour-only ground beside the company: no columns of its own, so it raises no lane beats; its
     * machines are stationed on its ground's target as any lane's are. Returns its index for {@link #addArmour}.
     */
    public int addSupport(HeartLane ground, int[] entry, long seed) {
        lanes.add(new Lane(lanes.size() + 1, ground, entry[0], entry[1], 0, new int[0], RiskLevel.MEDIUM, new Random(seed)));
        return lanes.size() - 1;
    }

    /** Stands the lanes' armour {@code cells} behind their aim, or ahead of it when negative. */
    public HeartLanes armourBehind(int cells) {
        armourBehind = cells;
        return this;
    }

    /** Lanes that advance toward higher ({@code +1}) or lower ({@code -1}) cells, so armour without a barrage stands behind its aim. */
    public HeartLanes advancing(int sign) {
        advanceSign = sign;
        return this;
    }

    /** Columns that follow a walking barrage: short of the firing line while their bore lies past it. */
    public HeartLanes following(BarrageScreen screen) {
        this.screen = screen;
        return this;
    }

    /**
     * Scheduled armour sets out from {@code depot} rather than its lane's foot, and waits while the depot cannot
     * send; it then drives up to its lane like the rest of the lane's armour.
     */
    public HeartLanes sentFrom(AlliedDepot depot) {
        this.depot = depot;
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
        // A formation drives the allied command, so it moves before the command carries its orders out.
        for (Lane lane : lanes) if (lane.advance != null && lane.advance.active()) lane.advance.advance(sim);
        allied.advance(sim);
        boolean refreshed = false;
        for (var due = armourDue.iterator(); due.hasNext(); ) {
            int[] a = due.next();
            if (a[0] > tick || byNeed()) continue;
            int[] from = depot == null ? new int[]{a[3], a[4]} : depot.dispatchCell(sim);
            if (from == null) continue;
            due.remove();
            // Later armour keeps clear of the machines standing now, not of where the first ones were set down.
            if (!refreshed) {
                refreshed = true;
                placed.clear();
                for (Lane lane : lanes) {
                    for (long hull : lane.hulls) if (sim.convoy().structure(hull) > 0f)
                        placed.add(new float[]{sim.convoy().body(hull).x, sim.convoy().body(hull).y});
                    for (long mech : lane.mechs) if (sim.resolveUnit(mech) != 0L && sim.world().isAlive(mech))
                        placed.add(new float[]{sim.world().x(mech), sim.world().y(mech)});
                }
            }
            addArmour(sim, a[1], a[2] < 0 ? null : VehicleType.values()[a[2]], from[0], from[1], a[5]);
        }
        road.tick(sim, beats);
        if (byNeed()) {
            shed.advance(sim);
            if (tick % RETARGET_TICKS == 0) {
                for (Lane lane : lanes) callArmour(sim, lane, beats);
                gunship.call(sim, shed, this, beats);
                reaim(sim, beats);
            }
        }
        if (tick % RETARGET_TICKS == 0) sampleMouths(sim);
        for (Lane lane : lanes) {
            boolean formed = lane.squads != null && !lane.squads.isEmpty() && formation(sim, lane, tick);
            boolean assaulting = !formed && assault(sim, lane, tick);
            if (tick % RETARGET_TICKS == 0 && !formed && !assaulting) driveArmour(sim, lane);
            lane.column.advance(sim, tick, lane, (beat, observerOnly) -> {
                if (beat.equals("column_arrived_1")) beats.add("lane_arrived_" + lane.number);
            });
            // A column stepping off for a gap says so the tick it does.
            beats.addAll(laneBeats);
            laneBeats.clear();
            if (lane.squads == null) continue;
            lane.squads.removeIf(squad -> sim.squadMemberCount(squad) == 0);
            int alive = 0;
            for (int squad : lane.squads) alive += sim.squadMemberCount(squad);
            if (!lane.broken && broken(alive, lane.joinedMembers)) {
                lane.broken = true;
                beats.add("lane_broken_" + lane.number);
            }
            if (formed || assaulting || lane.squads.isEmpty() || tick % RETARGET_TICKS != 0) continue;
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

    /**
     * Whether the lanes attack each bore in phases (`heart-bore-assault.md`): machines on a stand-off ring, militia on
     * a support line going in on a spent surge. On The Burn it takes a lane once the guns allow; on Broad Front where
     * the lane's formation rallies, on the bore's support line. Removal condition: the owner has seen it on both forms
     * and their evidence has been read; {@code false} is the control, armour posted by the lane's aim (ahead of it on
     * The Burn, behind it on Broad Front), formations rallying on the bore and militia attack-moving onto it.
     */
    static final boolean BORE_ASSAULT = Boolean.parseBoolean(System.getProperty("battle.heart.boreAssault", "true"));

    /** What the lanes' assaults did, for the evidence (`heart-bore-assault.md`, Acceptance). */
    private int assaultsBegun, boresFallen, goneInOnSurge, goneInOnQuiet, fallenBack;
    private int machineSeconds, machineSecondsInMouth;

    /**
     * The lane's assault on its bore this tick, begun where the guns allow it; whether the assault has the lane. Runs
     * its decisions once a second, as the lanes' other orders do.
     */
    private boolean assault(BattleSimulation sim, Lane lane, int tick) {
        if (!BORE_ASSAULT) return false;
        if (tick % RETARGET_TICKS != 0) return lane.assault != null;
        SwarmBore bore = lane.assault == null ? null : bore(sim, lane.assault.boreKey);
        if (lane.assault != null && (bore == null || bore.collapsed())) {
            boresFallen++;
            lane.assault = null;
            lane.afterFall = true;
        }
        if (lane.assault == null) {
            int[] from = lanePosition(sim, lane);
            // The bore the lane was sent for (Broad Front's formation brought it there), else the nearest of its own.
            KnownBore next = sentFor(sim, lane);
            if (next == null) next = lane.ground.target(known(sim), from[0], from[1]);
            // The guns are still on it: the lane follows the barrage as before.
            if (next == null || screen != null && screen.beyond(screen.along(next.x(), next.y()), tick)) return false;
            bore = bore(sim, next.key());
            if (bore == null) return false;
            BoreAssault begun = new BoreAssault(next.key(), next.x(), next.y(), from[0] - next.x(), from[1] - next.y(),
                    lane.afterFall, bore.surges(), tick);
            int[] support = BoreAssault.supportCell(begun.boreX, begun.boreY, begun.nearX, begun.nearY);
            support = walkableNear(sim, support[0], support[1]);
            begun.supportX = support[0];
            begun.supportY = support[1];
            if (!begun.armourFirst) begun.enter(BoreAssault.Phase.BASE_OF_FIRE, tick);
            lane.assault = begun;
            lane.afterFall = false;
            lane.assaultOrdered.clear();
            lane.targetX = next.x();
            lane.targetY = next.y();
            assaultsBegun++;
        }
        stepAssault(sim, lane, bore, tick);
        return true;
    }

    private void stepAssault(BattleSimulation sim, Lane lane, SwarmBore bore, int tick) {
        BoreAssault a = lane.assault;
        List<int[]> posts = postMachines(sim, lane, a);
        boolean surged = a.surged(bore.surges(), tick);
        float sincePhase = (tick - a.phaseTick) * BattleSimulation.TICK_DT;
        boolean squadsLeft = lane.squads != null && !lane.squads.isEmpty();
        switch (a.phase) {
            case MOVING_UP -> {
                if (machinesSet(sim, lane, posts) || sincePhase >= BoreAssault.ARMOUR_FIRST_SECONDS) {
                    enter(lane, BoreAssault.Phase.BASE_OF_FIRE, tick);
                }
            }
            case BASE_OF_FIRE, FALLING_BACK -> {
                float sinceSurge = a.lastSurgeTick < 0 ? -1f : (tick - a.lastSurgeTick) * BattleSimulation.TICK_DT;
                float sinceStart = (tick - a.startTick) * BattleSimulation.TICK_DT;
                if (squadsLeft && !surged && BoreAssault.spent(sinceSurge, sinceStart, mouthBodies(sim, bore))) {
                    if (a.lastSurgeTick >= 0 && sinceSurge < BoreAssault.QUIET_SECONDS) goneInOnSurge++;
                    else goneInOnQuiet++;
                    enter(lane, BoreAssault.Phase.ASSAULT, tick);
                }
            }
            case ASSAULT -> {
                if (surged) {
                    fallenBack++;
                    enter(lane, BoreAssault.Phase.FALLING_BACK, tick);
                }
            }
        }
        orderSquads(sim, lane, a);
    }

    private void enter(Lane lane, BoreAssault.Phase phase, int tick) {
        lane.assault.enter(phase, tick);
        lane.assaultOrdered.clear();
    }

    /** Gives each of the lane's squads not yet given it the current phase's order. */
    private void orderSquads(BattleSimulation sim, Lane lane, BoreAssault a) {
        if (lane.squads == null) return;
        for (int squad : lane.squads) {
            if (!lane.assaultOrdered.add(squad)) continue;
            ObjectiveAssignment order;
            String why;
            switch (a.phase) {
                case MOVING_UP -> {
                    int[] at = squadCell(sim, squad);
                    if (at == null) { lane.assaultOrdered.remove(squad); continue; }
                    order = ObjectiveAssignment.defendArea(squad, at[0], at[1]);
                    why = "lane " + lane.number + " waits for its armour on " + a.boreKey;
                }
                case ASSAULT -> {
                    order = ObjectiveAssignment.attackMove(squad, a.boreX, a.boreY);
                    why = "lane " + lane.number + " goes in on " + a.boreKey + " behind its surge";
                }
                default -> {
                    order = ObjectiveAssignment.defendArea(squad, a.supportX, a.supportY);
                    why = "lane " + lane.number + " holds its support line on " + a.boreKey;
                }
            }
            sim.assignSquadCommand(order, CommandAuthority.GARRISON, ISSUER, why);
        }
    }

    /**
     * Posts each of the lane's machines on its stand-off ring and has the allied command station it there, facing the
     * bore; returns the posts, in machine order.
     */
    private List<int[]> postMachines(BattleSimulation sim, Lane lane, BoreAssault a) {
        List<int[]> taken = new ArrayList<>();
        int count = 0;
        for (long hull : lane.hulls) if (sim.convoy().structure(hull) > 0f) count++;
        for (long mech : lane.mechs) if (sim.resolveUnit(mech) != 0L && sim.world().isAlive(mech)) count++;
        int i = 0;
        for (long hull : lane.hulls) {
            if (sim.convoy().structure(hull) <= 0f) continue;
            VehicleType type = sim.convoy().vehicleType(hull);
            int[] post = post(sim, a, clearance(sim, type), BoreAssault.ringFor(reach(type)), i++, count, taken);
            taken.add(post);
            allied.station(sim, hull, post[0], post[1], a.boreX - post[0], a.boreY - post[1]);
        }
        // A mech walks, but its post wants ground an APC's width would pass, so it is not wedged among the trees.
        for (long mech : lane.mechs) {
            if (sim.resolveUnit(mech) == 0L || !sim.world().isAlive(mech)) continue;
            int[] post = post(sim, a, clearance(sim, VehicleType.HEAVY_APC), BoreAssault.ringFor(0f), i++, count, taken);
            taken.add(post);
            allied.stationMech(sim, mech, post[0], post[1]);
        }
        return taken;
    }

    /** A machine's ring post, or, where the arc has none, a cell on the support line beside the others. */
    private int[] post(BattleSimulation sim, BoreAssault a, VehicleClearance clearance, int ring, int i, int count,
                       List<int[]> taken) {
        int[] post = BoreAssault.ringPost(sim.getGrid(), clearance, a.boreX, a.boreY, a.nearX, a.nearY, ring,
                BoreAssault.slotDegrees(i, count), taken, ARMOUR_SPACING);
        if (post != null) return post;
        int across = Math.round((i - (count - 1) / 2f) * ARMOUR_SPACING);
        return walkableNear(sim, Math.round(a.supportX - a.nearY * across), Math.round(a.supportY + a.nearX * across));
    }

    private static float reach(VehicleType type) {
        if (type == null || !type.hasTurretWeapon()) return 0f;
        var structure = type.turretStructure();
        return structure == null || structure.mount == null || structure.mount.weapon == null
                ? 0f : structure.mount.weapon.range;
    }

    /** Whether every living machine of the lane stands within {@link BoreAssault#SET_CELLS} of its post. */
    private boolean machinesSet(BattleSimulation sim, Lane lane, List<int[]> posts) {
        int i = 0;
        for (long hull : lane.hulls) {
            if (sim.convoy().structure(hull) <= 0f) continue;
            int[] post = posts.get(i++);
            var body = sim.convoy().body(hull);
            if (Math.max(Math.abs(body.x - post[0]), Math.abs(body.y - post[1])) > BoreAssault.SET_CELLS) return false;
        }
        for (long mech : lane.mechs) {
            if (sim.resolveUnit(mech) == 0L || !sim.world().isAlive(mech)) continue;
            int[] post = posts.get(i++);
            if (Math.max(Math.abs(sim.world().x(mech) - post[0]), Math.abs(sim.world().y(mech) - post[1]))
                    > BoreAssault.SET_CELLS) return false;
        }
        return true;
    }

    /** Where the lane is: the middle of its militia and machines, else its foot. */
    private int[] lanePosition(BattleSimulation sim, Lane lane) {
        List<Long> hulls = new ArrayList<>();
        for (long hull : lane.hulls) if (sim.convoy().structure(hull) > 0f) hulls.add(hull);
        int[] body = lane.squads == null ? null : laneBody(sim, lane, hulls);
        if (body == null && !hulls.isEmpty()) {
            var first = sim.convoy().body(hulls.get(0));
            body = new int[]{(int) first.x, (int) first.y};
        }
        return body != null ? body : new int[]{lane.entryX, lane.entryY};
    }

    private static int[] squadCell(BattleSimulation sim, int squad) {
        long leader = sim.getSquad(squad) == null ? 0L : sim.getSquad(squad).leaderId;
        if (leader == 0L || sim.resolveUnit(leader) == 0L || !sim.world().isAlive(leader)) return null;
        return walkableNear(sim, (int) sim.world().x(leader), (int) sim.world().y(leader));
    }

    /** The swarm's fighting bodies within the bore's mouth: its structures and workers not counted. */
    private static int mouthBodies(BattleSimulation sim, SwarmBore bore) {
        int n = 0;
        for (int i = 0, count = sim.liveUnitCount(); i < count; i++) {
            long unit = sim.liveUnitAt(i);
            UnitType type = sim.identity().type(unit);
            if (!type.isSwarm() || type.isSwarmWorker() || type.isTurret() || !sim.world().isAlive(unit)) continue;
            if (Math.hypot(sim.world().x(unit) - bore.x(), sim.world().y(unit) - bore.y()) <= BoreAssault.MOUTH_CELLS) n++;
        }
        return n;
    }

    /** Counts, once a second, the lanes' machines standing inside a living bore's mouth. */
    private void sampleMouths(BattleSimulation sim) {
        for (Lane lane : lanes) {
            for (long hull : lane.hulls) {
                if (sim.convoy().structure(hull) <= 0f) continue;
                machineSeconds++;
                var body = sim.convoy().body(hull);
                for (SwarmBore bore : sim.swarmBores().bores()) {
                    if (bore.collapsed()) continue;
                    if (Math.hypot(body.x - bore.x(), body.y - bore.y()) <= BoreAssault.MOUTH_CELLS) {
                        machineSecondsInMouth++;
                        break;
                    }
                }
            }
        }
    }

    /** The living bore at the lane's target, or {@code null} when its target is no bore (a barrage hold) or has fallen. */
    private static KnownBore sentFor(BattleSimulation sim, Lane lane) {
        if (lane.targetX < 0) return null;
        for (KnownBore bore : known(sim)) {
            if (bore.living() && bore.x() == lane.targetX && bore.y() == lane.targetY) return bore;
        }
        return null;
    }

    private static SwarmBore bore(BattleSimulation sim, String key) {
        for (SwarmBore bore : sim.swarmBores().bores()) if (bore.beatKey().equals(key)) return bore;
        return null;
    }

    /** What the lanes' assaults did, one line for the evidence summary. */
    public String assaultLine() {
        return assaultsBegun + " begun, " + boresFallen + " bores fallen to them, " + goneInOnSurge
                + " gone in on a spent surge, " + goneInOnQuiet + " on a quiet mouth, " + fallenBack
                + " fallen back; machines in a living mouth " + machineSecondsInMouth + " of " + machineSeconds
                + " machine-seconds";
    }

    /**
     * Whether Broad Front's lanes go on to each next bore as one formation (`heart-allied-commander.md`): militia
     * and armour as a screened advance, the screen picked by the country. Removal condition: the owner's
     * {@code heart-lane-formation} pick; {@code false} is the control, every squad and hull straight to the bore.
     */
    static final boolean LANE_FORMATION = Boolean.parseBoolean(System.getProperty("battle.heart.laneFormation", "true"));
    /** Share of the way to the next bore a tank can drive for the armour to screen the militia. */
    static final float ARMOUR_LEADS_OPEN = 0.85f;
    /** A swarm contact never lets up: a lane faces it this long before it pushes through. */
    static final float LANE_CONTACT_HOLD_SECONDS = 20f;

    /**
     * Broad Front's lane between bores: when the bore it was on is down, the lane forms up where it stands and goes
     * on to the next as a screened advance, armour screening across open ground and militia screening through woods
     * and streets. Returns whether the formation has the lane this tick; once it has rallied on the bore, the lane
     * attacks it as before.
     */
    private boolean formation(BattleSimulation sim, Lane lane, int tick) {
        if (!LANE_FORMATION || screen != null) return false;
        if (tick % RETARGET_TICKS == 0 && (lane.advance == null || !lane.advance.active())
                && (lane.targetX < 0 || !livingBoreAt(sim, lane.targetX, lane.targetY))) {
            KnownBore next = lane.ground.target(known(sim), lane.targetX, lane.targetY);
            if (next != null) {
                int[] aim = aim(sim, lane.ground, next);
                if (aim[0] != lane.targetX || aim[1] != lane.targetY) {
                    lane.targetX = aim[0];
                    lane.targetY = aim[1];
                    lane.advance = form(sim, lane, aim);
                    if (lane.advance == null && !BORE_ASSAULT) {
                        for (int squad : lane.squads) {
                            sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, aim[0], aim[1]),
                                    CommandAuthority.GARRISON, ISSUER, "lane " + lane.number + " goes on to " + next.key());
                        }
                    }
                }
            }
        }
        ScreenedAdvance advance = lane.advance;
        if (advance == null) return false;
        if (!advance.active()) {
            // Rallied on the bore (or halted): the lane attacks it from where the formation brought it, by assault
            // where the bore assault is on (the lane's next tick hands it over), else straight in.
            lane.advance = null;
            if (BORE_ASSAULT) return false;
            for (int squad : lane.squads) {
                sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, lane.targetX, lane.targetY),
                        CommandAuthority.GARRISON, ISSUER, "lane " + lane.number + " attacks its bore");
            }
            return false;
        }
        for (int squad : lane.squads) if (lane.formedSquads.add(squad)) advance.joinInfantry(squad, lane.armourLeads);
        for (long hull : lane.hulls) if (lane.formedMachines.add(hull)) advance.joinMachine(hull, false, lane.armourLeads);
        for (long mech : lane.mechs) if (lane.formedMachines.add(mech)) advance.joinMachine(mech, true, lane.armourLeads);
        return true;
    }

    /** The lane's formation from where it stands to {@code aim}, or {@code null} when it has no armour to form with. */
    private ScreenedAdvance form(BattleSimulation sim, Lane lane, int[] aim) {
        List<Long> hulls = new ArrayList<>(), mechs = new ArrayList<>();
        for (long hull : lane.hulls) if (sim.convoy().structure(hull) > 0f) hulls.add(hull);
        for (long mech : lane.mechs) if (sim.resolveUnit(mech) != 0L && sim.world().isAlive(mech)) mechs.add(mech);
        if (hulls.isEmpty() && mechs.isEmpty()) return null;
        int[] from = laneBody(sim, lane, hulls);
        if (from == null) return null;
        lane.armourLeads = openShare(sim, from, aim) >= ARMOUR_LEADS_OPEN;
        // The formation rallies on the bore's support line, not on the bore: its mouth is the swarm's ground, and the
        // assault goes on from there. A lane already that close has no advance to make.
        int[] rally = BORE_ASSAULT ? rallyShort(sim, from, aim) : aim;
        if (rally == null) return null;
        lane.formedSquads.clear();
        lane.formedMachines.clear();
        lane.formedSquads.addAll(lane.squads);
        lane.formedMachines.addAll(hulls);
        lane.formedMachines.addAll(mechs);
        ScreenedAdvance.Element infantry = new ScreenedAdvance.Element().squads(lane.squads);
        ScreenedAdvance.Element armour = new ScreenedAdvance.Element().hulls(hulls).mechs(mechs);
        return ScreenedAdvance.builder("lane-" + lane.number, ISSUER, allied,
                        AdvanceRoute.straight(from[0], from[1], rally[0], rally[1]))
                .screen(lane.armourLeads ? armour : infantry)
                .main(lane.armourLeads ? infantry : armour)
                .side(Faction.ALLY)
                .frontage(2f * lane.ground.halfWidth())
                .startWhen(ScreenedAdvance.Trigger.now())
                .onStalled(ScreenedAdvance.Stalled.PUSH_THROUGH)
                .onScreenBroken(ScreenedAdvance.Broken.MAIN_LEADS)
                .contactTimes(ScreenedAdvance.CONTACT_CLEAR_SECONDS, LANE_CONTACT_HOLD_SECONDS)
                .beats(key -> lane.pushesRunning = pushesAfter(lane.pushesRunning, key))
                .build();
    }

    /** {@link BoreAssault#rallyCell} on walkable ground. */
    private static int[] rallyShort(BattleSimulation sim, int[] from, int[] bore) {
        int[] cell = BoreAssault.rallyCell(from[0], from[1], bore[0], bore[1]);
        return cell == null ? null : walkableNear(sim, cell[0], cell[1]);
    }

    /** The middle of the lane's squad leaders and hulls: where its formation forms up. */
    private static int[] laneBody(BattleSimulation sim, Lane lane, List<Long> hulls) {
        float sx = 0, sy = 0;
        int n = 0;
        for (int squad : lane.squads) {
            long leader = sim.getSquad(squad).leaderId;
            if (leader == 0L || sim.resolveUnit(leader) == 0L || !sim.world().isAlive(leader)) continue;
            sx += sim.world().x(leader);
            sy += sim.world().y(leader);
            n++;
        }
        for (long hull : hulls) {
            sx += sim.convoy().body(hull).x;
            sy += sim.convoy().body(hull).y;
            n++;
        }
        return n == 0 ? null : new int[]{(int) (sx / n), (int) (sy / n)};
    }

    /** Share of the straight way from {@code from} to {@code to} a tank can drive: how open the country is. */
    private float openShare(BattleSimulation sim, int[] from, int[] to) {
        VehicleClearance clearance = clearance(sim, VehicleType.BREAKTHROUGH_TANK);
        int steps = Math.max(1, Math.max(Math.abs(to[0] - from[0]), Math.abs(to[1] - from[1]))), open = 0;
        for (int i = 0; i <= steps; i++) {
            int x = from[0] + Math.round((to[0] - from[0]) * (float) i / steps);
            int y = from[1] + Math.round((to[1] - from[1]) * (float) i / steps);
            if (clearance.isPassable(x, y)) open++;
        }
        return open / (float) (steps + 1);
    }

    /**
     * Whether a lane with a tool shed calls its armour up by need rather than on the waves' timetable
     * (`heart-allied-commander.md`): the shed's stock replaces the waves. Removal condition: the first played
     * comparison of the two at the owner's pick of the allied tool (`heart-allied-tool`); {@code false} is the
     * control, the timetable.
     */
    static final boolean ARMOUR_BY_NEED = Boolean.parseBoolean(System.getProperty("battle.heart.armourByNeed", "true"));
    /** A machine this close to its lane's aim has arrived, and is off the road. */
    static final int ARRIVED_CELLS = 20;

    /** The base the lanes call their armour from, or {@code null} when their waves keep the timetable. */
    private AlliedToolShed shed;
    /** Whether an uncovered lane asks for a mech lance before an armour pair: built-up country, where hulls are roadbound. */
    private boolean mechsFirst;
    /** The commander's gunship on call, and the shoots that open the air for it. */
    private final HeartGunship gunship = new HeartGunship();

    /** The commander's gunship on call: its maker and gate stock the shed. */
    public HeartGunship gunship() { return gunship; }
    /** The supply road the base's armour and ammunition come up (F6). */
    private HeartSupplyRoad road = new HeartSupplyRoad(List.of());

    /** The grounds of the lanes that take waves, in lane order: the allied lanes beside the company's. */
    public List<HeartLane> waveGrounds() {
        List<HeartLane> out = new ArrayList<>();
        for (Lane lane : lanes) if (lane.teams > 0) out.add(lane.ground);
        return out;
    }

    /** Each lane as it stands, for a probe: its aim, militia, formation and marching columns. */
    public List<String> describe(BattleSimulation sim) {
        List<String> out = new ArrayList<>();
        out.add("map " + sim.getGrid().getWidth() + "x" + sim.getGrid().getHeight());
        if (companySector != null) out.add("company sector " + companySector.across() + " +/- " + companySector.halfWidth());
        for (Lane lane : lanes) {
            out.add("ground " + lane.ground.across() + " +/- " + lane.ground.halfWidth());
            int alive = 0;
            if (lane.squads != null) for (int squad : lane.squads) alive += sim.squadMemberCount(squad);
            out.add("lane " + lane.number + " aim " + lane.targetX + "," + lane.targetY + (lane.broken ? " broken" : "")
                    + " militia " + alive + "/" + lane.joinedMembers
                    + (lane.advance == null ? "" : " formation " + lane.advance.describe(sim)));
            for (String column : lane.column.describe(sim)) out.add("  " + column);
        }
        return out;
    }

    /** Lays the supply road from its cells: its cuts hold back the base's restock and give the gunship its rung 3. */
    public HeartLanes road(List<int[]> cells) {
        road = new HeartSupplyRoad(cells);
        gunship.road(road);
        return this;
    }

    public HeartSupplyRoad road() { return road; }

    /** Where lane {@code number}'s waves step off, or {@code null} for no such lane. */
    public int[] entry(int number) {
        for (Lane lane : lanes) if (lane.number == number) return new int[]{lane.entryX, lane.entryY};
        return null;
    }

    /** In built-up country an uncovered lane asks the base for a mech lance first, and an armour pair if none is free. */
    public HeartLanes mechsFirst(boolean first) {
        this.mechsFirst = first;
        return this;
    }

    /** Brings a pair of Hounds on for the lane a request names, joining the lane's walkers. */
    public AlliedToolShed.Maker mechMaker() {
        return (sim, from, request) -> {
            Lane lane = laneFor(request);
            if (lane == null || from == null) return null;
            int index = lanes.indexOf(lane);
            int mechs = lane.mechs.size();
            addArmour(sim, index, null, from[0], from[1], 0f);
            addArmour(sim, index, null, from[0], from[1], 0f);
            if (lane.mechs.size() == mechs) return null;
            List<Long> sent = new ArrayList<>(lane.mechs.subList(mechs, lane.mechs.size()));
            return s -> {
                for (long id : sent) if (onTheRoad(s, lane, id)) return false;
                return true;
            };
        };
    }

    private Lane laneFor(AlliedRequest request) {
        for (Lane l : lanes) if (place(l.number).equals(request.toPlace())) return l;
        return null;
    }

    /** Where every lane machine stands now, hulls and walkers. */
    List<float[]> machinePositions(BattleSimulation sim) {
        List<float[]> out = new ArrayList<>();
        for (Lane lane : lanes) {
            for (long hull : lane.hulls) if (sim.convoy().structure(hull) > 0f)
                out.add(new float[]{sim.convoy().body(hull).x, sim.convoy().body(hull).y});
            for (long mech : lane.mechs) if (sim.resolveUnit(mech) != 0L && sim.world().isAlive(mech))
                out.add(new float[]{sim.world().x(mech), sim.world().y(mech)});
        }
        return out;
    }

    /** The bore the most stalled lanes are aimed at, or {@code null} when no lane is stalling. */
    int[] stalledLaneTarget() {
        int[] best = null;
        int most = 0;
        for (Lane lane : lanes) {
            if (lane.pushesRunning == 0 || lane.targetX < 0) continue;
            int n = 0;
            for (Lane other : lanes) if (other.pushesRunning > 0 && other.targetX == lane.targetX && other.targetY == lane.targetY) n++;
            if (n > most) {
                most = n;
                best = new int[]{lane.targetX, lane.targetY};
            }
        }
        return best;
    }

    /** Whether the company's middle stands inside a lane's band. */
    private static boolean companyIn(BattleSimulation sim, HeartLane ground) {
        float[] company = HeartObjectives.companyCentre(sim);
        return company != null && inBand(ground, company[0], company[1]);
    }

    /** Whether {@code (x, y)} lies inside the band {@code ground} runs along. */
    static boolean inBand(HeartLane ground, float x, float y) {
        float across = ground.advancesAlongY() ? x : y;
        return Math.abs(across - ground.across()) <= ground.halfWidth();
    }

    /** The bore the first broken lane is aimed at, or {@code null} when no lane is broken. */
    int[] brokenLaneTarget() {
        for (Lane lane : lanes) if (lane.broken && lane.targetX >= 0) return new int[]{lane.targetX, lane.targetY};
        return null;
    }

    /**
     * The lanes call their armour from {@code shed} by need (`allied-tools.md`), and the timed waves no longer come.
     * Stock the shed's armour pairs with {@link #armourMaker()}, so the machines it sends join their lane.
     */
    public HeartLanes calledFrom(AlliedToolShed shed) {
        this.shed = shed;
        return this;
    }

    /** Whether the lanes call their tools from the shed by need, and so advance it each tick. */
    public boolean callsByNeed() {
        return byNeed();
    }

    private boolean byNeed() {
        return ARMOUR_BY_NEED && shed != null;
    }

    /** The place a lane's requests name: {@code lane-<number>}. */
    static String place(int number) {
        return "lane-" + number;
    }

    /**
     * Brings an armour pair on for the lane a request names: a breakthrough tank and a technical set down from the
     * depot cell, joining the lane's armour so it is driven with the rest; on the road until both are within
     * {@link #ARRIVED_CELLS} of the lane's aim, or gone.
     */
    public AlliedToolShed.Maker armourMaker() {
        return (sim, from, request) -> {
            Lane lane = laneFor(request);
            if (lane == null || from == null) return null;
            int index = lanes.indexOf(lane);
            int hulls = lane.hulls.size();
            float facing = advanceFacing(lane.ground.advancesAlongY(), advanceSign);
            addArmour(sim, index, VehicleType.BREAKTHROUGH_TANK, from[0], from[1], facing);
            addArmour(sim, index, VehicleType.MILITIA_TECHNICAL, from[0], from[1], facing);
            if (lane.hulls.size() == hulls) return null;
            List<Long> sent = new ArrayList<>(lane.hulls.subList(hulls, lane.hulls.size()));
            Lane to = lane;
            return s -> {
                for (long id : sent) if (onTheRoad(s, to, id)) return false;
                return true;
            };
        };
    }

    /** Contacts pushed through in a row that make a lane <b>stalled</b>. */
    static final int STALL_PUSHES = 2;

    /**
     * The lane's run of contacts pushed through at the cap after one of its formation's beats: a push adds one,
     * a contact cleared, a rally or a new step-off ends the run.
     */
    static int pushesAfter(int running, String beat) {
        String key = beat.substring(beat.lastIndexOf('.') + 1);
        if (key.startsWith("pushed_")) return running + 1;
        if (key.startsWith("cleared_") || key.equals("rallied") || key.equals("step_off")) return 0;
        return running;
    }

    /**
     * The lane's call for armour, by need (the Cordon commander's signal names, `cordon-allied-command.md`): a lane
     * is <b>uncovered</b> when it has fewer machines standing than it has had, broken when its militia are, or
     * <b>stalled</b> when its formation has pushed through {@link #STALL_PUSHES} contacts in a row without clearing
     * one. It asks the shed for a pair, never with a pair of its own still on the road; the shed refuses while it
     * has none in stock or its depot cannot send.
     */
    private void callArmour(BattleSimulation sim, Lane lane, List<String> beats) {
        int standing = 0;
        for (long hull : lane.hulls) if (sim.convoy().structure(hull) > 0f) standing++;
        for (long mech : lane.mechs) if (sim.resolveUnit(mech) != 0L && sim.world().isAlive(mech)) standing++;
        lane.machinesHeld = Math.max(lane.machinesHeld, standing);
        boolean uncovered = standing < lane.machinesHeld;
        boolean stalled = lane.pushesRunning >= STALL_PUSHES;
        // The company has gone into a broken lane's gap: it is plugging it, and the commander does not send a wave on top.
        boolean plugged = lane.broken && !uncovered && !stalled && companyIn(sim, lane.ground);
        if (plugged) return;
        if (!(uncovered || lane.broken || stalled) || shed.onRoad(AlliedTool.ARMOUR_PAIR, place(lane.number)) > 0) return;
        int tx = lane.targetX >= 0 ? lane.targetX : lane.entryX, ty = lane.targetY >= 0 ? lane.targetY : lane.entryY;
        if (shed.onRoad(AlliedTool.MECH_LANCE, place(lane.number)) > 0) return;
        int before = lane.hulls.size() + lane.mechs.size();
        String reason = uncovered ? "uncovered" : lane.broken ? "broken" : "stalled";
        boolean granted = mechsFirst
                && shed.request(sim, new AlliedRequest(AlliedTool.MECH_LANCE, place(lane.number), tx, ty, reason));
        if (!granted) granted = shed.request(sim, new AlliedRequest(AlliedTool.ARMOUR_PAIR, place(lane.number), tx, ty, reason));
        if (!granted) return;
        // What the lane is to have once its call is up: the pair replaces what it lost.
        lane.machinesHeld = standing + lane.hulls.size() + lane.mechs.size() - before;
        lane.calls++;
        // The machines answer the stall; the next run of pushes is a new one.
        if (stalled) lane.pushesRunning = 0;
        beats.add("armour_called_" + lane.number + "_" + lane.calls);
        if (reason.equals("stalled")) beats.add("lane_stalled_" + lane.number);
    }

    /**
     * F2: a broken lane the company has not gone into gets the next wave of its nearest whole neighbour with waves to
     * come, re-aimed at its gap even on the march ({@code wave_reaimed_<lane>_from_<lane>}); once that wave is in, the
     * lane is whole again ({@code gap_closed_<lane>}). The company going into the gap calls the wave back to its own lane.
     */
    void reaim(BattleSimulation sim, List<String> beats) {
        for (Lane gap : lanes) {
            Lane sent = null;
            for (Lane lane : lanes) if (lane.reaimedTo == gap) sent = lane;
            if (!gap.broken || companyIn(sim, gap.ground)) {
                if (sent != null) {
                    sent.reaimedTo = null;
                    sent.gapAt = null;
                    if (gap.broken) beats.add("wave_recalled_" + gap.number + "_" + gap.reaims);
                }
                continue;
            }
            if (sent != null) continue;
            Lane nearest = null;
            for (Lane lane : lanes) {
                if (lane == gap || lane.broken || lane.reaimedTo != null || lane.column.remaining() == 0) continue;
                if (!neighbours(acrossOf(), wavesOf(), lanes.indexOf(lane), lanes.indexOf(gap))) continue;
                if (nearest == null || entryDistance(lane, gap) < entryDistance(nearest, gap)) nearest = lane;
            }
            if (nearest == null) continue;
            nearest.reaimedTo = gap;
            nearest.gapAt = gap.gapCell(sim);
            gap.reaims++;
            beats.add("wave_reaimed_" + gap.number + "_from_" + nearest.number + "_" + gap.reaims);
        }
        for (String closed : closedGaps) beats.add("gap_closed_" + closed);
        closedGaps.clear();
    }

    /**
     * Whether a lane standing at {@code alive} militia is broken: below a third of the {@code arrivedWith} its waves
     * brought in, counted as each arrived.
     */
    static boolean broken(int alive, int arrivedWith) {
        return alive * 3 < arrivedWith;
    }

    /** Where each lane lies across the front, in lane order. */
    private int[] acrossOf() {
        int[] out = new int[lanes.size()];
        for (int i = 0; i < out.length; i++) out[i] = lanes.get(i).ground.across();
        return out;
    }

    /** Whether each lane takes waves, in lane order: armour-only support ground ({@link #addSupport}) does not. */
    private boolean[] wavesOf() {
        boolean[] out = new boolean[lanes.size()];
        for (int i = 0; i < out.length; i++) out[i] = lanes.get(i).teams > 0;
        return out;
    }

    /**
     * Whether lane {@code from} is a neighbour of lane {@code gap} across the front: no other lane that takes waves
     * lies between them (support ground beside the company does not count). Only a neighbour's wave is re-aimed at
     * a gap; past it, the gap waits for the company or the next call, rather than a wave marching across the map.
     */
    static boolean neighbours(int[] across, boolean[] takesWaves, int from, int gap) {
        int low = Math.min(across[from], across[gap]), high = Math.max(across[from], across[gap]);
        for (int i = 0; i < across.length; i++) {
            if (i != from && i != gap && takesWaves[i] && across[i] > low && across[i] < high) return false;
        }
        return true;
    }

    /** How far {@code from}'s waves step off from {@code gap}'s aim. */
    private static double entryDistance(Lane from, Lane gap) {
        int[] at = gap.targetX >= 0 ? new int[]{gap.targetX, gap.targetY} : new int[]{gap.entryX, gap.entryY};
        return Math.hypot(from.entryX - at[0], from.entryY - at[1]);
    }

    /** The walkable cell nearest {@code (x, y)} within eight cells, or {@code (x, y)} itself. */
    static int[] walkableNear(BattleSimulation sim, int x, int y) {
        for (int r = 0; r <= 8; r++)
            for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++)
                if (sim.getGrid().inBounds(x + dx, y + dy) && sim.getGrid().isWalkable(x + dx, y + dy)) return new int[]{x + dx, y + dy};
        return new int[]{x, y};
    }

    /** Whether {@code (x, y)} is within {@link #REAIM_CELLS} of {@code cell}. */
    static boolean near(int[] cell, int x, int y) {
        return Math.max(Math.abs(cell[0] - x), Math.abs(cell[1] - y)) <= REAIM_CELLS;
    }

    /** Whether a machine sent to the lane is alive and still short of its lane's aim. */
    private static boolean onTheRoad(BattleSimulation sim, Lane lane, long id) {
        float x, y;
        if (sim.convoy().isVehicle(id)) {
            if (sim.convoy().structure(id) <= 0f || sim.convoy().body(id) == null) return false;
            x = sim.convoy().body(id).x;
            y = sim.convoy().body(id).y;
        } else {
            // A walker (a Hound of a mech lance).
            if (sim.resolveUnit(id) == 0L || !sim.world().isAlive(id)) return false;
            x = sim.world().x(id);
            y = sim.world().y(id);
        }
        int tx = lane.targetX >= 0 ? lane.targetX : lane.entryX, ty = lane.targetY >= 0 ? lane.targetY : lane.entryY;
        return Math.max(Math.abs(x - tx), Math.abs(y - ty)) > ARRIVED_CELLS;
    }

    /** Cells between neighbouring machines across a lane, and by default how far behind the lane's aim they stand. */
    static final int ARMOUR_SPACING = 5, ARMOUR_BEHIND = 4;

    /**
     * The chassis heading of the advance ({@code 0} is +Y, counter-clockwise): a hull is set down facing the way its
     * lane goes. Taken as a math-frame angle it pointed a hull side-on, into the woods or the wall beside a narrow
     * road, where a hull that turns on a car's circle has no room to come round.
     */
    public static float advanceFacing(boolean alongY, int sign) {
        int s = sign >= 0 ? 1 : -1;
        return alongY ? AirBody.facingToward(0f, s) : AirBody.facingToward(s, 0f);
    }

    /** Brings a machine on at {@code tick} as {@link #addArmour} sets one down; one with no ground near is dropped. */
    public void scheduleArmour(int tick, int index, VehicleType type, int x, int y, float facing) {
        armourDue.add(new int[]{tick, index, type == null ? -1 : type.ordinal(), x, y, Math.round(facing)});
    }

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
        // Ground it can set out from whichever way its route leaves; only where there is none near, ground it fits.
        int[] cell = null;
        for (int pass = 0; cell == null && pass < 2; pass++)
            for (int r = 0; cell == null && r <= 10; r++)
                for (int dy = -r; cell == null && dy <= r; dy++) for (int dx = -r; cell == null && dx <= r; dx++)
                    if (Math.max(Math.abs(dx), Math.abs(dy)) == r && clearance.isPassable(x + dx, y + dy)
                            && clearOfArmour(x + dx + .5f, y + dy + .5f)
                            && (pass == 1 || TurnAwareCorridor.canSetOutFrom(x + dx + .5f, y + dy + .5f, type, sim.getGrid())))
                        cell = new int[]{x + dx, y + dy};
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
        int toward = screen == null ? advanceSign : screen.sign();
        int count = lane.hulls.size() + lane.mechs.size(), i = 0;
        // The allied command drives each machine to its post and fights it from there; it re-orders only
        // when a post has moved, and pulls a goal the woods bar back toward the machine.
        // The line has moved: what could not be served on the old one may be on the new.
        if (lane.rankAim == null || Math.abs(lane.rankAim[0] - aim[0]) + Math.abs(lane.rankAim[1] - aim[1]) >= ARMOUR_SPACING) {
            lane.rankAim = aim;
            lane.rearRank.clear();
        }
        // The post faces the enemy: allied command keeps a hull on its line, never in front of it. One it cannot
        // serve a slot for goes to the second rank, deeper behind the line, rather than stand where it was refused.
        float aheadX = alongY ? 0f : toward, aheadY = alongY ? toward : 0f;
        for (long hull : lane.hulls) {
            int[] at = station(aim, alongY, toward, i++, count);
            if (sim.convoy().structure(hull) <= 0f) continue;
            if (allied.postUnservable(hull)) lane.rearRank.add(hull);
            if (lane.rearRank.contains(hull)) {
                at = alongY ? new int[]{at[0], at[1] - toward * ARMOUR_SPACING}
                        : new int[]{at[0] - toward * ARMOUR_SPACING, at[1]};
            }
            allied.station(sim, hull, at[0], at[1], aheadX, aheadY);
        }
        for (long mech : lane.mechs) {
            int[] at = station(aim, alongY, toward, i++, count);
            allied.stationMech(sim, mech, at[0], at[1]);
        }
    }

    private int[] station(int[] aim, boolean alongY, int toward, int i, int count) {
        int across = Math.round((i - (count - 1) / 2f) * ARMOUR_SPACING), back = -toward * armourBehind;
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
    /** Armour still to come, in the order it was scheduled: {@code [tick, lane, type ordinal or -1 for a Hound, x, y, facing]}. */
    private final List<int[]> armourDue = new ArrayList<>();

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
