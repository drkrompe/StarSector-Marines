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
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import java.awt.Color;
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
        if ("interface".equals(System.getProperty("battle.salvageRights.fixture"))) return frames;
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
        Runnable none = () -> { };
        var board = boardProps(progress, mission);
        frames.add(document(context, renderer, "contract-board.png", MissionSelectScreen.COMPONENT_PATHS,
                MissionSelectScreen.ROOT_COMPONENT, board));

        addBriefingFrames(context, renderer, mission, frames);
        try (var strings = new HeadlessStrings(context.modRoot(), SalvageRightsSnapshotSuite::expandDialogueCopy)) {
            var translatedMission = ShepherdRecoveryCampaign.offer(new ShepherdRecoveryProgress(), "Asharu");
            var translatedBoard = boardProps(new ShepherdRecoveryProgress(), translatedMission);
            frames.add(document(context, renderer, "contract-board-expanded.png", MissionSelectScreen.COMPONENT_PATHS,
                    MissionSelectScreen.ROOT_COMPONENT, translatedBoard));
            frames.add(document(context, renderer, "contract-board-expanded-conversation-end.png", MissionSelectScreen.COMPONENT_PATHS,
                    MissionSelectScreen.ROOT_COMPONENT, translatedBoard));
        }

        var result = MissionOutcome.builder().mission(mission).victory(true).payoutEarned(18000)
                .marinesEngaged(24).marinesLost(4).alliesEngaged(10).alliesLost(0)
                .salvageRights(new SalvageRightsSequence.Report(true,0)).build();
        var debrief = ResultsScreen.previewProps(true,false);
        debrief.put("missionName", mission.name);
        debrief.put("resultRows", ResultsScreen.resultRows(result, LootManifest.EMPTY, ModStrings.fromDisk(context.modRoot())));
        debrief.put("squadRows", List.of());
        ResultsScreen.putStoryDebrief(debrief,result);
        frames.add(document(context,renderer,"recovery-agreement.png",ResultsScreen.COMPONENT_PATHS,ResultsScreen.ROOT_COMPONENT,debrief));
        var hq = CompanyHqViewModel.preview().props();
        hq.put("openingGuide", new ShepherdRecoveryWorkGuide(progress, null, none));
        var desk = new CompanyHqNewsDesk(new Reactor());
        desk.put(hq);
        frames.add(document(context, renderer, "hq-work-guide.png", List.of(
                "data/ui/components/company/company-resource-stock.mlx",
                "data/ui/components/company/company-resource-cost.mlx", "data/ui/components/marine-ops-page-nav.mlx",
                "data/ui/components/company/company-hq.mlx"), "company-hq", hq));
    }

    private static Map<String, Object> boardProps(ShepherdRecoveryProgress progress, Mission mission) {
        var board = MissionSelectScreen.previewProps();
        Runnable none = () -> { };
        board.put("locationLabel", "Asharu / Corvus");
        board.put("clients", List.of(new MissionSelectScreen.ClientRow("client-vale", "client-vale-name", "client-vale-rep",
                OpeningBroker.CREST, "client-card selected", MissionDialogue.speakerName("Vale"), "Basin Reclamation Cooperative", false, none)));
        board.put("missionHeader", "Basin Reclamation Cooperative");
        board.put("missions", MissionSelectScreen.missionRows(List.of(mission), mission, ignored -> { }));
        board.put("stationingRows", List.of());
        MissionSelectScreen.putDetail(board, mission, none, none);
        MissionSelectScreen.putShepherd(board, progress);
        return board;
    }

    /** Append 35% copy without modifying MessageFormat or printf placeholders. */
    private static String expandDialogueCopy(String text) {
        if (text.isBlank()) return text;
        int extra = Math.max(2, (int) Math.ceil(text.length() * .35));
        return text + " " + "extended ".repeat(extra / 9 + 1).substring(0, extra).stripTrailing();
    }

    private void addBriefingFrames(SnapshotContext context, HeadlessUiRenderer renderer, Mission mission,
                                   List<SnapshotArtifact> frames) throws Exception {
        var props = BriefingViewModel.campaignPreviewProps(false);
        props.put("missionTitle", mission.name);
        props.put("missionKicker", "SALVAGE RIGHTS / ASHARU");
        MissionDialogue.put(props, "mission", mission);
        BriefingViewModel.putReward(props, mission);
        BriefingViewModel.putFactionIdentity(props, "employer", "Basin Reclamation Cooperative", null);
        BriefingViewModel.putFactionIdentity(props, "opposition", "Competing salvage crew", null);
        BriefingViewModel.putForceComparison(props, mission.tier, mission.risk, 24, 24);
        props.put("personnelSummary", "Established / 2 squads / 24 ready marines");
        BriefingViewModel.putLandingCraft(props, mission,
                List.of(FittedBoat.standard(ShuttleType.AEROSHUTTLE), FittedBoat.standard(ShuttleType.AEROSHUTTLE)),
                24, "Second Wind");
        props.put("missionRows", List.of(
                BriefingViewModel.info("presence", "Field presence", mission.fieldPresencePolicy.briefingText(), ""),
                BriefingViewModel.info("inspection", "Inspection", "Protect both technicians at Morrow's southern service access", "")));
        props.put("employerRows", List.of(
                BriefingViewModel.info("basin-militia", "Vale's militia", "Follow the company into the receiving store and workshop", ""),
                BriefingViewModel.info("basin-technicians", "Inspection party", "Two technicians / Morrow's southern service access", "")));
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), BriefingScreen.COMPONENT_PATHS);
        try (var instance = loader.reloadAndBuild(new Reactor(), BriefingScreen.ROOT_COMPONENT, props)) {
            BriefingViewModel.applyVisibility(instance, props);
            var document = new UiDocument(instance.root()).theme(MarineOpsThemes.standard());
            instance.styles().forEach(document::addStyleSheet);
            frames.add(new SnapshotArtifact("predeployment-briefing.png", renderer.renderRelative(document,
                    1920, 1080, 1f, MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT)));
            var dialogue = instance.requireElement("mission-dialogue");
            assertDialogueGeometry(document, dialogue);
            var pane = instance.requireElement("mission-overview-copy");
            var last = dialogue.children().get(dialogue.children().size() - 1);
            pane.scrollTop(Math.max(0f, last.box().borderBox().bottom() - pane.box().contentBox().bottom()));
            frames.add(new SnapshotArtifact("predeployment-briefing-conversation-end.png", renderer.renderRelative(document,
                    1920, 1080, 1f, MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT)));
            assertTrue(last.box().borderBox().bottom() <= pane.box().contentBox().bottom() + 1f,
                    "The last authored turn must be reachable through the production briefing scroll pane");
        }
    }

    private static void assertDialogueGeometry(UiDocument document, UiElement dialogue) {
        assertFalse(dialogue.children().isEmpty(), "The authored Vale/Castle exchange must be projected");
        float previousBottom = Float.NEGATIVE_INFINITY;
        var speakerColors = new HashMap<String, Color>();
        for (var turn : dialogue.children()) {
            var speakerWrap = turn.children().get(0);
            var speaker = speakerWrap.children().get(0);
            var body = turn.children().get(1);
            assertTrue(turn.box().borderBox().y() >= previousBottom,
                    "Wrapped dialogue turns must not overlap");
            var turnBox = turn.box().contentBox();
            assertTrue(turnBox.height() > 0f, "A dialogue turn must reserve its painted text height");
            assertTrue(speakerWrap.box().borderBox().y() >= turnBox.y() - 0.5f,
                    "The speaker belongs inside its turn");
            assertTrue(body.box().borderBox().bottom() <= turnBox.bottom() + 0.5f,
                    "Each turn must contain the complete painted body, rather than merely ordering zero-height boxes");
            assertTrue(turn.box().borderBox().bottom() <= dialogue.box().contentBox().bottom() + 0.5f,
                    "The dialogue container must reserve the height of every turn");
            assertTrue(body.box().borderBox().y() >= speakerWrap.box().borderBox().bottom(),
                    "Dialogue text must follow its complete speaker label");
            assertEquals(Color.WHITE, body.textColor(), "Spoken dialogue stays white");
            assertTextFits(document, body);
            if (!speaker.text().isBlank()) {
                assertTextFits(document, speaker);
                assertTrue(speaker.box().borderBox().bottom() <= speakerWrap.box().contentBox().bottom() + 0.5f,
                        "The name wrapper must reserve every wrapped name line");
                var prior = speakerColors.putIfAbsent(speaker.text(), speaker.textColor());
                if (prior != null) assertEquals(prior, speaker.textColor(), "A speaker retains one name color");
            }
            previousBottom = turn.box().borderBox().bottom();
        }
        assertTrue(new HashSet<>(speakerColors.values()).size() >= 2,
                "Vale and Castle must retain distinct name colors");
    }
    private static void assertTextFits(UiDocument document, UiElement label) {
        var font = document.styles().fontFor(label);
        assertNotNull(font);
        var content = label.box().contentBox();
        assertTrue(content.width() > 0f, "A text label needs positive wrapping width");
        float paintedHeight = font.wrapLines(label.text(), content.width()).size() * font.getLineHeight();
        assertTrue(paintedHeight > 0f && content.height() + 0.5f >= paintedHeight,
                "The label must reserve its actual painted line height: " + label.id());
    }

    private SnapshotArtifact document(SnapshotContext context, HeadlessUiRenderer renderer, String filename,
                                      List<String> paths, String root, Map<String,Object> props) throws Exception {
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), paths);
        loader.reload();
        try (var instance = loader.build(new Reactor(), root, props)) {
            var document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            var image = renderer.renderRelative(document,1920,1080,1f,
                    MarineOpsUiViewport.REFERENCE_WIDTH,MarineOpsUiViewport.REFERENCE_HEIGHT);
            if (root.equals(MissionSelectScreen.ROOT_COMPONENT)) {
                var dialogue = instance.requireElement("mission-dialogue");
                assertDialogueGeometry(document, dialogue);
                for (String id : List.of("mission-brief", "mission-back")) {
                    var control = instance.requireElement(id).box().borderBox();
                    var viewport = document.root().box().contentBox();
                    assertTrue(control.width() > 0f && control.height() > 0f
                                    && control.x() >= viewport.x() && control.y() >= viewport.y()
                                    && control.right() <= viewport.right() && control.bottom() <= viewport.bottom(),
                            "The translated contract board keeps its primary controls visible: " + id);
                }
                if (filename.contains("conversation-end")) {
                    var pane = instance.requireElement("mission-detail-copy");
                    var last = dialogue.children().get(dialogue.children().size() - 1);
                    pane.scrollTop(Math.max(0f, last.box().borderBox().bottom() - pane.box().contentBox().bottom()));
                    image = renderer.renderRelative(document,1920,1080,1f,
                            MarineOpsUiViewport.REFERENCE_WIDTH,MarineOpsUiViewport.REFERENCE_HEIGHT);
                    assertTrue(last.box().borderBox().bottom() <= pane.box().contentBox().bottom() + 1f,
                            "Expanded final dialogue remains accessible through the contract board scroll pane");
                }
            }
            return new SnapshotArtifact(filename, image);
        }
    }
}
