package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.BarracksEquipmentIssue;
import com.dillon.starsectormarines.marine.Commendations;
import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.OfficerSpecialty;
import com.dillon.starsectormarines.marine.OfficerInduction;
import com.dillon.starsectormarines.marine.OfficerInduction.Step;
import com.dillon.starsectormarines.marine.OfficerCareer;
import com.dillon.starsectormarines.marine.Status;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.MarinePersonnelLogistics.PersonnelDrawResult;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadArmorDoctrine;
import com.dillon.starsectormarines.marine.SquadProfile;
import com.dillon.starsectormarines.marine.SquadFoundingCost;
import com.dillon.starsectormarines.marine.SquadFoundingResources;
import com.dillon.starsectormarines.marine.SquadFoundingWorkshop;
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
    private String profileId;
    private boolean dossier;
    private boolean companyRoster;
    private CompanyRosterView.Sort rosterSort = CompanyRosterView.Sort.APTITUDE;
    private int rosterPage;
    private boolean editing;
    private boolean organizing;
    private boolean guideOpen;
    private Runnable preparationComplete = () -> { };
    public void onPreparationComplete(Runnable action) { preparationComplete = action; }
    private int guideStep;
    private Runnable equipmentWorkspace = () -> { };
    private Runnable missions = () -> { };
    private Consumer<String> editEquipment = id -> { };
    private boolean localMissions;
    private boolean returnToBriefing;
    private String draftName = Strings.get("openingBarracksCommandLineInfantry");
    private int weaponIndex;
    private int armorIndex;
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
        this.equipment = new BarracksEquipmentIssue(roster, resources);
        this.cargoMarines = cargoMarines;
        this.reinforce = reinforce;
        this.currentDay = currentDay;
        revision = reactor.signal(0);
        props.put("equipmentWorkspace", (Runnable) () -> equipmentWorkspace.run());
        bind(reactor, "equipmentLabel", () -> equipmentUnread() ? Strings.get("openingBarracksCommandEquipmentNew") : Strings.get("openingBarracksCommandEquipment"));
        bind(reactor, "equipmentClasses", () -> equipmentUnread()
                ? "flow-button console-action equipment-new" : "flow-button console-action");
        props.put("missions", (Runnable) () -> missions.run());
        bind(reactor, "missionLabel", () -> returnToBriefing ? Strings.get("openingBarracksCommandBriefing") : Strings.get("openingBarracksCommandMissions"));
        bind(reactor, "missionClasses", () -> localMissions ? "tool-button console-action" : "hidden");
        bind(reactor, "commands", this::commands);
        bind(reactor, "formationClasses", () -> dossier || companyRoster ? "hidden" : "formation-board panel console-section");
        bind(reactor, "ordersPanelClasses", () -> dossier || companyRoster ? "hidden" : "orders panel console-section");
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
        bind(reactor, "officerService", () -> selectedOfficer() == null ? "" : Strings.format("openingBarracksCommandOperationsVictoriesMissionXpRecorded", selectedOfficer().career().deployments(), selectedOfficer().career().victories(), selectedOfficer().career().earnedXp()));
        bind(reactor, "officerSquads", () -> selectedOfficer() == null ? "" : Strings.format("openingBarracksCommandHomeCommand", lineSquads().stream()
                .filter(squad -> command.equals(squad.homeCaptainId())).map(MarineSquad::name)
                .reduce((a, c) -> a + ", " + c).orElse(Strings.get("openingBarracksCommandNoSquadsAssigned"))));
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
        bind(reactor, "profiles", this::profiles);
        bind(reactor, "recruitIcon", () -> resources.commodityIcon(Commodities.MARINES));
        bind(reactor, "recruitStock", () -> Integer.toString(cargoMarines.getAsInt()));
        bind(reactor, "issueCargoStock", this::issueCargoStock);
        bind(reactor, "recruitBill", () -> recruitsRequired() <= 0 ? List.of() : List.of(
                CompanyResources.cost("barracks-enlist-cost", resources.commodityName(Commodities.MARINES),
                        resources.commodityIcon(Commodities.MARINES), cargoMarines.getAsInt(), recruitsRequired())));
        bind(reactor, "recruitDraw", () -> Integer.toString(recruitsRequired()));
        bind(reactor, "profilesEmpty", () -> roster.armory().squadProfiles().isEmpty()
                ? Strings.get("openingBarracksCommandNoSavedProfilesChooseNewToPair") : "");
        bind(reactor, "ordersClasses", () -> editing || !organizing ? "order-body hidden" : "order-body");
        bind(reactor, "preparationClasses", () -> editing || organizing ? "order-body hidden" : "order-body");
        props.put("preparation", (Runnable) () -> { organizing = false; editing = false; refresh(); });
        props.put("organization", (Runnable) () -> { organizing = true; editing = false; refresh(); });
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
        bind(reactor, "editorClasses", () -> editing ? "order-body" : "order-body hidden");
        bind(reactor, "title", this::title);
        bind(reactor, "summary", () -> Strings.format("openingBarracksCommandSquadsReadyPersonnelReserves", lineSquads().size(), roster.lineReadySoldiers().size(), roster.readyReserveCount()));
        bind(reactor, "healTimer", this::healTimer);
        bind(reactor, "scopeSummary", () -> Strings.format("openingBarracksCommandSquadsInViewSelected", filtered().size(), selected.size()));
        bind(reactor, "empty", () -> filtered().isEmpty() ? Strings.get("openingBarracksCommandNoSquadsMatchThisViewChooseRaise") : "");
        bind(reactor, "pageLabel", () -> Strings.format("openingBarracksCommandPage", (pageNumber() + 1), pages()));
        bind(reactor, "previousDisabled", () -> pageNumber() == 0);
        bind(reactor, "nextDisabled", () -> pageNumber() + 1 >= pages());
        bind(reactor, "selection", () -> Strings.format("openingBarracksCommandSquadsSelected", selected.size()));
        bind(reactor, "selectionDisabled", selected::isEmpty);
        bind(reactor, "profileDisabled", () -> selected.isEmpty() || profileId == null);
        bind(reactor, "recruitment", this::recruitment);
        bind(reactor, "reinforceDisabled", () -> Math.min(vacancies(), roster.readyReserveCount() + cargoMarines.getAsInt()) == 0);
        bind(reactor, "issueSummary", () -> {
            var plan = equipment.preview(selected);
            return Strings.format("openingBarracksCommandEligibleSkipped", plan.eligible(), plan.skipped());
        });
        bind(reactor, "issueCost", () -> Strings.get("openingBarracksCommandMachineShopFittingIsFreeForLearned"));
        bind(reactor, "issueDisabled", () -> {
            var plan = equipment.preview(selected);
            return plan.eligible() == 0 || !plan.affordable();
        });
        bind(reactor, "issueReason", () -> {
            var plan = equipment.preview(selected);
            if (!selected.isEmpty() && !plan.affordable()) return Strings.get("openingBarracksCommandSelectCurrentSquadsToFit");
            return plan.entries().stream().filter(e -> !e.eligible())
                    .map(e -> roster.squadById(e.squadId()).name() + ": " + e.reason())
                    .reduce((a, b) -> a + "\n" + b).orElse(Strings.get("openingBarracksCommandReadySquadsReceiveTheirAssignedEquipmentPersonnel"));
        });
        bind(reactor, "feedback", () -> feedback);
        bind(reactor, "draftName", () -> draftName);
        bind(reactor, "draftWeapon", () -> weapons().isEmpty() ? Strings.get("openingBarracksCommandNoKnownLoadouts") : draftWeapon().displayName());
        bind(reactor, "draftArmor", () -> draftArmor().displayName());
        bind(reactor, "saveDisabled", () -> draftName.isBlank() || draftName.trim().length() > 48 || weapons().isEmpty());
        props.put("editName", (Consumer<String>) value -> { draftName = value; refresh(); });
        props.put("selectAll", (Runnable) () -> {
            filtered().stream().filter(s -> !s.stationed()).forEach(s -> selected.add(s.id()));
            if (guideVisible() && guideStep <= 1 && !selected.isEmpty()) guideStep = 2;
            refresh();
        });
        props.put("clear", (Runnable) this::resetSelection);
        props.put("previous", (Runnable) () -> { page = Math.max(0, pageNumber() - 1); refresh(); });
        props.put("next", (Runnable) () -> { page = Math.min(pages() - 1, pageNumber() + 1); refresh(); });
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
    public void leave() { editing = false; refresh(); }
    public void showOverview() { dossier = false; refresh(); }
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
        SquadProfile profile = roster.armory().squadProfile(squad.squadProfileId());
        if (profile != null) return profile.name();
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
        OfficerSpecialty[] paths = OfficerSpecialty.values();
        for (OfficerSpecialty specialty : paths) {
            int level = officer.career().level(specialty);
            String id = "officer-specialty-" + specialty.name();
            // One crossbar under the root: each branch draws its half either side of
            // its drop, and the outermost halves are left open so the bar ends at a branch.
            boolean first = specialty.ordinal() == 0;
            boolean last = specialty.ordinal() == paths.length - 1;
            Map<String, Object> row = row("id", id, "titleId", id + "-title", "iconId", id + "-icon",
                    "headerId", id + "-header", "linkId", id + "-link", "specialty", specialty,
                    "icon", specialtyEmblem(specialty), "title", specialty.label(),
                    "link", "graphics/ui/officers/specialty-link-" + (level >= OfficerCareer.MAX_LEVEL ? "on" : "off") + ".svg",
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
        return new SpecSheet(Strings.format("openingBarracksCommandSpecialtyRankTitle", specialty.label(), rank == 1 ? "I" : "II"),
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
            var plan = equipment.preview(Set.of(squad.id()));
            SquadMuster muster = muster(squad.id());
            MarineArmorCatalogDef armor = representativeArmor(squad);
            String personnel = squad.stationed() ? Strings.get("openingBarracksCommandOnStation") : Strings.format("openingBarracksCommandReadyWiaVacant", ready, muster.woundedCount(), muster.vacantCount());
            String kit = squad.squadProfileId() == null && squad.weaponDoctrineId() == null ? Strings.get("openingBarracksCommandNoProfile")
                    : plan.eligible() == 0 ? Strings.get("openingBarracksCommandIssueHeld") : plan.cost().isZero() ? Strings.get("openingBarracksCommandEquipped") : Strings.get("openingBarracksCommandRefit");
            MarineCaptain home = roster.byId(squad.homeCaptainId());
            rows.add(row("id", "formation:" + squad.id(), "markId", "formation:" + squad.id() + ":mark",
                    "squadId", squad.id(), "musterId", "formation:" + squad.id() + ":muster",
                    "headerId", "formation:" + squad.id() + ":header", "checkId", "formation:" + squad.id() + ":check",
                    "strengthId", "formation:" + squad.id() + ":strength", "profileRowId", "formation:" + squad.id() + ":profile-row",
                    "armorIconId", "formation:" + squad.id() + ":armor-icon", "footerId", "formation:" + squad.id() + ":footer",
                    "kitId", "formation:" + squad.id() + ":kit", "armorIcon", armor == null ? "" : armor.iconPath(),
                    "strength", ready + "/12", "personnel", personnel, "kit", selected.contains(squad.id()) ? Strings.get("openingBarracksCommandSelected") : kit,
                    "personnelClasses", "label card-personnel " + (squad.stationed() ? "muted" : ready == 12 ? "good" : "warm"),
                    "kitClasses", "label card-kit " + (kit.equals(Strings.get("openingBarracksCommandEquipped")) ? "good" : "muted"),
                    "formationidentityId", "formation:" + squad.id() + ":identity",
                    "formationnameId", "formation:" + squad.id() + ":name",
                    "formationstatusId", "formation:" + squad.id() + ":status",
                    "formationprofileId", "formation:" + squad.id() + ":profile",
                    "loadoutId", "formation:" + squad.id() + ":loadout", "name", squad.name(),
                    "profile", profileName(squad),
                    "commanderId", "formation:" + squad.id() + ":commander",
                    "commanderImageId", "formation:" + squad.id() + ":commander-image",
                    "commanderNameId", "formation:" + squad.id() + ":commander-name",
                    "commanderName", home == null ? Strings.get("openingBarracksCommandIndependentActingNco") : captainName(home),
                    "commanderImage", home == null || home.portraitSprite() == null ? "graphics/ui/console/service-plate.svg" : home.portraitSprite(),
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
                "strengthId", id + ":strength", "profileRowId", id + ":profile-row", "armorIconId", id + ":armor-icon",
                "footerId", id + ":footer", "kitId", id + ":kit", "armorIcon", "", "strength", "", "personnel", "",
                "kit", "", "personnelClasses", "hidden", "kitClasses", "hidden",
                "formationidentityId", id + ":identity", "formationnameId", id + ":name",
                "formationstatusId", id + ":status", "formationprofileId", id + ":profile",
                "loadoutId", id + ":loadout", "name", "", "profile", "",
                "commanderId", id + ":commander", "commanderImageId", id + ":commander-image",
                "commanderNameId", id + ":commander-name", "commanderName", "", "commanderImage", "",
                "classes", "formation raise-tile", "mark", "", "disabled", true,
                "toggle", (Runnable) () -> { }, "loadout", (Runnable) () -> { },
                "selectClasses", "hidden", "footerClasses", "hidden"));
        founderFields(tile, id, true);
        return tile;
    }
    private void founderFields(Map<String, Object> row, String prefix, boolean shown) {
        String id = shown ? prefix : prefix + ":raise";
        row.put("founderId", id);
        for (String part : List.of("Plus", "Heading", "Title", "Destination", "Costs"))
            row.put("founder" + part + "Id", id + ":" + part.toLowerCase(Locale.ROOT));
        boolean ready = shown && raiseCommander() != null && foundingResources.canAfford(SquadFoundingCost.STANDARD);
        row.put("founderClasses", shown ? "raise-squad console-action" : "hidden");
        row.put("founderDisabled", !ready);
        row.put("raise", shown ? (Runnable) this::raiseSquad : (Runnable) () -> { });
        row.put("founderDestination", shown ? raiseDestination() : "");
        row.put("founderCosts", shown ? foundingCostRows() : List.of());
    }
    /** The whole founding bill as one line of commodity icons and counts; a short item is marked. */
    private List<Map<String, Object>> foundingCostRows() {
        return SquadFoundingCost.STANDARD.lines().stream().map(line -> {
            String id = "barracks-raise-cost:" + line.commodityId();
            return row("id", id, "iconId", id + ":icon", "labelId", id + ":label",
                    "icon", foundingResources.commodityIcon(line.commodityId()),
                    "label", CompanyResources.number(line.quantity()),
                    "classes", foundingResources.available(line.commodityId()) < line.quantity()
                            ? "raise-cost short" : "raise-cost");
        }).toList();
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
                : plan.cost().isZero() ? Strings.get("openingBarracksCommandEquipmentConforms") : Strings.format("openingBarracksCommandRefitPending", plan.cost().display());
        List<String> notes = new ArrayList<>();
        SquadProfile profile = roster.armory().squadProfile(squad.squadProfileId());
        if (profile != null) notes.add(Strings.format("openingBarracksCommandProfile", roster.armory().weaponDoctrineById(profile.weaponDoctrineId()).displayName(), ArmorPlanPresentation.label(roster.armory(), profile.armorDoctrineId())));
        notes.add(armor == null ? Strings.get("openingBarracksCommandNoIssuedArmorAllBilletsVacant") : Strings.format("openingBarracksCommandArmorImageTheMostCommonCurrentIssue", armor.displayName()));
        notes.add(Strings.format("openingBarracksCommandAssigningAProfileNeverChangesIssuedEquipment", issue));
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
    private List<Map<String, Object>> profiles() {
        return roster.armory().squadProfiles().stream().map(profile -> row(
                "id", profile.id(), "profilenameId", profile.id() + ":name",
                "profiledetailId", profile.id() + ":detail", "name", profile.name(),
                "detail", roster.armory().weaponDoctrineById(profile.weaponDoctrineId()).displayName()
                        + "\n" + ArmorPlanPresentation.label(roster.armory(), profile.armorDoctrineId()),
                "classes", profile.id().equals(profileId) ? "profile-card selected" : "profile-card",
                "select", (Runnable) () -> selectProfile(profile.id()))).toList();
    }
    public void assignCommand(String captainId) {
        var result = roster.assignCaptainToSquads(captainId, selected);
        feedback = result.valid() ? Strings.format("openingBarracksCommandSquadsAssignedTo", result.changedSquadCount(), captainName(roster.byId(captainId))) : result.reason();
        refresh();
    }
    public void assignProfile() {
        boolean applied = roster.assignSquadProfile(selected, profileId);
        feedback = applied ? Strings.format("openingBarracksCommandSquadsAssignedIssueEquipmentSeparatelyWhenReady", selected.size())
                : Strings.get("openingBarracksCommandSelectAProfileAndAvailableSquadsStationed");
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
    private int vacancies() {
        return lineSquads().stream().filter(s -> selected.contains(s.id()) && !s.stationed()).mapToInt(roster::vacancies).sum();
    }
    private String recruitment() {
        int vacancies = vacancies();
        int reserves = Math.min(vacancies, roster.readyReserveCount());
        int wounded = lineSquads().stream().filter(s -> selected.contains(s.id())).mapToInt(s -> muster(s.id()).woundedCount()).sum();
        int shortfall = vacancies - reserves - recruitsRequired();
        String summary = Strings.format("companyResourceRecruitment", vacancies, reserves, recruitsRequired(), wounded);
        return shortfall > 0 ? summary + " " + Strings.format("companyResourceRecruitmentShort", shortfall) : summary;
    }
    private int recruitsRequired() {
        return Math.min(Math.max(0, vacancies() - roster.readyReserveCount()), cargoMarines.getAsInt());
    }
    public SpecSheet recruitSheet() {
        return new SpecSheet(Strings.get("openingBarracksCommandMarinesAvailableRecruits"), Strings.get("openingBarracksCommandUnenlistedPersonnelInFleetCargo"),
                resources.commodityIcon(Commodities.MARINES), "item",
                List.of(SpecSheet.Stat.of(Strings.get("openingBarracksCommandInCargo"), Integer.toString(cargoMarines.getAsInt())),
                        SpecSheet.Stat.of(Strings.get("openingBarracksCommandNamedReserves"), Integer.toString(roster.readyReserveCount())),
                        SpecSheet.Stat.of(Strings.get("openingBarracksCommandWillEnlist"), Integer.toString(recruitsRequired()))),
                List.of(Strings.get("openingBarracksCommandTheMarinesCommodityRepresentsPersonnelOutsideYour"),
                        Strings.get("openingBarracksCommandFillVacanciesTransfersReadyCompanyReservesFirst"),
                        Strings.get("openingBarracksCommandEquipmentIssueIsASeparateOrder")));
    }

    private List<Map<String, Object>> issueCargoStock() {
        revision.get();
        return List.of(row("id", "barracks-recruit-stock", "copyId", "barracks-recruit-stock-copy", "iconId", "barracks-recruit-stock-icon",
                "labelId", "barracks-recruit-stock-count", "nameId", "barracks-recruit-stock-label",
                "name", resources.commodityName(Commodities.MARINES), "icon", resources.commodityIcon(Commodities.MARINES),
                "stock", CompanyResources.number(cargoMarines.getAsInt())),
                row("id", "barracks-reserve-stock", "copyId", "barracks-reserve-copy", "iconId", "barracks-reserve-icon", "labelId", "barracks-reserve-count",
                "nameId", "barracks-reserve-label", "name", Strings.get("openingBarracksCommandNamedReserves"),
                "icon", resources.commodityIcon(Commodities.MARINES), "stock", CompanyResources.number(roster.readyReserveCount())));
    }

    public void reinforceSelected() {
        int reserves = 0;
        int cargo = 0;
        for (MarineSquad squad : lineSquads()) {
            if (!selected.contains(squad.id()) || squad.stationed()) continue;
            PersonnelDrawResult result = reinforce.apply(squad.id());
            reserves += result.reservesAssigned(); cargo += result.cargoMarinesConsumed();
        }
        if (guideVisible() && guideStep == 2 && reserves + cargo > 0) guideStep = 3;
        feedback = Strings.format("openingBarracksCommandFilledBilletsReservesRecruitsEnlistedEquipmentIssue", (reserves + cargo), reserves, cargo);
        refresh();
    }
    public void issueSelected() {
        var result = equipment.apply(selected);
        if (guideVisible() && guideStep == 3 && result.succeeded()) guideStep = 4;
        feedback = result.succeeded() ? Strings.format("openingBarracksCommandIssuedEquipmentToSquadsSkipped", result.issued(), result.skipped())
                : Strings.get("openingBarracksCommandFittingBlockedCheckStationedSquadsAndRequired");
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
        dossier = false; editing = false; organizing = false;
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
        if (guideStep == 4) return localMissions ? "barracks-missions" : "page-nav-return";
        if (organizing || editing) return "barracks-preparation";
        if (guideStep <= 1 || selected.isEmpty()) return guideSquadTarget();
        return guideStep == 2 ? "barracks-reinforce" : "barracks-issue";
    }

    /** Each group becomes one opening; its actual controls are the only input targets. */
    public List<List<String>> guideRegions() {
        if (inductionRequired()) return inductionRegions();
        if (lineSquads().isEmpty() || guideStep == 4 || organizing || editing)
            return List.of(List.of(guideTargetId()));
        var regions = new ArrayList<List<String>>();
        if (guideStep <= 1 || selected.isEmpty()) {
            regions.add(List.of("barracks-selection-tools",
                    "barracks-formation-list", "barracks-page-tools"));
        }
        if (guideStep == 2 && !selected.isEmpty()) {
            regions.add(List.of("barracks-order-caption-82", "barracks-recruitment", "barracks-recruit-draw", "barracks-reinforce"));
            regions.add(List.of("barracks-recruit-stock"));
        }
        if (guideStep == 3 && !selected.isEmpty()) {
            regions.add(List.of("barracks-order-caption-85", "barracks-issue-summary",
                    "barracks-issue-reason", "barracks-issue"));
            regions.add(List.of("barracks-issue-stock"));
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
        if (guideStep == 4) return localMissions ? Strings.get("openingBarracksCommandUseMissionsWhenYouAreReadyFor") : Strings.get("openingBarracksCommandUseReturnWhenYouAreReadyTo");
        if (organizing || editing) return Strings.get("openingBarracksCommandChoosePreparationToReturnToThePersonnel");
        if (selected.isEmpty() || guideStep <= 1) return Strings.get("openingBarracksCommandSelectAReceivingSquadOnTheBoard");
        if (guideStep == 2) return Strings.format("openingBarracksCommandFillVacanciesIfNeededOrNextTo", recruitment().replace("\n", Strings.get("separatorPath")));
        return Strings.get("openingBarracksCommandReviewTheAssignedEquipmentAndSkippedSquad");
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
    public void returnToBriefing(boolean value) { returnToBriefing = value; refresh(); }
    private boolean equipmentUnread() {
        return FirstRefit.available(roster)
                && !roster.hasReviewedCompanyNotice(CompanyNotices.Notice.FIRST_REFIT.id);
    }
    public void equipmentWorkspace(Runnable action) { equipmentWorkspace = action; }
    public void navigation(Consumer<String> editEquipment, Runnable missions, boolean localMissions) {
        this.editEquipment = editEquipment; this.missions = missions;
        this.localMissions = localMissions; refresh();
    }
    private List<SquadWeaponDoctrine> weapons() {
        return roster.armory().weaponDoctrines().stream().filter(w -> roster.armory().canAuthorWeaponDoctrine(w.issues())).toList();
    }
    private SquadWeaponDoctrine draftWeapon() { var list = weapons(); return list.get(Math.floorMod(weaponIndex, list.size())); }
    private SquadArmorDoctrine draftArmor() { var list = roster.armory().armorDoctrines(); return list.get(Math.floorMod(armorIndex, list.size())); }
    public void saveProfile() {
        if (weapons().isEmpty()) return;
        SquadProfile profile = roster.armory().saveSquadProfile(draftName, draftWeapon().id(), draftArmor().id());
        if (profile == null) { feedback = Strings.get("openingBarracksCommandUseANameOfCharactersAndKnown"); refresh(); return; }
        profileId = profile.id(); editing = false;
        feedback = Strings.format("openingBarracksCommandSavedAssignItToSelectedSquadsEquipment", profile.name());
        refresh();
    }
    private static Map<String, Object> row(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
}
