package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end + column-walk coverage for {@link FacingSystem}: membership
 * (every {@link UnitType#drawnAsSheet()} type carries {@code SPRITE} at
 * spawn, seeded to the south-idle frame), the facing-source fallback chain
 * (target &rarr; path &rarr; SOUTH), the weapon-up window, the eight-way
 * mech convention, and the corpse handoff (a dead unit's authored selector/
 * flip get re-asserted to the corpse invariant and the row drops out of
 * {@link BattleComponents#liveSprites}).
 *
 * <p>Most scenarios construct the system directly and call {@link
 * FacingSystem#tick()} by hand — the {@code DeadBodySystemTest} arena
 * pattern, avoiding whole-tick AI nondeterminism for facing/target setup done
 * via direct {@code World} writes. The dead-target and corpse scenarios
 * still need one real {@link BattleSimulation#advance} to drain the death
 * mailbox through {@code DeadBodySystem}.
 */
public class FacingSystemTest {

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

    private static FacingSystem systemFor(BattleSimulation sim) {
        return new FacingSystem(sim.getEntityWorld(), sim.getBattleComponents(), sim.getRoster());
    }

    private static int spriteIndex(BattleSimulation sim, long id) {
        BattleComponents c = sim.getBattleComponents();
        return sim.getEntityWorld().getInt(id, c.SPRITE, BattleComponents.SPRITE_INDEX);
    }

    private static int spriteFlipV(BattleSimulation sim, long id) {
        BattleComponents c = sim.getBattleComponents();
        return sim.getEntityWorld().getInt(id, c.SPRITE, BattleComponents.SPRITE_FLIP_V);
    }

    private static int spriteSheet(BattleSimulation sim, long id) {
        BattleComponents c = sim.getBattleComponents();
        return sim.getEntityWorld().getInt(id, c.SPRITE, BattleComponents.SPRITE_SHEET);
    }

    @Test
    public void drawnAsSheetTruthTable() {
        for (UnitType t : UnitType.values()) {
            boolean expectSheetDrawn = t != UnitType.TURRET
                    && t != UnitType.DRONE_HUB_STRUCTURE && t != UnitType.DRONE;
            assertEquals(expectSheetDrawn, t.drawnAsSheet(), t.name());
        }
    }

    @Test
    public void liveSheetUnitsSpawnWithSouthIdleSprite() {
        BattleSimulation sim = openArena(40, 40);
        Entity marine = new Entity("m0", Faction.MARINE, UnitType.MARINE, 5, 5);
        sim.addUnit(marine);
        Entity civilian = new Entity("c0", Faction.CIVILIAN, UnitType.CIVILIAN, 10, 10);
        sim.addUnit(civilian);

        EntityWorld world = sim.getEntityWorld();
        BattleComponents c = sim.getBattleComponents();
        for (Entity u : new Entity[]{marine, civilian}) {
            assertTrue(world.has(u.entityId, c.SPRITE), u.id + " carries SPRITE at spawn");
            assertEquals(3, spriteIndex(sim, u.entityId), u.id + " seeds the south-idle frame");
            assertEquals(0, spriteSheet(sim, u.entityId), u.id);
            assertEquals(0, spriteFlipV(sim, u.entityId), u.id);
        }

        // Negative membership: a live non-sheet unit (whole-sprite turret)
        // carries no SPRITE — it gains one only at the corpse transmute.
        MapTurret turret = new MapTurret("t0", Faction.DEFENDER, TurretKind.VULCAN, 20, 20);
        sim.addUnit(turret);
        assertFalse(world.has(turret.entityId, c.SPRITE), "live turret carries no SPRITE");
    }

    @Test
    public void idleUnitStaysAtSouthIdleFrame() {
        BattleSimulation sim = openArena(40, 40);
        Entity marine = new Entity("m0", Faction.MARINE, UnitType.MARINE, 5, 5);
        sim.addUnit(marine);

        systemFor(sim).tick();

        assertEquals(3, spriteIndex(sim, marine.entityId));
        assertEquals(0, spriteFlipV(sim, marine.entityId));
        assertEquals(0, spriteSheet(sim, marine.entityId));
    }

    @Test
    public void targetFacingIdleThenWeaponUp() {
        BattleSimulation sim = openArena(40, 40);
        Entity marine = new Entity("m0", Faction.MARINE, UnitType.MARINE, 5, 5);
        sim.addUnit(marine);
        Entity enemy = new Entity("d0", Faction.DEFENDER, UnitType.MARINE, 8, 5);
        sim.addUnit(enemy);
        sim.world().setTargetId(marine.entityId, enemy.entityId);

        FacingSystem system = systemFor(sim);
        system.tick();
        assertEquals(2, spriteIndex(sim, marine.entityId), "east idle, cooldown still at 0");
        assertEquals(0, spriteFlipV(sim, marine.entityId));

        sim.world().setCooldownTimer(marine.entityId, sim.world().attackCooldown(marine.entityId));
        system.tick();
        assertEquals(5, spriteIndex(sim, marine.entityId), "east weapon-up");
    }

    @Test
    public void southFacingWeaponUpFlipsVertically() {
        BattleSimulation sim = openArena(40, 40);
        Entity marine = new Entity("m0", Faction.MARINE, UnitType.MARINE, 5, 5);
        sim.addUnit(marine);
        // dy<0 -> SOUTH (facingFromDelta's convention; matches the renderer).
        Entity enemy = new Entity("d0", Faction.DEFENDER, UnitType.MARINE, 5, 2);
        sim.addUnit(enemy);
        sim.world().setTargetId(marine.entityId, enemy.entityId);
        sim.world().setCooldownTimer(marine.entityId, sim.world().attackCooldown(marine.entityId));

        systemFor(sim).tick();

        assertEquals(6, spriteIndex(sim, marine.entityId), "south weapon-up frame");
        assertEquals(1, spriteFlipV(sim, marine.entityId), "south weapon-up mirrors vertically");
    }

    @Test
    public void pathFacingWhenNoTarget() {
        BattleSimulation sim = openArena(40, 40);
        Entity marine = new Entity("m0", Faction.MARINE, UnitType.MARINE, 5, 5);
        sim.addUnit(marine);
        // The next path cell sits at y+1. In this sim's cell space dy>0 is NORTH
        // (facingFromDelta's convention, matching the renderer) — trust the
        // delta math over screen-direction intuition.
        sim.setPath(marine, new int[]{5, 5, 5, 6});

        systemFor(sim).tick();

        assertEquals(1, spriteIndex(sim, marine.entityId), "north idle from the path direction");
        assertEquals(0, spriteFlipV(sim, marine.entityId));
    }

    @Test
    public void deadTargetFallsBackToSouthIdle() {
        BattleSimulation sim = openArena(40, 40);
        Entity marine = new Entity("m0", Faction.MARINE, UnitType.MARINE, 5, 5);
        sim.addUnit(marine);
        Entity keepalive = new Entity("d-keepalive", Faction.DEFENDER, UnitType.MARINE, 38, 38);
        sim.addUnit(keepalive);
        Entity enemy = new Entity("d0", Faction.DEFENDER, UnitType.MARINE, 8, 5);
        sim.addUnit(enemy);
        sim.world().setTargetId(marine.entityId, enemy.entityId);

        sim.applyDamage(enemy, 100_000f, 1f, 0f);
        sim.advance(BattleSimulation.TICK_DT);
        assertFalse(sim.getRoster().isLive(enemy.entityId), "the target is dead");
        // Re-affirm the stale reference in case the tick's own AI already
        // cleared it — this scenario targets the roster.isLive gate
        // specifically, not the tid==0 fast path. Clear the path + cooldown
        // the tick's AI may have set, so the assertion pins the dead-target
        // fallthrough rather than whatever the AI decided that tick.
        sim.world().setTargetId(marine.entityId, enemy.entityId);
        sim.clearPath(marine);
        sim.world().setCooldownTimer(marine.entityId, 0f);

        systemFor(sim).tick();

        assertEquals(3, spriteIndex(sim, marine.entityId), "falls through to SOUTH — no path either");
        assertEquals(0, spriteFlipV(sim, marine.entityId));
    }

    @Test
    public void eightWayFacingFromTarget() {
        BattleSimulation sim = openArena(40, 40);
        Entity mech = new Entity("mech0", Faction.MARINE, UnitType.HEAVY_MECH, 10, 10);
        sim.addUnit(mech);
        Entity enemy = new Entity("d0", Faction.DEFENDER, UnitType.MARINE, 13, 13);
        sim.addUnit(enemy);
        sim.world().setTargetId(mech.entityId, enemy.entityId);

        FacingSystem system = systemFor(sim);
        system.tick();
        assertEquals(5, spriteIndex(sim, mech.entityId), "NE diagonal");
        assertEquals(0, spriteFlipV(sim, mech.entityId), "eight-way never flips");

        sim.world().setCellPos(enemy.entityId, 10, 13);
        system.tick();
        assertEquals(1, spriteIndex(sim, mech.entityId), "due N borrows the NW frame");
        assertEquals(0, spriteFlipV(sim, mech.entityId));
    }

    @Test
    public void secondaryAimSelectsAimSheetAndForcesWeaponUp() {
        BattleSimulation sim = openArena(40, 40);
        Entity marine = new Entity("m0", Faction.MARINE, UnitType.MARINE, 5, 5);
        marine.seedSecondaryWeapon = MarineSecondary.ROCKET_LAUNCHER;
        sim.addUnit(marine);
        Entity enemy = new Entity("d0", Faction.DEFENDER, UnitType.MARINE, 8, 5);
        sim.addUnit(enemy);
        sim.world().setTargetId(marine.entityId, enemy.entityId);
        sim.world().setSecondaryActionTimer(marine.entityId, 0.5f);

        FacingSystem system = systemFor(sim);
        system.tick();

        assertEquals(1, spriteSheet(sim, marine.entityId), "aiming selects the secondary-aim sheet");
        assertEquals(5, spriteIndex(sim, marine.entityId),
                "inAim forces weapon-up even at cooldown 0 — east weapon-up frame");
        assertEquals(0, spriteFlipV(sim, marine.entityId));

        sim.world().setSecondaryActionTimer(marine.entityId, 0f);
        system.tick();
        assertEquals(0, spriteSheet(sim, marine.entityId), "timer expired — back to the base sheet");
        assertEquals(2, spriteIndex(sim, marine.entityId), "east idle again");
    }

    @Test
    public void corpseFreezesPoseAndZeroesSelectorAndFlip() {
        BattleSimulation sim = openArena(40, 40);
        Entity marine = new Entity("m0", Faction.MARINE, UnitType.MARINE, 5, 5);
        sim.addUnit(marine);
        Entity keepalive = new Entity("d-keepalive", Faction.DEFENDER, UnitType.MARINE, 38, 38);
        sim.addUnit(keepalive);
        long id = marine.entityId;
        EntityWorld world = sim.getEntityWorld();
        BattleComponents c = sim.getBattleComponents();
        // Simulate an authored mid-secondary-aim pose on the live row.
        world.setInt(id, c.SPRITE, BattleComponents.SPRITE_SHEET, 1);
        world.setInt(id, c.SPRITE, BattleComponents.SPRITE_FLIP_V, 1);

        sim.applyDamage(marine, 100_000f, 1f, 0f);
        sim.advance(BattleSimulation.TICK_DT);

        assertFalse(sim.getRoster().isLive(id));
        int pose = spriteIndex(sim, id);
        assertTrue(pose >= 0 && pose < 4, "a valid death-pose frame");
        assertEquals(0, spriteSheet(sim, id), "corpse re-asserts the base sheet");
        assertEquals(0, spriteFlipV(sim, id), "corpse re-asserts unflipped");

        boolean matchesLiveSprites = false;
        for (ArchetypeTable t : world.matched(c.liveSprites)) {
            for (int r = 0, n = t.rowCount(); r < n; r++) {
                if (t.entityAt(r) == id) matchesLiveSprites = true;
            }
        }
        assertFalse(matchesLiveSprites, "a corpse carries no HEALTH, so liveSprites excludes it");
    }

    @Test
    public void endToEndFrameMatchesIndependentDerivation() {
        BattleSimulation sim = openArena(40, 40);
        Entity marine = new Entity("m0", Faction.MARINE, UnitType.MARINE, 10, 10);
        sim.addUnit(marine);
        Entity enemy = new Entity("d0", Faction.DEFENDER, UnitType.MARINE, 13, 10);
        sim.addUnit(enemy);

        // Well inside the 1.0s attack-cooldown window (30 ticks) — no shots
        // fired yet, so both units are guaranteed still alive.
        for (int i = 0; i < 10; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }
        assertTrue(sim.getRoster().isLive(marine.entityId));

        World world = sim.world();
        long id = marine.entityId;

        boolean hasCombat = world.hasCombat(id);
        boolean hasSecondary = world.hasSecondaryWeapon(id);
        boolean inAim = hasSecondary && world.secondaryActionTimer(id) > 0f;
        boolean up = LiveAppearance.weaponUp(inAim, UnitType.MARINE.combatant,
                hasCombat ? world.cooldownTimer(id) : 0f, hasCombat ? world.attackCooldown(id) : 0f);

        int dx = 0;
        int dy = 0;
        boolean haveDelta = false;
        if (hasCombat) {
            long tid = world.targetId(id);
            if (tid != 0L && sim.getRoster().isLive(tid)) {
                int ddx = world.cellX(tid) - world.cellX(id);
                int ddy = world.cellY(tid) - world.cellY(id);
                if (ddx != 0 || ddy != 0) {
                    dx = ddx;
                    dy = ddy;
                    haveDelta = true;
                }
            }
        }
        if (!haveDelta && world.hasMovement(id)) {
            int[] path = world.path(id);
            int idx = world.pathIdx(id);
            if (idx < Paths.cellCount(path)) {
                int ddx = Paths.cellX(path, idx) - world.cellX(id);
                int ddy = Paths.cellY(path, idx) - world.cellY(id);
                if (ddx != 0 || ddy != 0) {
                    dx = ddx;
                    dy = ddy;
                    haveDelta = true;
                }
            }
        }
        LiveAppearance.Facing facing = haveDelta
                ? LiveAppearance.facingFromDelta(dx, dy) : LiveAppearance.Facing.SOUTH;
        int expectedFrame = LiveAppearance.pickFrame(facing, up);
        int expectedFlip = LiveAppearance.flipV(facing, up) ? 1 : 0;

        assertEquals(expectedFrame, spriteIndex(sim, id));
        assertEquals(expectedFlip, spriteFlipV(sim, id));
    }
}
