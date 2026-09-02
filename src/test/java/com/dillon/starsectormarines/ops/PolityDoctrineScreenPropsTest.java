package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.polity.GroundProductionQuality;
import com.dillon.starsectormarines.campaign.polity.PolityDoctrine;
import com.dillon.starsectormarines.campaign.polity.PolityDoctrineLedger;
import com.dillon.starsectormarines.marine.EquipmentAccessTier;
import com.dillon.starsectormarines.marine.EquipmentTemplateCard;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.MarineArmory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The polity's doctrine panel, asked what it projects. Every case builds the
 * props from stated inputs — no sector, no market, no live registry — because
 * that is the whole point of the screen's props seam.
 */
class PolityDoctrineScreenPropsTest {

    private static final String ADVANCED_PATTERN = "armor.combat";
    private static final String SECOND_PATTERN = "armor.aegis-composite";
    private static final String COMMON_PATTERN = "armor.militia";

    private final UnaryOperator<String> copy = ModStrings.fromDisk();
    private final List<CampaignState> rebuilds = new ArrayList<>();
    private int refreshes;

    /** Zero-sum: with the third point spent nothing can be raised anywhere. */
    @Test
    void everyPlusIsDisabledOnceTheLastPointIsSpent() {
        CampaignState state = new CampaignState();
        PolityDoctrineLedger.write(state, PolityDoctrine.of(2, 1, 0));

        List<PolityDoctrineScreen.AxisRow> axes = axes(props(state, null, null));

        assertEquals(0, PolityDoctrineLedger.read(state).pointsRemaining());
        for (PolityDoctrineScreen.AxisRow axis : axes) {
            assertTrue(axis.plusDisabled(), axis.id() + " can still be raised at zero points");
        }
        assertFalse(axes.get(0).minusDisabled(), "quality has two points to give back");
        assertTrue(axes.get(2).minusDisabled(), "heavy support has nothing spent on it");
    }

    /** Nothing spent is nothing to take back, and every axis is still open. */
    @Test
    void everyMinusIsDisabledWhileNothingIsSpent() {
        List<PolityDoctrineScreen.AxisRow> axes = axes(props(new CampaignState(), null, null));

        for (PolityDoctrineScreen.AxisRow axis : axes) {
            assertTrue(axis.minusDisabled(), axis.id() + " offers a point it never had");
            assertFalse(axis.plusDisabled(), axis.id() + " is closed with three points in hand");
        }
    }

    /** An axis stops at its own ceiling even with a point still unspent. */
    @Test
    void anAxisAtItsCeilingIsClosedWithAPointStillInHand() {
        CampaignState state = new CampaignState();
        PolityDoctrineLedger.write(state, PolityDoctrine.of(2, 0, 0));

        List<PolityDoctrineScreen.AxisRow> axes = axes(props(state, null, null));

        assertEquals(1, PolityDoctrineLedger.read(state).pointsRemaining());
        assertTrue(axes.get(0).plusDisabled(), "quality is already at the per-axis ceiling");
        assertFalse(axes.get(1).plusDisabled(), "the spare point can still go on numbers");
    }

    /** One press is one write and one rebuild, and the panel is projected again. */
    @Test
    void aPressWritesTheLedgerAndRebuildsTheRoster() {
        CampaignState state = new CampaignState();

        axes(props(state, null, null)).get(2).plusAction().run();

        assertEquals(1, PolityDoctrineLedger.read(state).heavySupport());
        assertEquals(1, rebuilds.size());
        assertSame(state, rebuilds.get(0));
        assertEquals(1, refreshes);

        axes(props(state, null, null)).get(2).minusAction().run();

        assertEquals(PolityDoctrine.NONE, PolityDoctrineLedger.read(state));
        assertEquals(2, rebuilds.size());
    }

    /**
     * A release moves a card from what could be given to what has been given,
     * and the Armory keeps every card it owned (law 2).
     */
    @Test
    void aReleaseMovesTheCardAcrossAndLeavesTheArmoryAlone() {
        CampaignState state = new CampaignState();
        MarineArmory armory = new MarineArmory();
        String advanced = EquipmentTemplateCatalog.armorId(ADVANCED_PATTERN);
        String second = EquipmentTemplateCatalog.armorId(SECOND_PATTERN);
        armory.acquireEquipmentTemplate(advanced);
        armory.acquireEquipmentTemplate(second);
        Set<String> ownedBefore = new LinkedHashSet<>(armory.ownedEquipmentTemplateIds());

        Map<String, Object> before = props(state, armory, null);
        Set<String> offeredBefore = names(releasable(before));
        assertTrue(offeredBefore.contains(name(advanced)));
        assertTrue(offeredBefore.contains(name(second)));
        assertTrue(released(before).isEmpty());
        String givenAway = releasable(before).get(0).name();
        releasable(before).get(0).action().run();

        Map<String, Object> after = props(state, armory, null);
        assertEquals(offeredBefore.size() - 1, releasable(after).size(),
                "the released card is not offered again");
        assertFalse(names(releasable(after)).contains(givenAway));
        assertEquals(1, released(after).size());
        assertEquals(givenAway, released(after).get(0).name(),
                "cards are named, never listed by id");
        assertEquals(1, state.releasedKitCount);
        assertEquals(1, rebuilds.size(), "a release rebuilds the roster at once");
        assertEquals(ownedBefore, new LinkedHashSet<>(armory.ownedEquipmentTemplateIds()),
                "a release grants a definition and moves no stock");
    }

