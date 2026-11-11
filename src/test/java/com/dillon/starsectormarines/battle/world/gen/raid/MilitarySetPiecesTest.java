package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.raid.MilitarySetPieces.Enclosure;
import com.dillon.starsectormarines.battle.world.gen.raid.MilitarySetPieces.Hut;
import com.dillon.starsectormarines.battle.world.gen.raid.MilitarySetPieces.Item;
import com.dillon.starsectormarines.battle.world.gen.raid.MilitarySetPieces.Kind;
import com.dillon.starsectormarines.battle.world.gen.raid.MilitarySetPieces.Plan;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The military set pieces' plans ({@link MilitarySetPieces}): a set piece is one footprint whose edge is closed but
 * for the gate on the side it faces, whose building and props stand inside it clear of each other, and whose gate (an
 * enclosed one's) and door are kept open. Plans only; no map is generated.
 */
class MilitarySetPiecesTest {

    /** The frame covers the plan's rectangle cell for cell, and the gate opens on the side it names, whichever way. */
    @Test
    void theFrameFillsTheFootprintAndTheGateIsOnItsSide() {
        for (Kind kind : Kind.values()) {
            for (int gate = 0; gate < 4; gate++) {
                Plan p = new Plan(kind, 10, 20, gate);
                Set<Long> seen = new HashSet<>();
                for (int u = 0; u < kind.along; u++) {
                    for (int v = 0; v < kind.deep; v++) {
                        int[] c = p.cell(u, v);
                        assertTrue(c[0] >= p.x0() && c[0] <= p.x1() && c[1] >= p.y0() && c[1] <= p.y1());
                        assertTrue(seen.add((long) c[0] << 32 | c[1]), kind + " gate " + gate + " cell twice");
                        if (!p.gateway(u, v)) continue;
                        int[] out = p.out();
                        int nx = c[0] + out[0], ny = c[1] + out[1];
                        assertFalse(nx >= p.x0() && nx <= p.x1() && ny >= p.y0() && ny <= p.y1(),
                                kind + " gate " + gate + " opens inward");
                    }
                }
                assertEquals(p.width() * p.height(), seen.size());
            }
        }
    }

    /** A fenced or bunded set piece is closed all round but for its gate. */
    @Test
    void theEnclosureIsClosedButForTheGate() {
        for (Kind kind : Kind.values()) {
            Plan p = new Plan(kind, 0, 0, 0);
            int closed = 0, edge = 0;
            for (int u = 0; u < kind.along; u++) {
                for (int v = 0; v < kind.deep; v++) {
                    if (p.edge(u, v)) edge++;
                    if (p.enclosed(u, v)) closed++;
                }
            }
            assertEquals(kind.enclosure == Enclosure.NONE ? 0 : edge - MilitarySetPieces.GATE, closed, kind.name());
        }
    }

    /**
     * Everything a set piece holds stands inside its enclosure, no two on one cell, the ground behind the gate and in
     * front of the building's door left clear.
     */
    @Test
    void theBuildingAndPropsStandInsideClearOfEachOtherAndOfTheWayIn() {
        for (Kind kind : Kind.values()) {
            Plan p = new Plan(kind, 0, 0, 0);
            int lo = kind.enclosure == Enclosure.NONE ? 0 : 1;
            Set<Long> taken = new HashSet<>();
            Hut hut = MilitarySetPieces.hut(kind);
            if (hut != null) take(kind, taken, lo, hut.u(), hut.v(), hut.along(), hut.deep());
            Set<Long> floor = new HashSet<>();
            for (Item item : MilitarySetPieces.items(kind)) {
                if (!item.indoors()) {
                    take(kind, taken, lo, item.u(), item.v(), item.along(), item.deep());
                    continue;
                }
                // Inside the building's walls, off the cell inside its door.
                assertTrue(hut != null && item.u() > hut.u() && item.v() > hut.v()
                        && item.u() + item.along() < hut.u() + hut.along()
                        && item.v() + item.deep() < hut.v() + hut.deep(), kind + " indoor item outside its building");
                for (int u = item.u(); u < item.u() + item.along(); u++) {
                    for (int v = item.v(); v < item.v() + item.deep(); v++) {
                        assertTrue(floor.add(key(u, v)), kind + " stands two things indoors at " + u + "," + v);
                    }
                }
            }
            if (hut != null) {
                assertFalse(floor.contains(key(hut.u() + hut.along() / 2, hut.v() + 1)), kind + " blocks its door inside");
            }
            for (int u = 0; u < kind.along; u++) {
                if (kind.enclosure != Enclosure.NONE && p.gateway(u, 0)) assertFalse(taken.contains(key(u, 1)), kind + " blocks its gate at " + u);
            }
            if (hut != null) {
                assertFalse(taken.contains(key(hut.u() + hut.along() / 2, hut.v() - 1)), kind + " blocks its door");
            }
        }
    }

    private static void take(Kind kind, Set<Long> taken, int lo, int u0, int v0, int along, int deep) {
        for (int u = u0; u < u0 + along; u++) {
            for (int v = v0; v < v0 + deep; v++) {
                assertTrue(u >= lo && v >= lo && u <= kind.along - 1 - lo && v <= kind.deep - 1 - lo,
                        kind + " has something on its edge at " + u + "," + v);
                assertTrue(taken.add(key(u, v)), kind + " stands two things at " + u + "," + v);
            }
        }
    }

    private static long key(int u, int v) {
        return (long) u << 32 | v;
    }
}
