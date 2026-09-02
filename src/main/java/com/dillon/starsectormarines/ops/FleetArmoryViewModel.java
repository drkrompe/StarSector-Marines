package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.InfantryCombatStats;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.FireTeamTemplateCard;
import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.EquipmentTemplateCost;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.ArmorRole;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.LoadoutEffectiveness;
import com.dillon.starsectormarines.marine.MarinePersonnelLogistics;
import com.dillon.starsectormarines.marine.MarinePersonnelLogistics.PersonnelDrawResult;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SoldierCareer;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.marine.SquadArmorPlan;
import com.dillon.starsectormarines.marine.SquadArmorDoctrine;
import com.dillon.starsectormarines.marine.SquadEquipmentBillet;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadEquipmentPreview;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
import com.dillon.starsectormarines.marine.SquadLoadoutPresentationDef;
import com.dillon.starsectormarines.marine.SquadLoadoutPresentationRegistry;
import com.dillon.starsectormarines.marine.SquadLoadoutRarity;
import com.dillon.starsectormarines.marine.SquadFoundingCost;
import com.dillon.starsectormarines.marine.SquadFoundingResources;
import com.dillon.starsectormarines.marine.SquadFoundingWorkshop;
import com.dillon.starsectormarines.marine.SquadWeaponDoctrine;
import com.dillon.starsectormarines.ops.spec.CatalogCeilings;
import com.dillon.starsectormarines.ops.spec.IntegralSystemCopy;
import com.dillon.starsectormarines.ops.spec.StatMeter;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.ComputedSignal;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * Retained presentation adapter for one authoritative squad equipment workflow.
 * It owns selection and copy only; every inventory answer and mutation comes from
 * {@link MarineRoster}'s squad-wide preview/apply transaction.
 */
public final class FleetArmoryViewModel {

    public enum EquipmentPickerKind {
        WEAPON,
        ARMOR
    }

    public enum LoadoutFilter {
        ALL("All"),
        COMMON("Common"),
        UNCOMMON("Uncommon"),
        RARE("Rare"),
        EXOTIC("Exotic");

        private final String label;

        LoadoutFilter(String label) {
            this.label = label;
        }

        private boolean accepts(SquadLoadoutRarity rarity) {
            return this == ALL || name().equals(rarity.name());
        }
    }

    private final MarineRoster roster;
    private final Runnable openSelectedSquad;
    private final DoubleSupplier currentDay;
    private final EquipmentIssueResources equipmentIssueResources;
    private final SquadFoundingResources squadFoundingResources;
    private final SquadFoundingWorkshop squadFoundingWorkshop;
    private final MutableSignal<String> selectedSquadId;
    private final MutableSignal<Integer> selectedTeamIndex;
    private final MutableSignal<String> selectedWeaponDoctrineId;
    private final MutableSignal<String> selectedArmorDoctrineId;
    private final MutableSignal<EquipmentPickerKind> equipmentPickerKind;
    private final MutableSignal<LoadoutFilter> loadoutFilter;
    private final MutableSignal<Boolean> issuableOnly;
    private final MutableSignal<Integer> domainRevision;
    private final MutableSignal<Feedback> feedback;
    private final ComputedSignal<String> companySummary;
    private final ComputedSignal<List<SquadCard>> squadCards;
    private final ComputedSignal<List<SquadGalleryCard>> squadGalleryCards;
    private final ComputedSignal<List<CargoCostRow>> foundingCargoRows;
    private final ComputedSignal<Boolean> foundingDisabled;
    private final MutableSignal<Feedback> foundingFeedback;
    private final ComputedSignal<String> foundingFeedbackText;
    private final ComputedSignal<String> foundingFeedbackClasses;
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
    private final ComputedSignal<List<CargoCostRow>> issueCargoRows;
    private final ComputedSignal<String> issueCargoClasses;
    private final ComputedSignal<String> weaponDoctrineSummary;
    private final ComputedSignal<String> armorDoctrineSummary;
    private final ComputedSignal<String> weaponPickerTabClasses;
    private final ComputedSignal<String> armorPickerTabClasses;
    private final ComputedSignal<String> weaponPickerPanelClasses;
    private final ComputedSignal<String> armorPickerPanelClasses;
    private final ComputedSignal<List<LoadoutFilterOption>> loadoutFilters;
    private final ComputedSignal<LoadoutFilterOption> issuableFilter;
    private final ComputedSignal<String> loadoutBrowserSummary;
    private final ComputedSignal<List<ArmorComparisonCard>> armorComparisonCards;
    private final ComputedSignal<String> armorComparisonSummary;

    public FleetArmoryViewModel(Reactor reactor, MarineRoster roster) {
        this(reactor, roster, () -> { }, () -> 0d, EquipmentIssueResources.UNLIMITED,
                SquadFoundingResources.NONE);
    }

    public FleetArmoryViewModel(
            Reactor reactor, MarineRoster roster, Runnable openSelectedSquad) {
        this(reactor, roster, openSelectedSquad, () -> 0d,
                EquipmentIssueResources.UNLIMITED, SquadFoundingResources.NONE);
    }

    public FleetArmoryViewModel(Reactor reactor, MarineRoster roster,
                                Runnable openSelectedSquad, DoubleSupplier currentDay) {
        this(reactor, roster, openSelectedSquad, currentDay,
                EquipmentIssueResources.UNLIMITED, SquadFoundingResources.NONE);
    }

    public FleetArmoryViewModel(
            Reactor reactor, MarineRoster roster, Runnable openSelectedSquad,
            DoubleSupplier currentDay, EquipmentIssueResources equipmentIssueResources) {
        this(reactor, roster, openSelectedSquad, currentDay, equipmentIssueResources,
                SquadFoundingResources.NONE);
    }

