package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.InfantryCombatStats;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.FireTeamRefitPreview;
import com.dillon.starsectormarines.marine.FireTeamTemplateAvailability;
import com.dillon.starsectormarines.marine.FireTeamTemplateCard;
import com.dillon.starsectormarines.marine.FireTeamTemplateResult;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarinePersonnelLogistics;
import com.dillon.starsectormarines.marine.MarinePersonnelLogistics.ReinforcementResult;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SoldierCareer;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
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
 * Retained presentation adapter for one authoritative fire-team refit workflow.
 * It owns selection and copy only; every inventory answer and mutation comes from
 * {@link MarineRoster}'s preview/apply transaction.
 */
public final class FleetArmoryViewModel {

    private final MarineRoster roster;
    private final Runnable openSelectedSquad;
    private final DoubleSupplier currentDay;
    private final MutableSignal<String> selectedSquadId;
    private final MutableSignal<Integer> selectedTeamIndex;
    private final MutableSignal<String> selectedTemplateId;
    private final MutableSignal<Integer> selectedBilletIndex;
    private final MutableSignal<Boolean> loadoutPickerOpen;
    private final MutableSignal<Integer> domainRevision;
    private final MutableSignal<Feedback> feedback;
    private final ComputedSignal<String> companySummary;
    private final ComputedSignal<List<SquadCard>> squadCards;
    private final ComputedSignal<List<FireTeamOverview>> fireTeamOverviews;
    private final ComputedSignal<List<SelectionRow>> squadRows;
    private final ComputedSignal<List<SelectionRow>> teamRows;
    private final ComputedSignal<List<TemplateTile>> templateTiles;
    private final ComputedSignal<List<SelectionRow>> billetRows;
    private final ComputedSignal<List<MarineViewerCard>> marineCards;
    private final ComputedSignal<String> previewSummary;
    private final ComputedSignal<FireTeamRefitPreview> preview;
    private final ComputedSignal<String> targetSummary;
    private final ComputedSignal<String> candidateSummary;
    private final ComputedSignal<String> applyLabel;
    private final ComputedSignal<String> pickerClasses;
    private final ComputedSignal<String> pickerToggleLabel;
    private final ComputedSignal<String> applyClasses;
    private final ComputedSignal<String> transactionSummary;
    private final ComputedSignal<String> transactionClasses;
    private final ComputedSignal<Boolean> applyDisabled;
    private final ComputedSignal<String> feedbackText;
    private final ComputedSignal<String> feedbackClasses;
    private final ComputedSignal<String> selectedSquadReadiness;
    private final ComputedSignal<String> reinforceLabel;
    private final ComputedSignal<Boolean> reinforceDisabled;

    public FleetArmoryViewModel(Reactor reactor, MarineRoster roster) {
        this(reactor, roster, () -> { }, () -> 0d);
    }

    public FleetArmoryViewModel(
            Reactor reactor, MarineRoster roster, Runnable openSelectedSquad) {
        this(reactor, roster, openSelectedSquad, () -> 0d);
    }

