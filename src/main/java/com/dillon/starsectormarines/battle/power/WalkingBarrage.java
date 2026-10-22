package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.deployable.FieldMortarService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * A walking barrage (`heart-nouns.md`, Barrage plan): off-map guns shelling a
 * line across the approach on a timetable frozen at the briefing, lifting from
 * line to line toward the enemy and stopping at a last line short of the
 * objective.
 *
 * <p>Each line opens at its second, fires for its seconds and lifts to the
 * next, whatever the battle is doing: a timetable, never a reaction. While a
 * line fires, shells are placed along it at a steady rate as pending
 * detonations through the command-power fire missions, each landing after its
 * flight. A line may fire incendiary shells, which light the ground through
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

    private final Plan plan;
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

    public WalkingBarrage(Plan plan, Ground ground, Faction side, long seed) {
        this.plan = plan;
        this.ground = ground;
        this.side = side;
        this.rng = new SplittableRandom(seed);
    }

    public Plan plan() { return plan; }
    public List<Event> events() { return events; }
    public int fired() { return fired; }
    public int vetoed() { return vetoed; }

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
            if (dangerClose(sim, x, y)) { vetoed++; continue; }
            Shell shell = plan.shell();
            PendingDetonation round = new PendingDetonation(CombatTelemetryService.NO_ATTACKER, x, y,
                    shell.flightSeconds(), shell.radius(), shell.damage(), shell.penetration(), shell.wallDamage(),
                    side, true, shell.radius() * 0.75f, true, false);
            if (line.heat() > 0) round.incendiary(line.heat());
            sim.getCommandPowerService().addFireMission(round);
            fired++;
        }
    }

    /** Whether a friendly body stands where the shell could reach it by the time it lands. */
    private boolean dangerClose(BattleSimulation sim, float x, float y) {
        float radius = plan.shell().radius() + FieldMortarService.FRIENDLY_MARGIN_CELLS + WALK_MARGIN_CELLS;
        bucket.clear();
        sim.getUnitIndex().gather(x, y, radius, bucket);
        float radiusSq = radius * radius;
        for (int i = 0, n = bucket.size; i < n; i++) {
            long id = bucket.ids[i];
            if (!sim.world().isAlive(id) || !side.friendlyTo(sim.identity().faction(id))) continue;
            if (sim.identity().type(id).isTurret()) continue;
            float dx = sim.world().x(id) - x, dy = sim.world().y(id) - y;
            if (dx * dx + dy * dy <= radiusSq) return true;
        }
        return false;
    }
}
