package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.CompanyMechLedger;
import com.dillon.starsectormarines.battle.mech.MechSections;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.power.BayManifest;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.MechBay;
import com.dillon.starsectormarines.marine.MechDamage;
import com.dillon.starsectormarines.marine.MechRepairs;
import com.dillon.starsectormarines.ops.battleview.ArmoryLoadoutPreviewComposer;
import com.dillon.starsectormarines.ops.battleview.DamageDollCanvas;
import com.dillon.starsectormarines.ops.battleview.SquadMuster;
import com.dillon.starsectormarines.ops.battleview.SquadMusterCanvas;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.spec.SpecSheet;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The debrief's field force: the briefing's picture tiles as the battle left them, squads with their lost
 * marines' seats empty and machines with their dolls worn, under how long the field's worst repair takes.
 */
final class DebriefForce {

    /** How many tiles the debrief's inset shows on one row at full width; past it the inset takes two. */
    static final int TILES_PER_ROW = 8;

    private DebriefForce() {
    }

    /**
     * One machine that took the field.
     *
     * @param variant its chassis, null for a vehicle
     * @param report what the battle left of it, null for a vehicle
     * @param vehicle its hull, null for a mech
     * @param repairDays days until it is whole; zero when lost, untouched or a vehicle
     */
    record Machine(String id, String name, MechVariant variant, MechSections.Report report, VehicleType vehicle,
                   boolean lost, float repairDays) implements MarkupPropertySource {

        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "classes" -> "field-tile console-readout" + (lost ? " field-tile-refused" : "");
                case "musterId" -> id + "-muster";
                case "musterClasses" -> "field-absent";
                case "dollId" -> id + "-doll";
                case "dollClasses" -> "field-doll";
                case "countId" -> id + "-count";
                case "countClasses" -> lost ? "label heading tone-danger field-count field-count-lost"
                        : repairDays > 0f ? "label heading tone-accent field-count field-count-repair" : "field-absent";
                case "count" -> lost ? Strings.get("resultsScreenMachineLostBadge")
                        : repairDays > 0f ? Strings.format("resultsScreenMachineDays", tenths(repairDays)) : "";
                default -> null;
            };
        }

        String typeName() {
            return variant != null ? variant.displayName() : BayManifest.vehicleName(vehicle);
        }

        SpecSheet hint() {
            List<String> notes = new ArrayList<>();
            if (vehicle != null) notes.add(Strings.get(lost ? "resultsScreenVehicleLost" : "resultsScreenVehicleWhole"));
            else if (lost) notes.add(Strings.get("resultsScreenMachineLost"));
            else notes.add(repairDays > 0f ? Strings.format("resultsScreenMachineRepair", tenths(repairDays))
                    : Strings.get("resultsScreenMachineWhole"));
            if (report != null && !lost) {
                for (MechWeaponComponent weapon : report.lostMounts()) {
                    if (weapon != null) notes.add(Strings.format("resultsScreenMachineWeaponLost", weapon.displayName()));
                }
            }
            return new SpecSheet(name, typeName(), null, "command", List.of(), notes);
        }
    }

    /** The machines to show: the company's own, and on a debug battle whatever it fielded. */
    static List<Machine> machines(MissionOutcome outcome, MechBay bay, float day) {
        if (outcome == null) return List.of();
        boolean all = outcome.missionSource.isDebug();
        List<Machine> machines = new ArrayList<>();
        for (CompanyMechLedger.Fielded mech : outcome.fieldedMechs) {
            if (mech.campaignId() == null && !all) continue;
            String name = mech.displayName() != null ? mech.displayName() : mech.variant().displayName();
            machines.add(new Machine("results-field-machine-" + machines.size(), name, mech.variant(), mech.report(),
                    null, mech.lost(), mech.lost() ? 0f : repairDays(mech, bay, day)));
        }
        for (ConvoyService.FieldedVehicle vehicle : outcome.fieldedVehicles) {
            if (vehicle.campaignId() == null && !all) continue;
            String name = vehicle.callsign() != null ? vehicle.callsign() : BayManifest.vehicleName(vehicle.type());
            machines.add(new Machine("results-field-machine-" + machines.size(), name, null, null,
                    vehicle.type(), vehicle.lost(), 0f));
        }
        return List.copyOf(machines);
    }

    /**
     * The mech's own clock once settlement has started it, which is the number the Mech Lab shows. A mech with
     * no clock (a debug battle settles nothing) is quoted what the gantry would take, before any ship refit.
     */
    static float repairDays(CompanyMechLedger.Fielded mech, MechBay bay, float day) {
        CampaignMech owned = bay != null && mech.campaignId() != null ? bay.mechById(mech.campaignId()) : null;
        if (owned != null) return owned.repairDaysRemaining(day);
        MechRepairs.Estimate estimate = MechRepairs.estimate(mech.variant(), MechDamage.of(mech.report()));
        return estimate != null ? estimate.days() : 0f;
    }

    /** Days until every machine that came back is whole: the longest single repair, since the gantries work side by side. */
    static float daysToFullyRepaired(List<Machine> machines) {
        float days = 0f;
        for (Machine machine : machines) days = Math.max(days, machine.repairDays());
        return days;
    }

    /** One squad tile: the same picture the briefing draws, its tally and names in the hover. */
    static BriefingViewModel.SquadPicture squad(int index, SquadMuster muster, ResultsScreen.PersonnelRow row) {
        return new BriefingViewModel.SquadPicture("results-field-squad-" + index, muster, new SpecSheet(row.name(),
                row.summary(), null, "command", List.of(), List.of(row.members())));
    }

    /** Fills the section's props and returns its tiles, squads then machines; the section hides with none. */
    static List<Object> put(Map<String, Object> props, List<BriefingViewModel.SquadPicture> squads, List<Machine> machines) {
        List<Object> tiles = new ArrayList<>(BriefingViewModel.stackSquads(squads));
        tiles.addAll(machines);
        props.put("fieldTiles", List.copyOf(tiles));
        props.put("forceClasses", tiles.isEmpty() ? "hidden" : "results-force");
        props.put("forceInsetClasses", "field-inset console-well "
                + (tiles.size() > TILES_PER_ROW ? "field-inset-two" : "field-inset-one"));
        float days = daysToFullyRepaired(machines);
        props.put("repairSummary", machines.isEmpty() ? "" : days > 0f
                ? Strings.format("resultsScreenRepairDays", tenths(days)) : Strings.get("resultsScreenRepairNone"));
        props.put("repairClasses", "label heading force-repair " + (days > 0f ? "tone-accent" : "tone-good"));
        return List.copyOf(tiles);
    }

    /** Draws every tile's muster or doll. */
    static void install(UiDocument document, MarkupInstance markup, List<Object> tiles,
                        ArmoryLoadoutPreviewComposer.Assets marineSprites) {
        for (Object tile : tiles) {
            if (tile instanceof BriefingViewModel.SquadPicture squad) {
                document.canvases().set(markup.requireElement(squad.id() + "-muster"),
                        new SquadMusterCanvas(squad::muster, SquadMusterCanvas.SHIPBOARD, marineSprites));
            } else if (tile instanceof Machine machine) {
                DamageDollCanvas.View view = new DamageDollCanvas.View();
                if (machine.variant() != null) view.readReport(machine.variant(), machine.report(), machine.lost());
                else if (machine.lost()) view.readWrecked(machine.vehicle());
                else view.readStock(machine.vehicle());
                document.canvases().set(markup.requireElement(machine.id() + "-doll"),
                        DamageDollCanvas.bare(view, BriefingLift.shortLabel(machine.typeName())));
            }
        }
    }

    /** Hovers on every tile. */
    static void bindHints(SpecSheetBinder binder, MarkupInstance markup, List<Object> tiles) {
        for (Object tile : tiles) {
            if (tile instanceof BriefingViewModel.SquadPicture squad) {
                binder.bind(markup.requireElement(squad.id()), squad.hint());
            } else if (tile instanceof Machine machine) {
                binder.bind(markup.requireElement(machine.id()), machine.hint());
            }
        }
    }

    /** Rounded up to the tenth, as the Mech Lab quotes a repair. */
    private static double tenths(float days) {
        return Math.ceil(days * 10d) / 10d;
    }
}
