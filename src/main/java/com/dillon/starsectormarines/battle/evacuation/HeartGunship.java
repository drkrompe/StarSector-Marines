package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.command.allied.AlliedRequest;
import com.dillon.starsectormarines.battle.command.allied.AlliedTool;
import com.dillon.starsectormarines.battle.command.allied.AlliedToolShed;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.List;

/**
 * The Broad Front's gunship on call (`heart-allied-commander.md`): the lanes' commander asks the logistics base for
 * one sortie at a time, only onto something worth it ({@link #target}), and risks it as its {@link Policy} says. A
 * sortie comes off station at half structure.
 *
 * <p>Where the policy wants the air cleared first, the base's guns shoot it open: a shoot sized to kill each spore
 * colony and outpost in the way, at most one every {@link #SHOOT_SECONDS}. Nothing else reaches those in a played
 * battle; the company and the lanes stop well short of the satellites.
 */
public final class HeartGunship {

    /** The place the gunship's requests name. */
    static final String PLACE = "air";
    /** Seconds a sortie works over its target. */
    static final float ORBIT_SECONDS = 150f;
    /** Cells from a lane's machine within which a Matriarch is on its armour. */
    static final float ON_ARMOUR_CELLS = 20f;
    /** Seconds between the base's shoots on the colonies keeping the air shut. */
    static final float SHOOT_SECONDS = 90f;
    /** The most shells a shoot puts on one target. */
    static final int MOST_SHELLS = 8;
    /** The shoot's shell: a field gun's high explosive, landing a second apart. */
    static final float SHELL_RADIUS = 2.5f, SHELL_DAMAGE = 80f, SHELL_PENETRATION = 20f, FIRST_FLIGHT = 3f;
    static final int SHELL_WALL_DAMAGE = 40;

    /**
     * How the commander risks its gunship, the owner's question {@code heart-gunship} (2026-10-24): every played
     * sortie had been shot down by spore anti-air. Removal condition: the owner's pick of the four; the others go,
     * with this switch.
     */
    public enum Policy {
        /** (a) Sent only where no standing spore anti-air, any bore's, reaches its target or its way there. */
        REACHABLE_ONLY,
        /** (b) Sent once the approach is clear, losses accepted: the behaviour the first played sorties had. */
        ACCEPT_LOSSES,
        /** (c) The base's guns kill the spore anti-air reaching the target and the way there, then the sortie goes. */
        CLEAR_PATH,
        /** (d) As (b), with a sortie {@link #TOUGHER} times as hard to bring down. */
        TOUGHER
    }

    /**
     * The policy every heart battle starts with: {@code -Dbattle.heart.gunshipPolicy=<name>}, see {@link Policy}.
     * {@link Policy#CLEAR_PATH} meanwhile: on the seed-2 town map to tick 16000 it flew three sorties and lost none,
     * where (b) lost both of its two.
     */
    static final Policy DEFAULT_POLICY = Policy.valueOf(System.getProperty("battle.heart.gunshipPolicy", "CLEAR_PATH"));
    /** How many times a sortie's structure and armour {@link Policy#TOUGHER} gives it. */
    static final float TOUGHER = 3f;
    /** Cells past a defence's own range the commander keeps its sortie. */
    static final float STANDOFF = 2f;

    private Policy policy = DEFAULT_POLICY;
    private List<int[]> road = List.of();
    private boolean[] roadMask;
    private int sorties;
    private int shoots;
    private int nextShoot;

    /** Sets how the commander risks its gunship (the look suite pictures each). */
    public HeartGunship policy(Policy policy) {
        this.policy = policy;
        return this;
    }

    public Policy policy() { return policy; }

    /** The supply road's cells, whose satellite is rung 3 (F6); none leaves the rung out. */
    public HeartGunship road(List<int[]> cells) {
        this.road = cells == null ? List.of() : cells;
        this.roadMask = null;
        return this;
    }

    /** The shed's gate on a sortie: the approach must be clear under (b) and (d); (a) and (c) judge each target instead. */
    public boolean gate(BattleSimulation sim) {
        return policy == Policy.REACHABLE_ONLY || policy == Policy.CLEAR_PATH || airOpen(sim);
    }

    /** Whether the air over the approach is open: no spore colony or outpost stands on the satellites nearest the company. */
    public static boolean airOpen(BattleSimulation sim) {
        for (SwarmBore bore : HeartObjectives.approachSatellites(sim)) {
            if (bore.collapsed()) continue;
            for (long colony : antiAir(bore)) {
                if (standing(sim, colony)) return false;
            }
        }
        return true;
    }

