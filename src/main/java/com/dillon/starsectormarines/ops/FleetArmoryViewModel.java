package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.InfantryCombatStats;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.FireTeamTemplateCard;
import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.MarinePersonnelLogistics;
import com.dillon.starsectormarines.marine.MarinePersonnelLogistics.PersonnelDrawResult;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SoldierCareer;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.marine.SquadArmorDoctrine;
import com.dillon.starsectormarines.marine.SquadEquipmentBillet;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadEquipmentPreview;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
import com.dillon.starsectormarines.marine.SquadWeaponDoctrine;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.ComputedSignal;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleSupplier;

/**
 * Retained presentation adapter for one authoritative squad equipment workflow.
 * It owns selection and copy only; every inventory answer and mutation comes from
 * {@link MarineRoster}'s squad-wide preview/apply transaction.
 */
public final class FleetArmoryViewModel {

    private final MarineRoster roster;
    private final Runnable openSelectedSquad;
    private final DoubleSupplier currentDay;
    private final EquipmentIssueResources equipmentIssueResources;
    private final MutableSignal<String> selectedSquadId;
    private final MutableSignal<Integer> selectedTeamIndex;
    private final MutableSignal<String> selectedWeaponDoctrineId;
    private final MutableSignal<String> selectedArmorDoctrineId;
    private final MutableSignal<Integer> domainRevision;
    private final MutableSignal<Feedback> feedback;
    private final ComputedSignal<String> companySummary;
    private final ComputedSignal<List<SquadCard>> squadCards;
    private final ComputedSignal<List<FireTeamOverview>> fireTeamOverviews;
    private final ComputedSignal<List<SelectionRow>> squadRows;
    private final ComputedSignal<List<SelectionRow>> teamRows;
    private final ComputedSignal<List<MarineViewerCard>> marineCards;
    private final ComputedSignal<String> targetSummary;
    private final ComputedSignal<String> candidateSummary;
    private final ComputedSignal<String> applyLabel;
    private final ComputedSignal<String> transactionSummary;
    private final ComputedSignal<String> transactionClasses;
    private final ComputedSignal<Boolean> applyDisabled;
    private final ComputedSignal<String> feedbackText;
    private final ComputedSignal<String> feedbackClasses;
    private final ComputedSignal<String> selectedSquadReadiness;
    private final ComputedSignal<String> reinforceLabel;
    private final ComputedSignal<Boolean> reinforceDisabled;
    private final ComputedSignal<List<DoctrineTile>> weaponDoctrineTiles;
    private final ComputedSignal<List<DoctrineTile>> armorDoctrineTiles;
    private final ComputedSignal<SquadEquipmentPreview> squadEquipmentPreview;
    private final ComputedSignal<String> weaponDoctrineSummary;
    private final ComputedSignal<String> armorDoctrineSummary;

    public FleetArmoryViewModel(Reactor reactor, MarineRoster roster) {
        this(reactor, roster, () -> { }, () -> 0d, EquipmentIssueResources.UNLIMITED);
    }

    public FleetArmoryViewModel(
            Reactor reactor, MarineRoster roster, Runnable openSelectedSquad) {
        this(reactor, roster, openSelectedSquad, () -> 0d,
                EquipmentIssueResources.UNLIMITED);
    }

    public FleetArmoryViewModel(Reactor reactor, MarineRoster roster,
                                Runnable openSelectedSquad, DoubleSupplier currentDay) {
        this(reactor, roster, openSelectedSquad, currentDay,
                EquipmentIssueResources.UNLIMITED);
    }

    public FleetArmoryViewModel(
            Reactor reactor, MarineRoster roster, Runnable openSelectedSquad,
            DoubleSupplier currentDay, EquipmentIssueResources equipmentIssueResources) {
        if (reactor == null) throw new IllegalArgumentException("reactor is required");
        if (roster == null) throw new IllegalArgumentException("roster is required");
        if (openSelectedSquad == null) {
            throw new IllegalArgumentException("openSelectedSquad is required");
        }
        if (currentDay == null) throw new IllegalArgumentException("currentDay is required");
        if (equipmentIssueResources == null) {
            throw new IllegalArgumentException("equipmentIssueResources is required");
        }
        this.roster = roster;
        this.openSelectedSquad = openSelectedSquad;
        this.currentDay = currentDay;
        this.equipmentIssueResources = equipmentIssueResources;

        MarineSquad initialSquad = firstLineSquad(roster);
        selectedSquadId = reactor.signal(initialSquad != null ? initialSquad.id() : null);
        selectedTeamIndex = reactor.signal(0);
        selectedWeaponDoctrineId = reactor.signal(initialSquad != null
                && initialSquad.weaponDoctrineId() != null
                ? initialSquad.weaponDoctrineId()
                : SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS);
        selectedArmorDoctrineId = reactor.signal(initialSquad != null
                && initialSquad.armorDoctrineId() != null
                ? initialSquad.armorDoctrineId()
                : SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);
        domainRevision = reactor.signal(0);
        feedback = reactor.signal(Feedback.neutral(
                "Choose squad Weapon and Armor equipment, inspect each team, then issue when ready."));

        companySummary = reactor.computed(this::buildCompanySummary);
        squadCards = reactor.computed(this::buildSquadCards);
        fireTeamOverviews = reactor.computed(this::buildFireTeamOverviews);
        squadRows = reactor.computed(this::buildSquadRows);
        teamRows = reactor.computed(this::buildTeamRows);
        marineCards = reactor.computed(this::buildMarineCards);
        targetSummary = reactor.computed(this::buildTargetSummary);
        candidateSummary = reactor.computed(this::buildCandidateSummary);
        applyLabel = reactor.computed(this::buildApplyLabel);
        squadEquipmentPreview = reactor.computed(this::buildSquadEquipmentPreview);
        transactionSummary = reactor.computed(this::buildViewerStatus);
        transactionClasses = reactor.computed(this::buildViewerStatusClasses);
        applyDisabled = reactor.computed(() -> !squadEquipmentPreview.get().canApply());
        feedbackText = reactor.computed(() -> feedback.get().text());
        feedbackClasses = reactor.computed(() -> feedback.get().succeeded()
                ? "feedback tone-good" : "feedback tone-muted");
        selectedSquadReadiness = reactor.computed(this::buildSelectedSquadReadiness);
        reinforceLabel = reactor.computed(() -> reinforcementLabel(
                roster.squadById(selectedSquadId.get())));
        reinforceDisabled = reactor.computed(() -> reinforcementCapacity(
                roster.squadById(selectedSquadId.get())) <= 0);
        weaponDoctrineTiles = reactor.computed(this::buildWeaponDoctrineTiles);
        armorDoctrineTiles = reactor.computed(this::buildArmorDoctrineTiles);
        weaponDoctrineSummary = reactor.computed(this::buildWeaponDoctrineSummary);
        armorDoctrineSummary = reactor.computed(this::buildArmorDoctrineSummary);
    }

