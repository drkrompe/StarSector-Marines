package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.MarinePersonnelLogistics.PersonnelDrawResult;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadCareer;
import com.dillon.starsectormarines.ops.spec.IntegralSystemCopy;
import com.dillon.starsectormarines.ops.spec.SpecSheets;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.ComputedSignal;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import com.dillon.starsectormarines.ui.spec.SpecSheet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;

/** Commander board and drill-down projection of persistent squads into their quarters. */
public final class BarracksViewModel {

    private final MarineRoster roster;
    private final BarracksCommandModel command;
    private final DoubleSupplier currentDay;
    private final MutableSignal<String> selectedSquadId;
    private final MutableSignal<Integer> revision;
    private final ComputedSignal<List<SquadRow>> squadRows;
    private final ComputedSignal<List<MusterRow>> musterRows;
    private final ComputedSignal<List<RecordCell>> recordCells;
    private final ComputedSignal<String> selectedSquadName;
    private final ComputedSignal<String> selectedSquadSummary;
    private final ComputedSignal<String> quartersStatus;

    public BarracksViewModel(Reactor reactor, MarineRoster roster,
                             DoubleSupplier currentDay) {
        this(reactor, roster, currentDay, EquipmentIssueResources.UNLIMITED, () -> 0,
                id -> new PersonnelDrawResult(roster.fillVacanciesFromReserve(id), 0));
    }

    public BarracksViewModel(Reactor reactor, MarineRoster roster, DoubleSupplier currentDay,
                            EquipmentIssueResources resources, IntSupplier cargoMarines,
                            Function<String, PersonnelDrawResult> reinforce) {
        if (reactor == null) throw new IllegalArgumentException("reactor is required");
        if (roster == null) throw new IllegalArgumentException("roster is required");
        if (currentDay == null) throw new IllegalArgumentException("currentDay is required");
        this.roster = roster;
        this.currentDay = currentDay;
        revision = reactor.signal(0);
        selectedSquadId = reactor.signal(firstSquadId());
        squadRows = reactor.computed(this::buildSquadRows);
        musterRows = reactor.computed(this::buildMusterRows);
        recordCells = reactor.computed(this::buildRecordCells);
        selectedSquadName = reactor.computed(() -> {
            revision.get();
            MarineSquad squad = selectedSquad();
            return squad != null ? squad.name() : Strings.get("openingBarracksNoLineSquadAssigned");
        });
        selectedSquadSummary = reactor.computed(this::buildSelectedSummary);
        quartersStatus = reactor.computed(this::buildQuartersStatus);
        command = new BarracksCommandModel(reactor, roster, resources, cargoMarines, reinforce,
                selectedSquadId::set, this::refresh, currentDay);
    }

    public BarracksCommandModel command() { return command; }
    public Signal<List<SquadRow>> squadRows() { return squadRows; }
    public Signal<List<MusterRow>> musterRows() { return musterRows; }
    public Signal<List<RecordCell>> recordCells() { return recordCells; }
    public Signal<String> selectedSquadName() { return selectedSquadName; }
    public Signal<String> selectedSquadSummary() { return selectedSquadSummary; }
    public Signal<String> quartersStatus() { return quartersStatus; }

    /** Ready personnel physically represented in the selected shipboard room. */
    public List<MarineSoldier> sceneMarines() {
        MarineSquad squad = selectedSquad();
        if (squad == null || squad.stationed()) return List.of();
        List<MarineSoldier> result = new ArrayList<>();
        for (String id : roster.manningMemberIds(squad)) {
            MarineSoldier soldier = roster.soldierById(id);
            if (soldier != null && soldier.status() == MarineSoldierStatus.ACTIVE) {
                result.add(soldier);
            }
        }
        return List.copyOf(result);
    }

    public void refresh() {
        if (selectedSquad() == null) selectedSquadId.set(firstSquadId());
        revision.update(value -> value + 1);
        command.refresh();
    }

