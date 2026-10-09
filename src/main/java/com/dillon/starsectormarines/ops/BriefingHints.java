package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
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
                        ? "Stationed officer" : "Operation commander / optional");
        bindChoices(binder, markup, props, "powerRows", "Command power");
        bindChoices(binder, markup, props, "sourceRows", "Power source");
        bindChoices(binder, markup, props, "carrierRows", "Fighter cover");
        for (Object value : (List<?>) props.getOrDefault("taskForceRows", List.of())) {
            BriefingViewModel.ForceCard row = (BriefingViewModel.ForceCard) value;
            binder.bind(markup.requireElement(row.id()), new SpecSheet(row.name(),
                    row.role() + (row.namedOfficer() ? " / coordinated group" : " / independent squad"),
                    row.portrait(), "command", List.of(), List.of(row.detail(),
                    row.namedOfficer()
                            ? "In Conquest, this officer coordinates these squads as one group and retasks them together."
                            : "The acting NCO follows basic squad orders independently. In Conquest, a home officer or operation officer enables multi-squad coordination.")));
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
        if (!Boolean.TRUE.equals(props.get("debugExpanded"))) return binder;
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
                    ? "Not enough free deck slots. Deselect another power to make room."
                    : "Unavailable with the current selection."
                    : selected ? "Currently selected." : "Click to select.");
            binder.bind(markup.requireElement(row.id()), new SpecSheet(base.title(), base.subtitle(),
                    base.crestPath(), base.accent(), base.stats(), notes));
        }
    }
}
