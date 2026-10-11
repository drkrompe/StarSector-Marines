package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.AbandonedColonyArchiveOutcome;
import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import com.dillon.starsectormarines.ops.loot.LootManifest;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.fs.starfarer.api.Global;

import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/** MLX-authored read-only operation debrief and recovery handoff. */
public class ResultsScreen extends MissionFlowMlxScreen {

    static final String ROOT_COMPONENT = "mission-results";
    static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/missions/mission-results.mlx");

    public ResultsScreen() {
        super(ROOT_COMPONENT, COMPONENT_PATHS);
    }

    @Override
    protected Map<String, Object> props() {
        MissionOutcome outcome = context != null ? context.getLastOutcome() : null;
        boolean victory = outcome != null && outcome.victory;
        LootManifest manifest = context != null ? context.getLootManifest() : LootManifest.EMPTY;
        Map<String, Object> props = baseProps();
        props.put("outcomeClasses", "results-outcome " + (victory ? "victory" : "defeat"));
        props.put("outcomeLabel", Strings.get(victory ? "battleVictory" : "battleDefeat"));
        props.put("missionName", outcome != null && outcome.missionName != null
                ? outcome.missionName : "Operation record unavailable");
        props.put("missionMeta", outcomeMeta(outcome, manifest));
        props.put("resultRows", resultRows(outcome, manifest));
        putStoryDebrief(props, outcome);
        props.put("personnelHeader", personnelHeader(outcome));
        List<PersonnelRow> personnel = personnelRows(outcome);
        props.put("squadRows", personnel);
        props.put("personnelEmpty", personnel.isEmpty() ? noPersonnelMessage(outcome) : "");
        if (manifest != null && !manifest.isEmpty()) {
            props.put("secondaryClasses", "danger-surface console-action");
            props.put("secondaryLabel", Strings.get("resultsForfeitSalvage"));
            props.put("secondaryAction", (Runnable) this::returnToMissions);
            props.put("primaryLabel", Strings.get("resultsReviewSalvage"));
            props.put("primaryAction", (Runnable) () -> context.goTo(ScreenId.LOOT));
        } else {
            props.put("secondaryClasses", "hidden");
            props.put("secondaryLabel", "");
            props.put("secondaryAction", (Runnable) () -> { });
            props.put("primaryLabel", Strings.get("resultsReturn"));
            props.put("primaryAction", (Runnable) this::returnToMissions);
        }
        return props;
    }

    static Map<String, Object> previewProps(boolean victory, boolean salvage) {
        Runnable none = () -> { };
        Map<String, Object> props = baseProps();
        props.put("title", "Debrief");
        props.put("outcomeClasses", "results-outcome " + (victory ? "victory" : "defeat"));
        props.put("outcomeLabel", victory ? "VICTORY" : "DEFEAT");
        props.put("missionName", "SABOTAGE — First Contract");
        props.put("missionMeta", "Operation settled · employer report filed");
        props.put("resultRows", List.of(
                row("result-payout", "Payout", victory ? "20,500 credits" : "—", "tone-good"),
                row("result-salvage", "Salvage", salvage ? "35% · 8 stacks · 14,200 credits" : "None", "tone-accent"),
                row("result-casualties", "Marines lost", "1 of 12", "tone-danger"),
                row("result-captain", "Captain", "Mira Hale — returned safely", "tone-good"),
                row("result-xp", "Experience", "+240 XP", "tone-edge")));
        props.put("personnelHeader", "PERSONNEL — RTD / WIA / MIA / KIA");
        props.put("squadRows", List.of(
                new PersonnelRow("result-squad-preview-0", "1st Squad",
                        "10R 1W 0M 1K", "label heading results-squad-summary tone-accent",
                        "Hale R · Chen R · Ilyin W · Okafor R · Bell K · Ruiz R"),
                new PersonnelRow("result-squad-preview-1", "2nd Squad",
                        "12R 0W 0M 0K", "label heading results-squad-summary tone-good",
                        "Vale R · Sato R · Moss R · Holt R · Venn R · Ward R")));
        props.put("personnelEmpty", "");
        props.put("secondaryClasses", salvage ? "danger-surface console-action" : "hidden");
        props.put("secondaryLabel", salvage ? "FORFEIT SALVAGE" : "");
        props.put("secondaryAction", none);
        props.put("primaryLabel", salvage ? "REVIEW SALVAGE" : "RETURN");
        props.put("primaryAction", none);
        return props;
    }

