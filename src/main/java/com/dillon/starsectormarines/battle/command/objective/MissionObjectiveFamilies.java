package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Item;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Marker;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.State;
import com.dillon.starsectormarines.battle.setup.ColdAccountIntervention;
import com.dillon.starsectormarines.battle.setup.ColdStartSequence;
import com.dillon.starsectormarines.battle.setup.WaterUnderSealSequence;
import com.dillon.starsectormarines.battle.story.WaterUnderSealProgress;
import com.dillon.starsectormarines.battle.world.gen.NorthWeir;
import com.dillon.starsectormarines.battle.setup.WorkingPassageSequence;
import com.dillon.starsectormarines.battle.story.WorkingPassageProgress;
import com.dillon.starsectormarines.battle.setup.DepotHoldSequence;
import com.dillon.starsectormarines.battle.setup.SalvageRightsSequence;
import com.dillon.starsectormarines.battle.setup.ThunderRunSequence;
import com.dillon.starsectormarines.battle.story.ColdStartProgress;
import com.dillon.starsectormarines.battle.story.ColdStartPicket;
import com.dillon.starsectormarines.battle.story.DepotHoldProgress;
import com.dillon.starsectormarines.battle.story.SalvageRightsProgress;
import java.util.List;

/** Read-only adapters. Negative progress means the current obligation has no measured clock. */
public final class MissionObjectiveFamilies {
    private MissionObjectiveFamilies() { }

    public static State state(Objective objective) {
        return objective.isFailed() ? State.FAILED : objective.isComplete() ? State.COMPLETE : State.ACTIVE;
    }

    public static Item at(String id, String name, State state, String phase, String steps,
                          String instruction, String measure, float progress, String blocker, int x, int y) {
        String markerId = id + "-site";
        return new Item(id, name, state, phase, steps, instruction, measure,
                state == State.COMPLETE ? 1 : progress < 0 ? -1 : Math.max(0, Math.min(1, progress)),
                state == State.COMPLETE ? "" : state == State.FAILED ? "Objective failed." : blocker, List.of(new Marker(markerId, name, x, y, phase, state)), markerId);
    }

    public static Item charge(ChargeSiteObjective site) {
        State state = state(site);
        return at("charge-" + site.siteId(), site.displayName(), state,
                state == State.COMPLETE ? "PLANTED" : site.planterOnSite() ? "PLANTING" : "REACH SITE",
                "Reach site > Plant charge", state == State.COMPLETE ? "Charge planted."
                        : "Keep the equipped planter at this site until the charge is planted.",
                timed("Planting", site.progress(), site.plantDuration()), site.progress() / site.plantDuration(),
                site.planterOnSite() ? "" : "Waiting for the equipped planter at the site.", site.cellX(), site.cellY());
    }

    public static Item raid(RaidObjective raid) {
        State state = state(raid);
        boolean returning = raid.targetSecured();
        String phase = raid.phase().name();
        String sourceId = "raid-" + raid.targetId() + "-source", pickupId = "raid-" + raid.targetId() + "-pickup";
        return new Item("raid-" + raid.targetId(), raid.targetName(), state, phase,
                "1 Seize target > 2 Return to landing zone",
                state == State.COMPLETE ? "Target secured and survivor returned."
                        : returning ? "Return a surviving marine to the landing zone and stop inside the pickup area."
                        : "Keep a settled marine in the target room and service area, uncontested.",
                returning ? state == State.COMPLETE ? "Objective complete" : "Awaiting survivor return"
                        : timed("Seizure", raid.serviceProgress(), raid.serviceDuration()),
                state == State.COMPLETE ? 1 : returning ? -1 : raid.serviceProgress() / raid.serviceDuration(),
                returning || raid.marineServicing() ? "" : "The target needs settled Marine presence and uncontested control.",
                List.of(new Marker(sourceId, raid.targetName(), raid.targetCellX(), raid.targetCellY(),
                                returning ? "SECURED" : phase, returning ? State.COMPLETE : state, List.of(), RaidObjective.TARGET_RADIUS),
                        new Marker(pickupId, "Landing zone", raid.egressCellX(), raid.egressCellY(),
                                returning ? phase : "RETURN HERE", state, List.of(), RaidObjective.EGRESS_RADIUS)), returning ? pickupId : sourceId);
    }

