package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.Commendations;
import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.OfficerSpecialty;
import com.dillon.starsectormarines.marine.OfficerInduction;
import com.dillon.starsectormarines.marine.OfficerInduction.Step;
import com.dillon.starsectormarines.marine.OfficerCareer;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.MarinePersonnelLogistics.PersonnelDrawResult;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadArmorPlan;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadWeaponDoctrine;
import com.dillon.starsectormarines.ops.FleetArmoryViewModel.CardFace;
import com.dillon.starsectormarines.marine.SquadFoundingCost;
import com.dillon.starsectormarines.marine.SquadFoundingResources;
import com.dillon.starsectormarines.marine.SquadFoundingWorkshop;
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

/** View-local selection over campaign commands and personnel orders. A squad's kit is its cards. */
public final class BarracksCommandModel {
    private static final int PAGE_SIZE = 12;
    private final MarineRoster roster;
    private final EquipmentIssueResources resources;
    private final SquadFoundingResources foundingResources;
    private final SquadFoundingWorkshop founding;
    private final IntSupplier cargoMarines;
    private final Function<String, PersonnelDrawResult> reinforce;
    private final MutableSignal<Integer> revision;
    private final ComputedSignal<List<Map<String, Object>>> cards;
    private final ComputedSignal<List<Map<String, Object>>> tiles;
    private final DoubleSupplier currentDay;
    private final Map<String, Object> props = new LinkedHashMap<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private String command = "all";
    private boolean dossier;
    private boolean companyRoster;
    private CompanyRosterView.Sort rosterSort = CompanyRosterView.Sort.APTITUDE;
    private int rosterPage;
    private boolean guideOpen;
    private Runnable preparationComplete = () -> { };
    public void onPreparationComplete(Runnable action) { preparationComplete = action; }
    private int guideStep;
    private Runnable equipmentWorkspace = () -> { };
    private Runnable missions = () -> { };
    private Consumer<String> editEquipment = id -> { };
    private boolean localMissions;
    /**
     * The calling way back to work, a primary-action green. Inline because the theme is the
     * cascade's final layer and repaints every button's surface, a class's included.
     */
    static final String MISSION_CALL_STYLE = "background-color: #1a4a30; border-color: #78d494; color: #e8d9a7;";
    private MissionReturn missionReturn = MissionReturn.MISSIONS;
    private boolean workHere;
    private int page;
    private String feedback = Strings.get("openingBarracksCommandSelectSquadsThenAssignSelectedOnA");