    public MarineRoster roster() { return roster; }
    public Signal<String> companySummary() { return companySummary; }
    public Signal<List<SquadCard>> squadCards() { return squadCards; }
    public Signal<List<FireTeamOverview>> fireTeamOverviews() { return fireTeamOverviews; }
    public Signal<List<SelectionRow>> squadRows() { return squadRows; }
    public Signal<List<SelectionRow>> teamRows() { return teamRows; }
    public Signal<List<MarineViewerCard>> marineCards() { return marineCards; }
    public Signal<String> targetSummary() { return targetSummary; }
    public Signal<String> candidateSummary() { return candidateSummary; }
    public Signal<String> applyLabel() { return applyLabel; }
    public Signal<String> transactionSummary() { return transactionSummary; }
    public Signal<String> transactionClasses() { return transactionClasses; }
    public Signal<Boolean> applyDisabled() { return applyDisabled; }
    public Signal<String> feedbackText() { return feedbackText; }
    public Signal<String> feedbackClasses() { return feedbackClasses; }
    public Signal<String> selectedSquadReadiness() { return selectedSquadReadiness; }
    public Signal<String> reinforceLabel() { return reinforceLabel; }
    public Signal<Boolean> reinforceDisabled() { return reinforceDisabled; }
    public Signal<List<DoctrineTile>> weaponDoctrineTiles() { return weaponDoctrineTiles; }
    public Signal<List<DoctrineTile>> armorDoctrineTiles() { return armorDoctrineTiles; }
    public Signal<String> weaponDoctrineSummary() { return weaponDoctrineSummary; }
    public Signal<String> armorDoctrineSummary() { return armorDoctrineSummary; }
    public String selectedSquadId() { return selectedSquadId.peek(); }
    public int selectedTeamIndex() { return selectedTeamIndex.peek(); }
    public String selectedWeaponDoctrineId() { return selectedWeaponDoctrineId.peek(); }
    public String selectedArmorDoctrineId() { return selectedArmorDoctrineId.peek(); }
    public String selectedSquadName() {
        MarineSquad squad = roster.squadById(selectedSquadId.peek());
        return squad != null ? squad.name() : "Squad";
    }
    public SquadEquipmentPreview currentSquadEquipmentPreview() {
        return squadEquipmentPreview.get();
    }

    public Runnable applyAction() {
        return () -> applySquadEquipmentSelection();
    }


    public Runnable reinforceSelectedSquadAction() {
        return () -> reinforceSquad(selectedSquadId.peek());
    }

    /** Reprojects campaign time, personnel, and cargo authority. */
    public void refresh() {
        domainRevision.update(value -> value + 1);
    }

    public FireTeamBillet viewerBilletAt(int index) {
        MarineSquad squad = roster.squadById(selectedSquadId.peek());
        if (squad == null) return null;
        SquadEquipmentPreview candidate = squadEquipmentPreview.get();
        int squadBillet = selectedTeamIndex.peek() * MarineSquad.TEAM_SIZE + index;
        if (squadBillet >= 0 && squadBillet < candidate.billets().size()) {
            return asFireTeamBillet(candidate.billet(squadBillet));
        }
        List<String> members = roster.teamMemberIds(squad, selectedTeamIndex.peek());
        if (index < 0 || index >= members.size()) return null;
        MarineSoldier soldier = roster.soldierById(members.get(index));
        return soldier != null ? currentBillet(squad, selectedTeamIndex.peek(), index, soldier)
                : null;
    }

    public SquadEquipmentResult applySquadEquipmentSelection() {
        SquadEquipmentPreview preview = squadEquipmentPreview.get();
        SquadEquipmentResult result = roster.applySquadEquipment(
                selectedSquadId.peek(), selectedWeaponDoctrineId.peek(),
                selectedArmorDoctrineId.peek(), equipmentIssueResources);
        MarineSquad squad = roster.squadById(selectedSquadId.peek());
        feedback.set(result == SquadEquipmentResult.APPLIED
                ? Feedback.success("Squad equipment issued to "
                        + (squad != null ? squad.name() : "selected squad")
                        + (preview.issueCost().isZero() ? "  ·  no cargo required."
                        : "  ·  " + preview.issueCost().display() + " consumed."))
                : Feedback.neutral(squadEquipmentMessage(
                        buildSquadEquipmentPreview())));
        domainRevision.update(value -> value + 1);
        return result;
    }

    private String buildCompanySummary() {
        domainRevision.get();
        int lineSquads = 0;
        int ready = 0;
        int wounded = 0;
        for (MarineSquad squad : roster.squads()) {
            if (squad.reserve()) continue;
            lineSquads++;
            ready += roster.readyCount(squad);
            wounded += woundedCount(squad);
        }
        return lineSquads + " squads  ·  " + ready + " RTD  ·  "
                + wounded + " WIA  ·  "
                + roster.armory().equipmentTemplateCards().size()
                + " equipment templates collected";
    }

    private List<SelectionRow> buildSquadRows() {
        domainRevision.get();
        String selected = selectedSquadId.get();
        List<SelectionRow> rows = new ArrayList<>();
        for (MarineSquad squad : roster.squads()) {
            boolean unavailable = squad.reserve();
            String label = squad.name() + "  ·  " + roster.readyCount(squad) + " / "
                    + MarineSquad.CAPACITY + " RTD"
                    + (squad.stationed() ? "  ·  Stationed" : unavailable ? "  ·  Reserve" : "");
            rows.add(new SelectionRow("squad:" + squad.id(), label,
                    squad.id().equals(selected) ? "selection-row selected" : "selection-row",
                    unavailable, () -> selectSquad(squad.id())));
        }
        return List.copyOf(rows);
    }

