package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.ship.stage.HullProfileStage;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void aReserveIsKeptAtASiteNamedForItsOwnPurpose() {
        var range = new DeckArrangement.Site(RoomPurpose.FIRING_RANGE, 0.4f, 0.6f, DeckSide.STARBOARD);
        assertSame(range, new DeckArrangement.Reserve(RoomRecipe.SECOND_WIND_RANGE, range).site());
        assertThrows(IllegalArgumentException.class,
                () -> new DeckArrangement.Reserve(RoomRecipe.SECOND_WIND_GYM, range));
    }

    @Test
    void secondWindsReservesAreNoPartOfAnyProgramPurposeSheHolds() {
        // A reserve shares no purpose with a sited programmed room, so a site
        // handed out by purpose can never send a programmed room to a reserve's
        // ground or the other way round.
        for (var reserve : DeckArrangement.SECOND_WIND.reserves()) {
            assertEquals(DeckArrangement.SECOND_WIND.sites().size(),
                    DeckArrangement.SECOND_WIND.rank(reserve.recipe().purpose()), reserve.recipe().purpose().name());
        }
    }

    @Test
    void conditionNeverForgetsWhichRoomsAreReserves() {
        var room = new DeckGraph.Compartment(0, RoomShape.rectangle(6, 6), 0, 0,
                RoomPose.CANONICAL, DeckSide.PORT, DeckZone.AFT, RoomPurpose.BRIG, List.of());
        var other = new DeckGraph.Compartment(1, RoomShape.rectangle(6, 6), 8, 0,
                RoomPose.CANONICAL, DeckSide.PORT, DeckZone.AFT, RoomPurpose.WASHROOM, List.of());
        var graph = new DeckGraph(List.of(room, other), List.of(), List.of(), null, Set.of(), Set.of(), Set.of(0));
        var damaged = graph.withCondition(List.of(), Set.of(1), Set.of());
        assertTrue(damaged.reserved(0));
        assertFalse(damaged.reserved(1));
        assertTrue(damaged.damaged(1));
        assertFalse(damaged.damaged(0), "condition is replaced, not merged");
    }
}
