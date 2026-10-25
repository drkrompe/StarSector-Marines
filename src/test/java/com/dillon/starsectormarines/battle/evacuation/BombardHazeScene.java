package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A Bombard's spore haze on its own (`bombard.md`, step 1), before any Bombard lobs it: the see-through obscurant
 * and its sting.
 *
 * <ul>
 *   <li>{@code clear}: a rifle squad shooting at sturdy, unarmed defenders across open ground. The control. The
 *       targets stand well apart, so a round that misses its mark does not land on the next one.</li>
 *   <li>{@code hazed}: the same, with a haze burst over the shooters. They still see every target and keep firing,
 *       and land less of it.</li>
 *   <li>{@code pulses}: unarmed marines in one haze and swarm Brutes in another, far apart. The marines are stung by
 *       the burst and then by its pulses; the swarm is spared; the haze thins away and leaves nothing on the grid.</li>
 * </ul>
 *
 * <p>The verdicts assert mechanics; damage dealt and taken is reported, never tuned against. Every number is UNTUNED.
 */
public final class BombardHazeScene implements BehaviorScene {

    private static final int WIDTH = 96;
    private static final int HEIGHT = 54;
    /** Forty seconds: inside the shooting loops' haze standing whole. */
    private static final int SHOOT_TICKS = 1200;
    /** Twenty-two seconds: past a scale-1 haze's whole life. */
    private static final int PULSE_TICKS = 660;
    private static final int FRAME_EVERY_TICKS = 15;
    private static final int BURST_TICK = 1;
    /** The shooting loops' haze is three adults': it covers the whole squad and stands whole past the loop. */
    private static final float SHOOT_SCALE = 3f;

    private static final int SHOOTER_X = 20;
    private static final int TARGET_X = 38;
    private static final int ROW_Y = 27;
    private static final int MARINE_HAZE_X = 20;
    private static final int SWARM_HAZE_X = 76;

    private static final List<String> LOOPS = List.of("clear", "hazed", "pulses");

    @Override public String id() { return "bombard-haze"; }

    @Override
    public String label() {
        return "Bombard spore haze: it spoils the aim of a squad shooting out of it without hiding anything, stings"
                + " marines in pulses, spares the swarm, and thins away.";
    }

    @Override public int frameDelayMillis() { return 70; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        float clearDamage = -1f;
        for (String loop : LOOPS) {
            if (loop.equals("pulses")) {
                reports.add(playPulses(frames));
            } else {
                ShootTrace trace = playShooting(loop, frames);
                if (loop.equals("clear")) clearDamage = trace.damageDealt;
                reports.add(reportShooting(loop, trace, clearDamage));
            }
        }
        return reports;
    }

