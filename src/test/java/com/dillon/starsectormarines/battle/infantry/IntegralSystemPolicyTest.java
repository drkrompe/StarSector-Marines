package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.GroundRosterProfile;
import com.dillon.starsectormarines.battle.setup.GroundRosterRegistry;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.SpecialAiPolicy;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * An integral system authors the moment it should be spent at, and the sweep
 * dispatches on that rather than on the effect ({@code progression-nouns.md}).
 *
 * <p>Every scene here runs against a real {@link BattleSimulation}. Nothing in
 * this file pins an authored balance number or a catalog count: expectations
 * are either derived from the loaded registries or, where a policy's mechanics
 * are the subject, expressed against a synthetic system whose numbers the test
 * itself chose.
 */
class IntegralSystemPolicyTest {

    private static final int W = 48;
    private static final int H = 16;
    private static final int ROW = 8;
    private static final int CARRIER_X = 6;

    /**
     * Far past any authored salvo total, so a standoff scene measures whether
     * the pod chose to fire rather than whether its target happened to survive
     * long enough to be fired at twice.
     */
    private static final float OUTLASTS_THE_RACK = 1_000_000f;

    /** Long enough for a stationary pod to acquire and spend, short enough to stay quick. */
    private static final int OBSERVATION_TICKS = 240;

    // ---------------------------------------------------------------- authoring

    /**
     * The trigger is readable from the catalog entry, for every system in it.
     * This is the acceptance the story states as "no integral system's trigger
     * lives in a system class any more": a reader who wants to know when a suit
     * spends itself opens the armour catalog, not {@link IntegralSystemSystem}.
     */
    @Test
    void everyCataloguedSystemAuthorsTheMomentItIsSpentAt() {
        MarineArmorCatalogRegistry catalog = MarineArmorCatalogRegistry.installed();
        assumeTrue(catalog != null, "armour catalog is not installed in this context");

        int inspected = 0;
        for (MarineArmorCatalogDef pattern : catalog.all()) {
            IntegralSystemDef system = pattern.integralSystem();
            if (system == null) continue;
            inspected++;
            assertNotNull(system.policy(), pattern.id() + " authors no use policy");
            assertSame(system.policy().aiPolicy(), system.aiPolicy(), pattern.id());
            switch (system.aiPolicy()) {
                case CROSSING_UNDER_FIRE -> assertTrue(
                        system.crossingUnderFire().threatRadiusCells() > 0f,
                        pattern.id() + " must author the radius its crossing is judged against");
                case SIGHTED_STANDOFF_CONTACT -> assertTrue(
                        system.sightedStandoff().minimumStandoffCells() > 0f,
                        pattern.id() + " must author the standoff its salvo is judged against");
                default -> fail(pattern.id() + " declares a policy with no authored parameters: "
                        + system.aiPolicy().key);
            }
        }
        assumeTrue(inspected > 0, "no catalogued pattern carries a system in this context");
    }

    /**
     * The parse-time refusal, in the style the armour catalog already uses for
     * durability keys: the failure names the policies that <em>do</em> apply, so
     * an author is taught the vocabulary rather than told they were wrong.
     */
    @Test
    void aPolicyThatCannotApplyToItsEffectIsRefusedNamingTheOnesThatCan() throws JSONException {
        JSONObject crossingOnAPod = missilePodJson()
                .put("policy", SpecialAiPolicy.CROSSING_UNDER_FIRE.key)
                .put("threatRadiusCells", 12.0);
        JSONException podFailure = assertThrows(JSONException.class,
                () -> IntegralSystemDef.parse(crossingOnAPod, "armor.test"));
        assertTrue(podFailure.getMessage().contains(SpecialAiPolicy.SIGHTED_STANDOFF_CONTACT.key),
                "the refusal should name the policy a missile pod can declare: "
                        + podFailure.getMessage());

        JSONObject standoffOnAnAssist = breacherAssistJson()
                .put("policy", SpecialAiPolicy.SIGHTED_STANDOFF_CONTACT.key)
                .put("minimumStandoffCells", 5.0);
        JSONException assistFailure = assertThrows(JSONException.class,
                () -> IntegralSystemDef.parse(standoffOnAnAssist, "armor.test"));
        assertTrue(assistFailure.getMessage().contains(SpecialAiPolicy.CROSSING_UNDER_FIRE.key),
                "the refusal should name the policy a breach assist can declare: "
                        + assistFailure.getMessage());
    }

