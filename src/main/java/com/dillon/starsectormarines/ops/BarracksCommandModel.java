package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.BarracksEquipmentIssue;
import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.OfficerSpecialty;
import com.dillon.starsectormarines.marine.OfficerInduction;
import com.dillon.starsectormarines.marine.OfficerInduction.Step;
import com.dillon.starsectormarines.marine.Status;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.MarinePersonnelLogistics.PersonnelDrawResult;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadArmorDoctrine;
import com.dillon.starsectormarines.marine.SquadProfile;
import com.dillon.starsectormarines.marine.SquadConsolidation;
import com.dillon.starsectormarines.marine.SquadWeaponDoctrine;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.ComputedSignal;
import com.dillon.starsectormarines.ops.battleview.SquadMuster;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.spec.SpecSheet;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** View-local selection and drafts over campaign commands, profiles and exact issue. */
public final class BarracksCommandModel {
    private static final int PAGE_SIZE = 12;
    private final MarineRoster roster;
    private final BarracksEquipmentIssue equipment;
    private final EquipmentIssueResources resources;
    private final IntSupplier cargoMarines;
    private final Function<String, PersonnelDrawResult> reinforce;
    private final Consumer<String> inspect;
    private final Runnable changed;
    private final MutableSignal<Integer> revision;
    private final ComputedSignal<List<Map<String, Object>>> cards;
    private final DoubleSupplier currentDay;
    private final Map<String, Object> props = new LinkedHashMap<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private String command = "all";
    private String profileFilter;
    private String profileId;
    private boolean attention;
    private boolean detail;
    private boolean dossier;
    private boolean editing;
    private boolean organizing;
    private boolean choosingDonors;
    private final Set<String> donors = new LinkedHashSet<>();
    private SquadConsolidation.Plan reviewedTransfers;
    private boolean guideOpen;
    private Runnable preparationComplete = () -> { };
    public void onPreparationComplete(Runnable action) { preparationComplete = action; }
    private int guideStep;
    private String inspectedId;
    private Runnable raiseSquad = () -> { };
    private Runnable missions = () -> { };
    private Consumer<String> editEquipment = id -> { };
    private boolean localMissions;
    private boolean returnToBriefing;
    private String search = "";
    private String draftName = "Line infantry";
    private int weaponIndex;
    private int armorIndex;
    private int page;
    private String feedback = "Select squads, then Assign Selected on a commander card. Use Preparation for personnel and equipment orders.";

