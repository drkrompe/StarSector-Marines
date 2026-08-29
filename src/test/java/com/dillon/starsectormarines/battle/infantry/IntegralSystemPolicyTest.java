package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.FireStance;
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
    /** Six cells east of the carrier: inside one authored lookahead and outside another. */
    private static final int SWEEP_WALL_X = CARRIER_X + 6;

    /**
     * Far past any authored salvo total, so a standoff scene measures whether
     * the pod chose to fire rather than whether its target happened to survive
     * long enough to be fired at twice.
     */
    private static final float OUTLASTS_THE_RACK = 1_000_000f;

    /** Long enough for a stationary pod to acquire and spend, short enough to stay quick. */
    private static final int OBSERVATION_TICKS = 240;

    /**
     * Enough health that neither side of a scene dies inside the window. A
     * casualty would end the fire the scene is about and turn "the screen never
     * came up" into "nobody was shooting by then".
     */
    private static final float SURVIVES_THE_SCENE = 1_000_000f;

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
                case EXPOSED_UNDER_FIRE -> assertTrue(
                        system.exposedUnderFire().incomingPressureThreshold() > 0f,
                        pattern.id() + " must author how much fire is worth its cooldown");
                case SIGHTED_STANDOFF_CONTACT -> assertTrue(
                        system.sightedStandoff().minimumStandoffCells() > 0f,
                        pattern.id() + " must author the standoff its salvo is judged against");
                case APPROACHING_DEAD_GROUND -> assertTrue(
                        system.approachingDeadGround().lookaheadCells() > 0f,
                        pattern.id() + " must author how far ahead it calls 'ahead'");
                case WOUNDED_SQUADMATE_IN_REACH -> assertTrue(
                        system.fieldAid().reachCells() > 0f
                                && system.fieldAid().restoredHealth() > 0f,
                        pattern.id() + " must author how far it will go and what a"
                                + " dressing is worth");
                case HOLDING_A_FIRING_POSITION -> assertTrue(
                        system.holdingFiringPosition().minimumTargetRangeFraction() > 0f
                                && system.holdingFiringPosition().breakOffRangeCells() > 0f,
                        pattern.id() + " must author how far out is worth planting for and"
                                + " how close is too close to be planted");
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
                .put("policy", SpecialAiPolicy.EXPOSED_UNDER_FIRE.key)
                .put("incomingPressureThreshold", 2.0)
                .put("maxCoverLevel", 1);
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
        assertTrue(assistFailure.getMessage().contains(SpecialAiPolicy.EXPOSED_UNDER_FIRE.key),
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
        assertTrue(failure.getMessage().contains(SpecialAiPolicy.EXPOSED_UNDER_FIRE.key),
                failure.getMessage());
    }

    /** A policy outside the vocabulary entirely is told what the vocabulary is. */
    @Test
    void anUnknownPolicyIsRefusedWithTheVocabulary() throws JSONException {
        JSONObject json = breacherAssistJson().put("policy", "charge-the-door");
        JSONException failure = assertThrows(JSONException.class,
                () -> IntegralSystemDef.parse(json, "armor.test"));
        assertTrue(failure.getMessage().contains(SpecialAiPolicy.EXPOSED_UNDER_FIRE.key),
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
        assertFalse(screenRaised(Scene.answerable()),
                "fire the wearer can shoot back at is not the screen's moment — shoot back");
        assertTrue(screenRaised(Scene.outranged()),
                "fire the wearer cannot reach back at is");
        assertFalse(podSpendsASalvo(/*standoff*/ 5f, /*hostileDistance*/ 3),
                "a contact already on top of the wearer is past the pod's moment");
    }

    /**
     * The waste guard. The same scene, the same incoming, and the only
     * difference is what the suit believes is worth its cooldown: a suit that
     * wants a real volume of fire holds while one that answers the first burst
     * spends. This is the whole reason the threshold is authored, so a failure
     * here means a suit can no longer decline a moment.
     */
    @Test
    void aSuitThatWantsMoreFireThanThisHoldsItsOneCard() {
        assertTrue(screenRaised(Scene.outranged().threshold(1f)),
                "a suit that answers the first rounds spends here");
        assertFalse(screenRaised(Scene.outranged().threshold(500f)),
                "one that wants a volume this scene never reaches keeps it");
    }

    /**
     * The cover gate. Cover resolves ahead of a screen in the durability model,
     * so fire the terrain is already stopping is fire the screen would be paid
     * to stop twice. Identical scenes; the wearer's cell is the difference.
     */
    @Test
    void terrainAlreadyCoveringTheThreatBearingHoldsTheScreen() {
        assertTrue(screenRaised(Scene.outranged()),
                "a wearer in the open spends");
        assertFalse(screenRaised(Scene.outranged().behindCover()),
                "the same wearer, same fire, with the bearing already covered does not");
    }

    /**
     * The occasion that reads path state rather than applied velocity. The
     * wearer here <em>can</em> reach its shooter, so being under way is the only
     * thing offering it the moment — and it is offered through a real
     * {@code sim.advance} tick, in which every mover's applied velocity is the
     * zero the movement pass has not yet overwritten.
     *
     * <p>This is the case the retired scene could not have caught: it wrote the
     * velocity by hand and drove the sweep directly, so it proved the policy's
     * arithmetic against a number the simulation never produces at that point in
     * the tick. A whole Conquest battle raised zero screens while it passed.
     */
    @Test
    void crossingGroundIsReadFromThePathAndNotFromAppliedVelocity() {
        assertFalse(screenRaised(Scene.answerable()),
                "standing still within reach of the shooter is not the moment");
        assertTrue(screenRaised(Scene.answerable().crossing()),
                "the same wearer under way is");
    }

    /**
     * The third policy's own moment, isolated the same way the other two are.
     * The carrier never moves under its own power, so {@code heading} is the
     * single difference between the cases: walking at a wall is dead ground
     * ahead, walking away from it along open floor is not, and standing on the
     * spot is not a moment at all however blind the suit is.
     */
    @Test
    void aSweepSpendsItselfOnGroundItCannotSeeIntoAndNotOtherwise() {
        assertFalse(sweepSpendsItself(/*headingX*/ 0),
                "standing still is not approaching anything");
        assertFalse(sweepSpendsItself(/*headingX*/ -1),
                "walking away down open floor has nothing to read");
        assertTrue(sweepSpendsItself(/*headingX*/ 1),
                "walking at a wall is walking at ground the suit cannot see into");
    }

    /**
     * A sweep's lookahead is its own number and reaches nothing else. The same
     * blind wall is inside one authored lookahead and outside another, with the
     * scene otherwise untouched.
     */
    @Test
    void aSweepsLookaheadDecidesItsOwnMomentAndNoOthers() {
        assertTrue(sweepSpendsItself(1, /*lookahead*/ 8f),
                "a suit that looks eight cells ahead sees the wall six cells away");
        assertFalse(sweepSpendsItself(1, /*lookahead*/ 3f),
                "one that looks three does not");
    }

    /**
     * The medic's whole point: a squadmate who was bleeding is not, and it cost
     * a dressing out of a finite satchel.
     */
    @Test
    void aMedicTreatsTheWorstWoundedSquadmateWithinReach() {
        BattleSimulation sim = arena();
        long medic = sim.spawn(carrier("medic", Faction.MARINE)
                .integralSystem(medicKit(/*reach*/ 5f, /*below*/ 0.6f, /*restores*/ 20f)));
        long scratched = sim.spawn(patient("scratched", CARRIER_X + 1));
        long bleeding = sim.spawn(patient("bleeding", CARRIER_X + 2));
        sim.spawn(hostile(Faction.DEFENDER, CARRIER_X + 30));
        advance(sim, 4);
        sim.world().setHp(scratched, 90f);
        sim.world().setHp(bleeding, 20f);

        int dressings = sim.integralSystems().ammo(medic);
        sweep(sim).tick(BattleSimulation.TICK_DT, sim);

        assertEquals(40f, sim.world().hp(bleeding), 1e-3f,
                "the worst wounded squadmate is the one treated");
        assertEquals(90f, sim.world().hp(scratched), 1e-3f,
                "a marine above the authored threshold is not worth a dressing");
        assertEquals(dressings - 1, sim.integralSystems().ammo(medic),
                "treatment costs one dressing");
    }

    /** A satchel is not spent on people who are fine. */
    @Test
    void aSectionWithNobodyHurtEnoughKeepsItsDressings() {
        BattleSimulation sim = arena();
        long medic = sim.spawn(carrier("medic", Faction.MARINE)
                .integralSystem(medicKit(5f, 0.6f, 20f)));
        long fine = sim.spawn(patient("fine", CARRIER_X + 1));
        sim.spawn(hostile(Faction.DEFENDER, CARRIER_X + 30));
        advance(sim, 4);
        sim.world().setHp(fine, 95f);

        int dressings = sim.integralSystems().ammo(medic);
        sweep(sim).tick(BattleSimulation.TICK_DT, sim);
        assertEquals(dressings, sim.integralSystems().ammo(medic));
    }

    /**
     * Reach is short on purpose. A medic is a marine kneeling next to another
     * marine, not a turret with a healing beam, so a casualty across the street
     * is somebody else's problem.
     */
    @Test
    void aWoundedMarineOutOfReachIsNotTreated() {
        BattleSimulation sim = arena();
        long medic = sim.spawn(carrier("medic", Faction.MARINE)
                .integralSystem(medicKit(/*reach*/ 3f, 0.6f, 20f)));
        long distant = sim.spawn(patient("distant", CARRIER_X + 12));
        sim.spawn(hostile(Faction.DEFENDER, CARRIER_X + 30));
        advance(sim, 4);
        sim.world().setHp(distant, 20f);

        sweep(sim).tick(BattleSimulation.TICK_DT, sim);
        assertEquals(20f, sim.world().hp(distant), 1e-3f);
    }

    /**
     * The carrier is never their own patient. A medic who treated themselves
     * first would be a self-heal wearing a squad system's name, and the value of
     * the role is that it belongs to the section.
     */
    @Test
    void aMedicWillNotTreatThemselves() {
        BattleSimulation sim = arena();
        long medic = sim.spawn(carrier("medic", Faction.MARINE)
                .integralSystem(medicKit(5f, 0.6f, 20f)).hp(100f).maxHp(100f));
        sim.spawn(hostile(Faction.DEFENDER, CARRIER_X + 30));
        advance(sim, 4);
        sim.world().setHp(medic, 15f);

        int dressings = sim.integralSystems().ammo(medic);
        sweep(sim).tick(BattleSimulation.TICK_DT, sim);
        assertEquals(15f, sim.world().hp(medic), 1e-3f,
                "the medic is bleeding and the satchel stays shut");
        assertEquals(dressings, sim.integralSystems().ammo(medic));
    }

    /** A dressing tops a marine up rather than over. */
    @Test
    void treatmentNeverPushesAPatientPastTheirOwnMaximum() {
        BattleSimulation sim = arena();
        long medic = sim.spawn(carrier("medic", Faction.MARINE)
                .integralSystem(medicKit(5f, 0.9f, /*restores*/ 500f)));
        long patient = sim.spawn(patient("patient", CARRIER_X + 1));
        sim.spawn(hostile(Faction.DEFENDER, CARRIER_X + 30));
        advance(sim, 4);
        sim.world().setHp(patient, 50f);

        sweep(sim).tick(BattleSimulation.TICK_DT, sim);
        assertEquals(sim.world().maxHp(patient), sim.world().hp(patient), 1e-3f);
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
                screenRaised(Scene.outranged().wearing(rig).as(Faction.MARINE)),
                screenRaised(Scene.outranged().wearing(rig).as(Faction.DEFENDER)),
                "a hostile in a system-carrying pattern must spend it exactly when a marine does");
        assertTrue(screenRaised(Scene.outranged().wearing(rig).as(Faction.DEFENDER)),
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


    // ---------------------------------------------------------------- the brace

    /**
     * The line role's own moment. A marine who has stopped where they mean to
     * be, holds a live engagement out where accuracy has fallen off, and has
     * nobody about to arrive, plants — which is the whole of
     * <b>assault crosses, line holds</b> expressed as a trigger.
     */
    @Test
    void aMarineHoldingAPositionAtRangeBraces() {
        assertTrue(braces(BraceScene.holding()));
    }

    /**
     * Crossing is the other family's occasion. A marine still walking has not
     * chosen a position yet, so there is nothing to commit to.
     */
    @Test
    void aMarineStillUnderWayDoesNotBrace() {
        assertFalse(braces(BraceScene.holding().crossing()));
    }

    /**
     * Steadiness is worth having where it is scarce. At half a room the round
     * was going to land anyway, and spending a stance on it is the waste the
     * authored fraction exists to refuse.
     */
    @Test
    void aTargetTooCloseToBeWorthPlantingForIsRefused() {
        assertTrue(braces(BraceScene.holding().targetAt(16)),
                "sixteen cells is past half the wearer's reach");
        assertFalse(braces(BraceScene.holding().targetAt(7)),
                "seven cells is not -- and is still outside the break-off, so this is the"
                        + " range judgement answering rather than the proximity one");
    }

    /**
     * The commitment's own counterplay. Planting with somebody about to be on
     * top of you is the mistake the cost makes possible, and the suit that pays
     * most for it names the distance at which it will not.
     */
    @Test
    void aContactInsideTheBreakOffDistanceStopsTheStanceBeingWorthIt() {
        assertFalse(braces(BraceScene.holding().withContactAt(2)),
                "somebody two cells away is inside the authored break-off");
        assertTrue(braces(BraceScene.holding().withContactAt(9)),
                "somebody nine cells away is not");
    }

    /** Nothing engaged is nothing to be steady for. */
    @Test
    void aMarineWithNothingEngagedDoesNotBrace() {
        assertFalse(braces(BraceScene.holding().engagingNothing()));
    }

    // ---------------------------------------------------------------- scenes

    /**
     * One wearer, one shooter that really shoots, played by the real
     * simulation for as long as it takes the fire to matter.
     *
     * <p>Every scene here runs through {@code sim.advance}, so the incoming-fire
     * signal is written by {@code SquadAlertSystem} off actual rounds and the
     * decision is made where it is made in a battle. Nothing is poked into the
     * wearer by hand: what varies between cases is geometry, terrain, the
     * wearer's own reach, and whether it has somewhere to be.
     */
    private static final class Scene {

        private IntegralSystemDef system;
        private float threshold = 2f;
        private float maxCover = 1f;
        private Faction wearerFaction = Faction.MARINE;
        private float wearerReach;
        private int shooterDistance;
        private boolean covered;
        private boolean crossing;

        /** Fire from a shooter the wearer can reach back at: answerable, so no occasion. */
        static Scene answerable() {
            Scene scene = new Scene();
            scene.shooterDistance = 8;
            scene.wearerReach = 20f;
            return scene;
        }

        /** Fire from beyond the wearer's own reach: nothing to do but cover up. */
        static Scene outranged() {
            Scene scene = new Scene();
            scene.shooterDistance = 14;
            scene.wearerReach = 6f;
            return scene;
        }

        Scene threshold(float v) { this.threshold = v; return this; }
        Scene behindCover() { this.covered = true; return this; }
        Scene crossing() { this.crossing = true; return this; }
        Scene wearing(IntegralSystemDef def) { this.system = def; return this; }
        Scene as(Faction faction) { this.wearerFaction = faction; return this; }

        IntegralSystemDef suit() {
            return system != null ? system : shield(threshold, maxCover);
        }
    }

    /**
     * Plays {@code scene} and answers whether the wearer's screen ever came up.
     * Watched every tick rather than sampled at the end, because a screen that
     * ran and expired inside the window is still a screen that was raised.
     */
    private static boolean screenRaised(Scene scene) {
        Faction opposing = scene.wearerFaction == Faction.MARINE
                ? Faction.DEFENDER : Faction.MARINE;
        int shooterX = CARRIER_X + scene.shooterDistance;
        BattleSimulation sim = scene.covered ? coveredArena(shooterX) : arena();

        long wearer = sim.spawn(carrier("wearer", scene.wearerFaction)
                .integralSystem(scene.suit())
                .moveSpeed(scene.crossing ? 2f : 0f)
                .attackRange(scene.wearerReach)
                .attackDamage(1f)
                .accuracy(0.3f)
                .attackCooldown(1f)
                .visionRange(40f)
                .hp(SURVIVES_THE_SCENE).maxHp(SURVIVES_THE_SCENE));
        long shooter = sim.spawn(hostile(opposing, shooterX)
                .attackRange(30f)
                .attackDamage(1f)
                .accuracy(0.95f)
                .attackCooldown(0.2f)
                .visionRange(40f)
                .hp(SURVIVES_THE_SCENE).maxHp(SURVIVES_THE_SCENE));

        if (scene.crossing) sim.setPath(wearer, crossingPath());

        for (int tick = 0; tick < OBSERVATION_TICKS; tick++) {
            // Fire is ordered rather than left to the squad AI, for the reason
            // TtkHarness orders it: the scene is about what the screen does with
            // incoming, not about whether a lone militiaman decided to engage.
            // The rounds themselves are real and travel the ordinary path, which
            // is what writes the signal the policy reads.
            sim.getRoster().combat().setFireIntent(shooter, wearer, FireStance.STANCED, false);
            sim.advance(BattleSimulation.TICK_DT);
            if (sim.integralSystems().isActive(wearer)) return true;
            // A crossing wearer that walks its path out has stopped crossing,
            // which would quietly turn this into a different scene.
            if (scene.crossing && sim.movement().settled(wearer)) {
                sim.setPath(wearer, crossingPath());
            }
        }
        return false;
    }


    /**
     * One wearer with a reach, one thing it is engaging, and optionally somebody
     * else standing nearby. The stance resolves from path state, an engagement,
     * a distance and a proximity count, so the scene needs no ballistics at all:
     * what varies between cases is exactly the four facts the policy reads.
     */
    private static final class BraceScene {

        private static final float REACH = 20f;
        private static final float MINIMUM_FRACTION = 0.5f;
        private static final float BREAK_OFF = 4f;

        private int targetDistance = 15;
        private int contactDistance = -1;
        private boolean crossing;
        private boolean engaged = true;

        static BraceScene holding() { return new BraceScene(); }

        BraceScene targetAt(int cells) { this.targetDistance = cells; return this; }
        BraceScene withContactAt(int cells) { this.contactDistance = cells; return this; }
        BraceScene crossing() { this.crossing = true; return this; }
        BraceScene engagingNothing() { this.engaged = false; return this; }
    }

    /** Plays {@code scene} for one sweep and answers whether the wearer planted. */
    private static boolean braces(BraceScene scene) {
        BattleSimulation sim = arena();
        long wearer = sim.spawn(carrier("wearer", Faction.MARINE)
                .integralSystem(brace(BraceScene.MINIMUM_FRACTION, BraceScene.BREAK_OFF))
                .attackRange(BraceScene.REACH)
                .moveSpeed(scene.crossing ? 2f : 0f)
                .hp(SURVIVES_THE_SCENE).maxHp(SURVIVES_THE_SCENE));
        long target = sim.spawn(named("target", Faction.DEFENDER,
                CARRIER_X + scene.targetDistance));
        if (scene.contactDistance > 0) {
            sim.spawn(named("contact", Faction.DEFENDER, CARRIER_X + scene.contactDistance));
        }
        // One settled tick so the spatial index holds everybody; the engagement
        // and the path are written afterwards, because a two-tick walk would
        // have exhausted the short path this scene uses to mean "under way".
        advance(sim, 1);
        assertFalse(sim.integralSystems().isActive(wearer),
                "fixture assumption: the settle tick offers the stance no reason of its own");

        if (scene.engaged) sim.getRoster().combat().setTargetId(wearer, target);
        if (scene.crossing) sim.setPath(wearer, crossingPath());

        sweep(sim).tick(BattleSimulation.TICK_DT, sim);
        return sim.integralSystems().isActive(wearer);
    }

    /** A short there-and-back that keeps a crossing wearer on roughly its own ground. */
    private static int[] crossingPath() {
        return new int[] {CARRIER_X, ROW, CARRIER_X, ROW + 1, CARRIER_X, ROW + 2};
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

    /**
     * A scout in front of a wall, driven the way the breacher scenes are: the
     * carrier cannot walk, so the velocity the policy reads is the one the test
     * wrote rather than one the pathfinder happened to produce.
     */
    private static boolean sweepSpendsItself(int headingX) {
        return sweepSpendsItself(headingX, 8f);
    }

    private static boolean sweepSpendsItself(int headingX, float lookaheadCells) {
        BattleSimulation sim = walledArena();
        long scout = sim.spawn(carrier("scout", Faction.MARINE)
                .integralSystem(sweepSystem(lookaheadCells)));
        advance(sim, 4);
        if (headingX != 0) setHeading(sim, scout, headingX);
        sweep(sim).tick(BattleSimulation.TICK_DT, sim);
        return sim.integralSystems().isActive(scout);
    }

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /**
     * The open arena plus one wall cell directly between the wearer and its
     * shooter, so the wearer's own cell reads cover on exactly the bearing the
     * incoming rounds arrive from and on no other.
     */
    private static BattleSimulation coveredArena(int shooterX) {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        grid.setCoverAtFacing(CARRIER_X, ROW,
                shooterX > CARRIER_X ? NavigationGrid.FACING_E : NavigationGrid.FACING_W,
                NavigationGrid.MAX_COVER);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** The same arena with one solid column east of the carrier, and open floor west. */
    private static BattleSimulation walledArena() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (x != SWEEP_WALL_X) grid.setWalkableFloor(x, y);
            }
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
        setHeading(sim, id, 1);
    }

    /** Writes the velocity a carrier under way would have, east or west. */
    private static void setHeading(BattleSimulation sim, long id, int signX) {
        UnitRosterService roster = sim.getRoster();
        roster.entityWorld().setFloat(id, roster.components().MOVEMENT,
                BattleComponents.MOVEMENT_VEL_X, signX);
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

    private static IntegralSystemDef shield(float pressureThreshold, float maxCoverLevel) {
        try {
            return IntegralSystemDef.parse(breacherAssistJson()
                    .put("incomingPressureThreshold", pressureThreshold)
                    .put("maxCoverLevel", maxCoverLevel), "armor.test");
        } catch (JSONException failure) {
            throw new AssertionError("test fixture should parse", failure);
        }
    }

    /**
     * A squadmate who spawns whole. Tests wound them after the arena has
     * settled, because the medic is a live system in live ticks: a marine who
     * spawns bleeding has already been treated by the time anything is read.
     */
    private static EntitySpec patient(String id, int cellX) {
        return new EntitySpec(id, Faction.MARINE, UnitType.MARINE, cellX, ROW)
                .moveSpeed(0f).hp(100f).maxHp(100f);
    }

    private static IntegralSystemDef medicKit(float reachCells, float belowFraction,
                                              float restoredHealth) {
        try {
            return IntegralSystemDef.parse(new JSONObject()
                    .put("id", "system.test-aid")
                    .put("grade", "service")
                    .put("displayName", "Test aid")
                    .put("description", "A satchel and somebody willing to kneel down.")
                    .put("effect", "field-aid")
                    .put("resource", "ammunition")
                    .put("policy", SpecialAiPolicy.WOUNDED_SQUADMATE_IN_REACH.key)
                    .put("reachCells", reachCells)
                    .put("treatBelowHealthFraction", belowFraction)
                    .put("restoredHealth", restoredHealth)
                    .put("durationSeconds", 1.5)
                    .put("startingAmmo", 3), "armor.test");
        } catch (JSONException failure) {
            throw new AssertionError("test fixture should parse", failure);
        }
    }

    private static EntitySpec named(String id, Faction faction, int cellX) {
        return new EntitySpec(id, faction, UnitType.MILITIA, cellX, ROW)
                .moveSpeed(0f).hp(SURVIVES_THE_SCENE).maxHp(SURVIVES_THE_SCENE);
    }

    private static IntegralSystemDef brace(float minimumTargetRangeFraction,
                                           float breakOffRangeCells) {
        try {
            return IntegralSystemDef.parse(new JSONObject()
                    .put("id", "system.test-brace")
                    .put("grade", "service")
                    .put("displayName", "Test brace")
                    .put("description", "Planted, and going nowhere for a while.")
                    .put("effect", "brace")
                    .put("resource", "cooldown")
                    .put("policy", SpecialAiPolicy.HOLDING_A_FIRING_POSITION.key)
                    .put("minimumTargetRangeFraction", minimumTargetRangeFraction)
                    .put("breakOffRangeCells", breakOffRangeCells)
                    .put("durationSeconds", 5.0)
                    .put("cooldownSeconds", 18.0)
                    .put("moveSpeedMult", 0.4)
                    .put("accuracyMult", 1.3), "armor.test");
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

    private static IntegralSystemDef sweepSystem(float lookaheadCells) {
        try {
            return IntegralSystemDef.parse(new JSONObject()
                    .put("id", "system.test-sweep")
                    .put("grade", "service")
                    .put("displayName", "Test sweep")
                    .put("description", "One wide active return.")
                    .put("effect", "perception-sweep")
                    .put("resource", "cooldown")
                    .put("policy", SpecialAiPolicy.APPROACHING_DEAD_GROUND.key)
                    .put("lookaheadCells", lookaheadCells)
                    .put("durationSeconds", 3.0)
                    .put("cooldownSeconds", 20.0)
                    .put("revealRangeCells", 30.0)
                    .put("wallReadRadiusCells", 6.0), "armor.test");
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
                .put("policy", SpecialAiPolicy.EXPOSED_UNDER_FIRE.key)
                .put("incomingPressureThreshold", 2.0)
                .put("maxCoverLevel", 1)
                .put("durationSeconds", 3.0)
                .put("cooldownSeconds", 22.0)
                .put("moveSpeedMult", 1.4)
                .put("screenSoak", 15.0)
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