    /**
     * The carried-item half of the shared vocabulary is refused just as loudly.
     * Membership of one enum is what keeps a single parse path; it is not a
     * licence for a suit to declare a policy written for a grenade.
     */
    @Test
    void aCarriedItemsPolicyIsRefusedOnASuit() throws JSONException {
        JSONObject json = breacherAssistJson()
                .put("policy", SpecialAiPolicy.HARDENED_DIRECT_FIRE.key);
        JSONException failure = assertThrows(JSONException.class,
                () -> IntegralSystemDef.parse(json, "armor.test"));
        assertTrue(failure.getMessage().contains(SpecialAiPolicy.CROSSING_UNDER_FIRE.key),
                failure.getMessage());
    }

    /** A policy outside the vocabulary entirely is told what the vocabulary is. */
    @Test
    void anUnknownPolicyIsRefusedWithTheVocabulary() throws JSONException {
        JSONObject json = breacherAssistJson().put("policy", "charge-the-door");
        JSONException failure = assertThrows(JSONException.class,
                () -> IntegralSystemDef.parse(json, "armor.test"));
        assertTrue(failure.getMessage().contains(SpecialAiPolicy.CROSSING_UNDER_FIRE.key),
                failure.getMessage());
    }

    // ---------------------------------------------------------------- dispatch

    /**
     * The whole reason there is more than one policy. The same scene — a
     * hostile eight cells away, nobody moving — is the standoff pod's moment
     * and is not the breacher's, even though that hostile is comfortably inside
     * the breacher's own threat radius. Set the breacher crossing and its
     * moment arrives without the pod's scene changing at all.
     */
    @Test
    void twoPoliciesSpendThemselvesOnDifferentOccasions() {
        assertTrue(podSpendsASalvo(/*standoff*/ 5f, /*hostileDistance*/ 8),
                "a sighted contact at standoff is the pod's moment");
        assertFalse(breacherSpendsItself(/*radius*/ 12f, /*hostileDistance*/ 8, /*crossing*/ false),
                "standing still with a hostile inside the radius is not the crossing moment");
        assertTrue(breacherSpendsItself(/*radius*/ 12f, /*hostileDistance*/ 8, /*crossing*/ true),
                "crossing with that same hostile inside the radius is");
        assertFalse(podSpendsASalvo(/*standoff*/ 5f, /*hostileDistance*/ 3),
                "a contact already on top of the wearer is past the pod's moment");
    }

    /**
     * Each policy's authored numbers govern its own system and nothing else.
     * Twelve cells is deliberately used on both sides: it is an outer bound for
     * one policy and an inner bound for the other, so a leak between them would
     * flip a result rather than nudge it.
     */
    @Test
    void neitherPolicysNumbersReachTheOthersSystem() {
        assertTrue(breacherSpendsItself(12f, 8, true));
        assertFalse(breacherSpendsItself(4f, 8, true),
                "a narrower authored radius holds the same suit in the same scene");

        assertTrue(podSpendsASalvo(5f, 8));
        assertFalse(podSpendsASalvo(12f, 8),
                "a wider authored standoff holds the same pod in the same scene");
    }

    // ---------------------------------------------------------------- defenders

    /**
     * The defender acceptance. Same authored suit, same geometry, opposite
     * faction: the sweep offers both the same reason and both take it. A
     * failure here means a defender-specific branch has appeared somewhere in
     * the path, which the standing one-data-path rule forbids.
     */
    @Test
    void aDefenderSpendsASystemOnTheSameOccasionAMarineDoes() {
        IntegralSystemDef rig = catalogSystem("armor.foundry-breaker");
        assertEquals(
                crossingCarrierSpends(Faction.MARINE, rig),
                crossingCarrierSpends(Faction.DEFENDER, rig),
                "a hostile in a system-carrying pattern must spend it exactly when a marine does");
        assertTrue(crossingCarrierSpends(Faction.DEFENDER, rig),
                "and the shipped rig's authored policy must actually fire in this scene");
    }