    public BarracksCommandModel(Reactor reactor, MarineRoster roster, EquipmentIssueResources resources,
                                IntSupplier cargoMarines, Function<String, PersonnelDrawResult> reinforce,
                                Consumer<String> inspect, Runnable changed, DoubleSupplier currentDay) {
        this.roster = roster;
        guideOpen = inductionRequired() || !roster.managementGuideDismissed();
        this.resources = resources;
        this.equipment = new BarracksEquipmentIssue(roster, resources);
        this.cargoMarines = cargoMarines;
        this.reinforce = reinforce;
        this.inspect = inspect;
        this.changed = changed;
        this.currentDay = currentDay;
        revision = reactor.signal(0);
        props.put("raiseSquad", (Runnable) () -> raiseSquad.run());
        props.put("missions", (Runnable) () -> missions.run());
        props.put("editEquipment", (Runnable) () -> { if (inspectedId != null) editEquipment.accept(inspectedId); });
        bind(reactor, "missionLabel", () -> returnToBriefing ? "BRIEFING" : "MISSIONS");
        bind(reactor, "missionClasses", () -> localMissions ? "tool-button console-action" : "hidden");
        bind(reactor, "commands", this::commands);
        bind(reactor, "formationClasses", () -> dossier ? "hidden" : "formation-board panel console-section");
        bind(reactor, "ordersPanelClasses", () -> dossier ? "hidden" : "orders panel console-section");
        bind(reactor, "dossierClasses", () -> dossier ? "officer-dossier panel console-section" : "hidden");
        bind(reactor, "dossierButtonClasses", () -> selectedOfficer() == null ? "hidden" : "flow-button console-action");
        props.put("openDossier", (Runnable) () -> { dossier = selectedOfficer() != null; refresh(); });
        props.put("closeDossier", (Runnable) () -> { dossier = false; refresh(); });
        bind(reactor, "officerName", () -> selectedOfficer() == null ? "" : captainName(selectedOfficer()));
        bind(reactor, "officerPortrait", () -> selectedOfficer() == null || selectedOfficer().portraitSprite() == null
                ? "graphics/ui/console/service-plate.svg" : selectedOfficer().portraitSprite());
        bind(reactor, "officerStatus", this::officerStatus);
        bind(reactor, "officerProgress", this::officerProgress);
        bind(reactor, "officerService", () -> selectedOfficer() == null ? "" : selectedOfficer().career().deployments()
                + " operations / " + selectedOfficer().career().victories() + " victories / "
                + selectedOfficer().career().earnedXp() + " mission XP recorded");
        bind(reactor, "officerSquads", () -> selectedOfficer() == null ? "" : "Home command: " + lineSquads().stream()
                .filter(squad -> command.equals(squad.homeCaptainId())).map(MarineSquad::name)
                .reduce((a, c) -> a + ", " + c).orElse("No squads assigned"));
        bind(reactor, "officerTraining", () -> selectedOfficer() == null ? "" : selectedOfficer().career().availablePoints(selectedOfficer().rank())
                + " TRAINING POINTS / one on appointment, one per promotion / choices are permanent");
        bind(reactor, "officerSpecialties", this::officerSpecialties);
        bind(reactor, "officerTraits", this::officerTraits);
        bind(reactor, "officerBiography", this::officerBiography);
        bind(reactor, "officerHistory", this::officerHistory);
        bind(reactor, "officerHistoryStyle", () -> "height: " + (officerHistory().size() * 52) + "px;");
        cards = reactor.computed(() -> { revision.get(); return squads(); });
        props.put("squads", cards);
        bind(reactor, "profiles", this::profiles);
        bind(reactor, "recruitIcon", () -> resources.commodityIcon(Commodities.MARINES));
        bind(reactor, "recruitStock", () -> Integer.toString(cargoMarines.getAsInt()));
        bind(reactor, "issueCargoStock", this::issueCargoStock);
        bind(reactor, "recruitDraw", () -> Integer.toString(recruitsRequired()));
        bind(reactor, "profilesEmpty", () -> roster.armory().squadProfiles().isEmpty()
                ? "No saved profiles. Choose NEW to pair a loadout and tactic sheet." : "");
        bind(reactor, "overviewClasses", () -> detail ? "command-board hidden" : "command-board");
        bind(reactor, "detailClasses", () -> detail ? "barracks-body" : "barracks-body hidden");
        bind(reactor, "ordersClasses", () -> editing || !organizing ? "order-body hidden" : "order-body");
        bind(reactor, "preparationClasses", () -> editing || organizing ? "order-body hidden" : "order-body");
        bind(reactor, "selectionMode", () -> choosingDonors ? "MARK DONOR SQUADS / receiving squads remain marked" : "SELECT SQUADS / assign on a commander card, or prepare on the right");
        bind(reactor, "donorLabel", () -> choosingDonors ? "CHOOSE RECIPIENTS" : "CHOOSE DONORS");
        bind(reactor, "transferSummary", () -> selected.size() + " receiving / " + donors.size() + " donor squads. Only marked donors may give personnel.");
        bind(reactor, "transferDisabled", () -> selected.isEmpty() || donors.isEmpty());
        bind(reactor, "transferRows", this::transferRows);
        bind(reactor, "transferRowsClasses", () -> reviewedTransfers == null || reviewedTransfers.strengths().isEmpty() ? "hidden" : "transfer-rows");
        bind(reactor, "transferReason", () -> reviewedTransfers == null ? "Review transfers before applying. Wounded stay in their billets."
                : reviewedTransfers.canApply() ? reviewedTransfers.moves().size() + " named marines will move with their kit. No cargo spent."
                : reviewedTransfers.reason());
        bind(reactor, "applyTransfersDisabled", () -> reviewedTransfers == null || !reviewedTransfers.canApply());
        props.put("preparation", (Runnable) () -> { organizing = false; editing = false; refresh(); });
        props.put("organization", (Runnable) () -> { organizing = true; editing = false; refresh(); });
        props.put("chooseDonors", (Runnable) () -> { choosingDonors = !choosingDonors; reviewedTransfers = null; refresh(); });
        props.put("reviewTransfers", (Runnable) this::reviewTransfers);
        props.put("applyTransfers", (Runnable) this::applyTransfers);
        props.put("cancelTransfers", (Runnable) () -> { donors.clear(); choosingDonors = false; reviewedTransfers = null; refresh(); });
        bind(reactor, "guideClasses", () -> guideVisible() ? "guide-callout console-readout" : "guide-callout hidden");
        bind(reactor, "guideTitle", () -> CompanyManagementGuide.speaker().toUpperCase(Locale.ROOT) + " / " + (inductionRequired() ? "FIRST COMMAND / " : (guideStep + 1) + " / " + CompanyManagementGuide.lines().size() + " / ") + guideLine().title());
        bind(reactor, "guidePortrait", CompanyManagementGuide::portrait);
        bind(reactor, "guideText", () -> guideLine().text());
        bind(reactor, "guideStatus", this::guideHint);
        bind(reactor, "guideDismissDisabled", () -> inductionRequired() || !guideVisible());
        bind(reactor, "guideDismissClasses", () -> inductionRequired() ? "hidden" : "guide-dismiss console-action");
        bind(reactor, "guideHelpDisabled", () -> !inductionRequired() && CompanyManagementGuide.lines().isEmpty());
        bind(reactor, "guidePreviousDisabled", () -> !guideVisible() || inductionRequired() || guideStep == 0);
        bind(reactor, "guideNextDisabled", () -> !guideVisible() || (inductionRequired() ? inductionStep() != Step.READY : guideStep + 1 >= CompanyManagementGuide.lines().size()));
        bind(reactor, "guideHelpLabel", () -> inductionRequired() ? "CASTLE'S LESSON" : "COMPANY GUIDE");
        bind(reactor, "guidePreviousClasses", () -> inductionRequired() ? "hidden" : "tool-button console-action");
        bind(reactor, "guideProgressClasses", () -> inductionRequired() ? "guide-progress" : "hidden");
        bind(reactor, "guideProgressImage", this::guideProgressImage);
        bind(reactor, "guideNextLabel", () -> inductionRequired() ? "READY" : "NEXT");
        bind(reactor, "guideDismissLabel", () -> "DISMISS");
        bind(reactor, "guideCloseHint", () -> inductionRequired() ? "USE THE HIGHLIGHTED CONTROL"
                : "USE THE HIGHLIGHTED CONTROL / ESC TO DISMISS");
        props.put("guideHelp", (Runnable) this::resumeGuide);
        props.put("guidePrevious", (Runnable) () -> { if (inductionRequired()) return; guideStep = Math.max(0, guideStep - 1); refresh(); });
        props.put("guideNext", (Runnable) () -> {
            if (inductionRequired()) {
                if (roster.officerInduction().finish(roster)) {
                    guideOpen = false; changed.run();
                    if (roster.officerInduction().companyPreparation()) preparationComplete.run();
                }
                refresh(); return;
            }
            guideStep = Math.min(Math.max(0, CompanyManagementGuide.lines().size() - 1), guideStep + 1); refresh(); });
        props.put("guideDismiss", (Runnable) this::dismissGuide);
        bind(reactor, "editorClasses", () -> editing ? "order-body" : "order-body hidden");
        bind(reactor, "title", this::title);
        bind(reactor, "summary", () -> lineSquads().size() + " squads / " + roster.lineReadySoldiers().size()
                + " ready personnel / " + roster.readyReserveCount() + " reserves");
        bind(reactor, "scopeSummary", () -> filtered().size() + " squads in view / " + selected.size() + " selected");
        bind(reactor, "empty", () -> filtered().isEmpty() ? "No squads match this view. Raise new formations in Armory." : "");
        bind(reactor, "pageLabel", () -> "PAGE " + (pageNumber() + 1) + " / " + pages());
        bind(reactor, "previousDisabled", () -> pageNumber() == 0);
        bind(reactor, "nextDisabled", () -> pageNumber() + 1 >= pages());
        bind(reactor, "search", () -> search);
        bind(reactor, "attentionLabel", () -> attention ? "ATTENTION ONLY" : "ALL STATES");
        bind(reactor, "filterLabel", () -> profileFilter == null ? "ALL PROFILES" : roster.armory().squadProfile(profileFilter).name());
        bind(reactor, "selection", () -> selected.size() + " RECEIVING / " + donors.size() + " DONORS");
        bind(reactor, "selectionDisabled", selected::isEmpty);
        bind(reactor, "profileDisabled", () -> selected.isEmpty() || profileId == null);
        bind(reactor, "recruitment", this::recruitment);
        bind(reactor, "reinforceDisabled", () -> Math.min(vacancies(), roster.readyReserveCount() + cargoMarines.getAsInt()) == 0);
        bind(reactor, "issueSummary", () -> {
            var plan = equipment.preview(selected);
            return plan.eligible() + " eligible / " + plan.skipped() + " skipped";
        });
        bind(reactor, "issueCost", () -> "Machine Shop fitting is free for learned equipment.");
        bind(reactor, "issueDisabled", () -> {
            var plan = equipment.preview(selected);
            return plan.eligible() == 0 || !plan.affordable();
        });
        bind(reactor, "issueReason", () -> {
            var plan = equipment.preview(selected);
            if (!selected.isEmpty() && !plan.affordable()) return "Select current squads to fit.";
            return plan.entries().stream().filter(e -> !e.eligible())
                    .map(e -> roster.squadById(e.squadId()).name() + ": " + e.reason())
                    .reduce((a, b) -> a + "\n" + b).orElse("Ready squads receive their assigned equipment. Personnel orders never change kit.");
        });
        bind(reactor, "feedback", () -> feedback);
        bind(reactor, "draftName", () -> draftName);
        bind(reactor, "draftWeapon", () -> weapons().isEmpty() ? "No known loadouts" : draftWeapon().displayName());
        bind(reactor, "draftArmor", () -> draftArmor().displayName());
        bind(reactor, "saveDisabled", () -> draftName.isBlank() || draftName.trim().length() > 48 || weapons().isEmpty());
        props.put("editSearch", (Consumer<String>) value -> { search = value; resetSelection(); });
        props.put("editName", (Consumer<String>) value -> { draftName = value; refresh(); });
        props.put("attention", (Runnable) () -> { attention = !attention; resetSelection(); });
        props.put("filter", (Runnable) this::cycleFilter);
        props.put("selectAll", (Runnable) () -> {
            filtered().stream().filter(s -> !s.stationed()).forEach(s -> {
                if (choosingDonors) { if (!selected.contains(s.id())) donors.add(s.id()); }
                else { if (!donors.contains(s.id())) selected.add(s.id()); }
            });
            if (guideVisible() && guideStep <= 1 && !selected.isEmpty()) guideStep = 2;
            reviewedTransfers = null; refresh();
        });
        props.put("clear", (Runnable) this::resetSelection);
        props.put("previous", (Runnable) () -> { page = Math.max(0, pageNumber() - 1); refresh(); });
        props.put("next", (Runnable) () -> { page = Math.min(pages() - 1, pageNumber() + 1); refresh(); });
        props.put("back", (Runnable) this::showOverview);
        props.put("assignProfile", (Runnable) this::assignProfile);
        props.put("reinforce", (Runnable) this::reinforceSelected);
        props.put("issue", (Runnable) this::issueSelected);
        props.put("newProfile", (Runnable) () -> { editing = true; refresh(); });
        props.put("cancelProfile", (Runnable) () -> { editing = false; refresh(); });
        props.put("weaponPrevious", (Runnable) () -> { weaponIndex--; refresh(); });
        props.put("weaponNext", (Runnable) () -> { weaponIndex++; refresh(); });
        props.put("armorPrevious", (Runnable) () -> { armorIndex--; refresh(); });
        props.put("armorNext", (Runnable) () -> { armorIndex++; refresh(); });
        props.put("saveProfile", (Runnable) this::saveProfile);
    }

