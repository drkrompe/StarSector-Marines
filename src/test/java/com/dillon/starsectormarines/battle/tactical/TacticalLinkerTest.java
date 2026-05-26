package com.dillon.starsectormarines.battle.tactical;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Linker-rule coverage for {@link TacticalLinker}. The full BSP city
 * generator covers the integration path; this test pins the discrete
 * link emission rules directly against handcrafted node lists so a
 * regression in one rule doesn't have to wait for a preview-image
 * inspection to catch.
 */
public class TacticalLinkerTest {

    private static TacticalNode node(TacticalNode.Kind kind, int x, int y) {
        return new TacticalNode(kind, x, y, x, y, x, y, Faction.DEFENDER, 50, 4);
    }

    @Test
    public void commandPostFallsBackToNearestCompoundSibling() {
        // Three-leaf compound: COMMAND_POST at origin, BARRACKS 8 cells south,
        // ARMORY 12 cells south. The nearest sibling for COMMAND_POST is the
        // BARRACKS.
        TacticalNode cmd = node(TacticalNode.Kind.COMMAND_POST, 50, 50);
        TacticalNode barracks = node(TacticalNode.Kind.BARRACKS, 50, 58);
        TacticalNode armory = node(TacticalNode.Kind.ARMORY, 50, 62);
        TacticalMap map = new TacticalMap(List.of(cmd, barracks, armory));
        TacticalLinker.link(map);

        List<TacticalNode> cmdFallback = cmd.linkedTo(TacticalNode.LinkKind.FALLBACK_TO);
        assertEquals(1, cmdFallback.size(),
                "COMMAND_POST should link to exactly one fallback target");
        assertEquals(barracks, cmdFallback.get(0),
                "nearest sibling is BARRACKS; ARMORY is further");
    }

    @Test
    public void interiorLeavesAllGetFallbackLinksWhenInRange() {
        // Same compound — every interior leaf must have an outgoing
        // FALLBACK_TO so the squad-level retreat trigger in
        // BattleSimulation.updateSquadFallback can fire from any of them.
        TacticalNode cmd = node(TacticalNode.Kind.COMMAND_POST, 50, 50);
        TacticalNode barracks = node(TacticalNode.Kind.BARRACKS, 50, 58);
        TacticalNode armory = node(TacticalNode.Kind.ARMORY, 50, 62);
        TacticalMap map = new TacticalMap(List.of(cmd, barracks, armory));
        TacticalLinker.link(map);

        assertFalse(cmd.linkedTo(TacticalNode.LinkKind.FALLBACK_TO).isEmpty(),
                "COMMAND_POST must emit a fallback link");
        assertFalse(barracks.linkedTo(TacticalNode.LinkKind.FALLBACK_TO).isEmpty(),
                "BARRACKS must emit a fallback link");
        assertFalse(armory.linkedTo(TacticalNode.LinkKind.FALLBACK_TO).isEmpty(),
                "ARMORY must emit a fallback link");
    }

    @Test
    public void isolatedInteriorLeafGetsNoFallbackLink() {
        // Single command post on the map — no sibling within reach, so the
        // fallback list stays empty rather than self-loop.
        TacticalNode cmd = node(TacticalNode.Kind.COMMAND_POST, 50, 50);
        TacticalMap map = new TacticalMap(List.of(cmd));
        TacticalLinker.link(map);

        assertTrue(cmd.linkedTo(TacticalNode.LinkKind.FALLBACK_TO).isEmpty(),
                "lone interior leaf has no fallback target — list must stay empty");
    }

    @Test
    public void innerPositionDoesNotParticipateInCompoundFallback() {
        // Patch 1 of the slice-6 fix regression-pin: a keep's INNER_POSITION
        // (the antechamber emitted by KeepEntryChamberStamper) lives inside
        // the same leaf bbox as its parent COMMAND_POST. Including it in the
        // interior-leaves fallback set would emit a same-leaf FALLBACK_TO —
        // the throne-room garrison consolidating into the antechamber rather
        // than to a sibling compound. Pin both directions: COMMAND_POST must
        // NOT pick INNER_POSITION as its fallback target even when it's the
        // closest candidate, AND INNER_POSITION must not emit a fallback link
        // of its own.
        TacticalNode cmd = node(TacticalNode.Kind.COMMAND_POST, 50, 50);
        TacticalNode inner = node(TacticalNode.Kind.INNER_POSITION, 50, 53);
        TacticalNode sibling = node(TacticalNode.Kind.BARRACKS, 50, 80);
        TacticalMap map = new TacticalMap(List.of(cmd, inner, sibling));
        TacticalLinker.link(map);

        List<TacticalNode> cmdFallback = cmd.linkedTo(TacticalNode.LinkKind.FALLBACK_TO);
        assertEquals(1, cmdFallback.size(),
                "COMMAND_POST should emit exactly one fallback link (to the sibling BARRACKS, NOT the INNER_POSITION)");
        assertEquals(sibling, cmdFallback.get(0),
                "INNER_POSITION must not appear as a fallback target even when nearer than the sibling compound");
        assertTrue(inner.linkedTo(TacticalNode.LinkKind.FALLBACK_TO).isEmpty(),
                "INNER_POSITION must not emit a compound-leaf fallback link — interior fallback is goal-AI territory");
    }
}