    public FleetArmoryViewModel(Reactor reactor, MarineRoster roster,
                                Runnable openSelectedSquad, DoubleSupplier currentDay) {
        if (reactor == null) throw new IllegalArgumentException("reactor is required");
        if (roster == null) throw new IllegalArgumentException("roster is required");
        if (openSelectedSquad == null) {
            throw new IllegalArgumentException("openSelectedSquad is required");
        }
        if (currentDay == null) throw new IllegalArgumentException("currentDay is required");
        this.roster = roster;
        this.openSelectedSquad = openSelectedSquad;
        this.currentDay = currentDay;

        MarineSquad initialSquad = firstLineSquad(roster);
        FireTeamTemplateCard initialTemplate = roster.armory().templateCards().isEmpty()
                ? null : roster.armory().templateCards().get(0);
        selectedSquadId = reactor.signal(initialSquad != null ? initialSquad.id() : null);
        selectedTeamIndex = reactor.signal(0);
        selectedTemplateId = reactor.signal(initialTemplate != null ? initialTemplate.id() : null);
        selectedBilletIndex = reactor.signal(0);
        loadoutPickerOpen = reactor.signal(false);
        domainRevision = reactor.signal(0);
        feedback = reactor.signal(Feedback.neutral(
                "Choose a fire team, preview a loadout, then equip it when ready."));

        companySummary = reactor.computed(this::buildCompanySummary);
        squadCards = reactor.computed(this::buildSquadCards);
        fireTeamOverviews = reactor.computed(this::buildFireTeamOverviews);
        squadRows = reactor.computed(this::buildSquadRows);
        teamRows = reactor.computed(this::buildTeamRows);
        templateTiles = reactor.computed(this::buildTemplateTiles);
        billetRows = reactor.computed(this::buildBilletRows);
        marineCards = reactor.computed(this::buildMarineCards);
        previewSummary = reactor.computed(this::buildPreviewSummary);
        preview = reactor.computed(this::buildPreview);
        targetSummary = reactor.computed(this::buildTargetSummary);
        candidateSummary = reactor.computed(this::buildCandidateSummary);
        applyLabel = reactor.computed(this::buildApplyLabel);
        pickerClasses = reactor.computed(() -> loadoutPickerOpen.get()
                ? "panel template-library picker-open"
                : "panel template-library picker-closed");
        pickerToggleLabel = reactor.computed(() -> loadoutPickerOpen.get()
                ? "Cancel Preview" : "Change Loadout");
        applyClasses = reactor.computed(() -> loadoutPickerOpen.get()
                ? "apply-button picker-confirm-open" : "apply-button picker-confirm-closed");
        transactionSummary = reactor.computed(this::buildViewerStatus);
        transactionClasses = reactor.computed(this::buildViewerStatusClasses);
        applyDisabled = reactor.computed(() -> !loadoutPickerOpen.get()
                || !preview.get().canApply());
        feedbackText = reactor.computed(() -> feedback.get().text());
        feedbackClasses = reactor.computed(() -> feedback.get().succeeded()
                ? "feedback tone-good" : "feedback tone-muted");
        selectedSquadReadiness = reactor.computed(this::buildSelectedSquadReadiness);
        reinforceLabel = reactor.computed(() -> reinforcementLabel(
                roster.squadById(selectedSquadId.get())));
        reinforceDisabled = reactor.computed(() -> reinforcementCapacity(
                roster.squadById(selectedSquadId.get())) <= 0);
    }

    public MarineRoster roster() { return roster; }
    public Signal<String> companySummary() { return companySummary; }
    public Signal<List<SquadCard>> squadCards() { return squadCards; }
    public Signal<List<FireTeamOverview>> fireTeamOverviews() { return fireTeamOverviews; }
    public Signal<List<SelectionRow>> squadRows() { return squadRows; }
    public Signal<List<SelectionRow>> teamRows() { return teamRows; }
    public Signal<List<TemplateTile>> templateTiles() { return templateTiles; }
    public Signal<List<SelectionRow>> billetRows() { return billetRows; }
    public Signal<List<MarineViewerCard>> marineCards() { return marineCards; }
    public Signal<String> previewSummary() { return previewSummary; }
    public Signal<String> targetSummary() { return targetSummary; }
    public Signal<String> candidateSummary() { return candidateSummary; }
    public Signal<String> applyLabel() { return applyLabel; }
    public Signal<String> pickerClasses() { return pickerClasses; }
    public Signal<String> pickerToggleLabel() { return pickerToggleLabel; }
    public Signal<String> applyClasses() { return applyClasses; }
    public Signal<String> transactionSummary() { return transactionSummary; }
    public Signal<String> transactionClasses() { return transactionClasses; }
    public Signal<Boolean> applyDisabled() { return applyDisabled; }
    public Signal<String> feedbackText() { return feedbackText; }
    public Signal<String> feedbackClasses() { return feedbackClasses; }
    public Signal<String> selectedSquadReadiness() { return selectedSquadReadiness; }
    public Signal<String> reinforceLabel() { return reinforceLabel; }
    public Signal<Boolean> reinforceDisabled() { return reinforceDisabled; }
    public String selectedSquadId() { return selectedSquadId.peek(); }
    public int selectedTeamIndex() { return selectedTeamIndex.peek(); }
    public String selectedTemplateId() { return selectedTemplateId.peek(); }
    public int selectedBilletIndex() { return selectedBilletIndex.peek(); }
    public boolean loadoutPickerOpen() { return loadoutPickerOpen.peek(); }
    public String selectedSquadName() {
        MarineSquad squad = roster.squadById(selectedSquadId.peek());
        return squad != null ? squad.name() : "Squad";
    }
    public FireTeamRefitPreview currentPreview() { return preview.get(); }

