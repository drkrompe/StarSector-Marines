package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.FireTeamGearDelta;
import com.dillon.starsectormarines.marine.FireTeamRefitPreview;
import com.dillon.starsectormarines.marine.FireTeamTemplateAvailability;
import com.dillon.starsectormarines.marine.FireTeamTemplateCard;
import com.dillon.starsectormarines.marine.FireTeamTemplateResult;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.ComputedSignal;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;

import java.util.ArrayList;
import java.util.List;

/**
 * Retained presentation adapter for one authoritative fire-team refit workflow.
 * It owns selection and copy only; every inventory answer and mutation comes from
 * {@link MarineRoster}'s preview/apply transaction.
 */
public final class FleetArmoryViewModel {

    private final MarineRoster roster;
    private final Runnable openSelectedSquad;
    private final MutableSignal<String> selectedSquadId;
    private final MutableSignal<Integer> selectedTeamIndex;
    private final MutableSignal<String> selectedTemplateId;
    private final MutableSignal<Integer> selectedBilletIndex;
    private final MutableSignal<Integer> domainRevision;
    private final MutableSignal<Feedback> feedback;
    private final ComputedSignal<String> companySummary;
    private final ComputedSignal<List<SquadCard>> squadCards;
    private final ComputedSignal<List<FireTeamOverview>> fireTeamOverviews;
    private final ComputedSignal<List<SelectionRow>> squadRows;
    private final ComputedSignal<List<SelectionRow>> teamRows;
    private final ComputedSignal<List<TemplateTile>> templateTiles;
    private final ComputedSignal<List<SelectionRow>> billetRows;
    private final ComputedSignal<List<BilletMannequin>> billetMannequins;
    private final ComputedSignal<String> previewSummary;
    private final ComputedSignal<FireTeamRefitPreview> preview;
    private final ComputedSignal<List<DetailRow>> gearRows;
    private final ComputedSignal<String> targetSummary;
    private final ComputedSignal<String> candidateSummary;
    private final ComputedSignal<String> transactionSummary;
    private final ComputedSignal<String> transactionClasses;
    private final ComputedSignal<Boolean> applyDisabled;
    private final ComputedSignal<String> feedbackText;
    private final ComputedSignal<String> feedbackClasses;

    public FleetArmoryViewModel(Reactor reactor, MarineRoster roster) {
        this(reactor, roster, () -> { });
    }

    public FleetArmoryViewModel(
            Reactor reactor, MarineRoster roster, Runnable openSelectedSquad) {
        if (reactor == null) throw new IllegalArgumentException("reactor is required");
        if (roster == null) throw new IllegalArgumentException("roster is required");
        if (openSelectedSquad == null) {
            throw new IllegalArgumentException("openSelectedSquad is required");
        }
        this.roster = roster;
        this.openSelectedSquad = openSelectedSquad;

        MarineSquad initialSquad = firstLineSquad(roster);
        FireTeamTemplateCard initialTemplate = roster.armory().templateCards().isEmpty()
                ? null : roster.armory().templateCards().get(0);
        selectedSquadId = reactor.signal(initialSquad != null ? initialSquad.id() : null);
        selectedTeamIndex = reactor.signal(0);
        selectedTemplateId = reactor.signal(initialTemplate != null ? initialTemplate.id() : null);
        selectedBilletIndex = reactor.signal(0);
        domainRevision = reactor.signal(0);
        feedback = reactor.signal(Feedback.neutral(
                "Choose a squad, fire team, and reusable template to preview its exact issue."));

        companySummary = reactor.computed(this::buildCompanySummary);
        squadCards = reactor.computed(this::buildSquadCards);
        fireTeamOverviews = reactor.computed(this::buildFireTeamOverviews);
        squadRows = reactor.computed(this::buildSquadRows);
        teamRows = reactor.computed(this::buildTeamRows);
        templateTiles = reactor.computed(this::buildTemplateTiles);
        billetRows = reactor.computed(this::buildBilletRows);
        billetMannequins = reactor.computed(this::buildBilletMannequins);
        previewSummary = reactor.computed(this::buildPreviewSummary);
        preview = reactor.computed(this::buildPreview);
        gearRows = reactor.computed(this::buildGearRows);
        targetSummary = reactor.computed(this::buildTargetSummary);
        candidateSummary = reactor.computed(this::buildCandidateSummary);
        transactionSummary = reactor.computed(() -> templateMessage(preview.get().result()));
        transactionClasses = reactor.computed(() -> preview.get().canApply()
                ? "transaction-result good-surface tone-good"
                : "transaction-result danger-surface tone-danger");
        applyDisabled = reactor.computed(() -> !preview.get().canApply());
        feedbackText = reactor.computed(() -> feedback.get().text());
        feedbackClasses = reactor.computed(() -> feedback.get().succeeded()
                ? "feedback tone-good" : "feedback tone-muted");
    }

