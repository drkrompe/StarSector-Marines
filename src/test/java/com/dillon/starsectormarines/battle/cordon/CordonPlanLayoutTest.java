package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.world.gen.layout.Deferred;
import com.dillon.starsectormarines.battle.world.gen.layout.Phase;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.pool.MapPatch;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class CordonPlanLayoutTest {

    @Test
    void theCordonLaysItsPicketsOnceTheRedoubtsStand() {
        PrecinctPlan plan = CordonPlan.planFor(CordonVariant.LINE, CordonPlan.Density.TOWN, 560, 336, 7117L);
        Piece pickets = plan.layout().find(CordonPlan.PICKETS);
        Deferred deferred = assertInstanceOf(Deferred.class, pickets);
        assertEquals(Phase.WORKS, deferred.phase());
        assertFalse(deferred.required(), "a picket course broken by the town is still a Cordon");
    }

    @Test
    void aPinnedPlanKeepsItsLayout() {
        PrecinctPlan plan = CordonPlan.planFor(CordonVariant.LINE, 560, 336, 3L);
        MapPatch patch = new MapPatch(List.of(new MapPatch.Pin(CordonPlan.REDOUBT_PLACE + 2, 200, 130)), List.of());
        assertSame(plan.layout(), patch.pinned(plan).layout());
    }
}
