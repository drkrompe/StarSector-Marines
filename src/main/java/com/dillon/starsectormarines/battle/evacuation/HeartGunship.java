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
import com.dillon.starsectormarines.battle.squad.BeliefSource;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Broad Front's gunship on call (`heart-allied-commander.md`): the lanes' commander asks the logistics base for
 * one sortie at a time, only onto something worth it ({@link #target}), and flies it when called, losses accepted. A
 * sortie comes off station at half structure.
 *
 * <p>Before a sortie the base's guns shell the spore anti-air it <b>knows</b> of on the way: colonies and outposts a
 * friendly squad has seen in the last {@link #KNOWN_SECONDS}, within {@link #GUN_RANGE} of the base, whose reach
 * covers the target or the way there ({@link #shellable}). A shoot is sized to kill each, at most one every
 * {@link #SHOOT_SECONDS}. What nobody has seen, or lies past the guns, stays a threat, so sorties are sometimes lost
 * (owner, 2026-10-24: "Things being lost is a feature").
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

    /** Seconds a spotted spore colony or outpost stays known to the base after a friendly squad last saw it. */
    static final float KNOWN_SECONDS = 120f;
    static final int KNOWN_TICKS = Math.round(KNOWN_SECONDS / BattleSimulation.TICK_DT);
    /** Cells from the base its guns reach: the middle of the field, not the mothers at its far side. */
    static final float GUN_RANGE = 160f;
    /** Cells past a defence's own range the commander keeps its sortie. */
    static final float STANDOFF = 2f;

    /** When a friendly squad last saw each spore colony and outpost, by id: what the base knows of. */
    private final Map<Long, Integer> spotted = new HashMap<>();
    private HeartSupplyRoad road = new HeartSupplyRoad(List.of());
    private int sorties;
    /** Sorties shot down; each is marked {@code gunship_lost_<n>} at the commander's next call. */
    private int lost;
    private int lostMarked;
    private int shoots;
    private int nextShoot;

    /** The supply road, whose satellite is rung 3 while it cuts the road (F6). */
    HeartGunship road(HeartSupplyRoad road) {
        this.road = road;
        return this;
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
            return s -> {
                if (sortie.destroyed) {
                    lost++;
                    return true;
                }
                if (sortie.state == ShuttleState.GONE) return true;
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
        spot(sim);
        // A sortie shot down since the last call: the evidence reads the owner's gunship question from these.
        while (lostMarked < lost) beats.add("gunship_lost_" + ++lostMarked);
        if (shed.onRoad(AlliedTool.GUNSHIP, PLACE) > 0) return;
        int[] target = target(sim, lanes);
        if (target == null) return;

        int[] from = shed.dispatchCell(sim);
        if (from == null) return;
        // The base shells what it knows of on the way first; the sortie goes once none of that stands.
        int tick = sim.getSimTickIndex();
        List<Long> known = new ArrayList<>();
        for (long gun : reaching(sim, from, target)) {
            Integer seen = spotted.get(gun);
            float range = (float) Math.hypot(sim.world().x(gun) - from[0], sim.world().y(gun) - from[1]);
            if (seen != null && shellable(tick, seen, range)) known.add(gun);
        }
        if (!known.isEmpty()) {
            shoot(sim, known, beats);
            return;
        }
        if (!shed.request(sim, new AlliedRequest(AlliedTool.GUNSHIP, PLACE, target[0], target[1], "target"))) return;
        sorties++;
        beats.add("gunship_called_" + sorties);
    }

    /**
     * Whether the base shells a spore gun it last saw at {@code spottedTick}, {@code range} cells from it: seen in the
     * last {@link #KNOWN_SECONDS} and within {@link #GUN_RANGE}. The base knows only what the field has reported.
     */
    static boolean shellable(int tick, int spottedTick, float range) {
        return tick - spottedTick <= KNOWN_TICKS && range <= GUN_RANGE;
    }

    /** Notes every spore colony and outpost a friendly squad (the company's or the allies') sees now. */
    private void spot(BattleSimulation sim) {
        Set<Long> guns = new HashSet<>();
        for (SwarmBore bore : sim.swarmBores().bores()) guns.addAll(antiAir(bore));
        if (guns.isEmpty()) return;
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.ALLY && squad.faction != Faction.MARINE) continue;
            for (BelievedContact seen : squad.believedContacts()) {
                if (seen.source() != BeliefSource.DIRECT || !guns.contains(seen.unitId())) continue;
                spotted.merge(seen.unitId(), seen.lastSeenTick(), Math::max);
            }
        }
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
        SwarmBore cutter = road.cut() ? road.satellite(sim) : null;
        return cutter != null ? new int[]{cutter.x(), cutter.y()} : lanes.stalledLaneTarget();
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
