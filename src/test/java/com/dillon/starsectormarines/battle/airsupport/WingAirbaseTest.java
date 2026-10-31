package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.air.AirfieldService;
import com.dillon.starsectormarines.battle.air.AirfieldService.Berth;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.Runway;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which berth a wing's call rolls from, and what a loss on the base costs it. */
class WingAirbaseTest {

    private static AirfieldService field(boolean strip) {
        AirfieldService field = new AirfieldService();
        field.addField(Faction.ALLY, strip ? new Runway(0f, 40f, 40f, 40f, 4f) : null, true);
        return field;
    }

    private static Berth stand(AirfieldService field, int x, FighterProfile airframe) {
        return field.addBerth(field.fields().get(1), LandingPad.allied(x, 30, LandingPad.Approach.NORTH), airframe, 0f);
    }

    private static WingAirbase base(AirfieldService field) {
        List<Berth> berths = field.berths();
        return new WingAirbase(field, new int[]{0, 20, 40, 45}, berths,
                berths.stream().map(b -> WingAirbase.kindOf((FighterProfile) b.airframe)).toList());
    }

    @Test
    void aCallRollsFromTheAirframeItAskedForBeforeAnyOtherOfItsKind() {
        AirfieldService field = field(true);
        Berth broadsword = stand(field, 5, FighterProfile.BROADSWORD);
        Berth talon = stand(field, 30, FighterProfile.TALON);
        stand(field, 15, FighterProfile.DAGGER);
        WingAirbase base = base(field);
        assertSame(talon, base.berthFor(AirPoolKind.FIGHTER, FighterProfile.TALON, 0f, 30f), "the asked-for airframe, though further");
        field.launch(talon);
        assertSame(broadsword, base.berthFor(AirPoolKind.FIGHTER, FighterProfile.TALON, 0f, 30f), "else any of the kind");
        field.launch(broadsword);
        assertNull(base.berthFor(AirPoolKind.FIGHTER, FighterProfile.TALON, 0f, 30f), "never a bomber's stand");
    }

    @Test
    void aBaseWithNoStripFliesNothingOffIt() {
        AirfieldService field = field(false);
        stand(field, 5, FighterProfile.TALON);
        assertNull(base(field).berthFor(AirPoolKind.FIGHTER, FighterProfile.TALON, 0f, 0f));
    }

    @Test
    void aBurnedBayIsWrittenOffOnceAndTheBaseIsWhoseFieldItIs() {
        AirfieldService field = field(true);
        stand(field, 5, FighterProfile.TALON);
        Berth burned = stand(field, 15, FighterProfile.TALON);
        WingAirbase base = base(field);
        assertEquals(Faction.ALLY, base.side());
        field.burnedOnPad(burned);
        assertEquals(List.of(AirPoolKind.FIGHTER), base.newlyBurned());
        assertTrue(base.newlyBurned().isEmpty(), "written off once");
    }

    /** What the base has to fly is what stands airworthy in its bays, kind by kind. */
    @Test
    void theBaysAreWhatTheBaseHasToFly() {
        AirfieldService field = field(true);
        Berth talon = stand(field, 5, FighterProfile.TALON);
        stand(field, 15, FighterProfile.BROADSWORD);
        stand(field, 25, FighterProfile.DAGGER);
        WingAirbase base = base(field);
        assertEquals(2, base.airworthy(AirPoolKind.FIGHTER));
        assertEquals(1, base.airworthy(AirPoolKind.BOMBER));
        field.launch(talon);
        assertEquals(1, base.airworthy(AirPoolKind.FIGHTER), "an aircraft away is not available");
        field.recover(talon, FighterProfile.TALON.maxHp() * 0.5f);
        assertEquals(1, base.airworthy(AirPoolKind.FIGHTER), "nor one home that its crew has not turned round");
    }

    @Test
    void enemiesOnTheBaseOrJustOffItAreOnIt() {
        WingAirbase base = base(field(true));
        assertTrue(base.within(20f, 30f));
        assertTrue(base.within(-3f, 30f));
        assertFalse(base.within(-6f, 30f));
    }
}