    public FleetArmoryViewModel(
            Reactor reactor, MarineRoster roster, Runnable openSelectedSquad,
            DoubleSupplier currentDay, EquipmentIssueResources equipmentIssueResources,
            SquadFoundingResources squadFoundingResources) {
        if (reactor == null) throw new IllegalArgumentException("reactor is required");
        if (roster == null) throw new IllegalArgumentException("roster is required");
        if (openSelectedSquad == null) {
            throw new IllegalArgumentException("openSelectedSquad is required");
        }
        if (currentDay == null) throw new IllegalArgumentException("currentDay is required");
        if (equipmentIssueResources == null) {
            throw new IllegalArgumentException("equipmentIssueResources is required");
        }
        if (squadFoundingResources == null) {
            throw new IllegalArgumentException("squadFoundingResources is required");
        }
        this.roster = roster;
        this.openSelectedSquad = openSelectedSquad;
        this.currentDay = currentDay;
        this.equipmentIssueResources = equipmentIssueResources;
        this.squadFoundingResources = squadFoundingResources;
        squadFoundingWorkshop = new SquadFoundingWorkshop(roster, squadFoundingResources);

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
        equipmentPickerKind = reactor.signal(EquipmentPickerKind.WEAPON);
        loadoutFilter = reactor.signal(LoadoutFilter.ALL);
        issuableOnly = reactor.signal(Boolean.FALSE);
        domainRevision = reactor.signal(0);
        feedback = reactor.signal(Feedback.neutral(
                "Hover equipment names for field notes. Assign a weapon loadout and a tactic sheet, then issue to the squad."));
        foundingFeedback = reactor.signal(Feedback.neutral(
                "The complete bill is required; no partial squad is created."));

        companySummary = reactor.computed(this::buildCompanySummary);
        squadCards = reactor.computed(this::buildSquadCards);
        foundingCargoRows = reactor.computed(this::buildFoundingCargoRows);
        foundingDisabled = reactor.computed(() -> {
            domainRevision.get();
            return !squadFoundingResources.canAfford(SquadFoundingCost.STANDARD);
        });
        foundingFeedbackText = reactor.computed(() -> foundingFeedback.get().text());
        foundingFeedbackClasses = reactor.computed(() -> foundingFeedback.get().succeeded()
                ? "squad-founder-feedback label tone-good"
                : "squad-founder-feedback label tone-muted");
        squadGalleryCards = reactor.computed(this::buildSquadGalleryCards);
        fireTeamOverviews = reactor.computed(this::buildFireTeamOverviews);
        squadRows = reactor.computed(this::buildSquadRows);
        teamRows = reactor.computed(this::buildTeamRows);
        marineCards = reactor.computed(this::buildMarineCards);
        targetSummary = reactor.computed(this::buildTargetSummary);
        candidateSummary = reactor.computed(this::buildCandidateSummary);
        applyLabel = reactor.computed(this::buildApplyLabel);
        squadEquipmentPreview = reactor.computed(this::buildSquadEquipmentPreview);
        issueCargoRows = reactor.computed(this::buildIssueCargoRows);
        issueCargoClasses = reactor.computed(() -> issueCargoRows.get().isEmpty()
                ? "cargo-costs empty" : "cargo-costs");
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
        weaponPickerTabClasses = reactor.computed(() -> equipmentPickerKind.get()
                == EquipmentPickerKind.WEAPON
                ? "equipment-picker-tab selected" : "equipment-picker-tab");
        armorPickerTabClasses = reactor.computed(() -> equipmentPickerKind.get()
                == EquipmentPickerKind.ARMOR
                ? "equipment-picker-tab selected" : "equipment-picker-tab");
        weaponPickerPanelClasses = reactor.computed(() -> equipmentPickerKind.get()
                == EquipmentPickerKind.WEAPON
                ? "doctrine-slot" : "doctrine-slot picker-hidden");
        armorPickerPanelClasses = reactor.computed(() -> equipmentPickerKind.get()
                == EquipmentPickerKind.ARMOR
                ? "doctrine-slot" : "doctrine-slot picker-hidden");
        loadoutFilters = reactor.computed(this::buildLoadoutFilters);
        issuableFilter = reactor.computed(this::buildIssuableFilter);
        loadoutBrowserSummary = reactor.computed(this::buildLoadoutBrowserSummary);
        armorComparisonCards = reactor.computed(this::buildArmorComparisonCards);
        armorComparisonSummary = reactor.computed(this::buildArmorComparisonSummary);
    }

    public MarineRoster roster() { return roster; }
    public Signal<String> companySummary() { return companySummary; }
    public Signal<List<SquadCard>> squadCards() { return squadCards; }
    public Signal<List<SquadGalleryCard>> squadGalleryCards() { return squadGalleryCards; }
    public Signal<List<CargoCostRow>> foundingCargoRows() { return foundingCargoRows; }
    public Signal<Boolean> foundingDisabled() { return foundingDisabled; }
    public Signal<String> foundingFeedbackText() { return foundingFeedbackText; }
    public Signal<String> foundingFeedbackClasses() { return foundingFeedbackClasses; }
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
    public Signal<List<CargoCostRow>> issueCargoRows() { return issueCargoRows; }
    public Signal<String> issueCargoClasses() { return issueCargoClasses; }
    public Signal<String> weaponDoctrineSummary() { return weaponDoctrineSummary; }
    public Signal<String> armorDoctrineSummary() { return armorDoctrineSummary; }
    public Signal<String> weaponPickerTabClasses() { return weaponPickerTabClasses; }
    public Signal<String> armorPickerTabClasses() { return armorPickerTabClasses; }
    public Signal<String> weaponPickerPanelClasses() { return weaponPickerPanelClasses; }
    public Signal<String> armorPickerPanelClasses() { return armorPickerPanelClasses; }
    public Signal<List<LoadoutFilterOption>> loadoutFilters() { return loadoutFilters; }
    public Signal<LoadoutFilterOption> issuableFilter() { return issuableFilter; }
    public Signal<String> loadoutBrowserSummary() { return loadoutBrowserSummary; }
    public Signal<List<ArmorComparisonCard>> armorComparisonCards() { return armorComparisonCards; }
    public Signal<String> armorComparisonSummary() { return armorComparisonSummary; }
    public String selectedSquadId() { return selectedSquadId.peek(); }
    public int selectedTeamIndex() { return selectedTeamIndex.peek(); }
    public String selectedWeaponDoctrineId() { return selectedWeaponDoctrineId.peek(); }
    public String selectedArmorDoctrineId() { return selectedArmorDoctrineId.peek(); }
    public EquipmentPickerKind equipmentPickerKind() { return equipmentPickerKind.peek(); }
    public LoadoutFilter loadoutFilter() { return loadoutFilter.peek(); }
    public boolean issuableOnly() { return issuableOnly.peek(); }
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

    public Runnable showWeaponPickerAction() {
        return () -> equipmentPickerKind.set(EquipmentPickerKind.WEAPON);
    }

    public Runnable showArmorPickerAction() {
        return () -> equipmentPickerKind.set(EquipmentPickerKind.ARMOR);
    }

    public Runnable showLoadoutFilterAction(LoadoutFilter filter) {
        if (filter == null) throw new IllegalArgumentException("filter is required");
        return () -> loadoutFilter.set(filter);
    }

    /** Hides every loadout whose issue would cost more cargo than the fleet has. */
    public Runnable toggleIssuableOnlyAction() {
        return () -> issuableOnly.update(on -> !Boolean.TRUE.equals(on));
    }


    public Runnable reinforceSelectedSquadAction() {
        return () -> reinforceSquad(selectedSquadId.peek());
    }

