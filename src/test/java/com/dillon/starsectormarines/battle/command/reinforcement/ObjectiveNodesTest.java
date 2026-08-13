package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Slice-4 coverage for {@link ObjectiveNodes}: the "assign at deboard, not on
 * arrival" contract's node lookup — exact-anchor hit, the tolerance boundary,
 * and the no-objective / no-map / no-match null cases.
 */
public class ObjectiveNodesTest {

    private static TacticalNode node(int x, int y) {
        return new TacticalNode(TacticalNode.Kind.HEAVY_TOWER, x, y, x - 1, y - 1, x + 1, y + 1,
                Faction.DEFENDER, 50, 4);
    }

    private static ReinforcementRequest requestWithObjective(int objX, int objY) {
        return new ReinforcementRequest(Faction.DEFENDER, ReinforcementRequest.Reason.GARRISON_DEPLETED,
                ReinforcementRequest.Strength.SMALL, 0, 0, objX, objY);
    }

    @Test
    public void resolvesExactAnchorMatch() {
        TacticalNode target = node(10, 55);
        TacticalMap map = new TacticalMap(List.of(target));

        assertEquals(target, ObjectiveNodes.resolve(map, requestWithObjective(10, 55)));
    }

    @Test
    public void resolvesWithinTolerance() {
        TacticalNode target = node(10, 55);
        TacticalMap map = new TacticalMap(List.of(target));

        // Manhattan distance 2 from the anchor — within the tolerance band.
        assertEquals(target, ObjectiveNodes.resolve(map, requestWithObjective(11, 56)));
    }

    @Test
    public void nullBeyondTolerance() {
        TacticalNode target = node(10, 55);
        TacticalMap map = new TacticalMap(List.of(target));

        // Manhattan distance 5 from the anchor — outside the tolerance band.
        assertNull(ObjectiveNodes.resolve(map, requestWithObjective(15, 55)));
    }

    @Test
    public void nullWhenRequestHasNoObjective() {
        TacticalNode target = node(10, 55);
        TacticalMap map = new TacticalMap(List.of(target));
        ReinforcementRequest req = new ReinforcementRequest(Faction.DEFENDER,
                ReinforcementRequest.Reason.GARRISON_DEPLETED, ReinforcementRequest.Strength.SMALL,
                0, 0, ReinforcementRequest.OBJECTIVE_UNSET, ReinforcementRequest.OBJECTIVE_UNSET);

        assertNull(ObjectiveNodes.resolve(map, req));
    }

    @Test
    public void nullWhenMapIsNull() {
        assertNull(ObjectiveNodes.resolve(null, requestWithObjective(10, 55)));
    }
}
