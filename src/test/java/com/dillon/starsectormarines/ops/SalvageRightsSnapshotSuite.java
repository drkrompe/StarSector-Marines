package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchOverlay;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.power.CommandPowerResources;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.battle.setup.SalvageRightsSequence;
import com.dillon.starsectormarines.ops.loot.LootManifest;
import java.util.Map;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real battle playback; outcomes are observations, not a forced victory. */
public final class SalvageRightsSnapshotSuite implements SnapshotSuite {
    public String id(){return "salvage-rights";}
    public String label(){return "Salvage Rights: company lift, workshop handoff and contested inspection";}
    public List<SnapshotArtifact> render(SnapshotContext context)throws Exception{
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        var frames=new ArrayList<SnapshotArtifact>();
        addInterfaceFrames(context, frames);
        var report=new StringBuilder("Salvage Rights production factory, named campaign overlay. 1920x1080 / 100%. No player orders, no Hound.\n");
        var out=context.projectRoot().resolve("build/reports/salvage-rights");Files.createDirectories(out);
        for(int variant=0;variant<3;variant++){
            long seed=14101+(variant==1?1:0);boolean absent=variant==2;
            String key=absent?"absent-company":"company-"+seed;
            var mission=ShepherdRecoveryCampaign.offer(new ShepherdRecoveryProgress(),"Asharu");
            var roster=DebugMissionCatalog.starterCompany(2);
            var selected=roster.squads().stream().filter(s->!s.reserve()).map(s->s.id()).collect(Collectors.toSet());
            var deployment=CampaignMarineDeployment.freezeSelection(roster,selected,24);
            var boats=List.of(FittedBoat.standard(variant==1?ShuttleType.HERMES:ShuttleType.AEROSHUTTLE),FittedBoat.standard(ShuttleType.AEROSHUTTLE));
            var manifest=DetachmentResolver.buildShuttleManifestForPersonnel(mission,boats,deployment.size());
            try(var sim=BattleSetup.createSalvageRights(seed,manifest,new TargetProfile(0,0,0,0,"pirates",Set.of(),SurfacePalette.ARID,SettlementLink.ROAD))){
                deployment.applyToAuthoredPlayerLift(sim);
                BattleLaunchOverlay.capture(0,CampaignMarineDeployment.EMPTY,FlybyRoster.EMPTY,FlybyRoster.EMPTY,List.of(),0,mission.fieldPresencePolicy).applyTo(sim,CommandPowerResources.FREE);
                int aircraft=0;var committed=new HashSet<String>();
                for(long air:sim.getAirEntityIds())if(sim.world().airFaction(air)==Faction.MARINE){
                    assertSame(boats.get(aircraft++),sim.world().airframe(air));
                    var flight=sim.world().mission(air);
                    for(var cycle:flight.cycleLoadouts)for(var person:cycle){assertNotNull(person.campaignSoldierId);assertTrue(committed.add(person.campaignSoldierId));}
                    if(absent)flight.pendingDelay+=9999;
                }
                assertEquals(24,committed.size());assertEquals(boats.size(),aircraft);
                var renderer=new BattleReviewFrameRenderer(context.modRoot(),1920,1080,101,104,170).withImpactEffects();
                var stage=sim.getSalvageRights().progress().phase();
                frames.add(new SnapshotArtifact(key+"-approach.png",renderer.render(sim,"SALVAGE RIGHTS / NIGHT APPROACH / "+key)));
                var landed=new HashSet<String>();var squadIds=new HashMap<String,Set<Integer>>();
                int maxTicks=absent?3600:18000;
                while(!sim.isComplete()&&sim.getSimTickIndex()<maxTicks){
                    sim.advance(BattleSimulation.TICK_DT);
                    for(int i=0;i<sim.liveUnitCount();i++){
                        long id=sim.liveUnitAt(i);
                        if(sim.identity().faction(id)!=Faction.MARINE)continue;
                        String name=(String)sim.getEntityWorld().getObject(id,sim.getBattleComponents().IDENTITY,BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID);
                        assertTrue(committed.contains(name),"No anonymous company replacements");
                        landed.add(name);
                        String companySquad=deployment.commitments().stream().filter(s->s.campaignSoldierId().equals(name)).findFirst().orElseThrow().campaignSquadId();
                        squadIds.computeIfAbsent(companySquad,ignored->new HashSet<>()).add(sim.squad().squadId(id));
                    }
                    var phase=sim.getSalvageRights().progress().phase();
                    if(phase!=stage){
                        report.append(key).append(" tick ").append(sim.getSimTickIndex()).append(" ").append(phase).append('\n');
                        var marks=ReviewAnnotations.builder().label(sim.getSalvageRights().targetX(),sim.getSalvageRights().targetY(),phase.name(),ReviewStyle.OBJECTIVE).build();
                        frames.add(new SnapshotArtifact(key+"-"+phase.name().toLowerCase()+".png",renderer.render(sim,sim.getSalvageRights().displayName(),marks)));
                        stage=phase;
                    }
                    if(sim.getSimTickIndex()%1800==0) {
                        var progress=sim.getSalvageRights().progress();
                        String gates=" tick="+sim.getSimTickIndex()+" phase="+phase+" landed="+landed.size()
                                +" companyAtTarget="+progress.companyAtTarget()+" contested="+progress.contested()
                                +" capture="+progress.capture()+" inspection="+progress.inspection();
                        System.out.println("[salvage-rights] "+key+gates);
                        report.append(key).append(gates).append('\n');
                    }
                }
                if(absent){assertEquals(0,landed.size());assertEquals("RECEIVING",stage.name());assertFalse(sim.isComplete());}
                else assertEquals(24,landed.size(),"Every committed named marine must land through the actual boat cycles");
                for(var ids:squadIds.values())assertEquals(1,ids.size(),"Each campaign squad reunites across physical sorties");
                report.append(key).append(" final=").append(stage).append(" ticks=").append(sim.getSimTickIndex()).append(" landed=").append(landed.size())
                        .append(" crewLost=").append(sim.getSalvageRights().report(sim).crewLost()).append('\n');
                for(var squad:sim.getSquads())if(sim.squadMemberCount(squad.id)>0)
                    report.append("  ").append(squad.faction).append(" squad ").append(squad.id).append(" count=").append(sim.squadMemberCount(squad.id))
                            .append(" at=").append(squad.centroidX).append(',').append(squad.centroidY).append(" order=").append(sim.getSquadCommandDirective(squad.id)).append('\n');
                for(var event:sim.getStoryBeats())report.append("  ").append(event).append('\n');
                frames.add(new SnapshotArtifact(key+"-final.png",renderer.render(sim,"FINAL OBSERVATION / "+stage+" / "+sim.getSimTickIndex()/30+" SECONDS")));
                Files.writeString(out.resolve("playback.txt"),report);
            }
        }
        return frames;
    }

