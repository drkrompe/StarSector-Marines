package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.List;

/**
 * A firebomb stick into a wood (`fire-nouns.md`, Blazes), in the look preset: brush and trees with open meadow round
 * them on every side, and the six rounds of {@link com.dillon.starsectormarines.battle.air.AirOrdnance#INCENDIARY_BOMBS}
 * laid in a line across its middle two seconds in, as their heat lands, without the aircraft. The fire is big enough
 * to blaze: it runs on through the wood a burst further than any flamer's fire, then dies down and stops inside the
 * wood, short of the meadow. Stills at fixed moments (lit, blazing, at its widest, burnt out), at play zoom and whole.
 */
public class FireBlazeLookSuite implements SnapshotSuite {

    private static final int WINDOW_W = 96, WINDOW_H = 56;
    /** The wood, in window cells: a margin of meadow on every side. */
    private static final int WOOD_X0 = 14, WOOD_Y0 = 8, WOOD_X1 = 82, WOOD_Y1 = 48;
    private static final int SECONDS = 70, DROP_TICK = 60, EVERY_TICKS = 30;
    /** The stick: rounds as far apart as the bomber's run lays them, each with its blast radius and heat. */
    private static final int ROUNDS = 6;
    private static final float ROUND_GAP = 3f, ROUND_RADIUS = 4f;
    private static final int ROUND_HEAT = 3000;

    @Override public String id() { return "fire-blaze"; }

    @Override public String label() { return "Look: a firebomb stick blazes through a wood and burns out inside it"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        LookReview.Shot shot = new LookReview.Shot("firebomb", LookGround.verdant(), WINDOW_W, WINDOW_H, placement -> {
            LookGround.Site site = placement.site();
            FlamerForestLookSuite.plantWood(placement.map(), site, WOOD_X0, WOOD_Y0, WOOD_X1, WOOD_Y1, WOOD_X0,
                    Integer.MIN_VALUE, Integer.MIN_VALUE);
            SceneWorld world = placement.builder().build();
            float cx = site.x(WINDOW_W / 2f), cy = site.y(WINDOW_H / 2f);
            placement.beforeTick(sim -> {
                if (sim.getSimTickIndex() == DROP_TICK) drop(sim, cx, cy);
                log(sim);
            });
            return world.sim();
        }, placement -> {
            LookGround.Site site = placement.site();
            float cx = site.x(WINDOW_W / 2f), cy = site.y(WINDOW_H / 2f);
            return List.of(LookReview.View.play(cx, cy), LookReview.View.close("whole", cx, cy, WINDOW_W));
        }, 30 * SECONDS, EVERY_TICKS, 4).withStills(4, 14, 30, 68);
        return LookReview.record(context.modRoot(), shot);
    }

    private static void drop(BattleSimulation sim, float cx, float cy) {
        float first = cx - ROUND_GAP * (ROUNDS - 1) / 2f;
        for (int i = 0; i < ROUNDS; i++) sim.fireField().addHeat(first + i * ROUND_GAP, cy, ROUND_RADIUS, ROUND_HEAT);
    }

    /** Every five seconds: cells burning, of them blazing, and burnt. */
    private static void log(BattleSimulation sim) {
        if (sim.getSimTickIndex() % 150 != 0) return;
        FireField fire = sim.fireField();
        int blazing = 0, burnt = 0;
        for (int i = 0; i < fire.burningCount(); i++) if (fire.isBlaze(fire.burningCell(i))) blazing++;
        for (int i = 0, n = fire.width() * fire.height(); i < n; i++) if (fire.stateAt(i) == FireField.BURNT) burnt++;
        System.out.printf("    [fire-blaze] t=%ds burning %d, blazing %d, burnt %d%n",
                sim.getSimTickIndex() / 30, fire.burningCount(), blazing, burnt);
    }
}
