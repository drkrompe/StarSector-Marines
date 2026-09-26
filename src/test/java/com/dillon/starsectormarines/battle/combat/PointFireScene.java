package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.infantry.InfantryUnitPrep;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.ScriptedPlayer;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One scripted trigger, with empty ground, a wall, and a crossing body.
 * The script supplies a physical pose and point intent; production firing,
 * burst continuation, visual flight and delayed damage remain authoritative.
 * Squadless actors keep autonomous targeting out of this firing experiment.
 */
public final class PointFireScene implements BehaviorScene {
    private static final int TICKS = 105;
    private static final float SHOOTER_X = 2.5f;
    private static final float LANE_Y = 8.5f;
    private static final float CROSSING_SPEED = -10f / 3f;

    @Override public String id() { return "point-fire"; }

    @Override public String label() {
        return "Point fire: empty ground, physical stops, moving interception and committed bursts";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("empty", "wall", "crossing", "burst")) {
            reports.add(playOne(loop, frames));
        }
        return reports;
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        boolean crossing = loop.equals("crossing");
        boolean wall = loop.equals("wall");
        boolean burst = loop.equals("burst");
        WeaponDef weapon = PointFireFixtures.rifle(burst ? 3 : 1);
        SceneBuilder builder = SceneBuilder.openGround(48, 24).missionCompletion(false)
                .unit("shooter", Faction.MARINE, UnitType.MARINE, 2, 8,
                        spec -> spec.primaryWeapon(weapon).health(1000f))
                .unit("body", Faction.DEFENDER, UnitType.MARINE, crossing ? 8 : 14,
                        crossing ? 10 : wall ? 8 : 21, spec -> spec.health(1000f));
        if (wall) builder.wall(8, 0, 8, 23);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20260926L);
        long shooter = world.unit("shooter");
        long body = world.unit("body");
        FiringSystem firing = new FiringSystem(sim.getGrid(), sim.getRoster());
        Trace trace = new Trace(shooter, body, sim.world().hp(body));
        ScriptedPlayer player = ScriptedPlayer.on(world);
        for (int scheduledTick = 0; scheduledTick < TICKS; scheduledTick++) {
            int tick = scheduledTick;
            player.at(tick, battle -> {
                    InfantryUnitPrep.tickCooldowns(shooter, battle.world());
                    if (crossing) {
                        float y = Math.max(1f,
                                10.5f + CROSSING_SPEED * tick * BattleSimulation.TICK_DT);
                        battle.world().setPos(body, 8.5f, y);
                        BattleComponents components = battle.getRoster().components();
                        battle.getRoster().entityWorld().setFloat(body, components.MOVEMENT,
                                BattleComponents.MOVEMENT_VEL_Y, y > 1f ? CROSSING_SPEED : 0f);
                    }
                    if (tick == 0 || burst && tick == 1) {
                        // Turning the cursor after round one must not retarget the burst.
                        PointFireAim aim = tick == 0
                                ? new PointFireAim(14.5f, LANE_Y)
                                : new PointFireAim(SHOOTER_X, 22f);
                        battle.combat().setPointFireIntent(shooter, aim, FireStance.STANCED);
                        firing.tick(battle);
                    }
                });
        }
        SceneRun.of(world).player(player).observe(trace)
                .frames(frames, loop, 3, tick -> loop + " / point fire / tick " + tick)
                .run(TICKS);
        return report(loop, trace, sim, shooter);
    }

    private SceneReport report(String loop, Trace trace, BattleSimulation sim, long shooter) {
        boolean crossing = loop.equals("crossing");
        boolean wall = loop.equals("wall");
        int expectedShots = loop.equals("burst") ? 3 : 1;
        BallisticResolver.StopKind expected = crossing ? BallisticResolver.StopKind.UNIT_HIT
                : wall ? BallisticResolver.StopKind.WALL : BallisticResolver.StopKind.OVERSHOOT;
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("round-count", trace.shots.size() == expectedShots,
                "emitted " + trace.shots.size() + "; wanted " + expectedShots));
        verdicts.add(Verdict.of("physical-outcome", !trace.shots.isEmpty()
                        && trace.shots.stream().allMatch(shot -> shot.stopKind == expected),
                "wanted " + expected + "; got "
                        + trace.shots.stream().map(shot -> shot.stopKind).toList()));
        verdicts.add(Verdict.of("no-invented-lock", sim.combat().targetId(shooter) == 0L
                        && sim.combat().reflexTargetId(shooter) == 0L
                        && trace.shots.stream().noneMatch(shot -> shot.hit),
                "point contact stays incidental and authors no target identity"));
        if (crossing) {
            float flight = trace.shots.isEmpty() ? Float.POSITIVE_INFINITY
                    : trace.shots.get(0).lifetimeMax;
            float firstDamageTime = trace.firstDamageTick * BattleSimulation.TICK_DT;
            verdicts.add(Verdict.of("damage-on-flight-clock", trace.damage > 0f
                            && trace.firstDamageTick > 0
                            && firstDamageTime + 0.0001f >= flight
                            && firstDamageTime <= flight + BattleSimulation.TICK_DT + 0.0001f,
                    "damage " + trace.damage + " at " + firstDamageTime
                            + " seconds; committed flight " + flight));
        } else {
            verdicts.add(Verdict.of("no-phantom-damage", trace.damage == 0f,
                    "body damage " + trace.damage + "; wanted 0"));
        }
        if (wall) {
            verdicts.add(Verdict.of("wall-near-boundary", trace.shots.stream()
                            .allMatch(shot -> Math.abs(shot.toX - 8f) < 0.0001f),
                    "every round stops at the wall's near edge, x=8"));
        } else if (!crossing) {
            verdicts.add(Verdict.of("range-exit", trace.shots.stream().allMatch(shot ->
                            Math.abs(Math.hypot(shot.toX - shot.fromX, shot.toY - shot.fromY)
                                    - sim.world().attackRange(shooter) * 1.5f) < 0.01f),
                    "unobstructed flight reaches the weapon's modeled reach beyond the cursor"));
        }
        if (loop.equals("burst")) {
            verdicts.add(Verdict.of("burst-keeps-bearing", trace.shots.stream()
                            .allMatch(shot -> shot.toX > 30f && Math.abs(shot.toY - LANE_Y) < 1f)
                            && sim.combat().burstPointAim(shooter) == null,
                    "three eastward rounds despite the cursor moving north; burst retired"));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("shots", trace.shots.size());
        metrics.put("bodyDamage", trace.damage);
        metrics.put("firstDamageTick", trace.firstDamageTick);
        metrics.put("firstFlightSeconds", trace.shots.isEmpty() ? 0f : trace.shots.get(0).lifetimeMax);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final long shooter;
        private final long body;
        private final float initialHp;
        private final List<ShotEvent> shots = new ArrayList<>();
        private float damage;
        private int firstDamageTick = -1;

        private Trace(long shooter, long body, float initialHp) {
            this.shooter = shooter;
            this.body = body;
            this.initialHp = initialHp;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shot.shooterId == shooter) shots.add(shot);
            }
            damage = initialHp - sim.world().hp(body);
            if (damage > 0f && firstDamageTick < 0) firstDamageTick = tick;
        }
    }
}