    public FireTeamBillet selectedBillet() {
        return billetAt(selectedBilletIndex.peek());
    }

    public FireTeamBillet billetAt(int index) {
        FireTeamTemplateCard card = roster.armory().templateCardById(selectedTemplateId.peek());
        return card != null && index >= 0 && index < card.billets().size()
                ? card.billet(index) : null;
    }

    public List<FireTeamBillet> billetsForTemplate(String templateId) {
        FireTeamTemplateCard card = roster.armory().templateCardById(templateId);
        return card != null ? List.copyOf(card.billets()) : List.of();
    }

    public Runnable applyAction() {
        return () -> applySelection();
    }

    public Runnable toggleLoadoutPickerAction() {
        return this::toggleLoadoutPicker;
    }

    public Runnable reinforceSelectedSquadAction() {
        return () -> reinforceSquad(selectedSquadId.peek());
    }

    /** Reprojects campaign time, personnel, and cargo authority. */
    public void refresh() {
        domainRevision.update(value -> value + 1);
    }

    public FireTeamBillet viewerBilletAt(int index) {
        if (loadoutPickerOpen.peek()) return billetAt(index);
        MarineSquad squad = roster.squadById(selectedSquadId.peek());
        if (squad == null) return null;
        List<String> members = roster.teamMemberIds(squad, selectedTeamIndex.peek());
        if (index < 0 || index >= members.size()) return null;
        MarineSoldier soldier = roster.soldierById(members.get(index));
        return soldier != null ? currentBillet(squad, selectedTeamIndex.peek(), index, soldier)
                : null;
    }

    public FireTeamTemplateResult applySelection() {
        FireTeamTemplateResult result = roster.applyFireTeamTemplate(
                selectedSquadId.peek(), selectedTeamIndex.peek(), selectedTemplateId.peek());
        FireTeamTemplateCard card = roster.armory().templateCardById(selectedTemplateId.peek());
        MarineSquad squad = roster.squadById(selectedSquadId.peek());
        String label = card != null ? card.displayName() : "Template";
        String target = squad != null
                ? squad.name() + " / " + teamName(selectedTeamIndex.peek()) : "selected team";
        feedback.set(result == FireTeamTemplateResult.APPLIED
                ? Feedback.success(label + " equipped to " + target + ".")
                : Feedback.neutral(templateMessage(result)));
        if (result == FireTeamTemplateResult.APPLIED) loadoutPickerOpen.set(false);
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
                + wounded + " WIA  ·  " + roster.armory().templateCards().size()
                + " templates";
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
            int assigned = 0;
            for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
                if (squad.teamTemplateCardId(team) != null) assigned++;
            }
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
                    assigned + " / " + MarineSquad.TEAMS_PER_SQUAD + " equipped",
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
            teams.add(new FireTeamOverview(id, id + ":name", id + ":status",
                    id + ":strength", id + ":recovery", id + ":template",
                    team == selected ? "fire-team-overview selected" : "fire-team-overview",
                    "fire-team-status heading " + readinessTone(ready, MarineSquad.TEAM_SIZE),
                    teamName(team), readinessLabel(ready, MarineSquad.TEAM_SIZE),
                    ready + " / " + MarineSquad.TEAM_SIZE + " RTD",
                    recoverySummary(squad, team), assignedTemplateName(squad, team),
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
            String assigned = assignedTemplateName(squad, team);
            String label = teamName(team) + "  ·  " + readyTeamMembers(squad, team)
                    + " / " + MarineSquad.TEAM_SIZE + " RTD  ·  " + assigned;
            rows.add(new SelectionRow("team:" + squad.id() + ":" + team, label,
                    team == selected ? "selection-row selected" : "selection-row",
                    false, () -> selectTeam(target)));
        }
        return List.copyOf(rows);
    }