    public static Item salvage(SalvageRightsSequence sequence) {
        var p = sequence.progress();
        boolean inspecting = p.phase() == SalvageRightsProgress.Phase.INSPECTION;
        float duration = inspecting ? SalvageRightsProgress.INSPECTION_SECONDS : SalvageRightsProgress.CAPTURE_SECONDS;
        float work = inspecting ? p.inspection() : p.capture();
        String name = switch (p.phase()) {
            case RECEIVING -> "Receiving yard";
            case WORKSHOP -> "Workshop";
            case CRADLE, INSPECTION, SECURED, FAILED -> "Morrow's cradle";
        };
        String blocker = p.contested() ? "Area contested: clear the working area."
                : !p.companyAtTarget() ? "Company cover must enter and remain in the marked area."
                : inspecting && !p.engineerPresent() ? "Waiting for a living technician at the inspection site."
                : inspecting && work >= duration && !p.responseCommitted() ? "Inspection complete; hold the claim through the announced road response." : "";
        return at("salvage-rights", name, state(sequence), p.phase().name(),
                "Receiving yard > Workshop > Cradle > Inspection",
                sequence.isComplete() ? "Inspection complete; Morrow's recovery claim is secured."
                        : sequence.isFailed() ? "Recovery operation lost."
                        : inspecting ? "Protect company cover and a living technician through the inspection."
                        : "Hold the marked working area uncontested for " + seconds(duration) + ".",
                sequence.isComplete() ? "Recovery claim secured" : sequence.isFailed() ? "Recovery failed"
                        : timed(inspecting ? "Inspection" : "Capture", work, duration), work / duration, blocker,
                sequence.targetX(), sequence.targetY());
    }

    public static Item workingPassage(WorkingPassageSequence sequence) {
        var p=sequence.progress();
        boolean build=p.phase()==WorkingPassageProgress.Phase.BEND_BUILD || p.phase()==WorkingPassageProgress.Phase.NARROWS_BUILD;
        boolean clearing=p.phase()==WorkingPassageProgress.Phase.BEND || p.phase()==WorkingPassageProgress.Phase.NARROWS
                || p.phase()==WorkingPassageProgress.Phase.CLIMB_OUT;
        var order=sequence.tender().current();
        boolean building=build && order!=null
                && Math.hypot(order.siteX()-sequence.buildX(),order.siteY()-sequence.buildY())<22;
        String blocker=p.terminal()?"":p.contested()?"Company position contested; tender travel and work are paused."
                :!p.companyPresent()?"Company cover must reach and remain near the forward position."
                :!p.tenderArrived()?"Morrow must physically reach the cleared assembly position."
                :building&&order.underFire()?"Builders under fire; clear the firing position before work resumes."
                :building&&order.contested()?"Clear known hostiles within 30 cells of the construction site."
                :building?"Builders are servicing the selected emplacement."
                :build&&!p.constructionReady()?"Select Build Vulcan Turret and complete a surviving gun near this position."
                :!p.responseReached()?"Hold through the announced finite road response.":"";
        return at("working-passage",switch(p.phase()) {
            case BEND,BEND_BUILD->"Pump bend build ground";
            case NARROWS,NARROWS_BUILD->"Narrows build ground";
            default->"Climb-out yard";
        },state(sequence),p.phase().name(),"Pump bend > Narrows > Climb-out > Physical departure",
                sequence.displayName(),p.phase()==WorkingPassageProgress.Phase.SPOOL?timed("Drive spool",p.work(),WorkingPassageProgress.SPOOL_SECONDS)
                        :clearing?timed("Area secure",p.work(),WorkingPassageProgress.CLEAR_SECONDS)
                        :building?"Construction / "+Math.round(100*order.fraction())+"%"
                        :build?p.constructionReady()?"Emplacement ready / hold through the road response":"Awaiting actual surviving construction":sequence.displayName(),
                p.phase()==WorkingPassageProgress.Phase.SPOOL?p.work()/WorkingPassageProgress.SPOOL_SECONDS
                        :clearing?p.work()/WorkingPassageProgress.CLEAR_SECONDS:building?order.fraction():-1,
                blocker,sequence.targetX(),sequence.targetY());
    }

