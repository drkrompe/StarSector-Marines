package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Contract for {@link CombatService}'s OBJECT primary-weapon column — the COMBAT
 * data owner seeds {@code primaryWeapon} from the unit's write-only
 * {@code seedPrimaryWeapon} at {@code allocate}, the by-id getter/setter hit the
 * one world slot every reader sees, an unseeded combatant reads {@code null}
 * (the fall-back-to-baked-stats signal), and COMBAT is combatant-only + live-only
 * so the accessors are fail-loud on a corpse / unknown id. Mirrors
 * {@link VisionServiceTest}.
 */
public class CombatServiceTest {

    private static UnitRosterService roster() {
        return new UnitRosterService(new UnitSpatialIndex(256, 256), null);
    }

    private static EntitySpec unit(String label) {
        return new EntitySpec(label, Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
    }

    @Test
    public void allocateSeedsThePrimaryWeaponFromTheUnitSeed() {
        UnitRosterService r = roster();
        long id = r.spawn(unit("u"));
        CombatService combat = r.combat();
        // seedPrimaryWeapon set the weapon ref only (no stat derivation) — the
        // EntitySpec equivalent is the post-spawn setter, not .primaryWeapon()
        // (which also derives range/damage/accuracy/cooldown from the weapon).
        combat.setPrimaryWeapon(id, WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID));

        assertTrue(combat.has(id));
        // Seeded from the write-only seed (no Entity deref afterward) — the SAME
        // flyweight instance the deboard loadout handed in, not a copy.
        assertSame(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), combat.primaryWeapon(id));
    }

    @Test
    public void primaryWeaponDefaultsToNullWhenUnseeded() {
        UnitRosterService r = roster();
        long id = r.spawn(unit("u"));   // no seedPrimaryWeapon set

        // A combatant with no per-weapon profile (the militia/alien/turret shape):
        // the OBJECT column appends null, so the getter reads null — the
        // fall-back-to-baked-stats signal every fire/scoring reader keys off.
        assertNull(r.combat().primaryWeapon(id));
    }

    @Test
    public void setterHitsTheSharedWorldSlot() {
        UnitRosterService r = roster();
        long id = r.spawn(unit("u"));
        CombatService combat = r.combat();

        // The deboard-loadout seam: setPrimaryWeapon writes the same column the
        // fire/scoring code reads back.
        combat.setPrimaryWeapon(id, WeaponRegistry.require(WeaponRegistry.SMG_ID));
        assertSame(WeaponRegistry.require(WeaponRegistry.SMG_ID), combat.primaryWeapon(id));
    }

    @Test
    public void isFailLoudOnceCombatIsGoneOrTheIdIsUnknown() {
        UnitRosterService r = roster();
        long id = r.spawn(unit("u"));
        CombatService combat = r.combat();

        // The corpse transmute removes COMBAT (a corpse does not fight) — reads are
        // fail-loud from then on, as is any never-allocated id.
        r.entityWorld().removeComponent(id, r.components().COMBAT);
        assertFalse(combat.has(id));
        assertThrows(IllegalArgumentException.class, () -> combat.primaryWeapon(id));    // corpse
        assertThrows(IllegalArgumentException.class, () -> combat.primaryWeapon(999L));  // never allocated
    }

    /**
     * Re-equipping is the only way a shooter's band changes; the sim has no
     * award path of its own ({@code progression-nouns.md}).
     */
    @Test
    public void reEquippingABetterProfileRefreshesDerivedCombatStats() {
        UnitRosterService r = roster();
        long id = r.spawn(unit("u"));
        CombatService combat = r.combat();
        WeaponDef dmr = WeaponRegistry.require(WeaponRegistry.DMR_ID);
        combat.equipPrimaryWeapon(id, dmr, EquipmentGrade.SURPLUS,
                new SoldierProfile(SoldierAptitude.STEADY, 0));
        float greenAccuracy = combat.accuracy(id);
        float greenCooldown = combat.attackCooldown(id);

        SoldierProfile veteran = new SoldierProfile(
                SoldierAptitude.STEADY, ExperienceTier.VETERAN.minimumXp);
        combat.equipPrimaryWeapon(id, dmr, EquipmentGrade.SURPLUS, veteran);

        assertEquals(ExperienceTier.VETERAN, veteran.experienceTier());
        assertTrue(combat.accuracy(id) > greenAccuracy);
        assertTrue(combat.attackCooldown(id) < greenCooldown);
        assertEquals(EquipmentGrade.SURPLUS, combat.equipmentGrade(id));
        assertEquals(veteran, combat.soldierProfile(id));
    }

    // ---------------------------------------------------------------- incoming fire

    /** Ticks equivalent to one half-life of the incoming-fire signal. */
    private static int oneHalfLife() {
        return Math.round(CombatService.INCOMING_PRESSURE_HALF_LIFE_SECONDS
                / BattleSimulation.TICK_DT);
    }

    @Test
    public void nobodyShootingAtYouReadsAsNoPressure() {
        UnitRosterService r = roster();
        long id = r.spawn(unit("u"));
        assertEquals(0f, r.combat().incomingPressure(id, 0), 1e-4f);
    }

    /**
     * Rounds accumulate. Two rounds in the same tick is twice the fire one round
     * is, which is the property the waste guard rests on — a threshold that
     * could not tell a burst from a stray shot would gate on nothing.
     */
    @Test
    public void roundsLandingNearYouAccumulate() {
        UnitRosterService r = roster();
        CombatService combat = r.combat();
        long id = r.spawn(unit("u"));
        combat.recordIncomingFire(id, 9, 4, 0);
        float afterOne = combat.incomingPressure(id, 0);
        combat.recordIncomingFire(id, 9, 4, 0);
        assertEquals(2f * afterOne, combat.incomingPressure(id, 0), 1e-4f);
    }

    /**
     * And it fades, so a firefight that has moved on stops looking like one.
     * Pinned against the declared half-life rather than a literal, so retuning
     * the constant retunes the test with it.
     */
    @Test
    public void pressureHalvesOverTheDeclaredHalfLife() {
        UnitRosterService r = roster();
        CombatService combat = r.combat();
        long id = r.spawn(unit("u"));
        combat.recordIncomingFire(id, 9, 4, 0);
        float atOnce = combat.incomingPressure(id, 0);
        assertEquals(atOnce / 2f, combat.incomingPressure(id, oneHalfLife()), 1e-3f);
        assertEquals(atOnce / 4f, combat.incomingPressure(id, 2 * oneHalfLife()), 1e-3f);
    }

    /**
     * A later round is measured from its own arrival, not from the first one.
     * Decaying to now before adding is what makes that true, and getting it
     * wrong would make sustained fire read as a single fading shot.
     */
    @Test
    public void aLaterRoundRestartsTheClockOnWhatIsLeft() {
        UnitRosterService r = roster();
        CombatService combat = r.combat();
        long id = r.spawn(unit("u"));
        combat.recordIncomingFire(id, 9, 4, 0);
        combat.recordIncomingFire(id, 9, 4, oneHalfLife());
        assertEquals(1.5f, combat.incomingPressure(id, oneHalfLife()), 1e-3f,
                "half of the first round plus all of the second");
    }

    /** The bearing rides along, because a screen has to know which way to face. */
    @Test
    public void theMostRecentRoundsBearingIsKept() {
        UnitRosterService r = roster();
        CombatService combat = r.combat();
        long id = r.spawn(unit("u"));
        combat.recordIncomingFire(id, 9, 4, 0);
        assertEquals(9, combat.incomingFromX(id));
        assertEquals(4, combat.incomingFromY(id));
        combat.recordIncomingFire(id, 21, 7, 1);
        assertEquals(21, combat.incomingFromX(id));
        assertEquals(7, combat.incomingFromY(id));
    }

    /**
     * Never having been hit is distinguishable from having been hit on tick
     * zero. The column starts at zero for every fresh combatant, so the stamp is
     * stored plus one — get that wrong and every marine spawns believing it was
     * just shot.
     */
    @Test
    public void neverHavingBeenHitIsNotTheSameAsHitOnTickZero() {
        UnitRosterService r = roster();
        CombatService combat = r.combat();
        long never = r.spawn(unit("never"));
        long hit = r.spawn(unit("hit"));
        assertEquals(Integer.MAX_VALUE, combat.ticksSinceDamaged(never, 0));
        combat.recordDamageTaken(hit, 0);
        assertEquals(0, combat.ticksSinceDamaged(hit, 0));
    }

    @Test
    public void damageRecencyCountsFromTheLastHit() {
        UnitRosterService r = roster();
        CombatService combat = r.combat();
        long id = r.spawn(unit("u"));
        combat.recordDamageTaken(id, 100);
        assertEquals(20, combat.ticksSinceDamaged(id, 120));
        combat.recordDamageTaken(id, 118);
        assertEquals(2, combat.ticksSinceDamaged(id, 120),
                "a later hit resets the clock");
    }

    /**
     * Damage taken and incoming pressure are different facts and must not be
     * derived from one another: a round that misses is fire you are under but
     * did not hurt you, and a round from something you cannot see hurt you
     * without ever being fire you could see coming.
     */
    @Test
    public void damageAndIncomingPressureAreIndependent() {
        UnitRosterService r = roster();
        CombatService combat = r.combat();
        long id = r.spawn(unit("u"));

        combat.recordIncomingFire(id, 3, 3, 0);
        assertTrue(combat.incomingPressure(id, 0) > 0f);
        assertEquals(Integer.MAX_VALUE, combat.ticksSinceDamaged(id, 0),
                "a near miss is not a hit");

        long other = r.spawn(unit("v"));
        combat.recordDamageTaken(other, 0);
        assertEquals(0f, combat.incomingPressure(other, 0), 1e-4f,
                "a hit from something unseen is not fire you could see coming");
    }
}