    private List<SquadRow> buildSquadRows() {
        revision.get();
        List<SquadRow> rows = new ArrayList<>();
        for (MarineSquad squad : lineSquads()) {
            int ready = roster.readyCount(squad);
            int wounded = woundedCount(squad);
            boolean selected = squad.id().equals(selectedSquadId.get());
            String status = squad.stationed() ? Strings.get("openingBarracksStationed")
                    : ready == MarineSquad.CAPACITY ? Strings.get("openingBarracksReady")
                    : wounded > 0 ? Strings.get("openingBarracksRecovering") : Strings.get("openingBarracksUnderstrength");
            String classes = "barracks-squad-row"
                    + (selected ? " selected" : "")
                    + (squad.stationed() ? " stationed" : "");
            String id = "barracks-squad:" + squad.id();
            rows.add(new SquadRow(id, id + ":name", id + ":status", id + ":detail",
                    id + ":record", classes, squad.name(), status,
                    Strings.format("openingBarracksRtd", ready, MarineSquad.CAPACITY, (wounded > 0 ? Strings.format("openingBarracksWia", wounded) : "")),
                    compactRecord(squad.career()),
                    () -> command.inspect(squad.id())));
        }
        return List.copyOf(rows);
    }

    private List<MusterRow> buildMusterRows() {
        revision.get();
        MarineSquad squad = selectedSquad();
        if (squad == null) return List.of();
        List<String> memberIds = roster.manningMemberIds(squad);
        List<MusterRow> rows = new ArrayList<>(MarineSquad.CAPACITY);
        for (int billet = 0; billet < MarineSquad.CAPACITY; billet++) {
            int team = billet / MarineSquad.TEAM_SIZE;
            String id = "barracks-billet:" + billet;
            String position = teamLabel(team) + " " + (billet % MarineSquad.TEAM_SIZE + 1);
            if (billet >= memberIds.size()) {
                SpecSheet sheet = new SpecSheet(Strings.get("openingBarracksOpenBillet"), position, null, "item", List.of(),
                        List.of(Strings.get("openingBarracksNoMarineHoldsThisPlaceReplenishThe")));
                rows.add(new MusterRow(id, id + ":name", id + ":detail",
                        "muster-row vacant", Strings.get("openingBarracksOpenBillet"), Strings.get("openingBarracksVacant"), position,
                        List.of(new MusterItem(id + ":vacancy", "graphics/ui/formation/marine-cutout-vacant.svg", sheet)), sheet));
                continue;
            }
            MarineSoldier soldier = roster.soldierById(memberIds.get(billet));
            if (soldier == null) continue;
            boolean wounded = soldier.status() == MarineSoldierStatus.WIA;
            String recovery = wounded ? FleetArmoryViewModel.formatRemainingCompact(
                    soldier.unavailableUntilDay(), currentDay.getAsDouble()) : "";
            String detail = wounded ? "+ " + recovery : squad.stationed() ? Strings.get("openingBarracksStationed2") : Strings.get("openingBarracksReady2");
            String name = soldier.enlistedRank().abbreviation() + " " + soldier.name();
            List<MusterItem> items = new ArrayList<>();
            items.add(new MusterItem(id + ":armor", soldier.armorDef().iconPath(), SpecSheets.armor(soldier.armorDef())));
            items.add(new MusterItem(id + ":primary", primaryIcon(soldier), SpecSheets.weapon(soldier.primaryDef(), soldier.primaryGrade())));
            if (soldier.specialEquipmentDef() != null) {
                items.add(new MusterItem(id + ":special", soldier.specialEquipmentDef().armoryIconPath(),
                        SpecSheets.special(soldier.specialEquipmentDef())));
            }
            if (soldier.armorDef().hasIntegralSystem()) {
                items.add(new MusterItem(id + ":system", IntegralSystemCopy.iconPath(soldier.armorDef()),
                        SpecSheets.integralSystem(soldier.armorDef())));
            }
            String availability = wounded ? Strings.format("openingBarracksWoundedReturnToDutyIn", recovery)
                    : squad.stationed() ? Strings.get("openingBarracksOnStationingDuty") : Strings.get("openingBarracksReadyForDeployment");
            SpecSheet sheet = new SpecSheet(name, position + " · " + availability,
                    soldier.armorDef().iconPath(), wounded ? "special" : "armor",
                    List.of(SpecSheet.Stat.of(Strings.get("openingBarracksPrimary"), soldier.primaryDef().catalogName(soldier.primaryGrade().tier)),
                            SpecSheet.Stat.of(Strings.get("openingBarracksArmor"), soldier.armorDef().displayName()),
                            SpecSheet.Stat.of(Strings.get("openingBarracksSpecialty"), soldier.specialEquipmentDef() == null ? Strings.get("openingBarracksNoneIssued") : soldier.specialEquipmentDef().displayName())),
                    List.of(wounded ? Strings.get("openingBarracksThisMarineKeepsTheirBilletWhileRecovering")
                            : Strings.get("openingBarracksEquipmentShownIsThisMarineSCurrent")));
            rows.add(new MusterRow(id, id + ":name", id + ":detail",
                    "muster-row " + (wounded ? "wounded" : "ready"), name, detail, position, List.copyOf(items), sheet));
        }
        return List.copyOf(rows);
    }

