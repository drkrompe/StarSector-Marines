package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.AutonomousMissionCommand;
import com.dillon.starsectormarines.battle.setup.ColdStartSequence;
import com.dillon.starsectormarines.battle.setup.WaterUnderSealSequence;
import com.dillon.starsectormarines.battle.setup.LimitedWarrantSequence;
import com.dillon.starsectormarines.battle.setup.YardOwnersSequence;
import com.dillon.starsectormarines.battle.world.gen.NorthWeir;
import com.dillon.starsectormarines.battle.setup.WorkingPassageSequence;
import com.dillon.starsectormarines.battle.setup.SalvageRightsSequence;
import com.dillon.starsectormarines.battle.setup.DepotHoldSequence;
import com.dillon.starsectormarines.battle.setup.ColdAccountIntervention;
import com.dillon.starsectormarines.battle.world.gen.JunctionCompound;
import com.dillon.starsectormarines.battle.world.gen.RelayNineCompound;
import com.dillon.starsectormarines.battle.world.gen.ThunderRunCompound;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommandPicture;
import com.dillon.starsectormarines.battle.command.ExtractionCommandSnapshot;
import com.dillon.starsectormarines.battle.command.RescueCommandSnapshot;
import com.dillon.starsectormarines.battle.command.SilentColonyCommandSnapshot;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationSystem;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Item;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Cell;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Marker;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.SquadActivity;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.State;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

/** Mission adapters produce one public picture. Adding a family never requires another HUD. */
public final class MissionObjectiveProjection {
    private MissionObjectiveProjection() { }

