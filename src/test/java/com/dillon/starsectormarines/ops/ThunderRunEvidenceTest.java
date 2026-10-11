package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.FreightRunProgress;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import java.awt.image.BufferedImage;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.ThunderRunCompound;
import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/** Production campaign construction and named loadouts, with explicit diagnostic controls. */
@Tag("thunder-run-command-evidence")
class ThunderRunEvidenceTest {
    @Test void reportConvoyFinale() throws Exception {
        Path output = Path.of(System.getProperty("thunderrun.command.evidence.outputDir", "build/reports/commander/thunder-run"));
        Files.createDirectories(output);
        int maxTicks = Integer.getInteger("thunderrun.command.evidence.maxTicks", 18000);
        String custom = System.getProperty("thunderrun.command.evidence.fixture.path", "");
        JSONArray cases = custom.isBlank() ? new JSONArray("[{id:'ordinary-9301',seed:9301},{id:'ordinary-9302',seed:9302},{id:'ordinary-9303',seed:9303},{id:'unarmed-final',seed:9301,unarmedFinal:true},{id:'no-arrival',seed:9301,absent:true},{id:'cargo-loss',seed:9301,destroyCargo:true},{id:'partial-retry',seed:9301,retry:true},{id:'withdraw-after-delivery',seed:9301,retreat:true}]") : new JSONArray(Files.readString(Path.of(custom)));
        JSONArray reports = new JSONArray();
        var renderer = new BattleReviewFrameRenderer(Path.of("mod"), 1920, 1080);
        for (int ci = 0; ci < cases.length(); ci++) {
            var fixture = cases.getJSONObject(ci); String name = fixture.getString("id");
            Path dir = output.resolve(name); Files.createDirectories(dir);
            var route = new BrokenSupplyRouteProgress("jangala", "Jangala", "Corvus");
            String id = route.missionId(); route.begin(id); route.settle(id, true, null); route.commit(3);
            id = route.missionId(); route.begin(id); route.settle(id, true, null);
            if (fixture.optBoolean("retry")) {
                id=route.missionId();route.begin(id);
                route.settle(id,false,null,new FreightRunProgress.Outcome(route.freight().previewAttempt(),List.of(0),List.of(1),false));
                assertEquals(List.of(2),route.freight().waiting());
            }
            Mission mission = SupplyRouteCampaign.offers(route).get(0);
            assertEquals("Thunder Run", mission.name);
            var roster = new MarineRoster(); roster.ensureActiveSoldiers(24);
            var selected = roster.squads().stream().filter(s -> !s.reserve()).map(s -> s.id()).collect(Collectors.toSet());
            var deployment = CampaignMarineDeployment.freezeSelection(roster, selected, 24);
            var boats = List.of(new FittedBoat(ShuttleType.VALKYRIE, BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE, "freight-alpha"),
                    new FittedBoat(ShuttleType.VALKYRIE, BoatFitting.requireById("reinforced_plating"), BoatFitting.requireById("tuned_drive"), "freight-bravo"));
            var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(mission, boats, 24);
            var profile = new TargetProfile(0,0,0,0,"",Set.of(),SurfacePalette.VERDANT,SettlementLink.ROAD);
            try (var sim = MissionLaunch.supplyRouteBuilder(route,fixture.getLong("seed"),manifest,profile).get()) {
                deployment.applyToAuthoredPlayerLift(sim);
                assertTrue(route.begin(mission.id));
                int seats = 0;
                for (long air : sim.getAirEntityIds()) if (sim.world().airFaction(air) == Faction.MARINE) {
                    var lift = sim.world().mission(air);
                    assertSame(manifest.get(lift.manifestOrdinal).airframe, sim.world().airframe(air));
                    for (var cycle : lift.cycleLoadouts) for (var seat : cycle) { assertNotNull(seat.campaignSoldierId); seats++; }
                    if (fixture.optBoolean("absent")) lift.pendingDelay += 2000;
                }
                assertEquals(24, seats);
                var column = sim.getThunderRun();
                assertEquals(fixture.optBoolean("retry")?1:3,column.cargo().size()); assertEquals(column.cargo().size()+2,sim.getConvoyVehicleIds().length);
                if (fixture.optBoolean("destroyCargo")) for (long vehicle : column.cargo().values()) sim.convoy().destroy(vehicle);
                List<Long> escorts = new ArrayList<>();
                for (long vehicle : sim.getConvoyVehicleIds()) if (sim.convoy().vehicleType(vehicle).hasTurretWeapon()) {
                    escorts.add(vehicle);
                    // Read-only diagnostic counters: the shared fire/damage seams already write these when present.
                    sim.getEntityWorld().addComponent(vehicle, sim.getBattleComponents().TELEMETRY);
                }
                Map<Long, Float> finalDamage = new LinkedHashMap<>();
                Map<Long, Integer> finalRounds = new LinkedHashMap<>();
                int firstFinalShot = -1;
                var sites = List.copyOf(sim.getCompoundService().getRecords());
                assertEquals(4,sites.size());
                assertEquals(3,sites.subList(1,4).stream().map(r -> sim.getCompoundService().captureZoneId(r,sim)).distinct().count());
                int previousPhase = -1, landedTick = -1, finalTick = -1;
                JSONArray samples = new JSONArray();
                var close = new BattleReviewFrameRenderer(Path.of("mod"),1920,1080,273,75,70);
                while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                    if (column.phase() == 3 && finalRounds.isEmpty()) for (long escort : escorts) {
                        finalRounds.put(escort, sim.telemetry().roundsFired(escort));
                        finalDamage.put(escort, sim.telemetry().damageDealt(escort));
                        if (fixture.optBoolean("unarmedFinal")) sim.convoy().turret(escort).ammo = 0;
                    }
                    sim.advance(BattleSimulation.TICK_DT);
                    if (column.phase() == 3) for (var shot : sim.getShotsThisFrame())
                        if (escorts.contains(shot.shooterId) && firstFinalShot < 0) firstFinalShot = sim.getSimTickIndex();
                    int tick = sim.getSimTickIndex();
                    if(fixture.optBoolean("retreat") && column.deliveredCount()>0 && !sim.isComplete()) assertTrue(sim.retreat());
                    if (landedTick < 0 && count(sim,Faction.MARINE)>0) landedTick=tick;
                    if (tick % 300 == 0 || column.phase()!=previousPhase || sim.isComplete()) {
                        JSONArray vehicles = new JSONArray();
                        for (long vehicle : sim.getConvoyVehicleIds()) {
                            var body = sim.convoy().body(vehicle);
                            vehicles.put(new JSONObject().put("id",vehicle).put("type",sim.convoy().vehicleType(vehicle).name())
                                    .put("x",body.x).put("y",body.y).put("speed",body.speed).put("structure",sim.convoy().structure(vehicle)));
                        }
                        samples.put(new JSONObject().put("tick",tick).put("phase",column.phase()).put("marines",count(sim,Faction.MARINE))
                                .put("militia",count(sim,Faction.ALLY)).put("raiders",count(sim,Faction.DEFENDER)).put("vehicles",vehicles)
                                .put("sites",new JSONArray(sites.stream().map(r->r.state.name()).toList())));
                        Files.writeString(dir.resolve("samples.json"),samples.toString(2));
                    }
                    boolean phaseChanged=column.phase()!=previousPhase;
                    if(column.phase()==3 && finalTick<0) finalTick=tick;
                    if(name.equals("ordinary-9301") && finalTick>=0 && ((tick-finalTick)%60==0
                            || (firstFinalShot>=0 && tick-firstFinalShot<450 && (tick-firstFinalShot)%6==0))) {
                        ImageIO.write(withHud(close.render(sim,"THUNDER RUN / "+(tick/30)+"s / "+column.status()),sim),"png",dir.resolve(String.format("convoy-%05d.png",tick)).toFile());
                    }
                    if ((name.equals("ordinary-9301") && (phaseChanged || tick%900==0)) || sim.isComplete()) {
                        var marks=ReviewAnnotations.builder();
                        for(int si=0;si<sites.size();si++) {
                            var r=sites.get(si);
                            marks.label(r.node.anchorX,r.node.anchorY,ThunderRunCompound.SITE_NAMES.get(si).toUpperCase()+" / "+r.state,
                                    r.state==CompoundState.MARINE_HELD?ReviewStyle.LANDING:ReviewStyle.OBJECTIVE);
                        }
                        marks.label(306,83,"FREIGHT EXIT",ReviewStyle.LANDING);
                        for(var entry:column.cargo().entrySet()) if(sim.convoy().isVehicle(entry.getValue())) {
                            var body=sim.convoy().body(entry.getValue());
                            marks.label(body.x,body.y-4,"CARGO "+(entry.getKey()+1),ReviewStyle.NOTE);
                        }
                        ImageIO.write(renderer.render(sim,"THUNDER RUN / "+(tick/30)+"s / "+column.status(),marks.build()),"png",dir.resolve(String.format("frame-%05d.png",tick)).toFile());
                    }
                    previousPhase=column.phase();
                    if(fixture.optBoolean("absent") && tick>=9000) break;
                }
                if(fixture.optBoolean("absent")) { assertEquals(0,column.phase()); assertEquals(0,column.deliveredCount()); }
                if(fixture.optBoolean("destroyCargo")) { assertTrue(sim.isComplete()); assertNotEquals(Faction.MARINE,sim.getWinner()); assertEquals(3,column.lostCount()); }
                if (!fixture.optBoolean("absent") && !fixture.optBoolean("unarmedFinal") && maxTicks >= 18000) {
                    assertTrue(sim.isComplete(), "A full convoy run must resolve, including its rear carrier");
                    if (!fixture.optBoolean("destroyCargo") && !fixture.optBoolean("retreat")) {
                        assertEquals(Faction.MARINE, sim.getWinner());
                        assertTrue(column.deliveredCount() > 0);
                    }
                }
                JSONArray escortReports = new JSONArray();
                for (long escort : escorts) escortReports.put(new JSONObject().put("id",escort)
                        .put("rounds",sim.telemetry().roundsFired(escort)).put("hits",sim.telemetry().roundsHit(escort))
                        .put("damage",sim.telemetry().damageDealt(escort)).put("kills",sim.telemetry().kills(escort))
                        .put("finalRounds",sim.telemetry().roundsFired(escort)-finalRounds.getOrDefault(escort,sim.telemetry().roundsFired(escort)))
                        .put("finalDamage",sim.telemetry().damageDealt(escort)-finalDamage.getOrDefault(escort,sim.telemetry().damageDealt(escort)))
                        .put("structure",sim.convoy().structure(escort)).put("armor",sim.world().armor(escort))
                        .put("ammo",sim.convoy().turret(escort).ammo));
                JSONArray beats=new JSONArray();
                for(var e:sim.getStoryBeats()) beats.put(new JSONObject().put("tick",e.tick()).put("id",e.id()));
                var report=new JSONObject().put("fixture",name).put("ticks",sim.getSimTickIndex()).put("landedTick",landedTick)
                        .put("result",sim.isComplete()?String.valueOf(sim.getWinner()):"UNRESOLVED").put("phase",column.phase())
                        .put("delivered",column.deliveredCount()).put("lost",column.lostCount()).put("namedSeats",seats)
                        .put("marinesAlive",count(sim,Faction.MARINE)).put("militiaAlive",count(sim,Faction.ALLY)).put("raidersAlive",count(sim,Faction.DEFENDER))
                        .put("beats",beats).put("escorts",escortReports).put("firstFinalShot",firstFinalShot);
                if(column.outcome()!=null) {
                    int paid=route.settle(mission.id,sim.getWinner()==Faction.MARINE,null,column.outcome());
                    assertTrue(paid>=0); assertEquals(-1,route.settle(mission.id,sim.getWinner()==Faction.MARINE,null,column.outcome()));
                    report.put("paid",paid).put("campaignStatus",route.status());
                    if(fixture.optBoolean("retreat")) {
                        assertEquals(0,paid);assertFalse(route.freight().waiting().isEmpty());
                        assertEquals(column.deliveredCount(),route.freight().deliveredCount());
                        for(int carrier:route.freight().waiting()) assertTrue(column.cargo().containsKey(carrier));
                    }
                    if(fixture.optBoolean("retry") && sim.getWinner()==Faction.MARINE) assertEquals(6000+4000*(1+column.deliveredCount()),paid);
                }
                Files.writeString(dir.resolve("summary.json"),report.toString(2)); reports.put(report);
                Files.writeString(output.resolve("summary.json"),reports.toString(2));
                System.out.println("[thunder-run] "+name+" "+report.getString("result")+" tick="+sim.getSimTickIndex()+" phase="+column.phase());
            }
        }
    }
    private static BufferedImage withHud(BufferedImage image,BattleSimulation sim) throws Exception {
        Reactor reactor=new Reactor();
        var model=new BattleHudOverlayModel(reactor,ignored->{},"Pause","1x","2x","4x");
        var column=sim.getThunderRun();
        model.update(1f,sim.getCompoundService().getRecords(),sim.getCommanderSnapshot(Faction.MARINE));
        model.updateFreight(column.phase(),column.cargoIdentities(),column.cargoStates(),column.deliveredCount(),column.lostCount());
        var loader=new MarkupLoader(path->Files.readString(Path.of("mod").resolve(path)),List.of(BattleHudOverlay.COMPONENT_PATH));
        loader.reload();
        try(var instance=loader.build(reactor,BattleHudOverlay.COMPONENT,model.props())) {
            BattleHudOverlay.wireLayout(instance);
            var document=new UiDocument(instance.root());
            for(var style:instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            var ui=new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(Path.of("mod")),null);
            var overlay=ui.render(document,1896,Math.round(BattleHudOverlay.OBJECTIVE_HEIGHT));
            var graphics=image.createGraphics();graphics.drawImage(overlay,12,42,null);graphics.dispose();
        }
        return image;
    }
    private static int count(BattleSimulation sim,Faction faction) {
        int n=0;for(int i=0;i<sim.liveUnitCount();i++) if(sim.identity().faction(sim.liveUnitAt(i))==faction)n++;return n;
    }
}
