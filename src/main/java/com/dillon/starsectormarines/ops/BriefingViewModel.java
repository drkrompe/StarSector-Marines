package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Display-ready values shared by the live briefing and deterministic snapshots. */
final class BriefingViewModel {

    private BriefingViewModel() { }

    static Map<String, Object> baseProps() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("screenHeader", "Mission Briefing");
        props.put("captainHeader", "Commanding Officer");
        props.put("debugHeader", "Mission DEBUG");
        props.put("commitmentHeader", "Your Force");
        props.put("commandHeader", "Command Deck");
        props.put("sourceHeader", "Power Sources");
        props.put("transportHeader", "Transport");
        props.put("carrierHeader", "Fighter Cover");
        props.put("employerHeader", "Employer Provides");
        props.put("assignClasses", "");
        props.put("assignDisabled", false);
        return props;
    }

    static Map<String, Object> previewProps(boolean conquest) {
        Map<String, Object> props = baseProps();
        Runnable none = () -> { };
        boolean late = conquest;
        props.put("missionKicker", late ? "CONQUEST  ·  SEVERE COMMITMENT" : "SABOTAGE  ·  MEDIUM RISK");
        props.put("missionTitle", late ? "CONQUEST — Full Strength" : "SABOTAGE — First Contract");
        props.put("missionFlavor", late
                ? "Commit a campaign army across three active drop zones. Six shuttles cycle squads into contested lanes until the enemy front collapses."
                : "Infiltrate the target complex, disable its critical systems, and withdraw before the garrison can mass against the team.");
        props.put("missionRows", late ? List.of(
                info("type", "Type", "Conquest", "tone-danger"),
                info("tier", "Operation scale", "Full Strength · recommends 84 squads", "tone-accent"),
                info("presence", "Field presence", "Unrestricted", ""),
                info("opposition", "Opposition", "Veteran core · elites probable", ""),
                info("lift", "Lift", "168 sorties · 1,008 marines", "")) : List.of(
                info("type", "Type", "Sabotage", "tone-accent"),
                info("tier", "Operation scale", "First Contract · recommends 1 squad", "tone-accent"),
                info("presence", "Field presence", "1 squad active · reserve held off-map", ""),
                info("opposition", "Opposition", "Regular core · veterans possible", ""),
                info("lift", "Lift", "2 sorties · 12 marines", "")));
        props.put("termActions", List.of());
        props.put("captains", List.of(new ChoiceRow("captain-preview", "choice-row selected",
                "Mira Hale", "Lieutenant · 1 squad command", false, none, null)));
        props.put("captainEmpty", "");
        props.put("debugClasses", "panel debug-workspace");
        props.put("tierSummary", late ? "Full Strength · 84 squads · 168 sorties"
                : "First Contract · 1 squad · 2 sorties");
        props.put("tierSteps", tierPreview(late, none));
        props.put("debugControls", late ? List.of(
                stepper("squads", "Company squads", "84", "", none),
                stepper("mechs", "Player mechs", "8", "REROLL", none),
                stepper("zones", "Drop zones", "3", "", none),
                stepper("pairs", "Pairs / zone", "2", "", none),
                stepper("jitter", "Timing jitter", "0.75 s", "", none),
                stepper("transport", "Valkyrie transports", "84", "TYPE", none)) : List.of(
                stepper("squads", "Company squads", "1", "", none),
                stepper("mechs", "Player mechs", "0", "REROLL", none),
                stepper("transport", "Valkyrie transports", "1", "TYPE", none)));
        props.put("airRows", List.of(
                new AirRow("air-talon", "Talon", "[ ] ATK", "[ ] DEF", "", "", none, none),
                new AirRow("air-dagger", "Dagger", "[ ] ATK", "[x] DEF", "", "selected", none, none)));
        props.put("personnelSummary", late ? "Full Strength · 84 squads · 1,008 marines"
                : "First Contract · 1 squad · 12 marines");
        props.put("personnelClasses", "label tone-good commitment-summary");
        props.put("experienceSummary", late ? "Campaign army" : "Conscript / opening company");
        props.put("taskForceRows", late ? List.of(
                info("tf-1", "Major Hale", "34 squads · western lane", ""),
                info("tf-2", "Captain Venn", "25 squads · central lane", ""),
                info("tf-3", "Captain Okafor", "25 squads · eastern lane", "")) : List.of());
        props.put("commandSummary", "5 / 5 slots");
        props.put("powerRows", List.of(
                new ChoiceRow("power-barrage", "choice-row selected", "Orbital Barrage", "3 slots · 4 CP · 5 supplies", false, none, null),
                new ChoiceRow("power-drop", "choice-row selected", "Marine Drop", "2 slots · 3 CP · 2 supplies", false, none, null),
                new ChoiceRow("power-armour", "choice-row", "Armour Support", "2 slots · 3 CP", true, none, null)));
        props.put("powerEmpty", "");
        props.put("sourceRows", List.of(new ChoiceRow("source-siege", "choice-row selected",
                "HSS Siege of Raesvelg", "Mech Support · Orbital Barrage · Marine Drop", false, none, null)));
        props.put("sourceEmpty", "");
        props.put("transportRows", List.of(info("transport-row", "Valkyrie",
                late ? "84 craft · 168 sorties" : "1 craft · 2 sorties", "")));
        props.put("carrierRows", List.of());
        props.put("employerRows", List.of(
                info("employer-transport", "Transport", "Overridden by DEBUG picker", ""),
                info("employer-air", "Allied air", late ? "3x Thunder · 2x Dagger" : "None", "")));
        props.put("assignLabel", "");
        props.put("assignClasses", "briefing-assign-hidden");
        props.put("assignDisabled", true);
        props.put("assignAction", none);
        props.put("deployLabel", "DEPLOY");
        props.put("deployClasses", "briefing-deploy good-surface");
        props.put("deployDisabled", false);
        props.put("deployAction", none);
        props.put("backAction", none);
        return props;
    }

    private static List<TierStep> tierPreview(boolean conquest, Runnable action) {
        return List.of(
                tier("first", "I", "First Contract", !conquest, conquest, action),
                tier("established", "II", "Established", false, conquest, action),
                tier("veteran", "III", "Veteran", false, conquest, action),
                tier("reinforced", "IV", "Reinforced", false, false, action),
                tier("full", "V", "Full Strength", conquest, false, action));
    }

    private static TierStep tier(String key, String numeral, String label,
                                 boolean selected, boolean disabled, Runnable action) {
        return new TierStep("tier-" + key,
                "tier-step" + (selected ? " selected" : "") + (disabled ? " locked" : ""),
                numeral, label, disabled, action);
    }

    private static DebugControl stepper(String key, String label, String value,
                                        String cycleLabel, Runnable action) {
        return new DebugControl("debug-" + key, label, value,
                "-10", "-", "+", "+10", cycleLabel,
                cycleLabel.isEmpty() ? "debug-cycle-absent" : "debug-cycle",
                false, false, false, false, false,
                action, action, action, action, action);
    }

    static InfoRow info(String key, String label, String value, String valueClasses) {
        String base = "briefing-info-" + key;
        return new InfoRow(base, base + "-label", base + "-value", label, value,
                "label info-value " + valueClasses);
    }

    record InfoRow(String id, String labelId, String valueId, String label,
                   String value, String valueClasses) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "labelId" -> labelId; case "valueId" -> valueId;
                case "label" -> label; case "value" -> value;
                case "valueClasses" -> valueClasses; default -> null;
            };
        }
    }

    record ChoiceRow(String id, String classes, String title, String detail,
                     boolean disabled, Runnable action, String icon)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "copyId" -> id + "-copy";
                case "titleId" -> id + "-title"; case "detailId" -> id + "-detail";
                case "iconId" -> id + "-icon"; case "classes" -> classes; case "title" -> title;
                case "detail" -> detail; case "disabled" -> disabled;
                case "action" -> action; case "icon" -> icon; default -> null;
            };
        }
    }

    record TierStep(String id, String classes, String numeral, String label,
                    boolean disabled, Runnable action) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "numeralId" -> id + "-numeral";
                case "labelId" -> id + "-label"; case "classes" -> classes; case "numeral" -> numeral;
                case "label" -> label; case "disabled" -> disabled;
                case "action" -> action; default -> null;
            };
        }
    }

    record DebugControl(String id, String label, String value,
                        String farMinusLabel, String minusLabel,
                        String plusLabel, String farPlusLabel, String cycleLabel,
                        String cycleClasses,
                        boolean farMinusDisabled, boolean minusDisabled,
                        boolean plusDisabled, boolean farPlusDisabled,
                        boolean cycleDisabled, Runnable farMinusAction,
                        Runnable minusAction, Runnable plusAction,
                        Runnable farPlusAction, Runnable cycleAction)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "labelId" -> id + "-label";
                case "farMinusId" -> id + "-far-minus"; case "minusId" -> id + "-minus";
                case "valueId" -> id + "-value"; case "plusId" -> id + "-plus";
                case "farPlusId" -> id + "-far-plus"; case "cycleId" -> id + "-cycle";
                case "label" -> label; case "value" -> value;
                case "farMinusLabel" -> farMinusLabel; case "minusLabel" -> minusLabel;
                case "plusLabel" -> plusLabel; case "farPlusLabel" -> farPlusLabel;
                case "cycleLabel" -> cycleLabel;
                case "cycleClasses" -> cycleClasses;
                case "farMinusDisabled" -> farMinusDisabled; case "minusDisabled" -> minusDisabled;
                case "plusDisabled" -> plusDisabled; case "farPlusDisabled" -> farPlusDisabled;
                case "cycleDisabled" -> cycleDisabled;
                case "farMinusAction" -> farMinusAction; case "minusAction" -> minusAction;
                case "plusAction" -> plusAction; case "farPlusAction" -> farPlusAction;
                case "cycleAction" -> cycleAction; default -> null;
            };
        }
    }

    record AirRow(String id, String label, String attackLabel, String defendLabel,
                  String attackClasses, String defendClasses,
                  Runnable attackAction, Runnable defendAction)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "labelId" -> id + "-label";
                case "attackId" -> id + "-attack"; case "defendId" -> id + "-defend";
                case "label" -> label; case "attackLabel" -> attackLabel;
                case "defendLabel" -> defendLabel; case "attackClasses" -> attackClasses;
                case "defendClasses" -> defendClasses; case "attackAction" -> attackAction;
                case "defendAction" -> defendAction; default -> null;
            };
        }
    }
}
