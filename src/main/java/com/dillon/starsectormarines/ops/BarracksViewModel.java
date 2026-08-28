package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadCareer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.ComputedSignal;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

/** Read-only projection of line squads into their shipboard quarters. */
public final class BarracksViewModel {

    private final MarineRoster roster;
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
            return squad != null ? squad.name() : "No line squad assigned";
        });
        selectedSquadSummary = reactor.computed(this::buildSelectedSummary);
        quartersStatus = reactor.computed(this::buildQuartersStatus);
    }

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
    }

    private List<SquadRow> buildSquadRows() {
        revision.get();
        List<SquadRow> rows = new ArrayList<>();
        for (MarineSquad squad : lineSquads()) {
            int ready = roster.readyCount(squad);
            int wounded = woundedCount(squad);
            boolean selected = squad.id().equals(selectedSquadId.get());
            String status = squad.stationed() ? "STATIONED"
                    : ready == MarineSquad.CAPACITY ? "READY"
                    : wounded > 0 ? "RECOVERING" : "UNDERSTRENGTH";
            String classes = "barracks-squad-row"
                    + (selected ? " selected" : "")
                    + (squad.stationed() ? " stationed" : "");
            String id = "barracks-squad:" + squad.id();
            rows.add(new SquadRow(id, id + ":name", id + ":status", id + ":detail",
                    id + ":record", classes, squad.name(), status,
                    ready + " / " + MarineSquad.CAPACITY + " RTD"
                            + (wounded > 0 ? "  ·  " + wounded + " WIA" : ""),
                    compactRecord(squad.career()),
                    () -> selectedSquadId.set(squad.id())));
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
            if (billet >= memberIds.size()) {
                rows.add(new MusterRow(id, id + ":name", id + ":detail",
                        "muster-row vacant", teamLabel(team) + " " + (billet % 4 + 1),
                        "Open billet"));
                continue;
            }
            MarineSoldier soldier = roster.soldierById(memberIds.get(billet));
            if (soldier == null) continue;
            boolean wounded = soldier.status() == MarineSoldierStatus.WIA;
            String detail = wounded
                    ? "WIA  ·  RTD " + FleetArmoryViewModel.formatRemainingCompact(
                            soldier.unavailableUntilDay(), currentDay.getAsDouble())
                    : soldier.primaryDef().displayName + "  ·  "
                    + soldier.armorDef().displayName();
            rows.add(new MusterRow(id, id + ":name", id + ":detail",
                    "muster-row " + (wounded ? "wounded" : "ready"),
                    soldier.enlistedRank().abbreviation() + " " + soldier.name(), detail));
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
        cells.add(cell("operations", "OPERATIONS", career.missionsDeployed() == 0 ? "none"
                : career.missionsDeployed() + "  (" + career.missionsWon() + " won)"));
        cells.add(cell("kills", "CONFIRMED", String.valueOf(career.kills())));
        cells.add(cell("accuracy", "ROUNDS ON TARGET", career.roundsFired() == 0 ? "no data"
                : Math.round(career.landedFraction() * 100f) + "%"));
        cells.add(cell("casualties", "CASUALTIES", career.marinesDeployed() == 0 ? "none"
                : career.casualties() + " of " + career.marinesDeployed()));
        // Kept un-netted from damage dealt at the telemetry seam precisely so it
        // can be reported rather than quietly folded away.
        cells.add(cell("friendly", "FRIENDLY FIRE",
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
        if (career == null || career.missionsDeployed() == 0) return "No operations on record";
        return career.missionsWon() + "W / " + career.missionsDeployed() + " ops"
                + "  ·  " + career.kills() + " confirmed";
    }

    private String buildSelectedSummary() {
        revision.get();
        MarineSquad squad = selectedSquad();
        if (squad == null) return "No line formations on the company roll";
        int ready = roster.readyCount(squad);
        int wounded = woundedCount(squad);
        return ready + " ready aboard  ·  " + wounded + " recovering  ·  "
                + Math.max(0, MarineSquad.CAPACITY - ready - wounded) + " open billets";
    }

    private String buildQuartersStatus() {
        revision.get();
        MarineSquad squad = selectedSquad();
        if (squad == null) return "Quarters unassigned";
        if (squad.stationed()) {
            return "Quarters vacant  ·  formation deployed on stationing duty";
        }
        int aboard = sceneMarines().size();
        int wounded = woundedCount(squad);
        if (aboard == 0) return "Quarters vacant  ·  no ready personnel aboard";
        return aboard + " marines aboard  ·  transit watch"
                + (wounded > 0 ? "  ·  " + wounded + " in recovery" : "");
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
            case 0 -> "Alpha";
            case 1 -> "Bravo";
            default -> "Charlie";
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

    public record MusterRow(String id, String nameId, String detailId,
                            String classes, String name, String detail)
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
                default -> throw new IllegalArgumentException(
                        "Unknown barracks muster property: " + property);
            };
        }
    }
}