    private static Map<String, Object> baseProps() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("title", Strings.get("resultsHeader"));
        putStoryDebrief(props, null);
        return props;
    }

    static void putStoryDebrief(Map<String, Object> props, MissionOutcome outcome) {
        StoryScene scene = outcome == null ? null : StoryScene.forRecord(outcome.missionId, outcome.missionSource);
        props.put("sceneClasses", scene == null ? "hidden" : "results-scene console-well");
        props.put("sceneImage", scene == null ? null : scene.image);
        props.put("sceneCaption", scene == null ? "" : "LOCATION ARCHIVE / " + scene.location);
        OpeningOperationKind chapter = WaylineArc.chapter(outcome);
        props.put("storyClasses", chapter != null ? "story-dispatch console-readout" : "hidden");
        props.put("storyPortrait", chapter != null ? OpeningBroker.PORTRAIT : null);
        props.put("storySender", chapter != null ? OpeningBroker.NAME + " / WAYLINE" : "");
        props.put("storyProgress", chapter == null ? "" : !outcome.victory ? "CONTRACT REMAINS OPEN"
                : chapter == OpeningOperationKind.RELIEF ? "CHAPTER 1 COMPLETE / DEPOT JOB AVAILABLE" : "AN OPEN ROAD / ARC COMPLETE");
        props.put("storyMessage", WaylineArc.debrief(outcome));
        if (outcome != null && SupplyRouteCampaign.owns(outcome.missionId)) {
            props.put("storyClasses", "story-dispatch console-readout");
            props.put("storyPortrait", outcome.lastLiftOutcome != null ? SupplyRouteCampaign.IONA_PORTRAIT : OpeningBroker.PORTRAIT);
            props.put("storySender", (outcome.lastLiftOutcome != null ? "Iona Serr" : OpeningBroker.NAME) + " / WAYLINE DISPATCH");
            props.put("storyProgress", outcome.missionSource == MissionSource.RECURRING ? "RECURRING WORK / JOB REPORT" : "BROKEN SUPPLY ROUTE");
            String report = outcome.victory ? "Objective confirmed. Payment: " + outcome.payoutEarned + " cr." : "Operation withdrawn. No payment; confirmed losses remain on record.";
            if (outcome.lastLiftOutcome != null) {
                int stranded = outcome.lastLiftOutcome.attempt().people().size()
                        - outcome.lastLiftOutcome.rescued().size() - outcome.lastLiftOutcome.dead().size();
                report += " This sortie: " + outcome.lastLiftOutcome.rescued().size() + " rescued / "
                        + outcome.lastLiftOutcome.dead().size() + " confirmed dead / " + stranded + " still stranded."
                        + (stranded > 0 ? " Retry covers those still stranded." : " This crew has no remaining rescue attempts.");
            }
            var route = SupplyRouteCampaign.current();
            if (route != null && outcome.missionSource == MissionSource.STORY) report += " " + route.status();
            props.put("storyMessage", report);
        }
    }

    private static String outcomeMeta(MissionOutcome outcome, LootManifest manifest) {
        if (outcome == null) return "No frozen outcome is attached to this debrief.";
        if (manifest != null && !manifest.isEmpty()) {
            return "Operation settled · recovery claim ready for review";
        }
        return "Operation settled · no recovery claim remains";
    }

    static List<ResultRow> resultRows(MissionOutcome outcome, LootManifest manifest) {
        return resultRows(outcome, manifest, Strings::get);
    }

    static List<ResultRow> resultRows(MissionOutcome outcome, LootManifest manifest, UnaryOperator<String> copy) {
        if (outcome == null) return List.of();
        List<ResultRow> rows = new ArrayList<>();
        if (outcome.evacuationRepresentatives > 0) {
            rows.add(row("result-evacuation", trimLabel(copy.apply("resultsEvacuationLabel")),
                    formatEvacuation(outcome), "tone-edge"));
        }
        if (outcome.colonyArchiveOutcome != AbandonedColonyArchiveOutcome.NONE) {
            rows.add(row("result-colony", trimLabel(copy.apply("resultsColonyArchiveLabel")),
                    formatColonyArchive(outcome), outcome.colonyArchiveOutcome
                            == AbandonedColonyArchiveOutcome.RECOVERED ? "tone-good" : "tone-danger"));
        }
        String payout = outcome.payoutEarned > 0
                ? MessageFormat.format(copy.apply("payoutFmt"),
                NumberFormat.getIntegerInstance().format(outcome.payoutEarned)) : "—";
        rows.add(row("result-payout", trimLabel(copy.apply("resultsPayoutLabel")),
                payout, outcome.payoutEarned > 0 ? "tone-good" : "tone-muted"));
        if (outcome.salvageEntitlement > 0) {
            rows.add(row("result-salvage", trimLabel(copy.apply("resultsSalvageLabel")),
                    salvageSummary(outcome, manifest), "tone-accent"));
        }
        rows.add(row("result-casualties", trimLabel(copy.apply("resultsCasualtiesLabel")),
                MessageFormat.format(copy.apply("resultsCasualtiesFmt"),
                        outcome.marinesLost, outcome.marinesEngaged),
                outcome.marinesLost > 0 ? "tone-danger" : "tone-good"));
        // Allied losses are reported and never counted as the company's. The
        // row appears only when allies actually took the field, so an ordinary
        // contract's debrief is unchanged.
        if (outcome.alliesEngaged > 0) {
            rows.add(row("result-allied-losses",
                    trimLabel(copy.apply("resultsAlliedLossesLabel")),
                    MessageFormat.format(copy.apply("resultsAlliedLossesFmt"),
                            outcome.alliesLost, outcome.alliesEngaged),
                    outcome.alliesLost > 0 ? "tone-edge" : "tone-good"));
        }
        if (!outcome.boatsLost.isEmpty()) {
            rows.add(row("result-boats-lost", trimLabel(copy.apply("resultsBoatsLostLabel")),
                    formatBoatsLost(outcome), "tone-danger"));
        }
        if (outcome.captainId != null) {
            rows.add(row("result-captain", trimLabel(copy.apply("resultsCaptainLabel")),
                    formatCaptainStatus(outcome), statusTone(outcome.newCaptainStatus)));
        }
        if (outcome.xpGained > 0) {
            rows.add(row("result-xp", trimLabel(copy.apply("resultsXpLabel")),
                    MessageFormat.format(copy.apply("resultsXpFmt"), outcome.xpGained), "tone-edge"));
        }
        Rank promotedTo = outcome.promotedTo;
        if (promotedTo != null) {
            rows.add(row("result-promotion", trimLabel(copy.apply("resultsPromotionLabel")),
                    MessageFormat.format(copy.apply("resultsPromotionFmt"), promotedTo.displayName()),
                    "tone-accent"));
        }
        return List.copyOf(rows);
    }

    private static String salvageSummary(MissionOutcome outcome, LootManifest manifest) {
        if (manifest != null && !manifest.isEmpty() && outcome.salvageRecoveryBonusPct > 0) {
            return MessageFormat.format(Strings.get("resultsSalvageModifiedFmt"),
                    outcome.salvageEntitlement, outcome.salvageRecoveryBonusPct,
                    manifest.stacks.size(), NumberFormat.getIntegerInstance().format(manifest.selectionBudget));
        }
        if (manifest != null && !manifest.isEmpty()) {
            return MessageFormat.format(Strings.get("resultsSalvageManifestFmt"),
                    outcome.salvageEntitlement, manifest.stacks.size(),
                    NumberFormat.getIntegerInstance().format(manifest.selectionBudget));
        }
        return MessageFormat.format(Strings.get("resultsSalvageFmt"), outcome.salvageEntitlement);
    }

    private List<PersonnelRow> personnelRows(MissionOutcome outcome) {
        if (outcome == null) return List.of();
        MarineRosterScript script = MarineRosterScript.getInstance();
        MarineRoster roster = script != null ? script.roster() : null;
        Map<String, MarineSoldierStatus> dispositions = new HashMap<>();
        for (String id : outcome.survivingSoldierIds) dispositions.put(id, MarineSoldierStatus.ACTIVE);
        for (String id : outcome.fallenSoldierIds) {
            MarineSoldier soldier = roster != null ? roster.soldierById(id) : null;
            dispositions.put(id, soldier != null ? soldier.status() : MarineSoldierStatus.KIA);
        }
        List<MarineSquad> deployed = deployedSquads(outcome, roster, dispositions);
        boolean namedStationing = outcome.missionSource == MissionSource.STATIONING
                && !outcome.deployedFireteamIds.isEmpty();
        if (namedStationing && roster != null) {
            for (MarineSquad squad : deployed) {
                for (MarineSoldier soldier : roster.squadMembers(squad)) {
                    dispositions.putIfAbsent(soldier.id(), soldier.status());
                }
            }
        }
        if (roster == null || dispositions.isEmpty()) return List.of();
        List<PersonnelRow> rows = new ArrayList<>();
        for (MarineSquad squad : deployed) rows.add(personnelRow(roster, squad, dispositions, rows.size()));
        return List.copyOf(rows);
    }

    private static List<MarineSquad> deployedSquads(MissionOutcome outcome,
                                                     MarineRoster roster,
                                                     Map<String, MarineSoldierStatus> dispositions) {
        List<MarineSquad> deployed = new ArrayList<>();
        if (roster == null) return deployed;
        if (!outcome.deployedFireteamIds.isEmpty()) {
            for (String squadId : outcome.deployedFireteamIds) {
                MarineSquad squad = roster.squadById(squadId);
                if (squad != null && !squad.reserve()) deployed.add(squad);
            }
        } else {
            for (MarineSquad squad : roster.squads()) {
                for (String id : squad.memberIds()) {
                    if (dispositions.containsKey(id)) { deployed.add(squad); break; }
                }
            }
        }
        return deployed;
    }

    private static PersonnelRow personnelRow(MarineRoster roster, MarineSquad squad,
                                               Map<String, MarineSoldierStatus> dispositions,
                                               int index) {
        int rtd = 0, wia = 0, mia = 0, kia = 0;
        StringBuilder members = new StringBuilder();
        for (MarineSoldier soldier : roster.squadMembers(squad)) {
            MarineSoldierStatus status = dispositions.get(soldier.id());
            if (status == null) continue;
            switch (status) {
                case ACTIVE -> rtd++;
                case WIA -> wia++;
                case MIA -> mia++;
                case KIA -> kia++;
            }
            if (members.length() > 0) members.append(" · ");
            members.append(shortName(soldier.name())).append(' ')
                    .append(status == MarineSoldierStatus.ACTIVE ? 'R' : status.name().charAt(0));
        }
        String tone = kia > 0 || mia > 0 ? "tone-danger" : wia > 0 ? "tone-accent" : "tone-good";
        return new PersonnelRow("result-squad-" + index, squad.name(),
                rtd + "R " + wia + "W " + mia + "M " + kia + "K",
                "label heading results-squad-summary " + tone, members.toString());
    }

    static String personnelHeader(MissionOutcome outcome) {
        if (outcome != null && outcome.missionSource == MissionSource.STATIONING) {
            return outcome.deployedFireteamIds.isEmpty()
                    ? "STATIONING PERSONNEL — AGGREGATE REPORT"
                    : "STATIONED DETACHMENT — RTD / WIA / MIA / KIA";
        }
        return "PERSONNEL — RTD / WIA / MIA / KIA";
    }

    static String noPersonnelMessage(MissionOutcome outcome) {
        return outcome != null && outcome.missionSource == MissionSource.STATIONING
                && outcome.deployedFireteamIds.isEmpty()
                ? "Legacy anonymous detachment — aggregate casualties only."
                : "No persistent personnel assigned.";
    }

    static String formatEvacuation(MissionOutcome outcome) {
        if (outcome == null || outcome.evacuationRepresentatives <= 0
                || outcome.representativesEvacuated < 0) return "—";
        if (outcome.civiliansAtRisk > outcome.evacuationRepresentatives) {
            return MessageFormat.format(Strings.get("resultsEvacuationScaledFmt"),
                    outcome.representativesEvacuated, outcome.evacuationRepresentatives,
                    NumberFormat.getIntegerInstance().format(outcome.civiliansRescued),
                    NumberFormat.getIntegerInstance().format(outcome.civiliansAtRisk));
        }
        return MessageFormat.format(Strings.get("resultsEvacuationFmt"),
                outcome.representativesEvacuated, outcome.evacuationRepresentatives);
    }

    static String formatBoatsLost(MissionOutcome outcome) {
        if (outcome == null || outcome.boatsLost.isEmpty()) return "—";
        return MessageFormat.format(Strings.get("resultsBoatsLostFmt"),
                outcome.boatsLost.size(), boatLossNames(outcome));
    }

    /**
     * The lost boats by name, in the order the battle lost them. Split out
     * because the row's phrasing is a translated format and this is not: a
     * debrief that lists two boats has to name both of them whatever language
     * the sentence around them is in.
     */
    static String boatLossNames(MissionOutcome outcome) {
        StringBuilder names = new StringBuilder();
        for (MissionOutcome.BoatLoss loss : outcome.boatsLost) {
            if (names.length() > 0) names.append(", ");
            names.append(loss.displayName());
        }
        return names.toString();
    }

    static String formatColonyArchive(MissionOutcome outcome) {
        if (outcome == null) return "—";
        return switch (outcome.colonyArchiveOutcome) {
            case RECOVERED -> Strings.get("resultsColonyArchiveRecovered");
            case LOST -> Strings.get("resultsColonyArchiveLost");
            case NONE -> "—";
        };
    }

    private static String shortName(String name) {
        if (name == null || name.isEmpty()) return "???";
        int split = name.lastIndexOf(' ');
        String value = split >= 0 ? name.substring(split + 1) : name;
        return value.substring(0, Math.min(7, value.length()));
    }

    private static String trimLabel(String label) {
        return label != null && label.endsWith(":") ? label.substring(0, label.length() - 1) : label;
    }

    private static String formatCaptainStatus(MissionOutcome outcome) {
        Status status = outcome.newCaptainStatus != null ? outcome.newCaptainStatus : Status.ACTIVE;
        return switch (status) {
            case INJURED -> {
                float currentDay = Global.getSector() != null ? CampaignClock.dayFloat() : 0f;
                int days = Math.max(1, (int) Math.ceil(outcome.injuredUntilDay - currentDay));
                yield outcome.captainName + " — " + MessageFormat.format(
                        Strings.get("resultsStatusInjuredFmt"), days);
            }
            case KIA -> outcome.captainName + " — " + Strings.get("resultsStatusKia");
            case ACTIVE, GARRISONED -> outcome.captainName + " — "
                    + Strings.get("resultsStatusActive");
        };
    }

    private static String statusTone(Status status) {
        if (status == Status.INJURED) return "tone-accent";
        if (status == Status.KIA) return "tone-danger";
        return "tone-good";
    }

    private static ResultRow row(String id, String label, String value, String tone) {
        return new ResultRow(id, label, value, "label result-value " + tone);
    }

    private void returnToMissions() {
        context.forfeitLoot();
        context.clearResolvedMission();
        context.goTo(ScreenId.MISSION_SELECT);
    }

    @Override protected void onCancel() {
        LootManifest manifest = context != null ? context.getLootManifest() : LootManifest.EMPTY;
        if (manifest == null || manifest.isEmpty()) returnToMissions();
    }

    @Override
    protected List<String> requiredElementIds() {
        return List.of("mission-results-root", "mission-results-header",
                "mission-results-body", "mission-results-summary",
                "mission-results-outcome", "mission-results-rows",
                "mission-results-personnel", "mission-results-squads",
                "mission-results-actions", "mission-results-primary",
                "mission-results-secondary");
    }

    record ResultRow(String id, String label, String value, String tone)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "labelId" -> id + "-label";
                case "valueId" -> id + "-value"; case "label" -> label; case "value" -> value;
                case "tone" -> tone; default -> null;
            };
        }
    }

    record PersonnelRow(String id, String name, String summary, String tone,
                        String members) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "topId" -> id + "-top";
                case "nameId" -> id + "-name"; case "summaryId" -> id + "-summary";
                case "membersId" -> id + "-members"; case "name" -> name;
                case "summary" -> summary; case "tone" -> tone;
                case "members" -> members; default -> null;
            };
        }
    }
}
