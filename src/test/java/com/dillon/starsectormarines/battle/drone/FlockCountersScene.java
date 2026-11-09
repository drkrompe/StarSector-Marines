package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The flock's counters measured (`derelict-drone-flocks.md`, slice 6): one derelict flock of sixteen strikes a fire
 * team of six, four ways. {@code rifles}: field rifles in the open. {@code automatics}: squad automatics in the open.
 * {@code grenades}: rifles with frag grenades in the open. {@code indoors}: rifles in a roofed room whose door looks
 * onto the street the flock comes down.
 *
 * <p>This confirms the counters fall out of the model; it is not a balance target. So the only verdicts are mechanisms
 * (no member under the roof, both sides fired, frags thrown at the flock where the team carries them); who lost what, how fast the flock broke, and how many members one
 * tick's fire hurt at once (area fire shows as several) are readings.
 */
public final class FlockCountersScene implements BehaviorScene {

    public static final String RIFLES = "rifles";
    public static final String AUTOMATICS = "automatics";
    public static final String GRENADES = "grenades";
    public static final String INDOORS = "indoors";

    static final int WIDTH = 96, HEIGHT = 56;
    static final int TEAM_X = 62, TEAM_Y = 26;
    /** The room for the indoors loop: x 58..68, y 21..33, roofed, a door on its west wall facing the flock. */
    static final int R_X0 = 58, R_Y0 = 21, R_X1 = 68, R_Y1 = 33;
    static final int TEAM = 6, FLOCK = 16;
    private static final int TICKS = 1500;

    private final int frameEvery;

    public FlockCountersScene() {
        this(10);
    }

    public FlockCountersScene(int frameEvery) {
        this.frameEvery = frameEvery;
    }

    @Override public String id() { return "derelict-flock-counters"; }

    @Override public String label() {
        return "Derelict flock counters: a flock of sixteen against a fire team of six with rifles, squad automatics, "
                + "frag grenades, and rifles fighting from a roofed room";
    }

    @Override public int frameDelayMillis() { return 70; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> out = new ArrayList<>();
        out.add(loop(RIFLES, WeaponRegistry.STARTER_PRIMARY_ID, false, false, frames));
        out.add(loop(AUTOMATICS, WeaponRegistry.SQUAD_AUTOMATIC_ID, false, false, frames));
        out.add(loop(GRENADES, WeaponRegistry.STARTER_PRIMARY_ID, true, false, frames));
        out.add(loop(INDOORS, WeaponRegistry.STARTER_PRIMARY_ID, false, true, frames));
        return out;
    }

