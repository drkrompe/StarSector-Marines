package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.ArmorTradition;
import com.dillon.starsectormarines.marine.MilitaryWarrants;
import com.dillon.starsectormarines.marine.EquipmentAccessTier;
import com.dillon.starsectormarines.marine.LoadoutCard;
import com.dillon.starsectormarines.marine.BilletRoles;
import com.dillon.starsectormarines.ops.CompanyResources.Row;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.marine.SquadExperienceStandard;
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
import com.dillon.starsectormarines.marine.ArmorRole;
import com.dillon.starsectormarines.marine.ArmorClass;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.LoadoutEffectiveness;
import com.dillon.starsectormarines.marine.MarinePersonnelLogistics;
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
import com.dillon.starsectormarines.marine.SquadWeaponDoctrine;
import com.dillon.starsectormarines.marine.SquadWeaponIssue;
import com.dillon.starsectormarines.ops.fitting.FittingFilterPill;
import com.dillon.starsectormarines.ops.spec.CatalogCeilings;
import com.dillon.starsectormarines.ops.spec.IntegralSystemCopy;
import com.dillon.starsectormarines.ops.spec.SpecSheets;
import com.dillon.starsectormarines.ops.spec.StatMeter;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.spec.RatingSymbols;
import com.dillon.starsectormarines.ui.spec.SpecSheet;
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
import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * Retained presentation adapter for one squad's equipment issue.
 * It owns selection and copy only; every inventory answer and mutation comes from
 * {@link MarineRoster}'s squad-wide preview/apply transaction.
 */
public final class FleetArmoryViewModel {

    public enum EquipmentPickerKind {
        WEAPON,
        ARMOR
    }

    /**
     * The browser's filter: by the tier a card shows, which is how cards are compared. None
     * chosen shows every tier, so there is no "all" to choose; choosing the chosen one clears it,
     * as the Mech Lab's weight filter does.
     */
    public enum LoadoutFilter {
        TIER_1("openingLoadoutFilterTier1", 1),
        TIER_2("openingLoadoutFilterTier2", 2),
        TIER_3("openingLoadoutFilterTier3", 3),
        TIER_4("openingLoadoutFilterTier4", 4);

        private final String labelKey;
        private final int tier;

        LoadoutFilter(String label, int tier) {
            this.labelKey = label;
            this.tier = tier;
        }

        String label() { return Strings.get(labelKey); }

