package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.trace.PlaceFightProbe;
import com.dillon.starsectormarines.battle.command.trace.ReactiveDefenceEvidence;
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;
import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import org.junit.jupiter.api.AfterEach;
import com.dillon.starsectormarines.battle.turret.TurretRole;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
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
import com.dillon.starsectormarines.battle.setup.ThunderRunPacing;
import com.dillon.starsectormarines.battle.setup.ThunderRunSequence;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.ThunderRunCompound;
import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
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
import java.util.HashSet;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import com.dillon.starsectormarines.tools.snapshot.AnimatedGifWriter;
import static org.junit.jupiter.api.Assertions.*;

/** Production campaign construction and named loadouts, with explicit diagnostic controls. */
@Tag("thunder-run-command-evidence")
class ThunderRunEvidenceTest {
    @AfterEach void resetWeaponSlots() { TurretSlotResolver.useHullSpecs(null); }

    @Test void reportConvoyFinale() throws Exception {
        InstalledWeaponSlots.install();
        Path output = Path.of(System.getProperty("thunderrun.command.evidence.outputDir", "build/reports/commander/thunder-run"));
        Files.createDirectories(output);
        int maxTicks = Integer.getInteger("thunderrun.command.evidence.maxTicks", 27000);
        String custom = System.getProperty("thunderrun.command.evidence.fixture.path", "");
        JSONArray cases = custom.isBlank() ? new JSONArray("[{id:'ordinary-9301',seed:9301},{id:'ordinary-9302',seed:9302},{id:'ordinary-9303',seed:9303},{id:'small-company',seed:9301,companySize:12},{id:'player-defend',seed:9301,playerDefend:true},{id:'silent-tank-final',seed:9301,silentTankFinal:true},{id:'unarmed-final',seed:9301,unarmedFinal:true},{id:'no-arrival',seed:9301,absent:true},{id:'cargo-loss',seed:9301,destroyCargo:true},{id:'partial-retry',seed:9301,retry:true},{id:'withdraw-after-delivery',seed:9301,retreat:true}]") : new JSONArray(Files.readString(Path.of(custom)));
        JSONArray reports = new JSONArray();
        var renderer = new BattleReviewFrameRenderer(Path.of("mod"), 1920, 1080);
        for (int ci = 0; ci < cases.length(); ci++) {
            var fixture = cases.getJSONObject(ci); String name = fixture.getString("id");
            Path dir = output.resolve(name); Files.createDirectories(dir);
            var staged = ThunderRunEvidenceBattle.build(fixture);
            var route = staged.route();
            Mission mission = staged.mission();
            int companySize = staged.companySize();
            var manifest = staged.manifest();
            try (var sim = staged.sim()) {
                // Measure the intact map, before any destruction changes its routes.
                var grid = sim.getGrid();
                var clearance = VehicleClearance.erode(grid,
                        VehicleClearance.radiusForWidth(VehicleType.CARGO_CARRIER.visualWidthCells));
                boolean[] vehicleRoute = new boolean[grid.getWidth() * grid.getHeight()];
                for (int y = 0; y < grid.getHeight(); y++) for (int x = 0; x < grid.getWidth(); x++)
                    vehicleRoute[grid.index(x,y)] = clearance.isPassable(x,y);
                boolean freightRoadClear = !Paths.isEmpty(GridPathfinder.findPath(grid,21,75,318,75,null,vehicleRoute));
                // Exclude only the checkpoint's road band from this diagnostic search.
                // A path would expose a real driveable bypass, not an AI route preference.
                for (int y = 68; y <= 82; y++) for (int x = 294; x <= 311; x++)
                    vehicleRoute[grid.index(x,y)] = false;
                boolean bypass = !Paths.isEmpty(GridPathfinder.findPath(grid,21,75,318,75,null,vehicleRoute));
                if (bypass) Files.writeString(dir.resolve("bypass.json"),new JSONArray(
                        GridPathfinder.findPath(grid,21,75,318,75,null,vehicleRoute)).toString());
                assertTrue(ThunderRunEvidenceBattle.launch(staged));
                int seats = 0;
                for (long air : sim.getAirEntityIds()) if (sim.world().airFaction(air) == Faction.MARINE) {
                    var lift = sim.world().mission(air);
                    assertSame(manifest.get(lift.manifestOrdinal).airframe, sim.world().airframe(air));
                    for (var cycle : lift.cycleLoadouts) for (var seat : cycle) { assertNotNull(seat.campaignSoldierId); seats++; }
                    if (fixture.optBoolean("absent")) lift.pendingDelay += 2000;
                }
                assertEquals(companySize, seats);
                if (fixture.optBoolean("clearFlak")) {
                    for (int i = sim.liveUnitCount() - 1; i >= 0; i--) {
                        long unit = sim.liveUnitAt(i);
                        if (sim.turretState().isTurret(unit) && sim.turretState().structure(unit).targetRole == TurretRole.AA)
                            sim.applyDamage(unit, 10000f, 100f);
                    }
                }
                var column = sim.getThunderRun();
                if (fixture.optBoolean("destroyCargo")) for (long vehicle : column.cargo().values()) sim.convoy().destroy(vehicle);
                List<Long> escorts = new ArrayList<>();
                for (long vehicle : sim.getConvoyVehicleIds()) if (sim.convoy().vehicleType(vehicle).hasTurretWeapon()) {
                    escorts.add(vehicle);
                    // Read-only diagnostic counters: the shared fire/damage seams already write these when present.
                    sim.getEntityWorld().addComponent(vehicle, sim.getBattleComponents().TELEMETRY);
                }
                Map<Long, Float> finalDamage = new LinkedHashMap<>();
                Map<Long, Integer> finalRounds = new LinkedHashMap<>();
                var impacts = new ReviewImpactFx(Path.of("mod"));
                // Opt-in recordings: -PsnapshotEveryTicks=N gives a whole-map review every N ticks
                // and a close camera following the column through the final run.
                int gifEvery = Integer.getInteger("commander.evidence.snapshot.everyTicks", 0);
                int gifDelay = Integer.getInteger("commander.evidence.snapshot.frameDelayMillis", 100);
                AnimatedGifWriter reviewGif = gifEvery > 0 ? new AnimatedGifWriter(dir.resolve("review.gif"), gifDelay * 3, AnimatedGifWriter.Palette.ADAPTIVE) : null;
                AnimatedGifWriter convoyGif = gifEvery > 0 ? new AnimatedGifWriter(dir.resolve("convoy-run.gif"), gifDelay, AnimatedGifWriter.Palette.ADAPTIVE) : null;
                // A fixed camera on the first crossroads, where the militia guns, the technical and the raiders meet.
                AnimatedGifWriter crossroadsGif = gifEvery > 0 ? new AnimatedGifWriter(dir.resolve("crossroads.gif"), gifDelay, AnimatedGifWriter.Palette.ADAPTIVE) : null;
                var crossroads = new BattleReviewFrameRenderer(Path.of("mod"), 1920, 1080, 150, 75, 84);
                int crossroadsFrames = 0;
                var follow = new BattleReviewFrameRenderer(Path.of("mod"), 1920, 1080, 60, 75, 96);
                int convoyFrames = 0;
                float followX = 60f;
                int firstFinalShot = -1;
                int[] rearContactTicks = new int[2];
                Map<Long, Integer> rearGunRounds = new LinkedHashMap<>();
                int rearStart = -1, advanceWreckTick = -1, retreatTick = -1;
                int openingRocketShots = 0;
                var rearCamera = new BattleReviewFrameRenderer(Path.of("mod"),1920,1080,150,75,112);
                var truckCamera = new BattleReviewFrameRenderer(Path.of("mod"),1920,1080,119,73,40);
                List<Long> measuredGuns = new ArrayList<>();
                List<Long> roadGuns = new ArrayList<>();
                Map<Long, Integer> gunDown = new LinkedHashMap<>();
                for (int i = 0; i < sim.liveUnitCount(); i++) {
                    long unit = sim.liveUnitAt(i);
                    if (sim.identity().type(unit).isTurret()) roadGuns.add(unit);
                }
                var sites = List.copyOf(sim.getCompoundService().getRecords());
                int previousPhase = -1, landedTick = -1, finalTick = -1;
                // Where the holds' damage lands: who absorbs it, and where both sides stand.
                Map<String, Float> soak = new LinkedHashMap<>();
                Map<Long, Float> lastTaken = new HashMap<>();
                Map<Long, String> soakKey = new HashMap<>();
                Map<Long, Float> lastHull = new HashMap<>();
                Map<String, Float> dealt = new TreeMap<>();
                Map<Long, String> dealtKey = new HashMap<>();
                Map<Long, Float> lastDealt = new HashMap<>();
                Set<Long> garrison = new HashSet<>();
                for (int i = 0; i < sim.liveUnitCount(); i++)
                    if (sim.identity().faction(sim.liveUnitAt(i)) == Faction.DEFENDER) garrison.add(sim.liveUnitAt(i));
                int[] raiderAt = new int[ThunderRunCompound.WIDTH / 10 + 1], marineAt = new int[raiderAt.length];
                int orderedHold = 0;
                int[] quietTicks = new int[4], longestQuiet = new int[4], combatTicks = new int[4];
                int[] longestQuietEnd = new int[4];
                int sharedDefenseTargets = 0;
                boolean orderReplaced = true;
                JSONArray samples = new JSONArray();
                JSONArray kestrelEncounter = new JSONArray();
                int observedKestrelBeats = 0;
                if (name.equals("ordinary-9301")) {
                    var checkpoint = new BattleReviewFrameRenderer(Path.of("mod"),1920,1080,270,73,164);
                    var marks = ReviewAnnotations.builder();
                    marks.label(256,64,"CLEAR OUTBOUND STRONGPOINT / RELEASE ARMOR",ReviewStyle.OBJECTIVE);
                    marks.label(296,35,"FENCED INSPECTION YARDS",ReviewStyle.NOTE);
                    marks.label(304,65,"DUG-IN GUN",ReviewStyle.OBJECTIVE);
                    marks.label(304,85,"DUG-IN GUN",ReviewStyle.OBJECTIVE);
                    marks.label(290,91,"RIFLE BAY / THREE RAIDERS",ReviewStyle.OBJECTIVE);
                    ImageIO.write(checkpoint.render(sim,"THUNDER RUN / PREPARED CHECKPOINT / BEFORE ARRIVAL",marks.build()),
                            "png",dir.resolve("checkpoint-before.png").toFile());
                }
                var close = new BattleReviewFrameRenderer(Path.of("mod"),1920,1080,282,75,80);
                PlaceFightProbe places = new PlaceFightProbe();
                // The command trace is the place-fight line's source of retakes and losses.
                sim.setCommandTraceEnabled(true, "THUNDER_RUN");
                while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                    for (long gun : column.supportGuns()) if (!measuredGuns.contains(gun)) {
                        measuredGuns.add(gun);
                        sim.getEntityWorld().addComponent(gun, sim.getBattleComponents().TELEMETRY);
                    }
                    if (column.phase() == 3 && finalRounds.isEmpty()) for (long escort : escorts) {
                        finalRounds.put(escort, sim.telemetry().roundsFired(escort));
                        finalDamage.put(escort, sim.telemetry().damageDealt(escort));
                        if (fixture.optBoolean("unarmedFinal") || (fixture.optBoolean("silentTankFinal") && escort == column.heavyLead())) {
                            sim.convoy().turret(escort).clearQueuedFire();
                            sim.convoy().turret(escort).ammo = 0;
                        }
                    }
                    if (fixture.optBoolean("playerDefend")) {
                        int hold = column.holdSite();
                        if (hold > orderedHold) {
                            var post = ThunderRunCompound.SUPPORT_POSTS.get(hold - 1);
                            for (var squad : sim.getSquads()) if (squad.faction == Faction.MARINE && squad.aliveMembers > 0)
                                sim.getSquadMoveOrderService().requestDefendArea(squad.id, post.x(), post.y());
                            orderedHold = hold;
                            orderReplaced = false;
                        } else if (hold == 0 && !orderReplaced) {
                            var next = sites.get(Math.min(3, orderedHold + 1));
                            for (var squad : sim.getSquads()) if (squad.faction == Faction.MARINE && squad.aliveMembers > 0)
                                sim.getSquadMoveOrderService().requestMove(squad.id, next.captureCellX, next.captureCellY);
                            orderReplaced = true;
                        }
                    }
                    sim.advance(BattleSimulation.TICK_DT);
                    impacts.feed(sim);
                    places.sample(sim);
                    if (column.phase() == 1 || column.phase() == 2) {
                        for (int i = 0; i < sim.liveUnitCount(); i++) {
                            long unit = sim.liveUnitAt(i);
                            Faction faction = sim.identity().faction(unit);
                            if (faction == Faction.DEFENDER && !sim.identity().type(unit).isStatic() && sim.getSimTickIndex() % 30 == 0)
                                raiderAt[Math.min(raiderAt.length - 1, (int) sim.world().x(unit) / 10)]++;
                            if (faction == Faction.MARINE && sim.getSimTickIndex() % 30 == 0)
                                marineAt[Math.min(marineAt.length - 1, (int) sim.world().x(unit) / 10)]++;
                            if (faction == Faction.DEFENDER) {
                                // Whoever was on the map before the first wave is garrison; later bodies are waves.
                                if (!dealtKey.containsKey(unit) && sim.telemetry().isRecorded(unit))
                                    dealtKey.put(unit, sim.identity().type(unit).isStatic() ? "emplacements"
                                            : garrison.contains(unit) ? "garrison" : "waves");
                                continue;
                            }
                            if (faction != Faction.MARINE && faction != Faction.ALLY) continue;
                            if (!sim.telemetry().isRecorded(unit)) sim.getEntityWorld().addComponent(unit, sim.getBattleComponents().TELEMETRY);
                            soakKey.putIfAbsent(unit, faction == Faction.MARINE ? "marines" : sim.identity().type(unit).isTurret() ? "militiaGuns" : "militiaInfantry");
                        }
                        // Hulls resolve through the body-damage path, which keeps no telemetry: read what they lost.
                        for (long vehicle : sim.getConvoyVehicleIds()) {
                            float hull = sim.convoy().structure(vehicle) + sim.world().armor(vehicle);
                            Float before = lastHull.put(vehicle, hull);
                            if (before != null && hull < before)
                                soak.merge("p" + column.phase() + (column.holdSite() > 0 ? "hold " : "push ") + "vehicle:" + sim.convoy().vehicleType(vehicle).name(), before - hull, Float::sum);
                        }
                        // Every body ever seen, so a killing blow on one that just left the roster still counts.
                        for (var seen : dealtKey.entrySet()) {
                            float now = sim.telemetry().damageDealt(seen.getKey());
                            float before = lastDealt.getOrDefault(seen.getKey(), 0f);
                            if (now > before) dealt.merge("p" + column.phase() + (column.holdSite() > 0 ? "hold " : "push ") + seen.getValue(), now - before, Float::sum);
                            lastDealt.put(seen.getKey(), now);
                        }
                        for (var seen : soakKey.entrySet())
                            if (sim.telemetry().isRecorded(seen.getKey())) soakDelta(sim, seen.getKey(), "p" + column.phase() + (column.holdSite() > 0 ? "hold " : "push ") + seen.getValue(), soak, lastTaken);
                    }
                    int combatPhase = column.phase();
                    if (sim.getShotsThisFrame().isEmpty()) {
                        quietTicks[combatPhase]++;
                        if (quietTicks[combatPhase] > longestQuiet[combatPhase]) {
                            longestQuiet[combatPhase] = quietTicks[combatPhase];
                            longestQuietEnd[combatPhase] = sim.getSimTickIndex();
                        }
                    } else {
                        combatTicks[combatPhase]++;
                        quietTicks[combatPhase] = 0;
                    }
                    if (column.phase() == 3) for (var shot : sim.getShotsThisFrame())
                        if (escorts.contains(shot.shooterId) && firstFinalShot < 0) firstFinalShot = sim.getSimTickIndex();
                    int tick = sim.getSimTickIndex();
                    var kestrelBeats = sim.getStoryBeats().stream()
                            .filter(e -> e.id().startsWith("supply_route.thunder_run.gunship_")).toList();
                    while (observedKestrelBeats < kestrelBeats.size()) {
                        var beat = kestrelBeats.get(observedKestrelBeats++);
                        var body = sim.world().kinematics(sim.getWaylineGunship().aircraft());
                        kestrelEncounter.put(new JSONObject().put("event", beat.id()).put("tick", beat.tick())
                                .put("x", body.x).put("y", body.y));
                    }
                    for (var shot : sim.getShotsThisFrame())
                        if (shot.weaponDef() != null && "weapon.scrap-rocket".equals(shot.weaponDef().id)) openingRocketShots++;
                    if (rearStart < 0 && sim.getStoryBeats().stream().anyMatch(e -> e.id().endsWith("road_batch_" + ThunderRunPacing.FIRST_REAR_BATCH))) {
                        rearStart = tick;
                        for (long gun : measuredGuns) rearGunRounds.put(gun, sim.telemetry().roundsFired(gun));
                    }
                    if (rearStart >= 0) {
                        for (var squad : sim.getSquads()) {
                            var a = squad.assignedObjective;
                            if (squad.faction != Faction.DEFENDER || squad.aliveMembers <= 0 || a == null) continue;
                            for (int post = 0; post < 2; post++) {
                                var cell = ThunderRunCompound.SUPPORT_POSTS.get(post);
                                if (a.targetCellX() != cell.x() || a.targetCellY() != cell.y()) continue;
                                float dx = squad.centroidX - cell.x(), dy = squad.centroidY - cell.y();
                                if (dx * dx + dy * dy < 24 * 24) rearContactTicks[post]++;
                            }
                        }
                        if ((tick - rearStart) % 900 == 0)
                            ImageIO.write(rearCamera.render(sim,"THUNDER RUN / REAR POSTS / " + tick / 30 + "s", ReviewAnnotations.NONE, impacts),
                                    "png",dir.resolve("rear-" + tick + ".png").toFile());
                    }
                    if (column.advanceHauler() != 0 && !sim.convoy().isTargetable(column.advanceHauler()) && advanceWreckTick < 0) {
                        advanceWreckTick = tick;
                        ImageIO.write(truckCamera.render(sim,"THUNDER RUN / ADVANCE HAULER LOST", ReviewAnnotations.NONE, impacts),
                                "png",dir.resolve("truck-destruction.png").toFile());
                    }
                    if (advanceWreckTick > 0 && tick == advanceWreckTick + 30)
                        ImageIO.write(truckCamera.render(sim,"THUNDER RUN / BURNING HAULER", ReviewAnnotations.NONE, impacts),
                                "png",dir.resolve("truck-burning.png").toFile());
                    if (tick % 75 == 0) {
                        Set<String> assignedPosts = new HashSet<>();
                        for (var squad : sim.getSquads()) {
                            var duty = squad.assignedObjective;
                            if (squad.aliveMembers <= 0 || squad.faction == Faction.DEFENDER
                                    || squad.assignedAuthority != CommandAuthority.MISSION_COMMAND
                                    || duty == null || duty.kind() != AssignmentKind.DEFEND_AREA) continue;
                            String post = duty.targetCellX() + "," + duty.targetCellY();
                            if (!assignedPosts.add(post)) sharedDefenseTargets++;
                        }
                    }
                    for (long gun : roadGuns) if (!sim.world().isAlive(gun)) gunDown.putIfAbsent(gun, tick);
                    if(fixture.optBoolean("retreat") && retreatTick < 0 && column.deliveredCount()>0 && !sim.isComplete()) {
                        assertTrue(sim.retreat()); retreatTick = tick;
                    }
                    if (landedTick < 0 && count(sim,Faction.MARINE)>0) landedTick=tick;
                    if (tick % 300 == 0 || column.phase()!=previousPhase || sim.isComplete()) {
                        JSONArray vehicles = new JSONArray();
                        for (long vehicle : sim.getConvoyVehicleIds()) {
                            var body = sim.convoy().body(vehicle);
                            vehicles.put(new JSONObject().put("id",vehicle).put("type",sim.convoy().vehicleType(vehicle).name())
                                    .put("x",body.x).put("y",body.y).put("speed",body.speed).put("structure",sim.convoy().structure(vehicle)));
                        }
                        JSONArray duties = new JSONArray();
                        for (var squad : sim.getSquads()) if (squad.faction != Faction.DEFENDER)
                            duties.put(new JSONObject().put("id",squad.id).put("faction",squad.faction)
                                    .put("alive",squad.aliveMembers).put("x",squad.centroidX).put("y",squad.centroidY)
                                    .put("authority",String.valueOf(squad.assignedAuthority))
                                    .put("assignment",String.valueOf(squad.assignedObjective)));
                        samples.put(new JSONObject().put("tick",tick).put("phase",column.phase()).put("marines",count(sim,Faction.MARINE))
                                .put("militia",militiaCount(sim)).put("raiders",count(sim,Faction.DEFENDER)).put("vehicles",vehicles)
                                .put("duties", duties).put("raiders", raiderCells(sim))
                                .put("sites",new JSONArray(sites.stream().map(r->r.state.name()).toList())));
                        Files.writeString(dir.resolve("samples.json"),samples.toString(2));
                    }
                    boolean phaseChanged=column.phase()!=previousPhase;
                    if(column.phase()==3 && finalTick<0) {
                        finalTick=tick;
                        var body = sim.convoy().body(column.technical());
                        var technicalCamera = new BattleReviewFrameRenderer(Path.of("mod"),1920,1080,body.x,body.y,32);
                        ImageIO.write(technicalCamera.render(sim,"WAYLINE MILITIA / TECHNICAL AT EAST CROSSING", ReviewAnnotations.NONE, impacts),
                                "png",dir.resolve("technical-parked.png").toFile());
                    }
                    if(name.equals("ordinary-9301") && finalTick>=0 && ((tick-finalTick)%60==0
                            || (firstFinalShot>=0 && tick-firstFinalShot<450 && (tick-firstFinalShot)%6==0))) {
                        ImageIO.write(withHud(close.render(sim,"THUNDER RUN / "+(tick/30)+"s / "+column.status()),sim),"png",dir.resolve(String.format("convoy-%05d.png",tick)).toFile());
                    }
                    if (name.equals("ordinary-9301") && tick % 900 == 0 && column.holdSite() > 0) {
                        var post = ThunderRunCompound.SUPPORT_POSTS.get(column.holdSite() - 1);
                        var defense = new BattleReviewFrameRenderer(Path.of("mod"),1920,1080,post.x()+12,75,100);
                        ImageIO.write(withHud(defense.render(sim,"THUNDER RUN / DEFEND AREA / " + tick/30 + "s"),sim),
                                "png",dir.resolve(String.format("defense-%05d.png",tick)).toFile());
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
                    if (reviewGif != null && (tick % gifEvery == 0 || sim.isComplete())) {
                        var marks=ReviewAnnotations.builder();
                        for(int si=0;si<sites.size();si++) {
                            var r=sites.get(si);
                            marks.label(r.node.anchorX,r.node.anchorY,ThunderRunCompound.SITE_NAMES.get(si).toUpperCase()+" / "+r.state,
                                    r.state==CompoundState.MARINE_HELD?ReviewStyle.LANDING:ReviewStyle.OBJECTIVE);
                        }
                        for(var entry:column.cargo().entrySet()) if(sim.convoy().isVehicle(entry.getValue())) {
                            var body=sim.convoy().body(entry.getValue());
                            marks.label(body.x,body.y-4,"CARGO "+(entry.getKey()+1),ReviewStyle.NOTE);
                        }
                        reviewGif.append(renderer.render(sim,"THUNDER RUN / "+(tick/30)+"s / "+column.status(),marks.build()));
                    }
                    if (crossroadsGif != null && column.phase() >= 1 && column.phase() < 3 && tick % 30 == 0 && crossroadsFrames < 600) {
                        crossroadsGif.append(crossroads.render(sim, "THUNDER RUN / CROSSROADS / " + (tick/30) + "s / " + column.status(),
                                crossroadsMarks(sim, column), impacts));
                        crossroadsFrames++;
                    }
                    if (convoyGif != null && column.phase() == 3 && tick % 10 == 0 && convoyFrames < 360) {
                        // Frame the moving column: the mean of the allied hulls still on the map.
                        float sum = 0f; int n = 0;
                        for (long vehicle : sim.getConvoyVehicleIds()) {
                            var m = sim.convoy().mission(vehicle);
                            var body = sim.convoy().body(vehicle);
                            if (sim.convoy().faction(vehicle) != Faction.ALLY || m.state == VehicleState.WRECKED
                                    || Math.abs(body.speed) < .2f || body.x > ThunderRunCompound.WIDTH) continue;
                            sum += body.x; n++;
                        }
                        // The camera moves in steps, so the ground holds still between them and the recording stays small.
                        if (n > 0) {
                            float wanted = Math.max(48f, Math.min(ThunderRunCompound.WIDTH - 48f, sum / n));
                            if (convoyFrames == 0 || Math.abs(wanted - followX) > 30f) follow.refocus(followX = wanted, 75, 96);
                        }
                        convoyGif.append(follow.render(sim,"THUNDER RUN / FINAL RUN / "+(tick/30)+"s / "+column.status(), ReviewAnnotations.NONE, impacts));
                        convoyFrames++;
                    }
                    previousPhase=column.phase();
                    if(fixture.optBoolean("absent") && tick>=9000) break;
                }
                if (reviewGif != null) { reviewGif.close(); convoyGif.close(); crossroadsGif.close(); }
                System.out.println(places.line(name, sim, sim.getCommandTraceJsonLines()));
                if(fixture.optBoolean("absent")) { assertEquals(0,column.phase()); assertEquals(0,column.deliveredCount()); }
                if(fixture.optBoolean("destroyCargo")) { assertTrue(sim.isComplete()); assertNotEquals(Faction.MARINE,sim.getWinner()); assertEquals(column.cargo().size(),column.lostCount()); }
                if (!fixture.optBoolean("absent") && !fixture.optBoolean("unarmedFinal") && !fixture.optBoolean("silentTankFinal") && maxTicks >= 18000) {
                    // Balance runs report a bounded timeout or loss; neither is manufactured into a win.
                    if (sim.isComplete() && sim.getWinner() == Faction.MARINE) {
                        assertTrue(column.resolved(), "Victory must account for every contracted load");
                        assertTrue(column.successful(), "Victory needs a delivery across this attempt and prior attempts");
                    }
                }
                JSONArray escortReports = new JSONArray();
                for (long escort : escorts) escortReports.put(new JSONObject().put("id",escort).put("type",sim.convoy().vehicleType(escort).name())
                        .put("rounds",sim.telemetry().roundsFired(escort)).put("hits",sim.telemetry().roundsHit(escort))
                        .put("damage",sim.telemetry().damageDealt(escort)).put("kills",sim.telemetry().kills(escort))
                        .put("finalRounds",sim.telemetry().roundsFired(escort)-finalRounds.getOrDefault(escort,sim.telemetry().roundsFired(escort)))
                        .put("finalDamage",sim.telemetry().damageDealt(escort)-finalDamage.getOrDefault(escort,sim.telemetry().damageDealt(escort)))
                        .put("structure",sim.convoy().structure(escort)).put("armor",sim.world().armor(escort))
                        .put("ammo",sim.convoy().turret(escort).ammo));
                if (fixture.optBoolean("playerDefend")) {
                    assertTrue(sim.getStoryBeats().stream().anyMatch(e -> e.id().endsWith("defend_order_placed")));
                }
                JSONArray fieldGuns = new JSONArray();
                if (companySize <= 48) assertEquals(0, sharedDefenseTargets,
                        "Automatic company screens and militia crew must not share their exact defense center");
                for (long gun : measuredGuns) fieldGuns.put(new JSONObject().put("id",gun)
                        .put("rounds",sim.telemetry().roundsFired(gun)).put("kills",sim.telemetry().kills(gun))
                        .put("rearRounds",sim.telemetry().roundsFired(gun)-rearGunRounds.getOrDefault(gun,sim.telemetry().roundsFired(gun)))
                        .put("alive",sim.world().isAlive(gun)));
                JSONArray beats=new JSONArray();
                for(var e:sim.getStoryBeats()) beats.put(new JSONObject().put("tick",e.tick()).put("id",e.id()));
                var report=new JSONObject().put("fixture",name).put("placeFight",places.json(sim, sim.getCommandTraceJsonLines())).put("reactiveDefence",ReactiveDefenceEvidence.figures(sim)).put("ticks",sim.getSimTickIndex()).put("landedTick",landedTick)
                        .put("result",sim.isComplete()?String.valueOf(sim.getWinner()):"UNRESOLVED").put("phase",column.phase())
                        .put("delivered",column.deliveredCount()).put("lost",column.lostCount()).put("namedSeats",seats)
                        .put("rearContactTicks",new JSONArray(List.of(rearContactTicks[0],rearContactTicks[1])))
                        .put("retreatTick",retreatTick).put("pressureWaves",column.pressure().sent()).put("rearStart",rearStart).put("openingRocketShots",openingRocketShots).put("advanceWreckTick",advanceWreckTick)
                        .put("supportGunsBuilt", column.supportGuns().size()).put("fieldGuns",fieldGuns)
                        .put("sharedAutomaticDefenseTargets", sharedDefenseTargets)
                        .put("pendingRaiders", column.pendingMembers())
                        .put("longestQuietSecondsByPhase", new JSONArray(List.of(
                                longestQuiet[0]/30f,longestQuiet[1]/30f,longestQuiet[2]/30f,longestQuiet[3]/30f)))
                        .put("combatTicksByPhase", new JSONArray(List.of(
                                combatTicks[0],combatTicks[1],combatTicks[2],combatTicks[3])))
                        .put("longestQuietEndSecondsByPhase", new JSONArray(List.of(
                                longestQuietEnd[0]/30f,longestQuietEnd[1]/30f,longestQuietEnd[2]/30f,longestQuietEnd[3]/30f)))
                        .put("advanceHaulerSurvives", sim.convoy().isTargetable(column.advanceHauler()))
                        .put("marinesAlive",count(sim,Faction.MARINE)).put("militiaAlive",militiaCount(sim)).put("raidersAlive",count(sim,Faction.DEFENDER))
                        .put("roadGunsDestroyedAt", new JSONObject(gunDown.entrySet().stream().collect(Collectors.toMap(e -> String.valueOf(e.getKey()), Map.Entry::getValue))))
                        .put("intactFreightBypass",bypass).put("freightRoadClear",freightRoadClear)
                        .put("kestrelLaunched", sim.getWaylineGunship().launched()).put("kestrelLost", sim.getWaylineGunship().lost())
                        .put("kestrelEncounter", kestrelEncounter)
                        .put("airWrecks", sim.getAirfieldService().groundWrecks().size())
                        .put("beats",beats).put("escorts",escortReports).put("firstFinalShot",firstFinalShot)
                        .put("holdDamageTaken", new JSONObject(soak)).put("holdDamageDealtByRaiders", new JSONObject(dealt))
                        .put("holdRaiderPositionsByX10", new JSONArray(raiderAt))
                        .put("holdMarinePositionsByX10", new JSONArray(marineAt))
                        .put("vehiclePushes", sim.vehicleRepulsion().pushes())
                        .put("driving", new JSONObject().put("reroutes", column.drivingCounters()[0])
                                .put("swerves", column.drivingCounters()[1]).put("stationWaitTicks", column.drivingCounters()[2])
                                .put("haulerSprints", column.drivingCounters()[3]));
                if(column.outcome()!=null) {
                    int paid=route.settle(mission.id,sim.getWinner()==Faction.MARINE,null,column.outcome());
                    assertTrue(paid>=0); assertEquals(-1,route.settle(mission.id,sim.getWinner()==Faction.MARINE,null,column.outcome()));
                    report.put("paid",paid).put("campaignStatus",route.status());
                    // The withdrawal needs a delivery to happen first, which the fight may not supply: a convoy lost
                    // whole is an ordinary defeat with nothing left waiting. Its ledger is asserted only once confirmed.
                    if(fixture.optBoolean("retreat") && retreatTick >= 0) {
                        assertEquals(0,paid);assertFalse(route.freight().waiting().isEmpty());
                        // A withdrawal leaves no trace on the ledger: the retry fields the whole column again.
                        assertEquals(0,route.freight().deliveredCount());
                        for(int carrier:route.freight().waiting()) assertTrue(column.cargo().containsKey(carrier));
                    }
                }
                Files.writeString(dir.resolve("summary.json"),report.toString(2)); reports.put(report);
                Files.writeString(output.resolve("summary.json"),reports.toString(2));
                System.out.println("[thunder-run] "+name+" "+report.getString("result")+" tick="+sim.getSimTickIndex()+" phase="+column.phase()
                        +" delivered="+column.deliveredCount()+" lost="+column.lostCount()+" marines="+count(sim,Faction.MARINE)
                        +" pushes="+sim.vehicleRepulsion().pushes()+" driving="+report.getJSONObject("driving"));
            }
        }
    }
    /** Every mobile raider's position, rounded to cells. */
    private static JSONArray raiderCells(BattleSimulation sim) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.DEFENDER || sim.identity().type(unit).isStatic()) continue;
            out.put(new JSONArray(List.of(Math.round(sim.world().x(unit)), Math.round(sim.world().y(unit)))));
        }
        return out;
    }

    /** Adds the fire {@code unit} absorbed since it was last seen to {@code key}. */
    private static void soakDelta(BattleSimulation sim, long unit, String key, Map<String, Float> soak, Map<Long, Float> lastTaken) {
        // Absorbed fire: what reached the body plus what its armor or screen refused.
        float taken = sim.telemetry().damageTaken(unit) + sim.telemetry().damageMitigated(unit);
        float before = lastTaken.getOrDefault(unit, 0f);
        if (taken > before) soak.merge(key, taken - before, Float::sum);
        lastTaken.put(unit, taken);
    }

    /** Names the militia gun bays and the technical, so a reader can find them among the raiders. */
    private static ReviewAnnotations crossroadsMarks(BattleSimulation sim, ThunderRunSequence column) {
        var marks = ReviewAnnotations.builder();
        var built = column.supportGuns();
        for (int post = 0; post < ThunderRunCompound.SUPPORT_POSTS.size(); post++) {
            var cell = ThunderRunCompound.SUPPORT_POSTS.get(post);
            boolean up = post < built.size() && sim.world().isAlive(built.get(post));
            marks.label(cell.x(), cell.y() - 3, (post == 0 ? "DISPATCH GUN" : "CROSSING GUN")
                    + (post < built.size() ? up ? "" : " / DESTROYED" : " / UNBUILT"), up ? ReviewStyle.LANDING : ReviewStyle.NOTE);
        }
        long technical = column.technical();
        if (sim.convoy().isVehicle(technical)) {
            var body = sim.convoy().body(technical);
            boolean wreck = sim.convoy().mission(technical).state == VehicleState.WRECKED;
            marks.label(body.x, body.y + 3, wreck ? "TECHNICAL / WRECKED"
                    : "TECHNICAL " + Math.round(sim.convoy().structure(technical)) + "/" + Math.round(sim.convoy().maxStructure(technical)),
                    wreck ? ReviewStyle.OBJECTIVE : ReviewStyle.LANDING);
        }
        return marks.build();
    }

    private static BufferedImage withHud(BufferedImage image,BattleSimulation sim) throws Exception {
        Reactor reactor=new Reactor();
        var model=new BattleHudOverlayModel(reactor,ignored->{},"Pause","1x","2x","4x");
        var column=sim.getThunderRun();
        model.update(1f,sim.getCompoundService().getRecords());
        model.updateFreight(column.phase(),column.cargoIdentities(),column.cargoStates(),column.deliveredCount(),column.lostCount());
        model.updateFreightHold(column.holdSite(), column.holdRemainingTicks());
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
    private static int militiaCount(BattleSimulation sim) {
        int n = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == Faction.ALLY && !sim.identity().type(unit).isTurret()) n++;
        }
        return n;
    }
    private static int count(BattleSimulation sim,Faction faction) {
        int n=0;for(int i=0;i<sim.liveUnitCount();i++) if(sim.identity().faction(sim.liveUnitAt(i))==faction)n++;return n;
    }
}