    public BarracksCommandModel(Reactor reactor, MarineRoster roster, EquipmentIssueResources resources,
                                SquadFoundingResources foundingResources,
                                IntSupplier cargoMarines, Function<String, PersonnelDrawResult> reinforce,
                                DoubleSupplier currentDay) {
        this.roster = roster;
        guideOpen = inductionRequired() || !roster.managementGuideDismissed();
        this.resources = resources;
        this.foundingResources = foundingResources;
        this.founding = new SquadFoundingWorkshop(roster, foundingResources);
        this.cargoMarines = cargoMarines;
        this.reinforce = reinforce;
        this.currentDay = currentDay;
        revision = reactor.signal(0);
        props.put("equipmentWorkspace", (Runnable) () -> equipmentWorkspace.run());
        bind(reactor, "equipmentLabel", () -> equipmentUnread() ? Strings.get("openingBarracksCommandEquipmentNew") : Strings.get("openingBarracksCommandEquipment"));
        bind(reactor, "equipmentClasses", () -> equipmentUnread()
                ? "flow-button console-action equipment-new" : "flow-button console-action");
        props.put("missions", (Runnable) () -> missions.run());
        bind(reactor, "missionLabel", () -> Strings.get(missionReturn.labelKey(workHere)));
        bind(reactor, "missionClasses", () -> !localMissions ? "hidden"
                : missionReturn.calls(workHere) ? "tool-button console-action mission-call"
                : "tool-button console-action");
        bind(reactor, "missionStyle", () -> localMissions && missionReturn.calls(workHere) ? MISSION_CALL_STYLE : "");
        bind(reactor, "commands", this::commands);
        bind(reactor, "formationClasses", () -> dossier || companyRoster ? "hidden" : "formation-board panel console-section");
        bind(reactor, "rosterClasses", () -> companyRoster && !dossier ? "company-roster panel console-section" : "hidden");
        props.put("openRoster", (Runnable) () -> { companyRoster = true; dossier = false; refresh(); });
        props.put("closeRoster", (Runnable) () -> { companyRoster = false; refresh(); });
        bind(reactor, "rosterRows", () -> companyRoster
                ? CompanyRosterView.rows(roster, rosterSort, rosterPage, currentDay.getAsDouble()) : List.of());
        bind(reactor, "rosterTitle", () -> Strings.format("companyRosterTitleFmt",
                CompanyRosterView.sorted(roster, rosterSort).size()));
        bind(reactor, "rosterPageLabel", () -> Strings.format("companyRosterPageFmt", rosterPage + 1,
                CompanyRosterView.pages(CompanyRosterView.sorted(roster, rosterSort).size())));
        bind(reactor, "rosterPreviousDisabled", () -> rosterPage <= 0);
        bind(reactor, "rosterNextDisabled", () -> rosterPage + 1
                >= CompanyRosterView.pages(CompanyRosterView.sorted(roster, rosterSort).size()));
        props.put("rosterPrevious", (Runnable) () -> { rosterPage = Math.max(0, rosterPage - 1); refresh(); });
        props.put("rosterNext", (Runnable) () -> { rosterPage++; refresh(); });
        props.put("rosterSortName", (Runnable) () -> sortRoster(CompanyRosterView.Sort.NAME));
        bind(reactor, "rosterSortNameClasses", () -> rosterSortClasses(CompanyRosterView.Sort.NAME));
        props.put("rosterSortSquad", (Runnable) () -> sortRoster(CompanyRosterView.Sort.SQUAD));
        bind(reactor, "rosterSortSquadClasses", () -> rosterSortClasses(CompanyRosterView.Sort.SQUAD));
        props.put("rosterSortStatus", (Runnable) () -> sortRoster(CompanyRosterView.Sort.STATUS));
        bind(reactor, "rosterSortStatusClasses", () -> rosterSortClasses(CompanyRosterView.Sort.STATUS));
        props.put("rosterSortAptitude", (Runnable) () -> sortRoster(CompanyRosterView.Sort.APTITUDE));
        bind(reactor, "rosterSortAptitudeClasses", () -> rosterSortClasses(CompanyRosterView.Sort.APTITUDE));
        props.put("rosterSortBand", (Runnable) () -> sortRoster(CompanyRosterView.Sort.BAND));
        bind(reactor, "rosterSortBandClasses", () -> rosterSortClasses(CompanyRosterView.Sort.BAND));
        props.put("rosterSortWeapon", (Runnable) () -> sortRoster(CompanyRosterView.Sort.WEAPON));
        bind(reactor, "rosterSortWeaponClasses", () -> rosterSortClasses(CompanyRosterView.Sort.WEAPON));
        props.put("rosterSortArmor", (Runnable) () -> sortRoster(CompanyRosterView.Sort.ARMOR));
        bind(reactor, "rosterSortArmorClasses", () -> rosterSortClasses(CompanyRosterView.Sort.ARMOR));
        props.put("rosterSortService", (Runnable) () -> sortRoster(CompanyRosterView.Sort.SERVICE));
        bind(reactor, "rosterSortServiceClasses", () -> rosterSortClasses(CompanyRosterView.Sort.SERVICE));
        bind(reactor, "dossierClasses", () -> dossier ? "officer-dossier panel console-section" : "hidden");
        bind(reactor, "dossierButtonClasses", () -> selectedOfficer() == null ? "hidden" : "flow-button console-action");
        props.put("openDossier", (Runnable) () -> { dossier = selectedOfficer() != null; companyRoster = false; refresh(); });
        props.put("closeDossier", (Runnable) () -> { dossier = false; refresh(); });
        bind(reactor, "officerName", () -> selectedOfficer() == null ? "" : captainName(selectedOfficer()));
        bind(reactor, "officerPortrait", () -> selectedOfficer() == null || selectedOfficer().portraitSprite() == null
                ? "graphics/ui/console/service-plate.svg" : selectedOfficer().portraitSprite());
        bind(reactor, "officerStatus", this::officerStatus);
        bind(reactor, "officerProgress", this::officerProgress);
        bind(reactor, "officerLevel", () -> selectedOfficer() == null ? "" : level(selectedOfficer()));
        bind(reactor, "officerXpStyle", () -> selectedOfficer() == null ? "width: 0%;" : xpStyle(selectedOfficer()));
        bind(reactor, "officerPointClasses", () -> selectedOfficer() != null && canTrain(selectedOfficer())
                ? "training-point officer-point" : "hidden");
        bind(reactor, "officerService", () -> selectedOfficer() == null ? "" : Strings.format("openingBarracksCommandOperationsVictoriesMissionXpRecorded", selectedOfficer().career().deployments(), selectedOfficer().career().victories(), selectedOfficer().career().earnedXp()));
        bind(reactor, "officerTraining", () -> selectedOfficer() == null ? "" : Strings.format("openingBarracksCommandTrainingPointsOneOnAppointmentOnePer", selectedOfficer().career().availablePoints(selectedOfficer().rank())));
        bind(reactor, "officerSpecialties", this::officerSpecialties);
        bind(reactor, "officerTraits", this::officerTraits);
        bind(reactor, "officerBiography", this::officerBiography);
        bind(reactor, "officerHistory", this::officerHistory);
        bind(reactor, "officerHistoryStyle", () -> "height: " + (officerHistory().size() * 52) + "px;");
        cards = reactor.computed(() -> { revision.get(); return squads(); });
        // The raise tile rides at the end of the last page: a markup loop owns all of
        // its parent's children, so the tile is a row rather than a sibling.
        tiles = reactor.computed(() -> {
            List<Map<String, Object>> rows = new ArrayList<>(cards.get());
            if (pageNumber() + 1 == pages()) rows.add(raiseTile());
            return rows;
        });
        props.put("squads", tiles);
        bind(reactor, "recruitIcon", () -> resources.commodityIcon(Commodities.MARINES));
        bind(reactor, "recruitStock", () -> Integer.toString(cargoMarines.getAsInt()));
        bind(reactor, "issueCargoStock", this::issueCargoStock);
        bind(reactor, "guideClasses", () -> guideVisible() ? "guide-callout console-readout" : "guide-callout hidden");
        bind(reactor, "guideTitle", () -> inductionRequired()
                ? Strings.format("openingOfficerGuideHeading", CompanyManagementGuide.speaker(), guideLine().title())
                : Strings.format("openingManagementGuideHeading", CompanyManagementGuide.speaker(),
                        guideStep + 1, CompanyManagementGuide.lines().size(), guideLine().title()));
        bind(reactor, "guidePortrait", CompanyManagementGuide::portrait);
        bind(reactor, "guideText", () -> guideLine().text());
        bind(reactor, "guideStatus", this::guideHint);
        bind(reactor, "guideDismissDisabled", () -> inductionRequired() || !guideVisible());
        bind(reactor, "guideDismissClasses", () -> inductionRequired() ? "hidden" : "guide-dismiss console-action");
        bind(reactor, "guideHelpDisabled", () -> !inductionRequired() && CompanyManagementGuide.lines().isEmpty());
        bind(reactor, "guidePreviousDisabled", () -> !guideVisible() || inductionRequired() || guideStep == 0);
        bind(reactor, "guideNextDisabled", () -> !guideVisible() || (inductionRequired() ? inductionStep() != Step.READY : guideStep + 1 >= CompanyManagementGuide.lines().size()));
        bind(reactor, "guideHelpLabel", () -> inductionRequired() ? Strings.get("openingBarracksCommandCastleSLesson") : Strings.get("openingBarracksCommandCompanyGuide"));
        bind(reactor, "guidePreviousClasses", () -> inductionRequired() ? "hidden" : "tool-button console-action");
        bind(reactor, "guideProgressClasses", () -> inductionRequired() ? "guide-progress" : "hidden");
        bind(reactor, "guideProgressImage", this::guideProgressImage);
        bind(reactor, "guideNextLabel", () -> inductionRequired() ? Strings.get("openingBarracksCommandReady") : Strings.get("openingBarracksCommandNext"));
        bind(reactor, "guideDismissLabel", () -> Strings.get("openingBarracksCommandDismiss"));
        bind(reactor, "guideCloseHint", () -> inductionRequired() ? Strings.get("openingBarracksCommandUseTheHighlightedControl")
                : Strings.get("openingBarracksCommandUseTheHighlightedControlEscToDismiss"));
        props.put("guideHelp", (Runnable) this::resumeGuide);
        props.put("guidePrevious", (Runnable) () -> { if (inductionRequired()) return; guideStep = Math.max(0, guideStep - 1); refresh(); });
        props.put("guideNext", (Runnable) () -> {
            if (inductionRequired()) {
                if (roster.officerInduction().finish(roster)) {
                    guideOpen = false; refresh();
                    if (roster.officerInduction().companyPreparation()) preparationComplete.run();
                }
                refresh(); return;
            }
            guideStep = Math.min(Math.max(0, CompanyManagementGuide.lines().size() - 1), guideStep + 1); refresh(); });
        props.put("guideDismiss", (Runnable) this::dismissGuide);
        bind(reactor, "title", this::title);
        bind(reactor, "summary", () -> Strings.format("openingBarracksCommandSquadsReadyPersonnel", lineSquads().size(), roster.lineReadySoldiers().size()));
        bind(reactor, "healTimer", this::healTimer);
        bind(reactor, "scopeSummary", () -> Strings.format("openingBarracksCommandSquadsInViewSelected", filtered().size(), selected.size()));
        bind(reactor, "empty", () -> filtered().isEmpty() ? Strings.get("openingBarracksCommandNoSquadsMatchThisViewChooseRaise") : "");
        bind(reactor, "pageLabel", () -> Strings.format("openingBarracksCommandPage", (pageNumber() + 1), pages()));
        bind(reactor, "previousDisabled", () -> pageNumber() == 0);
        bind(reactor, "nextDisabled", () -> pageNumber() + 1 >= pages());
        bind(reactor, "selectionToggleLabel", () -> allInViewSelected()
                ? Strings.get("openingBarracksCommandDeselectAll") : Strings.get("openingBarracksCommandSelectAll"));
        bind(reactor, "fillSelectedCount", () -> Integer.toString(vacancies(selected)));
        bind(reactor, "fillSelectedDisabled", () -> !canFill(selected));
        bind(reactor, "feedback", () -> feedback);
        props.put("toggleSelection", (Runnable) () -> {
            if (allInViewSelected()) { resetSelection(); return; }
            filtered().stream().filter(s -> !s.stationed()).forEach(s -> selected.add(s.id()));
            if (guideVisible() && guideStep <= 1 && !selected.isEmpty()) guideStep = 2;
            refresh();
        });
        props.put("selectAll", (Runnable) () -> {
            filtered().stream().filter(s -> !s.stationed()).forEach(s -> selected.add(s.id()));
            if (guideVisible() && guideStep <= 1 && !selected.isEmpty()) guideStep = 2;
            refresh();
        });
        props.put("clear", (Runnable) this::resetSelection);
        props.put("previous", (Runnable) () -> { page = Math.max(0, pageNumber() - 1); refresh(); });
        props.put("next", (Runnable) () -> { page = Math.min(pages() - 1, pageNumber() + 1); refresh(); });
        props.put("reinforce", (Runnable) this::reinforceSelected);
    }