    /** Sets a sortie down over the request's cell, flying in and out over the depot; done once it is gone. */
    public AlliedToolShed.Maker maker() {
        return (sim, from, request) -> {
            if (from == null) return null;
            long craft = sim.spawnGunship(ShuttleType.MUDSKIPPER, Faction.ALLY, request.x() + .5f, request.y() + .5f,
                    from[0], from[1], from[0], from[1]);
            ShuttleMission sortie = sim.world().mission(craft);
            if (sortie == null) return null;
            sortie.orbitSecondsLeft = ORBIT_SECONDS;
            if (policy == Policy.TOUGHER) {
                sim.world().setMaxHp(craft, sim.world().maxHp(craft) * TOUGHER);
                sim.world().setHp(craft, sim.world().maxHp(craft));
                sim.world().setMaxArmor(craft, sim.world().maxArmor(craft) * TOUGHER);
                sim.world().setArmor(craft, sim.world().maxArmor(craft));
            }
            return s -> {
                if (sortie.destroyed || sortie.state == ShuttleState.GONE) return true;
                // Off station at half structure: it turns for home and the next sortie waits for the base.
                if (s.world().hp(craft) < s.world().maxHp(craft) * .5f) sortie.orbitSecondsLeft = 0f;
                return false;
            };
        };
    }

    /**
     * The commander's call: with no sortie up and something worth one, asks the shed for a sortie over it, adding
     * {@code gunship_called_<n>} to {@code beats} when it is granted; with the air shut, has the base shoot the air
     * open instead ({@code air_shoot_<n>}).
     */
    void call(BattleSimulation sim, AlliedToolShed shed, HeartLanes lanes, List<String> beats) {
        if (shed.onRoad(AlliedTool.GUNSHIP, PLACE) > 0) return;
        int[] target = target(sim, lanes);
        if (target == null) return;

        if (policy == Policy.REACHABLE_ONLY || policy == Policy.CLEAR_PATH) {
            int[] from = shed.dispatchCell(sim);
            if (from == null) return;
            List<Long> reaching = reaching(sim, from, target);
            // (a) waits for a target nothing standing covers; (c) has the base kill what covers this one.
            if (!reaching.isEmpty()) {
                if (policy == Policy.CLEAR_PATH) shoot(sim, reaching, beats);
                return;
            }
        } else if (!airOpen(sim)) {
            List<Long> approach = new ArrayList<>();
            for (SwarmBore bore : HeartObjectives.approachSatellites(sim)) approach.addAll(antiAir(bore));
            shoot(sim, approach, beats);
            return;
        }
        if (!shed.request(sim, new AlliedRequest(AlliedTool.GUNSHIP, PLACE, target[0], target[1], "target"))) return;
        sorties++;
        beats.add("gunship_called_" + sorties);
    }

    /** One shoot on {@code targets} at most every {@link #SHOOT_SECONDS}, marked {@code air_shoot_<n>}. */
    private void shoot(BattleSimulation sim, List<Long> targets, List<String> beats) {
        if (sim.getSimTickIndex() < nextShoot || shootAt(sim, targets) == 0) return;
        shoots++;
        nextShoot = sim.getSimTickIndex() + Math.round(SHOOT_SECONDS / BattleSimulation.TICK_DT);
        beats.add("air_shoot_" + shoots);
    }

    /**
     * Every standing spore colony and outpost, of any bore, whose own reach (plus {@link #STANDOFF}) covers the target
     * or the straight way to it from {@code from}.
     */
    static List<Long> reaching(BattleSimulation sim, int[] from, int[] target) {
        List<Long> out = new ArrayList<>();
        for (SwarmBore bore : sim.swarmBores().bores()) {
            for (long gun : antiAir(bore)) {
                if (!standing(sim, gun)) continue;
                float reach = sim.turretState().isTurret(gun) ? sim.turretState().weapon(gun).range : 0f;
                float d = segmentDistance(sim.world().x(gun), sim.world().y(gun), from[0], from[1], target[0], target[1]);
                if (d <= reach + STANDOFF) out.add(gun);
            }
        }
        return out;
    }