    public static MissionObjectiveSnapshot capture(BattleSimulation sim) {
        if (sim == null) return MissionObjectiveSnapshot.EMPTY;
        List<Item> items = new ArrayList<>();
        boolean territory = false;
        int index = 0;
        for (Objective objective : sim.getObjectives()) {
            if (objective.owningFaction() != Faction.MARINE) continue;
            index++;
            if (objective instanceof ExtractionPayloadObjective payload)
                items.add(payload(payload, sim.getCivilianEvacuationProgress()));
            else if (objective instanceof ChargeSiteObjective site) items.add(MissionObjectiveFamilies.charge(site));
            else if (objective instanceof RaidObjective raid) {
                if (sim.getColdAccount() == null) items.add(MissionObjectiveFamilies.raid(raid));
            } else if (objective instanceof SalvageRightsSequence salvage) items.add(buildingFootprint(
                    MissionObjectiveFamilies.salvage(salvage),sim.getTopology(),sim.getGrid(),salvage.targetBuilding()));
            else if (objective instanceof WaterUnderSealSequence weir) items.add(weirFootprints(MissionObjectiveFamilies.waterUnderSeal(weir), sim.getTopology(), sim.getGrid()));
            else if (objective instanceof YardOwnersSequence yard) items.add(MissionObjectiveFamilies.yardOwners(yard));
            else if (objective instanceof LimitedWarrantSequence warrant) items.add(MissionObjectiveFamilies.limitedWarrant(warrant));
            else if (objective instanceof ColdStartSequence repair) {
                items.add(MissionObjectiveFamilies.coldStart(repair));
                for (var post : repair.pickets()) items.add(MissionObjectiveFamilies.coldStartPicket(post));
            }
            else if (objective instanceof WorkingPassageSequence passage) items.add(MissionObjectiveFamilies.workingPassage(passage));
            else if (objective instanceof DepotHoldSequence depot) items.add(MissionObjectiveFamilies.depot(depot));
            else if (objective instanceof ColdAccountIntervention intervention) items.add(MissionObjectiveFamilies.coldAccount(intervention,
                    sim.getObjectives().stream().filter(RaidObjective.class::isInstance)
                            .map(RaidObjective.class::cast).findFirst().orElse(null)));
            else if (objective instanceof FreightConvoyObjective && sim.getThunderRun() != null)
                items.add(MissionObjectiveFamilies.freight(objective, sim.getThunderRun()));
            else if (objective instanceof ConquestObjective) territory = true;
            else {
                // Depot defense already explains both warehouse control and the finite assault.
                if (objective instanceof EliminateFactionObjective && sim.getDepotHold() != null) continue;
                var command = sim.getCommanderSnapshot(Faction.MARINE);
                if (command != null && command.detail() instanceof OpeningOperationCommandPicture opening
                        && objective instanceof EliminateFactionObjective)
                    items.add(MissionObjectiveFamilies.at("opening-" + opening.placeId(), opening.placeName(),
                            MissionObjectiveFamilies.state(objective), opening.phase().name(), "Hold the position > Defeat the assault",
                            objective.isComplete() ? "Position secured; the assault has been defeated."
                                    : "Protect the named position and defeat the finite assault.",
                            objective.isComplete() ? "Assault defeated" : objective.isFailed() ? "Position lost" : "Assault unresolved", -1, "",
                            opening.placeCellX(), opening.placeCellY()));
                else items.add(MissionObjectiveFamilies.generic(objective, index));
            }
        }
        if (territory || sim.getThunderRun() != null) {
            var commander = sim.getCommanderSnapshot(Faction.MARINE);
            String strategy = commander != null ? commander.strategy()
                    : sim.getCommander(Faction.MARINE) instanceof AutonomousMissionCommand<?, ?> wired ? wired.strategyId() : "";
            List<String> names = strategy.startsWith("junction") ? JunctionCompound.SITE_NAMES
                    : strategy.startsWith("relay-nine") ? RelayNineCompound.SITE_NAMES
                    : sim.getThunderRun() != null ? ThunderRunCompound.SITE_NAMES : List.of();
            int site = 0;
            for (var record : sim.getCompoundService().getRecords()) {
                String name = site < names.size() ? names.get(site)
                        : record.node.kind.name().replace('_', ' ') + " " + (site + 1);
                items.add(MissionObjectiveFamilies.compound(record, site, name));
                site++;
            }
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

    /** The beacon and Locate share a legal central floor cell; the outline is the referee's actual interior. */
    static Item buildingFootprint(Item item,CellTopology topology,NavigationGrid grid,PointOfInterest building) {
        if(building==null || item.focus()==null)return item;
        var cells=new ArrayList<Cell>();
        double centerX=0,centerY=0;
        for(int y=building.top;y<=building.bottom;y++)for(int x=building.left;x<=building.right;x++) {
            if(!SalvageRightsSequence.insideBuilding(topology,building,x+.5f,y+.5f))continue;
            cells.add(new Cell(x,y));centerX+=x;centerY+=y;
        }
        if(cells.isEmpty())return item;
        centerX/=cells.size();centerY/=cells.size();
        Marker focus=item.focus();int anchorX=focus.x(),anchorY=focus.y();
        double nearest=Double.MAX_VALUE;
        for(var cell:cells) {
            if(!grid.isWalkable(cell.x(),cell.y()))continue;
            double distance=Math.hypot(cell.x()-centerX,cell.y()-centerY);
            if(distance<nearest){nearest=distance;anchorX=cell.x();anchorY=cell.y();}
        }
        var markers=new ArrayList<Marker>();
        for(var marker:item.markers())markers.add(marker.id().equals(focus.id())
                ?new Marker(marker.id(),marker.name(),anchorX,anchorY,marker.status(),marker.state(),cells):marker);
        return new Item(item.id(),item.name(),item.state(),item.phase(),item.steps(),item.instruction(),
                item.progressText(),item.progress(),item.blocker(),markers,item.focusMarkerId());
    }

    /** Switch outlines use the same occupied room mask as the work referee. */
    static Item weirFootprints(Item item, CellTopology topology, NavigationGrid grid) {
        var markers = new ArrayList<Marker>();
        for (var marker : item.markers()) {
            if (!marker.id().equals("weir-north") && !marker.id().equals("weir-south")) { markers.add(marker); continue; }
            boolean north = marker.id().equals("weir-north");
            var cells = new ArrayList<Cell>();
            for (int y = north ? 68 : 35; y <= (north ? 78 : 45); y++) for (int x = 74; x <= 86; x++)
                if (NorthWeir.insideRoom(topology, x, y, north)) cells.add(new Cell(x, y));
            int x = marker.x(), y = marker.y(); double best = Double.MAX_VALUE;
            for (var cell : cells) if (grid.isWalkable(cell.x(), cell.y())) {
                double distance = Math.hypot(cell.x() - marker.x(), cell.y() - marker.y());
                if (distance < best) { best = distance; x = cell.x(); y = cell.y(); }
            }
            markers.add(new Marker(marker.id(), marker.name(), x, y, marker.status(), marker.state(), cells));
        }
        return new Item(item.id(), item.name(), item.state(), item.phase(), item.steps(), item.instruction(),
                item.progressText(), item.progress(), item.blocker(), markers, item.focusMarkerId());
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
        if (rows.isEmpty()) for (var directive : commander.directives()) {
            if (!directive.ownsSquad()) continue;
            var assignment = directive.assignment();
            String task = directive.authority() == CommandAuthority.PLAYER_INTERVENTION ? "Following your order"
                    : assignment == null ? "Following mission command"
                    : assignment.kind().name().replace('_', ' ').toLowerCase(Locale.ROOT);
            rows.add(new SquadActivity(directive.squadId(), names.apply(directive.squadId()), task,
                    directive.authority() == CommandAuthority.PLAYER_INTERVENTION ? "Player command" : "Assigned task"));
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
