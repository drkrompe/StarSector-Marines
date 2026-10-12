package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.ExtractionCommandSnapshot;
import com.dillon.starsectormarines.battle.command.RescueCommandSnapshot;
import com.dillon.starsectormarines.battle.command.SilentColonyCommandSnapshot;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationSystem;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Item;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Marker;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.SquadActivity;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.State;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;

/** Mission adapters produce one public picture. Adding a family never requires another HUD. */
public final class MissionObjectiveProjection {
    private MissionObjectiveProjection() { }

    public static MissionObjectiveSnapshot capture(BattleSimulation sim) {
        if (sim == null) return MissionObjectiveSnapshot.EMPTY;
        List<Item> items = new ArrayList<>();
        for (Objective objective : sim.getObjectives()) {
            if (objective.owningFaction() == Faction.MARINE && objective instanceof ExtractionPayloadObjective payload)
                items.add(payload(payload, sim.getCivilianEvacuationProgress()));
        }
        if (items.isEmpty()) return MissionObjectiveSnapshot.EMPTY;
        items.sort(Comparator.comparing(Item::id));
        var activities = activities(sim.getCommanderSnapshot(Faction.MARINE), id -> {
            var squad = sim.getSquad(id);
            return squad != null && squad.campaignLabel != null ? squad.campaignLabel : "Squad " + id;
        });
        // A player lease can change between commander pulses. Show the current owner immediately.
        List<SquadActivity> current = new ArrayList<>();
        for (var activity : activities) {
            if (sim.squadMemberCount(activity.squadId()) == 0) continue;
            var directive = sim.getSquadCommandDirective(activity.squadId());
            if (directive != null && directive.authority() == CommandAuthority.PLAYER_INTERVENTION) {
                current.add(new SquadActivity(activity.squadId(), activity.name(), "Following your order", "Player command"));
            } else current.add(activity);
        }
        return new MissionObjectiveSnapshot(items, current);
    }

    public static Item payload(ExtractionPayloadObjective p, CivilianEvacuationSystem.Progress evacuation) {
        State state = p.isFailed() ? State.FAILED : p.isComplete() ? State.COMPLETE : State.ACTIVE;
        String phase = switch (p.extractionPhase()) {
            case AT_SOURCE -> "REACH SOURCE";
            case SECURING -> "SECURING";
            case IN_TRANSIT -> "ESCORTING";
            case BOARDING -> "BOARDING";
            case COMPLETE -> "COMPLETE";
            case FAILED -> "FAILED";
        };
        boolean archive = p.payloadKind() == ExtractionPayloadObjective.Kind.ARCHIVE;
        boolean cohort = p.payloadKind() == ExtractionPayloadObjective.Kind.COHORT;
        boolean atSource = p.extractionPhase() == ExtractionPayloadObjective.Phase.AT_SOURCE
                || p.extractionPhase() == ExtractionPayloadObjective.Phase.SECURING
                || (state == State.FAILED && !p.alarmActive());
        String instruction = archive ? "Keep marines inside the archive room."
                : atSource ? "Reach the source and secure the recovery site."
                : "Keep a close escort through the pickup corridor.";
        String measure = "";
        String blocker = "";
        float progress = p.normalizedProgress();
        if (p instanceof ExtractionObjective extraction) {
            if (atSource) {
                instruction = "Hold the source uncontested for " + seconds(extraction.secureDuration()) + ".";
                progress = extraction.secureProgress() / extraction.secureDuration();
                measure = "Securing / " + seconds(extraction.secureProgress()) + " of " + seconds(extraction.secureDuration());
            } else if (p.extractionPhase() == ExtractionPayloadObjective.Phase.BOARDING) {
                instruction = "Hold the pickup uncontested to finish boarding.";
                progress = extraction.boardingProgress() / extraction.boardingDuration();
                measure = "Boarding / " + seconds(extraction.boardingProgress()) + " of " + seconds(extraction.boardingDuration());
            } else {
                progress = (float) extraction.routeIndex() / Math.max(1, extraction.routeCellCount() - 1);
                measure = "Route / " + Math.round(progress * 100) + "% to pickup";
            }
            blocker = extraction.contested() ? "Hostile control is preventing progress."
                    : !p.escortPresent() ? atSource ? "Marines must reach and hold the source." : "Restore close escort at the payload." : "";
            if (extraction.lossProgress() > 0) blocker = "Unescorted under hostile control / loss in "
                    + seconds(extraction.lossDuration() - extraction.lossProgress());
            else if (extraction.abandonmentProgress() > 0) blocker = "No escort / abandonment in "
                    + seconds(extraction.abandonmentDuration() - extraction.abandonmentProgress());
        } else if (p instanceof ColonyArchiveObjective colony) {
            instruction = "Keep marines in the archive room for " + seconds(colony.recoveryDuration()) + ".";
            measure = "Recovery / " + seconds(colony.progress()) + " of " + seconds(colony.recoveryDuration());
            if (!p.escortPresent()) blocker = "Marines must enter and remain in the archive room.";
        } else if (cohort) {
            measure = p.boardedElements() + " aboard / " + p.activeElements() + " awaiting rescue / " + p.lostElements() + " lost";
            if (atSource) {
                instruction = evacuation.storyEscort() ? "Secure the shelter with the assigned escort for 2s."
                        : "Reach the shelter entrance to release the survivors.";
                progress = evacuation.storyEscort() ? evacuation.securedTicks() / 60f : 0;
                blocker = evacuation.shelterThreat() ? "Nearby threats prevent shelter release."
                        : evacuation.securedTicks() == 0 ? "Waiting for marines at the shelter entrance." : "";
                if (evacuation.securedTicks() > 0) phase = "SECURING SHELTER";
            } else {
                instruction = "Escort every remaining survivor to the pickup.";
                List<String> holds = new ArrayList<>();
                if (evacuation.unescorted() > 0) holds.add(evacuation.unescorted() + " need close escort");
                if (evacuation.blocked() > 0) holds.add(evacuation.blocked() + " have no route");
                if (evacuation.awaitingPickup() > 0) holds.add(evacuation.awaitingPickup() + " waiting for pickup");
                if (evacuation.screened() > 0) holds.add(evacuation.screened() + " sheltering behind escort");
                blocker = String.join("; ", holds);
            }
        }
        if (state == State.COMPLETE) {
            blocker = "";
            progress = 1;
            instruction = archive ? "Archive recovered. Recovery squads can rejoin the escort."
                    : cohort ? "All survivors accounted for; evacuation resolved." : "Package recovered and extracted.";
            if (!cohort) measure = "Objective complete";
        } else if (state == State.FAILED) {
            blocker = p instanceof CivilianEvacuationObjective civilian && civilian.pickupLost()
                    ? "Pickup lost before evacuation finished."
                    : p.failureReason() == ExtractionPayloadObjective.Failure.ABANDONED ? "Package abandoned."
                    : cohort ? "No survivors evacuated." : "Package lost to hostile control.";
            instruction = "Objective failed.";
        }
        String id = p.payloadId();
        String sourceName = p.sourceName();
        List<Marker> markers = new ArrayList<>();
        markers.add(new Marker(id + "-source", sourceName, p.sourceMarkerX(), p.sourceMarkerY(),
                archive || atSource ? phase : "SECURED", archive || atSource ? state : State.COMPLETE));
        String focus = id + "-source";
        if (!archive && p.egressCellX() >= 0 && p.egressCellY() >= 0) {
            markers.add(new Marker(id + "-pickup", p.egressName(), p.egressCellX(), p.egressCellY(),
                    state == State.ACTIVE ? "PICKUP" : phase, state));
            if (!atSource) focus = id + "-pickup";
        }
        if (!archive && !atSource && state == State.ACTIVE && p.payloadCellX() >= 0 && p.payloadCellY() >= 0
                && Math.abs(p.payloadCellX() - p.egressCellX()) + Math.abs(p.payloadCellY() - p.egressCellY()) > 3) {
            markers.add(new Marker(id + "-payload", p.payloadName(), p.payloadCellX(), p.payloadCellY(),
                    blocker.isEmpty() ? phase : "NEEDS ATTENTION", state));
            focus = id + "-payload";
        }
        String steps = archive ? "Recover archive"
                : atSource ? "1 Secure  >  2 Escort  >  3 Board"
                : state == State.COMPLETE ? "Secure: done / Escort: done / Board: done"
                : "Secure: done  >  Escort  >  Board";
        return new Item(id, p.payloadName(), state, phase, steps, instruction, measure,
                Math.max(0, Math.min(1, progress)), blocker, markers, focus);
    }

