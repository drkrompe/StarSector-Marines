package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.marine.IntegralSystemEffect;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadArmorDoctrine;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.ops.spec.StatMeter;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;
import com.dillon.starsectormarines.ui.spec.SpecSheetLayer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentDoctrineDesignerViewModelTest {

    private static final List<String> COMPONENTS = List.of(
            "mod/data/ui/components/marine-ops-page-nav.mlx",
            "mod/data/ui/components/armory/fleet-armory-doctrine-designer.mlx");

    /**
     * The catalog items a billet is authored with — its primary and its
     * specialty — are askable; the role and grade buttons beside them are not,
     * because a role is not a thing in a catalog. Armour is not authored here at
     * all, so it has no tile to bind.
     *
     * <p>The sheet is read through the live projection rather than captured, so
     * cycling a billet's primary changes what the same element says.
     */
    @Test
    void billetPrimaryAndSpecialtyAreAskableAndFollowThePicker() throws Exception {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        Reactor reactor = new Reactor();
        EquipmentDoctrineDesignerViewModel designer = new EquipmentDoctrineDesignerViewModel(
                reactor, roster, roster.squads().get(0).id(),
                SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS);
        MarkupLoader loader = new MarkupLoader(
                path -> Files.readString(Path.of(path)), COMPONENTS);
        loader.reload();

        try (MarkupInstance instance = loader.build(
                reactor, "fleet-armory-doctrine-designer", designerProps(designer))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            SpecSheetLayer layer = SpecSheetLayer.install(document);
            SpecSheetBinder binder = new SpecSheetBinder(document, layer);
            ArmorySpecSheets.bindBilletCards(binder, instance, designer.billets());
            document.layout(1744f, 938f);

            EquipmentDoctrineDesignerViewModel.BilletCard first = designer.billets().get().get(0);
            assertTrue(binder.isBound(instance.requireElement(first.primaryId())));
            assertTrue(binder.isBound(instance.requireElement(first.specialId())));
            assertFalse(binder.isBound(instance.requireElement(first.roleId())),
                    "a billet role is not a catalog item");
            assertFalse(binder.isBound(instance.requireElement(first.gradeId())),
                    "an issue grade is not a catalog item");
            assertEquals(2 * MarineSquad.TEAM_SIZE, binder.size());

            UiElement primary = instance.requireElement(first.primaryId());
            document.pointerMoved(
                    primary.box().borderBox().x() + primary.box().borderBox().width() * 0.5f,
                    primary.box().borderBox().y() + primary.box().borderBox().height() * 0.5f);
            binder.update();
            document.advance(0f);
            String before = instance.requireElement(
                    SpecSheetLayer.ELEMENT_ID + "-title").text();
            assertEquals(first.primarySheet().title(), before);

            first.cyclePrimary().run();
            instance.flush();
            binder.update();
            document.advance(0f);
            // The element never moved, so a captured sheet would still read the
            // weapon that used to be issued here.
            assertNotEquals(before, designer.billets().get().get(0).primarySheet().title());
        }
    }

    private static Map<String, Object> designerProps(
            EquipmentDoctrineDesignerViewModel viewModel) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("squadName", viewModel.squadName());
        props.put("designerHeading", viewModel.heading());
        props.put("designerSubheading", viewModel.subheading());
        props.put("draftName", viewModel.draftName());
        props.put("editName", viewModel.editName());
        props.put("definitions", viewModel.definitions());
        props.put("teamTabs", viewModel.teamTabs());
        props.put("billets", viewModel.billets());
        props.put("feedback", viewModel.feedback());
        props.put("newDraft", viewModel.newDraft());
        props.put("cloneSelected", viewModel.cloneSelected());
        props.put("saveAsNew", viewModel.saveAsNew());
        props.put("rename", viewModel.rename());
        props.put("renameDisabled", viewModel.renameDisabled());
        props.put("delete", viewModel.delete());
        props.put("deleteDisabled", viewModel.deleteDisabled());
        props.put("backToFireTeams", (Runnable) () -> { });
        props.put("back", (Runnable) () -> { });
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.ARMORY,
                MarineOpsPageNav.ANY_SHIP,
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
        return props;
    }

    @Test
    void specialPickerCyclesOnlyCollectedTemplateCards() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        EquipmentDoctrineDesignerViewModel designer = designer(roster);

        Set<SpecialEquipmentDef> seen = cycleSpecials(designer, 12);
        assertFalse(seen.contains(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID)));

        roster.armory().unlockSecondary(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID));
        seen = cycleSpecials(designer, 12);
        assertTrue(seen.contains(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID)));
    }

    @Test
    void primaryAndGradePickersSkipUncollectedCards() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        EquipmentDoctrineDesignerViewModel designer = designer(roster);

        Set<EquipmentGrade> grades = new HashSet<>();
        for (int index = 0; index < 8; index++) {
            designer.billets().get().get(0).cycleGrade().run();
            grades.add(designer.viewerBilletAt(0).grade());
        }
        assertFalse(grades.contains(EquipmentGrade.MILSPEC));
        assertFalse(grades.contains(EquipmentGrade.MASTERWORK));
    }

    @Test
    void billetCardsExposeStableComparativeMetersThatReactToDraftChanges() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        EquipmentDoctrineDesignerViewModel viewModel = designer(roster);

        EquipmentDoctrineDesignerViewModel.BilletCard weapon =
                viewModel.billets().get().get(0);
        assertEquals(List.of("DMG", "RNG", "ACC", "DPS"), weapon.stats().stream()
                .map(StatMeter::label).toList());
        assertTrue(weapon.flavor().contains("Fire-team lead"));
        List<String> before = weapon.stats().stream()
                .map(StatMeter::fillStyle).toList();

        weapon.cyclePrimary().run();

        List<String> after = viewModel.billets().get().get(0).stats().stream()
                .map(StatMeter::fillStyle).toList();
        assertNotEquals(before, after);

    }

    /**
     * <b>The designer authors weapons and nothing else.</b> Armour used to be a
     * second page here, twelve concrete patterns picked a billet at a time and
     * frozen at the moment of saving — which is the artifact the plan model
     * exists to remove, and which no longer has a way in
     * ({@code role-and-access.md}). The marine is still drawn wearing something,
     * and the honest something is what the squad's assigned sheet issues.
     */
    @Test
    void theDesignerDrawsTheArmourTheAssignedSheetIssuesAndCannotChangeIt() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        MarineSquad squad = roster.squads().get(0);
        EquipmentDoctrineDesignerViewModel designer = designer(roster);

        String sheetId = squad.armorDoctrineId() != null
                ? squad.armorDoctrineId() : SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR;
        SquadArmorDoctrine issued = roster.armory().armorDoctrineById(sheetId);
        assertEquals(issued.issueIds().get(0), designer.viewerBilletAt(0).armorId(),
                "the preview wears what the squad's own sheet issues");

        String before = designer.viewerBilletAt(0).armorId();
        for (EquipmentDoctrineDesignerViewModel.BilletCard card : designer.billets().get()) {
            card.cycleRole().run();
            card.cyclePrimary().run();
            card.cycleGrade().run();
            card.cycleSpecial().run();
        }
        assertEquals(before, designer.viewerBilletAt(0).armorId(),
                "no control on this screen may change what a billet wears");
    }

    private static EquipmentDoctrineDesignerViewModel designer(MarineRoster roster) {
        return new EquipmentDoctrineDesignerViewModel(
                new Reactor(), roster, roster.squads().get(0).id(),
                SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS);
    }

    private static Set<SpecialEquipmentDef> cycleSpecials(
            EquipmentDoctrineDesignerViewModel designer, int count) {
        Set<SpecialEquipmentDef> seen = new HashSet<>();
        for (int index = 0; index < count; index++) {
            designer.billets().get().get(0).cycleSpecial().run();
            seen.add(designer.viewerBilletAt(0).specialDef());
        }
        return seen;
    }
}