    private SceneReport loop(String loop, String primary, boolean grenades, boolean indoors, FrameSink frames) {
        SceneBuilder b = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        if (indoors) {
            b.wall(R_X0, R_Y0, R_X1, R_Y0).wall(R_X0, R_Y1, R_X1, R_Y1).wall(R_X1, R_Y0, R_X1, R_Y1)
                    .wall(R_X0, R_Y0, R_X0, 25).wall(R_X0, 29, R_X0, R_Y1);
        }
        SceneBuilder.SquadSpec team = b.squad("team").faction(Faction.MARINE).size(TEAM).inFile(TEAM_X, TEAM_Y, 1, 1)
                .primary(primary).stationary();
        if (grenades) team.special("special.frag-grenade", TEAM, 3);
        team.done();
        b.wall(WIDTH - 9, HEIGHT - 9, WIDTH - 2, HEIGHT - 9).wall(WIDTH - 9, HEIGHT - 2, WIDTH - 2, HEIGHT - 2)
                .wall(WIDTH - 9, HEIGHT - 9, WIDTH - 9, HEIGHT - 2).wall(WIDTH - 2, HEIGHT - 9, WIDTH - 2, HEIGHT - 2)
                .unit("defender-holdout", Faction.DEFENDER, UnitType.MARINE, WIDTH - 5, HEIGHT - 5);
        SceneWorld world = b.build();
        BattleSimulation sim = world.sim();
        if (indoors) DerelictFlockScene.roof(sim, R_X0, R_Y0, R_X1, R_Y1, 3);
        long[] men = world.members("team");
        Flock flock = sim.flocks().launch(sim, Faction.DEFENDER, 12.5f, 27.5f, FLOCK, FlockWeights.WASP, 200f);
        flock.strike(men[0]);
        Trace trace = new Trace(flock, men);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, frameEvery, tick -> loop + " / tick " + tick + " / " + trace.caption())
                .run(TICKS);
        sim.close();
        List<Verdict> v = new ArrayList<>();
        v.add(Verdict.of("never-indoors", trace.closedTicks == 0,
                trace.closedTicks + " member-ticks in a wall or under the roof"));
        v.add(Verdict.of("both-fired", trace.flockRounds > 0 && trace.teamRounds > 0,
                trace.flockRounds + " rounds from the flock, " + trace.teamRounds + " from the team"));
        if (grenades) {
            v.add(Verdict.of("frags-thrown", trace.grenadesThrown > 0,
                    trace.grenadesThrown + " frags thrown at the flock"));
        }
        return new SceneReport(id(), loop, TICKS, v, trace.readings());
    }

    private static final class Trace implements TickObserver {
        final Flock flock;
        final long[] men;
        final Set<Long> members = new HashSet<>();
        final Map<Long, Float> hp = new HashMap<>();
        final Map<Long, Integer> secondaryAmmo = new HashMap<>();
        int closedTicks, flockRounds, teamRounds, mostHurtInOneTick, ticksHurtingSeveral, brokenTick = -1,
                firstContactTick = -1, menLeft, membersLeft, grenadesThrown;
        private String caption = "";

        Trace(Flock flock, long[] men) {
            this.flock = flock;
            this.men = men;
            members.addAll(flock.memberIds());
        }

        String caption() { return caption; }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            FlockField.Open open = FlockSystem.open(sim.getGrid(), sim.getTopology());
            int hurt = 0;
            membersLeft = 0;
            for (long m : members) {
                boolean alive = sim.resolveUnit(m) != 0L;
                float now = alive ? sim.world().hp(m) : 0f;
                Float before = hp.put(m, now);
                if (before != null && now < before) hurt++;
                if (!alive) continue;
                membersLeft++;
                if (!open.open(sim.world().cellX(m), sim.world().cellY(m))) closedTicks++;
            }
            mostHurtInOneTick = Math.max(mostHurtInOneTick, hurt);
            if (hurt >= 2) ticksHurtingSeveral++;
            if (brokenTick < 0 && membersLeft * 3 < members.size()) brokenTick = tick;
            menLeft = 0;
            for (long man : men) {
                if (sim.resolveUnit(man) == 0L) continue;
                menLeft++;
                if (!sim.world().hasSecondaryWeapon(man)) continue;
                int ammo = sim.world().secondaryAmmo(man);
                Integer before = secondaryAmmo.put(man, ammo);
                if (before != null && ammo < before) grenadesThrown += before - ammo;
            }
            Set<Long> team = new HashSet<>();
            for (long man : men) team.add(man);
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (members.contains(shot.shooterId)) {
                    flockRounds++;
                    if (firstContactTick < 0) firstContactTick = tick;
                }
                if (team.contains(shot.shooterId)) teamRounds++;
            }
            caption = membersLeft + " drones, " + menLeft + " men";
        }

        Map<String, Number> readings() {
            Map<String, Number> m = new LinkedHashMap<>();
            m.put("dronesLost", members.size() - membersLeft);
            m.put("menLost", men.length - menLeft);
            m.put("flockBrokeTick", brokenTick);
            m.put("firstContactTick", firstContactTick);
            m.put("flockRounds", flockRounds);
            m.put("teamRounds", teamRounds);
            m.put("grenadesThrown", grenadesThrown);
            m.put("mostMembersHurtInOneTick", mostHurtInOneTick);
            m.put("ticksHurtingSeveral", ticksHurtingSeveral);
            m.put("closedTicks", closedTicks);
            return m;
        }
    }
}
