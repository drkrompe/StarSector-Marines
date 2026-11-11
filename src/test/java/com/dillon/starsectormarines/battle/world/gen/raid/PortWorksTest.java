package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
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
        for (Piece p : pieces) {
            if (p instanceof Site s) {
                assertEquals(s.name().equals(PortWorks.WAREHOUSES) ? SitePurpose.BUILT : SitePurpose.GROUND,
                        s.purpose(), s.name());
                held.add(s);
            }
        }
        for (Piece p : pieces) {
            if (!(p instanceof Structure s)) continue;
            warehouses++;
            for (Site site : held) {
                boolean clear = s.x() + s.width() - 1 < site.x0() || s.x() > site.x1()
                        || s.y() + s.height() - 1 < site.y0() || s.y() > site.y1();
                boolean inside = s.x() >= site.x0() && s.x() + s.width() - 1 <= site.x1()
                        && s.y() >= site.y0() && s.y() + s.height() - 1 <= site.y1();
                if (site.purpose() == SitePurpose.BUILT) assertTrue(inside, s.name() + " off its built ground");
                else assertTrue(clear, s.name() + " on held ground " + site.name());
            }
        }
        assertEquals(hub.sheds().size(), warehouses);
        // The dressing reads the same hub back from the held ground.
        Site lines = (Site) pieces.get(0), apron = (Site) pieces.get(1);
        assertSameHub(hub, PortWorks.hubOf(lines, apron));
        PortHub turned = PortHub.lay(2, 2, 129, 100, false, false, PortWorks.back(32));
        List<Piece> back = PortWorks.pieces(turned, 280, 168);
        assertSameHub(turned, PortWorks.hubOf((Site) back.get(0), (Site) back.get(1)));
    }

    @Test
    void groundBesideAWorksComesInToItsClaimButNeverAcrossABuilding() {
        Site east = PortWorks.apronSite(150, 110, 30, false, false, 280, 168);
        assertEquals(150 + 30 + PortWorks.APRON_GAP, east.x0());
        Site ground = new Site(PortWorks.APRON, 200, 90, 270, 150, SitePurpose.GROUND, true);
        // Open ground between the claim (ending at 180) and the held ground: brought in to the claim.
        assertEquals(180, PortWorks.clearEdge(180, true, ground, (x, y) -> false));
        // A shed grew at x 188..190 on the ground's rows: the ground starts past it.
        assertEquals(190, PortWorks.clearEdge(180, true, ground, (x, y) -> x >= 188 && x <= 190 && y == 120));
        // A building off the ground's rows does not stop it.
        assertEquals(180, PortWorks.clearEdge(180, true, ground, (x, y) -> x == 188 && y == 40));
    }

    private static void assertSameHub(PortHub want, PortHub got) {
        assertNotNull(got);
        assertEquals(want.lines(), got.lines());
        assertEquals(want.apron(), got.apron());
        assertEquals(want.sheds(), got.sheds());
        assertEquals(want.containers(), got.containers());
        assertEquals(want.pads(), got.pads());
        assertEquals(want.roads(), got.roads());
    }
}
