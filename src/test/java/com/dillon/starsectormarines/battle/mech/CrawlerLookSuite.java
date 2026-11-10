package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.appearance.FacingSystem;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ops.battleview.ReviewFactions;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.List;

/**
 * Tri-Tachyon's light crawler moving (`mech-lines.md`, high-tech light), for judging its gait: one crawler in
 * Tri-Tachyon's livery on verdant ground, its pace scripted a step each tick so the legs show every gait the rig has.
 * It stands, walks east, breaks into a run, stops, then turns on the spot through a half circle. Two views: one close
 * and following, one fixed on the whole route. Owner-facing look frames only; no outcome is judged.
 */
public final class CrawlerLookSuite implements SnapshotSuite {

    static final String KEY = "crawler";
    /** The script, ticks: standing, walking, running, standing, turning, standing. */
    static final int STAND = 30;
    static final int WALK = 150;
    static final int RUN = 120;
    static final int HALT = 45;
    static final int TURN = 75;
    static final int REST = 30;
    /** Paces, cells a second: a walk under the rig's run threshold, a run over it (DerelictLegRig.CRAWLER). */
    static final float WALK_SPEED = 0.9f;
    static final float RUN_SPEED = 2.2f;
    /** Facing east, then turned through this many degrees on the spot. */
    static final float EAST = -90f;
    static final float TURN_DEGREES = 180f;
    static final float ROUTE_CELLS = (WALK * WALK_SPEED + RUN * RUN_SPEED) * BattleSimulation.TICK_DT;

    @Override public String id() { return "crawler-look"; }

    @Override public String label() { return "Tri-Tachyon's light crawler standing, walking, running and turning"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        try (ReviewFactions ignored = ReviewFactions.bind("tritachyon", "pirates")) {
            return LookReview.record(context.modRoot(), shot());
        }
    }

    static LookReview.Shot shot() {
        int ticks = STAND + WALK + RUN + HALT + TURN + REST;
        float[] start = new float[2];
        return new LookReview.Shot("crawler-gait", LookGround.verdant(), 32, 16, placement -> {
            SceneBuilder builder = placement.holdouts(placement.builder());
            float[] at = placement.openNear(16f - ROUTE_CELLS / 2f, 8f);
            start[0] = (int) at[0] + 0.5f;
            start[1] = (int) at[1] + 0.5f;
            int holdX = (int) at[0];
            int holdY = (int) at[1];
            builder.squad(KEY).faction(Faction.MARINE).size(1)
                    .at(holdX - placement.site().x0(), holdY - placement.site().y0())
                    .mech(MechVariant.CRAWLER, MechRole.ASSAULT)
                    .assigned(id -> ObjectiveAssignment.defendArea(id, holdX, holdY))
                    .done();
            SceneWorld world = builder.build();
            long crawler = placement.subject(KEY, world.members(KEY)[0]);
            // Faced down the route from the first frame: a mech turns at its own rate in battle, never in one snap.
            world.sim().world().setPos(crawler, start[0], start[1]);
            FacingSystem.faceStanding(world.sim().getEntityWorld(), world.sim().getBattleComponents(), crawler, EAST);
            int[] tick = {0};
            placement.beforeTick(sim -> {
                int t = tick[0]++;
                sim.world().setPos(crawler, start[0] + travelled(t), start[1]);
                FacingSystem.faceStanding(sim.getEntityWorld(), sim.getBattleComponents(), crawler, facing(t));
            });
            return world.sim();
        }, placement -> List.of(
                LookReview.View.follow("close", placement, 9f, KEY),
                new LookReview.View("route", sim -> new float[] {start[0] + ROUTE_CELLS / 2f, start[1],
                        ROUTE_CELLS + 6f}, true)),
                ticks, 1, 0, new int[] {STAND + WALK / 2, STAND + WALK + RUN / 2,
                        STAND + WALK + RUN + HALT + TURN / 2});
    }

    /** How far east the crawler has come by tick {@code t}, cells. */
    static float travelled(int t) {
        int walking = Math.max(0, Math.min(WALK, t - STAND));
        int running = Math.max(0, Math.min(RUN, t - STAND - WALK));
        return (walking * WALK_SPEED + running * RUN_SPEED) * BattleSimulation.TICK_DT;
    }

    /** Its heading at tick {@code t}: east, then swung round on the spot once it has stopped. */
    static float facing(int t) {
        int turning = Math.max(0, Math.min(TURN, t - STAND - WALK - RUN - HALT));
        return EAST + TURN_DEGREES * turning / TURN;
    }
}
