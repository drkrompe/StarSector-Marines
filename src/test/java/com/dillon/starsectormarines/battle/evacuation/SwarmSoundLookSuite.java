package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.ops.battleview.BattleSoundLog;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * How loud the swarm is against the fight, for the owner rather than for a verdict: a marine line of rifles and squad
 * automatics on real ground, and the Rav coming at it, at play zoom, a frame every tick so the frames run in real time
 * at 30 fps. Every sound the battle screen would start is logged beside the frames ({@link BattleSoundLog},
 * {@code <shot>.jsonl} under {@code -Dsnapshot.swarmSound.logDir}, default {@code build/swarm-sound}), so a review
 * mixes the soundtrack offline from the game's own rules and levels.
 * <ul>
 *   <li>{@code swarm-sound-assault}: a Runner pack led by an elder, whelps among it, and two Brutes behind, tough
 *       enough to reach the line and charge it.</li>
 *   <li>{@code swarm-sound-matriarch}: the Matriarch walking in with a few Runners.</li>
 * </ul>
 * Opt-in: a mixing study, not part of {@code all}.
 */
public final class SwarmSoundLookSuite implements SnapshotSuite {

    static final int W = 64, H = 36;
    static final int TICKS = 900;
    static final int[] STILLS = {150, 300, 450, 600, 750};
    /** The Brutes' health against their own: enough to reach the line and charge it under the fire. */
    static final float BRUTE_HEALTH = 3f;

    @Override public String id() { return "swarm-sound"; }

    @Override public String label() { return "Swarm sound: a marine line against Runners, Brutes and the Matriarch, with the sounds logged"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        Path logs = Path.of(System.getProperty("snapshot.swarmSound.logDir",
                context.projectRoot().resolve("build/swarm-sound").toString()));
        List<SnapshotArtifact> out = new ArrayList<>();
        out.addAll(LookReview.record(context.modRoot(), shot("swarm-sound-assault", logs, false)));
        out.addAll(LookReview.record(context.modRoot(), shot("swarm-sound-matriarch", logs, true)));
        return out;
    }

    private static LookReview.Shot shot(String name, Path logs, boolean matriarch) {
        return new LookReview.Shot(name, LookGround.verdant(), W, H,
                placement -> {
                    BattleSimulation sim = matriarch ? matriarch(placement) : assault(placement);
                    BattleSoundLog.attach(placement, LookReview.View.followAtPlay(placement, "line", "swarm"),
                            logs.resolve(name + ".jsonl"));
                    return sim;
                },
                placement -> List.of(LookReview.View.followAtPlay(placement, "line", "swarm")),
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
        BattleSimulation sim = line(placement).sim();
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
