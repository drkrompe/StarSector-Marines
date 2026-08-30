package com.dillon.starsectormarines.marine;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a plan becomes twelve suits ({@code role-and-access.md}).
 *
 * <p>Every case here runs against a hand-built catalog rather than the shipped
 * one. The rules under test are about the <em>arrangement</em> of a role's
 * candidates — how many there are and what tiers they sit at — and pinning them
 * to the ids that happen to occupy those cells today would make a content edit
 * look like a code failure.
 */
class ArmorIssueResolverTest {

    /**
     * The defect this rule exists for. Two patterns of the same role, tradition
     * and tier, and a section with two billets of that role: issuing the single
     * best one twice makes the other unwearable at any company that owns both,
     * which is how the field-aid satchel stopped existing at the top of the
     * ladder and how a contributed pattern gets shadowed by the core one whose
     * cell it shares.
     */
    @Test
    void twoBilletsOfOneRoleTakeTwoDifferentSuitsWhenTheirTierHoldsTwo() {
        withCatalog(List.of(
                pattern("t.line", ArmorRole.LINE, 2, 10f, 8f),
                pattern("t.pod", ArmorRole.SUPPORT, 2, 9f, 7f),
                pattern("t.medic", ArmorRole.SUPPORT, 2, 8f, 6f)), () -> {
            SquadArmorDoctrine issued = ArmorIssueResolver.resolveUnrestricted(
                    plan(SquadRoleMix.BALANCED));

            List<String> support = wornBy(ArmorRole.SUPPORT, SquadRoleMix.BALANCED, issued);
            assertEquals(2, support.size(), "fixture assumption: two support billets");
            assertEquals(List.of("t.pod", "t.medic"), support,
                    "both support patterns are worth the same price, so both get worn"
                            + " — strongest to the first billet");
        });
    }

    /**
     * The other half of the same rule. A tier is a price band, and spreading
     * must not become "put somebody in whatever else is in the hold": eight
     * riflemen with one battlesuit and a pile of surplus all wear the
     * battlesuit.
     */
    @Test
    void aRoleDoesNotReachBelowItsBestTierToVaryWhatItIssues() {
        withCatalog(List.of(
                pattern("t.best-line", ArmorRole.LINE, 4, 20f, 12f),
                pattern("t.surplus-line", ArmorRole.LINE, 1, 3f, 2f),
                pattern("t.older-line", ArmorRole.LINE, 2, 6f, 4f),
                pattern("t.support", ArmorRole.SUPPORT, 2, 9f, 7f)), () -> {
            SquadArmorDoctrine issued = ArmorIssueResolver.resolveUnrestricted(
                    plan(SquadRoleMix.LINE_HOLD));

            List<String> line = wornBy(ArmorRole.LINE, SquadRoleMix.LINE_HOLD, issued);
            assertTrue(line.size() > 1, "fixture assumption: several line billets");
            assertEquals(Set.of("t.best-line"), new LinkedHashSet<>(line),
                    "the surplus in the hold is cheaper kit, not a second option");
        });
    }

    /** The plan's tradition still decides absolutely; spreading happens inside it. */
    @Test
    void spreadingNeverReachesOutsideThePlansOwnTradition() {
        withCatalog(List.of(
                pattern("t.own-line", ArmorRole.LINE, 3, 10f, 8f, ArmorTradition.HEGEMONY),
                pattern("t.other-line", ArmorRole.LINE, 3, 11f, 9f, ArmorTradition.PIRATES),
                pattern("t.own-support", ArmorRole.SUPPORT, 3, 9f, 7f,
                        ArmorTradition.HEGEMONY)), () -> {
            SquadArmorDoctrine issued = ArmorIssueResolver.resolve(
                    plan(SquadRoleMix.LINE_HOLD, ArmorTradition.HEGEMONY), pattern -> true);

            assertTrue(issued.issueIds().stream().noneMatch("t.other-line"::equals),
                    "a stronger suit from somebody else's tradition is still not this"
                            + " section's kit: " + issued.issueIds());
        });
    }

