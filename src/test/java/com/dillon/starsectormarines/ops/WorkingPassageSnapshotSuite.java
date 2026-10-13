package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.WorkingPassageProgress;
import com.dillon.starsectormarines.battle.tender.BuildOption;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.ops.battleview.*;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.ops.loot.LootManifest;
import com.dillon.starsectormarines.testsupport.*;
import com.dillon.starsectormarines.tools.snapshot.*;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in production playback, with ordinary commands and explicit fault controls. */
public final class WorkingPassageSnapshotSuite implements SnapshotSuite {
    private record Fixture(String id, long seed, boolean mixed, boolean absent, boolean build,
                           boolean retreat, boolean disable, boolean refitted) {
        Fixture(String id,long seed,boolean mixed,boolean absent,boolean build,boolean retreat,boolean disable) {
            this(id,seed,mixed,absent,build,retreat,disable,true);
        }
    }
    public String id() { return "working-passage"; }
    public String label() { return "Working Passage: company lift, mobile construction and physical climb-out"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        String property="battle.unitUpdate.minimumParallelUnits", previous=System.getProperty(property);
        System.setProperty(property,Integer.toString(Integer.MAX_VALUE));
        try { return renderSerial(context); }
        finally { if(previous==null)System.clearProperty(property);else System.setProperty(property,previous); }
    }
    private List<SnapshotArtifact> renderSerial(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        InstalledHullSpecs.install(); InstalledVanillaData.install();
        var frames=new ArrayList<SnapshotArtifact>();
        var report=new StringBuilder("Working Passage / production mission factory / 1920x1080 / 100%.\n"
                +"Detached named campaign company: 24 marines; service pulse rifles and starting Watchman's Kit, no Hound or officer bonuses.\n"
                +"Starter controls retain the original Rook rifles. No campaign inventory or economy is changed by this fixture.\n"
                +"Real fitted boats and physical trips; normal ship-bar construction requests at both stops.\n"
                +"Infantry and Morrow otherwise autonomous. Controls explicitly delay boats, withhold construction, retreat or damage Morrow.\n"
                +"Observer renders are not a live save, fog-of-war or campaign acceptance certificate.\n");
        var output=context.projectRoot().resolve("build/reports/working-passage");
        Files.createDirectories(output);
        var fixtures=List.of(new Fixture("campaign-aero-14101",14101,false,false,true,false,false),
                new Fixture("paired-aero-15101",15101,false,false,true,false,false),
                new Fixture("mixed-lift-15102",15102,true,false,true,false,false),
                new Fixture("starter-control-14101",14101,false,false,true,false,false,false),
                new Fixture("absent-company",15101,false,true,false,false,false),
                new Fixture("no-build",15101,false,false,false,false,false),
                new Fixture("withdraw-after-bend",15101,false,false,true,true,false),
                new Fixture("disabled-morrow",15101,false,false,true,false,true));
        var campaign=readyCampaign();
        addBoard(context,frames,campaign);
        if ("interface".equals(System.getProperty("battle.workingPassage.fixture"))) {
            // Authored frozen outcome sample for UI review; no battle or campaign handover is performed.
            addDebrief(context, frames, "interface", ShepherdRecoveryCampaign.offer(campaign,"Asharu"),
                    new WorkingPassageProgress.Report(true,false,false,1),3);
            return frames;
        }
        for(var fixture:fixtures) {
            String filter=System.getProperty("battle.workingPassage.fixture","");
            if(!filter.isBlank()&&!fixture.id.contains(filter))continue;
            try {
                var result=play(context,fixture,readyCampaign(),frames,report);
                if(fixture.retreat&&result.aborted()) {
                    String attempt=campaign.missionId();assertTrue(campaign.begin(attempt,"Asharu"));
                    assertEquals(0,campaign.settleWorkingPassage(attempt,"Asharu",false,0,result.militiaLost(),false,
                            result.disabled(),result.aborted(),null,0));
                    assertTrue(campaign.repaired());assertFalse(campaign.departed());
                    report.append("  withdrawal retained repair/claim; retryId=").append(campaign.missionId()).append('\n');
                    play(context,new Fixture("retry-after-withdrawal",15101,false,false,true,false,false),campaign,frames,report);
                }
            } finally { Files.writeString(output.resolve("playback.txt"),report); }
        }
        return frames;
    }
    private ShepherdRecoveryProgress readyCampaign() {
        var p=new ShepherdRecoveryProgress();String claim=p.missionId();assertTrue(p.begin(claim,"Asharu"));
        assertEquals(ShepherdRecoveryProgress.PAYMENT,p.settle(claim,"Asharu",true,ShepherdRecoveryProgress.PAYMENT,0,true));
        String repair=p.missionId();assertTrue(p.begin(repair,"Asharu"));
        assertEquals(ShepherdRecoveryProgress.COLD_PAYMENT,p.settleColdStart(repair,"Asharu",true,
                ShepherdRecoveryProgress.COLD_PAYMENT,0,true,true,true,true));return p;
    }
    private WorkingPassageProgress.Report play(SnapshotContext context,Fixture f,ShepherdRecoveryProgress campaign,List<SnapshotArtifact> frames,StringBuilder report) throws Exception {
        var mission=ShepherdRecoveryCampaign.offer(campaign,"Asharu");
        var roster=f.refitted?DebugMissionCatalog.expectedCompany(DebugMissionCatalog.Scenario.WORKING_PASSAGE.mission(),2)
                :DebugMissionCatalog.starterCompany(2);
        var selected=roster.squads().stream().filter(s->!s.reserve()).map(s->s.id()).collect(Collectors.toSet());
        var deployment=CampaignMarineDeployment.freezeSelection(roster,selected,24);
        var boats=List.of(FittedBoat.standard(f.mixed?ShuttleType.HERMES:ShuttleType.AEROSHUTTLE),FittedBoat.standard(ShuttleType.AEROSHUTTLE));
        var manifest=DetachmentResolver.buildShuttleManifestForPersonnel(mission,boats,deployment.size());
        report.append('\n').append(f.id).append(" seed=").append(f.seed).append(" mission=").append(mission.id).append(" boats=").append(boats).append('\n');
        var loadout=roster.soldiers().stream().collect(Collectors.groupingBy(
                s->s.primaryId()+"/"+s.primaryGrade()+"/"+s.armorId(),TreeMap::new,Collectors.counting()));
        report.append("  loadout=").append(loadout).append('\n');
        try(var sim=BattleSetup.createWorkingPassage(f.seed,manifest,new TargetProfile(0,0,0,0,"pirates",Set.of(),SurfacePalette.ARID,SettlementLink.ROAD))) {
            deployment.applyToAuthoredPlayerLift(sim);var sequence=sim.getWorkingPassage();
            var committed=new HashSet<String>();var soldierSquads=new HashMap<String,String>();
            for(var seat:deployment.commitments())soldierSquads.put(seat.campaignSoldierId(),seat.campaignSquadId());
            int aircraft=0;
            for(long air:sim.getAirEntityIds())if(sim.world().airFaction(air)==Faction.MARINE) {
                assertSame(boats.get(aircraft++),sim.world().airframe(air));var flight=sim.world().mission(air);
                for(var cycle:flight.cycleLoadouts)for(var person:cycle) {
                    assertNotNull(person.campaignSoldierId);assertTrue(committed.add(person.campaignSoldierId));
                }
                if(f.absent)flight.pendingDelay+=9999;
            }
            assertEquals(24,committed.size());assertEquals(2,aircraft);
            report.append("  committed=").append(committed.stream().sorted().toList()).append('\n');
            var overview=new BattleReviewFrameRenderer(context.modRoot(),1920,1080,160,95,300).withImpactEffects().withUnitShadows();
            var landing=new BattleReviewFrameRenderer(context.modRoot(),1920,1080,47,91,110).withImpactEffects().withUnitShadows();
            var phase=sequence.progress().phase();float startX=sequence.tender().anchorX(),startY=sequence.tender().anchorY();
            frames.add(frame(f.id+"-arrival.png",sim,overview,"WORKING PASSAGE / DAYBREAK / ACTUAL PRODUCTION MAP"));
            var landed=new HashSet<String>();var reunited=new HashMap<String,Set<Integer>>();var shots=new HashMap<Long,Integer>();
            var requested=EnumSet.noneOf(WorkingPassageProgress.Phase.class);var building=EnumSet.noneOf(WorkingPassageProgress.Phase.class);
            boolean landedFrame=false,firingFrame=false,disabled=false;
            int maxTicks=f.absent?3600:Integer.getInteger("battle.workingPassage.maxTicks",27000);
            while(!sim.isComplete()&&sim.getSimTickIndex()<maxTicks) {
                if(f.build&&(phase==WorkingPassageProgress.Phase.BEND_BUILD||phase==WorkingPassageProgress.Phase.NARROWS_BUILD)&&!requested.contains(phase)
                        &&sequence.tender().groundSiteReachable(sequence.buildX()+.5f,sequence.buildY()+.5f,sim.getGrid())) {
                    requested.add(phase);
                    sim.shipCommands().requestBuild(sequence.tender(),BuildOption.VULCAN_TURRET,sequence.buildX(),sequence.buildY());
                    report.append("  player ship-bar construction request ").append(phase).append(" tick=").append(sim.getSimTickIndex())
                            .append(" hull=").append(sequence.tender().anchorX()).append(',').append(sequence.tender().anchorY())
                            .append(" plotWalkable=").append(sim.getGrid().isWalkable(sequence.buildX(),sequence.buildY())).append('\n');
                }
                sim.advance(BattleSimulation.TICK_DT);
                for(int i=0;i<sim.liveUnitCount();i++) {
                    long unit=sim.liveUnitAt(i);
                    if(sim.identity().faction(unit)!=Faction.MARINE||sim.identity().type(unit)!=UnitType.MARINE)continue;
                    String soldier=(String)sim.getEntityWorld().getObject(unit,sim.getBattleComponents().IDENTITY,BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID);
                    assertNotNull(soldier,"No anonymous replacements");assertTrue(committed.contains(soldier));landed.add(soldier);
                    reunited.computeIfAbsent(soldierSquads.get(soldier),ignored->new HashSet<>()).add(sim.squad().squadId(unit));
                }
                for(var shot:sim.getShotsThisFrame())if(sequence.turretEntityIds().contains(shot.shooterId))shots.merge(shot.shooterId,1,Integer::sum);
                if(!landedFrame&&!landed.isEmpty()) { landedFrame=true;frames.add(frame(f.id+"-first-landing.png",sim,landing,"ACTUAL FITTED BOAT ARRIVAL / "+sim.getSimTickIndex()/30+"s")); }
                if(!firingFrame&&!shots.isEmpty()) { firingFrame=true;frames.add(detail(context,f.id+"-turret-fire.png",sim,"CONSTRUCTED TURRET ENGAGING / "+sim.getSimTickIndex()/30+"s")); }
                if(!building.contains(phase)&&sequence.tender().orders().stream().anyMatch(o->o.fraction()>.2f&&o.fraction()<.8f)) {
                    building.add(phase);frames.add(detail(context,f.id+"-"+phase.name().toLowerCase()+"-building.png",sim,"ACTUAL DRONE CONSTRUCTION / "+sim.getSimTickIndex()/30+"s"));
                }
                var next=sequence.progress().phase();
                if(next!=phase) {
                    report.append("  tick=").append(sim.getSimTickIndex()).append(" phase=").append(next).append(" hull=")
                            .append(sequence.tender().anchorX()).append(',').append(sequence.tender().anchorY()).append(" hp=")
                            .append(sequence.tender().ship().hullFraction()).append(" pending=").append(sequence.pendingMembers()).append('\n');
                    frames.add(detail(context,f.id+"-"+next.name().toLowerCase()+".png",sim,"WORKING PASSAGE / "+next+" / "+sim.getSimTickIndex()/30+"s"));
                    phase=next;
                }
                if(f.retreat&&phase==WorkingPassageProgress.Phase.NARROWS) { assertTrue(sim.retreat());report.append("  confirmed player withdrawal\n"); }
                if(f.disable&&!disabled&&!landed.isEmpty()) {
                    disabled=true;sequence.tender().ship().takeDamage(100000,100000);report.append("  control: catastrophic damage injected\n");
                }
                if(sim.getSimTickIndex()%1800==0) {
                    String observation="  tick="+sim.getSimTickIndex()+" phase="+phase+" landed="+landed.size()+" hull="+sequence.tender().anchorX()+","+sequence.tender().anchorY()+" notice="+sim.shipCommands().notice(sequence.tender().ship());
                    report.append(observation).append('\n');System.out.println("[working-passage] "+f.id+observation);
                }
                if(!f.build&&!f.absent&&phase==WorkingPassageProgress.Phase.BEND_BUILD&&sim.getSimTickIndex()>15000)break;
            }
            var result=sequence.report(sim);
            if(f.absent) {assertEquals(0,landed.size());assertEquals(startX,sequence.tender().anchorX(),.01f);assertEquals(startY,sequence.tender().anchorY(),.01f);assertFalse(result.departed());}
            else if(!f.disable)assertEquals(24,landed.size(),"All named personnel arrive through actual trips");
            for(var ids:reunited.values())assertEquals(1,ids.size(),"Campaign squads reunite across boats/trips");
            if(!f.build||f.retreat||f.disable)assertFalse(result.departed());
            if(f.disable)assertTrue(result.disabled());
            int alive=0;
            for(int i=0;i<sim.liveUnitCount();i++)if(soldierSquads.containsKey((String)sim.getEntityWorld().getObject(sim.liveUnitAt(i),sim.getBattleComponents().IDENTITY,BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID)))alive++;
            report.append("  final=").append(phase).append(" tick=").append(sim.getSimTickIndex()).append(" complete=").append(sim.isComplete())
                    .append(" companyAlive=").append(alive).append(" landed=").append(landed.size()).append(" report=").append(result)
                    .append(" physicallyDeparted=").append(sequence.tender().departed()).append(" turretShots=").append(shots)
                    .append(" built=").append(sequence.tender().built()).append(" buildersLost=").append(sequence.tender().buildersLost()).append('\n');
            for(var o:sequence.tender().orders())report.append("  build phase=").append(o.phase()).append(" fraction=").append(o.fraction()).append(" failure=").append(o.failure()).append('\n');
            for(var s:sim.getSquads())if(sim.squadMemberCount(s.id)>0)report.append("  ").append(s.faction).append(" squad=").append(s.id).append(" count=").append(sim.squadMemberCount(s.id)).append(" at=").append(s.centroidX).append(',').append(s.centroidY).append(" order=").append(sim.getSquadCommandDirective(s.id)).append('\n');
            report.append("  reunited=").append(reunited).append('\n');for(var beat:sim.getStoryBeats())report.append("  ").append(beat).append('\n');
            for(int i=0;i<sim.liveUnitCount();i++) {
                long id=sim.liveUnitAt(i);if(sim.identity().faction(id)!=Faction.DEFENDER)continue;
                var squad=sim.squadOf(id);var step=squad==null||squad.currentPlan==null?null:squad.currentPlan.currentStep();
                report.append("  defender ").append(id).append(" squad=").append(squad==null?-1:squad.id).append(" at=").append(sim.world().x(id)).append(',').append(sim.world().y(id))
                        .append(" settled=").append(sim.movement().settled(id)).append(" target=").append(sim.targetOf(id)).append(" goal=").append(squad==null?null:squad.currentGoal)
                        .append(" action=").append(step==null?null:step.action.name()).append(" slot=").append(step==null?null:step.slotOf(id)).append(" contact=").append(squad==null?null:squad.contactPicture).append('\n');
            }
            frames.add(frame(f.id+"-final.png",sim,overview,"FINAL OBSERVATION / "+phase+" / "+sim.getSimTickIndex()/30+"s"));
            if(result.departed())addDebrief(context,frames,f.id,mission,result,24-alive);
            if(f.refitted&&f.build&&!f.absent&&!f.retreat&&!f.disable&&maxTicks>=27000) {
                assertTrue(result.departed(),"Expected company must complete the departure sequence");
                assertTrue(sequence.tender().departed(),"The actual hull must leave the map");
                assertTrue(sequence.tender().built()>=2,"Both positions require physical construction");
                assertFalse(shots.isEmpty(),"An actually constructed defense must engage");
            }
            return result;
        }
    }
    private SnapshotArtifact detail(SnapshotContext c,String name,BattleSimulation sim,String caption)throws Exception {
        var s=sim.getWorkingPassage();return frame(name,sim,new BattleReviewFrameRenderer(c.modRoot(),1920,1080,s.targetX()-8,s.targetY(),115).withImpactEffects().withUnitShadows(),caption);
    }
    private SnapshotArtifact frame(String name,BattleSimulation sim,BattleReviewFrameRenderer renderer,String caption)throws Exception {
        var s=sim.getWorkingPassage();var marks=ReviewAnnotations.builder().label(s.targetX(),s.targetY(),s.displayName(),ReviewStyle.OBJECTIVE).build();return new SnapshotArtifact(name,renderer.render(sim,caption,marks));
    }
    private void addBoard(SnapshotContext c,List<SnapshotArtifact> frames,ShepherdRecoveryProgress p)throws Exception {
        var mission=ShepherdRecoveryCampaign.offer(p,"Asharu");var board=MissionSelectScreen.previewProps();Runnable none=()->{};
        board.put("locationLabel","Asharu / Corvus");board.put("clients",List.of(new MissionSelectScreen.ClientRow("client-vale","client-vale-name","client-vale-rep",OpeningBroker.CREST,"client-card selected","Tamsin Vale","Basin Reclamation Cooperative",false,none)));
        board.put("missionHeader","Basin Reclamation Cooperative");board.put("missions",MissionSelectScreen.missionRows(List.of(mission),mission,ignored->{}));board.put("stationingRows",List.of());
        MissionSelectScreen.putDetail(board,mission,none,none);MissionSelectScreen.putShepherd(board,p);
        frames.add(document(c,"contract-board.png",MissionSelectScreen.COMPONENT_PATHS,MissionSelectScreen.ROOT_COMPONENT,board));
    }
    private void addDebrief(SnapshotContext c,List<SnapshotArtifact> frames,String prefix,Mission mission,WorkingPassageProgress.Report report,int lost)throws Exception {
        var outcome=MissionOutcome.builder().mission(mission).victory(true).payoutEarned(0).marinesEngaged(24).marinesLost(lost).alliesEngaged(8).alliesLost(report.militiaLost()).workingPassage(report).build();
        var props=ResultsScreen.previewProps(true,false);props.put("missionName",mission.name);props.put("resultRows",ResultsScreen.resultRows(outcome,LootManifest.EMPTY,ModStrings.fromDisk(c.modRoot())));props.put("squadRows",List.of());ResultsScreen.putStoryDebrief(props,outcome);
        frames.add(document(c,prefix+"-handover.png",ResultsScreen.COMPONENT_PATHS,ResultsScreen.ROOT_COMPONENT,props));
    }
    private SnapshotArtifact document(SnapshotContext c,String name,List<String> paths,String root,Map<String,Object> props)throws Exception {
        var loader=new MarkupLoader(path->Files.readString(c.modRoot().resolve(path)),paths);loader.reload();
        try(var instance=loader.build(new Reactor(),root,props)) {
            var doc=new UiDocument(instance.root());for(var style:instance.styles())doc.addStyleSheet(style);doc.theme(MarineOpsThemes.standard());
            return new SnapshotArtifact(name,new HeadlessUiRenderer(c.modRoot(),c.starsectorCore()).renderRelative(doc,1920,1080,1f,MarineOpsUiViewport.REFERENCE_WIDTH,MarineOpsUiViewport.REFERENCE_HEIGHT));
        }
    }
}
