package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.ship.stage.HullProfileStage;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckArrangementTest {

    private static DeckProfile profile(int frames) {
        int[] top = new int[frames];
        int[] bottom = new int[frames];
        DeckZone[] zone = new DeckZone[frames];
        for (int f = 0; f < frames; f++) {
            top[f] = 0;
            bottom[f] = 19;
            zone[f] = DeckZone.MIDSHIPS;
        }
        return new DeckProfile(9, 10, top, bottom, zone);
    }

    @Test
    void aSiteHoldsOnlyItsStretchAndItsSide() {
        DeckProfile deck = profile(101);
        var site = new DeckArrangement.Site(RoomPurpose.HANGAR, 0.40f, 0.50f, DeckSide.PORT);
        assertTrue(site.holds(deck, 45, 3));
        assertTrue(site.holds(deck, 40, 3));
        assertTrue(site.holds(deck, 50, 3));
        assertFalse(site.holds(deck, 39, 3), "forward of the stretch");
        assertFalse(site.holds(deck, 51, 3), "abaft the stretch");
        assertFalse(site.holds(deck, 45, 15), "the other side of the spine");
    }

    @Test
    void sitesAreHandedOutInOrderPerPurpose() {
        var first = new DeckArrangement.Site(RoomPurpose.HANGAR, 0.4f, 0.4f, DeckSide.PORT);
        var second = new DeckArrangement.Site(RoomPurpose.HANGAR, 0.4f, 0.4f, DeckSide.STARBOARD);
        var bridge = new DeckArrangement.Site(RoomPurpose.BRIDGE, 0f, 0.2f, DeckSide.PORT);
        var arrangement = new DeckArrangement("test", List.of(first, bridge, second), List.of());
        var allocation = arrangement.allocate();
        assertSame(first, allocation.next(RoomPurpose.HANGAR));
        assertSame(second, allocation.next(RoomPurpose.HANGAR));
        assertNull(allocation.next(RoomPurpose.HANGAR), "a third bay falls back to its zone");
        assertSame(bridge, allocation.next(RoomPurpose.BRIDGE));
        assertEquals(0, arrangement.rank(RoomPurpose.HANGAR));
        assertEquals(1, arrangement.rank(RoomPurpose.BRIDGE));
        assertEquals(3, arrangement.rank(RoomPurpose.MESS_HALL), "an unsited purpose is laid after every sited one");
    }

    @Test
    void aFairingLaysTheFlankStraightAtItsNarrowest() {
        int frames = 40;
        int height = 30;
        // A hull whose beam narrows steadily toward the stern: the diagonal a
        // collision polygon draws between two corners.
        float[] port = new float[64];
        float[] starboard = new float[64];
        for (int i = 0; i < 64; i++) {
            port[i] = 1f - 0.5f * i / 63f;
            starboard[i] = port[i];
        }
        var silhouette = new HullSilhouette(port, starboard, 0.5f, "test");
        var fairing = new DeckArrangement.Fairing(0.25f, 0.75f, DeckSide.PORT);

        GenContext ctx = new GenContext(new NavigationGrid(frames, height),
                new CellTopology(frames, height), new Random(1), frames, height, 1);
        ctx.put(ShipKeys.DECK_ARRANGEMENT, new DeckArrangement("test", List.of(), List.of(fairing)));
        new HullProfileStage(4, silhouette).run(ctx);
        DeckProfile faired = ctx.get(ShipKeys.DECK_PROFILE);

        GenContext plain = new GenContext(new NavigationGrid(frames, height),
                new CellTopology(frames, height), new Random(1), frames, height, 1);
        new HullProfileStage(4, silhouette).run(plain);
        DeckProfile traced = plain.get(ShipKeys.DECK_PROFILE);

        int first = fairing.first(frames);
        int last = fairing.last(frames);
        assertTrue(traced.top(first) < traced.top(last), "the fixture must taper across the stretch");
        for (int f = first; f <= last; f++) {
            assertEquals(traced.top(last), faired.top(f), "frame " + f + " is laid at the narrowest beam");
            assertEquals(traced.bottom(f), faired.bottom(f), "the starboard flank is not faired");
        }
        assertEquals(traced.top(first - 1), faired.top(first - 1), "the hull outside the stretch is untouched");
        assertEquals(traced.top(last + 1), faired.top(last + 1));
        assertEquals(CellTopology.GroundKind.VOID, ctx.topology.getGroundKind(first, traced.top(first)),
                "deck taken off by the fairing is space");
    }
}