    private void sortRoster(CompanyRosterView.Sort sort) {
        rosterSort = sort;
        rosterPage = 0;
        refresh();
    }

    private String rosterSortClasses(CompanyRosterView.Sort sort) {
        return "roster-sort console-action" + (sort == rosterSort ? " active" : "");
    }

    /** Opens the company roster, for evidence and for screens that land on it. */
    public void showCompanyRoster() {
        companyRoster = true;
        dossier = false;
        refresh();
    }

    private void bind(Reactor reactor, String key, Supplier<?> supplier) {
        props.put(key, reactor.computed(() -> { revision.get(); return supplier.get(); }));
    }
    public Map<String, Object> props() { return props; }
    public List<Map<String, Object>> cards() { return cards.get(); }
    public SquadMuster muster(String id) { return SquadMuster.from(roster, id, (float) currentDay.getAsDouble()); }
    public Set<String> selected() { return Set.copyOf(selected); }
    public void refresh() { revision.update(n -> n + 1); }
    public void leave() { refresh(); }
    public void showOverview() { dossier = false; refresh(); }
    public void selectCommand(String id) {
        command = id; dossier = selectedOfficer() != null;
        if (inductionRequired() && selectedOfficer() == inductionOfficer())
            roster.officerInduction().introduce(selectedOfficer());
        resetSelection();
    }
    public void toggle(String id) {
        MarineSquad squad = roster.squadById(id);
        if (squad == null || squad.reserve() || squad.stationed()) return;
        if (!selected.remove(id)) selected.add(id);
        if (guideVisible() && guideStep <= 1 && !selected.isEmpty()) guideStep = 2;
        refresh();
    }
    private void resetSelection() { selected.clear(); page = 0; refresh(); }
    private List<MarineSquad> lineSquads() { return roster.squads().stream().filter(s -> !s.reserve()).toList(); }
    private String captainName(MarineCaptain captain) {
        return captain == null ? Strings.get("openingBarracksCommandIndependentActingNco") : captain.rank().displayName() + " " + captain.name();
    }
    private String group(MarineSquad squad) {
        return roster.byId(squad.homeCaptainId()) == null ? "independent" : squad.homeCaptainId();
    }
    private String title() { return "all".equals(command) ? Strings.get("openingBarracksCommandCompanyFormations") : captainName(roster.byId(command)); }
    private String profileName(MarineSquad squad) {
        var weapon = roster.armory().weaponDoctrineById(squad.weaponDoctrineId());
        return weapon != null ? weapon.displayName() : Strings.get("openingBarracksCommandIndividualIssue");
    }
    private List<MarineSquad> filtered() {
        return lineSquads().stream()
                .filter(s -> "all".equals(command) || group(s).equals(command))
                .toList();
    }
    private int pages() { return Math.max(1, (filtered().size() + PAGE_SIZE - 1) / PAGE_SIZE); }
    private int pageNumber() { return Math.min(page, pages() - 1); }
    private MarineCaptain selectedOfficer() { return roster.byId(command); }
    private String officerStatus() {
        MarineCaptain officer = selectedOfficer();
        if (officer == null) return "";
        return Strings.format("openingBarracksCommandCoordinatedSquads", officer.status(), officer.rank().squadCommandCap(), (officer.status() == Status.INJURED ? Strings.format("openingBarracksCommandReturnsDay", (int) Math.ceil(officer.injuredUntilDay())) : ""), (officer.status() != Status.ACTIVE ? Strings.get("openingBarracksCommandUnavailableForNewFieldCommand") : ""));
    }
    private String officerProgress() {
        MarineCaptain officer = selectedOfficer();
        if (officer == null) return "";
        return officer.rank().isTerminal() ? Strings.get("openingBarracksCommandColonelHighestCommandRank")
                : Strings.format("openingBarracksCommandXpTo", officer.xp(), officer.rank().xpToNext(), officer.rank().promote().displayName());
    }
    /** The officer's rank as a level, counted from one at the bottom of the ladder. */
    private static String level(MarineCaptain officer) {
        return Strings.format("openingBarracksCommandLevel", officer.rank().ordinal() + 1);
    }

    /** XP toward the next rank as a fill width; the top rank reads full. */
    private static String xpStyle(MarineCaptain officer) {
        return "width: " + Math.round(100f * xpFraction(officer.xp(), officer.rank())) + "%;";
    }

    static float xpFraction(int xp, Rank rank) {
        if (rank.isTerminal()) return 1f;
        return Math.max(0f, Math.min(1f, (float) xp / rank.xpToNext()));
    }

