package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.spec.SpecSheet;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;
import com.dillon.starsectormarines.ui.spec.SpecSheetLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shared live/preview bindings for secondary briefing information. */
final class BriefingHints {
    private BriefingHints() { }

    static SpecSheetBinder install(UiDocument document, MarkupInstance markup, Map<String, Object> props) {
        SpecSheetBinder binder = new SpecSheetBinder(document, SpecSheetLayer.install(document), true);
        bindChoices(binder, markup, props, "captains",
                "Stationed Officer".equals(props.get("captainHeader"))
                        ? Strings.get("briefingUiHintCaptainStationed") : Strings.get("briefingUiHintCaptainCommander"));
        bindChoices(binder, markup, props, "powerRows", Strings.get("briefingUiHintCommandPower"));
        bindChoices(binder, markup, props, "sourceRows", Strings.get("briefingUiHintPowerSource"));
        bindChoices(binder, markup, props, "carrierRows", Strings.get("briefingUiHintFighterCover"));
        for (Object value : (List<?>) props.getOrDefault("taskForceRows", List.of())) {
            BriefingViewModel.ForceCard row = (BriefingViewModel.ForceCard) value;
            binder.bind(markup.requireElement(row.id()), new SpecSheet(row.name(),
                    Strings.join("separatorPath", row.role(), Strings.get(row.namedOfficer()
                            ? "briefingUiHintCoordinatedGroup" : "briefingUiHintIndependentSquad")),
                    row.portrait(), "command", List.of(), List.of(row.detail(),
                    row.namedOfficer()
                            ? Strings.get("briefingUiHintOfficerCoordinates")
                            : Strings.get("briefingUiHintNcoIndependent"))));
        }
        if (props.get("forceComparisonHint") instanceof SpecSheet force) {
            binder.bind(markup.requireElement("mission-force-comparison"), force);
        }
        if (props.get("missionThreatHint") instanceof SpecSheet threat) {
            binder.bind(markup.requireElement("mission-threat"), threat);
        }
        if (props.get("landingCraftHint") instanceof SpecSheet landingCraft) {
            binder.bind(markup.requireElement("mission-transport-readout"), landingCraft);
        }
        BriefingLift.bindHints(binder, markup, props);
        if (!Boolean.TRUE.equals(props.get("debugExpanded"))) return binder;
        // The kit picker's cards open the same field notes and seat sheets they do in the Armory.
        if (props.get("kitPicker") instanceof Map<?, ?> picker && Boolean.TRUE.equals(picker.get("open"))) {
            for (String key : List.of("weaponTiles", "armorTiles")) {
                List<FleetArmoryViewModel.DoctrineTile> tiles = new ArrayList<>();
                for (Object tile : (List<?>) picker.get(key)) tiles.add((FleetArmoryViewModel.DoctrineTile) tile);
                if (!tiles.isEmpty()) ArmorySpecSheets.bindDoctrineTiles(binder, markup, new Reactor().signal(tiles));
            }
        }
        for (Object value : (List<?>) props.getOrDefault("airRows", List.of())) {
            BriefingViewModel.AirRow row = (BriefingViewModel.AirRow) value;
            binder.bind(markup.requireElement(row.id()), new SpecSheet(row.label(),
                    "Debug air support", row.image(), "command", List.of(),
                    List.of("ATK adds this craft to the Marine side; DEF adds it to the defender side.",
                            "Both sides may be selected independently. These are development fixture controls.")));
        }
        return binder;
    }

    private static void bindChoices(SpecSheetBinder binder, MarkupInstance markup,
                                    Map<String, Object> props, String key, String kind) {
        for (Object value : (List<?>) props.getOrDefault(key, List.of())) {
            BriefingViewModel.ChoiceRow row = (BriefingViewModel.ChoiceRow) value;
            SpecSheet base = row.hint() != null ? row.hint()
                    : new SpecSheet(row.title(), kind, row.icon(), "command", List.of(),
                            row.detail().isBlank() ? List.of() : List.of(row.detail()));
            List<String> notes = new ArrayList<>(base.notes());
            boolean selected = row.classes().contains("selected");
            notes.add(row.disabled() ? key.equals("powerRows")
                    ? Strings.get("briefingUiHintNoDeckSlots")
                    : Strings.get("briefingUiHintUnavailable")
                    : Strings.get(selected ? "briefingUiHintSelected" : "briefingUiHintClickToSelect"));
            binder.bind(markup.requireElement(row.id()), new SpecSheet(base.title(), base.subtitle(),
                    base.crestPath(), base.accent(), base.stats(), notes));
        }
    }
}
