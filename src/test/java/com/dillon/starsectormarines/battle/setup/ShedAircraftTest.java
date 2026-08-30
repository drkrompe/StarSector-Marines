package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a station keeps in its sheds.
 *
 * <p>Aimed at the assignment rather than at a generated map: the shape a real
 * field has — three sheds in a row, sixteen cells apart along one axis — is the
 * whole of what makes this hard, and it can be written down in three lines. An
 * earlier version read every shed's own position into the walk and cancelled
 * it exactly, putting one aircraft on every field; that is the regression this
 * is here for, and it fails against it.
 */
class ShedAircraftTest {

    /** The row a station actually lays: three sheds along its back fence. */
    private static List<Gantry> aStationsSheds(int firstX, int y) {
        List<Gantry> sheds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            sheds.add(new Gantry(firstX + i * 16, y, 2, 2, Gantry.Facing.SOUTH));
        }
        return sheds;
    }

    /**
     * A field disperses.
     *
     * <p>Checked across a sweep of origins rather than one. Whether an
     * assignment disperses depends on how its arithmetic lands against the
     * shed spacing, and a scheme can be right at one origin and uniform at the
     * next — a single case is exactly the evidence that misled the version
     * this replaced.
     */
    @Test
    void everyShedOnAFieldHoldsADifferentFighter() {
        List<FighterProfile> pool = FighterProfile.poolForFaction("hegemony");
        for (int x = 8; x < 260; x += 7) {
            for (int y = 8; y < 160; y += 11) {
                List<FighterProfile> based =
                        BattleSetup.shedAircraft(pool, aStationsSheds(x, y));
                assertEquals(3, based.size());
                assertEquals(3, new HashSet<>(based).size(),
                        "sheds at " + x + "," + y + " all hold " + based);
            }
        }
    }

    /** Which fighters they are is the defender's business, not the map's. */
    @Test
    void aFieldFliesWhatItsFactionFlies() {
        List<Gantry> sheds = aStationsSheds(218, 157);
        Set<FighterProfile> hegemony = new HashSet<>(
                BattleSetup.shedAircraft(FighterProfile.poolForFaction("hegemony"), sheds));
        Set<FighterProfile> triTachyon = new HashSet<>(
                BattleSetup.shedAircraft(FighterProfile.poolForFaction("tritachyon"), sheds));

        assertTrue(hegemony.contains(FighterProfile.BROADSWORD),
                "a Hegemony field with no Broadsword on it: " + hegemony);
        assertTrue(triTachyon.contains(FighterProfile.WASP),
                "a Tri-Tachyon field with no Wasp on it: " + triTachyon);
        assertTrue(hegemony.stream().noneMatch(f -> f == FighterProfile.WASP
                        || f == FighterProfile.THUNDER),
                "a Hegemony field is flying hi-tech: " + hegemony);
    }

    /**
     * A field with more sheds than the faction has aircraft repeats rather
     * than running off the end of the pool.
     */
    @Test
    void aFieldLargerThanItsPoolWrapsAround() {
        List<FighterProfile> pool = FighterProfile.poolForFaction("hegemony");
        List<Gantry> sheds = new ArrayList<>(aStationsSheds(100, 100));
        sheds.add(new Gantry(148, 100, 2, 2, Gantry.Facing.SOUTH));
        sheds.add(new Gantry(164, 100, 2, 2, Gantry.Facing.SOUTH));

        List<FighterProfile> based = BattleSetup.shedAircraft(pool, sheds);

        assertEquals(5, based.size());
        assertEquals(pool.size(), new HashSet<>(based).size(),
                "a field should use every aircraft its faction has before repeating");
    }

    /** A map with no sheds asks for nothing, rather than indexing an empty list. */
    @Test
    void aFieldWithNoShedsBasesNothing() {
        assertTrue(BattleSetup.shedAircraft(
                FighterProfile.poolForFaction("hegemony"), List.of()).isEmpty());
    }
}