    /**
     * The selected formation's lifetime service record. Read-only evidence: the
     * career is never a combat input ({@code progression-nouns.md}), so nothing
     * here feeds back into how the squad fights.
     */
    private List<RecordCell> buildRecordCells() {
        revision.get();
        MarineSquad squad = selectedSquad();
        if (squad == null) return List.of();
        SquadCareer career = squad.career();
        List<RecordCell> cells = new ArrayList<>();
        // Zero states spell themselves out. The heading face carries no em-dash,
        // so a placeholder glyph renders as an empty cell that reads as a bug.
        cells.add(cell("operations", Strings.get("openingBarracksOperations"), career.missionsDeployed() == 0 ? "none"
                : Strings.format("openingBarracksWon", career.missionsDeployed(), career.missionsWon())));
        cells.add(cell("kills", Strings.get("openingBarracksConfirmed"), String.valueOf(career.kills())));
        cells.add(cell("accuracy", Strings.get("openingBarracksRoundsOnTarget"), career.roundsFired() == 0 ? Strings.get("openingBarracksNoData")
                : Math.round(career.landedFraction() * 100f) + "%"));
        cells.add(cell("casualties", Strings.get("openingBarracksCasualties"), career.marinesDeployed() == 0 ? "none"
                : Strings.format("openingBarracksOf", career.casualties(), career.marinesDeployed())));
        // Kept un-netted from damage dealt at the telemetry seam precisely so it
        // can be reported rather than quietly folded away.
        cells.add(cell("friendly", Strings.get("openingBarracksFriendlyFire"),
                career.friendlyFireDamage() <= 0f ? "none"
                        : String.valueOf(Math.round(career.friendlyFireDamage()))));
        return List.copyOf(cells);
    }

    private static RecordCell cell(String key, String label, String value) {
        String id = "barracks-record:" + key;
        return new RecordCell(id, id + ":label", id + ":value", label, value);
    }

    /** One line the squad rail can carry without crowding the readiness detail. */
    private static String compactRecord(SquadCareer career) {
        if (career == null || career.missionsDeployed() == 0) return Strings.get("openingBarracksNoOperationsOnRecord");
        return Strings.format("openingBarracksWOpsConfirmed", career.missionsWon(), career.missionsDeployed(), career.kills());
    }

    private String buildSelectedSummary() {
        revision.get();
        MarineSquad squad = selectedSquad();
        if (squad == null) return Strings.get("openingBarracksNoLineFormationsOnTheCompanyRoll");
        int ready = roster.readyCount(squad);
        int wounded = woundedCount(squad);
        String summary = Strings.format("openingBarracksReadyAboardRecoveringOpenBillets", ready, wounded, Math.max(0, MarineSquad.CAPACITY - ready - wounded));
        int systems = integralSystemCount(squad);
        if (systems == 0) return summary;
        return Strings.format("openingBarracksSystemsAboard", summary, systems);
    }

