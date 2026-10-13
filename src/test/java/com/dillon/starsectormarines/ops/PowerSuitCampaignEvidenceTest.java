package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.SquadFormUpSystem;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.marine.ArmorClass;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.OfficerCommandProfile;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in detached issue, ordinary lift, combat, personnel writeback and serialization evidence. */
@Tag("power-suit-campaign-evidence")
class PowerSuitCampaignEvidenceTest {
    @Test void mixedNamedSquadReturnsThroughPersonnelWritebackAndDetachedSerialization() throws Exception {
        assertNull(Global.getSector(), "This instrument must never attach to a live campaign");
        HeadlessArmoryPreviewRenderer.installCatalogs(Path.of("mod"));
        Path output = Path.of(System.getProperty("powersuit.campaign.evidence.outputDir",
                "build/reports/commander/power-suit-campaign"));
        Files.createDirectories(output);
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(12);
        var squad = roster.squads().stream().filter(s -> !s.reserve()).findFirst().orElseThrow();
        MarineCaptain officer = new MarineCaptain("Detached suit commander", null, Rank.CAPTAIN, 0f);
        roster.add(officer);
        assertTrue(roster.assignCaptainToSquads(officer.id(), List.of(squad.id())).valid());
        var weapons = roster.armory().weaponDoctrineById(SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS);
        // Explicit fixture acquisition exercises ordinary ownership/issue without inventing a story reward.
        for (var issue : weapons.issues()) {
            roster.armory().acquireEquipmentTemplate(EquipmentTemplateCatalog.primaryId(issue.primaryId(), issue.grade()));
            if (issue.specialEquipmentId() != null) roster.armory().acquireEquipmentTemplate(
                    EquipmentTemplateCatalog.specialId(issue.specialEquipmentId()));
        }
        for (String id : List.of("armor.specter-recovered", "armor.arbalest-recovered"))
            roster.armory().acquireEquipmentTemplate(EquipmentTemplateCatalog.armorId(id));
        var armor = roster.armory().armorDoctrineById(SquadEquipmentDoctrines.SALVAGE_ASSISTED_ARMOR);
        assertNotNull(armor);
        Set<String> named = Set.copyOf(squad.memberIds());
        assertEquals(SquadEquipmentResult.APPLIED, roster.applySquadEquipment(squad.id(), weapons.id(), armor.id()));
        assertEquals(named, Set.copyOf(squad.memberIds()));
        assertEquals(2, roster.squadMembers(squad).stream().filter(s -> "armor.specter-recovered".equals(s.armorId())).count());
        assertEquals(2, roster.squadMembers(squad).stream().filter(s -> "armor.arbalest-recovered".equals(s.armorId())).count());
        assertEquals(8, roster.squadMembers(squad).stream().filter(s -> s.armorDef().armorClass() == ArmorClass.STANDARD).count());
        JSONObject issued = personnel(roster, named);
        var deployment = CampaignMarineDeployment.freezeSelection(roster, Set.of(squad.id()), 12, officer);
        assertEquals(12, deployment.size());
        Map<String, MarineLoadout> seats = new HashMap<>();
        for (int i = 0; i < deployment.size(); i++) {
            var seat = deployment.seat(i);
            var issuedArmor = roster.soldierById(seat.campaignSoldierId).armorDef();
            assertEquals(issuedArmor.armorClass(), seat.armorClass);
            assertEquals(issuedArmor.appearanceFamily(), seat.armorFamily);
            assertEquals(issuedArmor.integralSystem(), seat.integralSystem);
            seats.put(seat.campaignSoldierId, seat);
        }
        var route = new BrokenSupplyRouteProgress("jangala", "Jangala", "Corvus");
        Mission mission = SupplyRouteCampaign.offers(route).get(0);
        var boat = FittedBoat.standard(ShuttleType.AEROSHUTTLE);
        var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(mission, List.of(boat), 12);
        JSONObject report = new JSONObject().put("scope", "detached production personnel loop")
                .put("acquisition", "explicit detached fixture cards; campaign reward milestone unverified")
                .put("liveSaveReloadVerified", false).put("hostSettlementReceiptVerified", false)
                .put("campaignMutation", false).put("issued", issued);
        try (var sim = MissionLaunch.supplyRouteBuilder(route, 9301L, manifest, TargetProfile.NEUTRAL).get()) {
            Map<Long, MarineLoadout[]> employer = new HashMap<>();
            for (long air : sim.getAirEntityIds()) if (sim.world().airFaction(air) != Faction.MARINE)
                employer.put(air, sim.world().mission(air).marineLoadout);
            deployment.applyToAuthoredPlayerLift(sim);
            sim.setOperationOfficer(OfficerCommandProfile.capture(officer));
            Set<String> scheduled = new HashSet<>();
            int cycles = 0;
            for (long air : sim.getAirEntityIds()) {
                var lift = sim.world().mission(air);
                if (sim.world().airFaction(air) != Faction.MARINE) {
                    assertSame(employer.get(air), lift.marineLoadout);
                    continue;
                }
                assertSame(boat, sim.world().airframe(air));
                for (var cycle : lift.cycleLoadouts) {
                    cycles++;
                    for (var seat : cycle) assertTrue(scheduled.add(seat.campaignSoldierId));
                }
            }
            assertEquals(named, scheduled);
            assertEquals(2, cycles, "Twelve wearers use two ordinary six-seat trips");
            Set<String> landed = new HashSet<>();
            Map<String, Long> landedEntities = new HashMap<>();
            Set<Integer> battleSquads = new HashSet<>();
            int maxTicks = Integer.getInteger("powersuit.campaign.evidence.maxTicks", 9000);
            while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                sim.advance(BattleSimulation.TICK_DT);
                for (int i = 0; i < sim.liveUnitCount(); i++) {
                    long unit = sim.liveUnitAt(i);
                    if (sim.identity().faction(unit) != Faction.MARINE) continue;
                    assertEquals(UnitType.MARINE, sim.identity().type(unit), "Issue must not create Mech Support");
                    String id = (String) sim.getEntityWorld().getObject(unit, sim.getBattleComponents().IDENTITY,
                            BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID);
                    assertTrue(named.contains(id));
                    if (!landed.add(id)) continue;
                    landedEntities.put(id, unit);
                    var seat = seats.get(id);
                    assertEquals(seat.armorClass, sim.identity().armorClass(unit));
                    assertEquals(seat.integralSystem, sim.integralSystems().spec(unit));
                    assertEquals(seat.armorFamily.ordinal(), sim.getEntityWorld().getInt(unit,
                            sim.getBattleComponents().LAYERED_ANIMATION, BattleComponents.LAYERED_BODY_FAMILY));
                    assertEquals(seat.campaignSquad.fireTeamIndex, sim.squad().fireTeamIndex(unit));
                    assertEquals(squad.id(), sim.identity().campaignSquadId(unit));
                    battleSquads.add(sim.squad().squadId(unit));
                }
            }
            assertEquals(named, landed, "Every named wearer must physically land");
            assertEquals(1, battleSquads.size(), "Ferry trips must rejoin one twelve-person squad");
            var battleSquad = sim.getSquad(battleSquads.iterator().next());
            assertEquals(12, battleSquad.expectedSize);
            assertFalse(SquadFormUpSystem.formingUp(battleSquad));
            boolean controlledRetreat = !sim.isComplete();
            if (controlledRetreat) assertTrue(sim.retreat());
            MissionOutcome outcome = MissionResolver.compute(sim, mission, officer);
            Set<String> accounted = new HashSet<>(outcome.survivingSoldierIds);
            accounted.addAll(outcome.fallenSoldierIds);
            JSONArray missing = new JSONArray();
            for (String id : named) if (!accounted.contains(id)) {
                long entity = landedEntities.get(id);
                var world = sim.getEntityWorld();
                var components = sim.getBattleComponents();
                boolean health = world.has(entity, components.HEALTH);
                missing.put(new JSONObject().put("soldier", id).put("entity", entity)
                        .put("hasIdentity", world.has(entity, components.IDENTITY))
                        .put("hasHealth", health).put("hp", health ? sim.world().hp(entity) : JSONObject.NULL)
                        .put("corpse", world.has(entity, components.CORPSE))
                        .put("alive", sim.world().isAlive(entity))
                        .put("diedThisTick", sim.getDeathsThisFrame().contains(entity)));
            }
            report.put("missingOutcomeIdentities", missing);
            Files.writeString(output.resolve("summary.json"), report.toString(2));
            assertEquals(named, accounted, "All named identities must settle; missing entity state: " + missing);
            int rounds = outcome.soldierTelemetry.values().stream().mapToInt(row -> row.roundsFired()).sum();
            double damage = outcome.soldierTelemetry.values().stream().mapToDouble(row -> row.damageTaken() + row.damageDealt()).sum();
            report.put("ticks", sim.getSimTickIndex()).put("landed", landed.size()).put("ordinaryTrips", cycles)
                    .put("controlledRetreat", controlledRetreat).put("victory", outcome.victory)
                    .put("roundsFired", rounds).put("damageObserved", damage)
                    .put("fallen", new JSONArray(outcome.fallenSoldierIds));
            Files.writeString(output.resolve("summary.json"), report.toString(2));
            assertTrue(rounds > 0, "The loop must include actual company firing");
            assertTrue(damage > 0, "The loop must include an actual durability change");
            roster.applySoldierOutcome(MissionResolver.resolvePersonnelOutcomes(outcome), 10f, 12f,
                    outcome.soldierTelemetry, outcome.victory);
            MissionResolver.applyRecoverySpecialties(roster, outcome, 10f, 12f);
            OfficerMissionResult.apply(roster, outcome, 10);
            for (String id : named) assertEquals(1, roster.soldierById(id).career().missionsDeployed());
            JSONObject settled = personnel(roster, named);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (var stream = new ObjectOutputStream(bytes)) { stream.writeObject(roster); }
            MarineRoster loaded;
            try (var stream = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                loaded = (MarineRoster) stream.readObject();
            }
            assertEquals(settled.toString(), personnel(loaded, named).toString());
            assertEquals(squad.memberIds(), loaded.squadById(squad.id()).memberIds());
            assertEquals(officer.id(), loaded.squadById(squad.id()).homeCaptainId());
            assertEquals(officer.status(), loaded.byId(officer.id()).status());
            assertEquals(officer.xp(), loaded.byId(officer.id()).xp());
            report.put("settled", settled).put("serialization", "Java ObjectOutputStream detached roster")
                    .put("serializedBytes", bytes.size()).put("detachedPersonnelWritebackVerified", true)
                    .put("detachedSerializationVerified", true);
            Files.writeString(output.resolve("summary.json"), report.toString(2));
        }
        assertNull(Global.getSector());
    }

    private static JSONObject personnel(MarineRoster roster, Set<String> ids) throws JSONException {
        JSONObject result = new JSONObject();
        for (String id : ids.stream().sorted().toList()) {
            MarineSoldier soldier = roster.soldierById(id);
            var armor = soldier.armorDef();
            var career = soldier.career();
            result.put(id, new JSONObject().put("name", soldier.name()).put("aptitude", soldier.aptitude().name())
                    .put("armor", soldier.armorId()).put("armorClass", armor.armorClass().name())
                    .put("family", armor.appearanceFamily().name())
                    .put("system", armor.integralSystem() == null ? JSONObject.NULL : armor.integralSystem().id())
                    .put("primary", soldier.primaryId()).put("grade", soldier.primaryGrade().name())
                    .put("special", soldier.specialEquipmentId() == null ? JSONObject.NULL : soldier.specialEquipmentId())
                    .put("status", soldier.status().name()).put("recoveryDay", soldier.unavailableUntilDay())
                    .put("squad", roster.squadForSoldier(id).id()).put("deployments", career.missionsDeployed())
                    .put("wins", career.missionsWon()).put("rounds", career.roundsFired())
                    .put("hits", career.roundsHit()).put("dealt", career.damageDealt()).put("taken", career.damageTaken())
                    .put("friendlyFire", career.friendlyFireDamage()).put("kills", career.kills()).put("wounds", career.timesWounded()));
        }
        return result;
    }
}