        /** The filter a card of {@code tier} answers to; a tier past the last reads as the last. */
        static LoadoutFilter of(int tier) {
            LoadoutFilter[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, tier - 1))];
        }

        /** Its symbol: one chevron a tier, dark on the tier's tone. */
        String icon() { return "graphics/ui/equipment/tier-" + tier + ".svg"; }

        /** The tier's tone, on the stores' ramp from pale to amber to red. */
        String tone() {
            return switch (this) {
                case TIER_1 -> "#c9c3a0";
                case TIER_2 -> "#e2c068";
                case TIER_3 -> "#e2a248";
                case TIER_4 -> "#d8604c";
            };
        }
    }

    private final MarineRoster roster;
    private final DoubleSupplier currentDay;
    private final EquipmentIssueResources equipmentIssueResources;
    private final MutableSignal<String> selectedSquadId;
    private final MutableSignal<Integer> selectedTeamIndex;
    private final MutableSignal<Integer> inspectedBillet;
    private final ComputedSignal<Map<String, Object>> inspection;
    private final MutableSignal<String> selectedWeaponDoctrineId;
    private final MutableSignal<String> selectedArmorDoctrineId;
    private final MutableSignal<EquipmentPickerKind> equipmentPickerKind;
    private final MutableSignal<LoadoutFilter> loadoutFilter;
    private final MutableSignal<Integer> domainRevision;
    private final MutableSignal<Feedback> feedback;
    private final ComputedSignal<String> companySummary;
    private final ComputedSignal<List<FireTeamOverview>> fireTeamOverviews;
    private final ComputedSignal<List<SelectionRow>> squadRows;
    private final ComputedSignal<List<SelectionRow>> teamRows;
    private final ComputedSignal<List<MarineViewerCard>> marineCards;
    private final ComputedSignal<String> targetSummary;
    private final ComputedSignal<String> candidateSummary;
    private final ComputedSignal<String> transactionSummary;
    private final ComputedSignal<String> transactionClasses;
    private final ComputedSignal<Boolean> applyDisabled;
    private final ComputedSignal<String> feedbackText;
    private final ComputedSignal<String> feedbackClasses;
    private final ComputedSignal<String> selectedSquadReadiness;
    private final ComputedSignal<List<DoctrineTile>> weaponDoctrineTiles;
    private final ComputedSignal<List<DoctrineTile>> armorDoctrineTiles;
    private final ComputedSignal<SquadEquipmentPreview> squadEquipmentPreview;
    private final ComputedSignal<List<Row>> issueCargoRows;
    private final ComputedSignal<String> issueCargoClasses;
    private final ComputedSignal<String> weaponPickerTabClasses;
    private final ComputedSignal<String> armorPickerTabClasses;
    private final ComputedSignal<String> weaponPickerPanelClasses;
    private final ComputedSignal<String> armorPickerPanelClasses;
    private final ComputedSignal<List<FittingFilterPill>> loadoutFilters;
    private final ComputedSignal<String> loadoutBrowserSummary;

    public FleetArmoryViewModel(Reactor reactor, MarineRoster roster) {
        this(reactor, roster, () -> 0d, EquipmentIssueResources.UNLIMITED);
    }

    public FleetArmoryViewModel(Reactor reactor, MarineRoster roster, DoubleSupplier currentDay) {
        this(reactor, roster, currentDay, EquipmentIssueResources.UNLIMITED);
    }

    public FleetArmoryViewModel(
            Reactor reactor, MarineRoster roster,
            DoubleSupplier currentDay, EquipmentIssueResources equipmentIssueResources) {
        if (reactor == null) throw new IllegalArgumentException("reactor is required");
        if (roster == null) throw new IllegalArgumentException("roster is required");
        if (currentDay == null) throw new IllegalArgumentException("currentDay is required");
        if (equipmentIssueResources == null) {
            throw new IllegalArgumentException("equipmentIssueResources is required");
        }
        this.roster = roster;
        this.currentDay = currentDay;
        this.equipmentIssueResources = equipmentIssueResources;

        MarineSquad initialSquad = firstLineSquad(roster);
        selectedSquadId = reactor.signal(initialSquad != null ? initialSquad.id() : null);
        selectedTeamIndex = reactor.signal(0);
        inspectedBillet = reactor.signal(0);
        selectedWeaponDoctrineId = reactor.signal(initialSquad != null
                && initialSquad.weaponDoctrineId() != null
                ? initialSquad.weaponDoctrineId()
                : SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS);
        selectedArmorDoctrineId = reactor.signal(initialSquad != null
                && initialSquad.armorDoctrineId() != null
                ? initialSquad.armorDoctrineId()
                : SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);
        equipmentPickerKind = reactor.signal(EquipmentPickerKind.WEAPON);
        loadoutFilter = reactor.signal(null);
        domainRevision = reactor.signal(0);
        feedback = reactor.signal(Feedback.neutral(
                Strings.get("openingFleetArmoryHoverEquipmentNamesForFieldNotesAssign")));

        companySummary = reactor.computed(this::buildCompanySummary);
        fireTeamOverviews = reactor.computed(this::buildFireTeamOverviews);
        squadRows = reactor.computed(this::buildSquadRows);
        teamRows = reactor.computed(this::buildTeamRows);
        marineCards = reactor.computed(this::buildMarineCards);
        squadEquipmentPreview = reactor.computed(this::buildSquadEquipmentPreview);
        inspection = reactor.computed(() -> {
            domainRevision.get();
            Map<String, Object> projected = new LinkedHashMap<>(LoadoutInspection.project(roster,
                    roster.squadById(selectedSquadId.get()), squadEquipmentPreview.get(),
                    inspectedBillet.get(), inspectedBillet::set));
            projected.put("cards", squadCards());
            return projected;
        });
        targetSummary = reactor.computed(this::buildTargetSummary);
        candidateSummary = reactor.computed(this::buildCandidateSummary);
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
        weaponDoctrineTiles = reactor.computed(this::buildWeaponDoctrineTiles);
        armorDoctrineTiles = reactor.computed(this::buildArmorDoctrineTiles);
        weaponPickerTabClasses = reactor.computed(() -> equipmentPickerKind.get()
                == EquipmentPickerKind.WEAPON
                ? "equipment-picker-tab spec-tab selected" : "equipment-picker-tab spec-tab");
        armorPickerTabClasses = reactor.computed(() -> equipmentPickerKind.get()
                == EquipmentPickerKind.ARMOR
                ? "equipment-picker-tab spec-tab selected" : "equipment-picker-tab spec-tab");
        weaponPickerPanelClasses = reactor.computed(() -> equipmentPickerKind.get()
                == EquipmentPickerKind.WEAPON
                ? "doctrine-slot" : "doctrine-slot picker-hidden");
        armorPickerPanelClasses = reactor.computed(() -> equipmentPickerKind.get()
                == EquipmentPickerKind.ARMOR
                ? "doctrine-slot" : "doctrine-slot picker-hidden");
        loadoutFilters = reactor.computed(this::buildLoadoutFilters);
        loadoutBrowserSummary = reactor.computed(this::buildLoadoutBrowserSummary);
    }

    public MarineRoster roster() { return roster; }
    public Signal<String> companySummary() { return companySummary; }
    public Signal<List<FireTeamOverview>> fireTeamOverviews() { return fireTeamOverviews; }
    public Signal<List<SelectionRow>> squadRows() { return squadRows; }
    public Signal<List<SelectionRow>> teamRows() { return teamRows; }
    public Signal<Map<String, Object>> inspection() { return inspection; }

    public Signal<List<MarineViewerCard>> marineCards() { return marineCards; }
    public Signal<String> targetSummary() { return targetSummary; }
    public Signal<String> candidateSummary() { return candidateSummary; }
    public Signal<String> transactionSummary() { return transactionSummary; }
    public Signal<String> transactionClasses() { return transactionClasses; }
    public Signal<Boolean> applyDisabled() { return applyDisabled; }
    public Signal<String> feedbackText() { return feedbackText; }
    public Signal<String> feedbackClasses() { return feedbackClasses; }
    public Signal<String> selectedSquadReadiness() { return selectedSquadReadiness; }
    public Signal<List<DoctrineTile>> weaponDoctrineTiles() { return weaponDoctrineTiles; }
    public Signal<List<DoctrineTile>> armorDoctrineTiles() { return armorDoctrineTiles; }
    public Signal<List<Row>> issueCargoRows() { return issueCargoRows; }
    public Signal<String> issueCargoClasses() { return issueCargoClasses; }
    public Signal<String> weaponPickerTabClasses() { return weaponPickerTabClasses; }
    public Signal<String> armorPickerTabClasses() { return armorPickerTabClasses; }
    public Signal<String> weaponPickerPanelClasses() { return weaponPickerPanelClasses; }
    public Signal<String> armorPickerPanelClasses() { return armorPickerPanelClasses; }
    public Signal<List<FittingFilterPill>> loadoutFilters() { return loadoutFilters; }
    public Signal<String> loadoutBrowserSummary() { return loadoutBrowserSummary; }
    public String selectedSquadId() { return selectedSquadId.peek(); }
    public int selectedTeamIndex() { return selectedTeamIndex.peek(); }
    public String selectedWeaponDoctrineId() { return selectedWeaponDoctrineId.peek(); }
    public String selectedArmorDoctrineId() { return selectedArmorDoctrineId.peek(); }
    public EquipmentPickerKind equipmentPickerKind() { return equipmentPickerKind.peek(); }
    public LoadoutFilter loadoutFilter() { return loadoutFilter.peek(); }
    public String selectedSquadName() {
        MarineSquad squad = roster.squadById(selectedSquadId.peek());
        return squad != null ? squad.name() : Strings.get("openingFleetArmorySquad");
    }
    /** The page header's path to this squad's issue; it stands in for the breadcrumb the screen once drew. */
    public String issueContextLabel() {
        return Strings.format("openingFleetArmoryIssueContext", selectedSquadName().toUpperCase(Locale.ROOT));
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

    /** Chooses a tier, or clears it when it is already chosen. */
    public Runnable showLoadoutFilterAction(LoadoutFilter filter) {
        if (filter == null) throw new IllegalArgumentException("filter is required");
        return () -> loadoutFilter.set(filter == loadoutFilter.peek() ? null : filter);
    }

    private boolean filterAccepts(int shownTier) {
        LoadoutFilter filter = loadoutFilter.get();
        return filter == null || filter.tier == shownTier;
    }

    /** Reprojects campaign time, personnel, and cargo authority. */
    public void refresh() {
        domainRevision.update(value -> value + 1);
    }

    /** Value-only trial request. Previewing never issues kit or lends campaign identity. */
    public record RangeCandidate(String name, MarineLoadout loadout) { }

    public String rangeUnavailableReason() {
        domainRevision.get();
        MarineSquad squad = roster.squadById(selectedSquadId.get());
        if (squad == null || squad.stationed()) return Strings.get("armoryRangeAway");
        List<String> members = roster.manningMemberIds(squad);
        int index = inspectedBillet.get();
        MarineSoldier soldier = index >= 0 && index < members.size()
                ? roster.soldierById(members.get(index)) : null;
        if (soldier == null) return Strings.get("armoryRangeVacant");
        if (soldier.status() != MarineSoldierStatus.ACTIVE) return Strings.get("armoryRangeUnfit");
        if (!squadEquipmentPreview.get().canApply()) return Strings.get("armoryRangeChooseKit");
        return "";
    }

    public RangeCandidate rangeCandidate() {
        if (!rangeUnavailableReason().isEmpty()) return null;
        MarineSquad squad = roster.squadById(selectedSquadId.get());
        int index = inspectedBillet.get();
        MarineSoldier soldier = roster.soldierById(roster.manningMemberIds(squad).get(index));
        FireTeamBillet kit = asFireTeamBillet(squadEquipmentPreview.get().billet(index));
        MarineArmorCatalogDef armor = kit.armorDef();
        MarineLoadout loadout = MarineLoadout.fromCatalog(UnitRole.COMBATANT, null,
                kit.primaryDef(), kit.grade(), SquadExperienceStandard.profileForArmor(soldier, armor),
                kit.specialDef(), null, armor.appearanceFamily(), armor.armorCapacity(),
                armor.armorRating(), armor.moveSpeedMult(), armor.incomingAccuracyMult(),
                null, armor.integralSystem()).withArmorClass(armor.armorClass());
        return new RangeCandidate(soldier.name(), loadout);
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
                ? Feedback.success(Strings.format("openingFleetArmorySquadEquipmentIssuedTo", (squad != null ? squad.name() : Strings.get("openingFleetArmorySelectedSquad")), (preview.issueCost().isZero() ? Strings.get("openingFleetArmoryNoCargoRequired")
                        : Strings.format("openingFleetArmoryConsumed", preview.issueCost().display()))))
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
        return Strings.format("openingFleetArmorySquadsRtdWiaEquipmentTemplatesCollected", lineSquads, ready, wounded, roster.armory().equipmentTemplateCards().size());
    }

    private List<SelectionRow> buildSquadRows() {
        domainRevision.get();
        String selected = selectedSquadId.get();
        List<SelectionRow> rows = new ArrayList<>();
        for (MarineSquad squad : roster.squads()) {
            boolean unavailable = squad.reserve();
            String label = Strings.format("openingFleetArmoryRtd", squad.name(), roster.readyCount(squad), MarineSquad.CAPACITY, (squad.stationed() ? "  ·  Stationed" : unavailable ? "  ·  Reserve" : ""));
            rows.add(new SelectionRow("squad:" + squad.id(), label,
                    squad.id().equals(selected) ? "selection-row selected" : "selection-row",
                    unavailable, () -> selectSquad(squad.id())));
        }
        return List.copyOf(rows);
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
            String label = Strings.format("openingFleetArmoryRtd3", teamName(team), readyTeamMembers(squad, team), MarineSquad.TEAM_SIZE, assigned);
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
            boolean available = roster.armory().knowsWeaponCard(doctrine.id());
            if (!available) continue;
            SquadLoadoutPresentationDef presentation = loadoutPresentation(
                    doctrine.id(), SquadLoadoutPresentationDef.Kind.WEAPON,
                    maximumWeaponTier(doctrine), doctrine.description());
            if (!filterAccepts(presentation.tier())) continue;
            String id = "weapon-doctrine:" + doctrine.id();
            SquadEquipmentPreview candidate = roster.previewSquadEquipment(
                    selectedSquadId.get(), doctrine.id(), selectedArmorDoctrineId.get(),
                    equipmentIssueResources);
            tiles.add(weaponTile(id, doctrine, doctrine.id().equals(selected),
                    () -> selectWeaponDoctrine(doctrine.id()))
                    .withIssue(() -> issueWeaponDoctrine(doctrine.id()),
                            !candidate.canApply(), issueReason(candidate),
                            doctrine.id().equals(currentSquadWeaponDoctrine())));
        }
        return rankedByRating(tiles);
    }

    /** Shared card presentation for the technology offer and the learned Armory kit. */
    static DoctrineTile weaponTile(String id, SquadWeaponDoctrine doctrine, boolean selected, Runnable select) {
        SquadLoadoutPresentationDef presentation = loadoutPresentation(doctrine.id(),
                SquadLoadoutPresentationDef.Kind.WEAPON, maximumWeaponTier(doctrine), doctrine.description());
        List<DoctrineSpan> issue = weaponDistributionSpans(id, doctrine);
        return doctrineTile(id, selected, doctrine.displayName(), LoadoutEffectiveness.weaponRating(doctrine),
                presentation, presentation.provenance(), presentation.tier(), weaponComposition(doctrine),
                joinSpans(issue), issue, "", List.of(),
                cardStamp(new LoadoutCard(LoadoutCard.Kind.WEAPON, doctrine.id()),
                        traditionByCrest(presentation.factionLogo())), weaponSeats(doctrine),
                weaponSeatSheets(doctrine), select);
    }

    /**
     * How a loadout card reads where it is named outside the Armory: its name, an icon, its
     * hover sheet, and whether it bears the Restricted seal.
     */
    record CardFace(String name, String icon, SpecSheet sheet, boolean restricted) { }

    /**
     * The stamp a card is printed with: the office that issued it, with a serial of its own,
     * and for a Restricted card the warrant it answers to. The issuer of a Restricted card is
     * its warrant's issuer; any other card is issued by the tradition that fields it.
     */
    static SpecSheet.Stamp cardStamp(LoadoutCard card, ArmorTradition maker) {
        boolean restricted = card.defined() && card.accessTier() == EquipmentAccessTier.RESTRICTED;
        ArmorTradition warrantIssuer = restricted ? MilitaryWarrants.issuerOf(card) : null;
        ArmorTradition issuer = warrantIssuer != null ? warrantIssuer
                : maker != null ? maker : ArmorTradition.INDEPENDENT;
        String serial = Integer.toString(1000 + Math.floorMod(card.id().hashCode(), 9000));
        String issued = Strings.format("loadoutCardIssuedFmt", Strings.get("loadoutCardIssuer" + issuer.name()),
                Strings.get("loadoutCardIssuerCode" + issuer.name()), serial);
        String restriction = !restricted ? null : warrantIssuer != null
                ? Strings.format("loadoutCardRestrictedFmt", warrantIssuer.displayName())
                : Strings.get("loadoutCardRestricted");
        return new SpecSheet.Stamp(issued, restriction);
    }

    /** The tradition whose crest a weapon card's presentation carries; null when none does. */
    private static ArmorTradition traditionByCrest(String crest) {
        if (crest == null) return null;
        for (ArmorTradition tradition : ArmorTradition.values()) {
            if (tradition.factionLogo().equals(crest)) return tradition;
        }
        return null;
    }

    /** A weapon card's face, iconed by the weapon most of its seats carry. */
    static CardFace weaponCardFace(SquadWeaponDoctrine doctrine) {
        SquadLoadoutPresentationDef presentation = loadoutPresentation(doctrine.id(),
                SquadLoadoutPresentationDef.Kind.WEAPON, maximumWeaponTier(doctrine), doctrine.description());
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SquadWeaponIssue issue : doctrine.issues()) counts.merge(issue.primaryId(), 1, Integer::sum);
        String common = counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        String icon = null;
        if (common != null) {
            for (SquadWeaponIssue issue : doctrine.issues()) {
                if (!issue.primaryId().equals(common)) continue;
                icon = EquipmentIcons.primary(WeaponRegistry.require(common), issue.grade());
                break;
            }
        }
        int rating = LoadoutEffectiveness.weaponRating(doctrine);
        SpecSheet.Stamp stamp = cardStamp(new LoadoutCard(LoadoutCard.Kind.WEAPON, doctrine.id()),
                traditionByCrest(presentation.factionLogo()));
        return new CardFace(doctrine.displayName(), icon, cardSheet(doctrine.displayName(), presentation,
                doctrineMetadata(presentation), "weapon", "openingBarracksCardFire", rating,
                List.of(weaponComposition(doctrine), joinSpans(weaponDistributionSpans("card-face", doctrine))))
                .withSeats(weaponSeats(doctrine), fireTeams()).withStamp(stamp), stamp.restricted());
    }

    /** What hovering each seat of a weapon card describes: the weapon at the grade it is issued. */
    private static List<SpecSheet> weaponSeatSheets(SquadWeaponDoctrine doctrine) {
        List<SpecSheet> sheets = new ArrayList<>();
        for (SquadWeaponIssue issue : doctrine.issues()) sheets.add(SpecSheets.weapon(issue.primaryDef(), issue.grade()));
        return sheets;
    }

    /** What hovering each seat of a tactic sheet describes: the suit it issues. */
    private static List<SpecSheet> armorSeatSheets(SquadArmorDoctrine issued) {
        List<SpecSheet> sheets = new ArrayList<>();
        for (String armorId : issued.issueIds()) sheets.add(SpecSheets.armor(MarineArmorCatalogRegistry.require(armorId)));
        return sheets;
    }

    /** Each seat's weapon, with the special it also carries on the corner. */
    private static List<SpecSheet.Seat> weaponSeats(SquadWeaponDoctrine doctrine) {
        List<SpecSheet.Seat> seats = new ArrayList<>();
        for (SquadWeaponIssue issue : doctrine.issues()) {
            SpecialEquipmentDef special = issue.specialEquipmentId() == null ? null : issue.specialDef();
            seats.add(new SpecSheet.Seat(EquipmentIcons.primary(issue.primaryDef(), issue.grade()),
                    special == null ? null : special.armoryIconPath()));
        }
        return seats;
    }

    private static List<String> fireTeams() {
        return List.of(Strings.get("openingLoadoutAlpha"), Strings.get("openingLoadoutBravo"),
                Strings.get("openingLoadoutCharlie"));
    }

    /** Each seat's suit, with the integral system it carries on the corner. */
    private static List<SpecSheet.Seat> armorSeats(SquadArmorDoctrine issued) {
        List<SpecSheet.Seat> seats = new ArrayList<>();
        for (String armorId : issued.issueIds()) {
            MarineArmorCatalogDef suit = MarineArmorCatalogRegistry.require(armorId);
            seats.add(new SpecSheet.Seat(suit.iconPath(), IntegralSystemCopy.iconPath(suit)));
        }
        return seats;
    }

    /** A tactic sheet's face, iconed by the suit most of its seats wear. */
    static CardFace armorCardFace(SquadArmorPlan plan, SquadArmorDoctrine issued) {
        SquadLoadoutPresentationDef presentation = loadoutPresentation(plan.id(),
                SquadLoadoutPresentationDef.Kind.ARMOR, maximumArmorTier(issued), plan.description());
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String armorId : issued.issueIds()) counts.merge(armorId, 1, Integer::sum);
        String common = counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        String icon = common == null ? null : MarineArmorCatalogRegistry.require(common).iconPath();
        String metadata = Strings.format("openingFleetArmoryFieldsTier", plan.armorClassDisplayName(),
                plan.tradition().displayName(), tierMark(maximumArmorTier(issued)));
        int rating = LoadoutEffectiveness.armorRating(issued);
        SpecSheet.Stamp stamp = cardStamp(new LoadoutCard(LoadoutCard.Kind.SHEET, plan.id()), plan.tradition());
        return new CardFace(plan.displayName(), icon, cardSheet(plan.displayName(), presentation, metadata,
                "armor", "openingBarracksCardGuard", rating,
                List.of(joinSpans(armorDistributionSpans("card-face", issued)),
                        armorCapabilities(armorCapabilitySpans("card-face", issued))))
                .withSeats(armorSeats(issued), fireTeams()).withStamp(stamp), stamp.restricted());
    }

    private static SpecSheet cardSheet(String name, SquadLoadoutPresentationDef presentation, String metadata,
                                       String accent, String ratingLabel, int rating, List<String> lines) {
        List<String> notes = new ArrayList<>();
        for (String line : lines) if (line != null && !line.isBlank()) notes.add(line);
        if (presentation.lore() != null && !presentation.lore().isBlank()) notes.add(presentation.lore());
        return new SpecSheet(name, presentation.rarity().displayName() + Strings.get("separatorDot") + metadata,
                presentation.factionLogo(), accent,
                List.of(new SpecSheet.Stat("rating", Strings.get(ratingLabel), rating + "%", rating / 100f,
                        ratingSymbol(accent))), notes);
    }

    /** The symbol a loadout's rating is drawn as: swords for firepower, shields for protection. */
    static String ratingSymbol(String accent) {
        return accent.equals("armor") ? RatingSymbols.SHIELD : RatingSymbols.SWORD;
    }

    static String weaponComposition(SquadWeaponDoctrine doctrine) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SquadWeaponIssue issue : doctrine.issues()) {
            String role = WeaponRegistry.require(issue.primaryId()).catalogRole();
            counts.merge(role, 1, Integer::sum);
        }
        return Strings.join("separatorDot", counts.entrySet().stream()
                .map(entry -> Strings.format("squadDoctrineCount", entry.getValue(), entry.getKey()))
                .toArray(String[]::new));
    }

    private List<DoctrineTile> buildArmorDoctrineTiles() {
        domainRevision.get();
        String selected = selectedArmorDoctrineId.get();
        List<DoctrineTile> tiles = new ArrayList<>();
        for (SquadArmorDoctrine issued : distinctArmorIssues()) {
            SquadArmorPlan plan = SquadEquipmentDoctrines.armorPlanById(issued.id());
            SquadLoadoutPresentationDef presentation = loadoutPresentation(
                    plan.id(), SquadLoadoutPresentationDef.Kind.ARMOR,
                    maximumArmorTier(issued), plan.description());
            if (!filterAccepts(maximumArmorTier(issued))) continue;
            String id = "armor-doctrine:" + plan.id();
            SquadEquipmentPreview candidate = roster.previewSquadEquipment(
                    selectedSquadId.get(), selectedWeaponDoctrineId.get(), plan.id(),
                    equipmentIssueResources);
            // The authored tier is what the sheet IS; the issued band is what
            // this company would actually put in the field today. Showing both
            // is the point of the picker under role-and-access.md — a sheet does
            // not get better, the kit filling it does.
            // The card shows the fielded tier as its chevrons, not in words.
            String metadata = Strings.join("separatorDot", plan.armorClassDisplayName(), plan.tradition().displayName());
            List<DoctrineSpan> patterns = armorDistributionSpans(id, issued);
            List<DoctrineSpan> capabilities = armorCapabilitySpans(id, issued);
            tiles.add(doctrineTile(id, plan.id().equals(selected),
                    plan.displayName(), LoadoutEffectiveness.armorRating(issued),
                    presentation, metadata, maximumArmorTier(issued),
                    roleComposition(plan), joinSpans(patterns), patterns,
                    armorCapabilities(capabilities), capabilities,
                    cardStamp(new LoadoutCard(LoadoutCard.Kind.SHEET, plan.id()), plan.tradition()),
                    armorSeats(issued), armorSeatSheets(issued), () -> selectArmorDoctrine(plan.id()))
                    .withIssue(() -> issueArmorDoctrine(plan.id()), !candidate.canApply(),
                            issueReason(candidate), plan.id().equals(currentSquadArmorDoctrine())));
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

    private static DoctrineTile doctrineTile(
            String id, boolean selected, String name, int rating,
            SquadLoadoutPresentationDef presentation, String metadata, int tier,
            String composition, String distribution, List<DoctrineSpan> distributionSpans,
            String carries, List<DoctrineSpan> carriesSpans, SpecSheet.Stamp stamp,
            List<SpecSheet.Seat> seats, List<SpecSheet> seatSheets, Runnable select) {
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
                Strings.format(presentation.kind() == SquadLoadoutPresentationDef.Kind.WEAPON ? "openingLoadoutFireRating" : "openingLoadoutGuardRating", rating), presentation.factionLogo(), metadata,
                presentation.lore(), composition, distribution, distributionSpans,
                carries, carriesSpans, stamp, seats, seatSheets, select, null, true, "", false, false, tier);
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
        return Strings.format("openingFleetArmoryTier", tierMark(presentation.tier()), presentation.provenance());
    }

    private List<FittingFilterPill> buildLoadoutFilters() {
        LoadoutFilter selected = loadoutFilter.get();
        List<FittingFilterPill> filters = new ArrayList<>();
        for (LoadoutFilter filter : LoadoutFilter.values()) {
            String id = "loadout-filter:" + filter.name().toLowerCase(Locale.ROOT);
            // While a tier is chosen the others dim, so the chosen one reads as on and
            // clicking it again (which shows every tier) reads as turning it off.
            String muted = selected != null && filter != selected ? " tier-pill-muted" : "";
            filters.add(new FittingFilterPill(id, filter.label(), filter.icon(), filter.tone(),
                    FittingFilterPill.classes(filter == selected) + " tier-pill" + muted,
                    showLoadoutFilterAction(filter)));
        }
        return List.copyOf(filters);
    }

    private String buildLoadoutBrowserSummary() {
        int known = equipmentPickerKind.get() == EquipmentPickerKind.WEAPON
                ? knownWeaponLoadouts() : knownArmorLoadouts();
        int shown = equipmentPickerKind.get() == EquipmentPickerKind.WEAPON
                ? weaponDoctrineTiles.get().size() : armorDoctrineTiles.get().size();
        return shown == known ? Strings.format("openingFleetArmoryKnown", known) : Strings.format("openingFleetArmoryOfKnown", shown, known);
    }

    private int knownWeaponLoadouts() {
        int known = 0;
        for (SquadWeaponDoctrine doctrine : roster.armory().weaponDoctrines()) {
            if (roster.armory().knowsWeaponCard(doctrine.id())) known++;
        }
        return known;
    }

    /** Learned, fieldable sheets, before rarity filtering and after identical-issue grouping. */
    private int knownArmorLoadouts() {
        return distinctArmorIssues().size();
    }

    /** Group identical issues only after the armory has checked each sheet's knowledge. */
    private List<SquadArmorDoctrine> distinctArmorIssues() {
        Map<List<String>, SquadArmorDoctrine> choices = new LinkedHashMap<>();
        for (SquadArmorDoctrine issue : roster.armory().armorDoctrines())
            choices.putIfAbsent(issue.issueIds(), issue);
        // Keep an existing assigned alias visible when it resolves to the same kit.
        String selected = selectedArmorDoctrineId.get();
        for (SquadArmorDoctrine issue : roster.armory().armorDoctrines())
            if (issue.id().equals(selected)) choices.put(issue.issueIds(), issue);
        return List.copyOf(choices.values());
    }

    private static SquadLoadoutPresentationDef loadoutPresentation(
            String id, SquadLoadoutPresentationDef.Kind kind, int tier, String fallbackLore) {
        SquadLoadoutPresentationDef authored = SquadLoadoutPresentationRegistry.get(id);
        return authored != null ? authored : new SquadLoadoutPresentationDef(
                id, kind, tier, SquadLoadoutRarity.COMMON,
                id.startsWith("wayline:") ? Strings.get("openingFleetArmoryWaylineTechnologyTransfer") : Strings.get("openingFleetArmoryCompanyAuthored"), null, fallbackLore != null ? fallbackLore : "");
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
            case 3 -> Strings.get("openingFleetArmoryIii");
            case 4 -> "IV";
            default -> "V";
        };
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
                    id + ":primary-text",
                    id + ":weapon-detail", id + ":weapon-stats",
                    id + ":armor", id + ":armor-faction-logo",
                    id + ":armor-text",
                    id + ":armor-detail",
                    id + ":armor-stats",
                    id + ":special", id + ":special-faction-logo",
                    id + ":special-text",
                    id + ":system", id + ":system-faction-logo",
                    id + ":system-text",
                    id + ":weapon-delta", id + ":armor-delta",
                    id + ":career", id + ":equipment", id + ":weapon-column",
                    id + ":armor-column",
                    marineCardClasses(soldier, previewing),
                    "marine-status heading " + marineStatusTone(soldier),
                    marineName(soldier), billet != null ? BilletRoles.label(billet.name()) : Strings.get("openingFleetArmoryUnfilledBillet"),
                    marineStatus(soldier), serviceSummary(soldier),
                    personnelSummary(soldier),
                    armorCatalog != null ? armorCatalog.armorClass().displayName() : Strings.get("openingFleetArmoryVacant"),
                    billet != null ? Strings.format("openingFleetArmoryW", billet.grade().tierMark()) : Strings.get("openingFleetArmoryW2"),
                    billet != null ? Strings.format("openingFleetArmoryA", tierMark(armorCatalog.tier())) : Strings.get("openingFleetArmoryA2"),
                    billet != null ? primary.catalogName(billet.grade().tier) + "  ·  "
                            + billet.grade().displayName() : Strings.get("openingFleetArmoryNoPrimary"),
                    primaryFactionLogo, equipmentFactionLogoClasses(primaryFactionLogo),
                    weaponStats(id, billet, soldier),
                    billet != null ? Strings.format("openingFleetArmoryTier3", armorCatalog.displayName(), armorCatalog.role().displayName(), tierMark(armorCatalog.tier())) : Strings.get("openingFleetArmoryNoArmor"),
                    armorStats(id, billet),
                    armorFactionLogo, equipmentFactionLogoClasses(armorFactionLogo),
                    special != null ? special.displayName() : Strings.get("openingFleetArmoryNoSpecialtyEquipment"),
                    specialFactionLogo, equipmentFactionLogoClasses(specialFactionLogo),
                    IntegralSystemCopy.summary(armorCatalog),
                    systemFactionLogo, equipmentFactionLogoClasses(systemFactionLogo),
                    systemClasses(armorCatalog),
                    weaponDelta(billet, soldier, previewing),
                    armorDelta(billet, soldier, previewing),
                    previewing ? "marine-delta label tone-accent"
                            : "marine-delta label picker-closed-line",
                    careerSummary(soldier),
                    billet != null ? SpecSheets.weapon(primary, billet.grade()) : null,
                    armorCatalog != null ? SpecSheets.armor(armorCatalog) : null,
                    special != null ? SpecSheets.special(special) : null,
                    IntegralSystemCopy.carried(armorCatalog)
                            ? SpecSheets.integralSystem(armorCatalog) : null));
        }
        return List.copyOf(marines);
    }

    private String buildTargetSummary() {
        domainRevision.get();
        MarineSquad squad = roster.squadById(selectedSquadId.get());
        return squad == null ? Strings.get("openingFleetArmorySelectALineSquad")
                : Strings.format("openingFleetArmoryBilletSquadIssue", squad.name());
    }

    private String buildCandidateSummary() {
        domainRevision.get();
        SquadWeaponDoctrine weapons = roster.armory().weaponDoctrineById(
                selectedWeaponDoctrineId.get());
        return Strings.format("openingFleetArmoryProposed", (weapons != null ? weapons.displayName() : Strings.get("openingFleetArmoryChooseWeapons")), ArmorPlanPresentation.label(roster.armory(), selectedArmorDoctrineId.get()));
    }

    /** Why a card's Issue is refused; empty when it can be pressed. */
    private String issueReason(SquadEquipmentPreview candidate) {
        return candidate.canApply() ? "" : squadEquipmentMessage(candidate);
    }

    /** The selected squad's two issued cards, as the squad view opens on them ({@link CardChips}). */
    private List<Map<String, Object>> squadCards() {
        MarineSquad squad = roster.squadById(selectedSquadId.get());
        List<Map<String, Object>> cards = new ArrayList<>();
        if (squad == null) return cards;
        SquadWeaponDoctrine weapons = roster.armory().weaponDoctrineById(squad.weaponDoctrineId());
        if (weapons != null) cards.add(CardChips.weapons("loadout-squad-card:weapons", weapons, false));
        SquadArmorPlan plan = SquadEquipmentDoctrines.armorPlanById(squad.armorDoctrineId());
        if (plan != null) cards.add(CardChips.sheet("loadout-squad-card:armor", plan, roster.armory().issue(plan), false));
        return cards;
    }

    private String currentSquadWeaponDoctrine() {
        MarineSquad squad = roster.squadById(selectedSquadId.get());
        return squad == null ? null : squad.weaponDoctrineId();
    }

    private String currentSquadArmorDoctrine() {
        MarineSquad squad = roster.squadById(selectedSquadId.get());
        return squad == null ? null : squad.armorDoctrineId();
    }

    private void issueWeaponDoctrine(String doctrineId) {
        if (roster.armory().weaponDoctrineById(doctrineId) == null) return;
        selectedWeaponDoctrineId.set(doctrineId);
        applySquadEquipmentSelection();
    }

    private void issueArmorDoctrine(String doctrineId) {
        if (roster.armory().armorDoctrineById(doctrineId) == null) return;
        selectedArmorDoctrineId.set(doctrineId);
        applySquadEquipmentSelection();
    }

    private List<Row> buildIssueCargoRows() {
        SquadEquipmentPreview preview = squadEquipmentPreview.get();
        EquipmentTemplateCost cost = preview.issueCost();
        EquipmentTemplateCost available = preview.availableCargo();
        List<Row> rows = new ArrayList<>(4);
        addRow(rows, Commodities.SUPPLIES, cost.supplies(), available.supplies());
        addRow(rows, Commodities.HAND_WEAPONS,
                cost.heavyArmaments(), available.heavyArmaments());
        addRow(rows, Commodities.HEAVY_MACHINERY,
                cost.heavyMachinery(), available.heavyMachinery());
        addRow(rows, Commodities.FOOD, cost.food(), available.food());
        return List.copyOf(rows);
    }

    private void addRow(List<Row> rows, String commodityId,
                                 int required, int available) {
        if (required <= 0) return;
        rows.add(CompanyResources.cost("issue-cargo:" + commodityId, equipmentIssueResources.commodityName(commodityId),
                equipmentIssueResources.commodityIcon(commodityId), available, required));
    }

    private String buildViewerStatus() {
        SquadEquipmentPreview preview = squadEquipmentPreview.get();
        return preview.canApply() ? "" : squadEquipmentMessage(preview);
    }

    private String buildViewerStatusClasses() {
        return squadEquipmentPreview.get().canApply()
                ? "transaction-result-empty"
                : "transaction-result danger-surface tone-danger";
    }

    public void selectSquad(String squadId) {
        MarineSquad squad = roster.squadById(squadId);
        if (squad == null || squad.reserve()) return;
        selectedSquadId.set(squadId);
        selectedTeamIndex.set(0);
        inspectedBillet.set(0);
        selectedWeaponDoctrineId.set(squad.weaponDoctrineId() != null
                ? squad.weaponDoctrineId() : SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS);
        selectedArmorDoctrineId.set(squad.armorDoctrineId() != null
                ? squad.armorDoctrineId() : SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);
    }

    private void selectTeam(int teamIndex) {
        selectedTeamIndex.set(teamIndex);
        feedback.set(Feedback.neutral(
                Strings.get("openingFleetArmoryInspectingThisFireTeamWithinTheSame")));
    }

    private void selectWeaponDoctrine(String doctrineId) {
        if (roster.armory().weaponDoctrineById(doctrineId) == null) return;
        selectedWeaponDoctrineId.set(doctrineId);
        feedback.set(Feedback.neutral(
                Strings.get("openingFleetArmoryWeaponEquipmentSelectedSpecialEquipmentFollowsThis")));
    }

    private void selectArmorDoctrine(String doctrineId) {
        if (roster.armory().armorDoctrineById(doctrineId) == null) return;
        selectedArmorDoctrineId.set(doctrineId);
        feedback.set(Feedback.neutral(Strings.get("openingFleetArmoryArmorEquipmentSelectedForAllTwelveBillets")));
    }

    private String buildSelectedSquadReadiness() {
        domainRevision.get();
        MarineSquad squad = roster.squadById(selectedSquadId.get());
        if (squad == null) return Strings.get("openingFleetArmorySelectALineSquad");
        int vacancies = roster.vacancies(squad);
        return Strings.format("openingFleetArmoryRtd4", roster.readyCount(squad), vacancies, (vacancies == 1 ? Strings.get("openingFleetArmoryOpenBillet") : Strings.get("openingFleetArmoryOpenBillets")), recoverySummary(squad));
    }

    private String assignedTemplateName(MarineSquad squad, int teamIndex) {
        FireTeamTemplateCard card = roster.armory().templateCardById(
                squad.teamTemplateCardId(teamIndex));
        return card != null ? card.displayName() : Strings.get("openingFleetArmoryUnassigned");
    }

    private String assignedDoctrineName(MarineSquad squad) {
        SquadWeaponDoctrine weapons = roster.armory().weaponDoctrineById(
                squad.weaponDoctrineId());
        if (weapons == null) return Strings.get("openingFleetArmoryIndividualEquipment");
        return weapons.displayName() + "  /  "
                + ArmorPlanPresentation.label(roster.armory(), squad.armorDoctrineId());
    }

    /**
     * The twelve-billet issue as one entry per weapon, each its own spec-sheet
     * subject ({@code ui-nouns.md}). The line still reads as the joined
     * sentence it always did — {@link #joinSpans} rebuilds it — but a reader
     * can only ask about a name the document knows is a name.
     *
     * <p>The specialty entry is a count of items that need not be the same
     * item. It carries a sheet when the loadout names exactly one and none at
     * all when it mixes them, because there is no single thing "2 special"
     * would then be describing.
     */
    private static List<DoctrineSpan> weaponDistributionSpans(
            String tileId, SquadWeaponDoctrine doctrine) {
        List<DoctrineSpan> spans = new ArrayList<>();
        for (WeaponDef weapon : WeaponRegistry.installed().all()) {
            if (weapon.mount != MountClass.MARINE_PRIMARY) continue;
            int count = 0;
            Map<EquipmentGrade, Integer> grades = new EnumMap<>(EquipmentGrade.class);
            for (SquadWeaponIssue issue : doctrine.issues()) {
                if (!issue.primaryId().equals(weapon.id)) continue;
                count++;
                grades.merge(issue.grade(), 1, Integer::sum);
            }
            if (count == 0) continue;
            spans.add(span(tileId, "distribution", spans.size(),
                    Strings.format("squadDoctrineCount", count, weapon.modelName()), weaponSheet(weapon, grades),
                    EquipmentIcons.primary(weapon, quotedGrade(grades))));
        }
        int specials = 0;
        String only = null;
        boolean mixed = false;
        for (SquadWeaponIssue issue : doctrine.issues()) {
            String specialId = issue.specialEquipmentId();
            if (specialId == null) continue;
            specials++;
            if (only == null) only = specialId;
            else if (!only.equals(specialId)) mixed = true;
        }
        if (specials > 0) {
            SpecialEquipmentDef def = mixed ? null : SpecialEquipmentRegistry.get(only);
            spans.add(span(tileId, "distribution", spans.size(),
                    Strings.format("openingFleetArmorySpecial", specials), def == null ? null : SpecSheets.special(def),
                    def == null ? "graphics/ui/equipment/firepower.svg" : def.armoryIconPath()));
        }
        return last(spans);
    }

    /**
     * The sheet for one weapon as <em>this loadout</em> issues it.
     *
     * <p>A loadout's billets each carry their own grade, so one weapon may
     * appear at several. The sheet is quoted at the grade <b>most</b> of its
     * carriers actually get, because a spec sheet reports what the simulation
     * applies rather than a catalog default, and the majority issue is the one
     * a player is deciding about. A tie goes to the higher grade — there is no
     * majority to quote, and the loadout was authored to reach it — which
     * cannot flatter the loadout silently, because a spread is named in the
     * subtitle either way.
     */
    private static SpecSheet weaponSheet(WeaponDef weapon,
                                         Map<EquipmentGrade, Integer> grades) {
        EquipmentGrade quoted = quotedGrade(grades);
        EquipmentGrade lowest = null;
        EquipmentGrade highest = null;
        for (Map.Entry<EquipmentGrade, Integer> issued : grades.entrySet()) {
            EquipmentGrade grade = issued.getKey();
            if (lowest == null || grade.tier < lowest.tier) lowest = grade;
            if (highest == null || grade.tier > highest.tier) highest = grade;
        }
        SpecSheet sheet = SpecSheets.weapon(weapon, quoted);
        if (lowest == highest) return sheet;
        return new SpecSheet(sheet.title(),
                Strings.format("openingFleetArmoryIssuedHere", sheet.subtitle(), lowest.displayName(), highest.displayName()),
                sheet.crestPath(), sheet.accent(), sheet.stats(), sheet.notes());
    }

    private static EquipmentGrade quotedGrade(Map<EquipmentGrade, Integer> grades) {
        return grades.entrySet().stream().max(Comparator
                .<Map.Entry<EquipmentGrade, Integer>>comparingInt(Map.Entry::getValue)
                .thenComparingInt(entry -> entry.getKey().tier)).orElseThrow().getKey();
    }

    /**
     * The section this sheet organises, as billet counts by role. This is the
     * half a player is actually choosing between: it does not change when the
     * company gets richer, which is exactly what distinguishes it from the issue
     * line beneath it ({@code role-and-access.md}).
     */
    private String roleComposition(SquadArmorPlan plan) {
        List<String> parts = new ArrayList<>();
        for (ArmorClass armorClass : ArmorClass.values()) {
            Map<ArmorRole, Integer> counts = new EnumMap<>(ArmorRole.class);
            for (int billet = 0; billet < MarineSquad.CAPACITY; billet++) {
                if (plan.armorClassAt(billet) == armorClass) counts.merge(plan.roleAt(billet), 1, Integer::sum);
            }
            for (ArmorRole role : ArmorRole.values()) {
                Integer count = counts.get(role);
                if (count == null) continue;
                String roleName = role.displayName().toLowerCase(Strings.locale());
                String part = plan.isMixedClass()
                        ? Strings.format("squadDoctrineRoleCountInClass", count, roleName, armorClass.displayName())
                        : Strings.format("squadDoctrineCount", count, roleName);
                if (!ownsAnyPatternFor(role, armorClass)) part = Strings.format("squadDoctrineRoleNoKit", part);
                parts.add(part);
            }
        }
        return Strings.join("separatorDot", parts.toArray(String[]::new));
    }

    private boolean ownsAnyPatternFor(ArmorRole role, ArmorClass armorClass) {
        MarineArmorCatalogRegistry catalog = MarineArmorCatalogRegistry.installed();
        if (catalog == null) return true;
        for (MarineArmorCatalogDef pattern : catalog.all()) {
            if (pattern.role() == role && pattern.armorClass() == armorClass
                    && roster.armory().ownsArmorTemplate(pattern.id())) {
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
    private static String armorCapabilities(List<DoctrineSpan> capabilities) {
        return capabilities.isEmpty()
                ? Strings.get("openingFleetArmoryNothingBeyondPlateAndTraining") : joinSpans(capabilities);
    }

    /**
     * The carried capabilities as spans.
     *
     * <p>A family is named rather than a pattern, and several patterns may
     * carry the same family under different names and different numbers — so
     * an entry carries the integral system's sheet only when this issue fills
     * it from one pattern. Where two patterns contribute to the same family
     * there is no one suit the entry describes, and quoting either one's clock
     * would be inventing a number this loadout does not field.
     */
    private static List<DoctrineSpan> armorCapabilitySpans(
            String tileId, SquadArmorDoctrine doctrine) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, MarineArmorCatalogDef> carriers = new LinkedHashMap<>();
        Map<String, Boolean> shared = new LinkedHashMap<>();
        for (String issueId : doctrine.issueIds()) {
            MarineArmorCatalogDef pattern = MarineArmorCatalogRegistry.installed().get(issueId);
            if (pattern == null || !pattern.hasIntegralSystem()) continue;
            String family = pattern.integralSystem().familyName();
            counts.merge(family, 1, Integer::sum);
            MarineArmorCatalogDef first = carriers.putIfAbsent(family, pattern);
            if (first != null && !first.id().equals(pattern.id())) shared.put(family, true);
        }
        if (counts.isEmpty()) return List.of();
        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(counts.entrySet());
        ranked.sort(Map.Entry.<String, Integer>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()));
        List<DoctrineSpan> spans = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : ranked) {
            String family = entry.getKey();
            SpecSheet sheet = shared.containsKey(family)
                    ? null : SpecSheets.integralSystem(carriers.get(family));
            spans.add(span(tileId, "carries", spans.size(),
                    Strings.format("squadDoctrineCount", entry.getValue(), family), sheet));
        }
        return last(spans);
    }

    private static List<DoctrineSpan> armorDistributionSpans(
            String tileId, SquadArmorDoctrine doctrine) {
        List<DoctrineSpan> spans = new ArrayList<>();
        for (MarineArmorCatalogDef pattern : MarineArmorCatalogRegistry.installed().all()) {
            int count = 0;
            for (String issueId : doctrine.issueIds()) {
                if (issueId.equals(pattern.id())) count++;
            }
            if (count == 0) continue;
            spans.add(span(tileId, "distribution", spans.size(),
                    Strings.format("squadDoctrineCount", count, pattern.displayName()), SpecSheets.armor(pattern), pattern.iconPath()));
        }
        return last(spans);
    }

    private static DoctrineSpan span(String tileId, String line, int index,
                                     String text, SpecSheet sheet) {
        return new DoctrineSpan(tileId + ":" + line + "-span:" + index, text, false, sheet, "");
    }

    private static DoctrineSpan span(String tileId, String line, int index,
                                     String text, SpecSheet sheet, String icon) {
        return new DoctrineSpan(tileId + ":" + line + "-span:" + index, text, false, sheet, icon);
    }

    /**
     * Marks the closing entry, which is the one that does not trail a
     * separator. The line reads as a sentence and only the last word of a
     * sentence ends it.
     */
    private static List<DoctrineSpan> last(List<DoctrineSpan> spans) {
        if (spans.isEmpty()) return List.of();
        DoctrineSpan closing = spans.get(spans.size() - 1);
        spans.set(spans.size() - 1,
                new DoctrineSpan(closing.id(), closing.text(), true, closing.sheet(), closing.icon()));
        return List.copyOf(spans);
    }

    /** The joined sentence the spans read as, for the line's plain fallback. */
    private static String joinSpans(List<DoctrineSpan> spans) {
        List<String> parts = new ArrayList<>();
        for (DoctrineSpan span : spans) parts.add(span.text());
        return Strings.join("separatorDot", parts.toArray(String[]::new));
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
        return wounded == 0 ? Strings.get("openingFleetArmoryNoWoundedPersonnel")
                : Strings.format("openingFleetArmoryWiaNextRtd", wounded, formatRemaining(earliest, currentDay.getAsDouble()));
    }

    static String formatRemaining(double unavailableUntilDay, double currentDay) {
        return remaining(unavailableUntilDay, currentDay, false);
    }

    static String formatRemainingCompact(double unavailableUntilDay, double currentDay) {
        return remaining(unavailableUntilDay, currentDay, true);
    }

    private static String remaining(double until, double now, boolean compact) {
        int hours = Math.max(0, (int) Math.ceil((until - now) * 24d));
        if (hours == 0) return Strings.get("openingRecoveryNow");
        String prefix = compact ? "openingRecoveryCompact" : "openingRecovery";
        if (hours < 24) return Strings.format(prefix + "Hours", hours);
        int days = hours / 24;
        int remainder = hours % 24;
        return remainder == 0 ? Strings.format(prefix + "Days", days)
                : Strings.format(prefix + "DaysHours", days, remainder);
    }

    private static MarineSquad firstLineSquad(MarineRoster roster) {
        for (MarineSquad squad : roster.squads()) if (!squad.reserve()) return squad;
        return null;
    }

    private static String teamName(int teamIndex) {
        return switch (teamIndex) {
            case 0 -> Strings.get("openingFleetArmoryAlpha");
            case 1 -> Strings.get("openingFleetArmoryBravo");
            case 2 -> Strings.get("openingFleetArmoryCharlie");
            default -> Strings.get("openingFleetArmoryTeam");
        };
    }

    private FireTeamBillet currentBillet(
            MarineSquad squad, int teamIndex, int billetIndex, MarineSoldier soldier) {
        FireTeamTemplateCard assigned = roster.armory().templateCardById(
                squad.teamTemplateCardId(teamIndex));
        String role = assigned != null && billetIndex < assigned.billets().size()
                ? assigned.billet(billetIndex).name()
                : billetIndex == 0 ? Strings.get("openingFleetArmoryTeamLeader") : Strings.get("openingFleetArmoryRifleman");
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
                : Strings.get("openingFleetArmoryVacantBillet");
    }

    private String marineStatus(MarineSoldier soldier) {
        if (soldier == null) return Strings.get("openingFleetArmoryVacant");
        return switch (soldier.status()) {
            case ACTIVE -> Strings.get("openingFleetArmoryReady");
            case WIA -> Strings.format("openingFleetArmoryWiaRtd2", formatRemaining(
                    soldier.unavailableUntilDay(), currentDay.getAsDouble()));
            case MIA -> Strings.get("openingFleetArmoryMissing");
            case KIA -> Strings.get("openingFleetArmoryKilled");
        };
    }

    private static String marineStatusTone(MarineSoldier soldier) {
        return soldier != null && soldier.status() == MarineSoldierStatus.ACTIVE
                ? "tone-good" : "tone-danger";
    }

    private static String serviceSummary(MarineSoldier soldier) {
        if (soldier == null) return Strings.get("openingFleetArmoryNoMarineAssigned");
        return Strings.format("openingFleetArmoryAptitudeIssue", soldier.profile().experienceTier().displayName, soldier.aptitude().displayName, soldier.armorDef().displayName());
    }

    private static String personnelSummary(MarineSoldier soldier) {
        if (soldier == null) return Strings.get("openingFleetArmoryNoPersonnelRecordIsAttachedToThis");
        String experience = soldier.profile().experienceTier().displayName
                .toLowerCase(Strings.locale());
        String aptitude = soldier.aptitude().displayName.toLowerCase(Strings.locale());
        SoldierCareer career = soldier.career();
        String record = career.missionsDeployed() == 0
                ? Strings.get("openingFleetArmoryNoCombatDeploymentsAreRecordedYet")
                : Strings.format("openingFleetArmoryDeploymentsConfirmedKillsAndWoundsAreOn", career.missionsDeployed(), career.kills(), career.timesWounded());
        return Strings.format("openingFleetArmoryAMarineAssessedWithAptitude", experience, aptitude, record);
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
                StatMeter.of(cardId + ":damage", Strings.get("openingFleetArmoryDmg"), formatOneDecimal(damage),
                        damage, CatalogCeilings.weaponDamage()),
                StatMeter.of(cardId + ":range", Strings.get("openingFleetArmoryRng"), formatOneDecimal(range),
                        range, CatalogCeilings.weaponRange()),
                StatMeter.of(cardId + ":accuracy", Strings.get("openingFleetArmoryAcc"),
                        String.format(Strings.locale(), "%.0f%%", accuracy * 100f),
                        accuracy, 1f),
                StatMeter.of(cardId + ":dps", Strings.get("openingFleetArmoryDps"), formatOneDecimal(dps),
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
        return String.format(Strings.locale(),
                Strings.get("openingFleetArmoryDmgFRngFAccFDps"),
                damage, range, accuracy, dps);
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
                StatMeter.of(cardId + ":health", Strings.get("openingFleetArmoryHealth"),
                        String.format(Strings.locale(), "%.0f", UnitType.MARINE.maxHp),
                        UnitType.MARINE.maxHp, UnitType.MARINE.maxHp),
                StatMeter.of(cardId + ":armor-value", Strings.get("openingFleetArmoryArmor"),
                        String.format(Strings.locale(), "%.0f", armor.armorCapacity()),
                        armor.armorCapacity(), CatalogCeilings.armorCapacity()),
                StatMeter.of(cardId + ":resist", Strings.get("openingFleetArmoryResist"),
                        String.format(Strings.locale(), "%.0f", armor.armorRating()),
                        armor.armorRating(), CatalogCeilings.armorRating()),
                StatMeter.of(cardId + ":speed", Strings.get("openingFleetArmorySpeed"),
                        String.format(Strings.locale(), "%.1f",
                                UnitType.MARINE.moveSpeed * armor.moveSpeedMult()),
                        UnitType.MARINE.moveSpeed * armor.moveSpeedMult(),
                        UnitType.MARINE.moveSpeed * CatalogCeilings.moveSpeedMult()));
    }

    private static String formatOneDecimal(float value) {
        return String.format(Strings.locale(), "%.1f", value);
    }

    private static String armorDelta(
            FireTeamBillet billet, MarineSoldier soldier, boolean previewing) {
        if (!previewing || billet == null || soldier == null) return "";
        MarineArmorCatalogDef next = billet.armorDef();
        MarineArmorCatalogDef current = soldier.armorDef();
        return String.format(Strings.locale(),
                Strings.get("openingFleetArmoryArmorFResistFSpeedF"),
                next.armorCapacity() - current.armorCapacity(),
                next.armorRating() - current.armorRating(),
                UnitType.MARINE.moveSpeed
                        * (next.moveSpeedMult() - current.moveSpeedMult()));
    }

    private static String careerSummary(MarineSoldier soldier) {
        if (soldier == null) return Strings.get("openingFleetArmoryAwaitingAssignment");
        SoldierCareer career = soldier.career();
        if (career.missionsDeployed() == 0) return Strings.get("openingFleetArmoryNoDeploymentsYet");
        return Strings.format("openingFleetArmoryDeploymentsKillsWoundedTimes", career.missionsDeployed(), career.kills(), career.timesWounded());
    }

    private static String squadEquipmentMessage(SquadEquipmentPreview preview) {
        return switch (preview.result()) {
            case APPLIED -> Strings.get("openingLoadoutReadyToFitAllBillets");
            case INVALID_SQUAD -> Strings.get("openingLoadoutSelectALineSquad");
            case SQUAD_NOT_READY -> Strings.get("openingLoadoutTheSquadChangedReviewItsCurrentRoll");
            case STATIONED -> Strings.get("openingLoadoutUnavailableThisSquadIsStationedAway");
            case UNKNOWN_WEAPON_DOCTRINE -> Strings.get("openingLoadoutChooseWeaponEquipment");
            case UNKNOWN_ARMOR_DOCTRINE -> Strings.get("openingLoadoutChooseArmorEquipment");
            case UNKNOWN_CARD ->
                    Strings.get("openingFleetArmoryBlockedCardNotCollected");
            case INSUFFICIENT_CARGO ->
                    Strings.get("openingFleetArmoryTheOrderChangedReviewTheCurrentSquad");
            case WARRANT_SUSPENDED -> Strings.get("squadEquipmentWarrantSuspended");
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
     * catalog by heart. A weapon sheet uses composition for grade-independent
     * battlefield-role counts and distribution for named weapon quantities.
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
            List<DoctrineSpan> distributionSpans, String carries,
            List<DoctrineSpan> carriesSpans, SpecSheet.Stamp stamp, List<SpecSheet.Seat> seats,
            List<SpecSheet> seatSheets, Runnable select, Runnable issue, boolean issueDisabled,
            String issueReason, boolean issued, boolean compact, int tier) implements MarkupPropertySource {
        /** Inert click handler for a card shown where nothing can be issued; its button is hidden. */
        private static final Runnable NO_ISSUE = () -> { };

        private boolean restricted() {
            return stamp != null && stamp.restricted();
        }

        /** A weapon card wears the spec sheet's weapon accent and an Equipment card its armour one. */
        private String accent() {
            return id.startsWith("armor-doctrine:") ? "armor" : "weapon";
        }

        /**
         * The card's own hover sheet: its field note, which the card face leaves out to keep the
         * catalogue short. Null for a card with no note; its seats and lines keep their own sheets.
         */
        public SpecSheet loreSheet() {
            if (description == null || description.isBlank()) return null;
            return new SpecSheet(name, metadata, factionLogo, accent(), List.of(), List.of(description));
        }

        /** A card whose seats carry nothing beside their weapon or suit prints no carries line. */
        private boolean carriesShown() {
            return !carriesSpans.isEmpty() || (carries != null && !carries.isBlank());
        }

        /**
         * The card's height, summed from its lines: the retained toolkit sizes a list
         * item by its style, not by its content, so the card says how tall it is. The
         * height is the content box: the card's padding and border sit outside it.
         */
        /**
         * A compact card's height, which First Refit lines its option tabs up against. A whole
         * card names none: it is a content-sized column, so its field note measures in the
         * real face instead of from a character estimate that clipped the longer notes.
         */
        private int cardHeight() {
            return 38                                              // heading
                    + (stamp == null ? 0 : restricted() ? 52 : 30)  // stamp, with the banner
                    + 22                                           // rating symbols
                    + (seats.isEmpty() ? 48 * ((distributionSpans.size() + 1) / 2) : 146);
        }

        /** The rating's ten symbols, each lit in quarters, as the hover sheet draws them. */
        private List<Map<String, Object>> ratingSymbols() {
            String symbol = ratingSymbol(accent());
            float[] fills = RatingSymbols.fills(rating / 100f);
            List<Map<String, Object>> symbols = new ArrayList<>(fills.length);
            for (int slot = 0; slot < fills.length; slot++) {
                String slotId = id + ":rating-symbol-" + slot;
                symbols.add(Map.of(
                        "id", slotId,
                        "emptyId", slotId + "-empty",
                        "litId", slotId + "-lit",
                        "litImageId", slotId + "-lit-image",
                        "empty", RatingSymbols.emptyImage(symbol),
                        "lit", RatingSymbols.litImage(symbol),
                        "litStyle", RatingSymbols.litStyle(fills[slot])));
            }
            return symbols;
        }

        private List<Map<String, Object>> seatTeams() {
            List<String> labels = fireTeams();
            List<Map<String, Object>> teams = new ArrayList<>();
            for (int team = 0; team * SpecSheet.TEAM_SIZE < seats.size(); team++) {
                String teamId = id + ":team-" + team;
                List<Map<String, Object>> rows = new ArrayList<>();
                for (int row = 0; row < SpecSheet.TEAM_SIZE / 2; row++) {
                    String rowId = teamId + ":row-" + row;
                    List<Map<String, Object>> cells = new ArrayList<>();
                    for (int column = 0; column < 2; column++) {
                        int index = team * SpecSheet.TEAM_SIZE + row * 2 + column;
                        if (index >= seats.size()) break;
                        SpecSheet.Seat seat = seats.get(index);
                        String seatId = ArmorySpecSheets.seatId(id, index);
                        Map<String, Object> cell = new LinkedHashMap<>();
                        cell.put("id", seatId);
                        cell.put("imageId", seatId + ":image");
                        cell.put("image", seat.image() == null ? "" : seat.image());
                        cell.put("badgeId", seatId + ":badge");
                        cell.put("badge", seat.badge() == null ? "" : seat.badge());
                        cell.put("badgeClasses", seat.badge() == null
                                ? "spec-sheet-seat-badge spec-sheet-seat-badge-absent" : "spec-sheet-seat-badge");
                        cells.add(cell);
                    }
                    rows.add(Map.of("id", rowId, "seats", cells));
                }
                teams.add(Map.of("id", teamId, "labelId", teamId + ":label", "rowsId", teamId + ":rows",
                        "label", team < labels.size() ? labels.get(team) : "", "rows", rows));
            }
            return teams;
        }

        /** The same card carrying its own Issue action and the reason it may be refused. */
        DoctrineTile withIssue(Runnable action, boolean disabled, String reason, boolean alreadyIssued) {
            return new DoctrineTile(id, headerId, nameId, rarityId, ratingId, metadataRowId,
                    factionLogoId, metadataId, descriptionId, compositionId, distributionId,
                    carriesId, classes, rarityClasses, ratingClasses, factionLogoClasses, name,
                    rarity, rating, ratingLabel, factionLogo, metadata, description, composition,
                    distribution, distributionSpans, carries, carriesSpans, stamp, seats, seatSheets, select, action,
                    disabled, reason, alreadyIssued, compact, tier);
        }

        /**
         * The same card cut off below its seats, for a screen that compares cards side by
         * side in a short row: name, issuer, rating and the fire-team picture.
         */
        DoctrineTile asCompact() {
            return new DoctrineTile(id, headerId, nameId, rarityId, ratingId, metadataRowId,
                    factionLogoId, metadataId, descriptionId, compositionId, distributionId,
                    carriesId, classes, rarityClasses, ratingClasses, factionLogoClasses, name,
                    rarity, rating, ratingLabel, factionLogo, metadata, description, composition,
                    distribution, distributionSpans, carries, carriesSpans, stamp, seats, seatSheets, select, issue,
                    issueDisabled, issueReason, issued, true, tier);
        }

        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "issueId" -> id + ":issue";
                case "issueReasonId" -> id + ":issue-reason";
                case "linesId" -> id + ":lines";
                // The tier as the filter strip draws it: one chevron a tier on the tier's tone.
                case "controlsId" -> id + ":controls";
                case "tierId" -> id + ":tier";
                case "tierTopId" -> id + ":tier-top";
                case "tierBottomId" -> id + ":tier-bottom";
                case "issueCheckTopId" -> id + ":issue-check-top";
                case "issueCheckBottomId" -> id + ":issue-check-bottom";
                case "tierIconId" -> id + ":tier-icon";
                case "tierIcon" -> LoadoutFilter.of(tier).icon();
                case "tierStyle" -> "background-color: " + LoadoutFilter.of(tier).tone() + ";";
                // The issued card's box stays lit rather than greyed out; ticking it again does nothing.
                case "issue" -> issue != null && !issued ? issue : NO_ISSUE;
                case "issueDisabled" -> issueDisabled && !issued;
                // A check box, one ticked a kind: the issued card's is ticked, every other is empty.
                case "issueButtonClasses" -> issue == null ? "doctrine-issue-button-hidden"
                        : issued ? "doctrine-issue-button issued"
                        : "doctrine-issue-button";
                case "issueBoxId" -> id + ":issue-box";
                case "issueCheckId" -> id + ":issue-check";
                case "issueCheckClasses" -> issued ? "doctrine-issue-check" : "doctrine-issue-check doctrine-issue-check-empty";
                case "issueReason" -> issueReason;
                case "issueReasonClasses" -> issueReason.isEmpty() || issue == null
                        ? "doctrine-issue-reason-empty"
                        : "doctrine-issue-reason label danger-surface tone-danger";
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
                case "distributionRowId" -> distributionId + "-spans";
                case "distributionGridId" -> distributionId + "-grid";
                case "distributionTagId" -> distributionId + "-tag";
                case "carriesId" -> carriesId;
                case "carriesRowId" -> carriesId + "-spans";
                case "carriesGridId" -> carriesId + "-grid";
                case "carriesTagId" -> carriesId + "-tag";
                case "classes" -> classes;
                // The chosen card is the only one at full strength: with no "Selected" line above
                // the list, the card itself says which it is.
                case "cardClasses" -> "spec-card spec-sheet-" + accent()
                        + (classes.contains(" selected") ? " selected" : " resting");
                case "cardStyle" -> compact ? "height: " + cardHeight() + "px;" : "";
                case "headingCopyId" -> id + ":heading-copy";
                case "crestClasses" -> factionLogo == null || factionLogo.isBlank()
                        ? "spec-sheet-crest spec-sheet-crest-absent" : "spec-sheet-crest spec-card-crest";
                case "stampIssuedId" -> id + ":stamp-issued";
                case "ratingLineId" -> id + ":rating-line";
                case "ratingLabelId" -> id + ":rating-label";
                case "ratingValueId" -> id + ":rating-value";
                case "ratingSymbolsId" -> id + ":rating-symbols";
                case "ratingSymbols" -> ratingSymbols();
                case "ratingName" -> Strings.get(accent().equals("armor") ? "openingBarracksCardGuard" : "openingBarracksCardFire");
                case "ratingValue" -> rating + "%";
                // The card's printed stamp, and for a Restricted card its seal and warrant banner.
                case "stampId" -> id + ":stamp";
                case "stampIssued" -> stamp == null ? "" : stamp.issued();
                case "stampClasses" -> stamp == null ? "doctrine-line-hidden"
                        : restricted() ? "spec-sheet-stamp spec-sheet-stamp-restricted" : "spec-sheet-stamp";
                case "restrictionId" -> id + ":restriction";
                case "restrictionSealId" -> id + ":restriction-seal";
                case "restrictionTextId" -> id + ":restriction-text";
                case "restriction" -> restricted() ? stamp.restriction() : "";
                case "restrictionClasses" -> restricted() ? "spec-sheet-stamp-badge"
                        : "spec-sheet-stamp-badge spec-sheet-stamp-badge-absent";
                case "sealId" -> id + ":seal";
                case "sealClasses" -> restricted() ? "doctrine-seal" : "doctrine-seal doctrine-seal-absent";
                case "distributionStyle" -> seats.isEmpty()
                        ? "height: " + (48 * ((distributionSpans.size() + 1) / 2)) + "px;" : "";
                // The squad's twelve seats as three fire teams of two by two, each seat's
                // weapon or suit with its special or integral system on the opposite corner:
                // the picture the Barracks hover paints, so the two screens read alike.
                case "seatsId" -> id + ":seats";
                case "seatsClasses" -> seats.isEmpty() ? "doctrine-line-hidden" : "spec-sheet-seats";
                case "teams" -> seatTeams();
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
                case "compositionLine" -> Strings.format("squadDoctrineSectionLine", composition);
                case "distributionLine" -> Strings.format("squadDoctrineIssuedLine", distribution);
                case "carriesLine" -> Strings.format("squadDoctrineCarriesLine", carries);
                case "distribution" -> distribution;
                case "distributionSpans" -> distributionSpans;
                // A line is drawn as spans or as the joined sentence, never as
                // both: the fallback is what a loadout issuing nothing to list
                // still has to say.
                case "distributionClasses" -> distributionSpans.isEmpty() || !seats.isEmpty()
                        ? "doctrine-distribution label" : "doctrine-line-hidden";
                case "distributionRowClasses" -> distributionSpans.isEmpty() || !seats.isEmpty()
                        ? "doctrine-line-hidden" : "doctrine-span-row doctrine-distribution-row";
                case "carries" -> carries;
                case "carriesSpans" -> carriesSpans;
                case "carriesClasses" -> carriesSpans.isEmpty() && carriesShown()
                        ? "doctrine-carries label" : "doctrine-line-hidden";
                case "carriesRowClasses" -> carriesSpans.isEmpty()
                        ? "doctrine-line-hidden" : "doctrine-span-row doctrine-carries-row";
                case "select" -> select;
                default -> throw new IllegalArgumentException("Unknown doctrine-tile property");
            };
        }
    }

    /**
     * One named entry inside a loadout card's issue line, and the sheet that
     * describes it ({@code ui-nouns.md}). The joined sentence is what the line
     * reads as; the spans are what a reader can point at.
     *
     * <p>The sheet is null where the entry names no single catalog item — a
     * specialty count over mixed items, a capability family filled from two
     * patterns — and a binding on it then opens nothing.
     */
    public record DoctrineSpan(String id, String text, boolean closing, SpecSheet sheet, String icon)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "label" -> closing ? text : text + "  ·";
                case "icon" -> icon;
                case "iconId" -> id + ":icon";
                case "textId" -> id + ":text";
                case "text" -> text;
                default -> throw new IllegalArgumentException("Unknown doctrine-span property");
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
            String primaryFactionLogoId,
            String primaryTextId,
            String weaponDetailId, String weaponStatsId,
            String armorId, String armorFactionLogoId,
            String armorTextId,
            String armorDetailId,
            String armorStatsId, String specialId, String specialFactionLogoId,
            String specialTextId,
            String systemId, String systemFactionLogoId,
            String systemTextId,
            String weaponDeltaId, String armorDeltaId,
            String careerId, String equipmentId, String weaponColumnId,
            String armorColumnId, String classes, String statusClasses,
            String name, String role, String status, String service, String personnel,
            String unitClass, String weaponBadge, String armorBadge,
            String primary, String primaryFactionLogo,
            String primaryFactionLogoClasses, List<StatMeter> weaponStats,
            String armor, List<StatMeter> armorStats,
            String armorFactionLogo, String armorFactionLogoClasses,
            String special, String specialFactionLogo,
            String specialFactionLogoClasses,
            String system, String systemFactionLogo,
            String systemFactionLogoClasses, String systemClasses,
            String weaponDelta, String armorDelta,
            String deltaClasses, String career,
            SpecSheet primarySheet, SpecSheet armorSheet, SpecSheet specialSheet,
            SpecSheet systemSheet) implements MarkupPropertySource {
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
                case "primaryTextId" -> primaryTextId;
                case "weaponDetailId" -> weaponDetailId;
                case "weaponStatsId" -> weaponStatsId;
                case "armorId" -> armorId;
                case "armorFactionLogoId" -> armorFactionLogoId;
                case "armorTextId" -> armorTextId;
                case "armorDetailId" -> armorDetailId;
                case "armorStatsId" -> armorStatsId;
                case "specialId" -> specialId;
                case "specialFactionLogoId" -> specialFactionLogoId;
                case "specialTextId" -> specialTextId;
                case "systemId" -> systemId;
                case "systemFactionLogoId" -> systemFactionLogoId;
                case "systemTextId" -> systemTextId;
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
                case "primaryFactionLogo" -> primaryFactionLogo;
                case "primaryFactionLogoClasses" -> primaryFactionLogoClasses;
                case "weaponStats" -> weaponStats;
                case "armor" -> armor;
                case "armorStats" -> armorStats;
                case "armorFactionLogo" -> armorFactionLogo;
                case "armorFactionLogoClasses" -> armorFactionLogoClasses;
                case "special" -> special;
                case "specialFactionLogo" -> specialFactionLogo;
                case "specialFactionLogoClasses" -> specialFactionLogoClasses;
                case "system" -> system;
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