    /**
     * Defender issue threads a pattern's declared system rather than dropping
     * it. The expected id is read out of the armour catalog, so this stays true
     * when the pirate table or the rig's numbers change.
     */
    @Test
    void defenderIssueCarriesTheSystemItsPatternDeclares() {
        GroundRosterRegistry rosters = GroundRosterRegistry.installed();
        assumeTrue(rosters != null, "ground rosters are not installed in this context");
        GroundRosterProfile pirates = GroundRosterRegistry.requireProfile("roster.pirates");
        String expected = catalogSystem("armor.foundry-breaker").id();

        Set<String> carried = new LinkedHashSet<>();
        Random rng = new Random(20260828L);
        for (int roll = 0; roll < 600; roll++) {
            MarineLoadout issued = InfantryLoadoutRolls.defenderLoadout(
                    pirates, GroundRosterProfile.ForceTier.ELITE, RiskLevel.HIGH, rng);
            if (issued.integralSystem != null) carried.add(issued.integralSystem.id());
        }

        assertTrue(carried.contains(expected),
                "a pirate elite roll should sometimes produce the rig the faction is named for,"
                        + " carrying the thing the rig is named after; saw " + carried);
        assertTrue(catalogSystemIds().containsAll(carried),
                "defender issue must not invent a system outside the armour catalog: " + carried);
    }

    /**
     * The recovery fantasy, stated as an equality. The suit the player takes
     * carries the same authored capability — policy and parameters included —
     * as the one it was taken from, because both sides read the same catalog
     * entry. There is no second definition for either side to diverge from.
     */
    @Test
    void aRecoveredSuitIsTheSuitItWasTakenFrom() {
        MarineArmorCatalogDef rig = MarineArmorCatalogRegistry.require("armor.foundry-breaker");
        assumeTrue(rig.hasIntegralSystem(), "fixture assumption: the rig carries a system");

        MarineLoadout recovered = MarineLoadout.fromCatalog(
                UnitRole.COMBATANT, null, null, EquipmentGrade.SERVICE, SoldierProfile.REGULAR,
                null, "marine-a", rig.appearanceFamily(), rig.armorCapacity(), rig.armorRating(),
                rig.moveSpeedMult(), rig.incomingAccuracyMult(), null, rig.integralSystem());

        GroundRosterProfile pirates = GroundRosterRegistry.requireProfile("roster.pirates");
        MarineLoadout takenFrom = null;
        Random rng = new Random(20260828L);
        for (int roll = 0; roll < 600 && takenFrom == null; roll++) {
            MarineLoadout issued = InfantryLoadoutRolls.defenderLoadout(
                    pirates, GroundRosterProfile.ForceTier.ELITE, RiskLevel.HIGH, rng);
            if (issued.integralSystem != null
                    && issued.integralSystem.id().equals(rig.integralSystem().id())) {
                takenFrom = issued;
            }
        }
        assertNotNull(takenFrom, "fixture assumption: the pirate elite table can roll the rig");

        assertEquals(takenFrom.integralSystem, recovered.integralSystem,
                "recovery is a capture, not a stat transfer: every authored value, the use"
                        + " policy included, must be the one the previous owner fought with");
    }

    // ---------------------------------------------------------------- scenes

    /**
     * A breacher and one hostile, geometry frozen so only the policy can move
     * the result. The carrier cannot walk, which is what lets {@code crossing}
     * be the single difference between the two cases: it writes the velocity
     * the crossing policy reads and then offers the sweep its one chance.
     */
    private static boolean breacherSpendsItself(float radiusCells, int hostileDistance,
                                                boolean crossing) {
        BattleSimulation sim = arena();
        long breacher = sim.spawn(carrier("breacher", Faction.MARINE)
                .integralSystem(assist(radiusCells)));
        sim.spawn(hostile(Faction.DEFENDER, CARRIER_X + hostileDistance));
        // A few real ticks so the spatial index the policy queries is populated
        // by the simulation rather than by the test.
        advance(sim, 4);
        if (crossing) beginCrossing(sim, breacher);
        sweep(sim).tick(BattleSimulation.TICK_DT, sim);
        return sim.integralSystems().isActive(breacher);
    }

    /** The same scene with the carrier's faction as the variable rather than its policy. */
    private static boolean crossingCarrierSpends(Faction wearerFaction, IntegralSystemDef system) {
        Faction opposing = wearerFaction == Faction.MARINE ? Faction.DEFENDER : Faction.MARINE;
        BattleSimulation sim = arena();
        long wearer = sim.spawn(carrier("wearer", wearerFaction).integralSystem(system));
        sim.spawn(hostile(opposing, CARRIER_X + 8));
        advance(sim, 4);
        beginCrossing(sim, wearer);
        sweep(sim).tick(BattleSimulation.TICK_DT, sim);
        return sim.integralSystems().isActive(wearer);
    }

