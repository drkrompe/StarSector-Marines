package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.PlayerFleetWings;
import com.dillon.starsectormarines.battle.power.CloseAirSupport;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.battleview.DamageDollCanvas;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.spec.SpecSheet;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The briefing's air cover as pictures: one plan per aircraft type the fleet sends, with how many aircraft it
 * puts up. Aircraft are a standing strength, not a stock of sorties: one flies from each carrier bay and one
 * per gunship, so the count is airframes. Fighter wings come from every carrier aboard and close air support from a gunship in the fleet; both
 * are what the company brings, not choices, so nothing here toggles. Words are in each tile's hover.
 */
final class BriefingAir {

    private BriefingAir() { }

    /** One aircraft type: its plan, its short name, how many airframes fly and where they come from. */
    record Craft(String id, String dollPath, String label, int aircraft, SpecSheet hint)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "dollId" -> id + ":doll";
                case "countId" -> id + ":count";
                case "count" -> Strings.format("briefingAirCount", aircraft);
                default -> null;
            };
        }
    }

    /**
     * Fighter types in the order the carriers first carry them, one aircraft a bay, then the gunship when the
     * mission resolved close air support: one for each fleet ship that brings it, or one the contract lends.
     *
     * @param gunshipShips names of the committed fleet ships whose presence brings close air support
     */
    static List<Craft> read(List<PlayerFleetWings.CarrierBay> carriers, List<CommandPower> powers,
                            List<String> gunshipShips) {
        Map<FighterProfile, Integer> aircraft = new LinkedHashMap<>();
        Map<FighterProfile, List<String>> sources = new LinkedHashMap<>();
        for (PlayerFleetWings.CarrierBay carrier : carriers == null ? List.<PlayerFleetWings.CarrierBay>of() : carriers) {
            for (FighterProfile profile : carrier.profiles) {
                aircraft.merge(profile, 1, Integer::sum);
                List<String> names = sources.computeIfAbsent(profile, ignored -> new ArrayList<>());
                if (!names.contains(carrier.shipName)) names.add(carrier.shipName);
            }
        }
        List<Craft> crafts = new ArrayList<>();
        for (Map.Entry<FighterProfile, Integer> entry : aircraft.entrySet()) {
            FighterProfile profile = entry.getKey();
            String name = profile.name().charAt(0) + profile.name().substring(1).toLowerCase(Locale.ROOT);
            crafts.add(new Craft("briefing-air-" + profile.hullId, DamageDollCanvas.dollPath(profile),
                    Strings.upper(name), entry.getValue(), new SpecSheet(name,
                    Strings.format("briefingAirWingRole", entry.getValue()), null, "command", List.of(),
                    List.of(Strings.format("briefingAirFrom", String.join(", ", sources.get(profile)))))));
        }
        for (CommandPower power : powers == null ? List.<CommandPower>of() : powers) {
            if (!(power instanceof CloseAirSupport)) continue;
            String name = ShuttleType.MUDSKIPPER.displayName();
            List<String> ships = gunshipShips == null ? List.of() : gunshipShips;
            int count = Math.max(1, ships.size());
            List<String> notes = new ArrayList<>(List.of(Strings.get("briefingAirGunshipNote")));
            if (!ships.isEmpty()) notes.add(Strings.format("briefingAirFrom", String.join(", ", ships)));
            crafts.add(new Craft("briefing-air-gunship", DamageDollCanvas.GUNSHIP_DOLL, Strings.upper(name),
                    count, new SpecSheet(name, Strings.format("briefingAirGunshipRole", count),
                    null, "command", List.of(), List.copyOf(notes))));
            break;
        }
        return List.copyOf(crafts);
    }

    static void put(Map<String, Object> props, List<Craft> crafts) {
        props.put("airCraft", crafts == null ? List.of() : crafts);
    }

    /** Draws each tile's plan under its short name. */
    static void install(UiDocument document, MarkupInstance markup, Map<String, Object> props) {
        for (Object value : (List<?>) props.getOrDefault("airCraft", List.of())) {
            Craft craft = (Craft) value;
            DamageDollCanvas.View view = new DamageDollCanvas.View();
            view.readAircraft(craft.dollPath());
            document.canvases().set(markup.requireElement(craft.id() + ":doll"),
                    DamageDollCanvas.bare(view, craft.label()));
        }
    }

    static void bindHints(SpecSheetBinder binder, MarkupInstance markup, Map<String, Object> props) {
        for (Object value : (List<?>) props.getOrDefault("airCraft", List.of())) {
            Craft craft = (Craft) value;
            binder.bind(markup.requireElement(craft.id()), craft.hint());
        }
    }
}
