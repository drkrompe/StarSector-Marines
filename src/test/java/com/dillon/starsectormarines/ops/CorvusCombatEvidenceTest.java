package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.battle.audio.StoryNarrationPlayer;
import com.dillon.starsectormarines.battle.audio.NarrationPlayer;
import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchOverlay;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.FiniteCommandPowerResources;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.setup.ColdAccountIntervention;
import com.dillon.starsectormarines.battle.setup.ColdAccountLayout;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.ops.detachment.PowerCatalog;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** Full production chapter playback; outcome is a finding, not a required victory. */
@Tag("corvus-command-evidence")
class CorvusCombatEvidenceTest {
    private StoryNarrationPlayer narration;
    @Test void recommendedCompanyAndRecoveredHound() throws Exception {
        assertEquals(Integer.MAX_VALUE, UnitUpdateSystem.configuredMinimumParallelUnits());
        Path output = Path.of(System.getProperty("corvus.command.evidence.outputDir", "build/reports/commander/corvus"));
        int boatCount = Integer.getInteger("battle.corvus.boats", 2);
        if (boatCount < 1 || boatCount > 4) throw new IllegalArgumentException("Use one to four company boats");
        if (boatCount != 2) output = output.resolve("boats-" + boatCount);
        String selectedChapter = System.getProperty("battle.corvus.chapter", "");
        if (!selectedChapter.isEmpty()) CorvusChapter.valueOf(selectedChapter);
        Files.createDirectories(output);
        TileRegistryTestInstaller.install(Path.of("."));
        InstalledVanillaData.install();
        int maxTicks = Integer.getInteger("corvus.command.evidence.maxTicks", 18000);
        if (maxTicks < 1) throw new IllegalArgumentException("maxTicks must be positive");
        int snapshotEveryTicks = Integer.getInteger("commander.evidence.snapshot.everyTicks", 1800);
        if (snapshotEveryTicks < 1) throw new IllegalArgumentException("snapshotEveryTicks must be positive");
        var progress = new CorvusWaylineProgress();
        var route = CorvusWaylineCampaignTest.closedRoute(2, true);
        var reports = new JSONArray();
        var renderer = new BattleReviewFrameRenderer(Path.of("mod"), 1920, 1080)
                .withImpactEffects().withUnitShadows();
        for (CorvusChapter chapter : CorvusChapter.values()) {
            Mission mission = CorvusWaylineCampaign.offer(progress, chapter.planetName, route);
            if (!selectedChapter.isEmpty() && !chapter.name().equals(selectedChapter)) {
                assertTrue(progress.begin(mission.id, chapter.planetName));
                progress.settle(mission.id, chapter.planetName, true, chapter.payment);
                continue;
            }
            Path dir = output.resolve(chapter.name().toLowerCase(Locale.ROOT));
            Files.createDirectories(dir);
            var roster = new MarineRoster();
            roster.ensureActiveSoldiers(chapter.squads * 12);
            assertTrue(roster.mechBay().receiveRecoveredMech(CampaignMech.fromDeployment(WaylineHound.ID, WaylineHound.deployment())));
            var selected = roster.squads().stream().filter(s -> !s.reserve()).map(s -> s.id()).collect(Collectors.toSet());
            for (String squad : selected) assertEquals(SquadEquipmentResult.APPLIED,
                    roster.applySquadEquipment(squad, SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,
                            SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR));
            // Same campaign organization path; each squad has an acting NCO, with no officer buffs.
            var deployment = CampaignMarineDeployment.freezeSelection(roster, selected, chapter.squads * 12, null);
            var boats = IntStream.range(0, boatCount).mapToObj(i -> FittedBoat.standard(ShuttleType.AEROSHUTTLE)).toList();
            var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(mission, boats, deployment.size());
            var fixture = chapter.fixture(mission.battleSeed, manifest, chapter.profile(chapter.surface));
            var powers = PowerCatalog.resolve(List.of(), mission, roster.mechBay().activeDeployment());
            assertEquals(1, powers.size());
            var resources = new FiniteCommandPowerResources(30);
            fixture = BattleFixtureJson.fromJson(BattleFixtureJson.toJson(fixture));
            try (var sim = fixture.build();
                 var damageReview = Boolean.getBoolean("battle.corvus.damageReview") && sim.getColdAccount() != null
                         ? new CompoundDamageReview(sim, dir) : null) {
                var dialogue = new JSONArray();
                double[] playbackSeconds = {0d};
                narration = new StoryNarrationPlayer(WaylineNarration.parse(new JSONObject(
                        Files.readString(Path.of("mod", WaylineNarration.PATH)))),
                        WaylineNarration.storyMissionId(mission), new NarrationPlayer(line -> null), line -> {
                            try { dialogue.put(new JSONObject().put("tick", sim.getSimTickIndex())
                                    .put("playbackSeconds", playbackSeconds[0]).put("id", line.id())
                                    .put("event", line.event()).put("speaker", line.speaker()).put("text", line.text())); }
                            catch (Exception e) { throw new IllegalStateException(e); }
                        });
                var overlay = BattleLaunchOverlay.capture(0, deployment, FlybyRoster.EMPTY, FlybyRoster.EMPTY, powers,
                        resources.availableSupplies(), mission.fieldPresencePolicy);
                overlay.applyTo(sim, resources);
                Files.writeString(dir.resolve("fixture.json"), BattleFixtureJson.toJson(new BattleLaunchFixture(fixture, overlay)).toString(2));
                sim.setCommandTraceEnabled(true, fixture.kind());
                Set<Long> landed = new HashSet<>(), fallen = new HashSet<>(), enemyFallen = new HashSet<>();
                var marineDeaths = new JSONArray();
                sim.subscribeDeath(event -> {
                    long id = event.unitId();
                    if (sim.identity().faction(id) == Faction.DEFENDER) enemyFallen.add(id);
                    else if (sim.identity().faction(id) == Faction.MARINE && sim.identity().campaignSquadId(id) != null) fallen.add(id);
                    if (sim.identity().faction(id) == Faction.MARINE) try {
                        marineDeaths.put(new JSONObject().put("tick", sim.getSimTickIndex()).put("id", id)
                                .put("type", sim.identity().type(id)).put("x", event.x()).put("y", event.y())
                                .put("heavyImpacts", sim.getHeavyImpactsThisFrame()));
                        Files.writeString(dir.resolve("marine-deaths.json"), marineDeaths.toString(2));
                    } catch (Exception e) { throw new IllegalStateException(e); }
                });
                int initialDefenders = count(sim, Faction.DEFENDER);
                Set<Long> approachGuards = new HashSet<>();
                if (sim.getColdAccount() != null) for (int i = 0; i < sim.liveUnitCount(); i++) {
                    long id = sim.liveUnitAt(i);
                    var squad = sim.squadOf(id);
                    if (squad != null && squad.assignedNode != null
                            && squad.assignedNode.kind == TacticalNode.Kind.FORWARD_BUNKER
                            && squad.assignedNode.anchorX > sim.getColdAccount().installationRight())
                        approachGuards.add(id);
                }
                int approachFirstShotAt = -1, approachShots = 0, approachDeathsAtEvidence = -1;
                int requestedAt = -1, houndLandedAt = -1, firstLandedAt = -1, allLandedAt = -1, firstShotAt = -1;
                long hound = -1;
                int houndShots = 0, houndKills = 0;
                float houndDamage = 0, houndHp = 0, houndArmor = 0;
                boolean houndAlive = false;
                int houndLostAt = -1, infantryDeathsBeforeBombardment = -1;
                int firstSalvoMounts = 0, firstCapitalShotAt = -1;
                int peakInfantryPickups = 0, peakLandedPickups = 0;
                boolean pickupFrameWritten = false;
                var pickupAircraft = new HashSet<Long>();
                var pickupFlights = new JSONArray();
                int recoveryTick = -1, enemyDeathsBeforeLanding = -1;
                boolean pickupCompleteAtPatrol = false;
                var withdrawalDurability = new HashMap<Long, Float>();
                float marineDurabilityLostAfterFirstSalvo = 0f;
                Warship departingCapital = null;
                var frameTicks = new JSONArray().put(0);
                var samples = new JSONArray();
                writeFrame(renderer, sim, chapter, dir);
                while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                    var service = sim.getCommandPowerService();
                    var support = service.getPower(MechSupport.ID);
                    if (requestedAt < 0 && service.canActivate(support)) {
                        for (var pad : sim.getMarineLandingPads()) if (support.canTarget(pad.centerX, pad.centerY, sim)) {
                            service.requestActivation(MechSupport.ID, pad.centerX, pad.centerY);
                            requestedAt = sim.getSimTickIndex() + 1;
                            break;
                        }
                    }
                    var beforePhase = sim.getColdAccount() == null ? null : sim.getColdAccount().phase();
                    int beforeShipShots = sim.getColdAccount() == null ? 0 : sim.getColdAccount().shotsFired();
                    var beforeNarration = narration.current();
                    sim.advance(BattleSimulation.TICK_DT);
                    if (damageReview != null) damageReview.tick();
                    playbackSeconds[0] += BattleSimulation.TICK_DT / replaySpeed(sim);
                    narration.advance(BattleSimulation.TICK_DT / replaySpeed(sim),
                            sim.getPlayerStoryBeatsAfter(narration.cursor()), false, sim.isComplete());
                    if (sim.getColdAccount() != null && sim.getColdAccount().shotsFired() > beforeShipShots) {
                        if (beforeShipShots == 0) {
                            firstCapitalShotAt = sim.getSimTickIndex();
                            firstSalvoMounts = (int) sim.warships().ships().stream().flatMap(s -> s.mounts().stream())
                                    .filter(m -> !m.pointDefence() && m.shotsFired > 0).count();
                            assertTrue(firstSalvoMounts >= 2, "The attack opens with a coordinated heavy salvo");
                            assertTrue(firstCapitalShotAt <= sim.getColdAccount().groundClearTick() + 2,
                                    "Ready batteries release immediately on clearance");
                            infantryDeathsBeforeBombardment = fallen.size();
                            assertFalse(sim.survivorPickup().complete(sim), "Bombardment begins before the last lift returns");
                        }
                        assertTrue(sim.getColdAccount().evidenceTick() > 0);
                        assertEquals(0, sim.getColdAccount().outsideWithdrawal());
                        assertTrue(sim.getSimTickIndex() - sim.getColdAccount().warningTick() >= ColdAccountIntervention.WARNING_TICKS);
                    }
                    if (sim.getColdAccount() != null) {
                        for (long air : sim.getAirEntityIds()) {
                            var flight = sim.world().mission(air);
                            if (flight == null || sim.world().airFaction(air) != Faction.MARINE) continue;
                            assertTrue(flight.entryX > sim.getGrid().getWidth(), "Company transports enter beyond the actual east edge");
                            assertTrue(flight.exitX > sim.getGrid().getWidth(), "Company transports return east");
                            assertEquals(flight.lzY, flight.entryY, .01f, "Inbound route aligns with the landing pad");
                            if (flight.state == ShuttleState.INCOMING)
                                assertTrue(sim.world().kinematics(air).x >= flight.lzX - 1f,
                                        "Inbound transports approach from east, without sweeping across the defenders");
                        }
                        if (sim.getColdAccount().companyRecovered() && recoveryTick < 0)
                            recoveryTick = sim.getSimTickIndex();
                        if (sim.getColdAccount().patrolTick() == sim.getSimTickIndex()) {
                            pickupCompleteAtPatrol = sim.survivorPickup().complete(sim);
                            assertTrue(sim.warships().shells().isEmpty() && sim.warships().strikes().isEmpty(),
                                    "The demolition's last impacts precede the patrol report");
                        }
                        if (!sim.getColdAccount().companyRecovered())
                            assertFalse(sim.getColdAccount().isComplete(), "Ship departure cannot settle an unfinished pickup");
                        var layout = sim.getColdAccount().layout();
                        for (var capital : sim.warships().ships()) {
                            if (sim.getColdAccount().patrolTick() >= 0) {
                                departingCapital = capital;
                                assertTrue(capital.departing());
                                assertEquals(beforeShipShots, sim.getColdAccount().shotsFired(), "Patrol fires no new rounds");
                                assertEquals(sim.getColdAccount().firingHeading(), capital.facingDegrees, .02f,
                                        "Departure continues nose-first on the firing bearing");
                                assertFalse(sim.isComplete(), "Let the ship leave before mission completion");
                                continue;
                            }
                            assertTrue(capital.x - Math.hypot(capital.lengthCells, capital.widthCells) / 2f
                                    > sim.getColdAccount().installationRight(), "The full hull must stay east of the compound");
                            assertEquals(layout.strikeY(), capital.y, .01f, "Arrival follows a straight east-west line");
                            if (capital.isMoving()) assertEquals(90f, capital.facingDegrees, .01f, "Approach must be nose-first");
                            if (sim.getColdAccount().bombardTick() >= 0) {
                                assertTrue(sim.getColdAccount().preparedTick() <= sim.getColdAccount().groundClearTick(),
                                        "Batteries prepared=" + sim.getColdAccount().preparedTick() + " clear=" + sim.getColdAccount().groundClearTick());
                                assertEquals(sim.getColdAccount().firingHeading(), capital.facingDegrees, .01f,
                                        "The firing hull holds the bearing established during withdrawal");
                                if (sim.getSimTickIndex() - sim.getColdAccount().bombardTick() < 60)
                                    assertTrue(capital.mounts().stream().filter(m -> !m.pointDefence() && m.shotsFired > 0)
                                            .allMatch(m -> m.weapon.damagePerShot() >= 100f), "Heavy batteries open the attack");
                            }
                        }
                    }
                    int tick = sim.getSimTickIndex();
                    int infantryPickups = 0, landedPickups = 0;
                    var flyingBoats = new ArrayList<FittedBoat>();
                    for (long air : sim.getAirEntityIds()) {
                        var pickup = sim.world().mission(air);
                        if (pickup == null || pickup.pickup == null || pickup.state == ShuttleState.GONE) continue;
                        var frame = sim.world().airframe(air);
                        if (frame instanceof FittedBoat boat) {
                            // Fixture boats without campaign ids compare equal by fit; their object identities remain distinct.
                            assertTrue(flyingBoats.stream().noneMatch(b -> b == boat), "One physical boat cannot fly two pickups");
                            flyingBoats.add(boat);
                            infantryPickups++;
                            if (pickup.state == ShuttleState.LANDED) landedPickups++;
                        }
                        if (pickupAircraft.add(air)) pickupFlights.put(new JSONObject().put("entity", air)
                                .put("tick", tick).put("frame", frame.toString())
                                .put("x", pickup.lzX).put("y", pickup.lzY));
                    }
                    peakInfantryPickups = Math.max(peakInfantryPickups, infantryPickups);
                    peakLandedPickups = Math.max(peakLandedPickups, landedPickups);
                    if (landedPickups >= 2 && !pickupFrameWritten) {
                        writeFrame(renderer, sim, chapter, dir);
                        frameTicks.put(tick);
                        var pickupArea = sim.getColdAccount().layout();
                        var close = new BattleReviewFrameRenderer(Path.of("mod"), 1920, 1080,
                                pickupArea.withdrawalX(), pickupArea.withdrawalY(), 65).withUnitShadows();
                        ImageIO.write(close.render(sim, "A Cold Account / concurrent survivor pickup / "
                                + tick + " ticks / 1920x1080, 100% UI scale"), "PNG",
                                dir.resolve("concurrent-pickup.png").toFile());
                        pickupFrameWritten = true;
                    }
                    for (int i = 0; i < sim.liveUnitCount(); i++) {
                        long id = sim.liveUnitAt(i);
                        if (sim.identity().faction(id) != Faction.MARINE) continue;
                        float durability = sim.world().hp(id) + sim.world().armor(id);
                        Float previousDurability = withdrawalDurability.put(id, durability);
                        if (firstCapitalShotAt >= 0 && previousDurability != null)
                            marineDurabilityLostAfterFirstSalvo += Math.max(0f, previousDurability - durability);
                        if (sim.identity().type(id) == UnitType.HEAVY_MECH) {
                            if (hound < 0) {
                                hound = id; houndLandedAt = tick;
                                assertEquals(WaylineHound.NAME, sim.identity().name(id));
                                sim.getEntityWorld().addComponent(id, sim.getBattleComponents().TELEMETRY);
                            }
                            assertEquals(hound, id, "Exactly one owned Hound must land");
                        } else if (sim.identity().campaignSquadId(id) != null) landed.add(id);
                    }
                    if (!landed.isEmpty() && firstLandedAt < 0) {
                        firstLandedAt = tick;
                        enemyDeathsBeforeLanding = enemyFallen.size();
                    }
                    if (landed.size() == deployment.size() && allLandedAt < 0) allLandedAt = tick;
                    for (var shot : sim.getShotsThisFrame()) {
                        if (firstShotAt < 0) firstShotAt = tick;
                        if (shot.shooterId == hound) houndShots++;
                        if (approachGuards.contains(shot.shooterId)) {
                            if (approachFirstShotAt < 0) approachFirstShotAt = tick;
                            approachShots++;
                        }
                    }
                    if (sim.getColdAccount() != null && sim.getColdAccount().evidenceTick() == tick)
                        approachDeathsAtEvidence = (int) approachGuards.stream().filter(enemyFallen::contains).count();
                    houndAlive = hound >= 0 && (sim.world().isAlive(hound) || sim.survivorPickup().surviving(hound));
                    if (hound >= 0 && !houndAlive && houndLostAt < 0) houndLostAt = tick;
                    if (houndAlive && sim.world().isAlive(hound)) {
                        houndHp = sim.world().hp(hound); houndArmor = sim.world().armor(hound);
                        houndKills = sim.telemetry().kills(hound); houndDamage = sim.telemetry().damageDealt(hound);
                    }
                    if (tick % 300 == 0 || sim.isComplete() || tick == maxTicks) {
                        samples.put(new JSONObject().put("tick", tick).put("landed", landed.size()).put("fallen", fallen.size())
                                .put("liveMarines", count(sim, Faction.MARINE)).put("liveDefenders", count(sim, Faction.DEFENDER))
                                .put("houndShots", houndShots).put("houndAlive", houndAlive).put("objectives", objectives(sim)));
                        Files.writeString(dir.resolve("samples.json"), samples.toString(2));
                    }
                    boolean phaseChanged = sim.getColdAccount() != null && sim.getColdAccount().phase() != beforePhase;
                    int frameStride = sim.getColdAccount() != null && sim.getColdAccount().evidenceTick() < 0
                            ? Math.max(60, snapshotEveryTicks) : snapshotEveryTicks;
                    if (tick % frameStride == 0 || phaseChanged || narration.current() != beforeNarration
                            || sim.isComplete() || tick == maxTicks) {
                        writeFrame(renderer, sim, chapter, dir);
                        frameTicks.put(tick);
                    }
                    if (tick % 1800 == 0 || phaseChanged || sim.isComplete() || tick == maxTicks) {
                        Files.writeString(dir.resolve(String.format(Locale.ROOT,"units-%05d.json",tick)), units(sim).toString(2));
                        System.out.println("[corvus] " + chapter.title + " tick=" + tick + " landed=" + landed.size()
                                + " fallen=" + fallen.size() + " defenders=" + count(sim,Faction.DEFENDER)
                                + (sim.getColdAccount() == null ? "" : " " + sim.getColdAccount().displayName()));
                    }
                }
                if (!sim.isComplete()) sim.recordCommandTraceTimeout(maxTicks);
                if (hound >= 0 && !sim.survivorPickup().surviving(hound)) {
                    houndKills = sim.telemetry().kills(hound);
                    houndDamage = sim.telemetry().damageDealt(hound);
                }
                Files.writeString(dir.resolve("trace.jsonl"), sim.getCommandTraceJsonLines());
                Files.writeString(dir.resolve("frame-index.json"), frameTicks.toString());
                var kits = new JSONArray();
                for (var seat : deployment.commitments()) kits.put(new JSONObject().put("soldier", seat.campaignSoldierId())
                        .put("weapon", seat.primaryWeaponId()).put("grade", seat.equipmentGrade().name())
                        .put("armorCapacity", seat.armorCapacity()).put("armorRating", seat.armorRating()));
                var report = new JSONObject().put("chapter", chapter.title).put("seed",mission.battleSeed)
                        .put("companyBoats",boatCount).put("captainBonuses",false)
                        .put("weapons",SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS)
                        .put("armor",SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR)
                        .put("result", sim.isComplete() ? String.valueOf(sim.getWinner()) : "TIMEOUT")
                        .put("ticks",sim.getSimTickIndex()).put("namedSeats",deployment.size()).put("landed",landed.size())
                        .put("infantryDeaths",fallen.size()).put("enemyDeaths",enemyFallen.size()).put("initialDefenders",initialDefenders)
                        .put("remainingDefenders", count(sim,Faction.DEFENDER)).put("firstLandedAt",firstLandedAt)
                        .put("allLandedAt",allLandedAt).put("firstShotAt",firstShotAt).put("supportRequestedAt",requestedAt)
                        .put("supportChargesRemaining",sim.getCommandPowerService().getChargesRemaining(MechSupport.ID))
                        .put("suppliesSpent",30-resources.availableSupplies()).put("houndLandedAt",houndLandedAt)
                        .put("houndAlive",houndAlive).put("houndLostAt",houndLostAt).put("houndHp",houndAlive?houndHp:0).put("houndArmor",houndAlive?houndArmor:0)
                        .put("houndShots",houndShots).put("houndKills",houndKills).put("houndDamage",houndDamage)
                        .put("objectives",objectives(sim)).put("kits",kits);
                report.put("dialogue", dialogue);
                var intervention = sim.getColdAccount();
                if (intervention != null) {
                    report.put("intervention", new JSONObject()
                            .put("phase", intervention.phase().name()).put("evidenceTick", intervention.evidenceTick())
                            .put("approachGuards", approachGuards.size()).put("approachFirstShotAt", approachFirstShotAt)
                            .put("approachShots", approachShots).put("approachDeathsAtEvidence", approachDeathsAtEvidence)
                            .put("warningTick", intervention.warningTick()).put("bombardTick", intervention.bombardTick())
                            .put("preparedTick", intervention.preparedTick()).put("groundClearTick", intervention.groundClearTick())
                            .put("clearanceRadius", intervention.layout().strikeRadius() + ColdAccountLayout.BLAST_MARGIN)
                            .put("marineDurabilityLostAfterFirstSalvo", marineDurabilityLostAfterFirstSalvo)
                            .put("patrolTick", intervention.patrolTick()).put("departedTick", intervention.departedTick())
                            .put("pickupCompleteAtPatrol", pickupCompleteAtPatrol).put("recoveryTick", recoveryTick)
                            .put("enemyDeathsBeforeLanding", enemyDeathsBeforeLanding)
                            .put("firstCapitalShotAt", firstCapitalShotAt).put("firstSalvoMounts", firstSalvoMounts)
                            .put("initialWalls", intervention.initialWalls()).put("remainingWalls", intervention.remainingWalls())
                            .put("shots", intervention.shotsFired()).put("outsideWithdrawal", intervention.outsideWithdrawal())
                            .put("pendingArrivals", intervention.pendingArrivals())
                            .put("firingHeading", Float.isFinite(intervention.firingHeading())
                                    ? intervention.firingHeading() : JSONObject.NULL).put("demolitionSector", intervention.demolitionSector())
                            .put("pickedUp", sim.survivorPickup().recoveredCount())
                            .put("pickupSorties", sim.survivorPickup().manifests().size())
                            .put("peakInfantryPickups", peakInfantryPickups).put("peakLandedPickups", peakLandedPickups)
                            .put("pickupFlights", pickupFlights)
                            .put("infantryDeathsBeforeBombardment", infantryDeathsBeforeBombardment)
                            .put("infantryDeathsAfterFirstSalvo", firstCapitalShotAt < 0 ? 0 : fallen.size() - infantryDeathsBeforeBombardment));
                    if (intervention.isComplete()) {
                        assertEquals(0, enemyDeathsBeforeLanding, "Delivery must not pre-clear the ground fight");
                        assertTrue(intervention.companyRecovered());
                        assertTrue(intervention.patrolTick() < recoveryTick,
                                "Recommended force: Resolute resumes patrol while survivor pickup is still running");
                        assertTrue(sim.survivorPickup().complete(sim));
                        assertEquals(deployment.size() - fallen.size(), sim.survivorPickup().manifests().stream()
                                .flatMap(m -> m.passengers().stream()).filter(p -> p.soldierId() != null).count());
                        // Pirates outside the strike area can still fight during withdrawal.
                        // Casualties are findings; clearance and recovery are the mission contracts.
                        assertTrue(intervention.remainingWalls() <= intervention.initialWalls() / 4);
                        assertTrue(sim.warships().shells().isEmpty());
                        assertTrue(intervention.departedTick() > intervention.patrolTick());
                        assertTrue(sim.getSimTickIndex() - intervention.departedTick() >= ColdAccountIntervention.AFTERMATH_TICKS);
                        assertTrue(sim.warships().ships().isEmpty(), "Resolute has physically left the scene");
                        assertNotNull(departingCapital);
                        float margin = departingCapital.lengthCells / 2f;
                        assertTrue(departingCapital.x < -margin || departingCapital.y < -margin
                                || departingCapital.x > sim.getGrid().getWidth() + margin
                                || departingCapital.y > sim.getGrid().getHeight() + margin,
                                "The full hull clears the map, rather than timing out");
                        assertTrue(dialogue.toString().contains("corvus.cold_account.stronghold_destroyed"),
                                "The patrol report plays before completion");
                    }
                }
                Files.writeString(dir.resolve("summary.json"),report.toString(2));
                reports.put(report);
                Files.writeString(output.resolve("summary.json"),reports.toString(2));
                writeSummary(output, reports, boatCount);
                assertTrue(houndLandedAt > 0, "Must actually deliver the recovered chassis");
                assertEquals(0, sim.getCommandPowerService().getChargesRemaining(MechSupport.ID));
            }
            // Independent force baselines: advance the offer even if the preceding playback lost.
            assertTrue(progress.begin(mission.id, chapter.planetName));
            progress.settle(mission.id, chapter.planetName, true, chapter.payment);
        }
    }

    private static JSONArray objectives(BattleSimulation sim) throws Exception {
        var result = new JSONArray();
        for (var objective : sim.getObjectives()) {
            var row = new JSONObject().put("name",objective.displayName()).put("faction",objective.owningFaction().name())
                    .put("complete",objective.isComplete()).put("failed",objective.isFailed());
            if (objective instanceof ChargeSiteObjective site) row.put("progress",site.progress());
            if (objective instanceof RaidObjective raid) row.put("phase",raid.phase().name())
                    .put("targetX",raid.targetCellX()).put("targetY",raid.targetCellY()).put("progress",raid.serviceProgress());
            result.put(row);
        }
        return result;
    }

    private static void writeSummary(Path output, JSONArray reports, int boats) throws Exception {
        var text = new StringBuilder("# Corvus combat evidence\n\n")
                .append("Independent full-strength companies; Frontier Security Equipment and Frontier Patchwork Protection; ")
                .append("acting NCOs without officer bonuses; ").append(boats).append(" standard Aeroshuttles. ")
                .append("One recovered Wayward Hound with nose chaingun and empty missile mounts. ")
                .append("Normal support activation at the first legal pad, then autonomous command. ")
                .append("Forced-serial headless production simulation; no live campaign or player-control acceptance.\n\n")
                .append("| Chapter | Result | Seconds | Named infantry lost | Hound | Hound kills |\n")
                .append("|---|---|---:|---:|---|---:|\n");
        for (int i = 0; i < reports.length(); i++) {
            var row = reports.getJSONObject(i);
            text.append("| ").append(row.getString("chapter")).append(" | ").append(row.getString("result"))
                    .append(" | ").append(String.format(Locale.ROOT,"%.1f",row.getInt("ticks") / 30.0))
                    .append(" | ").append(row.getInt("infantryDeaths")).append('/').append(row.getInt("namedSeats"))
                    .append(" | ").append(row.getBoolean("houndAlive") ? "Survived" : "Destroyed")
                    .append(" | ").append(row.getInt("houndKills")).append(" |\n");
        }
        text.append("\nTIMEOUT means unresolved at the observation limit, not defeat. ")
                .append("Infantry losses are battlefield deaths, before campaign medical settlement. ")
                .append("The evidence task succeeding verifies the instrumentation and delivery, not acceptable difficulty.\n");
        Files.writeString(output.resolve("summary.md"),text);
    }

    private static float replaySpeed(BattleSimulation sim) {
        // Cold Account has mission speech throughout the approach as well as the intervention.
        return sim.getColdAccount() == null ? 8f : 1f;
    }

    private void writeFrame(BattleReviewFrameRenderer renderer, BattleSimulation sim, CorvusChapter chapter, Path dir) throws Exception {
        var frame = renderer.render(sim, chapter.title + " / " + sim.getSimTickIndex()/30 + "s"
                + (sim.getColdAccount() == null ? "" : " / " + sim.getColdAccount().displayName()));
        var line = narration == null ? null : narration.current();
        if (line != null) {
            var g = frame.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(8, 13, 22, 240)); g.fillRoundRect(24, frame.getHeight() - 146, 1400, 122, 12, 12);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20)); g.setColor(new Color(231, 187, 109));
            g.drawString(line.speaker(), 44, frame.getHeight() - 115);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 23)); g.setColor(Color.WHITE);
            String row = ""; int y = frame.getHeight() - 78;
            for (String word : line.text().split(" ")) {
                if (g.getFontMetrics().stringWidth(row + word) > 1350) {
                    g.drawString(row, 44, y); y += 31; row = "";
                }
                row += word + " ";
            }
            g.drawString(row, 44, y); g.dispose();
        }
        ImageIO.write(frame, "png",
                dir.resolve(String.format(Locale.ROOT,"frame-%05d.png",sim.getSimTickIndex())).toFile());
    }

    private static int count(BattleSimulation sim, Faction side) {
        int result = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) if (sim.identity().faction(sim.liveUnitAt(i)) == side) result++;
        return result;
    }

    /** Neutral diagnostic reads only; no path or command is installed by this observer. */
    private static JSONArray units(BattleSimulation sim) throws Exception {
        var rows = new JSONArray();
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) != Faction.MARINE) continue;
            var squad = sim.squadOf(id);
            var row = new JSONObject().put("id", id).put("name",sim.identity().name(id))
                    .put("type",sim.identity().type(id).name()).put("x",sim.world().x(id)).put("y",sim.world().y(id))
                    .put("hp",sim.world().hp(id)).put("armor",sim.world().armor(id))
                    .put("role",sim.role().role(id).name()).put("settled",sim.movement().settled(id))
                    .put("pathRemaining",Paths.cellCount(sim.world().path(id))-sim.world().pathIdx(id));
            if (sim.world().hasMechLoadout(id)) {
                var route = sim.movement().continuousRoute(id);
                row.put("mechArrivedLatch", sim.world().mechLoadout(id).assignmentBoundaryReached);
                if (route != null) row.put("routeRequestedX", route.requestedCellX())
                        .put("routeRequestedY", route.requestedCellY()).put("routeEndX", route.endX())
                        .put("routeEndY", route.endY()).put("routeCompleted", route.completed());
            }
            if (squad != null) {
                row.put("contactPicture", squad.contactPicture.toString())
                        .put("advanceCommitted", squad.advanceEngageCommitted)
                        .put("advanceThreat", squad.advanceThreatId)
                        .put("target", sim.targetOf(id));
                row.put("squad",squad.id).put("goal",squad.currentGoal == null ? "NONE" : squad.currentGoal.name());
                var step = squad.currentPlan == null ? null : squad.currentPlan.currentStep();
                row.put("action",step == null ? "NONE" : step.action.name());
                if (step != null) row.put("slot",step.slotOf(id));
                var assignment = squad.assignmentForExecution();
                if (assignment != null) {
                    row.put("assignment",assignment.kind().name()).put("targetX",assignment.targetCellX()).put("targetY",assignment.targetCellY());
                    if (assignment.targetCellX() >= 0 && assignment.targetCellY() >= 0)
                        row.put("infantryRouteCells", Paths.cellCount(GridPathfinder.findPath(sim.getGrid(),
                                sim.world().cellX(id),sim.world().cellY(id),assignment.targetCellX(),assignment.targetCellY())));
                }
            }
            rows.put(row);
        }
        return rows;
    }
}