    private List<TemplateTile> buildTemplateTiles() {
        domainRevision.get();
        String selected = selectedTemplateId.get();
        String squadId = selectedSquadId.get();
        int team = selectedTeamIndex.get();
        List<TemplateTile> tiles = new ArrayList<>();
        for (FireTeamTemplateCard card : roster.armory().templateCards()) {
            FireTeamTemplateAvailability availability =
                    roster.fireTeamTemplateAvailability(card.id());
            FireTeamRefitPreview option = roster.previewFireTeamTemplate(
                    squadId, team, card.id());
            boolean available = option.canApply();
            String id = "template-tile:" + card.id();
            tiles.add(new TemplateTile(id, id + ":name", id + ":availability",
                    "template-preview:" + card.id(), card.id(),
                    templateTileClasses(card.id().equals(selected), available),
                    card.displayName(), available
                    ? "Available  ·  Fielded " + availability.fielded()
                    : "Unavailable for " + teamName(team),
                    !available, () -> selectTemplate(card.id())));
        }
        return List.copyOf(tiles);
    }

    private List<SelectionRow> buildBilletRows() {
        domainRevision.get();
        FireTeamTemplateCard card = roster.armory().templateCardById(selectedTemplateId.get());
        if (card == null) return List.of();
        int selected = selectedBilletIndex.get();
        List<SelectionRow> rows = new ArrayList<>();
        for (int index = 0; index < card.billets().size(); index++) {
            int target = index;
            FireTeamBillet billet = card.billet(index);
            String special = specialName(billet.specialEquipmentId());
            String label = (index + 1) + "  ·  " + billet.name() + "  ·  "
                    + billet.primary().displayName() + " / " + billet.grade().displayName
                    + "  ·  " + billet.armor().displayName
                    + (special == null ? "" : "  ·  " + special);
            rows.add(new SelectionRow("billet:" + index, label,
                    index == selected ? "billet-row selected" : "billet-row",
                    false, () -> selectedBilletIndex.set(target)));
        }
        return List.copyOf(rows);
    }

    private List<MarineViewerCard> buildMarineCards() {
        domainRevision.get();
        MarineSquad squad = roster.squadById(selectedSquadId.get());
        int teamIndex = selectedTeamIndex.get();
        selectedTemplateId.get();
        boolean previewing = loadoutPickerOpen.get();
        if (squad == null) return List.of();
        List<String> memberIds = roster.teamMemberIds(squad, teamIndex);
        List<MarineViewerCard> marines = new ArrayList<>();
        for (int index = 0; index < MarineSquad.TEAM_SIZE; index++) {
            MarineSoldier soldier = index < memberIds.size()
                    ? roster.soldierById(memberIds.get(index)) : null;
            FireTeamBillet billet = previewing ? billetAt(index)
                    : soldier != null ? currentBillet(squad, teamIndex, index, soldier) : null;
            String id = "marine-card:" + index;
            String special = billet != null ? specialName(billet.specialEquipmentId()) : null;
            marines.add(new MarineViewerCard(
                    id, "marine-preview:" + index, id + ":name", id + ":role",
                    id + ":status", id + ":service", id + ":primary",
                    id + ":weapon-stats", id + ":armor", id + ":armor-stats",
                    id + ":special", id + ":weapon-delta", id + ":armor-delta",
                    id + ":career",
                    marineCardClasses(soldier, previewing),
                    "marine-status heading " + marineStatusTone(soldier),
                    marineName(soldier), billet != null ? billet.name() : "Unfilled billet",
                    marineStatus(soldier), serviceSummary(soldier),
                    billet != null ? billet.primary().catalogName(billet.grade()) + "  ·  "
                            + billet.grade().displayName : "No primary",
                    weaponStats(billet, soldier),
                    billet != null ? billet.armor().displayName + "  ·  Tier "
                            + billet.armor().tierMark() : "No armor",
                    armorStats(billet), special != null ? "Special  ·  " + special
                            : "Special  ·  No issue",
                    weaponDelta(billet, soldier, previewing),
                    armorDelta(billet, soldier, previewing),
                    previewing ? "marine-delta label tone-accent"
                            : "marine-delta label picker-closed-line",
                    careerSummary(soldier)));
        }
        return List.copyOf(marines);
    }

