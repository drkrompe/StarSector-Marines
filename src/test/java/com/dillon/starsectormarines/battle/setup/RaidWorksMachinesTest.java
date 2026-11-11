package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Security's machines at a Raid works, on top of the market's force ({@code raid-demolish-the-works.md}, slice 6). */
class RaidWorksMachinesTest {

    private static TacticalNode post(String place, int machines) {
        TacticalNode node = new TacticalNode(TacticalNode.Kind.INNER_POSITION, 5, 5, 5, 5, 5, 5, Faction.DEFENDER, 75,
                machines);
        node.setPlace(place);
        return node;
    }

    @Test
    void everyMachinePostIsFilledFromTheMarketsOwnLineWhateverItsForce() {
        List<TacticalNode> fortified = List.of(post(RaidSiteTemplate.WORKS_MACHINES, 1),
                post(RaidSiteTemplate.WORKS_MACHINES, 1), post(RaidSiteTemplate.WORKS_RESERVE, 2));
        assertEquals(List.of(MechVariant.HOUND, MechVariant.TORCH, MechVariant.HOUND, MechVariant.TORCH),
                RaidWorksMachines.extraMechs(fortified, List.of(MechVariant.HOUND, MechVariant.TORCH)));
        assertEquals(List.of(MechVariant.BULWARK), RaidWorksMachines.extraMechs(
                List.of(post(RaidSiteTemplate.WORKS_MACHINES, 1)), List.of()), "a line with no mechs posts Bulwarks");
        assertEquals(List.of(), RaidWorksMachines.extraMechs(List.of(), List.of(MechVariant.HOUND)),
                "an open works has no posts, so nothing is added");
    }

    @Test
    void everyForwardPostIsFilledWithRiflemenOnTopOfTheForce() {
        assertEquals(8, RaidWorksMachines.extraRiflemen(List.of(post(RaidSiteTemplate.WORKS_FORWARD, 4),
                post(RaidSiteTemplate.WORKS_FORWARD, 4))).size());
    }

    @Test
    void aGateGetsALightHullAndTheYardAHeavyOne() {
        TacticalNode gate = post(RaidSiteTemplate.WORKS_MACHINES, 1);
        TacticalNode yard = post(RaidSiteTemplate.WORKS_RESERVE, 2);
        assertEquals(RaidWorksMachines.gateHull("hegemony"), RaidWorksMachines.hullFor(gate, "hegemony"));
        assertEquals(RaidWorksMachines.yardHull("hegemony"), RaidWorksMachines.hullFor(yard, "hegemony"));
        assertNull(RaidWorksMachines.hullFor(post(RaidSiteTemplate.GARRISON, 4), "hegemony"));
    }
}
