package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.spec.SpecSheet;
import com.dillon.starsectormarines.ops.spec.SpecSheets;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.OrbitalBarrage;
import com.dillon.starsectormarines.battle.power.MarineInsertion;
import com.dillon.starsectormarines.battle.power.VehicleSupport;
import com.dillon.starsectormarines.battle.power.ReconPing;
import com.dillon.starsectormarines.battle.power.EmergencyResupply;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.ops.detachment.MissionForceEnvelope;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Arrays;
import java.util.Locale;
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
        props.put("transportHeader", "Landing Craft");
        props.put("carrierHeader", "Fighter Cover");
        props.put("employerHeader", "Employer Provides");
        props.put("assignClasses", "");
        props.put("assignDisabled", false);
        putDebugDrawer(props, false, false, () -> { });
        return withEmptyStyles(props);
    }

    static Map<String, Object> previewProps(boolean conquest) {
        return previewProps(conquest, false);
    }

    static Map<String, Object> previewProps(boolean conquest, boolean debugExpanded) {
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
                "Mira Hale", "Lieutenant · 1 squad command", false, none, "graphics/portraits/portrait_mercenary01.png")));
        props.put("captainEmpty", "");
        putDebugDrawer(props, true, debugExpanded, none);
        props.put("tierSummary", late ? "Full Strength · 84 squads · 168 sorties"
                : "First Contract · 1 squad · 2 sorties");
        props.put("tierSteps", tierPreview(late, none));
        props.put("debugControls", late ? List.of(
                stepper("squads", "Company squads", "84", "", none),
                stepper("mechs", "Player mechs", "8", "REROLL", none),
                choiceStepper("standoff", "Landing standoff", "default", none),
                choiceStepper("landing", "Landing place", "derived", none),
                choiceStepper("lanes", "Resistance lanes", "default", none),
                stepper("zones", "Drop zones", "3", "", none),
                stepper("pairs", "Pairs / zone", "2", "", none),
                stepper("jitter", "Timing jitter", "0.75 s", "", none),
                stepper("transport", "Aeroshuttle transports", "6", "TYPE", none)) : List.of(
                stepper("squads", "Company squads", "1", "", none),
                stepper("mechs", "Player mechs", "0", "REROLL", none),
                stepper("transport", "Aeroshuttle transports", "1", "TYPE", none)));
        props.put("airRows", Arrays.stream(FighterProfile.values()).map(profile -> {
            String token = profile.name().toLowerCase(Locale.ROOT);
            String label = Character.toUpperCase(token.charAt(0)) + token.substring(1);
            return new AirRow("air-" + token, label, profile.spritePath, "ATK", "DEF",
                    "", profile == FighterProfile.DAGGER ? "selected" : "", none, none);
        }).toList());
        props.put("personnelSummary", late ? "Full Strength · 84 squads · 1,008 marines"
                : "First Contract · 1 squad · 12 marines");
        props.put("personnelClasses", "label tone-good commitment-summary");
        props.put("experienceSummary", late ? "Campaign army" : "Conscript / opening company");
        props.put("taskForceRows", late ? List.of(
                forceCard("tf-1", "Major Hale", "34 squads · western lane",
                        "graphics/portraits/portrait_mercenary01.png", ""),
                forceCard("tf-2", "Captain Venn", "25 squads · central lane",
                        "graphics/portraits/portrait_mercenary02.png", ""),
                forceCard("tf-3", "Captain Okafor", "25 squads · eastern lane",
                        "graphics/portraits/portrait_mercenary03.png", "")) : List.of());
        props.put("commandSummary", "5 / 5 slots");
        props.put("powerRows", List.of(
                previewPower("power-barrage", new OrbitalBarrage(), true, false, none),
                previewPower("power-drop", new MarineInsertion(), true, false, none),
                previewPower("power-armour", new VehicleSupport(), false, true, none),
                previewPower("power-recon", new ReconPing(), false, true, none),
                previewPower("power-resupply", new EmergencyResupply(), false, true, none)));
        props.put("powerEmpty", "");
        props.put("sourceRows", List.of(new ChoiceRow("source-siege", "choice-row selected",
                "HSS Siege of Raesvelg", "Mech Support · Orbital Barrage · Marine Drop", false, none, null)));
        props.put("sourceEmpty", "");
        putPreviewLandingCraft(props, conquest, true);
        props.put("carrierRows", List.of());
        props.put("employerRows", List.of(
                info("employer-transport", "Landing craft", "Overridden by DEBUG picker", ""),
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
        return withEmptyStyles(props);
    }

    /** The same visibility projection feeds live missions and preview fixtures. */
    static void putDebugDrawer(Map<String, Object> props, boolean available,
                               boolean expanded, Runnable toggle) {
        boolean open = available && expanded;
        props.put("debugAvailable", available);
        props.put("debugExpanded", open);
        props.put("debugToggleClasses", "debug-drawer-toggle" + (open ? " expanded" : ""));
        props.put("debugToggleLabel", open ? "CLOSE" : "DEBUG");
        props.put("debugToggleArrow", "graphics/ui/console/drawer-" + (open ? "close" : "open") + ".svg");
        props.put("debugToggleDisabled", !available);
        props.put("debugToggleAction", available ? toggle : (Runnable) () -> { });
    }

    /** Detach closed controls before layout so neither gaps nor keyboard targets remain. */
    static void applyDebugVisibility(MarkupInstance markup, Map<String, Object> props) {
        if (!Boolean.TRUE.equals(props.get("debugAvailable"))) {
            UiElement toggle = markup.requireElement("mission-debug-toggle");
            toggle.parent().remove(toggle);
        }
        if (!Boolean.TRUE.equals(props.get("debugExpanded"))) {
            UiElement drawer = markup.requireElement("mission-debug-drawer");
            drawer.parent().remove(drawer);
        }
    }

    static Map<String, Object> campaignPreviewProps(boolean conquest) {
        Map<String, Object> props = previewProps(conquest, false);
        putDebugDrawer(props, false, false, () -> { });
        putPreviewLandingCraft(props, conquest, false);
        props.put("tierSteps", List.of());
        props.put("debugControls", List.of());
        props.put("airRows", List.of());
        props.put("employerRows", List.of(info("employer-transport", "Landing craft",
                "None", ""), info("employer-air", "Allied air", "None", "")));
        return props;
    }

    private static void putPreviewLandingCraft(Map<String, Object> props, boolean conquest, boolean debug) {
        Mission mission = Mission.builder().id("landing-preview").name("Landing preview")
                .type(conquest ? MissionType.CONQUEST : MissionType.SABOTAGE)
                .source(debug ? MissionSource.DEBUG : MissionSource.GENERATED)
                .requiredDrops(conquest ? 168 : 2).build();
        putLandingCraft(props, mission, Collections.nCopies(conquest ? 6 : 1,
                FittedBoat.standard(ShuttleType.AEROSHUTTLE)), conquest ? 1008 : 12,
                debug ? null : "HSS Siege of Raesvelg");
    }

    /** Read deployment assignments; required drops are not a physical boat count. */
    static void putLandingCraft(Map<String, Object> props, Mission mission, List<FittedBoat> boats,
                                int selectedPersonnel, String carrier) {
        boolean local = mission.source == MissionSource.STATIONING;
        boolean debug = mission.source.isDebug();
        String title;
        String capacity;
        String schedule;
        String image = null;
        String tone = "tone-good";
        String note;
        if (local) {
            title = "Local lifts";
            capacity = "Stationed detachment";
            schedule = "No company boats committed";
            note = "This detachment is already stationed here and uses local lift.";
        } else if (boats.isEmpty()) {
            title = "No boats aboard";
            capacity = "0 available";
            boolean employerOnly = !debug && mission.employerShuttles > 0
                    && !MissionForceEnvelope.allowsUnderstrength(mission);
            schedule = employerOnly ? "Employer lift provided" : "Company lift unavailable";
            tone = employerOnly ? "tone-muted" : "tone-danger";
            note = employerOnly ? "Employer-provided craft supply this operation's lift."
                    : "The company needs at least one boat aboard its host ship to deploy.";
        } else {
            ShuttleType type = boats.get(0).pattern();
            List<ShuttleAssignment> manifest = MissionForceEnvelope.allowsUnderstrength(mission)
                    ? DetachmentResolver.buildShuttleManifestForPersonnel(mission, boats, selectedPersonnel)
                    : DetachmentResolver.buildShuttleManifest(mission, boats);
            List<ShuttleAssignment> player = manifest.subList(
                    DetachmentResolver.employerPhysicalShipCount(mission), manifest.size());
            int trips = player.stream().mapToInt(assignment -> assignment.cycles).sum();
            title = type.displayName();
            image = type.spritePath;
            capacity = boats.size() + " available  ·  "
                    + mission.marineArrivalPolicy.seatsPerSortie(type) + " seats / boat trip";
            schedule = trips + (trips == 1 ? " boat trip planned" : " boat trips planned");
            note = player.size() + " of " + boats.size() + " available boats assigned. "
                    + "Each trip is one boat landing; boats return and repeat as needed. "
                    + "Employer trips are listed separately.";
        }
        String origin = local ? "Local support" : debug ? "DEBUG override"
                : carrier == null ? "Company ship" : "Aboard " + carrier;
        props.put("landingCraftTitle", title);
        props.put("landingCraftCapacity", capacity);
        props.put("landingCraftSchedule", schedule);
        props.put("landingCraftOrigin", origin);
        props.put("landingCraftImage", image);
        props.put("landingCraftImageStyle", image == null ? "width: 0px;" : "width: 48px;");
        props.put("landingCraftScheduleClasses", "label landing-craft-line " + tone);
        props.put("landingCraftHint", new SpecSheet(title, "Landing craft", image, "command", List.of(),
                List.of(capacity, schedule, origin, note, local ? "Fleet boats remain aboard their ship."
                        : debug ? "Type and count come from the debug picker."
                        : "Boat type and berths come from the company ship. The Boat Deck retains losses and fittings.")));
    }

    static Map<String, Object> withEmptyStyles(Map<String, Object> props) {
        boolean hasForceCards = !((List<?>) props.getOrDefault("taskForceRows", List.of())).isEmpty();
        props.put("forceSummaryStyle", hasForceCards ? "height: 150px" : "height: 60px");
        props.put("forceCardsStyle", hasForceCards ? "height: 86px" : "height: 0px");
        for (String key : List.of("captainEmpty", "powerEmpty", "sourceEmpty")) {
            String text = (String) props.getOrDefault(key, "");
            props.put(key + "Style", text.isBlank()
                    ? "height: 0px; padding: 0px; overflow: hidden"
                    : "height: auto; white-space: normal");
        }
        return props;
    }

    private static ChoiceRow previewPower(String id, CommandPower power, boolean selected,
                                          boolean disabled, Runnable action) {
        return new ChoiceRow(id, "choice-row" + (selected ? " selected" : ""), power.displayName,
                power.slotWeight + (power.slotWeight == 1 ? " slot" : " slots"),
                disabled, action, null, SpecSheets.commandPower(power));
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

    private static DebugControl choiceStepper(String key, String label, String value, Runnable action) {
        return new DebugControl("debug-" + key, label, value,
                "", "-", "+", "", "", "debug-cycle-absent",
                true, false, false, true, true,
                action, action, action, action, action);
    }

    static ForceCard forceCard(String key, String name, String detail, String portrait, String tone) {
        return new ForceCard("briefing-info-" + key, name, detail, portrait,
                "label force-card-detail " + tone);
    }

    record ForceCard(String id, String name, String detail, String portrait, String detailClasses)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "nameId" -> id + "-label"; case "detailId" -> id + "-value";
                case "copyId" -> id + "-copy"; case "portraitId" -> id + "-portrait";
                case "name" -> name; case "detail" -> detail; case "portrait" -> portrait;
                case "detailClasses" -> detailClasses;
                case "portraitClasses" -> "force-card-portrait console-well"
                        + (portrait == null || portrait.isBlank() ? " portrait-absent" : "");
                default -> null;
            };
        }
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

    private static String rockerSource(boolean selected) {
        return "graphics/ui/console/rocker-" + (selected ? "on" : "off") + ".svg";
    }

    record ChoiceRow(String id, String classes, String title, String detail,
                     boolean disabled, Runnable action, String icon, SpecSheet hint)
            implements MarkupPropertySource {
        ChoiceRow(String id, String classes, String title, String detail,
                  boolean disabled, Runnable action, String icon) {
            this(id, classes, title, detail, disabled, action, icon, null);
        }

        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "copyId" -> id + "-copy";
                case "titleId" -> id + "-title"; case "detailId" -> id + "-detail";
                case "indicatorId" -> id + "-indicator";
                case "indicatorSource" -> rockerSource(classes.contains("selected"));
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
                case "indicatorId" -> id + "-indicator";
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
                case "bankId" -> id + "-bank";
                case "farMinusId" -> id + "-far-minus"; case "minusId" -> id + "-minus";
                case "valueId" -> id + "-value"; case "plusId" -> id + "-plus";
                case "farPlusId" -> id + "-far-plus"; case "cycleId" -> id + "-cycle";
                case "label" -> label; case "value" -> value;
                case "farMinusLabel" -> farMinusLabel; case "minusLabel" -> minusLabel;
                case "plusLabel" -> plusLabel; case "farPlusLabel" -> farPlusLabel;
                case "farMinusClasses" -> farMinusLabel.isEmpty() ? "debug-step-absent" : "";
                case "farPlusClasses" -> farPlusLabel.isEmpty() ? "debug-step-absent" : "";
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

    record AirRow(String id, String label, String image, String attackLabel, String defendLabel,
                  String attackClasses, String defendClasses,
                  Runnable attackAction, Runnable defendAction)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "labelId" -> id + "-label";
                case "imageId" -> id + "-image"; case "image" -> image;
                case "attackId" -> id + "-attack"; case "defendId" -> id + "-defend";
                case "attackIndicatorId" -> id + "-attack-indicator";
                case "defendIndicatorId" -> id + "-defend-indicator";
                case "attackLegendId" -> id + "-attack-legend";
                case "defendLegendId" -> id + "-defend-legend";
                case "attackIndicatorSource" -> rockerSource(attackClasses.contains("selected"));
                case "defendIndicatorSource" -> rockerSource(defendClasses.contains("selected"));
                case "label" -> label; case "attackLabel" -> attackLabel;
                case "defendLabel" -> defendLabel; case "attackClasses" -> attackClasses;
                case "defendClasses" -> defendClasses; case "attackAction" -> attackAction;
                case "defendAction" -> defendAction; default -> null;
            };
        }
    }
}