    private static final String TREE_LINE = "#738578";
    private static final String TREE_LINE_TRAINED = "#dec58a";

    /** Where one rank of one specialty stands for the selected officer. */
    enum SpecialtyNode { LOCKED, AVAILABLE, TRAINED }

    static SpecialtyNode specialtyNode(int level, int rank, boolean canTrain) {
        if (level >= rank) return SpecialtyNode.TRAINED;
        return level == rank - 1 && canTrain ? SpecialtyNode.AVAILABLE : SpecialtyNode.LOCKED;
    }

    private static boolean canTrain(MarineCaptain officer) {
        return officer.status() != Status.KIA && officer.career().availablePoints(officer.rank()) > 0;
    }

    /** One column per specialty, its ranks stacked top to bottom; a new specialty is a new neighbour. */
    private List<Map<String, Object>> officerSpecialties() {
        MarineCaptain officer = selectedOfficer();
        if (officer == null) return List.of();
        List<Map<String, Object>> rows = new ArrayList<>();
        List<OfficerSpecialty> paths = OfficerSpecialty.offered();
        for (OfficerSpecialty specialty : paths) {
            int level = officer.career().level(specialty);
            String id = "officer-specialty-" + specialty.name();
            // One crossbar under the root: each branch draws its half either side of
            // its drop, and the outermost halves are left open so the bar ends at a branch.
            boolean first = paths.indexOf(specialty) == 0;
            boolean last = paths.indexOf(specialty) == paths.size() - 1;
            Map<String, Object> row = row("id", id, "titleId", id + "-title", "iconId", id + "-icon",
                    "headerId", id + "-header", "linkId", id + "-link", "specialty", specialty,
                    "icon", specialtyEmblem(specialty), "title", specialty.label(),
                    "link", "graphics/ui/officers/specialty-link-" + (level >= 2 ? "on" : "off") + ".svg",
                    "link2Id", id + "-link-2",
                    "link2", "graphics/ui/officers/specialty-link-" + (level >= 3 ? "on" : "off") + ".svg",
                    "barId", id + "-bar", "barLeftId", id + "-bar-left", "barRightId", id + "-bar-right",
                    "dropId", id + "-drop",
                    "barLeftStyle", "background-color: " + (first ? "transparent" : TREE_LINE) + ";",
                    "barRightStyle", "background-color: " + (last ? "transparent" : TREE_LINE) + ";",
                    "dropStyle", "background-color: " + (level > 0 ? TREE_LINE_TRAINED : TREE_LINE) + ";");
            for (int rank = 1; rank <= OfficerCareer.MAX_LEVEL; rank++) {
                SpecialtyNode node = specialtyNode(level, rank, canTrain(officer));
                String nodeId = id + "-rank-" + rank;
                row.put("node" + rank + "Id", nodeId);
                row.put("node" + rank + "ImageId", nodeId + "-image");
                row.put("node" + rank + "Icon", "graphics/ui/officers/specialty-node-" + rank + "-"
                        + node.name().toLowerCase(Locale.ROOT) + ".svg");
                row.put("node" + rank + "Disabled", node != SpecialtyNode.AVAILABLE);
                row.put("node" + rank + "Train", (Runnable) () -> {
                    if (specialtyNode(officer.career().level(specialty), officer.career().level(specialty) + 1,
                            canTrain(officer)) != SpecialtyNode.AVAILABLE) return;
                    if (officer.train(specialty)) {
                        Commendations.add(officer, (int) currentDay.getAsDouble(), "openingBarracksCommandDayTrained", Commendations.ref(specialty.labelKey()), officer.career().level(specialty));
                        refresh();
                    }
                });
            }
            rows.add(row);
        }
        return rows;
    }

    private static String specialtyEmblem(OfficerSpecialty specialty) {
        return "graphics/ui/officers/" + specialty.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".svg";
    }

    /** The specialty columns as last published, for hover wiring. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> specialtyColumns() {
        return (List<Map<String, Object>>) ((ComputedSignal<?>) props.get("officerSpecialties")).get();
    }

    /** The tree's root: the officer's training budget, which every branch spends from. */
    public SpecSheet specialtyRootSheet() {
        MarineCaptain officer = selectedOfficer();
        if (officer == null) return null;
        return new SpecSheet(officer.name(), Strings.get("openingBarracksCommandSpecialtyRootSubtitle"),
                "graphics/ui/officers/specialty-root.svg", "item",
                List.of(SpecSheet.Stat.of(Strings.get("openingBarracksCommandSpecialtyRootPoints"),
                        Integer.toString(officer.career().availablePoints(officer.rank())))),
                List.of(Strings.get("openingBarracksCommandSpecialtyRootNote")));
    }

    /** What one rank does and whether it can be trained now: the hover answer in place of on-page copy. */
    public SpecSheet specialtySheet(OfficerSpecialty specialty, int rank) {
        MarineCaptain officer = selectedOfficer();
        if (officer == null) return null;
        int level = officer.career().level(specialty);
        SpecialtyNode node = specialtyNode(level, rank, canTrain(officer));
        String state = switch (node) {
            case TRAINED -> Strings.get("openingBarracksCommandSpecialtyTrained");
            case AVAILABLE -> Strings.get("openingBarracksCommandSpecialtyReady");
            case LOCKED -> officer.status() == Status.KIA ? Strings.get("openingBarracksCommandSpecialtyUnavailable")
                    : level < rank - 1 ? Strings.get("openingBarracksCommandSpecialtyNeedsRankOne")
                    : Strings.get("openingBarracksCommandSpecialtyNoPoint");
        };
        List<String> notes = new ArrayList<>();
        notes.add(specialty.effect(rank));
        if (node == SpecialtyNode.AVAILABLE) notes.add(Strings.get("openingBarracksCommandSpecialtyClickToTrain"));
        return new SpecSheet(Strings.format("openingBarracksCommandSpecialtyRankTitle", specialty.label(), OfficerSpecialty.numeral(rank)),
                state, specialtyEmblem(specialty), "item",
                List.of(SpecSheet.Stat.of(Strings.get("openingBarracksCommandSpecialtyRankStat"),
                                Strings.format("openingBarracksCommandSpecialtyRankOf", rank, OfficerCareer.MAX_LEVEL)),
                        SpecSheet.Stat.of(Strings.get("openingBarracksCommandSpecialtyCostStat"),
                                Strings.get("openingBarracksCommandPoint"))),
                notes);
    }
    private String officerTraits() {
        MarineCaptain officer = selectedOfficer();
        if (officer == null || officer.traits().isEmpty()) return Strings.get("openingBarracksCommandBackgroundNoRecordedTraits");
        return Strings.format("openingBarracksCommandBackground", officer.traits().stream().map(trait -> switch (trait) {
            case NATURAL_LEADER -> Strings.get("openingBarracksCommandNaturalLeaderOfficerMissionXp");
            case SALVAGE_EXPERT -> Strings.get("openingBarracksCommandSalvageExpertOperationCommanderSalvageRecoveryPoints");
            case FIELD_MEDIC -> Strings.get("openingBarracksCommandFieldMedicNamedPersonnelEffectPending");
            case IDEALIST -> Strings.get("openingBarracksCommandIdealistOutlook");
            case CYNICAL -> Strings.get("openingBarracksCommandCynicalOutlook");
            default -> Strings.format("openingBarracksCommandBackgroundNoPassiveEffectYet", trait.displayName());
        }).reduce((a, c) -> Strings.join("separatorList", a, c)).orElse(""));
    }
    private List<Map<String, Object>> officerHistory() {
        MarineCaptain officer = selectedOfficer();
        if (officer == null) return List.of();
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> notes = officer.commendations();
        for (int i = notes.size() - 1; i >= Math.max(0, notes.size() - 20); i--)
            rows.add(row("id", "officer-history-" + i, "text", Commendations.display(notes.get(i))));
        if (rows.isEmpty()) rows.add(row("id", "officer-history-empty", "text", Strings.get("openingBarracksCommandServiceRecordBeginsWithTheNextOperation")));
        return rows;
    }