    private List<SquadCard> buildSquadCards() {
        domainRevision.get();
        List<SquadCard> cards = new ArrayList<>();
        for (MarineSquad squad : roster.squads()) {
            if (squad.reserve()) continue;
            int ready = roster.readyCount(squad);
            int assigned = squad.weaponDoctrineId() != null && squad.armorDoctrineId() != null
                    ? MarineSquad.TEAMS_PER_SQUAD : 0;
            String readiness = readinessLabel(ready, MarineSquad.CAPACITY);
            String readinessClass = readinessClass(ready, MarineSquad.CAPACITY);
            MarineCaptain captain = roster.captainForSquad(squad.id());
            String command = captain != null
                    ? captain.rank().displayName() + " " + captain.name()
                    : "No officer assigned";
            String location = squad.stationed() ? "Stationed away" : "Aboard fleet";
            String id = "squad-card:" + squad.id();
            cards.add(new SquadCard(id, id + ":name", id + ":status",
                    id + ":strength", id + ":teams", id + ":command",
                    id + ":location", id + ":recovery", id + ":open",
                    id + ":reinforce",
                    "squad-card " + readinessClass,
                    "squad-card-status heading " + readinessTone(ready, MarineSquad.CAPACITY),
                    squad.name(), readiness, ready + " / " + MarineSquad.CAPACITY + " RTD",
                    assigned == MarineSquad.TEAMS_PER_SQUAD
                            ? "Squad doctrine issued" : "Individual equipment",
                    command, location, compactRecoverySummary(squad),
                    reinforcementLabel(squad), reinforcementCapacity(squad) <= 0,
                    () -> {
                        selectSquad(squad.id());
                        openSelectedSquad.run();
                    }, () -> reinforceSquad(squad.id())));
        }
        return List.copyOf(cards);
    }