    private void bind(Reactor reactor, String key, Supplier<?> supplier) {
        props.put(key, reactor.computed(() -> { revision.get(); return supplier.get(); }));
    }
    public Map<String, Object> props() { return props; }
    public List<Map<String, Object>> cards() { return cards.get(); }
    public SquadMuster muster(String id) { return SquadMuster.from(roster, id, (float) currentDay.getAsDouble()); }
    public Set<String> selected() { return Set.copyOf(selected); }
    public boolean inspecting() { revision.get(); return detail; }
    public void refresh() { revision.update(n -> n + 1); }
    public void leave() { reviewedTransfers = null; editing = false; refresh(); }
    public void showOverview() { detail = false; dossier = false; refresh(); }
    public void inspect(String id) { inspectedId = id; inspect.accept(id); detail = true; refresh(); }
    public void selectCommand(String id) {
        command = id; dossier = selectedOfficer() != null;
        if (inductionRequired() && selectedOfficer() == inductionOfficer())
            roster.officerInduction().introduce(selectedOfficer());
        resetSelection();
    }
    public void selectProfile(String id) { profileId = id; refresh(); }
    public void toggle(String id) {
        MarineSquad squad = roster.squadById(id);
        if (squad == null || squad.reserve() || squad.stationed()) return;
        if (choosingDonors) {
            if (selected.contains(id)) return;
            if (!donors.remove(id)) donors.add(id);
        } else {
            if (donors.contains(id)) return;
            if (!selected.remove(id)) selected.add(id);
        }
        reviewedTransfers = null;
        if (guideVisible() && guideStep <= 1 && !selected.isEmpty()) guideStep = 2;
        refresh();
    }
    private void resetSelection() { selected.clear(); donors.clear(); reviewedTransfers = null; choosingDonors = false; page = 0; refresh(); }
    private List<MarineSquad> lineSquads() { return roster.squads().stream().filter(s -> !s.reserve()).toList(); }
    private String captainName(MarineCaptain captain) {
        return captain == null ? "Independent / Acting NCO" : captain.rank().displayName() + " " + captain.name();
    }
    private String group(MarineSquad squad) {
        return roster.byId(squad.homeCaptainId()) == null ? "independent" : squad.homeCaptainId();
    }
    private String title() { return "all".equals(command) ? "COMPANY FORMATIONS" : captainName(roster.byId(command)); }
    private String profileName(MarineSquad squad) {
        SquadProfile profile = roster.armory().squadProfile(squad.squadProfileId());
        if (profile != null) return profile.name();
        var weapon = roster.armory().weaponDoctrineById(squad.weaponDoctrineId());
        return weapon != null ? weapon.displayName() : "Individual issue";
    }
    private boolean needsAttention(MarineSquad squad) {
        if (squad.stationed()) return false;
        if (roster.readyCount(squad) < MarineSquad.CAPACITY) return true;
        var plan = equipment.preview(Set.of(squad.id()));
        return plan.eligible() == 0 || !plan.cost().isZero();
    }
    private List<MarineSquad> filtered() {
        return lineSquads().stream()
                .filter(s -> "all".equals(command) || group(s).equals(command))
                .filter(s -> profileFilter == null || Objects.equals(profileFilter, s.squadProfileId()))
                .filter(s -> !attention || needsAttention(s))
                .filter(s -> (s.name() + " " + profileName(s)).toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
                .toList();
    }
    private int pages() { return Math.max(1, (filtered().size() + PAGE_SIZE - 1) / PAGE_SIZE); }
    private int pageNumber() { return Math.min(page, pages() - 1); }
    private MarineCaptain selectedOfficer() { return roster.byId(command); }
    private String officerStatus() {
        MarineCaptain officer = selectedOfficer();
        if (officer == null) return "";
        return officer.status() + " / " + officer.rank().squadCommandCap() + " coordinated squads"
                + (officer.status() == Status.INJURED ? " / returns day " + (int) Math.ceil(officer.injuredUntilDay()) : "")
                + (officer.status() != Status.ACTIVE ? " / unavailable for new field command" : "");
    }
    private String officerProgress() {
        MarineCaptain officer = selectedOfficer();
        if (officer == null) return "";
        return officer.rank().isTerminal() ? "Colonel / highest command rank"
                : officer.xp() + " / " + officer.rank().xpToNext() + " XP to " + officer.rank().promote().displayName();
    }
    private List<Map<String, Object>> officerSpecialties() {
        MarineCaptain officer = selectedOfficer();
        if (officer == null) return List.of();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (OfficerSpecialty specialty : OfficerSpecialty.values()) {
            int level = officer.career().level(specialty);
            String id = "officer-specialty-" + specialty.name();
            rows.add(row("id", id, "titleId", id + "-title", "effectId", id + "-effect",
                    "headerId", id + "-header", "iconId", id + "-icon", "footerId", id + "-footer",
                    "progressId", id + "-progress", "levelId", id + "-level", "costId", id + "-cost",
                    "icon", "graphics/ui/officers/" + specialty.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".svg",
                    "progress", "graphics/ui/officers/specialty-rank-" + level + ".svg",
                    "level", level + " / 2 RANKS", "cost", level == 2 ? "" : "1 POINT",
                    "nextId", id + "-next", "trainId", id + "-train",
                    "title", specialty.label(),
                    "effect", level == 0 ? "Not trained" : specialty.effect(level),
                    "next", level == 2 ? "Mastered" : "Next: " + specialty.effect(level + 1),
                    "button", level == 2 ? "MASTERED" : "TRAIN",
                    "disabled", officer.status() == Status.KIA || level == 2 || officer.career().availablePoints(officer.rank()) == 0,
                    "train", (Runnable) () -> {
                        if (officer.train(specialty)) {
                            officer.commendations().add("Day " + (int) currentDay.getAsDouble() + ": Trained "
                                    + specialty.label() + " " + officer.career().level(specialty) + ".");
                            changed.run(); refresh();
                        }
                    }));
        }
        return rows;
    }
    private String officerTraits() {
        MarineCaptain officer = selectedOfficer();
        if (officer == null || officer.traits().isEmpty()) return "Background: no recorded traits.";
        return "Background: " + officer.traits().stream().map(trait -> switch (trait) {
            case NATURAL_LEADER -> "Natural Leader (+50% officer mission XP)";
            case SALVAGE_EXPERT -> "Salvage Expert (operation commander: +25 salvage recovery points, 10% high-value chance)";
            case FIELD_MEDIC -> "Field Medic (named-personnel effect pending)";
            case IDEALIST -> "Idealist (outlook)";
            case CYNICAL -> "Cynical (outlook)";
            default -> trait.name().replace('_', ' ') + " (background; no passive effect yet)";
        }).reduce((a, c) -> a + "; " + c).orElse("");
    }
    private List<Map<String, Object>> officerHistory() {
        MarineCaptain officer = selectedOfficer();
        if (officer == null) return List.of();
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> notes = officer.commendations();
        for (int i = notes.size() - 1; i >= Math.max(0, notes.size() - 20); i--)
            rows.add(row("id", "officer-history-" + i, "text", notes.get(i)));
        if (rows.isEmpty()) rows.add(row("id", "officer-history-empty", "text", "Service record begins with the next operation. Existing commendations are retained."));
        return rows;
    }

    private List<Map<String, Object>> commands() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(commandRow("all", null, lineSquads()));
        for (MarineCaptain captain : roster.all()) {
            var squads = lineSquads().stream().filter(s -> captain.id().equals(group(s))).toList();
            rows.add(commandRow(captain.id(), captain, squads));
        }
        rows.add(commandRow("independent", null, lineSquads().stream().filter(s -> group(s).equals("independent")).toList()));
        return rows;
    }
    private Map<String, Object> commandRow(String id, MarineCaptain captain, List<MarineSquad> squads) {
        long ready = squads.stream().filter(s -> !s.stationed() && roster.readyCount(s) == MarineSquad.CAPACITY).count();
        var assignment = roster.previewCaptainAssignment(captain == null ? null : captain.id(), selected);
        return row("id", "command:" + id,
                "commanderportraitId", "command:" + id + ":portrait", "commandercopyId", "command:" + id + ":copy",
                "commandnameId", "command:" + id + ":name", "commandsummaryId", "command:" + id + ":summary",
                "commandcapacityId", "command:" + id + ":capacity",
                "cardStyle", "height: " + (id.equals("all") ? 126 : selected.isEmpty() ? 158 : 200) + "px;",
                "cardId", "command:" + id + ":card", "assignId", "command:" + id + ":assign",
                "assignClasses", id.equals("all") ? "hidden" : "command-assign console-action"
                        + (assignment.valid() ? " command-assign-ready" : ""),
                "assignLabel", selected.isEmpty() ? "SELECT SQUADS FIRST" : id.equals("independent") ? "MAKE INDEPENDENT (" + selected.size() + ")" : "ASSIGN SELECTED (" + selected.size() + ")",
                "assignDisabled", id.equals("all") || !assignment.valid(),
                "assign", (Runnable) () -> assignCommand(captain == null ? null : captain.id()),
                "reasonId", "command:" + id + ":reason",
                "reasonClasses", id.equals("all") || selected.isEmpty() ? "hidden" : "label muted wrap command-reason",
                "reason", assignment.reason(),
                "name", id.equals("all") ? "All formations" : captainName(captain),
                "portrait", captain != null && captain.portraitSprite() != null ? captain.portraitSprite() : "graphics/ui/console/service-plate.svg",
                "summary", ready + " ready / " + squads.size() + " assigned",
                "capacity", captain == null ? id.equals("all") ? "Company roll" : "Independent mission execution"
                        : captain.status() + " / " + squads.size() + " of " + captain.rank().squadCommandCap() + " command slots",
                "classes", command.equals(id) ? "command-card selected" : "command-card",
                "select", (Runnable) () -> selectCommand(id));
    }
    private List<Map<String, Object>> squads() {
        var filtered = filtered();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MarineSquad squad : filtered.subList(Math.min(filtered.size(), pageNumber() * PAGE_SIZE),
                Math.min(filtered.size(), (pageNumber() + 1) * PAGE_SIZE))) {
            int ready = roster.readyCount(squad);
            var plan = equipment.preview(Set.of(squad.id()));
            SquadMuster muster = muster(squad.id());
            MarineArmorCatalogDef armor = representativeArmor(squad);
            String personnel = squad.stationed() ? "ON STATION" : ready + " ready / " + muster.woundedCount() + " WIA / " + muster.vacantCount() + " vacant";
            String kit = squad.squadProfileId() == null && squad.weaponDoctrineId() == null ? "NO PROFILE"
                    : plan.eligible() == 0 ? "ISSUE HELD" : plan.cost().isZero() ? "EQUIPPED" : "REFIT";
            MarineCaptain home = roster.byId(squad.homeCaptainId());
            rows.add(row("id", "formation:" + squad.id(), "markId", "formation:" + squad.id() + ":mark",
                    "squadId", squad.id(), "musterId", "formation:" + squad.id() + ":muster",
                    "headerId", "formation:" + squad.id() + ":header", "checkId", "formation:" + squad.id() + ":check",
                    "strengthId", "formation:" + squad.id() + ":strength", "profileRowId", "formation:" + squad.id() + ":profile-row",
                    "armorIconId", "formation:" + squad.id() + ":armor-icon", "footerId", "formation:" + squad.id() + ":footer",
                    "kitId", "formation:" + squad.id() + ":kit", "armorIcon", armor == null ? "" : armor.iconPath(),
                    "strength", ready + "/12", "personnel", personnel, "kit", selected.contains(squad.id()) ? "SELECTED" : donors.contains(squad.id()) ? "DONOR" : kit,
                    "personnelClasses", "label card-personnel " + (squad.stationed() ? "muted" : ready == 12 ? "good" : "warm"),
                    "kitClasses", "label card-kit " + (kit.equals("EQUIPPED") ? "good" : "muted"),
                    "formationidentityId", "formation:" + squad.id() + ":identity",
                    "formationnameId", "formation:" + squad.id() + ":name",
                    "formationstatusId", "formation:" + squad.id() + ":status",
                    "formationprofileId", "formation:" + squad.id() + ":profile",
                    "inspectId", "formation:" + squad.id() + ":inspect", "name", squad.name(),
                    "profile", profileName(squad),
                    "commanderId", "formation:" + squad.id() + ":commander",
                    "commanderImageId", "formation:" + squad.id() + ":commander-image",
                    "commanderNameId", "formation:" + squad.id() + ":commander-name",
                    "commanderName", home == null ? "Independent / Acting NCO" : captainName(home),
                    "commanderImage", home == null || home.portraitSprite() == null ? "graphics/ui/console/service-plate.svg" : home.portraitSprite(),
                    "classes", "formation" + (selected.contains(squad.id()) ? " selected" : donors.contains(squad.id()) ? " donor" : "") + (squad.stationed() ? " stationed" : ""),
                    "mark", selected.contains(squad.id()) ? "+" : donors.contains(squad.id()) ? "D" : "", "disabled", squad.stationed(),
                    "toggle", (Runnable) () -> toggle(squad.id()), "inspect", (Runnable) () -> inspect(squad.id())));
        }
        return rows;
    }
    /** Representative current issue, never an invented class or the profile's future equipment. */
    private MarineArmorCatalogDef representativeArmor(MarineSquad squad) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String id : roster.manningMemberIds(squad)) {
            var soldier = roster.soldierById(id);
            if (soldier != null) counts.merge(soldier.armorId(), 1, Integer::sum);
        }
        String common = null;
        int largest = 0;
        for (var entry : counts.entrySet()) if (entry.getValue() > largest) {
            common = entry.getKey(); largest = entry.getValue();
        }
        return common == null ? null : MarineArmorCatalogRegistry.require(common);
    }
    public SpecSheet squadSheet(String id) {
        MarineSquad squad = roster.squadById(id);
        if (squad == null || squad.reserve()) return null;
        SquadMuster muster = muster(id);
        MarineArmorCatalogDef armor = representativeArmor(squad);
        var plan = equipment.preview(Set.of(id));
        String issue = plan.eligible() == 0 ? plan.entries().get(0).reason()
                : plan.cost().isZero() ? "Equipment conforms" : "Refit pending: " + plan.cost().display();
        List<String> notes = new ArrayList<>();
        SquadProfile profile = roster.armory().squadProfile(squad.squadProfileId());
        if (profile != null) notes.add("Profile: " + roster.armory().weaponDoctrineById(profile.weaponDoctrineId()).displayName()
                + " / " + roster.armory().armorDoctrineById(profile.armorDoctrineId()).displayName());
        notes.add(armor == null ? "No issued armor: all billets vacant." : "Armor image: " + armor.displayName() + ", the most common current issue.");
        notes.add(issue + ". Assigning a profile never changes issued equipment by itself.");
        if (muster.woundedCount() > 0) notes.add(String.format(Locale.ROOT,
                "Wounded personnel keep their billets. Next return in %.1f days.", muster.earliestRecoveryDays()));
        notes.add(squad.stationed() ? "On station: inspect is available; bulk orders are locked."
                : "Click the card to select it for bulk orders. Inspect opens quarters and the individual muster.");
        return new SpecSheet(squad.name(), profileName(squad), armor == null ? "" : armor.iconPath(), "item",
                List.of(SpecSheet.Stat.of("Ready", muster.readyCount() + " / 12"),
                        SpecSheet.Stat.of("Wounded", Integer.toString(muster.woundedCount())),
                        SpecSheet.Stat.of("Vacant", Integer.toString(muster.vacantCount())),
                        SpecSheet.Stat.of("Home command", captainName(roster.byId(squad.homeCaptainId())))), notes);
    }
    private List<Map<String, Object>> profiles() {
        return roster.armory().squadProfiles().stream().map(profile -> row(
                "id", profile.id(), "profilenameId", profile.id() + ":name",
                "profiledetailId", profile.id() + ":detail", "name", profile.name(),
                "detail", roster.armory().weaponDoctrineById(profile.weaponDoctrineId()).displayName()
                        + "\n" + roster.armory().armorDoctrineById(profile.armorDoctrineId()).displayName(),
                "classes", profile.id().equals(profileId) ? "profile-card selected" : "profile-card",
                "select", (Runnable) () -> selectProfile(profile.id()))).toList();
    }
    private void cycleFilter() {
        var profiles = roster.armory().squadProfiles();
        int index = -1;
        for (int i = 0; i < profiles.size(); i++) if (profiles.get(i).id().equals(profileFilter)) index = i;
        profileFilter = index + 1 < profiles.size() ? profiles.get(index + 1).id() : null;
        resetSelection();
    }
    public void assignCommand(String captainId) {
        var result = roster.assignCaptainToSquads(captainId, selected);
        feedback = result.valid() ? result.changedSquadCount() + " squads assigned to " + captainName(roster.byId(captainId)) + "." : result.reason();
        changed.run();
        refresh();
    }
    public void assignProfile() {
        boolean applied = roster.assignSquadProfile(selected, profileId);
        feedback = applied ? selected.size() + " squads assigned. Issue equipment separately when ready; no cargo spent."
                : "Select a profile and available squads. Stationed squads cannot change profile.";
        changed.run();
    }
    private int vacancies() {
        return lineSquads().stream().filter(s -> selected.contains(s.id()) && !s.stationed()).mapToInt(roster::vacancies).sum();
    }
    private String recruitment() {
        int vacancies = vacancies();
        int reserves = Math.min(vacancies, roster.readyReserveCount());
        int wounded = lineSquads().stream().filter(s -> selected.contains(s.id())).mapToInt(s -> muster(s.id()).woundedCount()).sum();
        return vacancies + " vacancies / " + reserves + " from reserves\n"
                + (vacancies - reserves - recruitsRequired()) + " remain unfilled / " + wounded + " wounded keep their billets";
    }
    private int recruitsRequired() {
        return Math.min(Math.max(0, vacancies() - roster.readyReserveCount()), cargoMarines.getAsInt());
    }
    public SpecSheet recruitSheet() {
        return new SpecSheet("Marines / available recruits", "UNENLISTED PERSONNEL IN FLEET CARGO",
                resources.commodityIcon(Commodities.MARINES), "item",
                List.of(SpecSheet.Stat.of("In cargo", Integer.toString(cargoMarines.getAsInt())),
                        SpecSheet.Stat.of("Named reserves", Integer.toString(roster.readyReserveCount())),
                        SpecSheet.Stat.of("Will enlist", Integer.toString(recruitsRequired()))),
                List.of("The Marines commodity represents personnel outside your company's named roster. They remain tradable until enlisted.",
                        "Fill vacancies transfers ready company reserves first, then enlists one cargo Marine per open billet. Wounded personnel keep their billets.",
                        "Equipment issue is a separate order."));
    }

    private List<Map<String, Object>> issueCargoStock() {
        revision.get();
        return List.of(Commodities.SUPPLIES, Commodities.HAND_WEAPONS,
                Commodities.HEAVY_MACHINERY, Commodities.FOOD).stream()
                .map(commodity -> row(
                        "id", "barracks-cargo-stock:" + commodity,
                        "iconId", "barracks-cargo-stock:" + commodity + ":icon",
                        "labelId", "barracks-cargo-stock:" + commodity + ":label",
                        "icon", resources.commodityIcon(commodity),
                        "label", resources.commodityName(commodity).toUpperCase(Locale.ROOT)
                                + "  " + availableIssueCargo(commodity)))
                .toList();
    }

    private int availableIssueCargo(String commodity) {
        var stock = resources.available();
        if (Commodities.SUPPLIES.equals(commodity)) return stock.supplies();
        if (Commodities.HAND_WEAPONS.equals(commodity)) return stock.heavyArmaments();
        if (Commodities.HEAVY_MACHINERY.equals(commodity)) return stock.heavyMachinery();
        if (Commodities.FOOD.equals(commodity)) return stock.food();
        return 0;
    }

    public void reinforceSelected() {
        int reserves = 0;
        int cargo = 0;
        for (MarineSquad squad : lineSquads()) {
            if (!selected.contains(squad.id()) || squad.stationed()) continue;
            PersonnelDrawResult result = reinforce.apply(squad.id());
            reserves += result.reservesAssigned(); cargo += result.cargoMarinesConsumed();
        }
        if (guideVisible() && guideStep == 3 && reserves + cargo > 0) guideStep = 4;
        feedback = "Filled " + (reserves + cargo) + " billets: " + reserves + " reserves, " + cargo
                + " recruits enlisted. Equipment issue remains a separate order.";
        changed.run();
    }
    public void issueSelected() {
        var result = equipment.apply(selected);
        if (guideVisible() && guideStep == 4 && result.succeeded()) guideStep = 5;
        feedback = result.succeeded() ? "Issued equipment to " + result.issued() + " squads; " + result.skipped() + " skipped."
                : "Fitting blocked. Check stationed squads and required technology.";
        changed.run();
    }
    public boolean guideVisible() {
        return guideOpen && (inductionRequired() || !dossier && !CompanyManagementGuide.lines().isEmpty());
    }
    public void dismissGuide() {
        if (inductionRequired()) return;
        guideOpen = false;
        if (!inductionRequired()) roster.dismissManagementGuide();
        refresh();
    }
    public void resumeGuide() {
        if (!inductionRequired()) { dossier = false; guideOpen = true; refresh(); return; }
        dossier = false; detail = false; editing = false; organizing = false;
        command = "all"; search = ""; profileFilter = null; attention = false;
        guideOpen = true; resetSelection();
    }
    private String guideProgressImage() {
        if (inductionRequired() && roster.officerInduction().companyPreparation()) {
            int index = switch (inductionStep()) {
                case SHIP -> 0;
                case LIFT -> 1;
                case INTRODUCTION, SPECIALTY -> 2;
                default -> 3;
            };
            return "graphics/ui/officers/preparation-step-" + index + ".svg";
        }
        return "graphics/ui/officers/induction-step-"
                + (inductionRequired() ? Math.min(4, inductionStep().ordinal()) : 0) + ".svg";
    }
    public boolean inductionRequired() { return roster.officerInductionRequired(); }
    private Step inductionStep() { return roster.officerInduction().step(roster); }
    private MarineCaptain inductionOfficer() { return roster.officerInduction().officer(roster); }
    private String inductionOfficerTarget() {
        MarineCaptain officer = inductionOfficer();
        return officer == null ? "barracks-command-list" : "command:" + officer.id();
    }
    private String officerBiography() {
        OfficerInduction induction = roster.officerInduction();
        return selectedOfficer() != null && induction != null && induction.miraId().equals(selectedOfficer().id())
                ? "Mira Hale / Founding field commander. A former convoy escort, she values clear orders and a counted muster. Castle trusts her with the company's first formation. Her specialty, promotions and service record grow through your decisions."
                : "";
    }
    private String inductionTarget() {
        if (detail) return "barracks-back-command";
        Step step = inductionStep();
        if (step == Step.SHIP) return "page-nav-hq";
        if (step == Step.LIFT) return "page-nav-boats";
        if (step == Step.INTRODUCTION) return inductionOfficerTarget();
        if (step == Step.SPECIALTY)
            return !dossier || selectedOfficer() != inductionOfficer() ? inductionOfficerTarget()
                    : "officer-specialty-TACTICAL_COVER-train";
        if (step == Step.READY) return inductionOfficerTarget();
        if (dossier) return "officer-formations";
        if (step == Step.FORMATION) return "barracks-raise-squad";
        if (!"all".equals(command)) return "command:all";
        if (selected.isEmpty()) return "barracks-select-all";
        return inductionOfficer() == null ? "barracks-command-list" : "command:" + inductionOfficer().id() + ":assign";
    }
    private List<List<String>> inductionRegions() {
        String target = inductionTarget();
        if (target.startsWith("officer-specialty-"))
            return List.of(List.of("officer-training", "officer-specialty-list"));
        if ("barracks-select-all".equals(target) || target.endsWith(":assign"))
            return List.of(List.of("barracks-selection-tools", "barracks-formation-list", "barracks-page-tools"),
                    List.of(inductionOfficerTarget() + ":card"));
        return List.of(List.of(target));
    }
    private String inductionHint() {
        if (inductionStep() == Step.READY) return roster.officerInduction().companyPreparation()
                ? "Choose Ready to finish preparation and continue to your briefing or Mara Venn's first-contract directions in HQ."
                : "Choose Ready to complete induction, then return to briefing to assign your deployment.";
        return switch (inductionTarget()) {
            case "page-nav-hq" -> "Choose a company ship in HQ before we continue.";
            case "page-nav-boats" -> "Commission our first shuttle on the Boat Deck, then return to meet Mira.";
            case "barracks-back-command" -> "Return to the command board to continue.";
            case "officer-formations" -> "Choose View Formations to return to the squad board.";
            case "barracks-raise-squad" -> "Raise Squad opens Armory. Found a squad there, then return to Barracks.";
            case "command:all" -> "Choose All Formations so the unassigned squad is visible.";
            case "barracks-select-all" -> "Mark the squad you want her to lead. You can deselect extra squads if they exceed her capacity.";
            case "barracks-command-list" -> "Recruit an available officer to continue.";
            case "officer-specialty-TACTICAL_COVER-train" -> "Scroll to compare all three specialties. Use any Train button; this spends the appointment point.";
            default -> inductionTarget().endsWith(":assign") ? "Click Assign Selected on the commander card. Capacity is checked before any squad moves." : "Open the highlighted officer portrait.";
        };
    }
    public String guideTargetId() {
        if (inductionRequired()) return inductionTarget();
        if (detail) return "barracks-back-command";
        if (lineSquads().isEmpty()) return "barracks-raise-squad";
        if (guideStep == 5) return localMissions ? "barracks-missions" : "page-nav-return";
        if (organizing || editing) return "barracks-preparation";
        if (guideStep <= 1 || selected.isEmpty()) return guideSquadTarget(false);
        if (guideStep == 2) {
            if (donors.isEmpty()) return choosingDonors ? guideSquadTarget(true) : "barracks-choose-donors";
            return reviewedTransfers != null && reviewedTransfers.canApply()
                    ? "barracks-apply-transfers" : "barracks-review-transfers";
        }
        return guideStep == 3 ? "barracks-reinforce" : "barracks-issue";
    }

    /** Each group becomes one opening; its actual controls are the only input targets. */
    public List<List<String>> guideRegions() {
        if (inductionRequired()) return inductionRegions();
        if (detail || lineSquads().isEmpty() || guideStep == 5 || organizing || editing)
            return List.of(List.of(guideTargetId()));
        var regions = new ArrayList<List<String>>();
        if (guideStep <= 2 || selected.isEmpty()) {
            regions.add(List.of("barracks-board-tools", "barracks-selection-tools",
                    "barracks-formation-list", "barracks-page-tools"));
        }
        if (guideStep == 2 && !selected.isEmpty()) regions.add(List.of("transfer-heading", "transfer-summary",
                "barracks-choose-donors", "barracks-review-transfers", "barracks-transfer-rows",
                "barracks-transfer-reason", "barracks-transfer-actions"));
        if (guideStep == 3 && !selected.isEmpty()) {
            regions.add(List.of("barracks-order-caption-82", "barracks-recruitment", "barracks-recruit-draw", "barracks-reinforce"));
            regions.add(List.of("barracks-recruit-stock"));
        }
        if (guideStep == 4 && !selected.isEmpty()) {
            regions.add(List.of("barracks-order-caption-85", "barracks-issue-summary", "barracks-issue-cost",
                    "barracks-issue-reason", "barracks-issue"));
            regions.add(List.of("barracks-issue-stock"));
        }
        return List.copyOf(regions);
    }

    private String guideSquadTarget(boolean donor) {
        var candidates = filtered().stream().skip((long) pageNumber() * PAGE_SIZE).limit(PAGE_SIZE)
                .filter(s -> !s.stationed() && (donor ? !selected.contains(s.id()) && roster.readyCount(s) > 0
                        : !donors.contains(s.id()) && roster.vacancies(s) > 0)).toList();
        return candidates.isEmpty() ? "barracks-formation-list" : "formation:" + candidates.get(0).id() + ":mark";
    }

    private String guideHint() {
        if (inductionRequired()) return inductionHint();
        if (detail) return "Use Back to Company to resume preparation. You can still inspect people here.";
        if (lineSquads().isEmpty()) return "Start with Raise Squad / Armory. Come back here once the company has a formation.";
        if (guideStep == 5) return localMissions ? "Use Missions when you are ready for another job." : "Use Return when you are ready to leave Barracks.";
        if (organizing || editing) return "Choose Preparation to return to the personnel orders.";
        if (selected.isEmpty() || guideStep <= 1) return "Select a receiving squad on the board. Look for vacant places; wounded billets stay occupied. Next skips any lesson you do not need.";
        if (guideStep == 2) {
            if (donors.isEmpty()) return choosingDonors ? "Select a donor squad on the board. Its ready marines can move into the receiving squads." : "Choose Donors, then mark the squads that may supply personnel.";
            if (reviewedTransfers == null) return "Review Transfers to see every affected squad before anyone moves.";
            return reviewedTransfers.canApply() ? "Check the projected strengths, then Apply Transfers when satisfied. The guide advances after the order succeeds." : reviewedTransfers.reason() + " Next continues without transferring anyone.";
        }
        if (guideStep == 3) return recruitment().replace("\n", " / ") + ". Fill Vacancies if needed, or Next to continue.";
        return "Review the assigned equipment and skipped-squad reasons, then Issue Assigned Equipment. The Machine Shop fits learned equipment for free.";
    }

    private CompanyManagementGuide.Line guideLine() {
        if (inductionRequired() && roster.officerInduction().companyPreparation() && inductionStep() == Step.READY)
            return new CompanyManagementGuide.Line("preparation-ready", "READY FOR OUR FIRST CONTRACT",
                    "A ship, our own lift, and a full squad under Mira. Good work, Commander. Mara Venn has our first job: Hold Until Relieved. Choose Ready for your briefing, or follow her office directions from HQ. At briefing, select Mira's squad for deployment.");
        if (inductionRequired()) return OfficerInductionGuide.line(inductionStep(),
                inductionOfficer() == null ? "your field commander" : inductionOfficer().name());
        var lines = CompanyManagementGuide.lines();
        return lines.isEmpty() ? new CompanyManagementGuide.Line("", "Guide unavailable", "") : lines.get(Math.min(guideStep, lines.size() - 1));
    }
    public void returnToBriefing(boolean value) { returnToBriefing = value; refresh(); }
    public void navigation(Runnable raiseSquad, Consumer<String> editEquipment, Runnable missions, boolean localMissions) {
        this.raiseSquad = raiseSquad; this.editEquipment = editEquipment; this.missions = missions;
        this.localMissions = localMissions; refresh();
    }
    public Set<String> donors() { return Set.copyOf(donors); }
    public void reviewTransfers() {
        reviewedTransfers = SquadConsolidation.preview(roster, selected, donors);
        refresh();
    }
    public void applyTransfers() {
        boolean applied = SquadConsolidation.apply(roster, reviewedTransfers);
        if (applied && guideVisible() && guideStep == 2) guideStep = 3;
        feedback = applied ? "Transferred " + reviewedTransfers.moves().size() + " marines with their equipment. Fill remaining vacancies, then review equipment issue."
                : "Personnel changed or no transfer is ready. Review transfers again; nothing moved.";
        reviewedTransfers = null;
        changed.run();
    }
    private List<Map<String, Object>> transferRows() {
        if (reviewedTransfers == null) return List.of();
        return reviewedTransfers.strengths().stream().map(s -> row("id", "transfer:" + s.squadId(),
                "text", roster.squadById(s.squadId()).name() + ": " + s.readyBefore() + " -> " + s.readyAfter()
                        + " ready / " + s.wounded() + " WIA / " + s.vacantBefore() + " -> " + s.vacantAfter() + " vacant")).toList();
    }

    private List<SquadWeaponDoctrine> weapons() {
        return roster.armory().weaponDoctrines().stream().filter(w -> roster.armory().canAuthorWeaponDoctrine(w.issues())).toList();
    }
    private SquadWeaponDoctrine draftWeapon() { var list = weapons(); return list.get(Math.floorMod(weaponIndex, list.size())); }
    private SquadArmorDoctrine draftArmor() { var list = roster.armory().armorDoctrines(); return list.get(Math.floorMod(armorIndex, list.size())); }
    public void saveProfile() {
        if (weapons().isEmpty()) return;
        SquadProfile profile = roster.armory().saveSquadProfile(draftName, draftWeapon().id(), draftArmor().id());
        if (profile == null) { feedback = "Use a name of 1–48 characters and known equipment."; refresh(); return; }
        profileId = profile.id(); editing = false;
        feedback = "Saved " + profile.name() + ". Assign it to selected squads; equipment changes only on issue.";
        changed.run();
    }
    private static Map<String, Object> row(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
}