    private List<Map<String, Object>> commands() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(commandRow("all", null, lineSquads()));
        for (MarineCaptain captain : roster.all()) {
            var squads = lineSquads().stream().filter(s -> captain.id().equals(group(s))).toList();
            rows.add(commandRow(captain.id(), captain, squads));
        }
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
                "assignClasses", id.equals("all") ? "hidden" : "command-assign console-action" + (assignment.valid() ? " command-assign-ready" : ""),
                "assignLabel", selected.isEmpty() ? Strings.get("openingBarracksCommandSelectSquadsFirst") : Strings.format("openingBarracksCommandAssignSelected", selected.size()),
                "assignDisabled", id.equals("all") || !assignment.valid(),
                "assign", (Runnable) () -> assignCommand(captain == null ? null : captain.id()),
                "reasonId", "command:" + id + ":reason",
                "reasonClasses", id.equals("all") || selected.isEmpty() ? "hidden" : "label muted wrap command-reason",
                "reason", assignment.reason(),
                "name", id.equals("all") ? Strings.get("openingBarracksCommandAllFormations") : captainName(captain),
                "portrait", captain != null && captain.portraitSprite() != null ? captain.portraitSprite() : "graphics/ui/console/service-plate.svg",
                "commanderbadgeId", "command:" + id + ":badge", "portraitframeId", "command:" + id + ":portrait-frame",
                "pointId", "command:" + id + ":point",
                "pointClasses", captain != null && canTrain(captain) ? "training-point" : "hidden",
                "levelId", "command:" + id + ":level",
                "levelClasses", captain == null ? "hidden" : "label heading warm commander-level",
                "level", captain == null ? "" : level(captain),
                "xpTrackId", "command:" + id + ":xp", "xpFillId", "command:" + id + ":xp-fill",
                "xpTrackClasses", captain == null ? "hidden" : "xp-track commander-xp",
                "xpStyle", captain == null ? "" : xpStyle(captain),
                "summary", Strings.format("openingBarracksCommandReadyAssigned", ready, squads.size()),
                "capacity", captain == null ? Strings.get("openingBarracksCommandCompanyRoll")
                        : Strings.format("openingBarracksCommandOfCommandSlots", captain.status(), squads.size(), captain.rank().squadCommandCap()),
                "classes", command.equals(id) ? "command-card selected" : "command-card",
                "select", (Runnable) () -> selectCommand(id));
    }
    private List<Map<String, Object>> squads() {
        var filtered = filtered();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MarineSquad squad : filtered.subList(Math.min(filtered.size(), pageNumber() * PAGE_SIZE),
                Math.min(filtered.size(), (pageNumber() + 1) * PAGE_SIZE))) {
            int ready = roster.readyCount(squad);
            SquadMuster muster = muster(squad.id());
            String personnel = squad.stationed() ? Strings.get("openingBarracksCommandOnStation") : Strings.format("openingBarracksCommandReadyWiaVacant", ready, muster.woundedCount(), muster.vacantCount());
            String id = "formation:" + squad.id();
            Map<String, Object> weaponCard = weaponCard(squad, id + ":weapon-card"), armorCard = armorCard(squad, id + ":armor-card");
            Set<String> only = Set.of(squad.id());
            int vacant = vacancies(only);
            rows.add(row("id", id, "markId", id + ":mark",
                    "squadId", squad.id(), "musterId", id + ":muster",
                    "headerId", id + ":header", "checkId", id + ":check", "strengthId", id + ":strength",
                    "cardsId", id + ":cards", "weaponChipId", id + ":weapon-card", "armorChipId", id + ":armor-card",
                    "weaponCard", weaponCard, "armorCard", armorCard,
                    "footerId", id + ":footer", "footerSpacerId", id + ":footer-spacer",
                    "fillId", id + ":fill", "fillIconId", id + ":fill-icon", "fillCountId", id + ":fill-count",
                    "fillClasses", vacant > 0 && !squad.stationed() ? "fill-button console-action" : "fill-button hidden",
                    "fillDisabled", !canFill(only), "fillCount", Integer.toString(vacant),
                    "fill", (Runnable) () -> fill(only),
                    "strength", ready + "/12", "personnel", personnel,
                    "personnelClasses", "label card-personnel " + (squad.stationed() ? "muted" : ready == 12 ? "good" : "warm"),
                    "formationnameId", id + ":name", "formationstatusId", id + ":status",
                    "loadoutId", id + ":loadout", "name", squad.name(),
                    "classes", "formation" + (selected.contains(squad.id()) ? " selected" : "") + (squad.stationed() ? " stationed" : ""),
                    "mark", selected.contains(squad.id()) ? "+" : "", "disabled", squad.stationed(),
                    "toggle", (Runnable) () -> toggle(squad.id()), "loadout", (Runnable) () -> editEquipment.accept(squad.id()),
                    "selectClasses", "formation-select", "footerClasses", "card-footer"));
            founderFields(rows.get(rows.size() - 1), "formation:" + squad.id(), false);
        }
        return rows;
    }
    /** The formation board's own founding control: the new squad joins whichever command is open. */
    private Map<String, Object> raiseTile() {
        String id = "barracks-raise-squad";
        Map<String, Object> tile = new LinkedHashMap<>(row("id", id + ":tile", "markId", id + ":select",
                "squadId", "", "musterId", id + ":muster", "headerId", id + ":header", "checkId", id + ":check",
                "strengthId", id + ":strength", "cardsId", id + ":cards", "weaponChipId", id + ":weapon-card",
                "armorChipId", id + ":armor-card",
                "weaponCard", CardChips.empty(id + ":weapon-card", true), "armorCard", CardChips.empty(id + ":armor-card", true),
                "footerId", id + ":footer", "footerSpacerId", id + ":footer-spacer",
                "fillId", id + ":fill", "fillIconId", id + ":fill-icon", "fillCountId", id + ":fill-count",
                "fillClasses", "fill-button hidden", "fillDisabled", true, "fillCount", "", "fill", (Runnable) () -> { },
                "strength", "", "personnel", "", "personnelClasses", "hidden",
                "formationnameId", id + ":name", "formationstatusId", id + ":status",
                "loadoutId", id + ":loadout", "name", "",
                "classes", "formation raise-tile", "mark", "", "disabled", true,
                "toggle", (Runnable) () -> { }, "loadout", (Runnable) () -> { },
                "selectClasses", "hidden", "footerClasses", "hidden"));
        founderFields(tile, id, true);
        return tile;
    }
    private void founderFields(Map<String, Object> row, String prefix, boolean shown) {
        String id = shown ? prefix : prefix + ":raise";
        row.put("founderId", id);
        for (String part : List.of("Heading", "Title", "Middle", "Emblem", "EmblemIcon", "Destination", "Costs"))
            row.put("founder" + part + "Id", id + ":" + part.toLowerCase(Locale.ROOT));
        boolean ready = shown && raiseCommander() != null && foundingResources.canAfford(SquadFoundingCost.STANDARD);
        row.put("founderClasses", shown ? "raise-squad" : "hidden");
        row.put("founderDisabled", !ready);
        row.put("raise", shown ? (Runnable) this::raiseSquad : (Runnable) () -> { });
        row.put("founderDestination", shown ? raiseDestination() : "");
        row.put("founderCosts", shown ? foundingCostRows() : List.of());
    }
    /** The whole founding bill as one line of icons and counts, the price for its stores and then its marines; a short item is marked. */
    private List<Map<String, Object>> foundingCostRows() {
        SquadFoundingCost cost = SquadFoundingCost.STANDARD;
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(foundingCostRow("credits", CompanyResources.CREDITS_ICON, CompanyResources.number(cost.credits()),
                foundingResources.credits() < cost.credits()));
        for (SquadFoundingCost.Line line : cost.lines()) {
            rows.add(foundingCostRow(line.commodityId(), foundingResources.commodityIcon(line.commodityId()),
                    CompanyResources.number(line.quantity()),
                    foundingResources.available(line.commodityId()) < line.quantity()));
        }
        return rows;
    }
    private static Map<String, Object> foundingCostRow(String key, String icon, String label, boolean shortOf) {
        String id = "barracks-raise-cost:" + key;
        return row("id", id, "iconId", id + ":icon", "labelId", id + ":label", "icon", icon, "label", label,
                "classes", shortOf ? "raise-cost short" : "raise-cost");
    }
    /**
     * The officer a raised squad would join, or null when no command has room. A squad is only
     * raised into a free command slot: the open officer's, or with All formations open, the first
     * officer on the roll who still has one.
     */
    private MarineCaptain raiseCommander() {
        MarineCaptain officer = selectedOfficer();
        if (officer != null) return hasCommandSlot(officer) ? officer : null;
        return roster.all().stream().filter(this::hasCommandSlot).findFirst().orElse(null);
    }
    private boolean hasCommandSlot(MarineCaptain officer) {
        long held = lineSquads().stream().filter(s -> officer.id().equals(s.homeCaptainId())).count();
        return held < officer.rank().squadCommandCap();
    }
    private String raiseDestination() {
        MarineCaptain officer = raiseCommander();
        if (officer != null) return Strings.format("openingBarracksCommandRaiseSquadJoins", captainName(officer));
        return selectedOfficer() != null
                ? Strings.format("openingBarracksCommandRaiseSquadCommandFull", captainName(selectedOfficer()))
                : Strings.get("openingBarracksCommandRaiseSquadNoSlot");
    }
    /** Founds a full line squad from stores into a free command slot; refuses when there is none. */
    public void raiseSquad() {
        MarineCaptain officer = raiseCommander();
        if (officer == null) {
            feedback = raiseDestination();
            refresh();
            return;
        }
        SquadFoundingWorkshop.Result result = founding.foundSquad();
        if (!result.succeeded()) {
            feedback = Strings.get("openingFleetArmoryInsufficientCargoTheCompleteFoundingBillMust");
            refresh();
            return;
        }
        MarineSquad squad = result.squad();
        roster.assignCaptainToSquads(officer.id(), Set.of(squad.id()));
        feedback = Strings.format("openingBarracksCommandRaisedSquadUnder", squad.name(), MarineSquad.CAPACITY, captainName(officer));
        page = pages() - 1;
        refresh();
    }

    /** The suit most of the squad wears, for the card's icon. */
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
        List<String> notes = new ArrayList<>();
        notes.add(Strings.format("openingBarracksCommandCards", profileName(squad),
                ArmorPlanPresentation.label(roster.armory(), squad.armorDoctrineId())));
        notes.add(armor == null ? Strings.get("openingBarracksCommandNoIssuedArmorAllBilletsVacant") : Strings.format("openingBarracksCommandArmorImageTheMostCommonCurrentIssue", armor.displayName()));
        if (muster.woundedCount() > 0) notes.add(String.format(Strings.locale(),
                Strings.get("openingBarracksCommandWoundedPersonnelKeepTheirBilletsNextReturn"), muster.earliestRecoveryDays()));
        notes.add(squad.stationed() ? Strings.get("openingBarracksCommandOnStationInspectIsAvailableBulkOrders")
                : Strings.get("openingBarracksCommandClickTheCardToSelectItFor"));
        return new SpecSheet(squad.name(), profileName(squad), armor == null ? "" : armor.iconPath(), "item",
                List.of(SpecSheet.Stat.of(Strings.get("openingBarracksCommandReady2"), Strings.format("openingBarracksCommandReadyOfBillets", muster.readyCount(), 12)),
                        SpecSheet.Stat.of(Strings.get("openingBarracksCommandWounded"), Integer.toString(muster.woundedCount())),
                        SpecSheet.Stat.of(Strings.get("openingBarracksCommandVacant"), Integer.toString(muster.vacantCount())),
                        SpecSheet.Stat.of(Strings.get("openingBarracksCommandHomeCommand2"), captainName(roster.byId(squad.homeCaptainId())))), notes);
    }
    public void assignCommand(String captainId) {
        var result = roster.assignCaptainToSquads(captainId, selected);
        feedback = result.valid() ? Strings.format("openingBarracksCommandSquadsAssignedTo", result.changedSquadCount(), captainName(roster.byId(captainId))) : result.reason();
        refresh();
    }
    /** Company-wide countdown until every line squad's wounded are back on duty. */
    private String healTimer() {
        List<SquadMuster> musters = lineSquads().stream().map(s -> muster(s.id())).toList();
        int hours = SquadMuster.hoursUntilFullHeal(musters);
        if (hours == 0) return Strings.get("openingBarracksCommandFullHealNone");
        int wounded = musters.stream().filter(Objects::nonNull).mapToInt(SquadMuster::woundedCount).sum();
        return Strings.format("openingBarracksCommandFullHealHours", hours, wounded);
    }
    private int vacancies(Set<String> squadIds) {
        return lineSquads().stream().filter(s -> squadIds.contains(s.id()) && !s.stationed()).mapToInt(roster::vacancies).sum();
    }
    private boolean canFill(Set<String> squadIds) {
        return Math.min(vacancies(squadIds), roster.readyReserveCount() + cargoMarines.getAsInt()) > 0;
    }
    private String recruitment(Set<String> squadIds) {
        int vacancies = vacancies(squadIds);
        int reserves = Math.min(vacancies, roster.readyReserveCount());
        int wounded = lineSquads().stream().filter(s -> squadIds.contains(s.id())).mapToInt(s -> muster(s.id()).woundedCount()).sum();
        int shortfall = vacancies - reserves - recruitsRequired(squadIds);
        String summary = Strings.format("companyResourceRecruitment", vacancies, recruitsRequired(squadIds), wounded);
        return shortfall > 0 ? summary + " " + Strings.format("companyResourceRecruitmentShort", shortfall) : summary;
    }
    private int recruitsRequired(Set<String> squadIds) {
        return Math.min(Math.max(0, vacancies(squadIds) - roster.readyReserveCount()), cargoMarines.getAsInt());
    }
    private boolean allInViewSelected() {
        List<MarineSquad> selectable = filtered().stream().filter(s -> !s.stationed()).toList();
        return !selectable.isEmpty() && selectable.stream().allMatch(s -> selected.contains(s.id()));
    }
    /** What filling these squads would draw, for the wrench on a card or on the board. */
    public SpecSheet fillSheet(Set<String> squadIds) {
        String subject = squadIds.size() == 1 && roster.squadById(squadIds.iterator().next()) != null
                ? roster.squadById(squadIds.iterator().next()).name()
                : Strings.format("openingBarracksCommandSquadsSelected", squadIds.size());
        int vacancies = vacancies(squadIds);
        return new SpecSheet(Strings.get("openingBarracksCommandFillVacancies"), subject,
                resources.commodityIcon(Commodities.MARINES), "item",
                List.of(SpecSheet.Stat.of(Strings.get("openingBarracksCommandVacant"), Integer.toString(vacancies)),
                        SpecSheet.Stat.of(Strings.get("openingBarracksCommandWillEnlist"), Integer.toString(recruitsRequired(squadIds)))),
                List.of(recruitment(squadIds), Strings.get("openingBarracksCommandRecruitsWearTheirSeatsKit")));
    }
    public SpecSheet fillSelectedSheet() { return fillSheet(Set.copyOf(selected)); }
    public SpecSheet weaponCardSheet(String squadId) {
        CardFace face = weaponFace(roster.squadById(squadId));
        return face == null ? null : face.sheet();
    }
    public SpecSheet armorCardSheet(String squadId) {
        CardFace face = armorFace(roster.squadById(squadId));
        return face == null ? null : face.sheet();
    }
    /** The squad's weapon card as a compact chip, or an empty slot where it holds none. */
    private Map<String, Object> weaponCard(MarineSquad squad, String id) {
        SquadWeaponDoctrine doctrine = roster.armory().weaponDoctrineById(squad.weaponDoctrineId());
        return doctrine == null ? CardChips.empty(id, true) : CardChips.weapons(id, doctrine, true);
    }

    /** The squad's tactic sheet as a compact chip, or an empty slot where it cannot be issued. */
    private Map<String, Object> armorCard(MarineSquad squad, String id) {
        SquadArmorPlan plan = SquadEquipmentDoctrines.armorPlanById(squad.armorDoctrineId());
        if (plan == null) return CardChips.empty(id, true);
        try {
            return CardChips.sheet(id, plan, roster.armory().issue(plan), true);
        } catch (IllegalStateException unresolvable) {
            return CardChips.empty(id, true);
        }
    }
    private CardFace weaponFace(MarineSquad squad) {
        SquadWeaponDoctrine doctrine = squad == null ? null : roster.armory().weaponDoctrineById(squad.weaponDoctrineId());
        return doctrine == null ? null : FleetArmoryViewModel.weaponCardFace(doctrine);
    }
    private CardFace armorFace(MarineSquad squad) {
        SquadArmorPlan plan = squad == null ? null : SquadEquipmentDoctrines.armorPlanById(squad.armorDoctrineId());
        if (plan == null) return null;
        try {
            return FleetArmoryViewModel.armorCardFace(plan, roster.armory().issue(plan));
        } catch (IllegalStateException unresolvable) {
            return null;
        }
    }
    public SpecSheet recruitSheet() {
        return new SpecSheet(Strings.get("openingBarracksCommandMarinesAvailableRecruits"), Strings.get("openingBarracksCommandUnenlistedPersonnelInFleetCargo"),
                resources.commodityIcon(Commodities.MARINES), "item",
                List.of(SpecSheet.Stat.of(Strings.get("openingBarracksCommandInCargo"), Integer.toString(cargoMarines.getAsInt())),
                        SpecSheet.Stat.of(Strings.get("openingBarracksCommandWillEnlist"), Integer.toString(recruitsRequired(Set.copyOf(selected))))),
                List.of(Strings.get("openingBarracksCommandTheMarinesCommodityRepresentsPersonnelOutsideYour"),
                        Strings.get("openingBarracksCommandFillVacanciesEnlistsCargoMarines"),
                        Strings.get("openingBarracksCommandRecruitsWearTheirSeatsKit")));
    }

    private List<Map<String, Object>> issueCargoStock() {
        revision.get();
        return List.of(row("id", "barracks-recruit-stock", "copyId", "barracks-recruit-stock-copy", "iconId", "barracks-recruit-stock-icon",
                "labelId", "barracks-recruit-stock-count", "nameId", "barracks-recruit-stock-label",
                "name", resources.commodityName(Commodities.MARINES), "icon", resources.commodityIcon(Commodities.MARINES),
                "stock", CompanyResources.number(cargoMarines.getAsInt())));
    }

    public void reinforceSelected() { fill(Set.copyOf(selected)); }
    /** Fills the given squads' true vacancies: ready reserves first, then cargo Marines. */
    public void fill(Set<String> squadIds) {
        int reserves = 0;
        int cargo = 0;
        for (MarineSquad squad : lineSquads()) {
            if (!squadIds.contains(squad.id()) || squad.stationed()) continue;
            PersonnelDrawResult result = reinforce.apply(squad.id());
            reserves += result.reservesAssigned(); cargo += result.cargoMarinesConsumed();
        }
        if (guideVisible() && guideStep == 2 && reserves + cargo > 0) guideStep = 3;
        feedback = Strings.format("openingBarracksCommandFilledBilletsEnlisted", reserves + cargo);
        refresh();
    }
    private boolean campaignGuidance;
    public void campaignGuidance(boolean active) { if (campaignGuidance != active) { campaignGuidance = active; refresh(); } }
    public boolean guideVisible() {
        if (campaignGuidance && !inductionRequired()) return false;
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
        dossier = false;
        command = "all";
        guideOpen = true; resetSelection();
    }
    private String guideProgressImage() {
        if (inductionRequired() && roster.officerInduction().companyPreparation()) {
            int index = switch (inductionStep()) {
                case SHIP -> 0;
                case INTRODUCTION, SPECIALTY -> 1;
                case FORMATION -> 2;
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
        return selectedOfficer() != null && induction != null && induction.foundingOfficerId().equals(selectedOfficer().id())
                ? Strings.get("openingBarracksCommandSabineHaleFoundingFieldCommanderAFormer")
                : "";
    }
    private String inductionTarget() {
        Step step = inductionStep();
        if (step == Step.SHIP) return "page-nav-hq";
        if (step == Step.INTRODUCTION) return inductionOfficerTarget();
        if (step == Step.SPECIALTY)
            return !dossier || selectedOfficer() != inductionOfficer() ? inductionOfficerTarget()
                    : "officer-specialty-list";
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
                ? Strings.get("openingBarracksCommandChooseReadyToFinishPreparationAndContinue")
                : Strings.get("openingBarracksCommandChooseReadyToCompleteInductionThenReturn");
        return switch (inductionTarget()) {
            case "page-nav-hq" -> Strings.get("openingBarracksCommandChooseACompanyShipInHqBefore");
            case "officer-formations" -> Strings.get("openingBarracksCommandChooseViewFormationsToReturnToThe");
            case "barracks-raise-squad" -> Strings.get("openingBarracksCommandRaiseSquadOpensTheEquipmentWorkspaceFound");
            case "command:all" -> Strings.get("openingBarracksCommandChooseAllFormationsSoTheUnassignedSquad");
            case "barracks-select-all" -> Strings.get("openingBarracksCommandMarkTheSquadYouWantHerTo");
            case "barracks-command-list" -> Strings.get("openingBarracksCommandRecruitAnAvailableOfficerToContinue");
            case "officer-specialty-list" -> Strings.get("openingBarracksCommandHoverToCompareSpecialties");
            default -> inductionTarget().endsWith(":assign") ? Strings.get("openingBarracksCommandClickAssignSelectedOnTheCommanderCard") : Strings.get("openingBarracksCommandOpenTheHighlightedOfficerPortrait");
        };
    }
    public String guideTargetId() {
        if (inductionRequired()) return inductionTarget();
        if (lineSquads().isEmpty()) return "barracks-raise-squad";
        if (guideStep == 3) return localMissions ? "barracks-missions" : "page-nav-return";
        if (guideStep <= 1 || selected.isEmpty()) return guideSquadTarget();
        return "barracks-fill-selected";
    }

    /** Each group becomes one opening; its actual controls are the only input targets. */
    public List<List<String>> guideRegions() {
        if (inductionRequired()) return inductionRegions();
        if (lineSquads().isEmpty() || guideStep == 3)
            return List.of(List.of(guideTargetId()));
        var regions = new ArrayList<List<String>>();
        if (guideStep <= 1 || selected.isEmpty()) {
            regions.add(List.of("barracks-selection-tools",
                    "barracks-formation-list", "barracks-page-tools"));
        }
        if (guideStep == 2 && !selected.isEmpty()) {
            regions.add(List.of("barracks-selection-tools", "barracks-formation-list", "barracks-page-tools"));
            regions.add(List.of("barracks-recruit-stock"));
        }
        return List.copyOf(regions);
    }

    private String guideSquadTarget() {
        var candidates = filtered().stream().skip((long) pageNumber() * PAGE_SIZE).limit(PAGE_SIZE)
                .filter(s -> !s.stationed() && roster.vacancies(s) > 0).toList();
        return candidates.isEmpty() ? "barracks-formation-list" : "formation:" + candidates.get(0).id() + ":mark";
    }

    private String guideHint() {
        if (inductionRequired()) return inductionHint();
        if (lineSquads().isEmpty()) return Strings.get("openingBarracksCommandStartWithRaiseSquadComeBackHere");
        if (guideStep == 3) return localMissions ? Strings.get("openingBarracksCommandUseMissionsWhenYouAreReadyFor") : Strings.get("openingBarracksCommandUseReturnWhenYouAreReadyTo");
        if (selected.isEmpty() || guideStep <= 1) return Strings.get("openingBarracksCommandSelectAReceivingSquadOnTheBoard");
        return Strings.format("openingBarracksCommandFillVacanciesIfNeededOrNextTo", recruitment(Set.copyOf(selected)).replace("\n", Strings.get("separatorPath")));
    }

    private CompanyManagementGuide.Line guideLine() {
        if (inductionRequired() && roster.officerInduction().companyPreparation() && inductionStep() == Step.READY)
            return new CompanyManagementGuide.Line("preparation-ready", Strings.get("openingBarracksCommandReadyForOurFirstContract"),
                    Strings.get("openingBarracksCommandMaraVennAnsweredWaylineNeedsItsCheckpoint"));
        if (inductionRequired()) return OfficerInductionGuide.line(inductionStep(),
                inductionOfficer() == null ? Strings.get("openingBarracksCommandYourFieldCommander") : inductionOfficer().name());
        var lines = CompanyManagementGuide.lines();
        return lines.isEmpty() ? new CompanyManagementGuide.Line("", Strings.get("openingBarracksCommandGuideUnavailable"), "") : lines.get(Math.min(guideStep, lines.size() - 1));
    }
    /** Where the way back to work leads, and whether a contact at this planet has work the company may take. */
    public void missionReturn(MissionReturn value, boolean workHere) {
        missionReturn = value;
        this.workHere = workHere;
        refresh();
    }
    private boolean equipmentUnread() {
        return FirstRefit.available(roster)
                && !roster.hasReviewedCompanyNotice(CompanyNotices.Notice.FIRST_REFIT.id);
    }
    public void equipmentWorkspace(Runnable action) { equipmentWorkspace = action; }
    public void navigation(Consumer<String> editEquipment, Runnable missions, boolean localMissions) {
        this.editEquipment = editEquipment; this.missions = missions;
        this.localMissions = localMissions; refresh();
    }
    private static Map<String, Object> row(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
}
