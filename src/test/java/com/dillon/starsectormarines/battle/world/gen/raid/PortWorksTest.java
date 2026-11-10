package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.gen.layout.Deferred;
import com.dillon.starsectormarines.battle.world.gen.layout.Paving;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.layout.Site;
import com.dillon.starsectormarines.battle.world.gen.layout.SitePurpose;
import com.dillon.starsectormarines.battle.world.gen.layout.Structure;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The port's site rules ({@link PortWorks}): the terminal laid in the order the cargo passes, the hub beside it
 * across the works' half, its bands held, its warehouses raised on open ground between them. Plans and rectangles.
 */
class PortWorksTest {

    private static final PortWorks PORT = new PortWorks();

    @Test
    void theTerminalRunsFromCustomsAndPassengersAtTheFrontToThePumpsAtTheRear() {
        FortressProgram program = PORT.works(4);
        int sheds = 0;
        for (FortressBuilding b : program.buildings()) {
            if (b.purpose() == PortWorks.CUSTOMS || b.purpose() == PortWorks.PASSENGERS) {
                assertEquals(Ward.FRONTAGE, b.ward(), "at the town's gate");
            }
            if (b.purpose() == PortWorks.BONDED) {
                assertEquals(Ward.YARD, b.ward());
                sheds += b.count();
            }
            if (b.purpose() == PortWorks.PUMPS) assertEquals(Ward.REAR, b.ward(), "the pumps toward the hub");
        }
        assertEquals(4, sheds, "a bonded shed per machine");
        assertFalse(PORT.deepFeeder(), "the deep end is the hub's");
        assertFalse(PORT.yards());
        assertTrue(PORT.industryIds().contains("megaport"));
    }

    @Test
    void theHubStandsBeyondTheTerminalFromTheLinesToTheOuterEdge() {
        Site east = PortWorks.hubBox(115, 120, 32, false, false, 280, 168);
        assertEquals(115 + 32 + PortWorks.HUB_GAP, east.x0());
        assertEquals(280 - 1 - PortWorks.MARGIN, east.x1());
        assertEquals(Math.round(PortWorks.LINES_AT * 168), east.y0(), "the lines across the map's middle");
        assertEquals(168 - 1 - PortWorks.MARGIN, east.y1());
        Site turned = PortWorks.hubBox(165, 48, 32, true, true, 280, 168);
        assertEquals(165 - 32 - PortWorks.HUB_GAP, turned.x1());
        assertEquals(PortWorks.MARGIN, turned.x0());
        assertEquals(168 - 1 - Math.round(PortWorks.LINES_AT * 168), turned.y1());
    }

    @Test
    void theHubsPiecesHoldItsBandsAndRaiseItsWarehousesOnOpenGround() {
        PortHub hub = PortHub.lay(150, 67, 277, 165, true, true, PortWorks.back(32));
        assertNotNull(hub);
        List<Piece> pieces = PortWorks.pieces(hub, 280, 168);
        List<Site> held = new ArrayList<>();
        int warehouses = 0;
        Paving highway = null;
        List<Piece> laid = new ArrayList<>(pieces);
        for (Piece p : pieces) if (p instanceof Deferred d) laid.addAll(d.resolve().apply(null));
        for (Piece p : pieces) {
            if (p instanceof Site s) {
                assertEquals(SitePurpose.GROUND, s.purpose());
                held.add(s);
            }
        }
        for (Piece p : laid) if (p instanceof Paving paving) highway = paving;
        for (Piece p : pieces) {
            if (!(p instanceof Structure s)) continue;
            warehouses++;
            for (Site site : held) {
                boolean clear = s.x() + s.width() - 1 < site.x0() || s.x() > site.x1()
                        || s.y() + s.height() - 1 < site.y0() || s.y() > site.y1();
                assertTrue(clear, s.name() + " on held ground " + site.name());
            }
        }
        assertEquals(hub.sheds().size(), warehouses);
        assertNotNull(highway);
        assertEquals(hub.highway().width() * hub.highway().height(), highway.cells().size());
        // The dressing reads the same hub back from the held ground.
        Site lines = (Site) pieces.get(0), apron = (Site) pieces.get(1);
        assertSameHub(hub, PortWorks.hubOf(lines, apron));
        PortHub turned = PortHub.lay(2, 2, 129, 100, false, false, PortWorks.back(32));
        List<Piece> back = PortWorks.pieces(turned, 280, 168);
        assertSameHub(turned, PortWorks.hubOf((Site) back.get(0), (Site) back.get(1)));
    }

    private static void assertSameHub(PortHub want, PortHub got) {
        assertNotNull(got);
        assertEquals(want.lines(), got.lines());
        assertEquals(want.apron(), got.apron());
        assertEquals(want.sheds(), got.sheds());
        assertEquals(want.containers(), got.containers());
        assertEquals(want.pads(), got.pads());
        assertEquals(want.heavyPad(), got.heavyPad());
    }
}
