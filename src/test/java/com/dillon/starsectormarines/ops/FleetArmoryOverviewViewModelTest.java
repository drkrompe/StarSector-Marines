package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.EquipmentAccessTier;
import com.dillon.starsectormarines.marine.EquipmentAcquisitionEligibility;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleetArmoryOverviewViewModelTest {

    private static final float EPSILON = 0.01f;
    private static final List<String> COMPONENTS = List.of(
            "mod/data/ui/components/marine-ops-page-nav.mlx",
            "mod/data/ui/components/armory/fleet-armory-overview.mlx",
            "mod/data/ui/components/armory/armory-company-list.mlx");

    @Test
    void primaryCardProjectsMarineMechAndReadinessAuthority() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY * 2);
        AtomicInteger opens = new AtomicInteger();
        FleetArmoryOverviewViewModel viewModel = new FleetArmoryOverviewViewModel(
                new Reactor(), roster, opens::incrementAndGet);

        FleetArmoryOverviewViewModel.CompanyCard card =
                viewModel.companyCards().get().get(0);
        assertEquals("2 marine squads", card.marineSquads());
        assertEquals("1 mech squad", card.mechSquads());
        assertEquals("24 / 24 marines RTD", card.readiness());
        assertEquals("READY", card.status());
        assertTrue(viewModel.fleetSummary().get().contains("1 owned company"));

        card.open().run();
        assertEquals(1, opens.get());
    }

    @Test
    void landingViewShowsOnlyAcquiredCollectionBandsAndNextAccessGates() {
        MarineRoster roster = new MarineRoster();
        FleetArmoryOverviewViewModel opening = new FleetArmoryOverviewViewModel(
                new Reactor(), roster, () -> { }, () -> 0d,
                () -> new EquipmentAcquisitionEligibility.Progress(4, 4));

        int[] acquired = new int[EquipmentAccessTier.values().length];
        roster.armory().equipmentTemplateCards().forEach(
                card -> acquired[card.accessTier().ordinal()]++);
        List<String> expectedCollection = new ArrayList<>();
        expectedCollection.add("TEMPLATE FILE  ·  "
                + roster.armory().equipmentTemplateCards().size() + " acquired");
        for (EquipmentAccessTier tier : EquipmentAccessTier.values()) {
            int count = acquired[tier.ordinal()];
            if (count > 0) expectedCollection.add(accessLabel(tier) + " " + count);
        }
        String collectionSummary = opening.templateCollectionSummary().get();
        assertEquals(String.join("  ·  ", expectedCollection), collectionSummary);
        assertFalse(collectionSummary.contains("/"));
        assertFalse(collectionSummary.contains("known"));
        assertEquals("CURRENT ACCESS  ·  Licensed / patron Common at MRB +4"
                        + "  ·  Recovery Common at 4 victories  ·  Open market Common only",
                opening.accessStatusSummary().get());
        assertEquals("NEXT ACCESS  ·  Advanced licensed / patron at MRB +5"
                        + "  ·  Advanced recovery after 5 victories"
                        + "  ·  Licensed stock requires Favorable faction standing",
                opening.accessNextSummary().get());

        FleetArmoryOverviewViewModel cleared = new FleetArmoryOverviewViewModel(
                new Reactor(), roster, () -> { }, () -> 0d,
                () -> new EquipmentAcquisitionEligibility.Progress(15, 20));
        assertEquals("All reputation and operational access bands cleared."
                        + "  ·  Licensed stock still requires Favorable faction standing.",
                cleared.accessNextSummary().get());
    }

    @Test
    void shippedOverviewBuildsAsAResponsiveScrollableCardGrid() throws Exception {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        Reactor reactor = new Reactor();
        FleetArmoryOverviewViewModel viewModel = new FleetArmoryOverviewViewModel(
                reactor, roster, () -> { });
        MarkupLoader loader = new MarkupLoader(
                path -> Files.readString(Path.of(path)), COMPONENTS);
        loader.reload();

        try (MarkupInstance instance = loader.build(
                reactor, "fleet-armory-overview", props(viewModel))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());

            document.layout(1744f, 938f);
            UiElement list = instance.requireElement("company-list");
            UiElement card = list.childAt(0);
            assertEquals(1f / 3f,
                    card.box().borderBox().width() / card.box().borderBox().height(),
                    EPSILON);
            assertTrue(card.box().borderBox().right()
                    <= list.box().contentBox().right() + EPSILON);

            document.layout(1163f, 625f);
            assertTrue(list.box().maxScrollTop() > 0f);
        }
    }

    @Test
    void companyCardSurfacesWoundedCountAndEarliestRecovery() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        Map<String, MarineSoldierStatus> outcome = new LinkedHashMap<>();
        outcome.put(roster.soldiers().get(0).id(), MarineSoldierStatus.WIA);
        roster.applySoldierOutcome(outcome, 50f, 0.5f);
        FleetArmoryOverviewViewModel viewModel = new FleetArmoryOverviewViewModel(
                new Reactor(), roster, () -> { }, () -> 50.5d);

        assertEquals("1 WIA  ·  RTD 12h",
                viewModel.companyCards().get().get(0).recovery());
    }

    private static Map<String, Object> props(FleetArmoryOverviewViewModel viewModel) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("fleetSummary", viewModel.fleetSummary());
        props.put("templateCollectionSummary", viewModel.templateCollectionSummary());
        props.put("accessStatusSummary", viewModel.accessStatusSummary());
        props.put("accessNextSummary", viewModel.accessNextSummary());
        props.put("companyCards", viewModel.companyCards());
        putPageNavigation(props);
        return props;
    }

    private static void putPageNavigation(Map<String, Object> props) {
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.ARMORY,
                MarineOpsPageNav.ANY_SHIP,
                () -> { }, () -> { }, () -> { }, () -> { });
    }

    private static String accessLabel(EquipmentAccessTier tier) {
        return switch (tier) {
            case COMMON -> "Common";
            case ADVANCED -> "Advanced";
            case PRESTIGE -> "Prestige";
        };
    }
}