    public static Item waterUnderSeal(WaterUnderSealSequence sequence) {
        var p = sequence.progress(); var phase = p.phase(); var milestones = p.report(true, 0, 0);
        var state = state(sequence);
        boolean build = phase == WaterUnderSealProgress.Phase.CONSTRUCTION;
        var order = sequence.tender() == null ? null : sequence.tender().current();
        boolean building = build && order != null && Math.hypot(order.siteX() - sequence.buildX(), order.siteY() - sequence.buildY()) < 12;
        float duration = phase == WaterUnderSealProgress.Phase.ESCORT ? WaterUnderSealProgress.UNLOAD_SECONDS
                : phase == WaterUnderSealProgress.Phase.NORTH_ROOM || phase == WaterUnderSealProgress.Phase.SOUTH_ROOM
                ? WaterUnderSealProgress.RECONNECT_SECONDS : 0;
        String focus = switch (phase) {
            case ESCORT -> "weir-escort"; case NORTH_ROOM -> "weir-north"; case SOUTH_ROOM -> "weir-south"; default -> "weir-road-gun";
        };
        String blocker = p.terminal() ? "" : build && !sequence.tenderAvailable() ? p.positionLost() ? "Call another already committed tender source, or withdraw for a new mobilization." : "Call the selected owned tender using Ship on Station."
                : build && !building ? p.positionLost() ? "Replace the destroyed road gun: select the surviving tender and place another Vulcan turret at the marked position." : "Select the tender, choose Build Vulcan Turret, and place it in the marked road position."
                : building && order.contested() ? "Clear known hostiles near the construction site and protect the builders."
                : phase == WaterUnderSealProgress.Phase.ESCORT && !p.carrierArrived() ? "The repair load must physically reach the isolation switch."
                : !p.safeWork() ? duration > 0 ? "Keep company troops and living technicians in the marked work area, uncontested."
                : "Keep company troops at the road position, clear hostile pressure, and protect the repair watch in the secured gallery."
                : phase == WaterUnderSealProgress.Phase.ATTACK ? "Defend the road gun until Edek confirms handover." : "";
        return new Item("water-under-seal", "North Weir", state, phase.name(), "Repair load > North switch > South switch > Road gun > Handover",
                sequence.displayName(), duration > 0 ? timed("Protected work", p.work(), duration)
                        : building ? "Construction / " + Math.round(100 * order.fraction()) + "%"
                        : phase == WaterUnderSealProgress.Phase.ATTACK ? "Hold the road position" : sequence.displayName(),
                duration > 0 ? p.work() / duration : building ? order.fraction() : -1, blocker,
                List.of(new Marker("weir-escort", "Repair-load escort rally", sequence.escortRallyX(), sequence.escortRallyY(), "ESCORT", state),
                        new Marker("weir-isolation", "Isolation switch / repair load", NorthWeir.ISOLATION_X, NorthWeir.ISOLATION_Y,
                                milestones.cargoDelivered() ? "DELIVERED" : "UNLOAD HERE", milestones.cargoDelivered() ? State.COMPLETE : state),
                        new Marker("weir-north", "North switching room", NorthWeir.NORTH_X, NorthWeir.NORTH_Y,
                                milestones.northRoomReconnected() ? "RECONNECTED" : "PROTECTED WORK", milestones.northRoomReconnected() ? State.COMPLETE : state),
                        new Marker("weir-south", "South switching room", NorthWeir.SOUTH_X, NorthWeir.SOUTH_Y,
                                milestones.southRoomReconnected() ? "RECONNECTED" : "PROTECTED WORK", milestones.southRoomReconnected() ? State.COMPLETE : state),
                        new Marker("weir-road-gun", "Maintenance road gun / build here", sequence.buildX(), sequence.buildY(),
                                milestones.turretConstructed() ? "COMMISSIONED" : "BUILD HERE", milestones.turretConstructed() ? State.COMPLETE : state,
                                List.of(), 8)).stream().filter(marker -> !milestones.cargoDelivered() || !marker.id().equals("weir-escort")).toList(), focus);
    }
    public static Item coldStart(ColdStartSequence sequence) {
        var p = sequence.progress();
        float duration = switch (p.phase()) {
            case ESCORT -> ColdStartProgress.UNLOAD_SECONDS;
            case POWER -> ColdStartProgress.POWER_SECONDS;
            case REPAIR -> ColdStartProgress.REPAIR_SECONDS;
            default -> 0;
        };
        String name = switch (p.phase()) {
            case ESCORT -> "Repair-carrier escort rally";
            case POWER -> "Shore-power pump";
            default -> "Morrow's work site";
        };
        String blocker = p.phase() == ColdStartProgress.Phase.CONSTRUCTION ? ""
                : p.contested() ? "Area contested: work is paused."
                : p.phase() == ColdStartProgress.Phase.ESCORT && !p.carrierArrived() ? "Repair carrier must reach the workshop unloading point."
                : !p.companyPresent() ? "Company cover must reach and remain near the work site."
                : !p.crewPresent() ? "Waiting for a living technician at the work site."
                : p.phase() == ColdStartProgress.Phase.REPAIR && p.work() >= duration && !p.responseReachedYard()
                ? "Repairs complete; hold the work site through the announced road response." : "";
        return at("cold-start", name, state(sequence), p.phase().name(),
                "Cargo > Shore power > Road gun > Repairs", switch (p.phase()) {
                    case ESCORT -> "Escort the carrier to the workshop; protect company cover and technicians through unloading.";
                    case POWER -> "Keep company cover and living technicians near the shore-power pump, uncontested.";
                    case CONSTRUCTION -> "Select Build Vulcan Turret and place it on clear exterior ground by Morrow's east bay.";
                    case REPAIR -> "Keep company cover and living technicians near Morrow's work site, uncontested.";
                    default -> sequence.displayName();
                },
                duration > 0 ? timed("Work", p.work(), duration) : p.phase() == ColdStartProgress.Phase.CONSTRUCTION
                        ? "Awaiting completed Vulcan turret" : sequence.displayName(),
                duration > 0 ? p.work() / duration : -1, blocker, sequence.targetX(), sequence.targetY());
    }

