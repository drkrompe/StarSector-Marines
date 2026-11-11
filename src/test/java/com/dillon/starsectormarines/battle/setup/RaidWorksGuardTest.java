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
    void theWorksGuardPostsAreTheWorksOwnDefenderGuardRoomsAndEmplacementsAndNothingElse() {
        TacticalNode worksPost = node(TacticalNode.Kind.GUARDPOST, Faction.DEFENDER, 40, RaidSiteTemplate.WORKS);
        TacticalNode worksGun = node(TacticalNode.Kind.GUARDPOST, Faction.DEFENDER, 70, RaidSiteTemplate.WORKS);
        TacticalNode garrisonPost = node(TacticalNode.Kind.GUARDPOST, Faction.DEFENDER, 40, RaidSiteTemplate.GARRISON);
        TacticalNode emplacementPost = node(TacticalNode.Kind.GUARDPOST, Faction.DEFENDER, 70, null);
        TacticalNode worksArmory = node(TacticalNode.Kind.ARMORY, Faction.DEFENDER, 70, RaidSiteTemplate.WORKS);
        TacticalNode marinePost = node(TacticalNode.Kind.GUARDPOST, Faction.MARINE, 40, RaidSiteTemplate.WORKS);
        assertEquals(List.of(worksPost, worksGun),
                RaidWorksGuard.posts(List.of(garrisonPost, worksPost, emplacementPost, worksArmory, worksGun, marinePost)));
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
    void theWorksMachinePostsAreSeatedRightAfterItsGuardAndBeforeEverythingElse() {
        TacticalNode command = node(TacticalNode.Kind.COMMAND_POST, Faction.DEFENDER, 90, RaidSiteTemplate.GARRISON);
        TacticalNode guard = node(TacticalNode.Kind.GUARDPOST, Faction.DEFENDER, 40, RaidSiteTemplate.WORKS);
        TacticalNode gate = node(TacticalNode.Kind.INNER_POSITION, Faction.DEFENDER, 75, RaidSiteTemplate.WORKS_MACHINES);
        assertEquals(List.of(gate), RaidWorksGuard.machinePosts(List.of(command, guard, gate)));
        assertEquals(List.of(guard, gate, command), RaidWorksGuard.seatingOrder(List.of(command, gate, guard)));
    }

    @Test
    void aForwardPostTakesOnlyRiflemenAndIsSeatedAfterTheMachines() {
        assertEquals(RaidWorksGuard.Holder.INFANTRY, RaidWorksGuard.holder(false, false, true, true, true));
        assertEquals(RaidWorksGuard.Holder.NONE, RaidWorksGuard.holder(false, false, true, true, false),
                "a forward post never takes a mech");
        TacticalNode command = node(TacticalNode.Kind.COMMAND_POST, Faction.DEFENDER, 90, RaidSiteTemplate.GARRISON);
        TacticalNode gate = node(TacticalNode.Kind.INNER_POSITION, Faction.DEFENDER, 75, RaidSiteTemplate.WORKS_MACHINES);
        TacticalNode forward = node(TacticalNode.Kind.INNER_POSITION, Faction.DEFENDER, 75, RaidSiteTemplate.WORKS_FORWARD);
        assertEquals(List.of(gate, forward, command), RaidWorksGuard.seatingOrder(List.of(command, forward, gate)));
    }

    @Test
    void aMachinePostTakesOnlyMachinesAndAGuardPostRiflemenFirst() {
        assertEquals(RaidWorksGuard.Holder.MACHINES, RaidWorksGuard.holder(false, true, true, true));
        assertEquals(RaidWorksGuard.Holder.NONE, RaidWorksGuard.holder(false, true, false, true),
                "no machines left: the post stays empty, the market's force is not grown for it");
        assertEquals(RaidWorksGuard.Holder.INFANTRY, RaidWorksGuard.holder(true, false, true, true));
        assertEquals(RaidWorksGuard.Holder.MACHINES, RaidWorksGuard.holder(true, false, true, false));
        assertEquals(RaidWorksGuard.Holder.MACHINES, RaidWorksGuard.holder(false, false, true, true));
        assertEquals(RaidWorksGuard.Holder.INFANTRY, RaidWorksGuard.holder(false, false, false, true));
        assertEquals(RaidWorksGuard.Holder.NONE, RaidWorksGuard.holder(false, false, false, false));
    }

    @Test
    void aMapWithNoWorksSeatsByPriorityAlone() {
        TacticalNode low = node(TacticalNode.Kind.GUARDPOST, Faction.DEFENDER, 40, null);
        TacticalNode high = node(TacticalNode.Kind.ARMORY, Faction.DEFENDER, 70, null);
        assertEquals(List.of(high, low), RaidWorksGuard.seatingOrder(List.of(low, high)));
    }
}
