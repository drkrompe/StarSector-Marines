package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Which posts the works' own guard holds, and that the defender allocation seats them first ({@code RaidWorksGuard}). */
class RaidWorksGuardTest {

    private static TacticalNode node(TacticalNode.Kind kind, Faction guard, int priority, String place) {
        TacticalNode node = new TacticalNode(kind, 0, 0, 0, 0, 0, 0, guard, priority, 3);
        node.setPlace(place);
        return node;
    }

    @Test
    void theWorksGuardPostsAreTheWorksOwnDefenderGuardRoomsAndNothingElse() {
        TacticalNode worksPost = node(TacticalNode.Kind.GUARDPOST, Faction.DEFENDER, 40, RaidSiteTemplate.WORKS);
        TacticalNode garrisonPost = node(TacticalNode.Kind.GUARDPOST, Faction.DEFENDER, 40, RaidSiteTemplate.GARRISON);
        TacticalNode emplacementPost = node(TacticalNode.Kind.GUARDPOST, Faction.DEFENDER, 70, null);
        TacticalNode worksArmory = node(TacticalNode.Kind.ARMORY, Faction.DEFENDER, 70, RaidSiteTemplate.WORKS);
        TacticalNode marinePost = node(TacticalNode.Kind.GUARDPOST, Faction.MARINE, 40, RaidSiteTemplate.WORKS);
        assertEquals(List.of(worksPost),
                RaidWorksGuard.posts(List.of(garrisonPost, worksPost, emplacementPost, worksArmory, marinePost)));
    }

    @Test
    void theWorksPostsAreSeatedBeforeEveryOtherPlaceAndTheRestByPriority() {
        TacticalNode command = node(TacticalNode.Kind.COMMAND_POST, Faction.DEFENDER, 90, RaidSiteTemplate.GARRISON);
        TacticalNode barracks = node(TacticalNode.Kind.BARRACKS, Faction.DEFENDER, 60, RaidSiteTemplate.GARRISON);
        TacticalNode gatePost = node(TacticalNode.Kind.GUARDPOST, Faction.DEFENDER, 40, RaidSiteTemplate.GARRISON);
        TacticalNode first = node(TacticalNode.Kind.GUARDPOST, Faction.DEFENDER, 40, RaidSiteTemplate.WORKS);
        TacticalNode second = node(TacticalNode.Kind.GUARDPOST, Faction.DEFENDER, 40, RaidSiteTemplate.WORKS);
        assertEquals(List.of(first, second, command, barracks, gatePost),
                RaidWorksGuard.seatingOrder(List.of(gatePost, first, barracks, second, command)));
    }

    @Test
    void aMapWithNoWorksSeatsByPriorityAlone() {
        TacticalNode low = node(TacticalNode.Kind.GUARDPOST, Faction.DEFENDER, 40, null);
        TacticalNode high = node(TacticalNode.Kind.ARMORY, Faction.DEFENDER, 70, null);
        assertEquals(List.of(high, low), RaidWorksGuard.seatingOrder(List.of(low, high)));
    }
}
