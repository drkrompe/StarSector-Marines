package com.dillon.starsectormarines.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dillon.starsectormarines.campaign.refit.RefitBand;
import com.dillon.starsectormarines.campaign.refit.RefitTrack;
import com.dillon.starsectormarines.campaign.refit.ShipCondition;
import com.dillon.starsectormarines.ops.ShipSystemsTree.Node;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShipSystemsTreeTest {
    @Test
    void everyTrackHangsInTheTreeExactlyOnce() {
        var seen = new ArrayList<RefitTrack>();
        for (var branch : ShipSystemsTree.BRANCHES.entrySet()) {
            assertEquals(RefitTrack.Group.CORE, branch.getKey().group, branch.getKey() + " is not a core");
            seen.add(branch.getKey());
            seen.addAll(branch.getValue());
        }
        assertEquals(RefitTrack.values().length, seen.size());
        assertEquals(EnumSet.allOf(RefitTrack.class), EnumSet.copyOf(seen));
    }

    @Test
    void aTierIsAvailableOnlyWhenPriceIsAllThatStandsInItsWay() {
        var start = ShipCondition.battered();
        assertEquals(Node.AVAILABLE, ShipSystemsTree.node(start, RefitTrack.STRUCTURE, RefitBand.RESTORED));
        assertEquals(Node.LOCKED, ShipSystemsTree.node(start, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED), "waits on Power");
        assertEquals(Node.LOCKED, ShipSystemsTree.node(start, RefitTrack.POWER, RefitBand.IMPROVED_I), "previous tier first");
        assertEquals(Node.ABSENT, ShipSystemsTree.node(start, RefitTrack.RESERVE_HANGAR, RefitBand.IMPROVED_II));

        var powered = start.with(RefitTrack.POWER, RefitBand.RESTORED);
        assertEquals(Node.OWNED, ShipSystemsTree.node(powered, RefitTrack.POWER, RefitBand.RESTORED));
        assertEquals(Node.LOCKED, ShipSystemsTree.node(powered, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED), "and sealed bulkheads");
        var sealed = powered.with(RefitTrack.STRUCTURE, RefitBand.RESTORED);
        assertEquals(Node.AVAILABLE, ShipSystemsTree.node(sealed, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED));
    }

    @Test
    void theWorkshopIsReadHereButNeverOrderedHere() {
        var start = ShipCondition.battered();
        assertEquals(Node.LOCKED, ShipSystemsTree.node(start, RefitTrack.FABRICATION, RefitBand.RESTORED));
        var repaired = start.with(RefitTrack.FABRICATION, RefitBand.RESTORED);
        assertEquals(Node.OWNED, ShipSystemsTree.node(repaired, RefitTrack.FABRICATION, RefitBand.RESTORED));
        assertEquals(Node.LOCKED, ShipSystemsTree.node(repaired, RefitTrack.FABRICATION, RefitBand.IMPROVED_I));
    }

    @Test
    void everyTreeGraphicParses() throws Exception {
        var names = new ArrayList<String>();
        for (RefitTrack track : RefitTrack.values()) names.add(ShipSystemsTree.icon(track));
        for (int tier = 1; tier <= 3; tier++)
            for (String state : List.of("owned", "available", "locked")) names.add("graphics/ui/refit/node-" + tier + "-" + state + ".svg");
        for (String name : names) {
            Path file = Path.of("mod").resolve(name);
            assertTrue(Files.isRegularFile(file), name);
            assertNotNull(SvgAsset.parse(Files.readString(file)), name);
        }
    }
}
