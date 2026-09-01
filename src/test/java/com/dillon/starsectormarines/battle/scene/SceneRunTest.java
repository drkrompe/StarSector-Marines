package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** That the shared loop runs its parts in the order the instruments assume. */
class SceneRunTest {

    private static final int W = 24;
    private static final int H = 12;

    private static SceneWorld world() {
        return SceneBuilder.openGround(W, H)
                .squad("alpha").size(3).at(5, 5).done()
                .unit("far", Faction.DEFENDER, UnitType.MARINE, W - 2, H - 2,
                        spec -> spec.moveSpeed(0f))
                .build();
    }

    @Test
    void observersSeeEveryTickOnceInAscendingOrder() {
        List<Integer> seen = new ArrayList<>();
        List<Integer> alsoSeen = new ArrayList<>();
        SceneRun run = SceneRun.of(world())
                .observe((sim, tick) -> seen.add(tick),
                        (sim, tick) -> alsoSeen.add(tick));

        assertEquals(0, run.tick());
        run.run(5);

        assertEquals(List.of(0, 1, 2, 3, 4), seen);
        assertEquals(seen, alsoSeen, "observers run in registration order on the same ticks");
        assertEquals(5, run.tick());
    }

    @Test
    void stepContinuesFromWhereTheRunStands() {
        List<Integer> seen = new ArrayList<>();
        SceneRun run = SceneRun.of(world()).observe((sim, tick) -> seen.add(tick));
        run.run(2);
        run.step();
        assertEquals(List.of(0, 1, 2), seen);
        assertEquals(3, run.tick());
    }

    @Test
    void theTickSOrdersAreInTheMailboxBeforeTheObserversLook() {
        SceneWorld world = world();
        List<String> log = new ArrayList<>();
        SceneRun run = SceneRun.of(world)
                .player(ScriptedPlayer.on(world)
                        .at(0, sim -> log.add("order@0"))
                        .at(1, sim -> log.add("order@1")))
                .observe((sim, tick) -> log.add("observe@" + tick));

        run.run(2);

        assertEquals(List.of("order@0", "observe@0", "order@1", "observe@1"), log,
                "an observer must see the state the tick will act on");
    }

    @Test
    void framesArriveAtTheCadenceUnderTheirLoopId() {
        List<String> frames = new ArrayList<>();
        SceneRun run = SceneRun.of(world())
                .frames((loopId, sim, tick, caption) ->
                        frames.add(loopId + "/" + tick + "/" + caption),
                        "yielded", 3, tick -> "t" + tick);

        run.run(7);

        assertEquals(List.of("yielded/0/t0", "yielded/3/t3", "yielded/6/t6"), frames);
    }

    @Test
    void aRunWithNoSinkRecordsNothing() {
        SceneRun run = SceneRun.of(world());
        run.run(3);
        assertEquals(3, run.tick());
    }
}
