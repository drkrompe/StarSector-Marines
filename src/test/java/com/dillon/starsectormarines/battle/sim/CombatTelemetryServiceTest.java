package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link CombatTelemetryService} and {@link CombatTelemetryReport}
 * — progression S3 slice 1
 * ({@code s3-per-soldier-telemetry.md}).
 *
 * <p>The load-bearing case is {@link #aKilledMarineKeepsItsRecord}: telemetry
 * is a lifecycle-stable capability, and a marine's statistics matter most when
 * they did not come home. Everything else guards attribution — who gets
 * credited, what "damage dealt" means once cover and overkill are accounted
 * for, and that friendly fire stays visible instead of being netted away.
 *
 * <p>Damage tests drive {@link BattleSimulation#applyDamage} directly rather
 * than a full tick: the serial path resolves inline through the same
 * {@code DamageResolver.resolve} the queued path uses, so the attribution
 * under test is identical and the arithmetic stays legible.
 */
public class CombatTelemetryServiceTest {

    private static BattleSimulation openArena(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, topology);
    }

    private static long unit(BattleSimulation sim, String name, Faction f, UnitType type, int x, int y) {
        return sim.spawn(new EntitySpec(name, f, type, x, y));
    }

    @Test
    public void combatantsAreRecordedAndBystandersAreNot() {
        BattleSimulation sim = openArena(20, 20);
        long marine = unit(sim, "marine", Faction.MARINE, UnitType.MARINE, 5, 5);
        long civilian = unit(sim, "resident", Faction.CIVILIAN, UnitType.CIVILIAN, 6, 5);

        CombatTelemetryService telemetry = sim.telemetry();
        assertTrue(telemetry.isRecorded(marine), "a combatant carries TELEMETRY");
        assertFalse(telemetry.isRecorded(civilian), "a non-combatant never fires, so nothing to record");
        assertFalse(telemetry.isRecorded(CombatTelemetryService.NO_ATTACKER),
                "the no-attacker sentinel is never a recorded entity");
    }

    @Test
    public void damageAndKillsCreditTheAttacker() {
        BattleSimulation sim = openArena(20, 20);
        long shooter = unit(sim, "shooter", Faction.MARINE, UnitType.MARINE, 5, 5);
        long victim = unit(sim, "victim", Faction.DEFENDER, UnitType.MILITIA, 8, 5);
        float victimHp = sim.world().hp(victim);

        sim.applyDamage(victim, shooter, 5f, 1f, 1f);

        CombatTelemetryService telemetry = sim.telemetry();
        assertEquals(5f, telemetry.damageDealt(shooter), 0.001f);
        assertEquals(5f, telemetry.damageTaken(victim), 0.001f);
        assertEquals(0, telemetry.kills(shooter), "still standing");

        sim.applyDamage(victim, shooter, victimHp, 1f, 1f);

        assertEquals(1, telemetry.kills(shooter));
        assertEquals(victimHp, telemetry.damageDealt(shooter), 0.001f,
                "overkill past zero is not output the shooter produced");
        assertEquals(victimHp, telemetry.damageTaken(victim), 0.001f);
    }

    @Test
    public void aKilledMarineKeepsItsRecord() {
        BattleSimulation sim = openArena(20, 20);
        long marine = unit(sim, "rifleman", Faction.MARINE, UnitType.MARINE, 5, 5);
        long enemy = unit(sim, "raider", Faction.DEFENDER, UnitType.MILITIA, 8, 5);

        sim.applyDamage(enemy, marine, 4f, 1f, 1f);
        sim.applyDamage(marine, enemy, 1_000_000f, 1f, 1f);

        assertFalse(sim.getRoster().isAliveById(marine), "the marine is down");
        CombatTelemetryService telemetry = sim.telemetry();
        assertTrue(telemetry.isRecorded(marine),
                "TELEMETRY rides the corpse transmute; a dead marine's record is the one the campaign wants");
        assertEquals(4f, telemetry.damageDealt(marine), 0.001f, "what they did before they fell");
        assertEquals(1, telemetry.kills(enemy));
    }

    @Test
    public void friendlyFireIsSeparatedAndNeverCountsAsAKill() {
        BattleSimulation sim = openArena(20, 20);
        long shooter = unit(sim, "shooter", Faction.MARINE, UnitType.MARINE, 5, 5);
        long squadmate = unit(sim, "squadmate", Faction.MARINE, UnitType.MARINE, 6, 5);

        sim.applyDamage(squadmate, shooter, 1_000_000f, 1f, 1f);

        CombatTelemetryService telemetry = sim.telemetry();
        assertEquals(0f, telemetry.damageDealt(shooter), 0.001f,
                "netting friendly fire into output would hide it");
        assertTrue(telemetry.friendlyFireDamage(shooter) > 0f);
        assertEquals(0, telemetry.kills(shooter), "you do not get credit for that");
        assertTrue(telemetry.damageTaken(squadmate) > 0f,
                "the victim absorbed it regardless of whose side fired");
    }

    @Test
    public void unattributedDamageStillRecordsWhatTheTargetAbsorbed() {
        BattleSimulation sim = openArena(20, 20);
        long victim = unit(sim, "victim", Faction.DEFENDER, UnitType.MILITIA, 8, 5);

        sim.applyExternalDamage(victim, 3f);

        assertEquals(3f, sim.telemetry().damageTaken(victim), 0.001f,
                "a strafing run still costs the target HP even though no entity is credited");
    }

    @Test
    public void firingCountsEveryRoundAndOnlyLandedOnesAsHits() {
        BattleSimulation sim = openArena(40, 20);
        long shooter = unit(sim, "shooter", Faction.MARINE, UnitType.MARINE, 5, 5);
        long target = unit(sim, "target", Faction.DEFENDER, UnitType.MILITIA, 9, 5);
        sim.combat().setPrimaryWeapon(shooter, MarineWeapon.PULSE_RIFLE);
        // Certain accuracy and a pool the target cannot burn through. The
        // firing pipeline rolls on ThreadLocalRandom, so leaving the nominal
        // 0.35 accuracy in place makes this a coin-flip test rather than a
        // measurement of the counting. 1.0 is not enough either: fireShot
        // feeds the stat through RangeFalloff and the grade/profile
        // multipliers, so even at a sixth of the weapon's range it lands just
        // under certain and one round in twenty still misses. Overshoot the
        // roll instead — what is under test is the bookkeeping, not the
        // marksmanship.
        sim.combat().setAccuracy(shooter, 10f);
        sim.world().setMaxHp(target, 1_000_000f);
        sim.world().setHp(target, 1_000_000f);

        CombatTelemetryService telemetry = sim.telemetry();
        for (int i = 0; i < 5; i++) {
            sim.fireShot(shooter, target, FireStance.STANCED);
        }

        assertEquals(5, telemetry.roundsFired(shooter), "one count per round, not per trigger pull");
        assertEquals(0, telemetry.roundsHit(shooter),
                "rounds are in flight; a hit is only counted when the impact clock expires");

        // Drain every pending impact through the sim's own tick so the hit
        // count comes from the production path, not a hand-written stand-in.
        for (int i = 0; i < 60 && telemetry.roundsHit(shooter) < 5; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }
        assertTrue(telemetry.roundsHit(shooter) >= 5,
                "five certain-accuracy rounds at four cells all reach the body");
        assertTrue(telemetry.roundsHit(shooter) <= telemetry.roundsFired(shooter),
                "landed rounds can never exceed fired rounds");
        assertTrue(telemetry.landedFraction(shooter) > 0f);
    }

    @Test
    public void gatherReportsTheFallenAlongsideTheLiving() {
        BattleSimulation sim = openArena(20, 20);
        long marine = unit(sim, "rifleman", Faction.MARINE, UnitType.MARINE, 5, 5);
        long enemy = unit(sim, "raider", Faction.DEFENDER, UnitType.MILITIA, 8, 5);

        sim.applyDamage(enemy, marine, 1_000_000f, 1f, 1f);
        // The corpse transmute is buffered to the death-dispatcher drain, so a
        // gather taken before the next tick still sees the dead as live rows.
        // One tick puts the world in the state a mission-end gather sees.
        sim.advance(BattleSimulation.TICK_DT);

        List<CombatTelemetryRow> rows = CombatTelemetryReport.gather(sim);
        CombatTelemetryRow killer = rowFor(rows, marine);
        CombatTelemetryRow fallen = rowFor(rows, enemy);

        assertTrue(killer.survived());
        assertEquals(1, killer.kills());
        assertSame(Faction.MARINE, killer.faction());
        assertEquals("rifleman", killer.name());

        assertFalse(fallen.survived(), "a corpse still reports");
        assertTrue(fallen.damageTaken() > 0f);

        assertTrue(rows.get(0).entityId() < rows.get(1).entityId(),
                "rows come back in spawn order so two runs can be diffed");
        assertTrue(CombatTelemetryReport.format(rows).contains("rifleman"));
    }

    @Test
    public void onlyCampaignMarinesCarryASoldierIdIntoTheGather() {
        BattleSimulation sim = openArena(20, 20);
        long deployed = sim.spawn(new EntitySpec("rifleman", Faction.MARINE, UnitType.MARINE, 5, 5)
                .campaignSoldierId("soldier-7"));
        long defender = unit(sim, "raider", Faction.DEFENDER, UnitType.MILITIA, 8, 5);
        long militia = unit(sim, "local", Faction.MARINE, UnitType.MILITIA, 6, 5);

        sim.applyDamage(defender, deployed, 3f, 1f, 1f);

        List<CombatTelemetryRow> rows = CombatTelemetryReport.gather(sim);
        assertEquals("soldier-7", rowFor(rows, deployed).campaignSoldierId(),
                "only CampaignMarineDeployment supplies this, so it IS the campaign key");
        assertNull(rowFor(rows, defender).campaignSoldierId(),
                "defenders never acquire a campaign career record");
        assertNull(rowFor(rows, militia).campaignSoldierId(),
                "employer militia fight on our side but are not our people");
    }

    @Test
    public void gatheringTwiceYieldsIdenticalRows() {
        BattleSimulation sim = openArena(20, 20);
        long shooter = unit(sim, "shooter", Faction.MARINE, UnitType.MARINE, 5, 5);
        long victim = unit(sim, "victim", Faction.DEFENDER, UnitType.MILITIA, 8, 5);
        sim.applyDamage(victim, shooter, 6f, 1f, 1f);

        assertEquals(CombatTelemetryReport.gather(sim), CombatTelemetryReport.gather(sim),
                "the gather is a pure read, so computing an outcome twice cannot drift");
    }

    private static CombatTelemetryRow rowFor(List<CombatTelemetryRow> rows, long entityId) {
        for (CombatTelemetryRow row : rows) {
            if (row.entityId() == entityId) return row;
        }
        assertNotNull(null, "no telemetry row for entity " + entityId);
        return null;
    }
}