    /**
     * A role nobody stocks still dresses its billets. The fallback existed
     * before spreading did and has to survive it, because a marine in the wrong
     * suit beats a marine in none.
     */
    @Test
    void aRoleWithNothingAvailableFallsBackToLineKit() {
        withCatalog(List.of(
                pattern("t.line", ArmorRole.LINE, 2, 10f, 8f)), () -> {
            SquadArmorDoctrine issued = ArmorIssueResolver.resolveUnrestricted(
                    plan(SquadRoleMix.BALANCED));

            assertEquals(MarineSquad.CAPACITY, issued.issueIds().size());
            assertEquals(Set.of("t.line"), new LinkedHashSet<>(issued.issueIds()));
        });
    }

    /** A squad's appearance must not shuffle between two runs of the same battle. */
    @Test
    void issuingTheSamePlanFromTheSameStockTwiceGivesTheSameTwelve() {
        withCatalog(List.of(
                pattern("t.a-line", ArmorRole.LINE, 3, 10f, 8f),
                pattern("t.b-line", ArmorRole.LINE, 3, 10f, 8f),
                pattern("t.support", ArmorRole.SUPPORT, 3, 9f, 7f)), () -> {
            SquadArmorPlan plan = plan(SquadRoleMix.BALANCED);

            assertEquals(ArmorIssueResolver.resolveUnrestricted(plan).issueIds(),
                    ArmorIssueResolver.resolveUnrestricted(plan).issueIds());
        });
    }

    // ---------------------------------------------------------------- fixture

    private static List<String> wornBy(ArmorRole role, SquadRoleMix mix,
                                       SquadArmorDoctrine issued) {
        List<String> worn = new ArrayList<>();
        for (int billet = 0; billet < MarineSquad.CAPACITY; billet++) {
            if (mix.roleAt(billet) == role) worn.add(issued.issueIds().get(billet));
        }
        return worn;
    }

    private static SquadArmorPlan plan(SquadRoleMix mix) {
        return plan(mix, ArmorTradition.HEGEMONY);
    }

    private static SquadArmorPlan plan(SquadRoleMix mix, ArmorTradition tradition) {
        return new SquadArmorPlan("plan.test", "Test plan", "", tradition, mix);
    }

    private static JSONObject pattern(String id, ArmorRole role, int tier,
                                      float capacity, float rating) {
        return pattern(id, role, tier, capacity, rating, ArmorTradition.HEGEMONY);
    }

    private static JSONObject pattern(String id, ArmorRole role, int tier,
                                      float capacity, float rating,
                                      ArmorTradition tradition) {
        try {
            return patternJson(id, role, tier, capacity, rating, tradition);
        } catch (JSONException failure) {
            throw new IllegalStateException("test pattern fixture failed", failure);
        }
    }

    private static JSONObject patternJson(String id, ArmorRole role, int tier,
                                          float capacity, float rating,
                                          ArmorTradition tradition) throws JSONException {
        return new JSONObject()
                .put("id", id)
                .put("catalog", new JSONObject()
                        .put("displayName", id)
                        .put("role", role.key)
                        .put("tradition", tradition.key)
                        .put("description", "A test pattern.")
                        .put("tier", tier)
                        .put("iconPath", "graphics/ui/armory/armor-tier-1-light.png"))
                .put("battle", new JSONObject()
                        .put("appearanceFamily", "ARMY_GREEN")
                        .put("armorCapacity", capacity)
                        .put("armorRating", rating)
                        .put("moveSpeedMult", 1.0)
                        .put("incomingAccuracyMult", 1.0));
    }

    private static void withCatalog(List<JSONObject> patterns, Runnable body) {
        MarineArmorCatalogRegistry previous = MarineArmorCatalogRegistry.installed();
        try {
            JSONArray armor = new JSONArray();
            patterns.forEach(armor::put);
            MarineArmorCatalogRegistry catalog = new MarineArmorCatalogRegistry();
            catalog.ingest(new JSONObject().put("armor", armor));
            MarineArmorCatalogRegistry.install(catalog);
            body.run();
        } catch (Exception failure) {
            throw new IllegalStateException("test catalog fixture failed", failure);
        } finally {
            MarineArmorCatalogRegistry.install(previous);
        }
    }
}