    private void addInterfaceFrames(SnapshotContext context, List<SnapshotArtifact> frames) throws Exception {
        var renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
        var progress = new ShepherdRecoveryProgress();
        var mission = ShepherdRecoveryCampaign.offer(progress, "Asharu");
        var board = MissionSelectScreen.previewProps();
        Runnable none = () -> { };
        board.put("locationLabel", "Asharu / Corvus");
        board.put("clients", List.of(new MissionSelectScreen.ClientRow("client-vale", "client-vale-name", "client-vale-rep",
                OpeningBroker.CREST, "client-card selected", "Tamsin Vale", "Basin Reclamation Cooperative", false, none)));
        board.put("missionHeader", "Basin Reclamation Cooperative");
        board.put("missions", MissionSelectScreen.missionRows(List.of(mission), mission, ignored -> { }));
        board.put("stationingRows", List.of());
        MissionSelectScreen.putDetail(board, mission, none, none);
        MissionSelectScreen.putShepherd(board, progress);
        frames.add(document(context, renderer, "contract-board.png", MissionSelectScreen.COMPONENT_PATHS,
                MissionSelectScreen.ROOT_COMPONENT, board));

        var result = MissionOutcome.builder().mission(mission).victory(true).payoutEarned(18000)
                .marinesEngaged(24).marinesLost(4).alliesEngaged(10).alliesLost(0)
                .salvageRights(new SalvageRightsSequence.Report(true,0)).build();
        var debrief = ResultsScreen.previewProps(true,false);
        debrief.put("missionName", mission.name);
        debrief.put("resultRows", ResultsScreen.resultRows(result, LootManifest.EMPTY, ModStrings.fromDisk(context.modRoot())));
        debrief.put("squadRows", List.of());
        ResultsScreen.putStoryDebrief(debrief,result);
        frames.add(document(context,renderer,"recovery-agreement.png",ResultsScreen.COMPONENT_PATHS,ResultsScreen.ROOT_COMPONENT,debrief));
    }

    private SnapshotArtifact document(SnapshotContext context, HeadlessUiRenderer renderer, String filename,
                                      List<String> paths, String root, Map<String,Object> props) throws Exception {
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), paths);
        loader.reload();
        try (var instance = loader.build(new Reactor(), root, props)) {
            var document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            return new SnapshotArtifact(filename, renderer.renderRelative(document,1920,1080,1f,
                    MarineOpsUiViewport.REFERENCE_WIDTH,MarineOpsUiViewport.REFERENCE_HEIGHT));
        }
    }
}