    private static String seconds(float value) { return Math.max(0, Math.round(value * 10)) / 10f + "s"; }

    /** Friendly command intent, not omniscient enemy positions or invented successful execution. */
    public static List<SquadActivity> activities(CommanderSnapshot<?> commander, IntFunction<String> names) {
        if (commander == null || commander.perspective() != Faction.MARINE) return List.of();
        List<SquadActivity> rows = new ArrayList<>();
        if (commander.detail() instanceof ExtractionCommandSnapshot extraction) {
            for (var s : extraction.squadIntents()) rows.add(activity(s.squadId(), names, s.role().name(), s.localContact(), false));
        } else if (commander.detail() instanceof RescueCommandSnapshot rescue) {
            for (var s : rescue.squadIntents()) rows.add(activity(s.squadId(), names, s.role().name(), s.localContact(), s.locallySlowed()));
        } else if (commander.detail() instanceof SilentColonyCommandSnapshot colony) {
            for (var s : colony.squadIntents()) rows.add(activity(s.squadId(), names, s.role().name(), s.localContact(), false));
        }
        rows.sort(Comparator.comparingInt(SquadActivity::squadId));
        return List.copyOf(rows);
    }

    private static SquadActivity activity(int id, IntFunction<String> names, String role, boolean contact, boolean slowed) {
        String task = switch (role) {
            case "ARCHIVE_RECOVERY" -> "Recovering colony archive";
            case "SURVIVOR_ESCORT", "COHORT_ESCORT" -> "Escorting survivors";
            case "PAYLOAD_ELEMENT" -> "Recovering and escorting package";
            case "CLOSE_ESCORT" -> "Providing close escort";
            case "LEAD_SCREEN" -> "Screening the route ahead";
            case "LEFT_SCREEN" -> "Protecting the left flank";
            case "RIGHT_SCREEN" -> "Protecting the right flank";
            case "REAR_SCREEN" -> "Protecting the rear";
            case "SHELTER_GUARD" -> "Guarding the shelter";
            case "PICKUP_GUARD" -> "Guarding the pickup";
            case "STRANDED" -> "No reachable mission route";
            case "RELEASED" -> "Mission assignment finished";
            default -> "Following separate orders";
        };
        return new SquadActivity(id, names.apply(id), task,
                slowed ? "Holding for local contact" : contact ? "In contact" : "STRANDED".equals(role) ? "Route unavailable" : "Assigned task");
    }
}
