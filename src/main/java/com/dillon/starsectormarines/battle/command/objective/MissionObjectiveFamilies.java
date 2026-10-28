package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Item;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Marker;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.State;
import com.dillon.starsectormarines.battle.setup.ColdAccountIntervention;
import com.dillon.starsectormarines.battle.setup.ColdStartSequence;
import com.dillon.starsectormarines.battle.setup.WaterUnderSealSequence;
import com.dillon.starsectormarines.battle.setup.LimitedWarrantSequence;
import com.dillon.starsectormarines.battle.setup.DistrictEscortSequence;
import com.dillon.starsectormarines.battle.story.DistrictEscortProgress;
import com.dillon.starsectormarines.battle.story.LimitedWarrantProgress;
import com.dillon.starsectormarines.battle.world.gen.LowQuay;
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
import java.util.ArrayList;
import com.dillon.starsectormarines.battle.setup.YardOwnersSequence;
import com.dillon.starsectormarines.battle.setup.TermsOfProtectionSequence;
import com.dillon.starsectormarines.battle.story.TermsOfProtectionProgress;
import com.dillon.starsectormarines.battle.story.YardOwnersProgress;
import com.dillon.starsectormarines.battle.world.gen.LatchkeyYard;
import com.dillon.starsectormarines.battle.setup.SmallWarSequence;
import com.dillon.starsectormarines.battle.story.SmallWarProgress;
import com.dillon.starsectormarines.battle.world.gen.BlackSluice;

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
                state == State.COMPLETE ? "" : state == State.FAILED ? Strings.get("objectiveFailedSentence") : blocker, List.of(new Marker(markerId, name, x, y, phase, state)), markerId);
    }

    /**
     * The item under its briefing letter, (A) for {@code ordinal} 0: the letter the mission's briefing text uses,
     * on the HUD row and its map markers alike, so the words and the map name the same place.
     */
    public static Item lettered(Item item, int ordinal) {
        String letter = String.valueOf((char) ('A' + ordinal));
        return new Item(item.id(), Strings.format("objectiveLettered", letter, item.name()), item.state(), item.phase(),
                item.steps(), item.instruction(), item.progressText(), item.progress(), item.blocker(),
                item.markers().stream().map(m -> new Marker(m.id(), Strings.format("objectiveLettered", letter, m.name()),
                        m.x(), m.y(), m.status(), m.state(), m.footprint(), m.radius())).toList(),
                item.focusMarkerId(), item.optional());
    }

    public static Item charge(ChargeSiteObjective site) {
        State state = state(site);
        return at("charge-" + site.siteId(), site.displayName(), state,
                state == State.COMPLETE ? Strings.get("objectiveChargePlanted") : site.planterOnSite() ? Strings.get("objectiveChargePlanting") : Strings.get("objectiveChargeReachSite"),
                Strings.get("objectiveChargeSteps"), state == State.COMPLETE ? Strings.get("objectiveChargeDone")
                        : Strings.get("objectiveChargeInstruction"),
                timed(Strings.get("objectiveChargeTimer"), site.progress(), site.plantDuration()), site.progress() / site.plantDuration(),
                site.planterOnSite() ? "" : Strings.get("objectiveChargeBlocker"), site.cellX(), site.cellY());
    }

    public static Item raid(RaidObjective raid) {
        State state = state(raid);
        boolean returning = raid.targetSecured();
        String phase = Strings.named("objectivePhase", raid.phase().name());
        String sourceId = "raid-" + raid.targetId() + "-source", pickupId = "raid-" + raid.targetId() + "-pickup";
        return new Item("raid-" + raid.targetId(), raid.targetName(), state, phase,
                Strings.get("objectiveRaidSteps"),
                state == State.COMPLETE ? Strings.get("objectiveRaidDone")
                        : returning ? Strings.get("objectiveRaidReturn")
                        : Strings.get("objectiveRaidSeize"),
                returning ? state == State.COMPLETE ? Strings.get("objectiveComplete") : Strings.get("objectiveRaidAwaitingReturn")
                        : timed(Strings.get("objectiveRaidTimer"), raid.serviceProgress(), raid.serviceDuration()),
                state == State.COMPLETE ? 1 : returning ? -1 : raid.serviceProgress() / raid.serviceDuration(),
                returning || raid.marineServicing() ? "" : Strings.get("objectiveRaidBlocker"),
                List.of(new Marker(sourceId, raid.targetName(), raid.targetCellX(), raid.targetCellY(),
                                returning ? Strings.get("objectiveMarkerSecured") : phase, returning ? State.COMPLETE : state, List.of(), RaidObjective.TARGET_RADIUS),
                        new Marker(pickupId, Strings.get("objectiveRaidLandingZone"), raid.egressCellX(), raid.egressCellY(),
                                returning ? phase : Strings.get("objectiveMarkerReturnHere"), state, List.of(), RaidObjective.EGRESS_RADIUS)), returning ? pickupId : sourceId);
    }

    public static Item salvage(SalvageRightsSequence sequence) {
        var p = sequence.progress();
        boolean inspecting = p.phase() == SalvageRightsProgress.Phase.INSPECTION;
        float duration = inspecting ? SalvageRightsProgress.INSPECTION_SECONDS : SalvageRightsProgress.CAPTURE_SECONDS;
        float work = inspecting ? p.inspection() : p.capture();
        String name = switch (p.phase()) {
            case RECEIVING -> Strings.get("objectiveSalvageReceivingYard");
            case WORKSHOP -> Strings.get("objectiveSalvageWorkshop");
            case CRADLE, INSPECTION, SECURED, FAILED -> Strings.get("objectiveSalvageCradle");
        };
        String blocker = p.contested() ? Strings.get("objectiveSalvageContested")
                : !p.companyAtTarget() ? Strings.get("objectiveSalvageCompanyCover")
                : inspecting && !p.engineerPresent() ? Strings.get("objectiveSalvageTechnician")
                : inspecting && work >= duration && !p.responseCommitted() ? Strings.get("objectiveSalvageHoldClaim") : "";
        return at("salvage-rights", name, state(sequence), Strings.named("objectivePhase", p.phase().name()),
                Strings.get("objectiveSalvageSteps"),
                sequence.isComplete() ? Strings.get("objectiveSalvageDone")
                        : sequence.isFailed() ? Strings.get("objectiveSalvageLost")
                        : inspecting ? Strings.get("objectiveSalvageProtect")
                        : Strings.format("objectiveSalvageHoldFor", seconds(duration)),
                sequence.isComplete() ? Strings.get("objectiveSalvageSecured") : sequence.isFailed() ? Strings.get("objectiveSalvageFailed")
                        : timed(inspecting ? Strings.get("objectiveSalvageInspectionTimer") : Strings.get("objectiveCaptureTimer"), work, duration), work / duration, blocker,
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
        String blocker=p.terminal()?"":p.contested()?Strings.get("objectivePassageContested")
                :!p.companyPresent()?Strings.get("objectivePassageCompanyCover")
                :!p.tenderArrived()?Strings.get("objectivePassageTender")
                :building&&order.underFire()?Strings.get("objectivePassageUnderFire")
                :building&&order.contested()?Strings.get("objectivePassageClearSite")
                :building?Strings.get("objectivePassageBuilding")
                :build&&!p.constructionReady()?Strings.get("objectivePassageBuildGun")
                :!p.responseReached()?Strings.get("objectivePassageHold"):"";
        return at("working-passage",switch(p.phase()) {
            case BEND,BEND_BUILD->Strings.get("objectivePassageBend");
            case NARROWS,NARROWS_BUILD->Strings.get("objectivePassageNarrows");
            default->Strings.get("objectivePassageClimbOut");
        },state(sequence),Strings.named("objectivePhase", p.phase().name()),Strings.get("objectivePassageSteps"),
                sequence.displayName(),p.phase()==WorkingPassageProgress.Phase.SPOOL?timed(Strings.get("objectivePassageSpoolTimer"),p.work(),WorkingPassageProgress.SPOOL_SECONDS)
                        :clearing?timed(Strings.get("objectivePassageSecureTimer"),p.work(),WorkingPassageProgress.CLEAR_SECONDS)
                        :building?Strings.format("objectiveConstructionPercent", Math.round(100*order.fraction()))
                        :build?p.constructionReady()?Strings.get("objectivePassageReady"):Strings.get("objectivePassageAwaitingBuild"):sequence.displayName(),
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
        String blocker = p.terminal() ? "" : build && !sequence.tenderAvailable() ? Strings.get(p.positionLost() ? "weirBlockerTenderLost" : "weirBlockerCallTender")
                : build && !building ? Strings.get(p.positionLost() ? "weirBlockerGunLost" : "weirBlockerBuildGun")
                : building && order.contested() ? Strings.get("weirBlockerClearSite")
                : phase == WaterUnderSealProgress.Phase.ESCORT && !p.carrierArrived() ? Strings.get("weirBlockerRepairLoad")
                : !p.safeWork() ? Strings.get(duration > 0 ? "weirBlockerKeepWorkArea" : "weirBlockerKeepRoadPosition")
                : phase == WaterUnderSealProgress.Phase.ATTACK ? Strings.get("weirBlockerDefendGun") : "";
        return new Item("water-under-seal", Strings.get("objectiveWeirName"), state, Strings.named("objectivePhase", phase.name()), Strings.get("objectiveWeirSteps"),
                sequence.displayName(), duration > 0 ? timed(Strings.get("objectiveProtectedWorkTimer"), p.work(), duration)
                        : building ? Strings.format("objectiveConstructionPercent", Math.round(100 * order.fraction()))
                        : phase == WaterUnderSealProgress.Phase.ATTACK ? Strings.get("objectiveWeirHoldRoad") : sequence.displayName(),
                duration > 0 ? p.work() / duration : building ? order.fraction() : -1, blocker,
                List.of(new Marker("weir-escort", Strings.get("objectiveWeirEscortRally"), sequence.escortRallyX(), sequence.escortRallyY(), Strings.get("objectiveMarkerEscort"), state),
                        new Marker("weir-isolation", Strings.get("objectiveWeirIsolation"), NorthWeir.ISOLATION_X, NorthWeir.ISOLATION_Y,
                                milestones.cargoDelivered() ? Strings.get("objectiveMarkerDelivered") : Strings.get("objectiveMarkerUnloadHere"), milestones.cargoDelivered() ? State.COMPLETE : state),
                        new Marker("weir-north", Strings.get("objectiveWeirNorthRoom"), NorthWeir.NORTH_X, NorthWeir.NORTH_Y,
                                milestones.northRoomReconnected() ? Strings.get("objectiveMarkerReconnected") : Strings.get("objectiveMarkerProtectedWork"), milestones.northRoomReconnected() ? State.COMPLETE : state),
                        new Marker("weir-south", Strings.get("objectiveWeirSouthRoom"), NorthWeir.SOUTH_X, NorthWeir.SOUTH_Y,
                                milestones.southRoomReconnected() ? Strings.get("objectiveMarkerReconnected") : Strings.get("objectiveMarkerProtectedWork"), milestones.southRoomReconnected() ? State.COMPLETE : state),
                        new Marker("weir-road-gun", Strings.get("objectiveWeirRoadGun"), sequence.buildX(), sequence.buildY(),
                                milestones.turretConstructed() ? Strings.get("objectiveMarkerCommissioned") : Strings.get("objectiveMarkerBuildHere"), milestones.turretConstructed() ? State.COMPLETE : state,
                                List.of(), 8)).stream().filter(marker -> !milestones.cargoDelivered() || !marker.id().equals("weir-escort")).toList(), focus);
    }
    public static Item yardOwners(YardOwnersSequence sequence) {
        var p = sequence.progress(); var state = state(sequence);
        var markers = new ArrayList<Marker>();
        for (int i = 0; i < 3; i++) {
            var site = LatchkeyYard.SITES.get(i);
            markers.add(new Marker("yard-site-"+i,site.name(),site.x(),site.y(),p.held(i) ? Strings.get("objectiveMarkerHeldKeepClear") : Strings.get("objectiveMarkerSecureCompany"),p.held(i) ? State.COMPLETE : state));
        }
        markers.add(new Marker("yard-tools",Strings.get("objectiveYardTools"),LatchkeyYard.TOOL_X,LatchkeyYard.TOOL_Y,Strings.get("objectiveMarkerRequiredTooling"),state));
        // Lenna's machine labels: whose work the benches do. Information only; nothing to capture or protect.
        for (var bench : LatchkeyYard.WORKPLACES)
            markers.add(new Marker("yard-"+bench.id(),bench.label(),bench.x(),bench.y(),Strings.get("objectiveMarkerAssociationLabel"),state));
        boolean inspecting = p.phase() == YardOwnersProgress.Phase.INSPECTION;
        int site = Math.min(2,p.phase().ordinal());
        return new Item("yard-owners",Strings.get("objectiveYardName"),state,Strings.named("objectivePhase", p.phase().name()),Strings.get("objectiveYardSteps"),sequence.displayName(),
                inspecting ? timed(Strings.get("objectiveYardInspectionTimer"),p.inspection(),YardOwnersProgress.INSPECTION_SECONDS)
                        : timed(Strings.get("objectiveSecureSiteTimer"),p.capture(site),YardOwnersProgress.CAPTURE_SECONDS),
                p.terminal() ? -1 : inspecting ? p.inspection()/YardOwnersProgress.INSPECTION_SECONDS : p.capture(site)/YardOwnersProgress.CAPTURE_SECONDS,
                p.terminal() ? "" : Strings.get("objectiveYardBlocker"),
                markers, inspecting || p.terminal() ? "yard-tools" : "yard-site-"+site);
    }
    public static Item smallWar(SmallWarSequence sequence) {
        var p = sequence.progress(); var state = state(sequence); var phase = p.phase();
        var markers = new ArrayList<Marker>();
        for (int i = 0; i < 3; i++) {
            var site = BlackSluice.SITES.get(i);
            markers.add(new Marker("sluice-site-" + i, site.name(), site.x(), site.y(),
                    p.held(i) ? Strings.get("objectiveMarkerHeldKeepClear") : Strings.get("objectiveMarkerSecureCompany"), p.held(i) ? State.COMPLETE : state));
        }
        for (int i = 0; i < BlackSluice.TANKS.size(); i++)
            markers.add(new Marker("sluice-tank-" + i, Strings.get("objectiveSluiceTank"), BlackSluice.TANKS.get(i)[0], BlackSluice.TANKS.get(i)[1], Strings.get("objectiveMarkerRequiredFuel"), state));
        boolean handover = phase == SmallWarProgress.Phase.HANDOVER;
        int site = switch (phase) { case FUEL_CONTROL -> 1; case DISPATCH -> 2; default -> 0; };
        String focus = handover || p.terminal() ? "sluice-site-1" : "sluice-site-" + site;
        String blocker = p.terminal() ? ""
                : handover && !p.safeHandover() ? Strings.get("objectiveSluiceHandoverBlocker")
                : handover ? "" : Strings.get("objectiveSluiceSiteBlocker");
        return new Item("small-war", Strings.get("objectiveSluiceName"), state, Strings.named("objectivePhase", phase.name()), Strings.get("objectiveSluiceSteps"),
                sequence.displayName(), handover ? timed(Strings.get("objectiveSluiceHandoverTimer"), p.handover(), SmallWarProgress.HANDOVER_SECONDS)
                        : timed(Strings.get("objectiveSecureSiteTimer"), p.capture(site), SmallWarProgress.CAPTURE_SECONDS),
                p.terminal() ? -1 : handover ? p.handover() / SmallWarProgress.HANDOVER_SECONDS
                        : p.capture(site) / SmallWarProgress.CAPTURE_SECONDS,
                blocker, markers, focus);
    }
    public static Item termsOfProtection(TermsOfProtectionSequence sequence) {
        var p = sequence.progress(); var state = state(sequence);
        var phase = p.phase();
        boolean custody = sequence.route() == TermsOfProtectionProgress.Route.HOUSE_CUSTODY;
        var markers = new ArrayList<Marker>();
        if (phase == TermsOfProtectionProgress.Phase.ESCORT)
            markers.add(new Marker("terms-escort", custody ? Strings.get("objectiveTermsCustodyCarrier") : Strings.get("objectiveTermsJointCarrier"),
                    sequence.escortRallyX(), sequence.escortRallyY(), Strings.get("objectiveMarkerEscort"), state));
        markers.add(new Marker("terms-handover", LatchkeyYard.HANDOVER.label(), LatchkeyYard.HANDOVER.x(), LatchkeyYard.HANDOVER.y(),
                p.signed() ? p.ratified() ? Strings.get("objectiveMarkerRatified") : Strings.get("objectiveMarkerSignedRatify") : p.delivered() ? Strings.get("objectiveMarkerSigningHold") : Strings.get("objectiveMarkerDeliverParty"),
                p.ratified() ? State.COMPLETE : state));
        markers.add(new Marker("terms-commissioning", LatchkeyYard.COMMISSIONING.label(), LatchkeyYard.COMMISSIONING.x(), LatchkeyYard.COMMISSIONING.y(),
                p.commissioned() ? Strings.get("objectiveMarkerCommissioned") : Strings.get("objectiveMarkerProtectedHall"), p.commissioned() ? State.COMPLETE : state));
        if (custody) markers.add(new Marker("terms-guards", LatchkeyYard.GUARD_POST.label(), LatchkeyYard.GUARD_POST.x(), LatchkeyYard.GUARD_POST.y(),
                Strings.get("objectiveMarkerEstateGuards"), state));
        else for (var bench : LatchkeyYard.WORKPLACES)
            markers.add(new Marker("terms-" + bench.id(), bench.label(), bench.x(), bench.y(), Strings.get("objectiveMarkerFittersStay"), state));
        // The mortar appears on the picture only as far as it has actually been observed.
        if (sequence.mortarSighted())
            markers.add(new Marker("terms-mortar", LatchkeyYard.MORTAR_POST.label(), LatchkeyYard.MORTAR_POST.x(), LatchkeyYard.MORTAR_POST.y(),
                    Strings.get("objectiveMarkerMortarSighted"), state));
        else if (sequence.mortarReported())
            markers.add(new Marker("terms-mortar", Strings.get("objectiveTermsMortarReported"), LatchkeyYard.CINDER_GATE.x(), LatchkeyYard.CINDER_GATE.y(),
                    Strings.get("objectiveMarkerTubeUnlocated"), state));
        String focus = switch (phase) {
            case ESCORT -> "terms-escort";
            case SIGNING, DEFENCE, COMPLETE -> "terms-handover";
            default -> "terms-commissioning";
        };
        return new Item("terms-of-protection", Strings.get("objectiveTermsName"), state, Strings.named("objectivePhase", phase.name()),
                Strings.get("objectiveTermsSteps"), sequence.displayName(),
                phase == TermsOfProtectionProgress.Phase.SIGNING ? timed(Strings.get("objectiveTermsSigningTimer"), p.signing(), TermsOfProtectionProgress.SIGNING_SECONDS)
                        : timed(Strings.get("objectiveTermsCommissioningTimer"), p.work(), TermsOfProtectionProgress.WORK_SECONDS),
                p.terminal() ? -1 : phase == TermsOfProtectionProgress.Phase.SIGNING ? p.signing() / TermsOfProtectionProgress.SIGNING_SECONDS
                        : p.work() / TermsOfProtectionProgress.WORK_SECONDS,
                p.terminal() ? "" : sequence.fieldClear() ? Strings.get("objectiveTermsFieldClear")
                        : Strings.get("objectiveTermsBlocker"),
                markers, focus);
    }
    public static Item districtEscort(DistrictEscortSequence sequence) {
        var p = sequence.progress(); var state = state(sequence);
        boolean escort = p.phase() == DistrictEscortProgress.Phase.ESCORT;
        return new Item("district-escort", Strings.get("objectiveEscortName"), state, Strings.named("objectivePhase", p.phase().name()),
                Strings.get("objectiveEscortSteps"), sequence.displayName(),
                escort || p.terminal() ? Strings.get("objectiveEscortConsignment") : timed(Strings.get("objectiveEscortLinkTimer"), p.link(), DistrictEscortProgress.LINK_SECONDS),
                escort || p.terminal() ? -1 : p.link() / DistrictEscortProgress.LINK_SECONDS,
                p.terminal() || p.covered() ? "" : Strings.get("objectiveEscortBlocker"),
                List.of(new Marker("escort-load", Strings.get("objectiveEscortLoad"), sequence.cargoX(), sequence.cargoY(), Strings.get("objectiveMarkerEscort"), state),
                        new Marker("escort-apron", Strings.get("objectiveEscortApron"), LowQuay.LANDING_X, LowQuay.LANDING_Y, Strings.get("objectiveMarkerDeliverLoadParty"), state)),
                "escort-load");
    }
    public static Item limitedWarrant(LimitedWarrantSequence sequence) {
        var p = sequence.progress(); var state = state(sequence);
        boolean egress = p.phase() == LimitedWarrantProgress.Phase.EGRESS;
        String focus = p.phase() == LimitedWarrantProgress.Phase.RECORDS ? "warrant-records"
                : p.phase() == LimitedWarrantProgress.Phase.CALIBRATION ? "warrant-kit" : "warrant-load";
        return new Item("limited-warrant", Strings.get("objectiveWarrantName"), state, Strings.named("objectivePhase", p.phase().name()),
                Strings.get("objectiveWarrantSteps"), sequence.displayName(),
                egress ? Strings.get("objectiveWarrantEscortCarrier") : timed(Strings.get("objectiveProtectedWorkTimer"), p.work(), LimitedWarrantProgress.WORK_SECONDS),
                egress || p.terminal() ? -1 : p.work() / LimitedWarrantProgress.WORK_SECONDS,
                p.terminal() || p.safeWork() ? "" : egress ? Strings.get("objectiveWarrantEgressBlocker")
                        : Strings.get("objectiveWarrantWorkBlocker"),
                List.of(new Marker("warrant-records", Strings.get("objectiveWarrantRecords"), LowQuay.RECORDS_X, LowQuay.RECORDS_Y,
                                p.recordsRecovered() ? Strings.get("objectiveMarkerAuthenticated") : Strings.get("objectiveMarkerProtectedWork"), p.recordsRecovered() ? State.COMPLETE : state),
                        new Marker("warrant-kit", Strings.get("objectiveWarrantKit"), LowQuay.KIT_X, LowQuay.KIT_Y, Strings.get("objectiveMarkerSupplierTechnician"), state),
                        new Marker("warrant-load", Strings.get("objectiveWarrantLoad"), egress || p.terminal() ? sequence.targetX() : LowQuay.LOAD_X,
                                egress || p.terminal() ? sequence.targetY() : LowQuay.LOAD_Y, Strings.get("objectiveMarkerEscort"), state),
                        new Marker("warrant-egress", Strings.get("objectiveWarrantEgress"), LowQuay.LANDING_X, LowQuay.LANDING_Y, Strings.get("objectiveMarkerReturnLoad"), state)), focus);
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
            case ESCORT -> Strings.get("objectiveColdEscortRally");
            case POWER -> Strings.get("objectiveColdShorePower");
            default -> Strings.get("objectiveColdWorkSite");
        };
        String blocker = p.phase() == ColdStartProgress.Phase.CONSTRUCTION ? ""
                : p.contested() ? Strings.get("objectiveColdContested")
                : p.phase() == ColdStartProgress.Phase.ESCORT && !p.carrierArrived() ? Strings.get("objectiveColdCarrier")
                : !p.companyPresent() ? Strings.get("objectiveColdCompanyCover")
                : !p.crewPresent() ? Strings.get("objectiveColdTechnician")
                : p.phase() == ColdStartProgress.Phase.REPAIR && p.work() >= duration && !p.responseReachedYard()
                ? Strings.get("objectiveColdHold") : "";
        return at("cold-start", name, state(sequence), Strings.named("objectivePhase", p.phase().name()),
                Strings.get("objectiveColdSteps"), switch (p.phase()) {
                    case ESCORT -> Strings.get("objectiveColdEscort");
                    case POWER -> Strings.get("objectiveColdPower");
                    case CONSTRUCTION -> Strings.get("objectiveColdConstruction");
                    case REPAIR -> Strings.get("objectiveColdRepair");
                    default -> sequence.displayName();
                },
                duration > 0 ? timed(Strings.get("objectiveWorkTimer"), p.work(), duration) : p.phase() == ColdStartProgress.Phase.CONSTRUCTION
                        ? Strings.get("objectiveColdAwaitingTurret") : sequence.displayName(),
                duration > 0 ? p.work() / duration : -1, blocker, sequence.targetX(), sequence.targetY());
    }

    public static Item coldStartPicket(ColdStartPicket.Report post) {
        String name = post.key().equals("south_haul") ? Strings.get("objectivePicketSouthHaul") : Strings.get("objectivePicketQuarry");
        String status = switch (post.state()) {
            case HOLDING -> post.contacted() ? Strings.get("objectivePicketContact") : Strings.get("objectivePicketOuterWatch");
            case WITHDRAWING -> Strings.get("objectivePicketFallingBack");
            case INNER_HOLD -> Strings.get("objectivePicketInnerLine");
            case LOST -> Strings.get("objectivePicketLost");
        };
        // Strength is a report, not a capture meter or an additional victory requirement.
        return at("cold-start-picket-" + post.key(), name,
                post.aliveMembers() == 0 ? State.FAILED : State.ACTIVE, status,
                Strings.get("objectivePicketSteps"),
                switch (post.state()) {
                    case HOLDING -> Strings.get("objectivePicketHolding");
                    case WITHDRAWING -> Strings.get("objectivePicketWithdrawing");
                    case INNER_HOLD -> Strings.get("objectivePicketInnerHold");
                    case LOST -> Strings.get("objectivePicketLostInstruction");
                },
                Strings.format(post.overrun() ? "objectivePicketAliveOverrun" : "objectivePicketAlive",
                        post.aliveMembers(), post.initialMembers()), -1, "",
                post.withdrawing() ? post.innerX() : post.outerX(),
                post.withdrawing() ? post.innerY() : post.outerY());
    }

    public static Item depot(DepotHoldSequence sequence) {
        var p = sequence.progress();
        String measure = sequence.isComplete() ? Strings.get("objectiveDepotSecured") : !p.committed() ? timed(Strings.get("objectiveCaptureTimer"), p.captureFraction() * DepotHoldProgress.CAPTURE_SECONDS, DepotHoldProgress.CAPTURE_SECONDS)
                : p.waves() < 2 ? Strings.format("objectiveDepotNextAttack", seconds(p.nextWaveSeconds()))
                : Strings.get("objectiveDepotBothCommitted");
        return at("depot-dispatch", Strings.get("objectiveDepotName"), state(sequence),
                sequence.isComplete() ? Strings.get("objectiveMarkerSecured") : p.committed() ? Strings.get("objectiveDepotDefend") : Strings.get("objectiveDepotCapture"),
                Strings.get("objectiveDepotSteps"),
                sequence.isComplete() ? Strings.get("objectiveDepotDone")
                        : p.committed() ? Strings.get("objectiveDepotDefendInstruction")
                        : Strings.get("objectiveDepotCaptureInstruction"),
                measure, !p.committed() ? p.captureFraction() : -1,
                p.enemyInside() ? Strings.get("objectiveDepotContested")
                        : !(p.committed() ? p.friendlyInside() : p.companyInside())
                        ? Strings.get("objectiveDepotKeepInside") : "",
                sequence.x(), sequence.y());
    }

    public static Item compound(CompoundService.Record record, int index, String name) {
        boolean held = record.state == CompoundService.CompoundState.MARINE_HELD;
        return at("territory-" + index, name, held ? State.COMPLETE : State.ACTIVE,
                held ? Strings.get("objectiveCompoundHeld") : record.defendersPresent ? Strings.get("objectiveCompoundContested") : record.attackersPresent ? Strings.get("objectiveCompoundCapturing") : Strings.get("objectiveCompoundReachRoom"),
                Strings.get("objectiveCompoundSteps"), held ? Strings.get("objectiveCompoundHeldInstruction")
                        : Strings.get("objectiveCompoundInstruction"),
                held ? Strings.get("objectiveCompoundMarineHeld") : Strings.format("objectiveCompoundTransition", Math.round(record.captureProgress * 100)),
                held ? 1 : record.captureProgress,
                held ? "" : record.defendersPresent ? Strings.get("objectiveCompoundContestedBlocker")
                        : !record.attackersPresent ? Strings.get("objectiveCompoundEnterBlocker") : "",
                record.captureCellX >= 0 ? record.captureCellX : record.node.anchorX,
                record.captureCellY >= 0 ? record.captureCellY : record.node.anchorY);
    }

    public static Item freight(Objective objective, ThunderRunSequence column) {
        return new Item("freight-convoy", Strings.get("objectiveFreightName"), state(objective), column.resolved() ? Strings.get("objectiveFreightResolved") : Strings.get("objectiveFreightEscort"),
                Strings.get("objectiveFreightSteps"),
                Strings.get("objectiveFreightInstruction"),
                Strings.format("objectiveFreightTally", column.deliveredCount(), column.lostCount(),
                        column.cargoIdentities().size() - column.deliveredCount() - column.lostCount()),
                -1, column.holdSite() > 0 ? Strings.format(column.holdSite() == 1 ? "objectiveFreightHoldDispatch" : "objectiveFreightHoldCrossing",
                        seconds(column.holdRemainingTicks() / 30f)) : "",
                List.of(), "");
    }

    public static Item coldAccount(ColdAccountIntervention intervention, RaidObjective records) {
        var layout = intervention.layout();
        boolean evidence = intervention.phase() == ColdAccountIntervention.Phase.EVIDENCE;
        String blocker = !evidence && !intervention.companyRecovered()
                ? intervention.outsideWithdrawal() > 0 ? Strings.get("objectiveColdAccountReachPickup")
                : intervention.pendingArrivals() ? Strings.get("objectiveColdAccountArrivals") : Strings.get("objectiveColdAccountAwaiting") : "";
        return at("cold-account", evidence ? Strings.get("objectiveColdAccountOffice") : Strings.get("objectiveColdAccountPickup"), state(intervention),
                Strings.named("objectivePhase", intervention.phase().name()), Strings.get("objectiveColdAccountSteps"),
                intervention.isComplete() || intervention.isFailed() ? intervention.displayName()
                        : evidence ? Strings.get("objectiveColdAccountSecure") : intervention.companyRecovered()
                        ? Strings.get("objectiveColdAccountRecovered") : Strings.get("objectiveColdAccountWithdraw"),
                evidence ? records == null ? Strings.get("objectiveColdAccountAwaitingEvidence")
                        : timed(Strings.get("objectiveColdAccountEvidenceTimer"), records.serviceProgress(), records.serviceDuration())
                        : intervention.companyRecovered() ? Strings.get("objectiveColdAccountCompanyRecovered") : Strings.get("objectiveColdAccountAwaitingExtraction"),
                evidence && records != null ? records.serviceProgress() / records.serviceDuration() : -1,
                evidence && records != null && !records.marineServicing()
                        ? Strings.get("objectiveColdAccountBlocker") : blocker,
                evidence ? layout.evidenceX() : layout.withdrawalX(), evidence ? layout.evidenceY() : layout.withdrawalY());
    }

    public static Item generic(Objective objective, int index) {
        State state = state(objective);
        return new Item("mission-" + index, objective.displayName(), state, Strings.named("objectiveState", state.name()), "", objective.displayName(),
                state == State.COMPLETE ? Strings.get("objectiveComplete") : state == State.FAILED ? Strings.get("objectiveFailed") : Strings.get("objectiveAwaitingCondition"),
                -1, "", List.of(), "");
    }
    static String seconds(float value) { return Strings.format("objectiveSeconds", Math.max(0, Math.round(value * 10)) / 10f); }
    static String timed(String label, float work, float duration) {
        return Strings.format("objectiveTimed", label, seconds(work), seconds(duration));
    }
}