    /** Distance from {@code (px, py)} to the segment {@code (ax, ay)}-{@code (bx, by)}. */
    static float segmentDistance(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        float length2 = dx * dx + dy * dy;
        float t = length2 == 0f ? 0f : Math.max(0f, Math.min(1f, ((px - ax) * dx + (py - ay) * dy) / length2));
        return (float) Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    /** Puts on each standing one of {@code targets} the shells that kill it ({@link #shellsFor}); returns how many it shot at. */
    static int shootAt(BattleSimulation sim, List<Long> targets) {
        int shotAt = 0;
        for (long gun : targets) {
            if (!standing(sim, gun)) continue;
            float x = sim.world().x(gun), y = sim.world().y(gun);
            int shells = shellsFor(sim.world().hp(gun), sim.world().armor(gun));
            for (int i = 0; i < shells; i++) {
                PendingDetonation shell = new PendingDetonation(CombatTelemetryService.NO_ATTACKER, x, y,
                        FIRST_FLIGHT + i, SHELL_RADIUS, SHELL_DAMAGE, SHELL_PENETRATION, SHELL_WALL_DAMAGE,
                        Faction.ALLY, true);
                shell.asUnmarked();
                sim.getCommandPowerService().addFireMission(shell);
            }
            shotAt++;
        }
        return shotAt;
    }

    /**
     * Shells sized to kill a target of {@code hp} structure behind {@code armor}: enough to strip both at a shell's
     * damage, and one over, at most {@link #MOST_SHELLS}. A shoot that leaves the target standing opens nothing.
     */
    static int shellsFor(float hp, float armor) {
        return Math.min(MOST_SHELLS, (int) Math.ceil((hp + armor) / SHELL_DAMAGE) + 1);
    }

    /**
     * The most pressing target: a living Matriarch on a lane's armour, else the bore a broken lane is on, else the
     * satellite beside the supply road while its surge is on the road (F6), else the bore the most stalled lanes are on.
     * Rung 3 waits for the cut: a satellite merely standing beside the road is the company's objective, not a sortie's.
     */
    int[] target(BattleSimulation sim, HeartLanes lanes) {
        List<float[]> armour = lanes.machinePositions(sim);
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().type(unit) != UnitType.SWARM_MATRIARCH || !sim.world().isAlive(unit)) continue;
            float x = sim.world().x(unit), y = sim.world().y(unit);
            for (float[] m : armour) {
                if (Math.abs(m[0] - x) <= ON_ARMOUR_CELLS && Math.abs(m[1] - y) <= ON_ARMOUR_CELLS) {
                    return new int[]{(int) x, (int) y};
                }
            }
        }
        int[] broken = lanes.brokenLaneTarget();
        if (broken != null) return broken;
        SwarmBore cutter = roadSatellite(sim, road);
        if (cutter != null && !roadCut(sim)) cutter = null;
        return cutter != null ? new int[]{cutter.x(), cutter.y()} : lanes.stalledLaneTarget();
    }

    /** Whether a swarm body stands on the supply road now. */
    boolean roadCut(BattleSimulation sim) {
        if (road.isEmpty()) return false;
        int width = sim.getGrid().getWidth();
        if (roadMask == null) {
            roadMask = new boolean[width * sim.getGrid().getHeight()];
            for (int[] cell : road) roadMask[cell[1] * width + cell[0]] = true;
        }
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.DEFENDER || sim.identity().type(unit).isTurret()) continue;
            int x = (int) sim.world().x(unit), y = (int) sim.world().y(unit);
            if (x >= 0 && y >= 0 && x < width && y * width + x < roadMask.length && roadMask[y * width + x]) return true;
        }
        return false;
    }

    /** The standing satellite within {@link HeartBores#ROAD_REACH} of the road, the one cutting it; null when none is. */
    static SwarmBore roadSatellite(BattleSimulation sim, List<int[]> road) {
        SwarmBore nearest = road.isEmpty() ? null : HeartObjectives.nearestToRoad(sim, road);
        if (nearest == null) return null;
        for (int[] cell : road) {
            if (Math.hypot(cell[0] - nearest.x(), cell[1] - nearest.y()) <= HeartBores.ROAD_REACH) return nearest;
        }
        return null;
    }

    /**
     * What on a bore shoots at aircraft: its spore colonies and its spore outposts. The first played sortie flew into
     * an air the colonies alone called open and was shot down by two outposts of the bore it was sent to.
     */
    static List<Long> antiAir(SwarmBore bore) {
        List<Long> out = new ArrayList<>(bore.colonies());
        out.addAll(bore.outposts());
        return out;
    }

    private static boolean standing(BattleSimulation sim, long colony) {
        return sim.resolveUnit(colony) != 0L && sim.world().isAlive(colony);
    }
}
