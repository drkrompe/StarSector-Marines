package com.dillon.starsectormarines.battle.matchup;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechSections;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.VehicleEngagement;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Plays one armor scenario for one pair of sides and one seed, headless, until a side is gone, nobody has hurt
 * anybody for {@link #STALEMATE_TICKS}, or the clock runs out.
 *
 * <p>Every attacking machine is brought into range the way a player brings it: every {@link #STEER_EVERY_TICKS} it is
 * given a move order to a standoff point at 70% of its longest gun's reach off the nearest living enemy, and left
 * alone once it is inside 85% of that reach (a mech, or a hull that engages on its own: anywhere inside its reach),
 * unless it then stands a whole pass without firing, when it is brought closer. Such a machine is brought closer only
 * when it has no line of sight to the enemy: one in sight may be holding fire to dissipate flux, turning, or holding a duel band on purpose. A lone mech would otherwise sit out the fight under its own doctrine (an
 * armored-support Bulwark with nobody to pace holds where it lands), and a hull has no tactical brain at all. Inside
 * range everything is the production game: mech doctrine and targeting, vehicle turrets acquiring on their own.
 * Attacking lances carry no mission of their own ({@link #MECH_ORDER}): once the steering has brought one into reach,
 * nothing but its doctrine decides where it fights. An attack-move onto the defenders' ground used to ride along, and
 * walked every attacking mech into contact whatever its doctrine wanted ({@code -PmechOrder=attack-move} restores it,
 * and the old steering of mechs with it).
 * Defending lances hold an area defence of their slot, and defending hulls hold where they were set down.
 */
public final class ArmorPlay {

    static final int TICKS_PER_SECOND = 30;
    /** Ticks without a point of damage, after the first, that end a fight as a stalemate (40 s). */
    static final int STALEMATE_TICKS = 1200;
    static final int STEER_EVERY_TICKS = 90;
    /**
     * Fraction of its longest reach an attacker is brought to ({@code -Pstandoff}, default 0.7). At 0.7 a Hound's
     * chaingun sets the distance and its 18-cell SRM pod stays out of range; lower it to ask what closing buys.
     */
    static final float STANDOFF = Float.parseFloat(System.getProperty("armorMatchup.standoff", "0.7"));
    private static final float CLOSE_ENOUGH = 0.85f;
    private static final float UNARMED_STANDOFF_CELLS = 6f;
    /** Rings a firing cell is looked for on, as fractions of the standoff: there, nearer, then farther. */
    private static final float[] RINGS = {1f, 0.75f, 0.5f, 0.3f, 1.3f, 1.6f};
    private static final int RING_BEARINGS = 48;
    /** Cells of walkable ground a hull's firing cell needs around it, so the order service does not refuse it. */
    private static final int HULL_CLEARANCE = 2;
    /** Each time a machine sits in range without firing, its standoff shrinks by this, at most {@link #MAX_CLOSE_IN} times. */
    private static final float CLOSE_IN_FACTOR = 0.6f;
    private static final int MAX_CLOSE_IN = 4;
    private static final boolean DEBUG = Boolean.getBoolean("armorMatchup.debug");
    /**
     * What an attacking lance is ordered to do besides the steering ({@code -PmechOrder}): {@code engage} (default)
     * gives it nothing more, so inside reach its doctrine alone fights; {@code attack-move} restores the old
     * attack-move onto the defenders' ground.
     */
    static final String MECH_ORDER = System.getProperty("armorMatchup.mechOrder", "engage");

    /**
     * Degrees a defending hull is turned from facing the attackers when it is set down ({@code -PdefenderFacing}):
     * 0 (default) faces them, 90 shows them its flank, 180 its rear. A holding hull turns only as its doctrine
     * drives it, so this asks what being caught off its front costs. Mechs are not turned: a standing mech pivots
     * onto its target at once.
     */
    static final float DEFENDER_FACING = Float.parseFloat(System.getProperty("armorMatchup.defenderFacing", "0"));
    /**
     * Degrees of arc, seen from the defenders, the attacking machines are fanned across when they are set down
     * ({@code -PattackerSpread}): 0 (default) sets them abreast at the west edge; 90 sets two of them 45° either side
     * of west, 180 one due north and one due south, so a second machine comes in on the defender's flank. Each is set
     * as far out on its bearing as the map allows, up to the usual eighty cells, facing the defenders, and is steered
     * from there the usual way, which keeps it on its own side. Open ground only: a bearing on the other grounds runs
     * into their walls.
     */
    static final float ATTACKER_SPREAD = Float.parseFloat(System.getProperty("armorMatchup.attackerSpread", "0"));
    /**
     * Cells off the defenders a fanned attacker is set at, at most ({@code -PattackerRadius}; default 0, as far out
     * as the map allows on its bearing). A wide fan only fits close in, so a narrow fan at the same radius is its
     * head-on control: the same start distance, one approach.
     */
    static final float ATTACKER_RADIUS = Float.parseFloat(System.getProperty("armorMatchup.attackerRadius", "0"));
    private static final int SPREAD_EDGE_CELLS = 4;
    /** {@code attack-move} also restores the old steering of mechs: brought closer like a hull, in sight or not. */
    private static final boolean LEGACY_MECH_ORDER = MECH_ORDER.equals("attack-move");

    static final String ATTACKERS = "attackers";
    static final String DEFENDERS = "defenders";

    private ArmorPlay() {}

    /** What one weapon did for its side: shots fired, shots that hit, and its share of the damage its side dealt. */
    public record WeaponStats(int shots, int hits, float damage) {
        WeaponStats plus(WeaponStats o) {
            return new WeaponStats(shots + o.shots, hits + o.hits, damage + o.damage);
        }
    }

    /**
     * One side after the fight. Integrity is armor plus structure summed over the side's machines (a dead machine
     * counts zero); damage dealt is the integrity the other side lost, so overkill is not credited. {@code weapons}
     * apportions that damage among the side's weapons by hits times the weapon's nominal damage (a contact warhead's payload where it has one), since the
     * simulation credits damage to the shooter and not to the gun.
     */
    public record SideStats(String label, int start, int left, float integrityStart, float integrityLeft,
                            float damageDealt, int shots, int hits, int mountsLost, int legsCrippled,
                            float fluxLimitedSeconds, Map<String, WeaponStats> weapons, String survivors,
                            int roundsEngaged, int roundsStopped) {

        public float hitRate() {
            return shots == 0 ? 0f : (float) hits / shots;
        }

        /** Remaining armor+structure of the whole side, 0..1. */
        public float integrityFraction() {
            return integrityStart <= 0f ? 0f : integrityLeft / integrityStart;
        }
    }

    /**
     * What one fight came to. {@code winner} is ATTACKERS, DEFENDERS, BOTH_DESTROYED, STALEMATE or UNRESOLVED
     * (the tick limit). {@code firstDamageTick} is -1 when nobody was ever hit. {@code fightRange} is the median,
     * over the ticks from the first hit, of the distance between the nearest two opposing machines: the range the
     * fight was actually fought at, -1 when nobody was hit.
     */
    public record Result(String scenario, String attacker, String defender, long seed, String winner,
                         int ticks, int firstDamageTick, float fightRange, SideStats attackers, SideStats defenders) {

        public boolean decided() {
            return winner.equals("ATTACKERS") || winner.equals("DEFENDERS") || winner.equals("BOTH_DESTROYED");
        }

        /** Seconds from the first hit to the decision; -1 for a fight nobody won. */
        public float timeToKillSeconds() {
            if (!decided() || firstDamageTick < 0) return -1f;
            return (ticks - firstDamageTick) / (float) TICKS_PER_SECOND;
        }
    }

    public static Result play(ArmorScenarios.ArmorScenario scenario, ArmorSide attacker, ArmorSide defender,
                              long seed, MatchupRecording recording) {
        if (ATTACKER_SPREAD > 0f && !scenario.id().equals("open-ground")) {
            throw new IllegalArgumentException("-PattackerSpread is for open-ground only, not " + scenario.id());
        }
        ArmorScenarios.Ground ground = ArmorScenarios.build(scenario);
        SceneBuilder builder = SceneBuilder.over(ground.grid(), ground.topology()).missionCompletion(false).seed(seed);
        List<String> attackerSquads = declareMechs(builder, scenario, attacker, true);
        List<String> defenderSquads = declareMechs(builder, scenario, defender, false);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        try {
            Side a = new Side(attacker, true);
            Side d = new Side(defender, false);
            a.collect(world, attackerSquads);
            d.collect(world, defenderSquads);
            a.spawnVehicles(sim, scenario, Faction.MARINE);
            d.spawnVehicles(sim, scenario, Faction.DEFENDER);
            a.begin(sim);
            d.begin(sim);
            long[][] both = {a.all(), d.all()};
            SceneRun run = SceneRun.of(world);
            int ticks = 0;
            int firstDamage = -1;
            int lastDamage = -1;
            float damageSoFar = 0f;
            boolean stalemate = false;
            List<Float> ranges = new ArrayList<>();
            while (ticks < scenario.maxTicks() && a.alive(sim) > 0 && d.alive(sim) > 0) {
                if (recording != null) {
                    recording.observe(sim, ticks, firstDamage >= 0, status(sim, a, d), both);
                }
                if (ticks % STEER_EVERY_TICKS == 0) a.steer(sim, d);
                run.step();
                ticks++;
                if (recording != null) recording.advanced(sim);
                a.observe(sim);
                d.observe(sim);
                if (firstDamage >= 0) {
                    float range = a.closestTo(sim, d);
                    if (range >= 0f) ranges.add(range);
                }
                float damage = a.integrityLost() + d.integrityLost();
                if (damage > damageSoFar + 1e-3f) {
                    damageSoFar = damage;
                    if (firstDamage < 0) firstDamage = ticks;
                    lastDamage = ticks;
                } else if (lastDamage >= 0 && ticks - lastDamage >= STALEMATE_TICKS) {
                    stalemate = true;
                    break;
                }
            }
            if (recording != null) recording.finish(sim, ticks, status(sim, a, d), both);
            int aLeft = a.alive(sim);
            int dLeft = d.alive(sim);
            String winner;
            if (aLeft == 0 && dLeft == 0) winner = "BOTH_DESTROYED";
            else if (dLeft == 0) winner = "ATTACKERS";
            else if (aLeft == 0) winner = "DEFENDERS";
            else winner = stalemate ? "STALEMATE" : "UNRESOLVED";
            return new Result(scenario.id(), attacker.label(), defender.label(), seed, winner, ticks, firstDamage,
                    median(ranges),
                    a.stats(sim, d.integrityLost()), d.stats(sim, a.integrityLost()));
        } finally {
            sim.close();
        }
    }

    /** Declares one squad per mech line and returns their keys; vehicles are spawned after the world stands. */
    private static List<String> declareMechs(SceneBuilder builder, ArmorScenarios.ArmorScenario scenario,
                                             ArmorSide side, boolean attacking) {
        List<String> keys = new ArrayList<>();
        int slot = 0;
        String prefix = attacking ? ATTACKERS : DEFENDERS;
        for (ArmorSide.Unit unit : side.units()) {
            if (unit instanceof ArmorSide.Mech mech) {
                String key = prefix + "-" + keys.size();
                int first = slot;
                int[] firstCell = cell(scenario, side, attacking, first);
                int[] seat = {first};
                SceneBuilder.SquadSpec squad = builder.squad(key)
                        .faction(attacking ? Faction.MARINE : Faction.DEFENDER)
                        .size(mech.count())
                        .at(firstCell[0], firstCell[1])
                        .mech(mech.deployment())
                        .each(spec -> {
                            int[] cell = cell(scenario, side, attacking, seat[0]++);
                            spec.atPosition(cell[0] + 0.5f, cell[1] + 0.5f);
                        });
                if (attacking) {
                    if (LEGACY_MECH_ORDER) {
                        squad.assigned(id -> ObjectiveAssignment.attackMove(id, ArmorScenarios.DEFENDER_X,
                                ArmorScenarios.MID_Y));
                    }
                } else {
                    squad.assigned(id -> ObjectiveAssignment.defendArea(id, firstCell[0], firstCell[1]));
                }
                squad.done();
                keys.add(key);
            }
            slot += unit.count();
        }
        return keys;
    }

    /**
     * Cell of the {@code index}th machine of a side: the scenario's slot, or for an attacker under
     * {@link #ATTACKER_SPREAD} its place on the fan around the first defender's slot.
     */
    static int[] cell(ArmorScenarios.ArmorScenario scenario, ArmorSide side, boolean attacking, int index) {
        int count = 0;
        for (ArmorSide.Unit unit : side.units()) count += unit.count();
        if (!attacking || ATTACKER_SPREAD <= 0f || count < 2) return scenario.slot(attacking, index);
        return fanned(scenario.slot(false, 0), scenario.slot(true, 0), ATTACKER_SPREAD, index, count);
    }

    /**
     * The {@code index}th of {@code count} cells fanned across {@code spread} degrees around {@code anchor}, centred
     * on the bearing to {@code usual}: each as far out as {@code usual} is, or less where the map edge comes first.
     */
    static int[] fanned(int[] anchor, int[] usual, float spread, int index, int count) {
        double base = Math.atan2(usual[1] - anchor[1], usual[0] - anchor[0]);
        double angle = base + Math.toRadians(spread * (index / (float) (count - 1) - 0.5f));
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float radius = (float) Math.hypot(usual[0] - anchor[0], usual[1] - anchor[1]);
        if (ATTACKER_RADIUS > 0f) radius = Math.min(radius, ATTACKER_RADIUS);
        for (; radius > 1f; radius -= 0.5f) {
            int x = Math.round(anchor[0] + cos * radius);
            int y = Math.round(anchor[1] + sin * radius);
            if (x >= SPREAD_EDGE_CELLS && x < ArmorScenarios.WIDTH - SPREAD_EDGE_CELLS
                    && y >= SPREAD_EDGE_CELLS && y < ArmorScenarios.HEIGHT - SPREAD_EDGE_CELLS) {
                return new int[] {x, y};
            }
        }
        return usual;
    }

    static float median(List<Float> values) {
        if (values.isEmpty()) return -1f;
        float[] sorted = new float[values.size()];
        for (int i = 0; i < sorted.length; i++) sorted[i] = values.get(i);
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static String status(BattleSimulation sim, Side a, Side d) {
        return "attackers " + a.alive(sim) + "/" + a.ids.size() + "   defenders " + d.alive(sim) + "/" + d.ids.size();
    }

    /** One body on the field and what has been seen of it. */
    private static final class Body {
        final long id;
        final String name;
        final boolean vehicle;
        float integrityStart;
        float integrity;
        boolean dead;
        boolean crippled;
        int mountsLost;
        /** Hostile rounds this hull's active protection engaged and stopped. */
        int roundsEngaged;
        int roundsStopped;
        int fluxLimitedTicks;
        long lastOrderCell = Long.MIN_VALUE;
        final Set<Long> refusedCells = new HashSet<>();
        /** Rounds this machine has fired, and the count at the last steering pass. */
        int shots;
        int shotsAtSteer;
        /** How many times it has been brought closer for sitting in range without firing. */
        int closeIn;
        String sections = "";

        Body(long id, String name, boolean vehicle) {
            this.id = id;
            this.name = name;
            this.vehicle = vehicle;
        }
    }

    private static final class Side {
        final ArmorSide side;
        final boolean attacking;
        final List<Body> ids = new ArrayList<>();
        final Map<Long, Body> byId = new HashMap<>();
        final Map<String, int[]> shotsByWeapon = new TreeMap<>();
        final Map<String, Float> hitWeight = new TreeMap<>();

        Side(ArmorSide side, boolean attacking) {
            this.side = side;
            this.attacking = attacking;
        }

        void collect(SceneWorld world, List<String> squads) {
            int squad = 0;
            for (ArmorSide.Unit unit : side.units()) {
                if (!(unit instanceof ArmorSide.Mech mech)) continue;
                for (long id : world.members(squads.get(squad++))) add(new Body(id, mech.label(), false));
            }
        }

        void spawnVehicles(BattleSimulation sim, ArmorScenarios.ArmorScenario scenario, Faction faction) {
            int slot = 0;
            ConvoyService convoy = sim.convoy();
            for (ArmorSide.Unit unit : side.units()) {
                if (unit instanceof ArmorSide.Vehicle vehicle) {
                    for (int i = 0; i < unit.count(); i++) {
                        int[] cell = cell(scenario, side, attacking, slot + i);
                        float x = cell[0] + 0.5f;
                        float y = cell[1] + 0.5f;
                        long id = convoy.spawn(vehicle.type(), faction, VehicleMission.deployed(x, y));
                        // Facing the enemy (0° is +Y): the arcs a hull is hit on start from where it points.
                        int[] anchor = scenario.slot(false, 0);
                        float facing = attacking
                                ? AirBody.facingToward(anchor[0] + 0.5f - x, anchor[1] + 0.5f - y)
                                : 90f + DEFENDER_FACING;
                        convoy.body(id).teleport(x, y, facing);
                        add(new Body(id, vehicle.label(), true));
                    }
                }
                slot += unit.count();
            }
        }

        private void add(Body body) {
            ids.add(body);
            byId.put(body.id, body);
        }

        void begin(BattleSimulation sim) {
            for (Body b : ids) {
                b.integrityStart = sim.world().hp(b.id) + (sim.world().hasArmor(b.id) ? sim.world().armor(b.id) : 0f);
                b.integrity = b.integrityStart;
            }
        }

        long[] all() {
            long[] out = new long[ids.size()];
            for (int i = 0; i < out.length; i++) out[i] = ids.get(i).id;
            return out;
        }

        boolean isAlive(BattleSimulation sim, Body b) {
            if (b.vehicle) return sim.convoy().isVehicle(b.id) && sim.convoy().isPresent(b.id);
            return sim.getRoster().isAliveById(b.id);
        }

        int alive(BattleSimulation sim) {
            int n = 0;
            for (Body b : ids) if (!b.dead && isAlive(sim, b)) n++;
            return n;
        }

        float integrityLost() {
            float lost = 0f;
            for (Body b : ids) lost += b.integrityStart - b.integrity;
            return lost;
        }

        /** Reads the bodies after a tick: integrity, section losses, flux, and this side's shots. */
        void observe(BattleSimulation sim) {
            for (Body b : ids) {
                if (b.dead) continue;
                if (!isAlive(sim, b)) {
                    b.dead = true;
                    b.integrity = 0f;
                    continue;
                }
                b.integrity = Math.min(b.integrity,
                        sim.world().hp(b.id) + (sim.world().hasArmor(b.id) ? sim.world().armor(b.id) : 0f));
                if (b.vehicle) {
                    GroundTurret turret = sim.convoy().turret(b.id);
                    b.mountsLost = turret != null && turret.destroyed() ? 1 : 0;
                    if (turret != null && turret.reservoir != null
                            && (turret.reservoir.aiDissipating() || turret.reservoir.venting())) b.fluxLimitedTicks++;
                    int[] intercepts = sim.pointDefense().hullInterceptions(b.id);
                    b.roundsEngaged = intercepts[0];
                    b.roundsStopped = intercepts[1];
                }
                if (!b.vehicle && sim.world().hasMechLoadout(b.id)) {
                    MechLoadoutComponent loadout = sim.world().mechLoadout(b.id);
                    if (loadout.flux.aiDissipating() || loadout.flux.venting()) b.fluxLimitedTicks++;
                    MechSections sections = loadout.sections();
                    if (sections != null) {
                        b.crippled = sections.crippled();
                        int lost = 0;
                        for (MechMountSlot slot : MechMountSlot.values()) {
                            if (sections.lostMount(slot) != null) lost++;
                        }
                        b.mountsLost = lost;
                        b.sections = sectionText(sections);
                    }
                }
            }
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                Body shooter = byId.get(shot.shooterId);
                if (shooter == null) continue;
                WeaponDef weapon = weaponOf(shot);
                // A hull's active protection posts its bursts as shots; they are not the machine's fire.
                if (weapon != null && weapon.damage <= 0f && weapon.contactDamage <= 0f) continue;
                shooter.shots++;
                String key = weapon != null ? weaponKey(weapon.id) : "unknown";
                int[] counts = shotsByWeapon.computeIfAbsent(key, k -> new int[2]);
                counts[0]++;
                if (shot.hit) {
                    counts[1]++;
                    hitWeight.merge(key, weapon != null ? Math.max(1f, Math.max(weapon.damage, weapon.contactDamage)) : 1f, Float::sum);
                }
            }
        }

        /**
         * Brings each attacking machine into range the way a player would: while it is farther than
         * {@link #CLOSE_ENOUGH} of its reach from the nearest living enemy, it is ordered to a cell about
         * {@link #STANDOFF} of that reach off the enemy that can see it ({@link #firingCell}); inside that, it is left
         * to its own doctrine and turret. A machine that stands a whole pass in range without firing is brought closer,
         * and a refused order is not repeated.
         */
        void steer(BattleSimulation sim, Side enemy) {
            if (!attacking) return;
            for (Body b : ids) {
                if (b.dead || !isAlive(sim, b)) continue;
                // A hull whose gun was shot away has nothing left to bring into range; a player would not drive it in.
                if (b.vehicle && b.mountsLost > 0) continue;
                float[] at = position(sim, b);
                float[] target = enemy.nearest(sim, at[0], at[1]);
                if (target == null) continue;
                float reach = reach(sim, b);
                boolean silent = b.shots == b.shotsAtSteer;
                b.shotsAtSteer = b.shots;
                float dx = at[0] - target[0];
                float dy = at[1] - target[1];
                float distance = (float) Math.hypot(dx, dy);
                // A mech is left to its doctrine anywhere inside its reach; a hull, which has none, inside 85% of it.
                // A hull fights armor on its own once in reach (VehicleEngagement); with that off it has no brain
                // and is steered all the way in, as before.
                boolean steeredLikeAHull = b.vehicle && !VehicleEngagement.isEnabled() || LEGACY_MECH_ORDER;
                float leaveAlone = steeredLikeAHull ? reach * CLOSE_ENOUGH * closer(b) : reach * closer(b);
                if (reach > 0f && distance <= leaveAlone) {
                    // In range: leave it to its doctrine and turret, unless it has stood there a whole pass without
                    // firing (a wall or a corner between it and the enemy), when a player would bring it closer.
                    // A mech with the enemy in sight is left to its doctrine even when it is quiet: it may be
                    // holding fire to dissipate flux, turning, or holding a duel band on purpose, and bringing it closer every pass
                    // walked every mech into contact whatever its doctrine wanted.
                    if (!silent || hasOrder(sim, b) || b.closeIn >= MAX_CLOSE_IN
                            || !steeredLikeAHull && sim.getGrid().hasLineOfSight((int) at[0], (int) at[1],
                            (int) target[0], (int) target[1])) continue;
                    b.closeIn++;
                }
                float standoff = reach > 0f ? reach * STANDOFF * closer(b) : UNARMED_STANDOFF_CELLS;
                boolean refused = refused(sim, b);
                if (refused) b.refusedCells.add(b.lastOrderCell);
                int[] pick = firingCell(sim.getGrid(), at, target, standoff, b.vehicle ? HULL_CLEARANCE : 1,
                        b.refusedCells);
                int cx = pick[0];
                int cy = pick[1];
                long cell = ((long) cx << 32) | (cy & 0xffffffffL);
                if (!refused && hasOrder(sim, b) && cell == b.lastOrderCell) continue;
                if (b.vehicle) sim.getVehicleMoveOrderService().requestMove(b.id, cx, cy);
                else sim.getMechMoveOrderService().requestMove(b.id, cx, cy);
                b.lastOrderCell = cell;
                if (DEBUG) {
                    System.out.println("steer " + b.name + " at " + Math.round(at[0]) + "," + Math.round(at[1])
                            + " -> " + cx + "," + cy + (refused ? " (after refusal)" : ""));
                }
            }
        }

        /**
         * Where a player would put the machine: a drivable cell about {@code standoff} off the target with a clear
         * line of sight to it, nearest the machine's own position. Rings are tried at the standoff, then nearer, then
         * farther; a cell whose order was refused is not offered again. With nothing suitable, the target's own cell
         * (the order service snaps it to drivable ground).
         */
        static int[] firingCell(NavigationGrid grid, float[] at, float[] target, float standoff, int clearance,
                                Set<Long> refused) {
            int tx = (int) target[0];
            int ty = (int) target[1];
            for (float ring : RINGS) {
                float radius = Math.max(2f, standoff * ring);
                int[] best = null;
                float bestD = Float.MAX_VALUE;
                for (int k = 0; k < RING_BEARINGS; k++) {
                    double angle = 2 * Math.PI * k / RING_BEARINGS;
                    int cx = Math.round(target[0] + (float) Math.cos(angle) * radius);
                    int cy = Math.round(target[1] + (float) Math.sin(angle) * radius);
                    if (refused.contains(((long) cx << 32) | (cy & 0xffffffffL))) continue;
                    if (!clear(grid, cx, cy, clearance) || !grid.hasLineOfSight(cx, cy, tx, ty)) continue;
                    float d = (cx - at[0]) * (cx - at[0]) + (cy - at[1]) * (cy - at[1]);
                    if (d < bestD) {
                        bestD = d;
                        best = new int[] {cx, cy};
                    }
                }
                if (best != null) return best;
            }
            return new int[] {clamp(tx, grid.getWidth()), clamp(ty, grid.getHeight())};
        }

        /** Every cell within {@code clearance} of (x, y) on the map and walkable. */
        private static boolean clear(NavigationGrid grid, int x, int y, int clearance) {
            for (int dy = -clearance; dy <= clearance; dy++) {
                for (int dx = -clearance; dx <= clearance; dx++) {
                    if (!grid.inBounds(x + dx, y + dy) || !grid.isWalkable(x + dx, y + dy)) return false;
                }
            }
            return true;
        }

        private static float closer(Body b) {
            return (float) Math.pow(CLOSE_IN_FACTOR, b.closeIn);
        }

        private static float[] position(BattleSimulation sim, Body b) {
            if (b.vehicle) {
                GroundBody body = sim.convoy().body(b.id);
                return new float[] {body.x, body.y};
            }
            return new float[] {sim.world().x(b.id), sim.world().y(b.id)};
        }

        private static float reach(BattleSimulation sim, Body b) {
            if (b.vehicle) return sim.convoy().weaponRange(b.id);
            return sim.world().hasMechLoadout(b.id) ? sim.world().mechLoadout(b.id).maxWeaponRange() : 0f;
        }

        private static boolean hasOrder(BattleSimulation sim, Body b) {
            return b.vehicle ? sim.getVehicleMoveOrderService().activeOrder(b.id) != null
                    : sim.getMechMoveOrderService().activeOrder(b.id) != null;
        }

        private static boolean refused(BattleSimulation sim, Body b) {
            if (b.lastOrderCell == Long.MIN_VALUE || !b.vehicle) return false;
            return sim.getVehicleMoveOrderService().refusal(b.id) != null
                    && sim.getVehicleMoveOrderService().activeOrder(b.id) == null;
        }

        private static int clamp(int v, int size) {
            return Math.max(1, Math.min(size - 2, v));
        }

        /** Distance between this side's and {@code enemy}'s nearest living machines, or -1 when a side is gone. */
        float closestTo(BattleSimulation sim, Side enemy) {
            float best = Float.MAX_VALUE;
            for (Body b : ids) {
                if (b.dead || !isAlive(sim, b)) continue;
                float[] at = position(sim, b);
                float[] other = enemy.nearest(sim, at[0], at[1]);
                if (other != null) best = Math.min(best, (float) Math.hypot(at[0] - other[0], at[1] - other[1]));
            }
            return best == Float.MAX_VALUE ? -1f : best;
        }

        /** Position of the living enemy nearest (x, y), or null when none stands. */
        float[] nearest(BattleSimulation sim, float x, float y) {
            float[] best = null;
            float bestD = Float.MAX_VALUE;
            for (Body b : ids) {
                if (b.dead || !isAlive(sim, b)) continue;
                float bx, by;
                if (b.vehicle) {
                    GroundBody body = sim.convoy().body(b.id);
                    bx = body.x;
                    by = body.y;
                } else {
                    bx = sim.world().x(b.id);
                    by = sim.world().y(b.id);
                }
                float dd = (bx - x) * (bx - x) + (by - y) * (by - y);
                if (dd < bestD) {
                    bestD = dd;
                    best = new float[] {bx, by};
                }
            }
            return best;
        }

        SideStats stats(BattleSimulation sim, float damageDealt) {
            int left = 0, mounts = 0, legs = 0, flux = 0, engaged = 0, stopped = 0;
            float start = 0f, now = 0f;
            List<String> survivors = new ArrayList<>();
            for (Body b : ids) {
                start += b.integrityStart;
                now += b.integrity;
                mounts += b.mountsLost;
                engaged += b.roundsEngaged;
                stopped += b.roundsStopped;
                if (b.crippled) legs++;
                flux += b.fluxLimitedTicks;
                if (!b.dead && isAlive(sim, b)) {
                    left++;
                    survivors.add(survivorText(b));
                }
            }
            float weightTotal = 0f;
            for (float w : hitWeight.values()) weightTotal += w;
            Map<String, WeaponStats> weapons = new TreeMap<>();
            int shots = 0, hits = 0;
            for (Map.Entry<String, int[]> e : shotsByWeapon.entrySet()) {
                float share = weightTotal > 0f ? hitWeight.getOrDefault(e.getKey(), 0f) / weightTotal : 0f;
                weapons.put(e.getKey(), new WeaponStats(e.getValue()[0], e.getValue()[1], damageDealt * share));
                shots += e.getValue()[0];
                hits += e.getValue()[1];
            }
            return new SideStats(side.label(), ids.size(), left, start, now, damageDealt, shots, hits, mounts, legs,
                    flux / (float) TICKS_PER_SECOND, weapons, String.join(" ", survivors), engaged, stopped);
        }

        private static String survivorText(Body b) {
            int pct = b.integrityStart <= 0f ? 0 : Math.round(100f * b.integrity / b.integrityStart);
            String text = b.name + "[" + pct + "%";
            if (!b.sections.isEmpty()) text += " " + b.sections;
            if (b.crippled) text += " crippled";
            return text + "]";
        }
    }

    private static WeaponDef weaponOf(ShotEvent shot) {
        if (shot.mechWeaponDef != null) return shot.mechWeaponDef;
        if (shot.turretStructureDef != null && shot.turretStructureDef.mount != null) {
            return shot.turretStructureDef.mount.weapon;
        }
        return shot.primaryWeaponDef;
    }

    /** {@code weapon.mech-srm-pod} reads as {@code mech-srm-pod}. */
    static String weaponKey(String weaponId) {
        return weaponId.startsWith("weapon.") ? weaponId.substring("weapon.".length()) : weaponId;
    }

    /** Remaining armor+structure per fitted section, as percentages: {@code L80/T45/A100/LS0/H100}. */
    static String sectionText(MechSections sections) {
        List<String> parts = new ArrayList<>();
        for (MechSection s : MechSection.values()) {
            if (!sections.fitted(s)) continue;
            float max = sections.maxArmor(s) + sections.maxStructure(s);
            float now = sections.armor(s) + sections.structure(s);
            int pct = max <= 0f ? 0 : Math.round(100f * now / max);
            parts.add(sectionCode(s) + pct);
        }
        return String.join("/", parts);
    }

    static String sectionCode(MechSection section) {
        StringBuilder code = new StringBuilder();
        for (String word : section.name().split("_")) code.append(word.charAt(0));
        return code.toString().toUpperCase(Locale.ROOT);
    }
}
