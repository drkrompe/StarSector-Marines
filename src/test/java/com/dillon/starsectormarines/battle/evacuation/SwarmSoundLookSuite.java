package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * How loud the swarm is against the fight, for the owner rather than for a verdict: a marine line of rifles and squad
 * automatics on real ground, and the Rav coming at it, at play zoom, a frame every tick so the frames run in real time
 * at 30 fps. With {@code -Dsnapshot.lookSound=true} every sound the battle screen would start is logged
 * ({@link BattleSoundLog}, {@code <shot>.jsonl} under {@code build/look-sound}) and mixed into each MP4's soundtrack
 * from the game's own rules and levels ({@link LookReview}).
 * <ul>
 *   <li>{@code swarm-sound-assault}: a Runner pack led by an elder, whelps among it, and two Brutes behind, tough
 *       enough to reach the line and charge it.</li>
 *   <li>{@code swarm-sound-matriarch}: the Matriarch walking in with a few Runners.</li>
 *   <li>{@code swarm-sound-matriarch-run}: a rifle team caught out forward of the line, close to the Matriarch as
 *       she comes out, falls back to it. She tells, runs it down before it gets there and reaps it.</li>
 *   <li>{@code swarm-sound-skitterers}: a nest at work ({@link SwarmWorkerLookSuite#nest}) with a Runner pack round
 *       its mouth, and a rifle line walking up on it from the west: the workers patter, haul and mend, the pack goes
 *       for the line, and the nest's workers scatter under the fire.</li>
 *   <li>{@code swarm-sound-feeding}: close on a nest, its workers hauling the dead lying round it home and dropping
 *       each down the mouth, where it slides in, sinks and is swallowed ({@link SwarmHarvest#feeding}).</li>
 * </ul>
 * {@code -Dsnapshot.swarmSound.only=<shot>} records one shot alone. Opt-in: a mixing study, not part of {@code all}.
 */
public final class SwarmSoundLookSuite implements SnapshotSuite {

    static final int W = 64, H = 36;
    static final int TICKS = 900;
    static final int[] STILLS = {150, 300, 450, 600, 750};
    static final int FEEDING_TICKS = 750;
    static final int[] FEEDING_STILLS = {150, 300, 450, 600};
    /** The Brutes' health against their own: enough to reach the line and charge it under the fire. */
    static final float BRUTE_HEALTH = 3f;

    @Override public String id() { return "swarm-sound"; }

    @Override public String label() { return "Swarm sound: a marine line against Runners, Brutes and the Matriarch, to hear with -Dsnapshot.lookSound=true"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        String only = System.getProperty("snapshot.swarmSound.only", "");
        List<SnapshotArtifact> out = new ArrayList<>();
        for (String name : List.of("swarm-sound-assault", "swarm-sound-matriarch", "swarm-sound-matriarch-run",
                "swarm-sound-skitterers", "swarm-sound-feeding")) {
            if (only.isEmpty() || only.equals(name)) out.addAll(LookReview.record(context.modRoot(), shot(name)));
        }
        return out;
    }

    private static LookReview.Shot shot(String name) {
        if (name.endsWith("-feeding")) {
            return new LookReview.Shot(name, LookGround.verdant(), SwarmWorkerLookSuite.W, SwarmWorkerLookSuite.H,
                    SwarmSoundLookSuite::feeding,
                    placement -> List.of(LookReview.View.follow("close", placement, 18f, "nest")),
                    FEEDING_TICKS, 1, 2, FEEDING_STILLS);
        }
        boolean nest = name.endsWith("-skitterers");
        String[] keys = nest ? new String[]{"nest"} : new String[]{"line", "swarm"};
        return new LookReview.Shot(name, LookGround.verdant(), nest ? SwarmWorkerLookSuite.W : W,
                nest ? SwarmWorkerLookSuite.H : H,
                placement -> switch (name) {
                    case "swarm-sound-assault" -> assault(placement);
                    case "swarm-sound-matriarch-run" -> caughtOut(placement);
                    case "swarm-sound-skitterers" -> skitterers(placement);
                    default -> matriarch(placement);
                },
                placement -> List.of(LookReview.View.followAtPlay(placement, keys)),
                TICKS, 1, 2, STILLS);
    }

    /** Six rifles and two squad automatics holding in the west, given health to last the shot. */
    private static SceneWorld line(LookReview.Placement placement) {
        SceneBuilder builder = placement.builder();
        float[] at = placement.openNear(10f, H / 2f);
        int x = (int) at[0] - placement.site().x0(), y = (int) at[1] - placement.site().y0();
        builder.squad("rifles").faction(Faction.MARINE).size(6).at(x, y - 2)
                .primary("weapon.field-rifle").health(600f).stationary().done();
        builder.squad("autos").faction(Faction.MARINE).size(2).at(x - 1, y + 3)
                .primary("weapon.squad-automatic").health(600f).stationary().done();
        SceneWorld world = builder.build();
        placement.subject("line", world.members("rifles")[0]);
        return world;
    }

    private static BattleSimulation assault(LookReview.Placement placement) {
        BattleSimulation sim = line(placement).sim();
        for (int i = 0; i < 10; i++) {
            float[] at = placement.openNear(40f + (i % 3) * 2f, H / 2f - 5f + i);
            SwarmStage stage = i == 0 ? SwarmStage.ELDER : i % 4 == 3 ? SwarmStage.WHELP : SwarmStage.ADULT;
            long runner = sim.spawn(SwarmStrains.spec("Runner " + i, UnitType.SWARM_RUNNER, (int) at[0], (int) at[1])
                    .swarmStage(stage));
            if (i == 0) placement.subject("swarm", runner);
        }
        for (int i = 0; i < 2; i++) {
            float[] at = placement.openNear(44f, H / 2f - 4f + i * 8f);
            sim.spawn(SwarmStrains.spec("Brute " + i, UnitType.SWARM_BRUTE, (int) at[0], (int) at[1])
                    .health(UnitType.SWARM_BRUTE.maxHp * BRUTE_HEALTH));
        }
        return sim;
    }

    private static BattleSimulation matriarch(LookReview.Placement placement) {
        return matriarch(placement, line(placement).sim());
    }

    /**
     * The worker look's nest well east, a mother's brood at work on colonies all shot half down, a Runner pack at its
     * mouth, and the line far in the west ordered onto the bore, so the nest is heard at work
     * before the fire reaches it and its workers scatter when it does.
     */
    private static BattleSimulation skitterers(LookReview.Placement placement) {
        int h = SwarmWorkerLookSuite.H;
        int x0 = placement.site().x0(), y0 = placement.site().y0();
        int bx = x0 + 52, by = y0 + h / 2;
        SceneBuilder builder = placement.builder();
        float[] at = placement.openNear(12f, h / 2f);
        int x = (int) at[0] - x0, y = (int) at[1] - y0;
        int toX = bx - 3, toY = by;
        builder.squad("rifles").faction(Faction.MARINE).size(6).at(x, y - 2)
                .primary("weapon.field-rifle").health(600f)
                .assigned(id -> ObjectiveAssignment.withdraw(id, toX, toY - 2)).done();
        builder.squad("autos").faction(Faction.MARINE).size(2).at(x - 1, y + 3)
                .primary("weapon.squad-automatic").health(600f)
                .assigned(id -> ObjectiveAssignment.withdraw(id, toX - 1, toY + 3)).done();
        SceneWorld world = builder.build();
        placement.subject("line", world.members("rifles")[0]);
        BattleSimulation sim = world.sim();
        SwarmBore bore = SwarmWorkerLookSuite.nest(placement, sim, bx, by, true);
        for (long colony : bore.colonies()) sim.world().setHp(colony, sim.world().maxHp(colony) * 0.45f);
        // The company has eyes on the nest from the start, so it is heard at work before the line sees it.
        sim.getFogOfWar().addEphemeralSource(bx, by, 16, 16f);
        for (int i = 0; i < 6; i++) {
            float[] r = placement.openNear(bx - x0 + 3f + (i % 2) * 2f, h / 2f - 5f + i * 2f);
            sim.spawn(SwarmStrains.spec("Runner " + i, UnitType.SWARM_RUNNER, (int) r[0], (int) r[1]));
        }
        return sim;
    }

    /**
     * A mother's nest at work with dead Runners lying round its mouth, seen from the start; no fight, so its workers
     * haul each home in turn.
     */
    private static BattleSimulation feeding(LookReview.Placement placement) {
        BattleSimulation sim = placement.builder().build().sim();
        int bx = placement.site().x0() + SwarmWorkerLookSuite.W / 2, by = placement.site().y0() + SwarmWorkerLookSuite.H / 2;
        SwarmWorkerLookSuite.nest(placement, sim, bx, by, true);
        sim.getFogOfWar().addEphemeralSource(bx, by, 16, 16f);
        for (int[] at : new int[][]{{bx + 5, by - 3}, {bx - 4, by + 4}, {bx + 3, by + 5}, {bx - 6, by - 2}}) {
            long dead = SwarmStrains.spawn(sim, "Fallen Runner", UnitType.SWARM_RUNNER, at[0], at[1]);
            sim.applyDamage(dead, 1e6f, 1e6f);
        }
        return sim;
    }

    /** The line, a rifle team a few cells in front of where the Matriarch comes out, ordered back to the line. */
    private static BattleSimulation caughtOut(LookReview.Placement placement) {
        SceneBuilder builder = placement.builder();
        float[] home = placement.openNear(10f, H / 2f);
        float[] out = placement.openNear(25f, H / 2f);
        int x0 = placement.site().x0(), y0 = placement.site().y0();
        int homeX = (int) home[0] - x0, homeY = (int) home[1] - y0;
        builder.squad("caught").faction(Faction.MARINE).size(4).at((int) out[0] - x0, (int) out[1] - y0)
                .primary("weapon.field-rifle").health(600f)
                .assigned(id -> ObjectiveAssignment.withdraw(id, homeX, homeY)).done();
        builder.squad("rifles").faction(Faction.MARINE).size(6).at(homeX, homeY - 2)
                .primary("weapon.field-rifle").health(600f).stationary().done();
        builder.squad("autos").faction(Faction.MARINE).size(2).at(homeX - 1, homeY + 3)
                .primary("weapon.squad-automatic").health(600f).stationary().done();
        SceneWorld world = builder.build();
        placement.subject("line", world.members("caught")[0]);
        return matriarch(placement, world.sim());
    }

    private static BattleSimulation matriarch(LookReview.Placement placement, BattleSimulation sim) {
        float[] at = placement.openNear(30f, H / 2f);
        placement.subject("swarm", sim.spawn(SwarmStrains.spec("Matriarch", UnitType.SWARM_MATRIARCH,
                (int) at[0], (int) at[1])));
        for (int i = 0; i < 4; i++) {
            float[] r = placement.openNear(36f, H / 2f - 6f + i * 4f);
            sim.spawn(SwarmStrains.spec("Runner " + i, UnitType.SWARM_RUNNER, (int) r[0], (int) r[1]));
        }
        return sim;
    }
}