    /** Squad total; individual carried systems also appear in the muster kit. */
    private int integralSystemCount(MarineSquad squad) {
        int carrying = 0;
        for (String id : roster.manningMemberIds(squad)) {
            MarineSoldier soldier = roster.soldierById(id);
            if (soldier != null && soldier.armorDef().hasIntegralSystem()) carrying++;
        }
        return carrying;
    }

    private String buildQuartersStatus() {
        revision.get();
        MarineSquad squad = selectedSquad();
        if (squad == null) return Strings.get("openingBarracksQuartersUnassigned");
        if (squad.stationed()) {
            return Strings.get("openingBarracksQuartersVacantFormationDeployedOnStationingDuty");
        }
        int aboard = sceneMarines().size();
        int wounded = woundedCount(squad);
        if (aboard == 0) return Strings.get("openingBarracksQuartersVacantNoReadyPersonnelAboard");
        return Strings.format("openingBarracksMarinesAboardTransitWatch", aboard, (wounded > 0 ? Strings.format("openingBarracksInRecovery", wounded) : ""));
    }

    private MarineSquad selectedSquad() {
        String selected = selectedSquadId.get();
        if (selected == null) return null;
        MarineSquad squad = roster.squadById(selected);
        return squad != null && !squad.reserve() ? squad : null;
    }

    private String firstSquadId() {
        List<MarineSquad> squads = lineSquads();
        return squads.isEmpty() ? null : squads.get(0).id();
    }

    private List<MarineSquad> lineSquads() {
        return roster.squads().stream().filter(squad -> !squad.reserve()).toList();
    }

    private int woundedCount(MarineSquad squad) {
        int wounded = 0;
        for (String id : roster.manningMemberIds(squad)) {
            MarineSoldier soldier = roster.soldierById(id);
            if (soldier != null && soldier.status() == MarineSoldierStatus.WIA) wounded++;
        }
        return wounded;
    }

    private static String teamLabel(int team) {
        return switch (team) {
            case 0 -> Strings.get("openingBarracksAlpha");
            case 1 -> Strings.get("openingBarracksBravo");
            default -> Strings.get("openingBarracksCharlie");
        };
    }

    public record RecordCell(String id, String labelId, String valueId,
                            String label, String value) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "labelId" -> labelId;
                case "valueId" -> valueId;
                case "label" -> label;
                case "value" -> value;
                default -> throw new IllegalArgumentException(
                        "Unknown barracks record property: " + property);
            };
        }
    }

    public record SquadRow(String id, String nameId, String statusId, String detailId,
                           String recordId, String classes, String name, String status,
                           String detail, String record, Runnable select)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "nameId" -> nameId;
                case "statusId" -> statusId;
                case "detailId" -> detailId;
                case "recordId" -> recordId;
                case "classes" -> classes;
                case "name" -> name;
                case "status" -> status;
                case "detail" -> detail;
                case "record" -> record;
                case "select" -> select;
                default -> throw new IllegalArgumentException(
                        "Unknown barracks squad property: " + property);
            };
        }
    }

    /** Uses the same held-weapon family and grade variants as the ship and battle sprites. */
    private static String primaryIcon(MarineSoldier soldier) {
        return EquipmentIcons.primary(soldier.primaryDef(), soldier.primaryGrade());
    }

    public record MusterItem(String id, String icon, SpecSheet sheet) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "imageId" -> id + ":image";
                case "icon" -> icon;
                default -> throw new IllegalArgumentException("Unknown muster equipment property: " + property);
            };
        }
    }

    public record MusterRow(String id, String nameId, String detailId,
                            String classes, String name, String detail, String position,
                            List<MusterItem> items, SpecSheet sheet)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "nameId" -> nameId;
                case "detailId" -> detailId;
                case "classes" -> classes;
                case "name" -> name;
                case "detail" -> detail;
                case "position" -> position;
                case "positionId" -> id + ":position";
                case "headerId" -> id + ":header";
                case "kitId" -> id + ":kit";
                case "slotsId" -> id + ":slots";
                case "items" -> items;
                default -> throw new IllegalArgumentException(
                        "Unknown barracks muster property: " + property);
            };
        }
    }
}