    public MarineRoster roster() { return roster; }
    public Signal<String> companySummary() { return companySummary; }
    public Signal<List<SquadCard>> squadCards() { return squadCards; }
    public Signal<List<FireTeamOverview>> fireTeamOverviews() { return fireTeamOverviews; }
    public Signal<List<SelectionRow>> squadRows() { return squadRows; }
    public Signal<List<SelectionRow>> teamRows() { return teamRows; }
    public Signal<List<TemplateTile>> templateTiles() { return templateTiles; }
    public Signal<List<SelectionRow>> billetRows() { return billetRows; }
    public Signal<List<BilletMannequin>> billetMannequins() { return billetMannequins; }
    public Signal<String> previewSummary() { return previewSummary; }
    public Signal<List<DetailRow>> gearRows() { return gearRows; }
    public Signal<String> targetSummary() { return targetSummary; }
    public Signal<String> candidateSummary() { return candidateSummary; }
    public Signal<String> transactionSummary() { return transactionSummary; }
    public Signal<String> transactionClasses() { return transactionClasses; }
    public Signal<Boolean> applyDisabled() { return applyDisabled; }
    public Signal<String> feedbackText() { return feedbackText; }
    public Signal<String> feedbackClasses() { return feedbackClasses; }
    public String selectedSquadId() { return selectedSquadId.peek(); }
    public int selectedTeamIndex() { return selectedTeamIndex.peek(); }
    public String selectedTemplateId() { return selectedTemplateId.peek(); }
    public int selectedBilletIndex() { return selectedBilletIndex.peek(); }
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

    public FireTeamTemplateResult applySelection() {
        FireTeamTemplateResult result = roster.applyFireTeamTemplate(
                selectedSquadId.peek(), selectedTeamIndex.peek(), selectedTemplateId.peek());
        FireTeamTemplateCard card = roster.armory().templateCardById(selectedTemplateId.peek());
        MarineSquad squad = roster.squadById(selectedSquadId.peek());
        String label = card != null ? card.displayName() : "Template";
        String target = squad != null
                ? squad.name() + " / " + teamName(selectedTeamIndex.peek()) : "selected team";
        feedback.set(result == FireTeamTemplateResult.APPLIED
                ? Feedback.success(label + " issued to " + target + " in one atomic transaction.")
                : Feedback.neutral(templateMessage(result)));
        domainRevision.update(value -> value + 1);
        return result;
    }