    public static Item coldStartPicket(ColdStartPicket.Report post) {
        String name = post.key().equals("south_haul") ? "South haul picket" : "Quarry picket";
        String status = switch (post.state()) {
            case HOLDING -> post.contacted() ? "CONTACT REPORTED" : "OUTER WATCH";
            case WITHDRAWING -> "FALLING BACK";
            case INNER_HOLD -> "INNER LINE";
            case LOST -> "POST LOST";
        };
        // Strength is a report, not a capture meter or an additional victory requirement.
        return at("cold-start-picket-" + post.key(), name,
                post.aliveMembers() == 0 ? State.FAILED : State.ACTIVE, status,
                "Optional support / outer watch > inner defense",
                switch (post.state()) {
                    case HOLDING -> "Optional support: reinforce this outer watch.";
                    case WITHDRAWING -> "Cover the survivors' withdrawal to this position.";
                    case INNER_HOLD -> "Survivors hold this repair-yard approach.";
                    case LOST -> "Detachment lost. Protect the inner line.";
                },
                post.aliveMembers() + "/" + post.initialMembers() + " militia alive"
                        + (post.overrun() ? " / outer post overrun" : ""), -1, "",
                post.withdrawing() ? post.innerX() : post.outerX(),
                post.withdrawing() ? post.innerY() : post.outerY());
    }

    public static Item depot(DepotHoldSequence sequence) {
        var p = sequence.progress();
        String measure = sequence.isComplete() ? "Dispatch secured" : !p.committed() ? timed("Capture", p.captureFraction() * DepotHoldProgress.CAPTURE_SECONDS, DepotHoldProgress.CAPTURE_SECONDS)
                : p.waves() < 2 ? "Next announced road attack / " + seconds(p.nextWaveSeconds())
                : "Both road attacks committed; secure dispatch";
        return at("depot-dispatch", "Dispatch warehouse", state(sequence),
                sequence.isComplete() ? "SECURED" : p.committed() ? "DEFEND DISPATCH" : "CAPTURE DISPATCH",
                "Capture dispatch > Hold against both road attacks",
                sequence.isComplete() ? "Dispatch secured; both road attacks defeated."
                        : p.committed() ? "Keep friendly forces inside dispatch and defeat both road attacks."
                        : "Keep company forces inside the warehouse, uncontested, for 8s.",
                measure, !p.committed() ? p.captureFraction() : -1,
                p.enemyInside() ? "Dispatch contested: clear the warehouse."
                        : !(p.committed() ? p.friendlyInside() : p.companyInside())
                        ? "Keep friendly forces inside the warehouse." : "",
                sequence.x(), sequence.y());
    }