    private String buildPreviewSummary() {
        domainRevision.get();
        selectedTemplateId.get();
        selectedBilletIndex.get();
        FireTeamBillet billet = selectedBillet();
        if (billet == null) return "Select a billet to inspect its materialized field kit.";
        String special = specialName(billet.specialEquipmentId());
        return billet.name() + "  ·  " + billet.primary().catalogName(billet.grade())
                + "  ·  " + billet.armor().displayName
                + (special == null ? "" : "  ·  " + special);
    }

    private FireTeamRefitPreview buildPreview() {
        domainRevision.get();
        return roster.previewFireTeamTemplate(selectedSquadId.get(),
                selectedTeamIndex.get(), selectedTemplateId.get());
    }

    private String buildTargetSummary() {
        domainRevision.get();
        MarineSquad squad = roster.squadById(selectedSquadId.get());
        int team = selectedTeamIndex.get();
        return squad == null ? "Select a line squad"
                : squad.name() + " / " + teamName(team) + "  ·  "
                + roster.teamMemberIds(squad, team).size() + " marines  ·  Current: "
                + assignedTemplateName(squad, team);
    }

    private String buildCandidateSummary() {
        domainRevision.get();
        if (!loadoutPickerOpen.get()) return "Showing currently equipped loadout";
        FireTeamTemplateCard card = roster.armory().templateCardById(selectedTemplateId.get());
        return card == null ? "Choose a loadout template"
                : "Previewing " + card.displayName() + " template on this fire team";
    }

    private String buildApplyLabel() {
        FireTeamTemplateCard card = roster.armory().templateCardById(selectedTemplateId.get());
        String template = card != null ? card.displayName() : "Template";
        return "Equip " + teamName(selectedTeamIndex.get()) + " with " + template;
    }

    private String buildViewerStatus() {
        if (!loadoutPickerOpen.get()) {
            MarineSquad squad = roster.squadById(selectedSquadId.get());
            return squad == null ? "Select a fire team"
                    : "Current loadout  ·  "
                    + assignedTemplateName(squad, selectedTeamIndex.get());
        }
        return templateMessage(preview.get().result());
    }

    private String buildViewerStatusClasses() {
        if (!loadoutPickerOpen.get()) {
            return "transaction-result surface-dark tone-muted";
        }
        return preview.get().canApply()
                ? "transaction-result good-surface tone-good"
                : "transaction-result danger-surface tone-danger";
    }

    private void toggleLoadoutPicker() {
        if (loadoutPickerOpen.peek()) {
            loadoutPickerOpen.set(false);
            feedback.set(Feedback.neutral("Showing the fire team's current equipment."));
            return;
        }
        chooseInitialAvailableTemplate();
        loadoutPickerOpen.set(true);
        feedback.set(Feedback.neutral(
                "Choose an available loadout to preview its equipment and stat changes."));
    }

    private void chooseInitialAvailableTemplate() {
        MarineSquad squad = roster.squadById(selectedSquadId.peek());
        String assigned = squad != null
                ? squad.teamTemplateCardId(selectedTeamIndex.peek()) : null;
        if (assigned != null && roster.previewFireTeamTemplate(
                selectedSquadId.peek(), selectedTeamIndex.peek(), assigned).canApply()) {
            selectedTemplateId.set(assigned);
            return;
        }
        if (roster.previewFireTeamTemplate(selectedSquadId.peek(),
                selectedTeamIndex.peek(), selectedTemplateId.peek()).canApply()) return;
        for (FireTeamTemplateCard card : roster.armory().templateCards()) {
            if (roster.previewFireTeamTemplate(selectedSquadId.peek(),
                    selectedTeamIndex.peek(), card.id()).canApply()) {
                selectedTemplateId.set(card.id());
                return;
            }
        }
    }

