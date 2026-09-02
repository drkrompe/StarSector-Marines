package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the marines come down on, and what stands there.
 *
 * <p>Two questions with different answers. The <b>derivation</b> is about the
 * world: a port makes a spaceport, an off-grid lifeline makes a strip, and
 * everything else is a field. The <b>program</b> is about the place: every kind
 * owes the apron its berths stand on, and only the built kinds owe buildings.
 */
class LandingKindTest {

    private static TargetProfile world(int spaceportTier, SettlementLink link) {
        return new TargetProfile(6, 50, 3, spaceportTier, "",
                EnumSet.noneOf(EconomicFunction.class), SurfacePalette.ROCK, link);
    }

    @Test
    void aRealPortIsWhereALandingForcePutsItselfDown() {
        assertEquals(LandingKind.SPACEPORT, LandingKind.derive(
                world(1, SettlementLink.ROAD), PrecinctPlan.Sprawl.BALANCED));
        assertEquals(LandingKind.SPACEPORT, LandingKind.derive(
                world(2, SettlementLink.ROAD), PrecinctPlan.Sprawl.DENSE),
                "a megaport is still a port");
    }

    @Test
    void aSettlementSuppliedByShipHasAStrip() {
        assertEquals(LandingKind.STRIP, LandingKind.derive(
                world(0, SettlementLink.LANDING), PrecinctPlan.Sprawl.BALANCED));
    }

    @Test
    void aWorldWithNoPortIsALandingOnBareGround() {
        assertEquals(LandingKind.FIELD, LandingKind.derive(
                world(0, SettlementLink.ROAD), PrecinctPlan.Sprawl.BALANCED));
        assertEquals(LandingKind.FIELD, LandingKind.derive(
                world(0, SettlementLink.NONE), PrecinctPlan.Sprawl.DENSE));
        assertEquals(LandingKind.FIELD, LandingKind.derive(null, null),
                "a battle with no world behind it lands on a field");
    }

    /**
     * A remote map is an installation in country, and the sprawl already said
     * there is no settlement on it. A civil spaceport campus standing alone in
     * that country would be the town the sprawl declined.
     */
    @Test
    void aRemoteWorldIsAFieldWhateverItsMarketReports() {
        assertEquals(LandingKind.FIELD, LandingKind.derive(
                world(2, SettlementLink.LANDING), PrecinctPlan.Sprawl.REMOTE));
    }

    /**
     * The apron is the part every kind owes: it is the ground the berths stand
     * on, and claiming it is the whole reason the landing place is a precinct
     * rather than a scan for open ground.
     */
    @Test
    void everyKindClaimsTheGroundItsBerthsStandOn() {
        for (LandingKind kind : LandingKind.values()) {
            assertEquals(FortressProgram.LANDING_APRON, kind.program().apron(),
                    kind + " owes no apron, so its berths have no ground of their own");
            assertEquals(0, kind.program().airfields(),
                    kind + " orders an airfield lot, which would fence its own apron");
            assertEquals(0, kind.program().countOf(RoomPurpose.KEEP_THRONE),
                    kind + " packs a keep, and a map has exactly one");
        }
    }

    @Test
    void aFieldBuildsNothingAndAStripBuildsAHut() {
        assertTrue(LandingKind.FIELD.program().buildings().isEmpty(),
                "a field is bare ground");
        assertEquals(1, LandingKind.STRIP.program().buildings().size(),
                "a strip is an apron with one hut on it");
    }

    /**
     * The stock recipe's spaceport vocabulary, said as a program: a terminal,
     * somewhere to work an aircraft, the office that runs the place, and the
     * stores that arrive with a ship.
     */
    @Test
    void aSpaceportPacksItsCampus() {
        FortressProgram program = LandingKind.SPACEPORT.program();
        assertEquals(1, program.countOf(RoomPurpose.CIVIC_RECEPTION), "a terminal");
        assertEquals(1, program.countOf(RoomPurpose.HANGAR), "a hangar");
        assertEquals(1, program.countOf(RoomPurpose.CONTROL_ROOM), "a control office");
        assertEquals(1, program.countOf(RoomPurpose.STOCKROOM), "a fuel yard");
        assertTrue(program.envelopeArea() > program.apron(),
                "a spaceport asks for no more ground than a bare field does");
    }
}
