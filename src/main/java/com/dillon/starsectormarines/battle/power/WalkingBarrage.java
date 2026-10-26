package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.deployable.FieldMortarService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretAim;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.turret.TurretMountGeometry;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;
import java.util.SplittableRandom;

/**
 * A walking barrage (`heart-nouns.md`, Barrage plan): guns shelling a
 * line across the approach on a timetable frozen at the briefing, lifting from
 * line to line toward the enemy and stopping at a last line short of the
 * objective.
 *
 * <p>Each line opens at its second, fires for its seconds and lifts to the
 * next, whatever the battle is doing: a timetable, never a reaction. While a
 * line fires, shells fall along it at a steady rate. With a {@link #battery}
 * each shell is a real round fired from one of its guns on the map: the gun
 * turns onto the point and launches, and the round arcs across the map to land
 * after its flight, so the warning is the rounds themselves coming in (the
 * battery's guns can be killed, and a battery with no gun left is silent).
 * Without one the shells fall from off the map, placed as pending detonations
 * after a fixed flight. Neither marks the ground: the battle announces the guns
 * by the line. A line may fire incendiary shells, which light the ground through
 * fire's own ignition seam. <b>Danger close</b> is the field mortar's veto: a
 * shell is not fired on a point a friendly body stands near, widened by how far
 * a body walks while the shell is in the air. Shells are ordinary blasts: what
 * they land on is hurt, never scripted.
 */
public final class WalkingBarrage {

    /** Map ground: a depth into the approach and a share across it, to a cell. */
    public interface Ground {
        int[] cellAt(float depth, float across);
    }

    /** One line: its depth, the second it opens, how long it fires, and its shells' heat (0 for high explosive). */
    public record Line(float depth, float openSeconds, float fireSeconds, int heat) {
        float liftSeconds() { return openSeconds + fireSeconds; }
    }

    /** What one shell is. */
    public record Shell(float radius, float damage, float penetration, int wallDamage, float flightSeconds) { }