    private void selectTemplate(String templateId) {
        if (!roster.previewFireTeamTemplate(selectedSquadId.peek(),
                selectedTeamIndex.peek(), templateId).canApply()) return;
        selectedTemplateId.set(templateId);
        FireTeamTemplateCard card = roster.armory().templateCardById(templateId);
        feedback.set(Feedback.neutral("Previewing "
                + (card != null ? card.displayName() : "selected")
                + " loadout on the named marines above."));
    }

    private void selectSquad(String squadId) {
        MarineSquad squad = roster.squadById(squadId);
        if (squad == null || squad.reserve()) return;
        selectedSquadId.set(squadId);
        selectedTeamIndex.set(0);
        loadoutPickerOpen.set(false);
    }

    private void selectTeam(int teamIndex) {
        selectedTeamIndex.set(teamIndex);
        loadoutPickerOpen.set(false);
        feedback.set(Feedback.neutral("Showing the selected fire team's current equipment."));
    }

    private void reinforceSquad(String squadId) {
        MarineSquad squad = roster.squadById(squadId);
        if (squad == null) return;
        ReinforcementResult result = MarinePersonnelLogistics.reinforceSquad(roster, squadId);
        if (result.total() <= 0) {
            feedback.set(Feedback.neutral(reinforcementUnavailableReason(squad)));
        } else {
            String source = result.transferred() > 0 && result.enlisted() > 0
                    ? result.transferred() + " reserve, " + result.enlisted() + " enlisted"
                    : result.transferred() > 0 ? result.transferred() + " from reserve"
                    : result.enlisted() + " enlisted";
            feedback.set(Feedback.success(squad.name() + " reinforced  ·  " + source
                    + ". Review replacement equipment before deployment."));
        }
        domainRevision.update(value -> value + 1);
    }

    private String buildSelectedSquadReadiness() {
        domainRevision.get();
        MarineSquad squad = roster.squadById(selectedSquadId.get());
        if (squad == null) return "Select a line squad";
        return roster.readyCount(squad) + " RTD  ·  " + roster.vacancies(squad)
                + " open billets  ·  " + recoverySummary(squad);
    }

