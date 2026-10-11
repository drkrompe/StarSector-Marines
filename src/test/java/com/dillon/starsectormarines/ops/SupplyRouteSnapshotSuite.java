package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.LastLiftProgress;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.loot.LootManifest;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.fs.starfarer.api.campaign.RepLevel;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.awt.image.BufferedImage;

/** Production documents with explicit campaign state fixtures, 1920x1080 / 100%. */
public final class SupplyRouteSnapshotSuite implements SnapshotSuite {
    public String id(){return "supply-route";}
    public String label(){return "Broken Supply Route campaign flow";}
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        var renderer=new HeadlessUiRenderer(context.modRoot(),context.starsectorCore());
        var frames=new ArrayList<SnapshotArtifact>();
        var p=new BrokenSupplyRouteProgress("jangala","Jangala","Corvus");
        frames.add(board(context,renderer,p,0,"01-junction"));
        win(p);frames.add(board(context,renderer,p,0,"02-inspect-rill"));frames.add(board(context,renderer,p,1,"03-inspect-relay"));
        p.commit(2);frames.add(board(context,renderer,p,0,"04-rill-committed"));
        String first=p.missionId();p.begin(first);p.settle(first,false,new LastLiftProgress.Outcome(p.crew().previewAttempt(),List.of(0,1),List.of(2),false));
        frames.add(board(context,renderer,p,0,"05-rill-retry-nine"));
        String retry=p.missionId();p.begin(retry);p.settle(retry,true,new LastLiftProgress.Outcome(p.crew().previewAttempt(),p.crew().stranded(),List.of(),true));
        frames.add(board(context,renderer,p,0,"06-crew-recovery-new-job"));
        var hq=CompanyHqViewModel.supplyRoutePreview(p,new OpeningBroker.Destination("jangala","Jangala","Corvus",null));
        frames.add(new SnapshotArtifact("07-hq-rescue-record.png",UiSnapshotSuite.renderCompanyHq(context,renderer,1920,1080,1f,hq)));
        var freight=new BrokenSupplyRouteProgress("jangala","Jangala","Corvus");win(freight);freight.commit(3);frames.add(board(context,renderer,freight,0,"08-relay-committed"));win(freight);
        frames.add(board(context,renderer,freight,0,"09-route-security-new-job"));
        hq=CompanyHqViewModel.supplyRoutePreview(freight,null);
        frames.add(new SnapshotArtifact("10-hq-frozen-site-unavailable.png",UiSnapshotSuite.renderCompanyHq(context,renderer,1920,1080,1f,hq)));
        frames.add(debrief(context, renderer, 1, true, "11-junction-debrief"));
        frames.add(debrief(context, renderer, 2, false, "12-rill-partial-retreat"));
        frames.add(debrief(context, renderer, 3, true, "13-relay-debrief"));
        return frames;
    }
    private SnapshotArtifact debrief(SnapshotContext context, HeadlessUiRenderer renderer,
                                      int kind, boolean victory, String name) throws Exception {
        var route = new BrokenSupplyRouteProgress("jangala", "Jangala", "Corvus");
        if (kind != BrokenSupplyRouteProgress.JUNCTION) { win(route); route.commit(kind); }
        var mission = SupplyRouteCampaign.offers(route).get(0);
        var result = MissionOutcome.builder().mission(mission).victory(victory)
                .payoutEarned(victory ? mission.payout : 0).marinesEngaged(24).marinesLost(2);
        if (kind == BrokenSupplyRouteProgress.LIFT) result.lastLiftOutcome(new LastLiftProgress.Outcome(
                route.crew().previewAttempt(), List.of(0, 1), List.of(2), false));
        var outcome = result.build();
        var props = ResultsScreen.previewProps(victory, false);
        props.put("missionName", outcome.missionName);
        props.put("resultRows", ResultsScreen.resultRows(outcome,
                LootManifest.EMPTY, ModStrings.fromDisk(context.modRoot())));
        props.put("squadRows", List.of(
                new ResultsScreen.PersonnelRow("scene-squad-1", "1st Squad", "10R 0W 0M 2K",
                        "label heading results-squad-summary tone-danger",
                        "Hale R · Chen R · Ilyin R · Okafor R · Bell K · Ruiz K"),
                new ResultsScreen.PersonnelRow("scene-squad-2", "2nd Squad", "12R 0W 0M 0K",
                        "label heading results-squad-summary tone-good",
                        "Vale R · Sato R · Moss R · Holt R · Venn R · Ward R")));
        ResultsScreen.putStoryDebrief(props, outcome);
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), ResultsScreen.COMPONENT_PATHS);
        loader.reload();
        try (var instance = loader.build(new Reactor(), ResultsScreen.ROOT_COMPONENT, props)) {
            var document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            var image = renderer.renderRelative(document, 1920, 1080, 1f,
                    MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT);
            return new SnapshotArtifact(name + ".png", image);
        }
    }
    private void win(BrokenSupplyRouteProgress p){String id=p.missionId();p.begin(id);p.settle(id,true,null);}
    private SnapshotArtifact board(SnapshotContext context,HeadlessUiRenderer renderer,BrokenSupplyRouteProgress p,int selected,String name)throws Exception {
        var roster=new MarineRoster();roster.markStoryComplete(OpeningOperationKind.COUNTERATTACK.missionId);
        var client=OpeningBroker.present(new Client("independent","Independent",OpeningBroker.CREST,RepLevel.NEUTRAL,false,null),true);
        var missions=SupplyRouteCampaign.offers(p);var mission=missions.get(selected);
        Map<String,Object> props=MissionSelectScreen.previewProps();Runnable none=()->{};
        props.put("clients",List.of(new MissionSelectScreen.ClientRow("client-venn","client-venn-name","client-venn-rep",client.crestPath,"client-card selected",client.displayName,"Independent broker",false,none)));
        props.put("missionHeader",client.displayName);props.put("missions",MissionSelectScreen.missionRows(missions,mission,m->{}));props.put("stationingRows",List.of());
        MissionSelectScreen.putBroker(props,client,roster);MissionSelectScreen.putDetail(props,mission,none,none);MissionSelectScreen.putSupplyRoute(props,p,mission,true);
        var loader=new MarkupLoader(path->Files.readString(context.modRoot().resolve(path)),MissionSelectScreen.COMPONENT_PATHS);loader.reload();
        try(var instance=loader.build(new Reactor(),MissionSelectScreen.ROOT_COMPONENT,props)){
            var document=new UiDocument(instance.root());for(var style:instance.styles())document.addStyleSheet(style);document.theme(MarineOpsThemes.standard());
            BufferedImage image=renderer.renderRelative(document,1920,1080,1f,MarineOpsUiViewport.REFERENCE_WIDTH,MarineOpsUiViewport.REFERENCE_HEIGHT);
            return new SnapshotArtifact(name+".png",image);
        }
    }
}
