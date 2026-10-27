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
 * {@link SquadRunScene} recorded at 1920x1080, 100% scale: the walking and the running squad from the same plays the
 * verdicts are read from.
 *
 * <p>The owner is shown {@code look/squad-run-*}: a rifle squad ordered across real generated ground and told to run
 * once under way, the winged foot over its lead and its stamina draining beneath, at play zoom and close.
 */
public final class SquadRunSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public SquadRunSceneSnapshotSuite() {
        super(new SquadRunScene(), 1920, 1080);
    }

    @Override protected List<LookReview.Shot> lookShots() {
        return List.of(LookReview.Shot.of("squad-run", 60, 24, placement -> {
            SceneBuilder builder = placement.builder();
            builder.squad("rifles").faction(Faction.MARINE).type(UnitType.MARINE).size(6)
                    .primary("weapon.field-rifle").inFile(6, 9, 0, 1).done();
            SceneWorld world = builder.build();
            BattleSimulation sim = world.sim();
            int squad = world.squadId("rifles");
            placement.subject("rifles", world.members("rifles")[0]);
            // Under way first: the order stands only once the simulation has taken it.
            sim.getSquadMoveOrderService().requestMove(squad, placement.site().x(54), placement.site().y(12));
            sim.advance(BattleSimulation.TICK_DT);
            sim.getSquadMoveOrderService().toggleRun(squad);
            return sim;
        }, placement -> List.of(LookReview.View.followAtPlay(placement, "rifles"),
                LookReview.View.follow("close", placement, 24f, "rifles")), 420));
    }
}