    private int reinforcementCapacity(MarineSquad squad) {
        domainRevision.get();
        if (squad == null || squad.stationed()) return 0;
        int personnel = roster.readyReserveCount() + MarinePersonnelLogistics.availableRecruits();
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

    private static String specialName(String id) {
        SpecialEquipmentDef def = SpecialEquipmentRegistry.get(id);
        return def != null ? def.displayName() : null;
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

    private static String templateTileClasses(boolean selected, boolean available) {
        String classes = available ? "template-tile available" : "template-tile unavailable";
        return selected ? classes + " selected" : classes;
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

    private static String weaponStats(FireTeamBillet billet, MarineSoldier soldier) {
        if (billet == null || soldier == null) return "No combat profile";
        SoldierProfile profile = soldier.profile();
        return String.format(Locale.ROOT,
                "DMG %.1f  ·  RNG %.1f  ·  ACC %.0f%%  ·  DPS %.1f",
                InfantryCombatStats.damage(billet.primary(), billet.grade()),
                InfantryCombatStats.range(billet.primary(), billet.grade()),
                InfantryCombatStats.accuracy(billet.primary(), billet.grade(), profile) * 100f,
                InfantryCombatStats.estimatedDps(
                        billet.primary(), billet.grade(), profile));
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

    private static String armorStats(FireTeamBillet billet) {
        if (billet == null) return "No protection profile";
        return String.format(Locale.ROOT,
                "POOL %.0f  ·  RATING %.0f  ·  MOVE %.0f%%",
                billet.armor().armorPool, billet.armor().armorRating,
                billet.armor().moveSpeedMult * 100f);
    }

    private static String armorDelta(
            FireTeamBillet billet, MarineSoldier soldier, boolean previewing) {
        if (!previewing || billet == null || soldier == null) return "";
        return String.format(Locale.ROOT,
                "POOL %+.0f  ·  RATING %+.0f  ·  MOVE %+.0f%%",
                billet.armor().armorPool - soldier.armor().armorPool,
                billet.armor().armorRating - soldier.armor().armorRating,
                (billet.armor().moveSpeedMult - soldier.armor().moveSpeedMult) * 100f);
    }

    private static String careerSummary(MarineSoldier soldier) {
        if (soldier == null) return "Awaiting assignment";
        SoldierCareer career = soldier.career();
        if (career.missionsDeployed() == 0) return "No deployments yet";
        return career.missionsDeployed() + " deployments  ·  " + career.kills()
                + " kills  ·  wounded " + career.timesWounded() + " times";
    }

    private static String templateMessage(FireTeamTemplateResult result) {
        return switch (result) {
            case APPLIED -> "Ready to equip this fire team.";
            case INVALID_FIRE_TEAM -> "Select a line fire team.";
            case TEAM_NOT_READY -> "Not ready  ·  This team needs four RTD marines.";
            case STATIONED -> "Unavailable  ·  This squad is stationed away.";
            case UNKNOWN_TEMPLATE -> "Select an available reusable template.";
            case UNKNOWN_ARRANGEMENT -> "Squad arrangement unavailable.";
            case LOCKED_RECIPE -> "Blocked  ·  One or more required recipes are locked.";
            case INSUFFICIENT_PRIMARIES -> "Blocked  ·  Not enough primary weapons.";
            case INSUFFICIENT_ARMOR -> "Blocked  ·  Not enough armor.";
            case INSUFFICIENT_SECONDARIES -> "Blocked  ·  Not enough special equipment.";
        };
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
            String id, String nameId, String statusId, String strengthId,
            String recoveryId, String templateId, String classes, String statusClasses,
            String name, String status, String strength, String recovery, String template,
            Runnable select) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
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

    public record TemplateTile(
            String id, String nameId, String availabilityId, String canvasId,
            String templateId, String classes, String name, String availability,
            boolean disabled, Runnable select) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "nameId" -> nameId;
                case "availabilityId" -> availabilityId;
                case "canvasId" -> canvasId;
                case "templateId" -> templateId;
                case "classes" -> classes;
                case "name" -> name;
                case "availability" -> availability;
                case "disabled" -> disabled;
                case "select" -> select;
                default -> throw new IllegalArgumentException("Unknown template-tile property");
            };
        }
    }

    public record MarineViewerCard(
            String id, String canvasId, String nameId, String roleId,
            String statusId, String serviceId, String primaryId,
            String weaponStatsId, String armorId, String armorStatsId,
            String specialId, String weaponDeltaId, String armorDeltaId,
            String careerId, String classes, String statusClasses,
            String name, String role, String status, String service,
            String primary, String weaponStats, String armor, String armorStats,
            String special, String weaponDelta, String armorDelta,
            String deltaClasses, String career) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "canvasId" -> canvasId;
                case "nameId" -> nameId;
                case "roleId" -> roleId;
                case "statusId" -> statusId;
                case "serviceId" -> serviceId;
                case "primaryId" -> primaryId;
                case "weaponStatsId" -> weaponStatsId;
                case "armorId" -> armorId;
                case "armorStatsId" -> armorStatsId;
                case "specialId" -> specialId;
                case "weaponDeltaId" -> weaponDeltaId;
                case "armorDeltaId" -> armorDeltaId;
                case "careerId" -> careerId;
                case "classes" -> classes;
                case "statusClasses" -> statusClasses;
                case "name" -> name;
                case "role" -> role;
                case "status" -> status;
                case "service" -> service;
                case "primary" -> primary;
                case "weaponStats" -> weaponStats;
                case "armor" -> armor;
                case "armorStats" -> armorStats;
                case "special" -> special;
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
