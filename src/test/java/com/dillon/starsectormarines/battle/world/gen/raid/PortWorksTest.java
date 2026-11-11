package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.layout.Site;
import com.dillon.starsectormarines.battle.world.gen.layout.SitePurpose;
import com.dillon.starsectormarines.battle.world.gen.layout.Structure;
import com.dillon.starsectormarines.battle.world.gen.precinct.DefenceDoctrine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        // The garrison seeds below the lines, which run across the map's middle, never against them.
        assertTrue(PORT.garrisonAt().y1() < PortWorks.LINES_AT - .2f, "garrison clear of the lines");
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
    void theForecourtHoldsTheGroundBetweenTheTerminalAndTheHubBesideTheLines() {
        PortHub hub = PortHub.lay(150, 67, 277, 165, true, true, PortWorks.back(32));
        Site court = PortWorks.forecourt(hub, 115, 32, 280, 168);
        assertNotNull(court);
        assertEquals(SitePurpose.GROUND, court.purpose(), "held from the town's growth");
        assertEquals(115 + 16, court.x0(), "from half the terminal's reach");
        assertEquals(hub.paved().x0() - 1, court.x1(), "to the hub's start");
        assertEquals(hub.built().y0(), court.y0(), "beside the lines, not over them");
        assertEquals(hub.paved().y1(), court.y1());
        PortHub turned = PortHub.lay(2, 2, 129, 100, false, false, PortWorks.back(32));
        Site west = PortWorks.forecourt(turned, 165, 32, 280, 168);
        assertEquals(turned.paved().x1() + 1, west.x0());
        assertEquals(165 - 16, west.x1());
        assertEquals(turned.built().y1(), west.y1());
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

    @Test
    void thePortsGunsAreSpreadOverItsLinesItsFuelAndItsPadsWithShips() {
        PortHub hub = PortHub.lay(150, 67, 277, 165, true, true, PortWorks.back(32));
        // A Colossus on every other pad, a shuttle pod on the rest.
        List<LandingPad> stands = new ArrayList<>();
        for (int i = 0; i < hub.pads().size(); i++) {
            PortHub.Rect pad = hub.pads().get(i);
            boolean colossus = i % 2 == 0;
            stands.add(new LandingPad(pad.centerX(), pad.centerY(), colossus ? 6 : 1, colossus ? 10 : 1,
                    LandingPad.Approach.NORTH, LandingPad.Purpose.PORT_LIGHTER));
        }
        GuardedGrounds.Grounds fortified = PortWorks.grounds(hub, stands, true);
        List<String> names = new ArrayList<>();
        for (GuardedGrounds.Place place : fortified.places()) names.add(place.name());
        assertEquals(PortWorks.GUARD_LINES, names.get(0));
        assertEquals(PortWorks.GUARD_FUEL, names.get(1));
        assertEquals(2 + Math.min(PortWorks.FORTIFIED_PADS, hub.pads().size()), names.size(),
                "the lines, the fuel and the pads' share: " + names);
        // The lines are watched first over the open ground an attack crosses toward them, from the map's middle (the
        // outer edge is north: the middle is south), then where the highway comes in at the terminal's end.
        GuardedGrounds.Place lines = fortified.places().get(0);
        DefenceDoctrine.Gate approach = lines.gates().get(0);
        assertEquals(hub.highway().y0(), approach.y());
        assertEquals(-1, approach.outY());
        DefenceDoctrine.Gate truckGate = lines.gates().get(1);
        assertEquals(hub.terminalX(), truckGate.x());
        assertTrue(hub.highway().contains(truckGate.x(), truckGate.y()));
        assertEquals(-1, truckGate.outX(), "the deep edge is east: the town is west");
        // Each pad's gun stands on its apron toward the hub.
        for (GuardedGrounds.Place place : fortified.places().subList(2, names.size())) {
            assertEquals(-1, place.gates().get(0).outY(), place.name());
        }
        for (PortHub.Rect ground : lines.ground()) assertTrue(ground.x0() >= hub.terminalX(), "the hub's own length");
        // Each guarded pad guards the ship on it, a gun's room between it and the pad's edge; no pad twice.
        for (GuardedGrounds.Place place : fortified.places().subList(2, names.size())) {
            PortHub.Rect pad = hub.pads().get(Integer.parseInt(place.name().substring(PortWorks.GUARD_PAD.length())));
            for (PortHub.Rect ship : place.ground()) {
                assertTrue(ship.x0() > pad.x0() && ship.x1() < pad.x1() && ship.y0() > pad.y0() && ship.y1() < pad.y1(),
                        place.name() + " " + ship + " on " + pad);
            }
        }
        assertEquals(names.size(), names.stream().distinct().count());
        // Guarded holds fewer pads' guns and lighter ones; a pad with no ship holds none.
        GuardedGrounds.Grounds guarded = PortWorks.grounds(hub, stands.subList(0, 1), false);
        assertEquals(3, guarded.places().size(), "one pad has a ship");
        assertEquals(Map.of(DefensePostKind.LIGHT, 1), guarded.places().get(2).posts());
        // No gun stands on the roads or the highway.
        assertTrue(fortified.keepClear().containsAll(hub.roads()));
        assertTrue(fortified.keepClear().contains(hub.highway()));
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