    public Runnable foundSquadAction() {
        return this::foundSquad;
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
                    id + ":reinforce", id + ":reinforce-icon", id + ":reinforce-label",
                    "squad-card " + readinessClass,
                    "squad-card-status heading " + readinessTone(ready, MarineSquad.CAPACITY),
                    squad.name(), readiness, ready + " / " + MarineSquad.CAPACITY + " RTD",
                    assigned == MarineSquad.TEAMS_PER_SQUAD
                            ? "Squad doctrine issued" : "Individual equipment",
                    command, location, compactRecoverySummary(squad),
                    equipmentIssueResources.commodityIcon(Commodities.MARINES),
                    reinforcementLabel(squad), reinforcementCapacity(squad) <= 0,
                    () -> {
                        selectSquad(squad.id());
                        openSelectedSquad.run();
                    }, () -> reinforceSquad(squad.id())));
        }
        return List.copyOf(cards);
    }

    private List<CargoCostRow> buildFoundingCargoRows() {
        domainRevision.get();
        List<CargoCostRow> rows = new ArrayList<>();
        for (SquadFoundingCost.Line line : SquadFoundingCost.STANDARD.lines()) {
            int available = squadFoundingResources.available(line.commodityId());
            String id = "found-squad-cost:" + line.commodityId();
            String commodityName = squadFoundingResources.commodityName(line.commodityId());
            if (Commodities.HAND_WEAPONS.equals(line.commodityId())) {
                commodityName = "Armaments";
            }
            rows.add(new CargoCostRow(
                    id, id + ":icon", id + ":label",
                    available >= line.quantity() ? "cargo-cost" : "cargo-cost short",
                    squadFoundingResources.commodityIcon(line.commodityId()),
                    commodityName.toUpperCase(Locale.ROOT) + "  " + available
                            + "/" + line.quantity()));
        }
        return List.copyOf(rows);
    }

    private List<SquadGalleryCard> buildSquadGalleryCards() {
        List<SquadGalleryCard> cards = new ArrayList<>();
        for (SquadCard squad : squadCards.get()) {
            String founder = squad.id() + ":founder";
            cards.add(new SquadGalleryCard(squad.id(), squad.classes(), squad,
                    "squad-open", "squad-reinforce", "squad-founder-card hidden",
                    founder, founder + ":plus",
                    founder + ":heading", founder + ":copy",
                    founder + ":costs", founder + ":feedback", List.of(), "", "",
                    true, () -> { }));
        }
        String id = "found-squad-card";
        cards.add(new SquadGalleryCard(id, "squad-founder-slot", emptySquadCard(id),
                "squad-open hidden", "squad-reinforce hidden", "squad-founder-card",
                "found-squad", "found-squad-plus",
                "found-squad-heading", "found-squad-copy",
                "squad-founder-costs", "squad-founder-feedback", foundingCargoRows.get(),
                foundingFeedbackText.get(), foundingFeedbackClasses.get(),
                foundingDisabled.get(), this::foundSquad));
        return List.copyOf(cards);
    }

    private static SquadCard emptySquadCard(String id) {
        return new SquadCard(id + ":empty", id + ":empty-name", id + ":empty-status",
                id + ":empty-strength", id + ":empty-teams", id + ":empty-command",
                id + ":empty-location", id + ":empty-recovery", id + ":empty-open",
                id + ":empty-reinforce", id + ":empty-reinforce-icon",
                id + ":empty-reinforce-label", "", "", "", "", "", "", "",
                "", "", "", "", true, () -> { }, () -> { });
    }

    private List<FireTeamOverview> buildFireTeamOverviews() {
        domainRevision.get();
        MarineSquad squad = roster.squadById(selectedSquadId.get());
        if (squad == null) return List.of();
        int selected = selectedTeamIndex.get();
        List<FireTeamOverview> teams = new ArrayList<>();
        for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
            int target = team;
            String id = "fire-team:" + squad.id() + ":" + team;
            teams.add(new FireTeamOverview(id,
                    team == selected ? "fire-team-tab selected" : "fire-team-tab",
                    teamName(team),
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
            boolean available = roster.armory().canAuthorWeaponDoctrine(doctrine.issues());
            if (!available) continue;
            SquadLoadoutPresentationDef presentation = loadoutPresentation(
                    doctrine.id(), SquadLoadoutPresentationDef.Kind.WEAPON,
                    maximumWeaponTier(doctrine), doctrine.description());
            if (!loadoutFilter.get().accepts(presentation.rarity())) continue;
            if (issuableOnly.get() && !affordable(
                    doctrine.id(), selectedArmorDoctrineId.get())) continue;
            String id = "weapon-doctrine:" + doctrine.id();
            tiles.add(doctrineTile(id, doctrine.id().equals(selected),
                    doctrine.displayName(), LoadoutEffectiveness.weaponRating(doctrine),
                    presentation, doctrineMetadata(presentation),
                    "", weaponDistribution(doctrine), "",
                    () -> selectWeaponDoctrine(doctrine.id())));
        }
        return rankedByRating(tiles);
    }

    private List<DoctrineTile> buildArmorDoctrineTiles() {
        domainRevision.get();
        String selected = selectedArmorDoctrineId.get();
        List<DoctrineTile> tiles = new ArrayList<>();
        for (SquadArmorPlan plan : SquadEquipmentDoctrines.armorPlans()) {
            SquadArmorDoctrine issued = roster.armory().issue(plan);
            SquadLoadoutPresentationDef presentation = loadoutPresentation(
                    plan.id(), SquadLoadoutPresentationDef.Kind.ARMOR,
                    maximumArmorTier(issued), plan.description());
            if (!loadoutFilter.get().accepts(presentation.rarity())) continue;
            if (issuableOnly.get() && !affordable(
                    selectedWeaponDoctrineId.get(), plan.id())) continue;
            String id = "armor-doctrine:" + plan.id();
            // The authored tier is what the sheet IS; the issued band is what
            // this company would actually put in the field today. Showing both
            // is the point of the picker under role-and-access.md — a sheet does
            // not get better, the kit filling it does.
            String metadata = titleCase(plan.tradition().key.replace('_', ' '))
                    + "  ·  FIELDS TIER " + tierMark(maximumArmorTier(issued));
            tiles.add(doctrineTile(id, plan.id().equals(selected),
                    plan.displayName(), LoadoutEffectiveness.armorRating(issued),
                    presentation, metadata,
                    roleComposition(plan), armorDistribution(issued),
                    armorCapabilities(issued),
                    () -> selectArmorDoctrine(plan.id())));
        }
        return rankedByRating(tiles);
    }

    /**
     * Best first. The question this picker exists to answer is "which of the
     * things I can field is the strongest", and authored declaration order
     * cannot answer it — the built-in definitions are listed in the order
     * somebody wrote them, which put the starter kit above everything the
     * company has bought since. Ties break by name so the order is stable.
     */
    private static List<DoctrineTile> rankedByRating(List<DoctrineTile> tiles) {
        List<DoctrineTile> ranked = new ArrayList<>(tiles);
        ranked.sort(Comparator.comparingInt(DoctrineTile::rating).reversed()
                .thenComparing(DoctrineTile::name));
        return List.copyOf(ranked);
    }

    /**
     * Whether issuing this pairing to the selected squad costs no more cargo
     * than the fleet is carrying.
     *
     * <p>Deliberately narrower than {@link SquadEquipmentPreview#canApply()}. A
     * stationed or under-strength squad cannot be issued anything at all, and
     * hiding every tile behind that would turn the picker into an empty list
     * with no explanation — the apply row already says why. This asks only the
     * supplies question, and a preview that stopped before pricing reports no
     * cost, so those squads keep a full list to browse.
     */
    private boolean affordable(String weaponDoctrineId, String armorDoctrineId) {
        SquadEquipmentPreview preview = roster.previewSquadEquipment(
                selectedSquadId.get(), weaponDoctrineId, armorDoctrineId,
                equipmentIssueResources);
        return preview.availableCargo().covers(preview.issueCost());
    }

    private static DoctrineTile doctrineTile(
            String id, boolean selected, String name, int rating,
            SquadLoadoutPresentationDef presentation, String metadata,
            String composition, String distribution, String carries, Runnable select) {
        String rarityClass = presentation.rarity().cssClass();
        String classes = "doctrine-tile " + rarityClass
                + (selected ? " selected" : "");
        return new DoctrineTile(id, id + ":header", id + ":name", id + ":rarity",
                id + ":rating", id + ":metadata-row", id + ":faction-logo",
                id + ":metadata", id + ":description", id + ":composition",
                id + ":distribution", id + ":carries", classes,
                "doctrine-rarity label " + rarityClass, ratingClasses(rating),
                factionLogoClasses(presentation.factionLogo()),
                name, presentation.rarity().displayName(), rating,
                "RATING " + rating, presentation.factionLogo(), metadata,
                presentation.lore(), composition, distribution, carries, select);
    }

    private static String factionLogoClasses(String factionLogo) {
        return factionLogo == null || factionLogo.isBlank()
                ? "doctrine-faction-logo faction-logo-hidden"
                : "doctrine-faction-logo";
    }

    /**
     * Three bands rather than a gradient, because the chip is read at a glance
     * beside nineteen others and a continuous colour ramp is not a thing anyone
     * can rank by eye. The thresholds are presentation only; nothing in the
     * armoury or the battle reads them.
     */
    private static String ratingClasses(int rating) {
        String band = rating >= 60 ? "rating-high" : rating >= 30 ? "rating-mid" : "rating-low";
        return "doctrine-rating label " + band;
    }

    private static String doctrineMetadata(SquadLoadoutPresentationDef presentation) {
        return "TIER " + tierMark(presentation.tier()) + "  ·  " + presentation.provenance();
    }

    private List<LoadoutFilterOption> buildLoadoutFilters() {
        LoadoutFilter selected = loadoutFilter.get();
        List<LoadoutFilterOption> filters = new ArrayList<>();
        for (LoadoutFilter filter : LoadoutFilter.values()) {
            String id = "loadout-filter:" + filter.name().toLowerCase(Locale.ROOT);
            filters.add(new LoadoutFilterOption(id, filter.label,
                    filter == selected ? "loadout-filter selected" : "loadout-filter",
                    showLoadoutFilterAction(filter)));
        }
        return List.copyOf(filters);
    }

    /**
     * The supplies filter, kept out of the rarity row above it because the two
     * are different kinds of control. Rarity is a radio — exactly one band at a
     * time — and this is a switch that narrows whichever band is showing. Put
     * side by side they read as six alternatives, and a player who pressed
     * "Common" then "Ready" would reasonably expect the first to have been
     * turned off.
     */
    private LoadoutFilterOption buildIssuableFilter() {
        boolean on = Boolean.TRUE.equals(issuableOnly.get());
        return new LoadoutFilterOption("loadout-supply-filter",
                on ? "Supplies on hand" : "Any supply cost",
                on ? "supply-filter selected" : "supply-filter",
                toggleIssuableOnlyAction());
    }

    private String buildLoadoutBrowserSummary() {
        int known = equipmentPickerKind.get() == EquipmentPickerKind.WEAPON
                ? knownWeaponLoadouts() : knownArmorLoadouts();
        int shown = equipmentPickerKind.get() == EquipmentPickerKind.WEAPON
                ? weaponDoctrineTiles.get().size() : armorDoctrineTiles.get().size();
        return shown == known ? known + " known" : shown + " of " + known + " known";
    }

    private int knownWeaponLoadouts() {
        int known = 0;
        for (SquadWeaponDoctrine doctrine : roster.armory().weaponDoctrines()) {
            if (roster.armory().canAuthorWeaponDoctrine(doctrine.issues())) known++;
        }
        return known;
    }

    /**
     * Every authored sheet, always. A sheet names roles and the armoury fills
     * them from whatever is owned, so there is no such thing as one this company
     * cannot field — the worst case is a role marked "(no kit)" on the section
     * line and filled with line armour ({@code role-and-access.md}). Weapons
     * still gate on collected cards, which is why the weapon count above it does
     * real work and this one is a total.
     */
    private int knownArmorLoadouts() {
        return roster.armory().armorDoctrines().size();
    }

    /**
     * Every armor pattern <b>this company owns a template card for</b>, side by
     * side, sorted by tier then name so the frontier baseline reads before the
     * battlesuits regardless of catalog declaration order. This is a read
     * surface: comparing patterns does not select one, because a squad's armor
     * is issued as a doctrine bundling twelve billets, not as one pattern chosen
     * in isolation.
     *
     * <p>Owned rather than catalogued. The screen exists to answer "what is my
     * kit and which of it should the section be in", and a table where most rows
     * are suits the company has never held answers a different question badly —
     * the twenty-seventh pattern is not an option, it is a rumour. The summary
     * beneath still names the whole catalog's size, so the fact that there is
     * more out there survives without pretending it is choosable.
     */
    private List<ArmorComparisonCard> buildArmorComparisonCards() {
        domainRevision.get();
        List<MarineArmorCatalogDef> patterns = new ArrayList<>();
        for (MarineArmorCatalogDef pattern : MarineArmorCatalogRegistry.installed().all()) {
            if (roster.armory().ownsArmorTemplate(pattern.id())) patterns.add(pattern);
        }
        patterns.sort(Comparator.comparingInt(MarineArmorCatalogDef::tier)
                .thenComparing(MarineArmorCatalogDef::displayName));
        List<ArmorComparisonCard> cards = new ArrayList<>();
        for (MarineArmorCatalogDef armor : patterns) {
            String id = "armor-comparison:" + armor.id();
            cards.add(new ArmorComparisonCard(
                    id, id + ":header", id + ":name", id + ":tier", id + ":class",
                    id + ":stats", id + ":system-row", id + ":system-icon", id + ":system",
                    id + ":description",
                    "comparison-card",
                    armor.displayName(),
                    "TIER " + tierMark(armor.tier()),
                    armor.role().displayName(),
                    armorComparisonStats(id, armor),
                    IntegralSystemCopy.tile(armor),
                    IntegralSystemCopy.iconPath(armor),
                    comparisonSystemClasses(armor),
                    armor.description()));
        }
        return List.copyOf(cards);
    }

    private String buildArmorComparisonSummary() {
        int owned = armorComparisonCards.get().size();
        int catalogued = MarineArmorCatalogRegistry.installed().all().size();
        return owned + " of " + catalogued
                + " armor patterns held  ·  sorted by tier, then name";
    }

    private static List<StatMeter> armorComparisonStats(String cardId, MarineArmorCatalogDef armor) {
        float evasion = CatalogCeilings.evasionOf(armor);
        return List.of(
                StatMeter.of(cardId + ":armor-value", "ARMOR",
                        String.format(Locale.ROOT, "%.0f", armor.armorCapacity()),
                        armor.armorCapacity(), CatalogCeilings.armorCapacity()),
                StatMeter.of(cardId + ":resist", "RESIST",
                        String.format(Locale.ROOT, "%.0f", armor.armorRating()),
                        armor.armorRating(), CatalogCeilings.armorRating()),
                StatMeter.of(cardId + ":move", "MOVE",
                        String.format(Locale.ROOT, "%.0f%%", armor.moveSpeedMult() * 100f),
                        armor.moveSpeedMult(), CatalogCeilings.moveSpeedMult()),
                StatMeter.of(cardId + ":evasion", "EVASION",
                        signedPercent(evasion), evasion, CatalogCeilings.armorEvasion()));
    }

    private static String signedPercent(float value) {
        int percent = Math.round(value * 100f);
        return (percent > 0 ? "+" : "") + percent + "%";
    }

    /**
     * Comparison-card styling for the integral-system line, kept independent
     * of {@link #systemClasses} so the comparison surface never depends on the
     * fire-team card's local CSS classes.
     */
    private static String comparisonSystemClasses(MarineArmorCatalogDef armor) {
        return "comparison-system label surface-dark "
                + (IntegralSystemCopy.carried(armor) ? "tone-accent" : "tone-muted");
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) return "Unclassified";
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static SquadLoadoutPresentationDef loadoutPresentation(
            String id, SquadLoadoutPresentationDef.Kind kind, int tier, String fallbackLore) {
        SquadLoadoutPresentationDef authored = SquadLoadoutPresentationRegistry.get(id);
        return authored != null ? authored : new SquadLoadoutPresentationDef(
                id, kind, tier, SquadLoadoutRarity.COMMON,
                "Company-authored", null, fallbackLore != null ? fallbackLore : "");
    }

    private static int maximumWeaponTier(SquadWeaponDoctrine doctrine) {
        int tier = 1;
        for (var issue : doctrine.issues()) tier = Math.max(tier, issue.grade().tier);
        return tier;
    }

    private static int maximumArmorTier(SquadArmorDoctrine doctrine) {
        int tier = 1;
        for (String armorId : doctrine.issueIds()) {
            tier = Math.max(tier, MarineArmorCatalogRegistry.require(armorId).tier());
        }
        return tier;
    }

    private static String tierMark(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "V";
        };
    }

    private String buildWeaponDoctrineSummary() {
        SquadWeaponDoctrine doctrine = roster.armory().weaponDoctrineById(
                selectedWeaponDoctrineId.get());
        return doctrine != null
                ? "Selected  ·  " + doctrine.displayName()
                : "Choose weapon equipment";
    }

    private String buildArmorDoctrineSummary() {
        SquadArmorDoctrine doctrine = roster.armory().armorDoctrineById(
                selectedArmorDoctrineId.get());
        return doctrine != null
                ? "Assigned  ·  " + doctrine.displayName()
                : "Assign a tactic sheet";
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
                    ? billet.armorDef() : null;
            WeaponDef primary = billet != null ? billet.primaryDef() : null;
            String primaryFactionLogo = primary != null ? primary.catalogFactionLogo : null;
            String armorFactionLogo = armorCatalog != null
                    ? armorCatalog.tradition().factionLogo() : null;
            String specialFactionLogo = special != null ? special.catalogFactionLogo() : null;
            String systemFactionLogo = IntegralSystemCopy.carried(armorCatalog)
                    ? armorFactionLogo : null;
            marines.add(new MarineViewerCard(
                    id, id + ":content", "marine-preview:" + index, id + ":header",
                    id + ":hero", id + ":identity",
                    id + ":badges", id + ":class-badge", id + ":weapon-badge",
                    id + ":armor-badge", id + ":name", id + ":role",
                    id + ":status", id + ":service", id + ":personnel",
                    id + ":primary", id + ":primary-faction-logo",
                    id + ":primary-tooltip-faction-logo", id + ":primary-text",
                    id + ":primary-tooltip-text", id + ":primary-description",
                    id + ":weapon-detail", id + ":weapon-stats",
                    id + ":armor", id + ":armor-faction-logo",
                    id + ":armor-tooltip-faction-logo", id + ":armor-text",
                    id + ":armor-tooltip-text", id + ":armor-description",
                    id + ":armor-detail",
                    id + ":armor-stats",
                    id + ":special", id + ":special-faction-logo",
                    id + ":special-tooltip-faction-logo", id + ":special-text",
                    id + ":special-tooltip-text",
                    id + ":special-description",
                    id + ":system", id + ":system-faction-logo",
                    id + ":system-tooltip-faction-logo", id + ":system-text",
                    id + ":system-tooltip-text", id + ":system-description",
                    id + ":weapon-delta", id + ":armor-delta",
                    id + ":career", id + ":equipment", id + ":weapon-column",
                    id + ":armor-column",
                    marineCardClasses(soldier, previewing),
                    "marine-status heading " + marineStatusTone(soldier),
                    marineName(soldier), billet != null ? billet.name() : "Unfilled billet",
                    marineStatus(soldier), serviceSummary(soldier),
                    personnelSummary(soldier),
                    armorCatalog != null ? armorCatalog.role().displayName() : "VACANT",
                    billet != null ? "W " + billet.grade().tierMark() : "W —",
                    billet != null ? "A " + tierMark(armorCatalog.tier()) : "A —",
                    billet != null ? primary.catalogName(billet.grade().tier) + "  ·  "
                            + billet.grade().displayName : "No primary",
                    billet != null ? primary.catalogDescription : "",
                    primaryFactionLogo, equipmentFactionLogoClasses(primaryFactionLogo),
                    weaponStats(id, billet, soldier),
                    billet != null ? armorCatalog.displayName() + "  ·  Tier "
                            + tierMark(armorCatalog.tier()) : "No armor",
                    armorStats(id, billet), armorDescription(armorCatalog),
                    armorFactionLogo, equipmentFactionLogoClasses(armorFactionLogo),
                    special != null ? special.displayName() : "No specialty equipment",
                    special != null ? special.catalogDescription()
                            : "This billet carries no specialty equipment beyond its primary weapon.",
                    specialFactionLogo, equipmentFactionLogoClasses(specialFactionLogo),
                    IntegralSystemCopy.summary(armorCatalog),
                    IntegralSystemCopy.detail(armorCatalog),
                    systemFactionLogo, equipmentFactionLogoClasses(systemFactionLogo),
                    systemClasses(armorCatalog),
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
        return squad == null ? "Select a line squad"
                : squad.name() + "  ·  12-billet squad issue";
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
        return "Issue to Squad";
    }

    private List<CargoCostRow> buildIssueCargoRows() {
        SquadEquipmentPreview preview = squadEquipmentPreview.get();
        EquipmentTemplateCost cost = preview.issueCost();
        EquipmentTemplateCost available = preview.availableCargo();
        List<CargoCostRow> rows = new ArrayList<>(4);
        addCargoCostRow(rows, Commodities.SUPPLIES, cost.supplies(), available.supplies());
        addCargoCostRow(rows, Commodities.HAND_WEAPONS,
                cost.heavyArmaments(), available.heavyArmaments());
        addCargoCostRow(rows, Commodities.HEAVY_MACHINERY,
                cost.heavyMachinery(), available.heavyMachinery());
        addCargoCostRow(rows, Commodities.FOOD, cost.food(), available.food());
        return List.copyOf(rows);
    }

    private void addCargoCostRow(List<CargoCostRow> rows, String commodityId,
                                 int required, int available) {
        if (required <= 0) return;
        String id = "issue-cargo:" + commodityId;
        rows.add(new CargoCostRow(id, id + ":icon", id + ":label",
                available >= required ? "cargo-cost" : "cargo-cost short",
                equipmentIssueResources.commodityIcon(commodityId),
                equipmentIssueResources.commodityName(commodityId).toUpperCase(Locale.ROOT)
                        + "  " + available + " / " + required));
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

    private void foundSquad() {
        SquadFoundingWorkshop.Result result = squadFoundingWorkshop.foundSquad();
        if (!result.succeeded()) {
            foundingFeedback.set(Feedback.neutral(
                    "Insufficient cargo; the complete founding bill must be aboard."));
        } else {
            MarineSquad squad = result.squad();
            selectSquad(squad.id());
            foundingFeedback.set(Feedback.success(squad.name() + " founded  ·  "
                    + MarineSquad.CAPACITY + " marines ready for issue."));
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
        for (WeaponDef weapon : WeaponRegistry.installed().all()) {
            if (weapon.mount != MountClass.MARINE_PRIMARY) continue;
            int count = 0;
            for (var issue : doctrine.issues()) {
                if (issue.primaryId().equals(weapon.id)) count++;
            }
            if (count > 0) parts.add(count + " " + weapon.displayName);
        }
        int specials = 0;
        for (var issue : doctrine.issues()) if (issue.specialEquipmentId() != null) specials++;
        if (specials > 0) parts.add(specials + " special");
        return String.join("  ·  ", parts);
    }

    /**
     * The section this sheet organises, as billet counts by role. This is the
     * half a player is actually choosing between: it does not change when the
     * company gets richer, which is exactly what distinguishes it from the issue
     * line beneath it ({@code role-and-access.md}).
     */
    private String roleComposition(SquadArmorPlan plan) {
        Map<ArmorRole, Integer> counts = new EnumMap<>(ArmorRole.class);
        for (ArmorRole role : plan.mix().billets()) counts.merge(role, 1, Integer::sum);
        List<String> parts = new ArrayList<>();
        for (ArmorRole role : ArmorRole.values()) {
            Integer count = counts.get(role);
            if (count == null) continue;
            String part = count + " " + role.displayName().toLowerCase(Locale.ROOT);
            // A role this company owns nothing for is filled with line kit by the
            // resolver, silently. Saying so here is what turns a sheet the player
            // cannot yet field into a reason to buy something: the ISSUED line
            // below would otherwise just show twelve of the wrong suit and no
            // explanation ({@code role-and-access.md}).
            if (!ownsAnyPatternFor(role)) part += " (no kit)";
            parts.add(part);
        }
        return String.join("  ·  ", parts);
    }

    private boolean ownsAnyPatternFor(ArmorRole role) {
        MarineArmorCatalogRegistry catalog = MarineArmorCatalogRegistry.installed();
        if (catalog == null) return true;
        for (MarineArmorCatalogDef pattern : catalog.all()) {
            if (pattern.role() == role && roster.armory().ownsArmorTemplate(pattern.id())) {
                return true;
            }
        }
        return false;
    }

    /**
     * What this sheet's twelve billets would carry, counted by capability
     * family rather than by pattern.
     *
     * <p>The line beneath it already names the patterns, and a player who knows
     * the catalog by heart could derive this from those names. Nobody should
     * have to: the whole reason a role mix is worth choosing between is that one
     * sheet puts a sensor sweep and seven braces in the field where another puts
     * four breachers, and that difference was previously visible only by opening
     * Compare Patterns and cross-referencing twelve names.
     *
     * <p>Ordered by how many marines carry each, so the sheet's dominant
     * character reads first and a clipped line loses the least. Derived from the
     * <em>issued</em> doctrine rather than the plan, so it improves as the
     * company's stock does — the same rule the issue line follows.
     */
    private static String armorCapabilities(SquadArmorDoctrine doctrine) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String issueId : doctrine.issueIds()) {
            MarineArmorCatalogDef pattern = MarineArmorCatalogRegistry.installed().get(issueId);
            if (pattern == null || !pattern.hasIntegralSystem()) continue;
            counts.merge(pattern.integralSystem().familyName(), 1, Integer::sum);
        }
        if (counts.isEmpty()) return "Nothing beyond plate and training";
        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(counts.entrySet());
        ranked.sort(Map.Entry.<String, Integer>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()));
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : ranked) {
            parts.add(entry.getValue() + " " + entry.getKey());
        }
        return String.join("  ·  ", parts);
    }

    private static String armorDistribution(SquadArmorDoctrine doctrine) {
        List<String> parts = new ArrayList<>();
        for (MarineArmorCatalogDef pattern : MarineArmorCatalogRegistry.installed().all()) {
            int count = 0;
            for (String issueId : doctrine.issueIds()) {
                if (issueId.equals(pattern.id())) count++;
            }
            if (count > 0) parts.add(count + " " + pattern.displayName());
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
        return new FireTeamBillet(role, soldier.primaryId(), soldier.primaryGrade(),
                soldier.specialEquipmentId(), soldier.armorId());
    }

    private static FireTeamBillet asFireTeamBillet(SquadEquipmentBillet billet) {
        return billet != null ? new FireTeamBillet(
                billet.role(), billet.primaryId(), billet.grade(),
                billet.specialEquipmentId(), billet.armorId()) : null;
    }

    private static String marineCardClasses(MarineSoldier soldier, boolean previewing) {
        String classes = "marine-viewer-card " + (previewing ? "previewing" : "viewer-only");
        if (soldier == null) return classes + " status-vacant";
        return classes + switch (soldier.status()) {
            case ACTIVE -> " status-ready";
            case WIA -> " status-wia";
            case MIA -> " status-missing";
            case KIA -> " status-killed";
        };
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
                + soldier.armorDef().displayName() + " issue";
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
        float damage = InfantryCombatStats.damage(billet.primaryDef(), billet.grade());
        float range = InfantryCombatStats.range(billet.primaryDef(), billet.grade());
        float accuracy = InfantryCombatStats.accuracy(
                billet.primaryDef(), billet.grade(), profile);
        float dps = InfantryCombatStats.estimatedDps(
                billet.primaryDef(), billet.grade(), profile);
        return List.of(
                StatMeter.of(cardId + ":damage", "DMG", formatOneDecimal(damage),
                        damage, CatalogCeilings.weaponDamage()),
                StatMeter.of(cardId + ":range", "RNG", formatOneDecimal(range),
                        range, CatalogCeilings.weaponRange()),
                StatMeter.of(cardId + ":accuracy", "ACC",
                        String.format(Locale.ROOT, "%.0f%%", accuracy * 100f),
                        accuracy, 1f),
                StatMeter.of(cardId + ":dps", "DPS", formatOneDecimal(dps),
                        dps, CatalogCeilings.weaponDps(profile)));
    }

    private static String weaponDelta(
            FireTeamBillet billet, MarineSoldier soldier, boolean previewing) {
        if (!previewing || billet == null || soldier == null) return "";
        SoldierProfile profile = soldier.profile();
        float damage = InfantryCombatStats.damage(billet.primaryDef(), billet.grade())
                - InfantryCombatStats.damage(soldier.primaryDef(), soldier.primaryGrade());
        float range = InfantryCombatStats.range(billet.primaryDef(), billet.grade())
                - InfantryCombatStats.range(soldier.primaryDef(), soldier.primaryGrade());
        float accuracy = (InfantryCombatStats.accuracy(
                billet.primaryDef(), billet.grade(), profile)
                - InfantryCombatStats.accuracy(
                soldier.primaryDef(), soldier.primaryGrade(), profile)) * 100f;
        float dps = InfantryCombatStats.estimatedDps(billet.primaryDef(), billet.grade(), profile)
                - InfantryCombatStats.estimatedDps(
                soldier.primaryDef(), soldier.primaryGrade(), profile);
        return String.format(Locale.ROOT,
                "DMG %+.1f  ·  RNG %+.0f  ·  ACC %+.0f%%  ·  DPS %+.1f",
                damage, range, accuracy, dps);
    }

    private static String armorDescription(MarineArmorCatalogDef armor) {
        return armor == null ? "" : armor.description();
    }

    private static String equipmentFactionLogoClasses(String factionLogo) {
        return factionLogo == null || factionLogo.isBlank()
                ? "equipment-faction-logo faction-logo-hidden"
                : "equipment-faction-logo";
    }

    /**
     * A suit that carries a capability says so in the accent the special-item
     * line uses, because the two read as the same kind of thing to a player.
     * A suit that carries none stays muted rather than vanishing: an absent
     * line is indistinguishable from a line that failed to render, and the
     * player is choosing between patterns that mostly have nothing here.
     */
    private static String systemClasses(MarineArmorCatalogDef armor) {
        return "marine-equipment-title equipment-note-target label surface-dark "
                + (IntegralSystemCopy.carried(armor) ? "tone-accent" : "tone-muted");
    }

    private static List<StatMeter> armorStats(String cardId, FireTeamBillet billet) {
        if (billet == null) return List.of();
        MarineArmorCatalogDef armor = billet.armorDef();
        return List.of(
                StatMeter.of(cardId + ":health", "HEALTH",
                        String.format(Locale.ROOT, "%.0f", UnitType.MARINE.maxHp),
                        UnitType.MARINE.maxHp, UnitType.MARINE.maxHp),
                StatMeter.of(cardId + ":armor-value", "ARMOR",
                        String.format(Locale.ROOT, "%.0f", armor.armorCapacity()),
                        armor.armorCapacity(), CatalogCeilings.armorCapacity()),
                StatMeter.of(cardId + ":resist", "RESIST",
                        String.format(Locale.ROOT, "%.0f", armor.armorRating()),
                        armor.armorRating(), CatalogCeilings.armorRating()),
                StatMeter.of(cardId + ":speed", "SPEED",
                        String.format(Locale.ROOT, "%.1f",
                                UnitType.MARINE.moveSpeed * armor.moveSpeedMult()),
                        UnitType.MARINE.moveSpeed * armor.moveSpeedMult(),
                        UnitType.MARINE.moveSpeed * CatalogCeilings.moveSpeedMult()));
    }

    private static String formatOneDecimal(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String armorDelta(
            FireTeamBillet billet, MarineSoldier soldier, boolean previewing) {
        if (!previewing || billet == null || soldier == null) return "";
        MarineArmorCatalogDef next = billet.armorDef();
        MarineArmorCatalogDef current = soldier.armorDef();
        return String.format(Locale.ROOT,
                "ARMOR %+.0f  ·  RESIST %+.0f  ·  SPEED %+.1f",
                next.armorCapacity() - current.armorCapacity(),
                next.armorRating() - current.armorRating(),
                UnitType.MARINE.moveSpeed
                        * (next.moveSpeedMult() - current.moveSpeedMult()));
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
                    : "Ready  ·  Fleet cargo bill shown below.";
            case INVALID_SQUAD -> "Select a line squad.";
            case SQUAD_NOT_READY -> "Not ready  ·  This squad needs twelve RTD marines.";
            case STATIONED -> "Unavailable  ·  This squad is stationed away.";
            case UNKNOWN_WEAPON_DOCTRINE -> "Choose weapon equipment.";
            case UNKNOWN_ARMOR_DOCTRINE -> "Choose armor equipment.";
            case MISSING_TEMPLATE ->
                    "Blocked  ·  One or more required equipment templates are not known.";
            case INSUFFICIENT_CARGO ->
                    "Blocked  ·  Fleet cargo shortages are marked below.";
        };
    }

    /**
     * One buyable sheet in the picker.
     *
     * <p>{@code composition}, {@code distribution} and {@code carries} are the
     * three halves — the word is wrong and the split is not — of what a squad's
     * armour actually is ({@code role-and-access.md}). The first is the section
     * this sheet organises: fixed, and the thing the player is choosing between.
     * The second is which patterns the company's own stock currently puts in
     * those billets. The third is what those patterns <em>do</em>, which is the
     * half a player cannot work out from the other two without knowing the
     * catalog by heart. A weapon sheet has none of them and leaves them blank.
     */
    public record DoctrineTile(
            String id, String headerId, String nameId, String rarityId,
            String ratingId, String metadataRowId, String factionLogoId,
            String metadataId, String descriptionId, String compositionId,
            String distributionId, String carriesId,
            String classes, String rarityClasses, String ratingClasses,
            String factionLogoClasses, String name, String rarity, int rating,
            String ratingLabel, String factionLogo, String metadata,
            String description, String composition, String distribution,
            String carries,
            Runnable select) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "headerId" -> headerId;
                case "nameId" -> nameId;
                case "rarityId" -> rarityId;
                case "ratingId" -> ratingId;
                case "metadataRowId" -> metadataRowId;
                case "factionLogoId" -> factionLogoId;
                case "metadataId" -> metadataId;
                case "descriptionId" -> descriptionId;
                case "compositionId" -> compositionId;
                case "distributionId" -> distributionId;
                case "carriesId" -> carriesId;
                case "classes" -> classes;
                case "rarityClasses" -> rarityClasses;
                case "ratingClasses" -> ratingClasses;
                case "factionLogoClasses" -> factionLogoClasses;
                case "name" -> name;
                case "rarity" -> rarity;
                case "rating" -> rating;
                case "ratingLabel" -> ratingLabel;
                case "factionLogo" -> factionLogo;
                case "metadata" -> metadata;
                case "description" -> description;
                case "composition" -> composition;
                case "distribution" -> distribution;
                case "carries" -> carries;
                case "select" -> select;
                default -> throw new IllegalArgumentException("Unknown doctrine-tile property");
            };
        }
    }

    public record LoadoutFilterOption(
            String id, String label, String classes, Runnable select)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "label" -> label;
                case "classes" -> classes;
                case "select" -> select;
                default -> throw new IllegalArgumentException("Unknown loadout-filter property");
            };
        }
    }

    /**
     * One pattern's row in the side-by-side comparison surface: role/class,
     * tier, protection and mobility meters, its integral system if it carries
     * one — named, quantified, and shown with its family icon — and the
     * provenance copy authored on the catalog entry. See
     * {@code powered-assault-armor-roles.md}'s comparison-presentation
     * acceptance and {@code progression-nouns.md}'s presentation law: this
     * reads existing catalog and simulation data, never selects or mutates it.
     */
    public record ArmorComparisonCard(
            String id, String headerId, String nameId, String tierId, String classId,
            String statsId, String systemRowId, String systemIconId, String systemId,
            String descriptionId, String classes,
            String name, String tier, String unitClass, List<StatMeter> stats,
            String system, String systemIcon, String systemClasses, String description)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "headerId" -> headerId;
                case "nameId" -> nameId;
                case "tierId" -> tierId;
                case "classId" -> classId;
                case "statsId" -> statsId;
                case "systemRowId" -> systemRowId;
                case "systemIconId" -> systemIconId;
                case "systemId" -> systemId;
                case "descriptionId" -> descriptionId;
                case "classes" -> classes;
                case "name" -> name;
                case "tier" -> tier;
                case "unitClass" -> unitClass;
                case "stats" -> stats;
                case "system" -> system;
                case "systemIcon" -> systemIcon;
                case "systemClasses" -> systemClasses;
                case "description" -> description;
                default -> throw new IllegalArgumentException(
                        "Unknown armor-comparison-card property");
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
            String openId, String reinforceId, String reinforceIconId,
            String reinforceLabelId,
            String classes, String statusClasses, String name, String status,
            String strength, String teams, String command, String location,
            String recovery, String reinforceIcon, String reinforceLabel,
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
                case "reinforceIconId" -> reinforceIconId;
                case "reinforceLabelId" -> reinforceLabelId;
                case "classes" -> classes;
                case "statusClasses" -> statusClasses;
                case "name" -> name;
                case "status" -> status;
                case "strength" -> strength;
                case "teams" -> teams;
                case "command" -> command;
                case "location" -> location;
                case "recovery" -> recovery;
                case "reinforceIcon" -> reinforceIcon;
                case "reinforceLabel" -> reinforceLabel;
                case "reinforceDisabled" -> reinforceDisabled;
                case "open" -> open;
                case "reinforce" -> reinforce;
                default -> throw new IllegalArgumentException("Unknown squad-card property");
            };
        }
    }

    public record SquadGalleryCard(
            String id, String classes, SquadCard squad,
            String openClasses, String reinforceClasses, String founderClasses,
            String founderId, String plusId,
            String founderHeadingId, String founderCopyId,
            String founderCostsId, String founderFeedbackId,
            List<CargoCostRow> foundingCargoRows, String foundingFeedbackText,
            String foundingFeedbackClasses, boolean foundingDisabled, Runnable foundSquad)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "classes" -> classes;
                case "squad" -> squad;
                case "openClasses" -> openClasses;
                case "reinforceClasses" -> reinforceClasses;
                case "founderClasses" -> founderClasses;
                case "founderId" -> founderId;
                case "plusId" -> plusId;
                case "founderHeadingId" -> founderHeadingId;
                case "founderCopyId" -> founderCopyId;
                case "founderCostsId" -> founderCostsId;
                case "founderFeedbackId" -> founderFeedbackId;
                case "foundingCargoRows" -> foundingCargoRows;
                case "foundingFeedbackText" -> foundingFeedbackText;
                case "foundingFeedbackClasses" -> foundingFeedbackClasses;
                case "foundingDisabled" -> foundingDisabled;
                case "foundSquad" -> foundSquad;
                default -> throw new IllegalArgumentException(
                        "Unknown squad-gallery-card property: " + property);
            };
        }
    }

    public record CargoCostRow(
            String id, String iconId, String labelId, String classes,
            String icon, String label) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "iconId" -> iconId;
                case "labelId" -> labelId;
                case "classes" -> classes;
                case "icon" -> icon;
                case "label" -> label;
                default -> throw new IllegalArgumentException(
                        "Unknown cargo-cost property: " + property);
            };
        }
    }

    public record FireTeamOverview(
            String id, String classes, String name, Runnable select)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "classes" -> classes;
                case "name" -> name;
                case "select" -> select;
                default -> throw new IllegalArgumentException("Unknown fire-team property");
            };
        }
    }

    public record MarineViewerCard(
            String id, String contentId, String canvasId, String headerId, String heroId,
            String identityId,
            String badgesId, String classBadgeId, String weaponBadgeId,
            String armorBadgeId, String nameId, String roleId,
            String statusId, String serviceId, String personnelId, String primaryId,
            String primaryFactionLogoId, String primaryTooltipFactionLogoId,
            String primaryTextId, String primaryTooltipTextId, String primaryDescriptionId,
            String weaponDetailId, String weaponStatsId,
            String armorId, String armorFactionLogoId, String armorTooltipFactionLogoId,
            String armorTextId, String armorTooltipTextId, String armorDescriptionId,
            String armorDetailId,
            String armorStatsId, String specialId, String specialFactionLogoId,
            String specialTooltipFactionLogoId, String specialTextId,
            String specialTooltipTextId, String specialDescriptionId,
            String systemId, String systemFactionLogoId, String systemTooltipFactionLogoId,
            String systemTextId, String systemTooltipTextId, String systemDescriptionId,
            String weaponDeltaId, String armorDeltaId,
            String careerId, String equipmentId, String weaponColumnId,
            String armorColumnId, String classes, String statusClasses,
            String name, String role, String status, String service, String personnel,
            String unitClass, String weaponBadge, String armorBadge,
            String primary, String primaryDescription, String primaryFactionLogo,
            String primaryFactionLogoClasses, List<StatMeter> weaponStats,
            String armor, List<StatMeter> armorStats, String armorDescription,
            String armorFactionLogo, String armorFactionLogoClasses,
            String special, String specialDescription, String specialFactionLogo,
            String specialFactionLogoClasses,
            String system, String systemDescription, String systemFactionLogo,
            String systemFactionLogoClasses, String systemClasses,
            String weaponDelta, String armorDelta,
            String deltaClasses, String career) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "contentId" -> contentId;
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
                case "primaryFactionLogoId" -> primaryFactionLogoId;
                case "primaryTooltipFactionLogoId" -> primaryTooltipFactionLogoId;
                case "primaryTextId" -> primaryTextId;
                case "primaryTooltipTextId" -> primaryTooltipTextId;
                case "primaryDescriptionId" -> primaryDescriptionId;
                case "weaponDetailId" -> weaponDetailId;
                case "weaponStatsId" -> weaponStatsId;
                case "armorId" -> armorId;
                case "armorFactionLogoId" -> armorFactionLogoId;
                case "armorTooltipFactionLogoId" -> armorTooltipFactionLogoId;
                case "armorTextId" -> armorTextId;
                case "armorTooltipTextId" -> armorTooltipTextId;
                case "armorDescriptionId" -> armorDescriptionId;
                case "armorDetailId" -> armorDetailId;
                case "armorStatsId" -> armorStatsId;
                case "specialId" -> specialId;
                case "specialFactionLogoId" -> specialFactionLogoId;
                case "specialTooltipFactionLogoId" -> specialTooltipFactionLogoId;
                case "specialTextId" -> specialTextId;
                case "specialTooltipTextId" -> specialTooltipTextId;
                case "specialDescriptionId" -> specialDescriptionId;
                case "systemId" -> systemId;
                case "systemFactionLogoId" -> systemFactionLogoId;
                case "systemTooltipFactionLogoId" -> systemTooltipFactionLogoId;
                case "systemTextId" -> systemTextId;
                case "systemTooltipTextId" -> systemTooltipTextId;
                case "systemDescriptionId" -> systemDescriptionId;
                case "weaponDeltaId" -> weaponDeltaId;
                case "armorDeltaId" -> armorDeltaId;
                case "careerId" -> careerId;
                case "equipmentId" -> equipmentId;
                case "weaponColumnId" -> weaponColumnId;
                case "armorColumnId" -> armorColumnId;
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
                case "primaryFactionLogo" -> primaryFactionLogo;
                case "primaryFactionLogoClasses" -> primaryFactionLogoClasses;
                case "weaponStats" -> weaponStats;
                case "armor" -> armor;
                case "armorStats" -> armorStats;
                case "armorDescription" -> armorDescription;
                case "armorFactionLogo" -> armorFactionLogo;
                case "armorFactionLogoClasses" -> armorFactionLogoClasses;
                case "special" -> special;
                case "specialDescription" -> specialDescription;
                case "specialFactionLogo" -> specialFactionLogo;
                case "specialFactionLogoClasses" -> specialFactionLogoClasses;
                case "system" -> system;
                case "systemDescription" -> systemDescription;
                case "systemFactionLogo" -> systemFactionLogo;
                case "systemFactionLogoClasses" -> systemFactionLogoClasses;
                case "systemClasses" -> systemClasses;
                case "weaponDelta" -> weaponDelta;
                case "armorDelta" -> armorDelta;
                case "deltaClasses" -> deltaClasses;
                case "career" -> career;
                default -> throw new IllegalArgumentException("Unknown marine-card property");
            };
        }
    }

    private record Feedback(String text, boolean succeeded) {
        private static Feedback neutral(String text) { return new Feedback(text, false); }
        private static Feedback success(String text) { return new Feedback(text, true); }
    }
}