    /**
     * A pod and one durable hostile, both stationary, played by the real
     * simulation because a delivered payload has to travel. Whether a salvo
     * left the rack is the answer.
     */
    private static boolean podSpendsASalvo(float standoffCells, int hostileDistance) {
        BattleSimulation sim = arena();
        long gunner = sim.spawn(carrier("gunner", Faction.MARINE).integralSystem(pod(standoffCells)));
        int startingAmmo = sim.integralSystems().ammo(gunner);
        sim.spawn(hostile(Faction.DEFENDER, CARRIER_X + hostileDistance)
                .health(OUTLASTS_THE_RACK));
        advance(sim, OBSERVATION_TICKS);
        return sim.integralSystems().ammo(gunner) < startingAmmo;
    }

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static void advance(BattleSimulation sim, int ticks) {
        for (int i = 0; i < ticks; i++) sim.advance(BattleSimulation.TICK_DT);
    }

    private static EntitySpec carrier(String id, Faction faction) {
        return new EntitySpec(id, faction, UnitType.MARINE, CARRIER_X, ROW).moveSpeed(0f);
    }

    private static EntitySpec hostile(Faction faction, int cellX) {
        return new EntitySpec("hostile", faction, UnitType.MILITIA, cellX, ROW).moveSpeed(0f);
    }

    /**
     * Writes the velocity a crossing suit would have. The carrier's own move
     * speed is zero so the simulation never writes one, which is exactly what
     * makes the scene reproducible: the policy is offered a crossing that the
     * test, not the pathfinder, decided on.
     */
    private static void beginCrossing(BattleSimulation sim, long id) {
        UnitRosterService roster = sim.getRoster();
        roster.entityWorld().setFloat(id, roster.components().MOVEMENT,
                BattleComponents.MOVEMENT_VEL_X, 1f);
    }

    /**
     * A sweep driven a tick at a time. The crossing policy resolves entirely
     * from movement and proximity, so no ballistics are needed to offer it its
     * decision; every scene that uses this carries a breacher and nothing else.
     */
    private static IntegralSystemSystem sweep(BattleSimulation sim) {
        return new IntegralSystemSystem(sim.getRoster(), null, null, new Random(7));
    }

    // ---------------------------------------------------------------- fixtures

    private static IntegralSystemDef catalogSystem(String armorId) {
        MarineArmorCatalogDef pattern = MarineArmorCatalogRegistry.require(armorId);
        assumeTrue(pattern.hasIntegralSystem(),
                "fixture assumption: " + armorId + " carries a system");
        return pattern.integralSystem();
    }

    private static Set<String> catalogSystemIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (MarineArmorCatalogDef pattern : MarineArmorCatalogRegistry.installed().all()) {
            if (pattern.integralSystem() != null) ids.add(pattern.integralSystem().id());
        }
        return ids;
    }

    private static IntegralSystemDef assist(float threatRadiusCells) {
        try {
            return IntegralSystemDef.parse(breacherAssistJson()
                    .put("threatRadiusCells", threatRadiusCells), "armor.test");
        } catch (JSONException failure) {
            throw new AssertionError("test fixture should parse", failure);
        }
    }

    private static IntegralSystemDef pod(float minimumStandoffCells) {
        try {
            return IntegralSystemDef.parse(missilePodJson()
                    .put("minimumStandoffCells", minimumStandoffCells), "armor.test");
        } catch (JSONException failure) {
            throw new AssertionError("test fixture should parse", failure);
        }
    }

    private static JSONObject breacherAssistJson() throws JSONException {
        return new JSONObject()
                .put("id", "system.test-assist")
                .put("grade", "service")
                .put("displayName", "Test assist")
                .put("description", "A shove and a screen.")
                .put("effect", "breacher-assist")
                .put("resource", "cooldown")
                .put("policy", SpecialAiPolicy.CROSSING_UNDER_FIRE.key)
                .put("threatRadiusCells", 12.0)
                .put("durationSeconds", 3.0)
                .put("cooldownSeconds", 22.0)
                .put("moveSpeedMult", 1.4)
                .put("frontalResistance", 0.4)
                .put("shieldedArcDegrees", 140.0);
    }

    private static JSONObject missilePodJson() throws JSONException {
        return new JSONObject()
                .put("id", "system.test-pod")
                .put("grade", "service")
                .put("displayName", "Test volley")
                .put("description", "A brace of micro-missiles.")
                .put("effect", "missile-pod")
                .put("resource", "ammunition")
                .put("policy", SpecialAiPolicy.SIGHTED_STANDOFF_CONTACT.key)
                .put("minimumStandoffCells", 5.0)
                .put("durationSeconds", 1.0)
                .put("startingAmmo", 2)
                .put("weaponId", "weapon.micro-missile");
    }
}