    /**
     * The barrage plan: its lines in order, its shell, how many shells a second
     * fall on the line, and how far either side of the line's depth they land.
     */
    public record Plan(List<Line> lines, Shell shell, float shellsPerSecond, float depthSpread) {
        public Plan {
            lines = List.copyOf(lines);
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).openSeconds() < lines.get(i - 1).liftSeconds())
                    throw new IllegalArgumentException("barrage line " + (i + 1) + " opens before line " + i + " lifts");
            }
        }

        /** The index of the line firing at {@code seconds} into the battle, or -1 when none is. */
        public int firingAt(float seconds) {
            for (int i = 0; i < lines.size(); i++) {
                Line line = lines.get(i);
                if (seconds >= line.openSeconds() && seconds < line.liftSeconds()) return i;
            }
            return -1;
        }

        /** The second the last line lifts. */
        public float endSeconds() {
            return lines.isEmpty() ? 0f : lines.get(lines.size() - 1).liftSeconds();
        }
    }

    public enum EventKind { OPENED, LIFTED, ENDED }

    /** A timetable event; {@code line} counts from 1, and is 0 for the end. */
    public record Event(int tick, EventKind kind, int line) { }

    /** Cells a body may walk while a shell is in the air, past the mortar's own margin. */
    static final float WALK_MARGIN_CELLS = 3f;
    /** The shortest flight a battery's round takes, however close the point. */
    static final float MIN_FLIGHT_SECONDS = 2f;
    /** A battery round's speed over the ground: a slow lob, so it is in the air long enough to be seen. */
    static final float LOB_CELLS_PER_SECOND = 32f;
    /** How high a battery round's arc climbs, as a share of its throw. */
    static final float LOB_ARC_PER_CELL = 0.18f;
    /** How fast a body walks, for how far it may come under a round while the round is in the air. */
    static final float WALK_CELLS_PER_SECOND = 1.5f;

    /** How long a battery round is in the air over {@code distance} cells. */
    static float flightSeconds(float distance) {
        return Math.max(MIN_FLIGHT_SECONDS, distance / LOB_CELLS_PER_SECOND);
    }

    private Plan plan;
    private final Ground ground;
    private final Faction side;
    private final SplittableRandom rng;
    private final List<Event> events = new ArrayList<>();
    private final LongBucket bucket = new LongBucket();
    private int current = -1;
    private boolean ended;
    private float owed;
    private int fired;
    private int vetoed;
    private int vetoedForSide;
    private long lastVetoedBy;
    /** Whether the round {@link #dangerClose} last held was held for a body of the barrage's own side. */
    private boolean lastHeldForSide;
    private int silenced;
    private StructureDef battery;
    private long[] guns;

    public WalkingBarrage(Plan plan, Ground ground, Faction side, long seed) {
        this.plan = plan;
        this.ground = ground;
        this.side = side;
        this.rng = new SplittableRandom(seed);
    }

    public Plan plan() { return plan; }

    /**
     * Adds one more line {@code step} deeper than the last, opening as the last lifts and firing for
     * {@code fireSeconds}, as an ammunition delivery that reaches the guns does. Refused once the barrage has
     * ended, or when there is no line to follow; returns whether it was added.
     */
    public boolean extend(float step, float fireSeconds) {
        if (ended || plan.lines().isEmpty()) return false;
        Line last = plan.lines().get(plan.lines().size() - 1);
        List<Line> lines = new ArrayList<>(plan.lines());
        lines.add(new Line(last.depth() + step, last.liftSeconds(), fireSeconds, 0));
        plan = new Plan(lines, plan.shell(), plan.shellsPerSecond(), plan.depthSpread());
        return true;
    }
    public List<Event> events() { return events; }
    public int fired() { return fired; }
    public int vetoed() { return vetoed; }
    /** Rounds held for a body of the barrage's own side, not only an ally's: what the side itself is answerable for. */
    public int vetoedForSide() { return vetoedForSide; }
    /** The friendly body the last held round was held for, or 0: what a probe reads to trace a veto. */
    public long lastVetoedBy() { return lastVetoedBy; }
    /** Shells the battery could not fire because none of its guns was left. */
    public int silenced() { return silenced; }
    /** Shells a dry gun held back, firing rationed. */
    public int rationed() { return rationed; }

    /** A dry gun fires one shell in this many, as a crew scraping the last rounds together does. */
    public static final int DRY_RATION = 4;
    private LongPredicate magazine;
    private int dryShells;
    private int rationed;

    /**
     * Each shell spends a round from its gun through {@code magazine} (the gun's rounds on hand, `GunStock.spend`); a
     * gun with none left fires rationed, one shell in {@link #DRY_RATION}. Without one the battery never runs dry.
     */
    public WalkingBarrage magazine(LongPredicate magazine) {
        this.magazine = magazine;
        return this;
    }

    /**
     * Fires every shell from these guns, emplacements of {@code structureId} on
     * the map, each round from the gun nearest its point: the round is the structure's weapon's own (its sprite,
     * trail, launch and sound) carrying the plan's shell. Call once at setup.
     */
    public WalkingBarrage battery(String structureId, List<Long> gunIds) {
        this.battery = TurretCatalogRegistry.requireStructure(structureId);
        this.guns = gunIds.stream().mapToLong(Long::longValue).toArray();
        return this;
    }

    /** The battery's guns as they were set up, living or dead; empty before {@link #battery}. */
    public long[] guns() { return guns == null ? new long[0] : guns.clone(); }

    /** Whether the battery has been overrun: it had guns and none of them is left. */
    public boolean overrun(BattleSimulation sim) {
        return battery != null && guns.length > 0 && nearestGun(sim, 0f, 0f) == 0L;
    }

    /** The line firing now, counted from 1, or 0. */
    public int firingLine() { return current + 1; }

    public void tick(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        float seconds = tick * BattleSimulation.TICK_DT;
        int firing = plan.firingAt(seconds);
        if (firing != current) {
            if (current >= 0) events.add(new Event(tick, EventKind.LIFTED, current + 1));
            if (firing >= 0) events.add(new Event(tick, EventKind.OPENED, firing + 1));
            current = firing;
            owed = 0f;
        }
        if (!ended && !plan.lines().isEmpty() && seconds >= plan.endSeconds()) {
            ended = true;
            events.add(new Event(tick, EventKind.ENDED, 0));
        }
        if (firing < 0) return;
        Line line = plan.lines().get(firing);
        owed += plan.shellsPerSecond() * BattleSimulation.TICK_DT;
        int width = sim.getGrid().getWidth(), height = sim.getGrid().getHeight();
        while (owed >= 1f) {
            owed -= 1f;
            float depth = line.depth() + (2f * rng.nextFloat() - 1f) * plan.depthSpread();
            int[] cell = ground.cellAt(depth, rng.nextFloat());
            float x = Math.max(0, Math.min(width - 1, cell[0])) + 0.5f;
            float y = Math.max(0, Math.min(height - 1, cell[1])) + 0.5f;
            if (dangerClose(sim, x, y, flightTo(sim, x, y))) {
                vetoed++;
                if (lastHeldForSide) vetoedForSide++;
                continue;
            }
            if (battery == null) {
                PendingDetonation round = shell(CombatTelemetryService.NO_ATTACKER, x, y, plan.shell().flightSeconds(), line);
                round.asUnmarked();
                sim.getCommandPowerService().addFireMission(round);
                fired++;
            } else {
                long gun = nearestGun(sim, x, y);
                if (gun == 0L) {
                    silenced++;
                } else if (magazine != null && !magazine.test(gun) && dryShells++ % DRY_RATION != 0) {
                    rationed++;
                } else {
                    launch(sim, gun, x, y, line);
                    fired++;
                }
            }
        }
    }

    private PendingDetonation shell(long shooter, float x, float y, float flight, Line line) {
        Shell shell = plan.shell();
        PendingDetonation round = new PendingDetonation(shooter, x, y,
                flight, shell.radius(), shell.damage(), shell.penetration(), shell.wallDamage(),
                side, true, shell.radius() * 0.75f, true, false);
        if (line.heat() > 0) round.incendiary(line.heat());
        return round;
    }

    /** Fires one round at {@code (x, y)} from {@code gun}, the battery's nearest living gun, its sector's. */
    private void launch(BattleSimulation sim, long gun, float x, float y, Line line) {
        WeaponDef weapon = battery.mount.weapon;
        float gx = sim.world().x(gun), gy = sim.world().y(gun);
        float facing = TurretAim.bearingTo(gx, gy, x, y);
        sim.turretState().setFacingDegrees(gun, facing);
        TurretMountGeometry.Point muzzle = TurretMountGeometry.muzzle(gx, gy, facing, battery.mount, fired);
        float distance = (float) Math.hypot(x - muzzle.x(), y - muzzle.y());
        float flight = flightSeconds(distance);
        // A lob, not a flat shot: the round climbs a share of its throw, so it is seen coming in.
        float arc = distance * LOB_ARC_PER_CELL;
        PendingDetonation round = shell(gun, x, y, flight, line);
        sim.queueProjectile(new Projectile(muzzle.x(), muzzle.y(), x, y, weapon.boostRamp, arc,
                side, true, flight, round, weapon.id, false));
        sim.postShot(new ShotEvent(gun, muzzle.x(), muzzle.y(), x, y, true, side, flight, battery).withArcHeight(arc));
    }

    /** How long a shell aimed at {@code (x, y)} now will be in the air: from the nearest gun, or the plan's fixed flight. */
    private float flightTo(BattleSimulation sim, float x, float y) {
        if (battery == null) return plan.shell().flightSeconds();
        long gun = nearestGun(sim, x, y);
        return gun == 0L ? 0f : flightSeconds((float) Math.hypot(x - sim.world().x(gun), y - sim.world().y(gun)));
    }

    /** The living gun nearest {@code (x, y)}, or 0 when none is left. */
    private long nearestGun(BattleSimulation sim, float x, float y) {
        long best = 0L;
        float bestSq = Float.MAX_VALUE;
        for (long gun : guns) {
            if (sim.resolveUnit(gun) == 0L || !sim.world().isAlive(gun)) continue;
            float dx = x - sim.world().x(gun), dy = y - sim.world().y(gun);
            if (dx * dx + dy * dy < bestSq) { bestSq = dx * dx + dy * dy; best = gun; }
        }
        return best;
    }

    /**
     * Whether a friendly body stands where the shell could reach it by the time it lands {@code flight} seconds on;
     * {@link #lastHeldForSide} says whether one of them is the barrage's own side's.
     */
    private boolean dangerClose(BattleSimulation sim, float x, float y, float flight) {
        float radius = plan.shell().radius() + FieldMortarService.FRIENDLY_MARGIN_CELLS
                + Math.max(WALK_MARGIN_CELLS, WALK_CELLS_PER_SECOND * flight);
        bucket.clear();
        sim.getUnitIndex().gather(x, y, radius, bucket);
        float radiusSq = radius * radius;
        boolean held = false;
        lastHeldForSide = false;
        for (int i = 0, n = bucket.size; i < n; i++) {
            long id = bucket.ids[i];
            Faction faction = sim.identity().faction(id);
            if (!sim.world().isAlive(id) || !side.friendlyTo(faction)) continue;
            if (sim.identity().type(id).isTurret()) continue;
            float dx = sim.world().x(id) - x, dy = sim.world().y(id) - y;
            if (dx * dx + dy * dy > radiusSq) continue;
            held = true;
            lastVetoedBy = id;
            if (faction == side) {
                lastHeldForSide = true;
                break;
            }
        }
        return held;
    }
}