    public static Item compound(CompoundService.Record record, int index, String name) {
        boolean held = record.state == CompoundService.CompoundState.MARINE_HELD;
        return at("territory-" + index, name, held ? State.COMPLETE : State.ACTIVE,
                held ? "HELD" : record.defendersPresent ? "CONTESTED" : record.attackersPresent ? "CAPTURING" : "REACH ROOM",
                "Enter capture room > Clear > Hold", held ? "Position held; protect it from recapture."
                        : "Keep friendly forces inside the capture room and hold uncontested for 4s.",
                held ? "Marine-held" : "Control transition / " + Math.round(record.captureProgress * 100) + "%",
                held ? 1 : record.captureProgress,
                held ? "" : record.defendersPresent ? "Area contested: clear the capture room."
                        : !record.attackersPresent ? "Friendly forces must enter and remain in the capture room." : "",
                record.captureCellX >= 0 ? record.captureCellX : record.node.anchorX,
                record.captureCellY >= 0 ? record.captureCellY : record.node.anchorY);
    }

    public static Item freight(Objective objective, ThunderRunSequence column) {
        return new Item("freight-convoy", "Employer freight", state(objective), column.resolved() ? "RESOLVED" : "ESCORT CONVOY",
                "Clear road posts > Hold dispatch windows > Escort freight out",
                "Secure road posts and protect physical cargo delivery; capturing the road alone does not finish the job.",
                column.deliveredCount() + " delivered / " + column.lostCount() + " lost / "
                        + (column.cargoIdentities().size() - column.deliveredCount() - column.lostCount()) + " en route",
                -1, column.holdSite() > 0 ? "Hold the dispatch post / " + seconds(column.holdRemainingTicks() / 30f) + " remaining" : "",
                List.of(), "");
    }

    public static Item coldAccount(ColdAccountIntervention intervention, RaidObjective records) {
        var layout = intervention.layout();
        boolean evidence = intervention.phase() == ColdAccountIntervention.Phase.EVIDENCE;
        String blocker = !evidence && !intervention.companyRecovered()
                ? intervention.outsideWithdrawal() > 0 ? "Company personnel must reach the shuttle pickup."
                : intervention.pendingArrivals() ? "Waiting for committed company arrivals before extraction." : "Awaiting company extraction." : "";
        return at("cold-account", evidence ? "Dispatch office" : "Shuttle pickup", state(intervention),
                intervention.phase().name(), "Recover evidence > Withdraw > Naval intervention",
                intervention.isComplete() || intervention.isFailed() ? intervention.displayName()
                        : evidence ? "Secure the dispatch office." : intervention.companyRecovered()
                        ? "Company recovered; naval intervention is continuing." : "Withdraw to the shuttle pickup; keep clear of the announced bombardment area.",
                evidence ? records == null ? "Awaiting evidence recovery"
                        : timed("Evidence recovery", records.serviceProgress(), records.serviceDuration())
                        : intervention.companyRecovered() ? "Company recovered" : "Awaiting extraction",
                evidence && records != null ? records.serviceProgress() / records.serviceDuration() : -1,
                evidence && records != null && !records.marineServicing()
                        ? "The dispatch office needs settled Marine presence and uncontested control." : blocker,
                evidence ? layout.evidenceX() : layout.withdrawalX(), evidence ? layout.evidenceY() : layout.withdrawalY());
    }

    public static Item generic(Objective objective, int index) {
        State state = state(objective);
        return new Item("mission-" + index, objective.displayName(), state, state.name(), "", objective.displayName(),
                state == State.COMPLETE ? "Objective complete" : state == State.FAILED ? "Objective failed" : "Awaiting mission condition",
                -1, "", List.of(), "");
    }
    static String seconds(float value) { return Math.max(0, Math.round(value * 10)) / 10f + "s"; }
    static String timed(String label, float work, float duration) { return label + " / " + seconds(work) + " of " + seconds(duration); }
}