    private ShootTrace playShooting(String loop, FrameSink frames) {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false).seed(20261025L);
        builder.squad("shooters").faction(Faction.MARINE).size(6).at(SHOOTER_X, ROW_Y).primary("weapon.field-rifle")
                .health(400f).stationary().done();
        builder.squad("targets").faction(Faction.DEFENDER).size(6).inFile(TARGET_X, ROW_Y - 10, 0, 4).unarmed().health(4000f)
                .stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        ShootTrace trace = new ShootTrace(loop.equals("hazed"), world);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, FRAME_EVERY_TICKS, tick -> String.format(Locale.ROOT,
                        "bombard haze / %s / tick %d / %.0f damage dealt / %s", loop, tick, trace.damageDealt,
                        hazeCaption(sim)))
                .run(SHOOT_TICKS);
        return trace;
    }

    private SceneReport reportShooting(String loop, ShootTrace t, float clearDamage) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("the-squad-fired", t.damageDealt > 0f,
                String.format(Locale.ROOT, "%.0f damage dealt to the targets (wanted some)", t.damageDealt)));
        if (loop.equals("hazed")) {
            verdicts.add(Verdict.of("the-haze-lay-on-the-lines-of-fire", t.hazedLineSamples > 0,
                    t.hazedLineSamples + " shooter-to-target samples crossing haze (wanted some)"));
            verdicts.add(Verdict.of("the-haze-hid-nothing", t.blockedSamples == 0,
                    t.blockedSamples + " of " + t.hazedLineSamples + " hazed samples with sight cut (wanted none)"));
            verdicts.add(Verdict.of("the-haze-spoiled-the-aim", clearDamage >= 0f && t.damageDealt < clearDamage,
                    String.format(Locale.ROOT, "%.0f damage dealt through haze against %.0f in the clear (wanted less)",
                            t.damageDealt, clearDamage)));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("damageDealt", Math.round(t.damageDealt));
        metrics.put("hazedLineSamples", t.hazedLineSamples);
        metrics.put("sightCutSamples", t.blockedSamples);
        if (clearDamage >= 0f) metrics.put("clearDamageDealt", Math.round(clearDamage));
        return new SceneReport(id(), loop, SHOOT_TICKS, verdicts, metrics);
    }

    private SceneReport playPulses(FrameSink frames) {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false).seed(20261025L);
        builder.squad("marines").faction(Faction.MARINE).size(4).at(MARINE_HAZE_X, ROW_Y).unarmed().health(400f)
                .stationary().done();
        builder.squad("swarm").faction(Faction.DEFENDER).type(UnitType.SWARM_BRUTE).size(3).at(SWARM_HAZE_X, ROW_Y)
                .unarmed().stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        PulseTrace trace = new PulseTrace(world);
        SceneRun.of(world).observe(trace)
                .frames(frames, "pulses", FRAME_EVERY_TICKS, tick -> String.format(Locale.ROOT,
                        "bombard haze / pulses / tick %d / marines -%.0f HP / swarm -%.0f HP / %s", tick,
                        trace.marineHpLost, trace.swarmHpLost, hazeCaption(sim)))
                .run(PULSE_TICKS);
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("the-burst-stung", trace.stungAtBurst,
                "marines lost HP within a second of the burst: " + trace.stungAtBurst));
        verdicts.add(Verdict.of("the-haze-pulsed", trace.woundTicksAfterBurst >= 3,
                trace.woundTicksAfterBurst + " later ticks on which the marines lost HP (wanted at least three pulses)"));
        verdicts.add(Verdict.of("the-swarm-was-spared", trace.swarmHpLost == 0f,
                String.format(Locale.ROOT, "%.1f swarm HP lost in its own haze (wanted none)", trace.swarmHpLost)));
        verdicts.add(Verdict.of("the-haze-thinned-away", trace.goneTick >= 0 && !trace.hazeLeftOnGrid,
                "hazes gone at tick " + trace.goneTick + ", haze left on the grid at the end: " + trace.hazeLeftOnGrid));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("marineHpLost", Math.round(trace.marineHpLost));
        metrics.put("swarmHpLost", Math.round(trace.swarmHpLost));
        metrics.put("woundTicksAfterBurst", trace.woundTicksAfterBurst);
        metrics.put("hazeGoneTick", trace.goneTick);
        return new SceneReport(id(), "pulses", PULSE_TICKS, verdicts, metrics);
    }

    private static String hazeCaption(BattleSimulation sim) {
        if (sim.smokeFields().activeHazes().isEmpty()) return "no haze";
        var haze = sim.smokeFields().activeHazes().get(0);
        return String.format(Locale.ROOT, "haze r %.1f of %.1f, %.0f s left", haze.radius(), haze.fullRadius(),
                haze.remaining());
    }

    private static float hpOf(World w, long[] ids) {
        float hp = 0f;
        for (long id : ids) if (w.isAlive(id)) hp += w.hp(id);
        return hp;
    }

    /** Bursts the haze over the shooters (hazed loop only), and reads damage dealt and every hazed line's sight. */
    private static final class ShootTrace implements TickObserver {
        private final boolean hazed;
        private final long[] shooters;
        private final long[] targets;
        private final float targetStartHp;
        float damageDealt;
        int hazedLineSamples;
        int blockedSamples;

        ShootTrace(boolean hazed, SceneWorld world) {
            this.hazed = hazed;
            this.shooters = world.members("shooters");
            this.targets = world.members("targets");
            this.targetStartHp = hpOf(world.sim().world(), targets);
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            if (hazed && tick == BURST_TICK) {
                sim.sporeHazes().burst(sim, 0L, SHOOTER_X + 0.5f, ROW_Y + 0.5f, SHOOT_SCALE);
            }
            World w = sim.world();
            damageDealt = targetStartHp - hpOf(w, targets);
            if (tick % 5 != 0) return;
            NavigationGrid grid = sim.getGrid();
            for (long s : shooters) {
                if (!w.isAlive(s)) continue;
                for (long t : targets) {
                    if (!w.isAlive(t)) continue;
                    if (grid.hazeDepthOnLine(w.x(s), w.y(s), w.x(t), w.y(t)) == 0) continue;
                    hazedLineSamples++;
                    if (!grid.hasLineOfSight(w.cellX(s), w.cellY(s), w.cellX(t), w.cellY(t))) blockedSamples++;
                }
            }
        }
    }

    /** Bursts a haze over each group, and reads the marines' wounds, the swarm's, and when the hazes went. */
    private static final class PulseTrace implements TickObserver {
        private final long[] marines;
        private final long[] swarm;
        private final float marineStartHp;
        private final float swarmStartHp;
        private float lastMarineHp;
        float marineHpLost;
        float swarmHpLost;
        boolean stungAtBurst;
        int woundTicksAfterBurst;
        int goneTick = -1;
        boolean hazeLeftOnGrid;
        private boolean burst;

        PulseTrace(SceneWorld world) {
            World w = world.sim().world();
            this.marines = world.members("marines");
            this.swarm = world.members("swarm");
            this.marineStartHp = hpOf(w, marines);
            this.swarmStartHp = hpOf(w, swarm);
            this.lastMarineHp = marineStartHp;
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            if (tick == BURST_TICK) {
                sim.sporeHazes().burst(sim, 0L, MARINE_HAZE_X + 0.5f, ROW_Y + 0.5f, 1f);
                sim.sporeHazes().burst(sim, 0L, SWARM_HAZE_X + 0.5f, ROW_Y + 0.5f, 1f);
                burst = true;
            }
            World w = sim.world();
            float marineHp = hpOf(w, marines);
            marineHpLost = marineStartHp - marineHp;
            swarmHpLost = swarmStartHp - hpOf(w, swarm);
            if (burst && marineHp < lastMarineHp) {
                if (tick <= BURST_TICK + 30) stungAtBurst = true;
                else woundTicksAfterBurst++;
            }
            lastMarineHp = marineHp;
            if (burst && goneTick < 0 && sim.smokeFields().activeHazes().isEmpty()) goneTick = tick;
            if (tick == PULSE_TICKS - 1) {
                NavigationGrid grid = sim.getGrid();
                hazeLeftOnGrid = grid.hazeDepthOnLine(MARINE_HAZE_X - 5f, ROW_Y + 0.5f, MARINE_HAZE_X + 6f, ROW_Y + 0.5f) > 0
                        || grid.hazeDepthOnLine(SWARM_HAZE_X - 5f, ROW_Y + 0.5f, SWARM_HAZE_X + 6f, ROW_Y + 0.5f) > 0;
            }
        }
    }
}