    private String buildCompanySummary() {
        domainRevision.get();
        int lineSquads = 0;
        int ready = 0;
        for (MarineSquad squad : roster.squads()) {
            if (squad.reserve()) continue;
            lineSquads++;
            ready += roster.readyCount(squad);
        }
        return lineSquads + " squads  ·  " + ready + " RTD  ·  "
                + roster.armory().templateCards().size() + " templates";
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
                    id + ":location", id + ":open",
                    "squad-card " + readinessClass,
                    "squad-card-status heading " + readinessTone(ready, MarineSquad.CAPACITY),
                    squad.name(), readiness, ready + " / " + MarineSquad.CAPACITY + " RTD",
                    assigned + " / " + MarineSquad.TEAMS_PER_SQUAD + " equipped",
                    command, location, "Inspect Squad", () -> {
                        selectSquad(squad.id());
                        openSelectedSquad.run();
                    }));
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
                    id + ":strength", id + ":template",
                    team == selected ? "fire-team-overview selected" : "fire-team-overview",
                    "fire-team-status heading " + readinessTone(ready, MarineSquad.TEAM_SIZE),
                    teamName(team), readinessLabel(ready, MarineSquad.TEAM_SIZE),
                    ready + " / " + MarineSquad.TEAM_SIZE + " RTD",
                    assignedTemplateName(squad, team), () -> selectedTeamIndex.set(target)));
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
                    false, () -> selectedTeamIndex.set(target)));
        }
        return List.copyOf(rows);
    }

    private List<TemplateTile> buildTemplateTiles() {
        domainRevision.get();
        String selected = selectedTemplateId.get();
        List<TemplateTile> tiles = new ArrayList<>();
        for (FireTeamTemplateCard card : roster.armory().templateCards()) {
            FireTeamTemplateAvailability availability =
                    roster.fireTeamTemplateAvailability(card.id());
            String id = "template-tile:" + card.id();
            tiles.add(new TemplateTile(id, id + ":name", id + ":availability",
                    "template-preview:" + card.id(), card.id(),
                    card.id().equals(selected) ? "template-tile selected" : "template-tile",
                    card.displayName(), "Fielded " + availability.fielded()
                    + "  ·  Ready " + availability.readyToIssue(),
                    () -> selectedTemplateId.set(card.id())));
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

    private List<BilletMannequin> buildBilletMannequins() {
        domainRevision.get();
        selectedTemplateId.get();
        FireTeamTemplateCard card = roster.armory().templateCardById(selectedTemplateId.peek());
        if (card == null) return List.of();
        List<BilletMannequin> mannequins = new ArrayList<>();
        for (int index = 0; index < card.billets().size(); index++) {
            FireTeamBillet billet = card.billet(index);
            String special = specialName(billet.specialEquipmentId());
            String id = "mannequin:" + index;
            mannequins.add(new BilletMannequin(id, "billet-preview:" + index,
                    id + ":role", id + ":primary", id + ":armor", id + ":special",
                    billet.name(),
                    billet.primary().catalogName(billet.grade()),
                    billet.armor().displayName,
                    special != null ? special : "No special issue"));
        }
        return List.copyOf(mannequins);
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

    private List<DetailRow> buildGearRows() {
        List<DetailRow> rows = new ArrayList<>();
        for (FireTeamGearDelta delta : preview.get().gear()) {
            String free = delta.unlimited() ? "unlimited" : Integer.toString(delta.free());
            String label = delta.label() + "  ·  Free " + free + "  ·  Return "
                    + delta.returned() + "  ·  Need " + delta.required();
            rows.add(new DetailRow("gear:" + delta.kind() + ":" + delta.label(), label,
                    delta.sufficient() ? "detail-row" : "detail-row tone-danger"));
        }
        return List.copyOf(rows);
    }

    private String buildTargetSummary() {
        domainRevision.get();
        MarineSquad squad = roster.squadById(selectedSquadId.get());
        return squad == null ? "Target  ·  Select a line squad"
                : "Target  ·  " + squad.name() + " / " + teamName(selectedTeamIndex.get())
                + "  ·  Current " + assignedTemplateName(squad, selectedTeamIndex.get());
    }

    private String buildCandidateSummary() {
        domainRevision.get();
        FireTeamTemplateCard card = roster.armory().templateCardById(selectedTemplateId.get());
        if (card == null) return "Candidate  ·  Select a template";
        FireTeamTemplateAvailability availability =
                roster.fireTeamTemplateAvailability(card.id());
        return "Candidate  ·  " + card.displayName() + "  ·  "
                + availability.readyToIssue() + " complete kits ready";
    }

    private void selectSquad(String squadId) {
        MarineSquad squad = roster.squadById(squadId);
        if (squad == null || squad.reserve()) return;
        selectedSquadId.set(squadId);
        selectedTeamIndex.set(0);
    }

    private String assignedTemplateName(MarineSquad squad, int teamIndex) {
        FireTeamTemplateCard card = roster.armory().templateCardById(
                squad.teamTemplateCardId(teamIndex));
        return card != null ? card.displayName() : "Unassigned";
    }

    private int readyTeamMembers(MarineSquad squad, int teamIndex) {
        int ready = 0;
        for (String memberId : squad.teamMembers(teamIndex)) {
            MarineSoldier soldier = roster.soldierById(memberId);
            if (soldier != null && soldier.status() == MarineSoldierStatus.ACTIVE) ready++;
        }
        return ready;
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

    private static String templateMessage(FireTeamTemplateResult result) {
        return switch (result) {
            case APPLIED -> "Ready  ·  This exact transaction can be issued atomically.";
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

    public record DetailRow(String id, String label,
                            String classes) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String name) {
            return switch (name) {
                case "id" -> id;
                case "label" -> label;
                case "classes" -> classes;
                default -> throw new IllegalArgumentException("Unknown detail-row property");
            };
        }
    }

    public record SquadCard(
            String id, String nameId, String statusId, String strengthId,
            String teamsId, String commandId, String locationId, String openId,
            String classes, String statusClasses, String name, String status,
            String strength, String teams, String command, String location,
            String openLabel, Runnable open) implements MarkupPropertySource {
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
                case "openId" -> openId;
                case "classes" -> classes;
                case "statusClasses" -> statusClasses;
                case "name" -> name;
                case "status" -> status;
                case "strength" -> strength;
                case "teams" -> teams;
                case "command" -> command;
                case "location" -> location;
                case "openLabel" -> openLabel;
                case "open" -> open;
                default -> throw new IllegalArgumentException("Unknown squad-card property");
            };
        }
    }

    public record FireTeamOverview(
            String id, String nameId, String statusId, String strengthId,
            String templateId, String classes, String statusClasses,
            String name, String status, String strength, String template,
            Runnable select) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "nameId" -> nameId;
                case "statusId" -> statusId;
                case "strengthId" -> strengthId;
                case "templateId" -> templateId;
                case "classes" -> classes;
                case "statusClasses" -> statusClasses;
                case "name" -> name;
                case "status" -> status;
                case "strength" -> strength;
                case "template" -> template;
                case "select" -> select;
                default -> throw new IllegalArgumentException("Unknown fire-team property");
            };
        }
    }

    public record TemplateTile(
            String id, String nameId, String availabilityId, String canvasId,
            String templateId, String classes, String name, String availability,
            Runnable select) implements MarkupPropertySource {
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
                case "select" -> select;
                default -> throw new IllegalArgumentException("Unknown template-tile property");
            };
        }
    }

    public record BilletMannequin(
            String id, String canvasId, String roleId, String primaryId,
            String armorId, String specialId, String role, String primary,
            String armor, String special) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "canvasId" -> canvasId;
                case "roleId" -> roleId;
                case "primaryId" -> primaryId;
                case "armorId" -> armorId;
                case "specialId" -> specialId;
                case "role" -> role;
                case "primary" -> primary;
                case "armor" -> armor;
                case "special" -> special;
                default -> throw new IllegalArgumentException("Unknown billet-mannequin property");
            };
        }
    }

    private record Feedback(String text, boolean succeeded) {
        private static Feedback neutral(String text) { return new Feedback(text, false); }
        private static Feedback success(String text) { return new Feedback(text, true); }
    }
}
