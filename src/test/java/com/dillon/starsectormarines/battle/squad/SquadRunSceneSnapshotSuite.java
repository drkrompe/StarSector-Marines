package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.List;

/**
 * {@link SquadRunScene} recorded at 1920x1080, 100% scale: the walking and the running squad, in the open and past a
 * file of dummies, from the same plays the verdicts are read from.
 *
 * <p>The owner is shown {@code look/squad-*}, a rifle squad ordered across real generated ground and drawn as the
 * player's selection: {@code squad-run}, told to run once under way, the winged foot over its lead and its stamina
 * draining beneath, at play zoom and close; and {@code squad-walk-contact} / {@code squad-run-contact}, the same
 * order past harmless enemy dummies, walking (firing on the move) and running (holding fire while it runs).
 */
public final class SquadRunSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int DUMMY_HP = 100000;

    public SquadRunSceneSnapshotSuite() {
        super(new SquadRunScene(), 1920, 1080);
    }

    @Override protected List<LookReview.Shot> lookShots() {
        return List.of(
                LookReview.Shot.of("squad-run", 60, 24, placement -> stage(placement, true, false),
                        placement -> List.of(LookReview.View.followAtPlay(placement, "rifles"),
                                LookReview.View.follow("close", placement, 24f, "rifles")), 420),
                LookReview.Shot.of("squad-walk-contact", 70, 30, placement -> stage(placement, false, true),
                        placement -> List.of(LookReview.View.followAtPlay(placement, "rifles")), 600)
                        .withStills(28, 34, 40, 46),
                LookReview.Shot.of("squad-run-contact", 70, 30, placement -> stage(placement, true, true),
                        placement -> List.of(LookReview.View.followAtPlay(placement, "rifles")), 600)
                        // Frames every 6 ticks: the runners reach the dummies near frame 16 and are winded near 30.
                        .withStills(14, 18, 22, 26));
    }

    /** A rifle squad selected and ordered east along the window, running once under way, past dummies or not. */
    private static BattleSimulation stage(LookReview.Placement placement, boolean run, boolean dummies) {
        SceneBuilder builder = placement.builder();
        builder.squad("rifles").faction(Faction.MARINE).type(UnitType.MARINE).size(6)
                .primary("weapon.field-rifle").inFile(6, 9, 0, 1).done();
        if (dummies) {
            for (int i = 0; i < 4; i++) {
                builder.unit("dummy-" + i, Faction.DEFENDER, UnitType.MILITIA, 22 + i * 12, 22,
                        spec -> spec.moveSpeed(0f).attackDamage(0f).hp(DUMMY_HP).maxHp(DUMMY_HP));
            }
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        int squad = world.squadId("rifles");
        placement.subject("rifles", world.members("rifles")[0]);
        placement.select(squad);
        // Under way first: the order stands only once the simulation has taken it.
        sim.getSquadMoveOrderService().requestMove(squad, placement.site().x(placement.site().width() - 6),
                placement.site().y(12));
        sim.advance(BattleSimulation.TICK_DT);
        if (run) sim.getSquadMoveOrderService().toggleRun(squad);
        return sim;
    }
}