    /**
     * The Common band is released by construction, so it is on neither list:
     * there is nothing to give and nothing to record.
     */
    @Test
    void theCommonBandIsListedOnNeitherSide() {
        CampaignState state = new CampaignState();
        MarineArmory armory = new MarineArmory();
        armory.acquireEquipmentTemplate(EquipmentTemplateCatalog.armorId(COMMON_PATTERN));
        armory.acquireEquipmentTemplate(EquipmentTemplateCatalog.armorId(ADVANCED_PATTERN));
        Set<String> commonNames = new LinkedHashSet<>();
        for (EquipmentTemplateCard card : EquipmentTemplateCatalog.all()) {
            if (card.accessTier() == EquipmentAccessTier.COMMON) commonNames.add(card.displayName());
        }

        Map<String, Object> before = props(state, armory, null);
        for (String offered : names(releasable(before))) {
            assertFalse(commonNames.contains(offered),
                    offered + " is Common and needs no release");
        }
        releasable(before).get(0).action().run();

        Map<String, Object> after = props(state, armory, null);
        assertEquals(1, released(after).size(), "one release, one row");
        assertFalse(commonNames.contains(released(after).get(0).name()));
    }

    /** The market's own headcount, and the note that says why it is smaller. */
    @Test
    void theHeadcountLineCountsThisMarketAndSaysWhenTheCompanyIsInIt() {
        CampaignState state = new CampaignState();
        PolityDoctrineLedger.write(state, PolityDoctrine.of(0, 2, 0));

        Map<String, Object> garrisoned = props(state, null, market(260f, 40f));

        assertEquals("7 squads (28 troops)", field(garrisoned, "polity-field-headcount"),
                "260 less the company's 40, at 1.5x, is 7.3 fireteams' worth");
        assertEquals("polity-note surface-dark", garrisoned.get("noteClasses"));
        assertFalse(String.valueOf(garrisoned.get("noteText")).isBlank());
    }

    /** No colony under the panel is a headcount that reads as absent, not as zero. */
    @Test
    void withNoMarketTheHeadcountIsAbsentAndTheNoteIsHidden() {
        Map<String, Object> props = props(new CampaignState(), null, null);

        assertEquals(copy.apply("polityNoMarket"), field(props, "polity-field-headcount"));
        assertEquals("polity-note hidden", props.get("noteClasses"));
    }

    /** Law 3 on the panel: a polity with no sheds says so, whatever doctrine says. */
    @Test
    void theProductionLineIsTheStepAndNotTheDoctrine() {
        CampaignState state = new CampaignState();
        PolityDoctrineLedger.write(state, PolityDoctrine.of(2, 0, 1));

        Map<String, Object> props = PolityDoctrineScreen.props(copy, state, null,
                GroundProductionQuality.NONE, null, rebuilds::add, () -> refreshes++, () -> { });

        assertEquals(copy.apply("polityProductionNone"), field(props, "polity-field-production"));
        assertEquals("Surplus", field(props, "polity-field-grades"),
                "no industry admits one grade, so the range collapses to it");
        assertEquals(copy.apply("polityNoHeavySupport"), field(props, "polity-field-lance"),
                "a point of heavy support cannot build a mech where nothing is built");
    }

    private Map<String, Object> props(CampaignState state, MarineArmory armory,
                                      TargetProfile market) {
        return PolityDoctrineScreen.props(copy, state, armory,
                GroundProductionQuality.ADVANCED, market,
                rebuilds::add, () -> refreshes++, () -> { });
    }

    private static TargetProfile market(float groundDefence, float stationed) {
        return new TargetProfile(6, 7, 1, 1, "player",
                EnumSet.noneOf(EconomicFunction.class), SurfacePalette.ROCK,
                SettlementLink.ROAD, groundDefence, stationed);
    }

    private static String name(String cardId) {
        return EquipmentTemplateCatalog.require(cardId).displayName();
    }

    private static Set<String> names(List<PolityDoctrineScreen.ReleaseRow> rows) {
        Set<String> names = new LinkedHashSet<>();
        for (PolityDoctrineScreen.ReleaseRow row : rows) names.add(row.name());
        return names;
    }

    @SuppressWarnings("unchecked")
    private static List<PolityDoctrineScreen.AxisRow> axes(Map<String, Object> props) {
        return (List<PolityDoctrineScreen.AxisRow>) props.get("axisRows");
    }

    @SuppressWarnings("unchecked")
    private static List<PolityDoctrineScreen.ReleaseRow> releasable(Map<String, Object> props) {
        return (List<PolityDoctrineScreen.ReleaseRow>) props.get("releasableRows");
    }

    @SuppressWarnings("unchecked")
    private static List<PolityDoctrineScreen.ReleasedRow> released(Map<String, Object> props) {
        return (List<PolityDoctrineScreen.ReleasedRow>) props.get("releasedRows");
    }

    @SuppressWarnings("unchecked")
    private static String field(Map<String, Object> props, String id) {
        for (PolityDoctrineScreen.FieldRow row
                : (List<PolityDoctrineScreen.FieldRow>) props.get("fieldRows")) {
            if (row.id().equals(id)) return row.value();
        }
        throw new AssertionError("no field row " + id);
    }
}
