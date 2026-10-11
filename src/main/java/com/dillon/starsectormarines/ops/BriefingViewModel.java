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

import java.util.ArrayList;
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
        props.put("narrationAvailable", false);
        props.put("replayNarrationAction", (Runnable) () -> { });
        props.put("stopNarrationAction", (Runnable) () -> { });
        putLeadership(props, false, false);
        props.put("debugHeader", "Mission DEBUG");
        props.put("commitmentHeader", "01 / FORCE COMMITMENT");
        props.put("commandHeader", "02 / COMMAND DECK");
        props.put("sourceHeader", "Fleet Support");
        props.put("transportHeader", "Landing Craft");
        props.put("carrierHeader", "Fighter Cover");
        props.put("employerHeader", "Employer Provides");
        props.put("rewardHeader", "CONTRACT AUTHORIZATION");
        putFactionIdentity(props, "employer", "Undisclosed", null);
        putFactionIdentity(props, "opposition", "Unidentified", null);
        props.put("rewardValue", "Payment not specified");
        props.put("rewardTerms", "Terms supplied by the employer");
        putSurvey(props, null, "Compiling terrain and landing intelligence...");
        props.put("assignClasses", "");
        props.put("assignDisabled", false);
        putDebugDrawer(props, false, false, () -> { });
        return withEmptyStyles(props);
    }

    static Map<String, Object> actingNcoPreviewProps() {
        Map<String, Object> props = campaignPreviewProps(false);
        props.put("captains", List.of(new ChoiceRow("captain-acting-nco", "choice-row selected",
                "No operation commander", "Home officers retain groups; remaining squads use acting NCOs",
                false, () -> { }, null)));
        props.put("captainEmpty", "");
        props.put("taskForceRows", List.of(forceCard("nco-1", "Squad 01",
                "1 squad · 12 marines", null, "", false)));
        return withEmptyStyles(props);
    }

    static Map<String, Object> previewProps(boolean conquest) {
        return previewProps(conquest, false);
    }

    static Map<String, Object> previewProps(boolean conquest, boolean debugExpanded) {
        Map<String, Object> props = baseProps();
        Runnable none = () -> { };
        boolean late = conquest;
        putLeadership(props, conquest, false);
        props.put("missionKicker", late ? "CONQUEST  ·  SEVERE COMMITMENT" : "SABOTAGE  ·  MEDIUM RISK");
        props.put("missionTitle", late ? "CONQUEST — Full Strength" : "SABOTAGE — First Contract");
        props.put("missionFlavor", late
                ? "Commit a campaign army across three active drop zones. Six shuttles cycle squads into contested lanes until the enemy front collapses."
                : "Infiltrate the target complex, disable its critical systems, and withdraw before the garrison can mass against the team.");
        props.put("missionRows", List.of(
                info("presence", "Field presence", late ? "Unrestricted" : "1 active squad / reserves off-map", ""),
                info("opposition", "Expected opposition", late ? "Veteran core / elites probable" : "Regular core / veterans possible", "")));
        putForceComparison(props, late ? OperationTier.FULL_STRENGTH : OperationTier.FIRST_CONTRACT,
                late ? RiskLevel.HIGH : RiskLevel.MEDIUM, late ? 1008 : 12, late ? 1008 : 12);
        props.put("deploymentStatus", "Ready to deploy");
        props.put("rewardValue", late ? "480,000 credits" : "18,000 credits");
        props.put("rewardTerms", "Payable on successful completion");
        putFactionIdentity(props, "employer", "Hegemony", "graphics/factions/crest_hegemony.png");
        putFactionIdentity(props, "opposition", "Pirates", "graphics/factions/crest_pirates.png");
        props.put("termActions", List.of());
        props.put("captains", List.of(new ChoiceRow("captain-preview", "choice-row selected",
                late ? "Hale" : "Mira Hale", late ? "Colonel · 24 squad coordination" : "Lieutenant · 3 squad coordination", false, none, "graphics/portraits/portrait_mercenary01.png")));
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
                forceCard("tf-1", "Colonel Hale", "24 squads · 288 marines",
                        "graphics/portraits/portrait_mercenary01.png", "").asOperationCommander(),
                forceCard("tf-2", "Colonel Venn", "24 squads · 288 marines",
                        "graphics/portraits/portrait_mercenary02.png", ""),
                forceCard("tf-3", "Colonel Okafor", "24 squads · 288 marines",
                        "graphics/portraits/portrait_mercenary03.png", ""),
                forceCard("tf-4", "Colonel Reyes", "12 squads · 144 marines",
                        "graphics/portraits/portrait_mercenary04.png", "")) : List.of());
        props.put("commandSummary", "5 / 5 slots");
        props.put("powerRows", List.of(
                previewPower("power-barrage", new OrbitalBarrage(), true, false, none),
                previewPower("power-drop", new MarineInsertion(), true, false, none),
                previewPower("power-armour", new VehicleSupport(), false, true, none),
                previewPower("power-recon", new ReconPing(), false, true, none),
                previewPower("power-resupply", new EmergencyResupply(), false, true, none)));
        props.put("powerEmpty", "");
        props.put("sourceRows", List.of(new ChoiceRow("source-siege", "choice-row selected",
                "HSS Siege of Raesvelg", "Mech Support · Orbital Barrage · Marine Drop", false, none, "graphics/ships/onslaught/onslaught_base.png")));
        props.put("sourceEmpty", "");
        putPreviewLandingCraft(props, conquest, true);
        props.put("carrierRows", List.of());
        props.put("employerRows", List.of(
                info("employer-transport", "Landing craft", "Overridden by DEBUG picker", ""),
                info("employer-air", "Allied air", late ? "3x Thunder · 2x Dagger" : "None", "")));
        props.put("assignLabel", "ASSIGN SQUADS");
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
    static void applyVisibility(MarkupInstance markup, Map<String, Object> props) {
        if (!Boolean.TRUE.equals(props.get("narrationAvailable"))) {
            for (String id : List.of("mission-narration-replay", "mission-narration-stop")) {
                UiElement control = markup.requireElement(id);
                control.parent().remove(control);
            }
        }
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
        props.put("assignClasses", "");
        props.put("assignDisabled", false);
        props.put("tierSteps", List.of());
        props.put("debugControls", List.of());
        props.put("airRows", List.of());
        props.put("employerRows", List.of(info("employer-transport", "Landing craft",
                "None", ""), info("employer-air", "Allied air", "None", "")));
        return props;
    }

    static void putReward(Map<String, Object> props, Mission mission) {
        int multiplier = mission.cashMultiplier & 0xff;
        if (multiplier == 0) multiplier = 100;
        long payment = (long) mission.payout * multiplier / 100;
        if (SupplyRouteCampaign.freight(mission)) {
            props.put("rewardHeader", "FREIGHT DELIVERY AUTHORIZATION");
            props.put("rewardValue", "Up to 18,000 credits");
            props.put("rewardTerms", "6,000 + 4,000 per carrier delivered / once on victory");
            return;
        }
        if (SupplyRouteCampaign.recovery(mission)) {
            props.put("rewardHeader", "CREW RECOVERY AUTHORIZATION");
            props.put("rewardValue", "Up to 10,000 credits");
            props.put("rewardTerms", "4,000 + 500 per recovered person / once on victory");
            return;
        }
        props.put("rewardHeader", mission.source.isDebug() ? "SIMULATION CONTRACT" : "CONTRACT AUTHORIZATION");
        props.put("rewardValue", payment > 0 ? String.format(Locale.ROOT, "%,d credits", payment) : "No credit payment");
        int salvage = mission.contractSalvageNegotiated & 0xff;
        props.put("rewardTerms", (mission.contractSalvageBaseline & 0xff) > 0
                ? salvage + "% salvage rights / negotiated terms"
                : payment > 0 ? "Payable on successful completion" : "Operation without a cash reward");
    }

    static void putSurvey(Map<String, Object> props, BriefingSurvey map, String pending) {
        props.put("surveyMap", map);
        props.put("surveyStatus", map == null ? pending : "Terrain confirmed / deployment area prepared");
        props.put("surveyDetails", map == null ? "Locating objectives and insertion points"
                : (map.lanes().isEmpty() ? "Direct insertion" : map.lanes().size() + " approach lanes")
                        + " / objectives marked in amber");
        List<InfoRow> rows = new ArrayList<>();
        if (map != null) {
            int i = 0;
            for (String objective : map.objectives()) rows.add(info("survey-objective-" + i++, "", objective, ""));
        }
        props.put("surveyObjectives", List.copyOf(rows));
    }

    /** The recommendation meter is advisory; deployment eligibility remains screen-owned. */
    static void putForceComparison(Map<String, Object> props, OperationTier tier, RiskLevel risk,
                                   int selected, int recommended) {
        props.put("operationScale", tier.displayName);
        props.put("operationRisk", risk.name() + " RISK");
        props.put("scaleIndicators", indicators("scale", tier.ordinal() + 1, 5, "chevron"));
        props.put("riskIndicators", indicators("risk", risk.ordinal() + 1, 3, "skull"));
        props.put("forceSelected", String.format(Locale.ROOT, "%,d", selected));
        props.put("forceRecommended", String.format(Locale.ROOT, "%,d recommended", recommended));
        int filled = recommended > 0 ? (int) Math.min(10, (long) selected * 10 / recommended) : 0;
        props.put("forceSegments", indicators("force", filled, 10, ""));
        props.put("forceAssessment", selected >= recommended ? "Recommendation met" : "Below recommended strength");
        props.put("forceAssessmentClasses", "label " + (selected >= recommended ? "tone-good" : "tone-accent"));
        props.put("forceComparisonHint", new SpecSheet("Force commitment", "Personnel recommendation",
                "graphics/ui/briefing/infantry.svg", "command", List.of(), List.of(
                selected + " selected ready marines / " + recommended + " recommended.",
                "The meter compares personnel only. Experience, equipment, officers, and support still matter.",
                "A recommendation is not a launch requirement or a prediction of victory.")));
        props.put("missionThreatHint", new SpecSheet("Operation profile", tier.displayName, null,
                "command", List.of(), List.of("Chevrons show operation scale: " + tier.displayName + ".",
                "Skulls show risk within that scale: " + risk.name().toLowerCase(Locale.ROOT) + ".",
                "Risk describes uncertainty and defender quality; it does not replace operation scale.")));
    }

    private static List<Indicator> indicators(String prefix, int filled, int total, String symbol) {
        List<Indicator> values = new ArrayList<>();
        for (int i = 0; i < total; i++) values.add(new Indicator(prefix + "-pip-" + i,
                "indicator" + (i < filled ? " lit" : " dim"),
                symbol.isEmpty() ? null : "graphics/ui/briefing/" + symbol + ".svg"));
        return List.copyOf(values);
    }

    record Indicator(String id, String classes, String image) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "classes" -> classes; case "image" -> image;
                default -> null;
            };
        }
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
            boolean employerOnly = DetachmentResolver.hasRequiredTransport(mission, boats);
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

    static void putLeadership(Map<String, Object> props, boolean conquest, boolean stationed) {
        props.put("captainHeader", stationed ? "Stationed Officer" : "Operation Commander / Optional");
        props.put("captainRoleSummary", stationed
                ? "Leads the force assigned to this posting."
                : "Takes squads without a home officer, up to rank capacity. Other officers keep their groups.");
        props.put("groupCommandHeading", conquest ? "FIELD COMMAND / CONQUEST" : "FIELD LEADERSHIP");
        props.put("groupCommandSummary", conquest
                ? "Officers coordinate their squads' advance, regrouping and objective changes. Acting NCOs lead one squad independently."
                : "Home officers lead their assigned squads. Acting NCOs lead squads without an available officer.");
    }

    static void putFactionIdentity(Map<String, Object> props, String role, String name, String crest) {
        props.put(role + "FactionName", name);
        props.put(role + "FactionCrest", crest);
        props.put(role + "FactionCrestStyle", crest == null || crest.isBlank()
                ? "width: 0px; height: 0px" : "width: 40px; height: 40px");
    }

    static Map<String, Object> withEmptyStyles(Map<String, Object> props) {
        props.put("termActionsStyle", ((List<?>) props.getOrDefault("termActions", List.of())).isEmpty()
                ? "height: 0px; overflow: hidden" : "height: 100px");
        boolean hasCarriers = !((List<?>) props.getOrDefault("carrierRows", List.of())).isEmpty();
        props.put("carrierEmpty", hasCarriers ? "" : "No fighter cover");
        props.put("carrierEmptyStyle", hasCarriers ? "height: 0px; padding: 0px;" : "height: 20px;");
        boolean hasForceCards = !((List<?>) props.getOrDefault("taskForceRows", List.of())).isEmpty();
        int cards = ((List<?>) props.getOrDefault("taskForceRows", List.of())).size();
        int cardsHeight = cards > 4 ? 168 : 84;
        props.put("forceSummaryStyle", hasForceCards ? "height: " + (248 + cardsHeight) + "px" : "height: 174px");
        props.put("groupCommandStyle", hasForceCards ? "height: 54px" : "height: 0px; gap: 0px; overflow: hidden");
        props.put("forceCardsStyle", hasForceCards ? "height: " + cardsHeight + "px" : "height: 0px");
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
                disabled, action, SpecSheets.commandPowerIcon(power), SpecSheets.commandPower(power));
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
        return forceCard(key, name, detail, portrait, tone, true);
    }

    static ForceCard forceCard(String key, String name, String detail, String portrait,
                               String tone, boolean namedOfficer) {
        return new ForceCard("briefing-info-" + key, name, detail, portrait,
                "label force-card-detail " + tone, namedOfficer,
                namedOfficer ? "SUB-COMMANDER" : "ACTING NCO");
    }

    record ForceCard(String id, String name, String detail, String portrait, String detailClasses,
                     boolean namedOfficer, String role)
            implements MarkupPropertySource {
        ForceCard asOperationCommander() {
            return new ForceCard(id, name, detail, portrait, detailClasses, namedOfficer, "OP COMMANDER");
        }
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "nameId" -> id + "-label"; case "detailId" -> id + "-value";
                case "copyId" -> id + "-copy"; case "portraitId" -> id + "-portrait";
                case "roleId" -> id + "-role"; case "role" -> role;
                case "roleClasses" -> "label heading force-card-role "
                        + ("OP COMMANDER".equals(role) ? "tone-accent" : "tone-muted");
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
