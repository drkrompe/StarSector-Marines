package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.AutonomousMissionCommand;
import com.dillon.starsectormarines.battle.setup.ColdStartSequence;
import com.dillon.starsectormarines.battle.setup.MissileAaTestSetup.LaneObjective;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.setup.WaterUnderSealSequence;
import com.dillon.starsectormarines.battle.setup.LimitedWarrantSequence;
import com.dillon.starsectormarines.battle.setup.DistrictEscortSequence;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence;
import com.dillon.starsectormarines.battle.setup.YardOwnersSequence;
import com.dillon.starsectormarines.battle.setup.SmallWarSequence;
import com.dillon.starsectormarines.battle.setup.TermsOfProtectionSequence;

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
import com.dillon.starsectormarines.battle.objective.side.SideField;
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
            if (objective instanceof LaneObjective lane)
                items.add(MissionObjectiveFamilies.at("missile-aa-" + lane.lane.id(), lane.displayName(),
                        State.ACTIVE, Strings.get("missileAaTestPhase"), "",
                        Strings.get("missileAaTestInstruction"), lane.measure(sim), -1, "",
                        lane.lane.x(), lane.lane.y()));
            else if (objective instanceof ExtractionPayloadObjective payload)
                items.add(payload(payload, sim.getCivilianEvacuationProgress()));
            else if (objective instanceof ChargeSiteObjective site) items.add(MissionObjectiveFamilies.charge(site));
            else if (objective instanceof RaidObjective raid) {
                if (sim.getColdAccount() == null) items.add(MissionObjectiveFamilies.raid(raid));
            } else if (objective instanceof SalvageRightsSequence salvage) items.add(buildingFootprint(
                    MissionObjectiveFamilies.salvage(salvage),sim.getTopology(),sim.getGrid(),salvage.targetBuilding()));
            else if (objective instanceof WaterUnderSealSequence weir) items.add(weirFootprints(MissionObjectiveFamilies.waterUnderSeal(weir), sim.getTopology(), sim.getGrid()));
            else if (objective instanceof YardOwnersSequence yard) items.add(MissionObjectiveFamilies.yardOwners(yard));
            else if (objective instanceof SmallWarSequence sluice) items.add(MissionObjectiveFamilies.smallWar(sluice));
            else if (objective instanceof TermsOfProtectionSequence terms) items.add(MissionObjectiveFamilies.termsOfProtection(terms));
            else if (objective instanceof LimitedWarrantSequence warrant) items.add(MissionObjectiveFamilies.limitedWarrant(warrant));
            else if (objective instanceof DistrictEscortSequence escort) items.add(MissionObjectiveFamilies.districtEscort(escort));
            else if (objective instanceof DustLedgerSequence ledger) items.addAll(DustLedgerChecklist.items(ledger, sim));
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
                            MissionObjectiveFamilies.state(objective), Strings.named("objectivePhase", opening.phase().name()),
                            Strings.get("objectiveOpeningSteps"),
                            Strings.get(objective.isComplete() ? "objectiveOpeningDone" : "objectiveOpeningInstruction"),
                            Strings.get(objective.isComplete() ? "objectiveOpeningDefeated"
                                    : objective.isFailed() ? "objectiveOpeningLost" : "objectiveOpeningUnresolved"), -1, "",
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
                        : Strings.format("objectiveSiteFallback", Strings.named("objectiveSiteKind", record.node.kind.name()), site + 1);
                items.add(MissionObjectiveFamilies.compound(record, site, name));
                site++;
            }
        }
        items.sort(Comparator.comparing(Item::id));
        // Side objectives follow the mission's own rows, in offer order.
        items.addAll(SideObjectiveRows.items(sim.getSideObjectives(), SideField.of(sim)));
        if (items.isEmpty()) return MissionObjectiveSnapshot.EMPTY;
        var activities = activities(sim.getCommanderSnapshot(Faction.MARINE), id -> {
            var squad = sim.getSquad(id);
            return squad != null && squad.campaignLabel != null ? squad.campaignLabel : Strings.format("objectiveSquadFallback", id);
        });
        // A player lease can change between commander pulses. Show the current owner immediately.
        List<SquadActivity> current = new ArrayList<>();
        for (var activity : activities) {
            if (sim.squadMemberCount(activity.squadId()) == 0) continue;
            var directive = sim.getSquadCommandDirective(activity.squadId());
            if (directive != null && directive.authority() == CommandAuthority.PLAYER_INTERVENTION) {
                current.add(new SquadActivity(activity.squadId(), activity.name(), Strings.get("objectiveTaskPlayerOrder"), Strings.get("objectiveStatusPlayerCommand")));
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
            case AT_SOURCE -> Strings.get("objectivePayloadAtSource");
            case SECURING -> Strings.get("objectivePayloadSecuring");
            case IN_TRANSIT -> Strings.get("objectivePayloadInTransit");
            case BOARDING -> Strings.get("objectivePayloadBoarding");
            case COMPLETE -> Strings.get("objectivePayloadComplete");
            case FAILED -> Strings.get("objectivePayloadFailed");
        };
        boolean archive = p.payloadKind() == ExtractionPayloadObjective.Kind.ARCHIVE;
        boolean cohort = p.payloadKind() == ExtractionPayloadObjective.Kind.COHORT;
        boolean atSource = p.extractionPhase() == ExtractionPayloadObjective.Phase.AT_SOURCE
                || p.extractionPhase() == ExtractionPayloadObjective.Phase.SECURING
                || (state == State.FAILED && !p.alarmActive());
        String instruction = archive ? Strings.get("objectivePayloadArchiveInstruction")
                : atSource ? Strings.get("objectivePayloadReachSource")
                : Strings.get("objectivePayloadEscortCorridor");
        String measure = "";
        String blocker = "";
        float progress = p.normalizedProgress();
        if (p instanceof ExtractionObjective extraction) {
            if (atSource) {
                instruction = Strings.format("objectivePayloadHoldSource", seconds(extraction.secureDuration()));
                progress = extraction.secureProgress() / extraction.secureDuration();
                measure = MissionObjectiveFamilies.timed(Strings.get("objectivePayloadSecuringTimer"), extraction.secureProgress(), extraction.secureDuration());
            } else if (p.extractionPhase() == ExtractionPayloadObjective.Phase.BOARDING) {
                instruction = Strings.get("objectivePayloadHoldPickup");
                progress = extraction.boardingProgress() / extraction.boardingDuration();
                measure = MissionObjectiveFamilies.timed(Strings.get("objectivePayloadBoardingTimer"), extraction.boardingProgress(), extraction.boardingDuration());
            } else {
                progress = (float) extraction.routeIndex() / Math.max(1, extraction.routeCellCount() - 1);
                measure = Strings.format("objectivePayloadRoute", Math.round(progress * 100));
            }
            blocker = extraction.contested() ? Strings.get("objectivePayloadHostileControl")
                    : !p.escortPresent() ? atSource ? Strings.get("objectivePayloadReachHold") : Strings.get("objectivePayloadRestoreEscort") : "";
            if (extraction.lossProgress() > 0) blocker = Strings.format("objectivePayloadLossIn",
                    seconds(extraction.lossDuration() - extraction.lossProgress()));
            else if (extraction.abandonmentProgress() > 0) blocker = Strings.format("objectivePayloadAbandonIn",
                    seconds(extraction.abandonmentDuration() - extraction.abandonmentProgress()));
        } else if (p instanceof ColonyArchiveObjective colony) {
            instruction = Strings.format("objectivePayloadArchiveFor", seconds(colony.recoveryDuration()));
            measure = MissionObjectiveFamilies.timed(Strings.get("objectivePayloadRecoveryTimer"), colony.progress(), colony.recoveryDuration());
            if (!p.escortPresent()) blocker = Strings.get("objectivePayloadArchiveBlocker");
        } else if (cohort) {
            measure = Strings.format("objectivePayloadCohortTally", p.boardedElements(), p.activeElements(), p.lostElements());
            if (atSource) {
                instruction = evacuation.storyEscort() ? Strings.get("objectivePayloadSecureShelter")
                        : Strings.get("objectivePayloadReachShelter");
                progress = evacuation.storyEscort() ? evacuation.securedTicks() / 60f : 0;
                blocker = evacuation.shelterThreat() ? Strings.get("objectivePayloadShelterThreat")
                        : evacuation.securedTicks() == 0 ? Strings.get("objectivePayloadWaitingShelter") : "";
                if (evacuation.securedTicks() > 0) phase = Strings.get("objectivePayloadSecuringShelter");
            } else {
                instruction = Strings.get("objectivePayloadEscortSurvivors");
                List<String> holds = new ArrayList<>();
                if (evacuation.unescorted() > 0) holds.add(Strings.format("objectivePayloadNeedEscort", evacuation.unescorted()));
                if (evacuation.blocked() > 0) holds.add(Strings.format("objectivePayloadNoRoute", evacuation.blocked()));
                if (evacuation.awaitingPickup() > 0) holds.add(Strings.format("objectivePayloadAwaitingPickup", evacuation.awaitingPickup()));
                if (evacuation.screened() > 0) holds.add(Strings.format("objectivePayloadSheltering", evacuation.screened()));
                blocker = Strings.join("separatorList", holds.toArray(new String[0]));
            }
        }
        if (state == State.COMPLETE) {
            blocker = "";
            progress = 1;
            instruction = archive ? Strings.get("objectivePayloadArchiveDone")
                    : cohort ? Strings.get("objectivePayloadCohortDone") : Strings.get("objectivePayloadPackageDone");
            if (!cohort) measure = Strings.get("objectiveComplete");
        } else if (state == State.FAILED) {
            blocker = p instanceof CivilianEvacuationObjective civilian && civilian.pickupLost()
                    ? Strings.get("objectivePayloadPickupLost")
                    : p.failureReason() == ExtractionPayloadObjective.Failure.ABANDONED ? Strings.get("objectivePayloadAbandoned")
                    : cohort ? Strings.get("objectivePayloadNoSurvivors") : Strings.get("objectivePayloadLost");
            instruction = Strings.get("objectiveFailedSentence");
        }
        String id = p.payloadId();
        String sourceName = p.sourceName();
        List<Marker> markers = new ArrayList<>();
        markers.add(new Marker(id + "-source", sourceName, p.sourceMarkerX(), p.sourceMarkerY(),
                archive || atSource ? phase : Strings.get("objectiveMarkerSecured"), archive || atSource ? state : State.COMPLETE));
        String focus = id + "-source";
        if (!archive && p.egressCellX() >= 0 && p.egressCellY() >= 0) {
            markers.add(new Marker(id + "-pickup", p.egressName(), p.egressCellX(), p.egressCellY(),
                    state == State.ACTIVE ? Strings.get("objectiveMarkerPickup") : phase, state));
            if (!atSource) focus = id + "-pickup";
        }
        if (!archive && !atSource && state == State.ACTIVE && p.payloadCellX() >= 0 && p.payloadCellY() >= 0
                && Math.abs(p.payloadCellX() - p.egressCellX()) + Math.abs(p.payloadCellY() - p.egressCellY()) > 3) {
            markers.add(new Marker(id + "-payload", p.payloadName(), p.payloadCellX(), p.payloadCellY(),
                    blocker.isEmpty() ? phase : Strings.get("objectiveMarkerNeedsAttention"), state));
            focus = id + "-payload";
        }
        String steps = archive ? Strings.get("objectivePayloadArchiveSteps")
                : atSource ? Strings.get("objectivePayloadSteps")
                : state == State.COMPLETE ? Strings.get("objectivePayloadStepsDone")
                : Strings.get("objectivePayloadStepsEscort");
        return new Item(id, p.payloadName(), state, phase, steps, instruction, measure,
                Math.max(0, Math.min(1, progress)), blocker, markers, focus);
    }

    private static String seconds(float value) { return MissionObjectiveFamilies.seconds(value); }

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
            String task = directive.authority() == CommandAuthority.PLAYER_INTERVENTION ? Strings.get("objectiveTaskPlayerOrder")
                    : assignment == null ? Strings.get("objectiveTaskMissionCommand")
                    : Strings.named("objectiveAssignment", assignment.kind().name());
            rows.add(new SquadActivity(directive.squadId(), names.apply(directive.squadId()), task,
                    directive.authority() == CommandAuthority.PLAYER_INTERVENTION ? Strings.get("objectiveStatusPlayerCommand") : Strings.get("objectiveStatusAssigned")));
        }
        rows.sort(Comparator.comparingInt(SquadActivity::squadId));
        return List.copyOf(rows);
    }

    private static SquadActivity activity(int id, IntFunction<String> names, String role, boolean contact, boolean slowed) {
        String task = switch (role) {
            case "ARCHIVE_RECOVERY" -> Strings.get("objectiveTaskArchive");
            case "SURVIVOR_ESCORT", "COHORT_ESCORT" -> Strings.get("objectiveTaskEscortSurvivors");
            case "PAYLOAD_ELEMENT" -> Strings.get("objectiveTaskPayload");
            case "CLOSE_ESCORT" -> Strings.get("objectiveTaskCloseEscort");
            case "LEAD_SCREEN" -> Strings.get("objectiveTaskLeadScreen");
            case "LEFT_SCREEN" -> Strings.get("objectiveTaskLeftScreen");
            case "RIGHT_SCREEN" -> Strings.get("objectiveTaskRightScreen");
            case "REAR_SCREEN" -> Strings.get("objectiveTaskRearScreen");
            case "SHELTER_GUARD" -> Strings.get("objectiveTaskShelterGuard");
            case "PICKUP_GUARD" -> Strings.get("objectiveTaskPickupGuard");
            case "STRANDED" -> Strings.get("objectiveTaskStranded");
            case "RELEASED" -> Strings.get("objectiveTaskReleased");
            default -> Strings.get("objectiveTaskSeparate");
        };
        return new SquadActivity(id, names.apply(id), task,
                slowed ? Strings.get("objectiveStatusSlowed") : contact ? Strings.get("objectiveStatusContact") : "STRANDED".equals(role) ? Strings.get("objectiveStatusRouteUnavailable") : Strings.get("objectiveStatusAssigned"));
    }
}