    private List<FireTeamOverview> buildFireTeamOverviews() {
        domainRevision.get();
        MarineSquad squad = roster.squadById(selectedSquadId.get());
        if (squad == null) return List.of();
        int selected = selectedTeamIndex.get();
        List<FireTeamOverview> teams = new ArrayList<>();
        for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
            int target = team;
            int ready = readyTeamMembers(squad, team);
            String id = "fire-team:" + squad.id() + ":" + team;
            teams.add(new FireTeamOverview(id, id + ":heading-row", id + ":detail-row",
                    id + ":name", id + ":status", id + ":strength",
                    id + ":recovery", id + ":template",
                    team == selected ? "fire-team-overview selected" : "fire-team-overview",
                    "fire-team-status heading " + readinessTone(ready, MarineSquad.TEAM_SIZE),
                    teamName(team), readinessLabel(ready, MarineSquad.TEAM_SIZE),
                    ready + " / " + MarineSquad.TEAM_SIZE + " RTD",
                    recoverySummary(squad, team), assignedDoctrineName(squad),
                    () -> selectTeam(target)));
        }
        return List.copyOf(teams);
    }

    private List<SelectionRow> buildTeamRows() {
        domainRevision.get();
        MarineSquad squad = roster.squadById(selectedSquadId.get());
        if (squad == null) return List.of();
        int selected = selectedTeamIndex.get();
        List<SelectionRow> rows = new ArrayList<>();
        for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
            int target = team;
            String assigned = assignedDoctrineName(squad);
            String label = teamName(team) + "  ·  " + readyTeamMembers(squad, team)
                    + " / " + MarineSquad.TEAM_SIZE + " RTD  ·  " + assigned;
            rows.add(new SelectionRow("team:" + squad.id() + ":" + team, label,
                    team == selected ? "selection-row selected" : "selection-row",
                    false, () -> selectTeam(target)));
        }
        return List.copyOf(rows);
    }

    private SquadEquipmentPreview buildSquadEquipmentPreview() {
        domainRevision.get();
        return roster.previewSquadEquipment(selectedSquadId.get(),
                selectedWeaponDoctrineId.get(), selectedArmorDoctrineId.get(),
                equipmentIssueResources);
    }

    private List<DoctrineTile> buildWeaponDoctrineTiles() {
        domainRevision.get();
        String selected = selectedWeaponDoctrineId.get();
        List<DoctrineTile> tiles = new ArrayList<>();
        for (SquadWeaponDoctrine doctrine : roster.armory().weaponDoctrines()) {
            String id = "weapon-doctrine:" + doctrine.id();
            boolean available = roster.armory().canAuthorWeaponDoctrine(doctrine.issues());
            String distribution = weaponDistribution(doctrine)
                    + (available ? "" : "  ·  Missing template cards");
            tiles.add(new DoctrineTile(id, id + ":name", id + ":description",
                    id + ":distribution",
                    doctrine.id().equals(selected)
                            ? "doctrine-tile selected" + (available ? "" : " locked")
                            : "doctrine-tile" + (available ? "" : " locked"),
                    doctrine.displayName(), doctrine.description(), distribution,
                    () -> selectWeaponDoctrine(doctrine.id())));
        }
        return List.copyOf(tiles);
    }

    private List<DoctrineTile> buildArmorDoctrineTiles() {
        domainRevision.get();
        String selected = selectedArmorDoctrineId.get();
        List<DoctrineTile> tiles = new ArrayList<>();
        for (SquadArmorDoctrine doctrine : roster.armory().armorDoctrines()) {
            String id = "armor-doctrine:" + doctrine.id();
            boolean available = roster.armory().canAuthorArmorDoctrine(doctrine.issues());
            tiles.add(new DoctrineTile(id, id + ":name", id + ":description",
                    id + ":distribution",
                    doctrine.id().equals(selected)
                            ? "doctrine-tile selected" + (available ? "" : " locked")
                            : "doctrine-tile" + (available ? "" : " locked"),
                    doctrine.displayName(), doctrine.description(), armorDistribution(doctrine)
                            + (available ? "" : "  ·  Missing template cards"),
                    () -> selectArmorDoctrine(doctrine.id())));
        }
        return List.copyOf(tiles);
    }

    private String buildWeaponDoctrineSummary() {
        SquadWeaponDoctrine doctrine = roster.armory().weaponDoctrineById(
                selectedWeaponDoctrineId.get());
        return doctrine != null
                ? doctrine.displayName() + "  ·  " + weaponDistribution(doctrine)
                : "Choose weapon equipment";
    }

    private String buildArmorDoctrineSummary() {
        SquadArmorDoctrine doctrine = roster.armory().armorDoctrineById(
                selectedArmorDoctrineId.get());
        return doctrine != null
                ? doctrine.displayName() + "  ·  " + armorDistribution(doctrine)
                : "Choose armor equipment";
    }

    private List<MarineViewerCard> buildMarineCards() {
        domainRevision.get();
        MarineSquad squad = roster.squadById(selectedSquadId.get());
        int teamIndex = selectedTeamIndex.get();
        SquadEquipmentPreview candidate = squadEquipmentPreview.get();
        boolean previewing = candidate.billets().size() == MarineSquad.CAPACITY;
        if (squad == null) return List.of();
        List<String> memberIds = roster.teamMemberIds(squad, teamIndex);
        List<MarineViewerCard> marines = new ArrayList<>();
        for (int index = 0; index < MarineSquad.TEAM_SIZE; index++) {
            MarineSoldier soldier = index < memberIds.size()
                    ? roster.soldierById(memberIds.get(index)) : null;
            int squadBillet = teamIndex * MarineSquad.TEAM_SIZE + index;
            FireTeamBillet billet = previewing ? asFireTeamBillet(candidate.billet(squadBillet))
                    : soldier != null ? currentBillet(squad, teamIndex, index, soldier) : null;
            String id = "marine-card:" + index;
            SpecialEquipmentDef special = billet != null
                    ? SpecialEquipmentRegistry.get(billet.specialEquipmentId()) : null;
            MarineArmorCatalogDef armorCatalog = billet != null
                    ? MarineArmorCatalogRegistry.require(billet.armor().id) : null;
            marines.add(new MarineViewerCard(
                    id, "marine-preview:" + index, id + ":header",
                    id + ":hero", id + ":identity",
                    id + ":badges", id + ":class-badge", id + ":weapon-badge",
                    id + ":armor-badge", id + ":name", id + ":role",
                    id + ":status", id + ":service", id + ":personnel",
                    id + ":primary", id + ":primary-description",
                    id + ":weapon-detail", id + ":weapon-stats",
                    id + ":armor", id + ":armor-description", id + ":armor-detail",
                    id + ":armor-stats",
                    id + ":special",
                    id + ":special-description", id + ":weapon-delta", id + ":armor-delta",
                    id + ":career",
                    marineCardClasses(soldier, previewing),
                    "marine-status heading " + marineStatusTone(soldier),
                    marineName(soldier), billet != null ? billet.name() : "Unfilled billet",
                    marineStatus(soldier), serviceSummary(soldier),
                    personnelSummary(soldier),
                    armorCatalog != null ? armorCatalog.unitClass() : "VACANT",
                    billet != null ? "W " + billet.grade().tierMark() : "W —",
                    billet != null ? "A " + billet.armor().tierMark() : "A —",
                    billet != null ? billet.primary().catalogName(billet.grade()) + "  ·  "
                            + billet.grade().displayName : "No primary",
                    billet != null ? billet.primary().catalogDescription() : "",
                    weaponStats(id, billet, soldier),
                    billet != null ? billet.armor().displayName + "  ·  Tier "
                            + billet.armor().tierMark() : "No armor",
                    armorStats(id, billet), armorCatalog != null ? armorCatalog.description() : "",
                    special != null ? special.displayName() : "No specialty equipment",
                    special != null ? special.catalogDescription()
                            : "This billet carries no specialty equipment beyond its primary weapon.",
                    weaponDelta(billet, soldier, previewing),
                    armorDelta(billet, soldier, previewing),
                    previewing ? "marine-delta label tone-accent"
                            : "marine-delta label picker-closed-line",
                    careerSummary(soldier)));
        }
        return List.copyOf(marines);
    }

    private String buildTargetSummary() {
        domainRevision.get();
        MarineSquad squad = roster.squadById(selectedSquadId.get());
        int team = selectedTeamIndex.get();
        return squad == null ? "Select a line squad"
                : squad.name() + "  ·  12-billet squad issue  ·  Inspecting "
                + teamName(team);
    }

    private String buildCandidateSummary() {
        domainRevision.get();
        SquadWeaponDoctrine weapons = roster.armory().weaponDoctrineById(
                selectedWeaponDoctrineId.get());
        SquadArmorDoctrine armor = roster.armory().armorDoctrineById(
                selectedArmorDoctrineId.get());
        return "Proposed  ·  "
                + (weapons != null ? weapons.displayName() : "Choose weapons")
                + "  /  " + (armor != null ? armor.displayName() : "Choose armor");
    }

    private String buildApplyLabel() {
        SquadEquipmentPreview preview = squadEquipmentPreview.get();
        return preview.issueCost().isZero()
                ? "Issue Equipment to Entire Squad"
                : "Issue Squad  ·  " + preview.issueCost().display();
    }

    private String buildViewerStatus() {
        return squadEquipmentMessage(squadEquipmentPreview.get());
    }

    private String buildViewerStatusClasses() {
        return squadEquipmentPreview.get().canApply()
                ? "transaction-result good-surface tone-good"
                : "transaction-result danger-surface tone-danger";
    }

    private void selectSquad(String squadId) {
        MarineSquad squad = roster.squadById(squadId);
        if (squad == null || squad.reserve()) return;
        selectedSquadId.set(squadId);
        selectedTeamIndex.set(0);
        selectedWeaponDoctrineId.set(squad.weaponDoctrineId() != null
                ? squad.weaponDoctrineId() : SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS);
        selectedArmorDoctrineId.set(squad.armorDoctrineId() != null
                ? squad.armorDoctrineId() : SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);
    }

    private void selectTeam(int teamIndex) {
        selectedTeamIndex.set(teamIndex);
        feedback.set(Feedback.neutral(
                "Inspecting this fire team within the same squad equipment preview."));
    }

    private void selectWeaponDoctrine(String doctrineId) {
        if (roster.armory().weaponDoctrineById(doctrineId) == null) return;
        selectedWeaponDoctrineId.set(doctrineId);
        feedback.set(Feedback.neutral(
                "Weapon equipment selected. Special equipment follows this definition."));
    }

    private void selectArmorDoctrine(String doctrineId) {
        if (roster.armory().armorDoctrineById(doctrineId) == null) return;
        selectedArmorDoctrineId.set(doctrineId);
        feedback.set(Feedback.neutral("Armor equipment selected for all twelve billets."));
    }

    private void reinforceSquad(String squadId) {
        MarineSquad squad = roster.squadById(squadId);
        if (squad == null) return;
        PersonnelDrawResult result = MarinePersonnelLogistics.reinforceSquad(roster, squadId);
        if (result.total() <= 0) {
            feedback.set(Feedback.neutral(reinforcementUnavailableReason(squad)));
        } else {
            String source = result.reservesAssigned() > 0 && result.cargoMarinesConsumed() > 0
                    ? result.reservesAssigned() + " reserve, "
                            + result.cargoMarinesConsumed() + " cargo consumed"
                    : result.reservesAssigned() > 0
                            ? result.reservesAssigned() + " from reserve"
                            : result.cargoMarinesConsumed() + " cargo consumed";
            feedback.set(Feedback.success(squad.name() + " reinforced  ·  " + source
                    + ". Review replacement equipment before deployment."));
        }
        domainRevision.update(value -> value + 1);
    }

    private String buildSelectedSquadReadiness() {
        domainRevision.get();
        MarineSquad squad = roster.squadById(selectedSquadId.get());
        if (squad == null) return "Select a line squad";
        int vacancies = roster.vacancies(squad);
        return roster.readyCount(squad) + " RTD  ·  " + vacancies
                + (vacancies == 1 ? " open billet  ·  " : " open billets  ·  ")
                + recoverySummary(squad);
    }

    private int reinforcementCapacity(MarineSquad squad) {
        domainRevision.get();
        if (squad == null || squad.stationed()) return 0;
        int personnel = roster.readyReserveCount()
                + MarinePersonnelLogistics.availableCargoMarines();
        return Math.min(roster.vacancies(squad), personnel);
    }

    private String reinforcementLabel(MarineSquad squad) {
        int capacity = reinforcementCapacity(squad);
        return capacity > 0 ? "Reinforce +" + capacity : "Reinforce";
    }

    private String reinforcementUnavailableReason(MarineSquad squad) {
        if (squad.stationed()) return "Unavailable  ·  This squad is stationed away.";
        if (roster.vacancies(squad) <= 0 && woundedCount(squad) > 0) {
            return "No open billets  ·  WIA marines remain assigned while recovering.";
        }
        if (roster.vacancies(squad) <= 0) return "This squad is fully manned.";
        return "No ready reserve or cargo marines are available.";
    }

    private String assignedTemplateName(MarineSquad squad, int teamIndex) {
        FireTeamTemplateCard card = roster.armory().templateCardById(
                squad.teamTemplateCardId(teamIndex));
        return card != null ? card.displayName() : "Unassigned";
    }

    private String assignedDoctrineName(MarineSquad squad) {
        SquadWeaponDoctrine weapons = roster.armory().weaponDoctrineById(
                squad.weaponDoctrineId());
        SquadArmorDoctrine armor = roster.armory().armorDoctrineById(
                squad.armorDoctrineId());
        if (weapons == null || armor == null) return "Individual equipment";
        return weapons.displayName() + "  /  " + armor.displayName();
    }

    private static String weaponDistribution(SquadWeaponDoctrine doctrine) {
        List<String> parts = new ArrayList<>();
        for (MarineWeapon weapon : MarineWeapon.values()) {
            int count = 0;
            for (var issue : doctrine.issues()) if (issue.primary() == weapon) count++;
            if (count > 0) parts.add(count + " " + weapon.displayName());
        }
        int specials = 0;
        for (var issue : doctrine.issues()) if (issue.specialEquipmentId() != null) specials++;
        if (specials > 0) parts.add(specials + " special");
        return String.join("  ·  ", parts);
    }

    private static String armorDistribution(SquadArmorDoctrine doctrine) {
        List<String> parts = new ArrayList<>();
        for (MarineArmorPattern pattern : MarineArmorPattern.values()) {
            int count = 0;
            for (MarineArmorPattern issue : doctrine.issues()) if (issue == pattern) count++;
            if (count > 0) parts.add(count + " " + pattern.displayName);
        }
        return String.join("  ·  ", parts);
    }

    private int readyTeamMembers(MarineSquad squad, int teamIndex) {
        int ready = 0;
        for (String memberId : roster.teamMemberIds(squad, teamIndex)) {
            MarineSoldier soldier = roster.soldierById(memberId);
            if (soldier != null && soldier.status() == MarineSoldierStatus.ACTIVE) ready++;
        }
        return ready;
    }

    private int woundedCount(MarineSquad squad) {
        int wounded = 0;
        for (MarineSoldier soldier : roster.squadMembers(squad)) {
            if (soldier.status() == MarineSoldierStatus.WIA) wounded++;
        }
        return wounded;
    }

    private String recoverySummary(MarineSquad squad) {
        return recoverySummary(roster.teamMemberIds(squad, 0),
                roster.teamMemberIds(squad, 1), roster.teamMemberIds(squad, 2));
    }

    private String recoverySummary(MarineSquad squad, int teamIndex) {
        return recoverySummary(roster.teamMemberIds(squad, teamIndex));
    }

    private String compactRecoverySummary(MarineSquad squad) {
        int wounded = 0;
        float earliest = Float.POSITIVE_INFINITY;
        for (String id : roster.manningMemberIds(squad)) {
            MarineSoldier soldier = roster.soldierById(id);
            if (soldier == null || soldier.status() != MarineSoldierStatus.WIA) continue;
            wounded++;
            earliest = Math.min(earliest, soldier.unavailableUntilDay());
        }
        return wounded == 0 ? "No WIA" : wounded + " WIA  ·  RTD "
                + formatRemainingCompact(earliest, currentDay.getAsDouble());
    }

    @SafeVarargs
    private final String recoverySummary(List<String>... groups) {
        int wounded = 0;
        float earliest = Float.POSITIVE_INFINITY;
        for (List<String> group : groups) {
            for (String id : group) {
                MarineSoldier soldier = roster.soldierById(id);
                if (soldier == null || soldier.status() != MarineSoldierStatus.WIA) continue;
                wounded++;
                earliest = Math.min(earliest, soldier.unavailableUntilDay());
            }
        }
        return wounded == 0 ? "No wounded personnel"
                : wounded + " WIA  ·  next RTD " + formatRemaining(earliest, currentDay.getAsDouble());
    }

    static String formatRemaining(double unavailableUntilDay, double currentDay) {
        int hours = Math.max(0, (int) Math.ceil((unavailableUntilDay - currentDay) * 24d));
        if (hours <= 0) return "now";
        if (hours < 24) return "in " + hours + "h";
        int days = hours / 24;
        int remainder = hours % 24;
        return "in " + days + "d" + (remainder == 0 ? "" : " " + remainder + "h");
    }

    static String formatRemainingCompact(double unavailableUntilDay, double currentDay) {
        String remaining = formatRemaining(unavailableUntilDay, currentDay);
        return remaining.startsWith("in ") ? remaining.substring(3) : remaining;
    }

    private static MarineSquad firstLineSquad(MarineRoster roster) {
        for (MarineSquad squad : roster.squads()) if (!squad.reserve()) return squad;
        return null;
    }

    private static String teamName(int teamIndex) {
        return switch (teamIndex) {
            case 0 -> "Alpha";
            case 1 -> "Bravo";
            case 2 -> "Charlie";
            default -> "Team";
        };
    }

    private static String readinessLabel(int ready, int capacity) {
        if (ready >= capacity) return "READY";
        if (ready * 4 >= capacity * 3) return "OPERATIONAL";
        if (ready > 0) return "RECONSTITUTING";
        return "STANDING DOWN";
    }

    private static String readinessClass(int ready, int capacity) {
        if (ready >= capacity) return "company-card-ready";
        if (ready * 4 >= capacity * 3) return "company-card-operational";
        return "company-card-unready";
    }

    private static String readinessTone(int ready, int capacity) {
        if (ready >= capacity) return "tone-good";
        if (ready * 4 >= capacity * 3) return "tone-accent";
        return "tone-danger";
    }

    private FireTeamBillet currentBillet(
            MarineSquad squad, int teamIndex, int billetIndex, MarineSoldier soldier) {
        FireTeamTemplateCard assigned = roster.armory().templateCardById(
                squad.teamTemplateCardId(teamIndex));
        String role = assigned != null && billetIndex < assigned.billets().size()
                ? assigned.billet(billetIndex).name()
                : billetIndex == 0 ? "Team Leader" : "Rifleman";
        return new FireTeamBillet(role, soldier.primary(), soldier.primaryGrade(),
                soldier.secondary(), soldier.armor());
    }

    private static FireTeamBillet asFireTeamBillet(SquadEquipmentBillet billet) {
        return billet != null ? new FireTeamBillet(
                billet.role(), billet.primary(), billet.grade(),
                billet.special(), billet.armor()) : null;
    }

    private static String marineCardClasses(MarineSoldier soldier, boolean previewing) {
        String classes = "marine-viewer-card " + (previewing ? "previewing" : "viewer-only");
        return soldier != null ? classes : classes + " vacant";
    }

    private static String marineName(MarineSoldier soldier) {
        return soldier != null
                ? soldier.enlistedRank().abbreviation() + " " + soldier.name()
                : "Vacant billet";
    }

    private String marineStatus(MarineSoldier soldier) {
        if (soldier == null) return "VACANT";
        return switch (soldier.status()) {
            case ACTIVE -> "READY";
            case WIA -> "WIA  ·  RTD " + formatRemaining(
                    soldier.unavailableUntilDay(), currentDay.getAsDouble());
            case MIA -> "MISSING";
            case KIA -> "KILLED";
        };
    }

    private static String marineStatusTone(MarineSoldier soldier) {
        return soldier != null && soldier.status() == MarineSoldierStatus.ACTIVE
                ? "tone-good" : "tone-danger";
    }

    private static String serviceSummary(MarineSoldier soldier) {
        if (soldier == null) return "No marine assigned";
        return soldier.profile().experienceTier().displayName + "  ·  "
                + soldier.aptitude().displayName + " aptitude  ·  "
                + soldier.experienceXp() + " XP";
    }

    private static String personnelSummary(MarineSoldier soldier) {
        if (soldier == null) return "No personnel record is attached to this vacant billet.";
        String experience = soldier.profile().experienceTier().displayName
                .toLowerCase(Locale.ROOT);
        String aptitude = soldier.aptitude().displayName.toLowerCase(Locale.ROOT);
        SoldierCareer career = soldier.career();
        String record = career.missionsDeployed() == 0
                ? "No combat deployments are recorded yet."
                : career.missionsDeployed() + " deployments, " + career.kills()
                + " confirmed kills, and " + career.timesWounded() + " wounds are on file.";
        return "A " + experience + " marine assessed with " + aptitude
                + " aptitude. " + record;
    }

    private static List<StatMeter> weaponStats(
            String cardId, FireTeamBillet billet, MarineSoldier soldier) {
        if (billet == null || soldier == null) return List.of();
        SoldierProfile profile = soldier.profile();
        float damage = InfantryCombatStats.damage(billet.primary(), billet.grade());
        float range = InfantryCombatStats.range(billet.primary(), billet.grade());
        float accuracy = InfantryCombatStats.accuracy(
                billet.primary(), billet.grade(), profile);
        float dps = InfantryCombatStats.estimatedDps(
                billet.primary(), billet.grade(), profile);
        return List.of(
                statMeter(cardId + ":damage", "DMG", formatOneDecimal(damage),
                        damage, maximumWeaponDamage()),
                statMeter(cardId + ":range", "RNG", formatOneDecimal(range),
                        range, maximumWeaponRange()),
                statMeter(cardId + ":accuracy", "ACC",
                        String.format(Locale.ROOT, "%.0f%%", accuracy * 100f),
                        accuracy, 1f),
                statMeter(cardId + ":dps", "DPS", formatOneDecimal(dps),
                        dps, maximumWeaponDps(profile)));
    }

    private static String weaponDelta(
            FireTeamBillet billet, MarineSoldier soldier, boolean previewing) {
        if (!previewing || billet == null || soldier == null) return "";
        SoldierProfile profile = soldier.profile();
        float damage = InfantryCombatStats.damage(billet.primary(), billet.grade())
                - InfantryCombatStats.damage(soldier.primary(), soldier.primaryGrade());
        float range = InfantryCombatStats.range(billet.primary(), billet.grade())
                - InfantryCombatStats.range(soldier.primary(), soldier.primaryGrade());
        float accuracy = (InfantryCombatStats.accuracy(
                billet.primary(), billet.grade(), profile)
                - InfantryCombatStats.accuracy(
                soldier.primary(), soldier.primaryGrade(), profile)) * 100f;
        float dps = InfantryCombatStats.estimatedDps(billet.primary(), billet.grade(), profile)
                - InfantryCombatStats.estimatedDps(
                soldier.primary(), soldier.primaryGrade(), profile);
        return String.format(Locale.ROOT,
                "DMG %+.1f  ·  RNG %+.0f  ·  ACC %+.0f%%  ·  DPS %+.1f",
                damage, range, accuracy, dps);
    }

    private static List<StatMeter> armorStats(String cardId, FireTeamBillet billet) {
        if (billet == null) return List.of();
        MarineArmorPattern armor = billet.armor();
        return List.of(
                statMeter(cardId + ":health", "HEALTH",
                        String.format(Locale.ROOT, "%.0f", UnitType.MARINE.maxHp),
                        UnitType.MARINE.maxHp, UnitType.MARINE.maxHp),
                statMeter(cardId + ":armor-value", "ARMOR",
                        String.format(Locale.ROOT, "%.0f", armor.armorPool),
                        armor.armorPool, maximumArmorPool()),
                statMeter(cardId + ":resist", "RESIST",
                        String.format(Locale.ROOT, "%.0f", armor.armorRating),
                        armor.armorRating, maximumArmorRating()),
                statMeter(cardId + ":speed", "SPEED",
                        String.format(Locale.ROOT, "%.1f",
                                UnitType.MARINE.moveSpeed * armor.moveSpeedMult),
                        UnitType.MARINE.moveSpeed * armor.moveSpeedMult,
                        UnitType.MARINE.moveSpeed * maximumMoveSpeed()));
    }

    private static StatMeter statMeter(
            String id, String label, String value, float amount, float maximum) {
        int percentage = maximum > 0f
                ? Math.round(Math.max(0f, Math.min(1f, amount / maximum)) * 100f) : 0;
        return new StatMeter(id, id + ":label", id + ":track", id + ":fill",
                id + ":value", label, value, "width: " + percentage + "%;");
    }

    private static String formatOneDecimal(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static float maximumWeaponDamage() {
        float maximum = 1f;
        for (MarineWeapon weapon : MarineWeapon.values()) {
            for (EquipmentGrade grade : EquipmentGrade.values()) {
                maximum = Math.max(maximum, InfantryCombatStats.damage(weapon, grade));
            }
        }
        return maximum;
    }

    private static float maximumWeaponRange() {
        float maximum = 1f;
        for (MarineWeapon weapon : MarineWeapon.values()) {
            for (EquipmentGrade grade : EquipmentGrade.values()) {
                maximum = Math.max(maximum, InfantryCombatStats.range(weapon, grade));
            }
        }
        return maximum;
    }

    private static float maximumWeaponDps(SoldierProfile profile) {
        float maximum = 1f;
        for (MarineWeapon weapon : MarineWeapon.values()) {
            for (EquipmentGrade grade : EquipmentGrade.values()) {
                maximum = Math.max(maximum,
                        InfantryCombatStats.estimatedDps(weapon, grade, profile));
            }
        }
        return maximum;
    }

    private static float maximumArmorPool() {
        float maximum = 1f;
        for (MarineArmorPattern armor : MarineArmorPattern.values()) {
            maximum = Math.max(maximum, armor.armorPool);
        }
        return maximum;
    }

    private static float maximumArmorRating() {
        float maximum = 1f;
        for (MarineArmorPattern armor : MarineArmorPattern.values()) {
            maximum = Math.max(maximum, armor.armorRating);
        }
        return maximum;
    }

    private static float maximumMoveSpeed() {
        float maximum = 1f;
        for (MarineArmorPattern armor : MarineArmorPattern.values()) {
            maximum = Math.max(maximum, armor.moveSpeedMult);
        }
        return maximum;
    }

    private static String armorDelta(
            FireTeamBillet billet, MarineSoldier soldier, boolean previewing) {
        if (!previewing || billet == null || soldier == null) return "";
        return String.format(Locale.ROOT,
                "ARMOR %+.0f  ·  RESIST %+.0f  ·  SPEED %+.1f",
                billet.armor().armorPool - soldier.armor().armorPool,
                billet.armor().armorRating - soldier.armor().armorRating,
                UnitType.MARINE.moveSpeed
                        * (billet.armor().moveSpeedMult - soldier.armor().moveSpeedMult));
    }

    private static String careerSummary(MarineSoldier soldier) {
        if (soldier == null) return "Awaiting assignment";
        SoldierCareer career = soldier.career();
        if (career.missionsDeployed() == 0) return "No deployments yet";
        return career.missionsDeployed() + " deployments  ·  " + career.kills()
                + " kills  ·  wounded " + career.timesWounded() + " times";
    }

    private static String squadEquipmentMessage(SquadEquipmentPreview preview) {
        return switch (preview.result()) {
            case APPLIED -> preview.issueCost().isZero()
                    ? "Ready  ·  The squad already matches this equipment issue."
                    : "Ready  ·  Issue cost: " + preview.issueCost().display() + ".";
            case INVALID_SQUAD -> "Select a line squad.";
            case SQUAD_NOT_READY -> "Not ready  ·  This squad needs twelve RTD marines.";
            case STATIONED -> "Unavailable  ·  This squad is stationed away.";
            case UNKNOWN_WEAPON_DOCTRINE -> "Choose weapon equipment.";
            case UNKNOWN_ARMOR_DOCTRINE -> "Choose armor equipment.";
            case MISSING_TEMPLATE ->
                    "Blocked  ·  One or more required equipment template cards are missing.";
            case INSUFFICIENT_CARGO -> "Blocked  ·  Requires "
                    + preview.issueCost().display() + "; available: "
                    + preview.availableCargo().display() + ".";
        };
    }

    public record DoctrineTile(
            String id, String nameId, String descriptionId, String distributionId,
            String classes, String name, String description, String distribution,
            Runnable select) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "nameId" -> nameId;
                case "descriptionId" -> descriptionId;
                case "distributionId" -> distributionId;
                case "classes" -> classes;
                case "name" -> name;
                case "description" -> description;
                case "distribution" -> distribution;
                case "select" -> select;
                default -> throw new IllegalArgumentException("Unknown doctrine-tile property");
            };
        }
    }

    public record SelectionRow(String id, String label, String classes,
                               boolean disabled, Runnable select) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String name) {
            return switch (name) {
                case "id" -> id;
                case "label" -> label;
                case "classes" -> classes;
                case "disabled" -> disabled;
                case "select" -> select;
                default -> throw new IllegalArgumentException("Unknown selection-row property");
            };
        }
    }

    public record SquadCard(
            String id, String nameId, String statusId, String strengthId,
            String teamsId, String commandId, String locationId, String recoveryId,
            String openId, String reinforceId,
            String classes, String statusClasses, String name, String status,
            String strength, String teams, String command, String location,
            String recovery, String reinforceLabel,
            boolean reinforceDisabled, Runnable open, Runnable reinforce)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "nameId" -> nameId;
                case "statusId" -> statusId;
                case "strengthId" -> strengthId;
                case "teamsId" -> teamsId;
                case "commandId" -> commandId;
                case "locationId" -> locationId;
                case "recoveryId" -> recoveryId;
                case "openId" -> openId;
                case "reinforceId" -> reinforceId;
                case "classes" -> classes;
                case "statusClasses" -> statusClasses;
                case "name" -> name;
                case "status" -> status;
                case "strength" -> strength;
                case "teams" -> teams;
                case "command" -> command;
                case "location" -> location;
                case "recovery" -> recovery;
                case "reinforceLabel" -> reinforceLabel;
                case "reinforceDisabled" -> reinforceDisabled;
                case "open" -> open;
                case "reinforce" -> reinforce;
                default -> throw new IllegalArgumentException("Unknown squad-card property");
            };
        }
    }

    public record FireTeamOverview(
            String id, String headingRowId, String detailRowId,
            String nameId, String statusId, String strengthId,
            String recoveryId, String templateId, String classes, String statusClasses,
            String name, String status, String strength, String recovery, String template,
            Runnable select) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "headingRowId" -> headingRowId;
                case "detailRowId" -> detailRowId;
                case "nameId" -> nameId;
                case "statusId" -> statusId;
                case "strengthId" -> strengthId;
                case "recoveryId" -> recoveryId;
                case "templateId" -> templateId;
                case "classes" -> classes;
                case "statusClasses" -> statusClasses;
                case "name" -> name;
                case "status" -> status;
                case "strength" -> strength;
                case "recovery" -> recovery;
                case "template" -> template;
                case "select" -> select;
                default -> throw new IllegalArgumentException("Unknown fire-team property");
            };
        }
    }

    public record MarineViewerCard(
            String id, String canvasId, String headerId, String heroId, String identityId,
            String badgesId, String classBadgeId, String weaponBadgeId,
            String armorBadgeId, String nameId, String roleId,
            String statusId, String serviceId, String personnelId, String primaryId,
            String primaryDescriptionId, String weaponDetailId, String weaponStatsId,
            String armorId, String armorDescriptionId, String armorDetailId,
            String armorStatsId, String specialId, String specialDescriptionId,
            String weaponDeltaId, String armorDeltaId,
            String careerId, String classes, String statusClasses,
            String name, String role, String status, String service, String personnel,
            String unitClass, String weaponBadge, String armorBadge,
            String primary, String primaryDescription, List<StatMeter> weaponStats,
            String armor, List<StatMeter> armorStats, String armorDescription,
            String special, String specialDescription, String weaponDelta, String armorDelta,
            String deltaClasses, String career) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "canvasId" -> canvasId;
                case "headerId" -> headerId;
                case "heroId" -> heroId;
                case "identityId" -> identityId;
                case "badgesId" -> badgesId;
                case "classBadgeId" -> classBadgeId;
                case "weaponBadgeId" -> weaponBadgeId;
                case "armorBadgeId" -> armorBadgeId;
                case "nameId" -> nameId;
                case "roleId" -> roleId;
                case "statusId" -> statusId;
                case "serviceId" -> serviceId;
                case "personnelId" -> personnelId;
                case "primaryId" -> primaryId;
                case "primaryDescriptionId" -> primaryDescriptionId;
                case "weaponDetailId" -> weaponDetailId;
                case "weaponStatsId" -> weaponStatsId;
                case "armorId" -> armorId;
                case "armorDescriptionId" -> armorDescriptionId;
                case "armorDetailId" -> armorDetailId;
                case "armorStatsId" -> armorStatsId;
                case "specialId" -> specialId;
                case "specialDescriptionId" -> specialDescriptionId;
                case "weaponDeltaId" -> weaponDeltaId;
                case "armorDeltaId" -> armorDeltaId;
                case "careerId" -> careerId;
                case "classes" -> classes;
                case "statusClasses" -> statusClasses;
                case "name" -> name;
                case "role" -> role;
                case "status" -> status;
                case "service" -> service;
                case "personnel" -> personnel;
                case "unitClass" -> unitClass;
                case "weaponBadge" -> weaponBadge;
                case "armorBadge" -> armorBadge;
                case "primary" -> primary;
                case "primaryDescription" -> primaryDescription;
                case "weaponStats" -> weaponStats;
                case "armor" -> armor;
                case "armorStats" -> armorStats;
                case "armorDescription" -> armorDescription;
                case "special" -> special;
                case "specialDescription" -> specialDescription;
                case "weaponDelta" -> weaponDelta;
                case "armorDelta" -> armorDelta;
                case "deltaClasses" -> deltaClasses;
                case "career" -> career;
                default -> throw new IllegalArgumentException("Unknown marine-card property");
            };
        }
    }

    public record StatMeter(
            String id, String labelId, String trackId, String fillId, String valueId,
            String label, String value, String fillStyle)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "labelId" -> labelId;
                case "trackId" -> trackId;
                case "fillId" -> fillId;
                case "valueId" -> valueId;
                case "label" -> label;
                case "value" -> value;
                case "fillStyle" -> fillStyle;
                default -> throw new IllegalArgumentException("Unknown stat-meter property");
            };
        }
    }

    private record Feedback(String text, boolean succeeded) {
        private static Feedback neutral(String text) { return new Feedback(text, false); }
        private static Feedback success(String text) { return new Feedback(text, true); }
    }
}
