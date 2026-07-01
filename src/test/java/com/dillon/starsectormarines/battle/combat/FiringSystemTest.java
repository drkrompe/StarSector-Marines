package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.infantry.RepositionToCover;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link FiringSystem} — the proving slice of the FiringSystem
 * epic ({@code roadmap/ecs-migration/stories/firing-system.md}). Direct-tick
 * tests construct the system by hand and drive it against manually-written
 * {@code COMBAT} fire intent, the {@link com.dillon.starsectormarines.battle.unit.DeadBodySystemTest}
 * / {@code FacingSystemTest} arena pattern — avoiding whole-tick AI
 * nondeterminism for every gate except the end-to-end cadence golden and the
 * overkill/focus-fire test, which need the real
 * {@link com.dillon.starsectormarines.battle.infantry.EngagePosture} →
 * {@code FiringSystem} pipeline (or at least {@code BattleSimulation.tick}'s
 * full phase order) wired exactly as {@link BattleSimulation#tick()} runs it.
 *
 * <p>Direct-tick tests below don't bracket {@code FiringSystem.tick} with
 * {@code DamageService.enterCombatEffectDeferral}/{@code exit} — there's no
 * accessor to reach that flag from outside {@link BattleSimulation} — but
 * none of them need to: the two "already dead" gate tests kill their target
 * with an inline {@code sim.applyDamage} call BEFORE {@code tick()} runs (so
 * the deferral flag was never in play for that resolve either way), and none
 * of the others assert on a mid-walk kill produced by the walk's own shot.
 * The one test that genuinely needs the deferral semantics (the overkill
 * test) drives a real {@link BattleSimulation#advance} instead, which brackets
 * FIRING with the deferral flag exactly as production does.
 */
public class FiringSystemTest {

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

    private static Entity combatant(BattleSimulation sim, Faction f, int x, int y) {
        Entity u = new Entity("u" + sim.liveUnitCount(), f, UnitType.MARINE, x, y);
        sim.addUnit(u);
        return u;
    }

    private static FiringSystem systemFor(BattleSimulation sim) {
        return new FiringSystem(sim.getGrid(), sim.getRoster());
    }

    @Test
    public void intentInRangeWithLosAndCooldownReadyFiresExactlyOnce() {
        BattleSimulation sim = openArena(30, 10);
        Entity shooter = combatant(sim, Faction.MARINE, 5, 5);
        Entity target = combatant(sim, Faction.DEFENDER, 10, 5);
        // PULSE_RIFLE's 3-round burst gives beginBurst something to queue —
        // an observable, hit/miss-independent side effect of a fire.
        sim.combat().setPrimaryWeapon(shooter.entityId, MarineWeapon.PULSE_RIFLE);
        sim.world().setAttackRange(shooter.entityId, 10f);
        // cooldownTimer defaults to 0 — ready to fire.

        sim.combat().setFireIntent(shooter.entityId, target.entityId, FireStance.STANCED, false);
        systemFor(sim).tick(sim);

        assertEquals(0L, sim.combat().fireTargetId(shooter.entityId), "consume-once: intent cleared");
        assertEquals(sim.world().attackCooldown(shooter.entityId), sim.world().cooldownTimer(shooter.entityId), 1e-6f,
                "a successful fire resets cooldownTimer to attackCooldown");
        assertEquals(MarineWeapon.PULSE_RIFLE.burstCount - 1, sim.world().burstRemaining(shooter.entityId),
                "PULSE_RIFLE's 3-round burst queues 2 follow-up rounds via beginBurst");
        assertEquals(target.entityId, sim.world().burstTargetId(shooter.entityId));
    }

    @Test
    public void noIntentLeavesStateUnchanged() {
        BattleSimulation sim = openArena(30, 10);
        Entity shooter = combatant(sim, Faction.MARINE, 5, 5);
        combatant(sim, Faction.DEFENDER, 10, 5);
        // fireTargetId defaults to 0 — never written this test.

        systemFor(sim).tick(sim);

        assertEquals(0f, sim.world().cooldownTimer(shooter.entityId), 1e-6f, "no intent, no fire, no cooldown reset");
        assertEquals(0, sim.world().burstRemaining(shooter.entityId));
        assertEquals(0L, sim.combat().fireTargetId(shooter.entityId));
    }

    @Test
    public void intentBlockedByCooldownDoesNotFireButConsumesIntent() {
        BattleSimulation sim = openArena(30, 10);
        Entity shooter = combatant(sim, Faction.MARINE, 5, 5);
        Entity target = combatant(sim, Faction.DEFENDER, 10, 5);
        sim.world().setAttackRange(shooter.entityId, 10f);
        sim.world().setCooldownTimer(shooter.entityId, 0.5f);
        sim.combat().setFireIntent(shooter.entityId, target.entityId, FireStance.STANCED, false);

        systemFor(sim).tick(sim);

        assertEquals(0.5f, sim.world().cooldownTimer(shooter.entityId), 1e-6f,
                "cooldown gate blocks the fire — cooldownTimer is untouched, not reset");
        assertEquals(0L, sim.combat().fireTargetId(shooter.entityId), "intent still consumed even though it didn't fire");
        assertEquals(0, sim.world().burstRemaining(shooter.entityId));
    }

    @Test
    public void intentBlockedByRangeDoesNotFireButConsumesIntent() {
        BattleSimulation sim = openArena(40, 10);
        Entity shooter = combatant(sim, Faction.MARINE, 5, 5);
        Entity target = combatant(sim, Faction.DEFENDER, 30, 5); // dist 25
        sim.world().setAttackRange(shooter.entityId, 10f); // well short of 25
        sim.combat().setFireIntent(shooter.entityId, target.entityId, FireStance.STANCED, false);

        systemFor(sim).tick(sim);

        assertEquals(0f, sim.world().cooldownTimer(shooter.entityId), 1e-6f, "out-of-range intent must not fire");
        assertEquals(0L, sim.combat().fireTargetId(shooter.entityId));
    }

    @Test
    public void intentBlockedByWallLosDoesNotFireButConsumesIntent() {
        BattleSimulation sim = openArena(30, 10);
        NavigationGrid grid = sim.getGrid();
        grid.setWalkable(8, 5, false); // wall directly on the shooter-target line
        Entity shooter = combatant(sim, Faction.MARINE, 5, 5);
        Entity target = combatant(sim, Faction.DEFENDER, 12, 5);
        sim.world().setAttackRange(shooter.entityId, 15f); // in range were it not for the wall
        sim.combat().setFireIntent(shooter.entityId, target.entityId, FireStance.STANCED, false);

        systemFor(sim).tick(sim);

        assertEquals(0f, sim.world().cooldownTimer(shooter.entityId), 1e-6f, "wall-blocked LoS must not fire");
        assertEquals(0L, sim.combat().fireTargetId(shooter.entityId));
    }

    @Test
    public void intentAtDeadTargetDoesNotFireNoCrash() {
        BattleSimulation sim = openArena(30, 10);
        Entity shooter = combatant(sim, Faction.MARINE, 5, 5);
        Entity target = combatant(sim, Faction.DEFENDER, 10, 5);
        sim.world().setAttackRange(shooter.entityId, 10f);
        sim.combat().setFireIntent(shooter.entityId, target.entityId, FireStance.STANCED, false);
        // Inline kill — no drain/advance needed; the world row transmute is
        // buffered to the death-dispatcher drain, so the COMBAT table this
        // walk touches is untouched, but the roster pops the target.
        sim.applyDamage(target, 100_000f, 1f, 1f);

        assertDoesNotThrow(() -> systemFor(sim).tick(sim));

        assertEquals(0f, sim.world().cooldownTimer(shooter.entityId), 1e-6f, "a dead target must not be fired at");
        assertEquals(0L, sim.combat().fireTargetId(shooter.entityId), "intent still consumed");
    }

    @Test
    public void deadShooterWithStaleIntentIsSkippedNoCrash() {
        BattleSimulation sim = openArena(30, 10);
        Entity shooter = combatant(sim, Faction.MARINE, 5, 5);
        Entity target = combatant(sim, Faction.DEFENDER, 10, 5);
        sim.world().setAttackRange(shooter.entityId, 10f);
        sim.combat().setFireIntent(shooter.entityId, target.entityId, FireStance.STANCED, false);
        // Kill the shooter after intent was written — a different shot
        // landing earlier in the same walk, per the story's ordering.
        sim.applyDamage(shooter, 100_000f, 1f, 1f);

        assertDoesNotThrow(() -> systemFor(sim).tick(sim));

        assertEquals(0L, sim.combat().fireTargetId(shooter.entityId), "consume-once still clears for a dead shooter");
        assertEquals(0f, sim.world().cooldownTimer(shooter.entityId), 1e-6f, "no fire attempted for a dead shooter");
    }

    @Test
    public void repositionFlagTrueTriggersRepositionWhenBetterCoverExists() {
        BattleSimulation sim = openArena(30, 20);
        NavigationGrid grid = sim.getGrid();
        Entity shooter = combatant(sim, Faction.MARINE, 10, 10);
        Entity target = combatant(sim, Faction.DEFENDER, 14, 10); // due east, dist 4
        sim.world().setAttackRange(shooter.entityId, 10f);
        // The shooter's own cell has no cover against this threat direction
        // (default 0); give exactly one nearby cell strictly better cover so
        // findFiringPositionCoverPreferred has a real candidate to find on an
        // otherwise bare grid.
        grid.setCoverAtFacing(10, 9, NavigationGrid.FACING_E, 2);
        // RepositionToCover.tryReposition reads COMBAT.targetId ("who I'm
        // engaging"), a separate field from the fire-intent's fireTargetId —
        // production EngagePosture always sets this during target selection
        // before it ever considers firing.
        sim.world().setTargetId(shooter.entityId, target.entityId);
        sim.combat().setFireIntent(shooter.entityId, target.entityId, FireStance.STANCED, true);

        systemFor(sim).tick(sim);

        assertEquals(sim.world().attackCooldown(shooter.entityId), sim.world().cooldownTimer(shooter.entityId), 1e-6f,
                "test prerequisite: the shot actually fired");
        assertEquals(RepositionToCover.COOLDOWN_SECONDS, sim.world().repositionCooldown(shooter.entityId), 1e-6f,
                "reposition=true chains RepositionToCover, which stamps its cooldown on a successful move");
        assertFalse(Paths.isEmpty(sim.world().path(shooter.entityId)), "a path toward the better-cover cell was queued");
    }

    @Test
    public void repositionFlagFalseSkipsRepositionEvenWithBetterCoverAvailable() {
        BattleSimulation sim = openArena(30, 20);
        NavigationGrid grid = sim.getGrid();
        Entity shooter = combatant(sim, Faction.MARINE, 10, 10);
        Entity target = combatant(sim, Faction.DEFENDER, 14, 10);
        sim.world().setAttackRange(shooter.entityId, 10f);
        // Identical cover setup to the true-flag test above — the only
        // variable here is the reposition flag.
        grid.setCoverAtFacing(10, 9, NavigationGrid.FACING_E, 2);
        sim.world().setTargetId(shooter.entityId, target.entityId);
        sim.combat().setFireIntent(shooter.entityId, target.entityId, FireStance.STANCED, false);

        systemFor(sim).tick(sim);

        assertEquals(sim.world().attackCooldown(shooter.entityId), sim.world().cooldownTimer(shooter.entityId), 1e-6f,
                "test prerequisite: the shot actually fired");
        assertEquals(0f, sim.world().repositionCooldown(shooter.entityId), 1e-6f,
                "reposition=false must never call RepositionToCover, even when a better cell exists");
        assertTrue(Paths.isEmpty(sim.world().path(shooter.entityId)), "no path should be queued without the reposition flag");
    }

    @Test
    public void repositionFlagTrueButCooldownBlockedNeverRepositions() {
        BattleSimulation sim = openArena(30, 20);
        NavigationGrid grid = sim.getGrid();
        Entity shooter = combatant(sim, Faction.MARINE, 10, 10);
        Entity target = combatant(sim, Faction.DEFENDER, 14, 10);
        sim.world().setAttackRange(shooter.entityId, 10f);
        sim.world().setCooldownTimer(shooter.entityId, 0.5f); // blocks the fire gate
        // Same better-cover setup as the two tests above — a candidate exists,
        // so the only reason RepositionToCover must not fire here is that the
        // shot itself never happened.
        grid.setCoverAtFacing(10, 9, NavigationGrid.FACING_E, 2);
        sim.world().setTargetId(shooter.entityId, target.entityId);
        sim.combat().setFireIntent(shooter.entityId, target.entityId, FireStance.STANCED, true);

        systemFor(sim).tick(sim);

        assertEquals(0.5f, sim.world().cooldownTimer(shooter.entityId), 1e-6f,
                "test prerequisite: the cooldown gate blocked the shot");
        assertEquals(0f, sim.world().repositionCooldown(shooter.entityId), 1e-6f,
                "reposition is chained only on a SUCCESSFUL fire — a cooldown-blocked shot must never stamp it");
        assertTrue(Paths.isEmpty(sim.world().path(shooter.entityId)), "no path should be queued when the fire itself was blocked");
    }

    /**
     * Cadence golden — the end-to-end {@code EngagePosture} → {@code
     * FiringSystem} pipeline, driven through real {@link BattleSimulation#tick()}
     * calls exactly as the game runs it (not a direct-tick shortcut). A lone
     * squadded marine and a squad-less (therefore inert — {@code
     * GoapInfantryBehavior.update} no-ops without a squad) defender sit in
     * range and LoS of each other for the whole run, so the marine's squad
     * stays on {@code EngagePosture} the entire window and nothing but the
     * marine's own fire ever touches its {@code cooldownTimer}. HP is
     * cranked so an unlucky hit streak can never end the battle mid-window.
     *
     * <p>Verifies the story's "cadence is unchanged" equivalence claim: even
     * though execution moved from inline-during-the-parallel-dispatch to the
     * serial {@code FiringSystem} phase, the marine still resets its cooldown
     * on the same period ({@code attackCooldown / TICK_DT} ticks) it did
     * before the flip.
     */
    @Test
    public void engagePostureCadenceGoldenAcrossFullSimTicks() {
        BattleSimulation sim = openArena(30, 10);
        int squadId = sim.mintSquad(Faction.MARINE, null);
        Entity marine = new Entity("m", Faction.MARINE, UnitType.MARINE, 5, 5);
        marine.seedSquadId = squadId;
        marine.seedHp = 1_000_000f;
        marine.seedMaxHp = 1_000_000f;
        sim.addUnit(marine);

        // No squad assigned — GoapInfantryBehavior.update no-ops for it, so
        // it never moves, never fires, and never leaves range/LoS.
        Entity defender = new Entity("d", Faction.DEFENDER, UnitType.MARINE, 10, 5);
        defender.seedHp = 1_000_000f;
        defender.seedMaxHp = 1_000_000f;
        sim.addUnit(defender);

        int totalTicks = 120;
        List<Integer> resetTicks = new ArrayList<>();
        float prevCooldown = sim.world().cooldownTimer(marine.entityId);
        for (int tickIdx = 1; tickIdx <= totalTicks; tickIdx++) {
            sim.advance(BattleSimulation.TICK_DT);
            float cd = sim.world().cooldownTimer(marine.entityId);
            if (cd > prevCooldown) resetTicks.add(tickIdx); // a jump up = a fire happened this tick
            prevCooldown = cd;
        }

        assertFalse(resetTicks.isEmpty(), "the marine should have fired at least once over " + totalTicks + " ticks");

        int ticksPerCooldown = Math.round(sim.world().attackCooldown(marine.entityId) / BattleSimulation.TICK_DT);
        for (int i = 1; i < resetTicks.size(); i++) {
            int spacing = resetTicks.get(i) - resetTicks.get(i - 1);
            // Exact, not "within one tick": a systematic off-by-one in the
            // FIRING-phase gate (e.g. re-checking range/cooldown a tick late
            // post-advance) would show up as a steady 31-tick spacing here —
            // the old ±1 tolerance would have silently swallowed that.
            assertEquals(ticksPerCooldown, spacing,
                    "reset spacing should track attackCooldown/TICK_DT (" + ticksPerCooldown
                            + ") exactly; got " + spacing + " between ticks "
                            + resetTicks.get(i - 1) + " and " + resetTicks.get(i));
        }

        int firstReset = resetTicks.get(0);
        int expectedCount = 1 + (totalTicks - firstReset) / ticksPerCooldown;
        assertEquals(expectedCount, resetTicks.size(),
                "reset count should match (totalTicks - firstReset)/ticksPerCooldown + 1 = " + expectedCount
                        + "; got " + resetTicks.size() + " resets at ticks " + resetTicks);
    }

    /**
     * Overkill / focus-fire — pins the restored "both shooters fire" semantics
     * (critique-fix 1b). Two independent shooters author intent at the same
     * lethally-fragile target this tick; because FIRING now defers damage to
     * the same {@code APPLY_DAMAGE} barrier the old parallel UPDATE_UNITS path
     * used (see {@link com.dillon.starsectormarines.battle.combat.DamageService}),
     * neither shooter's row sees the target as dead — both fire and reset
     * their cooldown — and the target only actually dies once both queued
     * hits flush, after FIRING has finished for the tick.
     *
     * <p>Driven through the real {@link BattleSimulation#advance} (not the
     * direct-tick shortcut the gate tests above use) so it exercises the
     * production deferral bracket + same-tick flush exactly as
     * {@code BattleSimulation.tick} wires it. Both shooters are squadless —
     * like the cadence golden test's defender, {@code GoapInfantryBehavior}
     * no-ops for them, so the hand-written fire intents survive UPDATE_UNITS
     * untouched into FIRING.
     */
    @Test
    public void overkillBothShootersFireAtSharedFragileTargetDeferredDamageKillsAtFlush() {
        BattleSimulation sim = openArena(30, 10);
        Entity shooterA = combatant(sim, Faction.MARINE, 5, 5);
        Entity shooterB = combatant(sim, Faction.MARINE, 6, 5);
        Entity target = combatant(sim, Faction.DEFENDER, 10, 5);
        sim.world().setAttackRange(shooterA.entityId, 10f);
        sim.world().setAttackRange(shooterB.entityId, 10f);
        // No MarineWeapon on either shooter, so fireShot's accuracy/damage come
        // straight off the baked (overridable) World accessors: force a
        // guaranteed hit and make a single hit lethal — either shot alone
        // kills, so the test isolates "does the second shooter still fire"
        // rather than "does the target survive two hits".
        sim.world().setAccuracy(shooterA.entityId, 1f);
        sim.world().setAccuracy(shooterB.entityId, 1f);
        sim.world().setHp(target.entityId, 1f);
        sim.combat().setFireIntent(shooterA.entityId, target.entityId, FireStance.STANCED, false);
        sim.combat().setFireIntent(shooterB.entityId, target.entityId, FireStance.STANCED, false);

        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(sim.world().attackCooldown(shooterA.entityId), sim.world().cooldownTimer(shooterA.entityId), 1e-6f,
                "first shooter fires normally");
        assertEquals(sim.world().attackCooldown(shooterB.entityId), sim.world().cooldownTimer(shooterB.entityId), 1e-6f,
                "second shooter ALSO fires at the same nominally-still-alive target — the restored overkill semantics; "
                        + "the pre-fix inline-during-FIRING behavior would have left this cooldown untouched because "
                        + "the first shooter's damage had already (wrongly) resolved the target dead");
        assertFalse(sim.world().isAlive(target.entityId),
                "the target dies once both shots' queued damage flushes at APPLY_DAMAGE, after FIRING finished this tick");
    }

    // Not covered: the doomed-unit-gets-a-final-action semantic (1a) as it
    // applies to InfantryWeapons' INFANTRY_TICK burst continuation
    // specifically (as opposed to the FIRING-phase table walk the overkill
    // test above already exercises). InfantryWeapons.tick() only fires a
    // burst round for a shooter carrying a MarineWeapon (a null weapon just
    // clears the burst state without a shot), and a weapon's hit roll is
    // driven by its accuracy/accuracyFalloff through Entity.rng (an unseeded
    // java.util.Random per unit) rather than the overridable World.accuracy
    // used above — so there's no deterministic way to force the hit from
    // outside. The complementary deterministic signal (burstRemaining
    // decrementing to 0) lives on the VICTIM's own COMBAT row, but by the
    // time BattleSimulation.advance() returns from the tick that kills it,
    // the death-dispatcher drain (DEMOLISH, later in the same tick) has
    // already transmuted that row to the corpse archetype, and neither
    // DamageService nor InfantryWeapons is reachable from BattleSimulation's
    // public surface to invoke the burst-continuation pass standalone
    // mid-tick. The overkill test above already exercises the identical
    // underlying invariant (a target hit this tick stays roster-alive through
    // every later phase until the deferred flush), just via FIRING's own
    // table walk instead of the burst continuation one phase later.
}
