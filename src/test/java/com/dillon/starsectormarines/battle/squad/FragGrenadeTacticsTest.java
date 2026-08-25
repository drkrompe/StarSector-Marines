package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.infantry.FragGrenadeTactics;
import com.dillon.starsectormarines.battle.infantry.InfantryUnitPrep;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FragGrenadeTacticsTest {

    @Test
    void isolatedSoftTargetIsRejectedButAClusterIsReserved() {
        Fixture isolated = fixture();
        long lone = enemy(isolated.sim, 10, 5);
        observe(isolated, lone);
        assertFalse(FragGrenadeTactics.tryCommitThrow(isolated.carrier,
                MarineSecondary.FRAG_GRENADE, isolated.sim));

        Fixture cluster = fixture();
        long first = enemy(cluster.sim, 10, 5);
        long second = enemy(cluster.sim, 10, 6);
        observe(cluster, first, second);
        assertTrue(FragGrenadeTactics.tryCommitThrow(cluster.carrier,
                MarineSecondary.FRAG_GRENADE, cluster.sim));
        assertNotNull(cluster.sim.fragGrenades().reservationFor(cluster.carrier));
        assertEquals(MarineSecondary.FRAG_GRENADE.startingAmmo(),
                cluster.sim.world().secondaryAmmo(cluster.carrier),
                "commitment does not spend a grenade before release");
    }

    @Test
    void defenderCarrierUsesTheSameClusterPolicy() {
        Fixture fixture = fixture(Faction.DEFENDER);
        long first = enemy(fixture.sim, Faction.MARINE, 10, 5);
        long second = enemy(fixture.sim, Faction.MARINE, 10, 6);
        observe(fixture, first, second);

        assertTrue(FragGrenadeTactics.tryCommitThrow(fixture.carrier,
                MarineSecondary.FRAG_GRENADE, fixture.sim));
        assertNotNull(fixture.sim.fragGrenades().reservationFor(fixture.carrier));
    }

    @Test
    void currentAndCommittedFriendlyMovementRejectTheFootprint() {
        Fixture current = fixture();
        long a = enemy(current.sim, 10, 5);
        long b = enemy(current.sim, 10, 6);
        current.sim.spawn(new EntitySpec("friendly", Faction.MARINE,
                UnitType.MARINE, 10, 5));
        observe(current, a, b);
        assertFalse(FragGrenadeTactics.tryCommitThrow(current.carrier,
                MarineSecondary.FRAG_GRENADE, current.sim));

        Fixture moving = fixture();
        a = enemy(moving.sim, 10, 5);
        b = enemy(moving.sim, 10, 6);
        long friendly = moving.sim.spawn(new EntitySpec("moving-friendly", Faction.MARINE,
                UnitType.MARINE, 3, 12));
        moving.sim.setPath(friendly, new int[]{3, 12, 10, 5});
        observe(moving, a, b);
        assertFalse(FragGrenadeTactics.tryCommitThrow(moving.carrier,
                MarineSecondary.FRAG_GRENADE, moving.sim));
    }

    @Test
    void overlappingVolleyIsBlockedWhileASeparateClusterCanBeEngaged() {
        Fixture fixture = fixture();
        long secondCarrier = fixture.sim.spawn(new EntitySpec("frag-2", Faction.MARINE,
                UnitType.MARINE, 5, 10).squad(fixture.squad.id)
                .secondary(MarineSecondary.FRAG_GRENADE, 3));
        long a = enemy(fixture.sim, 10, 5);
        long b = enemy(fixture.sim, 10, 6);
        long c = enemy(fixture.sim, 10, 10);
        long d = enemy(fixture.sim, 10, 11);
        observe(fixture, a, b, c, d);

        assertTrue(FragGrenadeTactics.tryCommitThrow(fixture.carrier,
                MarineSecondary.FRAG_GRENADE, fixture.sim));
        assertTrue(FragGrenadeTactics.tryCommitThrow(secondCarrier,
                MarineSecondary.FRAG_GRENADE, fixture.sim));
        var first = fixture.sim.fragGrenades().reservationFor(fixture.carrier);
        var second = fixture.sim.fragGrenades().reservationFor(secondCarrier);
        float dx = first.targetX() - second.targetX();
        float dy = first.targetY() - second.targetY();
        float separated = first.blastRadius() + second.blastRadius();
        assertTrue(dx * dx + dy * dy > separated * separated,
                "the second carrier must choose the genuinely separate cluster");
    }

    @Test
    void releasedGrenadeKeepsFlyingAfterCarrierDeathAndUsesFriendlyFire() {
        Fixture fixture = fixture();
        long first = enemy(fixture.sim, 10, 5);
        long second = enemy(fixture.sim, 10, 6);
        observe(fixture, first, second);
        assertTrue(FragGrenadeTactics.tryCommitThrow(fixture.carrier,
                MarineSecondary.FRAG_GRENADE, fixture.sim));
        fixture.sim.world().setSecondaryActionTimer(fixture.carrier,
                MarineSecondary.FRAG_GRENADE.aimDuration() * 0.5f);
        InfantryUnitPrep.tickAimAndShortCircuit(fixture.carrier, fixture.sim);

        assertEquals(2, fixture.sim.world().secondaryAmmo(fixture.carrier));
        assertEquals(1, fixture.sim.getActiveProjectiles().size());
        Projectile projectile = fixture.sim.getActiveProjectiles().get(0);
        assertEquals("weapon.frag-grenade", projectile.sourceWeaponId);
        assertTrue(projectile.arcHeight > 0f);
        assertEquals(0, projectile.onArrival.wallDamage);
        assertFalse(projectile.onArrival.friendlyFireImmune);
        long friendly = fixture.sim.spawn(new EntitySpec("late-friendly", Faction.MARINE,
                UnitType.MARINE, (int) Math.floor(projectile.toX),
                (int) Math.floor(projectile.toY)));

        fixture.sim.applyDamage(fixture.carrier, 1000f, 1000f);
        assertFalse(fixture.sim.world().isAlive(fixture.carrier));
        fixture.sim.getShots().tickProjectiles(10f, fixture.sim::detonateNow);
        assertFalse(fixture.sim.world().isAlive(first));
        assertFalse(fixture.sim.world().isAlive(second));
        assertFalse(fixture.sim.world().isAlive(friendly));
    }

    @Test
    void hostileWarningRequiresObservationButFriendlyKnowledgeDoesNot() {
        BattleSimulation hidden = openArena(20, 12);
        int hiddenSquad = hidden.mintSquad(Faction.MARINE, UnitType.MARINE);
        long hiddenUnit = hidden.spawn(new EntitySpec("hidden-observer", Faction.MARINE,
                UnitType.MARINE, 4, 5).squad(hiddenSquad));
        for (int y = 0; y < 12; y++) hidden.getGrid().setWalkable(8, y, false);
        hidden.queueProjectile(hazard(Faction.DEFENDER, 12.5f, 5.5f,
                hidden.world().x(hiddenUnit), hidden.world().y(hiddenUnit)));
        assertFalse(FragGrenadeTactics.evadeKnownGrenade(hiddenUnit, hidden),
                "an unseen hostile throw supplies no supernatural warning");

        BattleSimulation visible = openArena(20, 12);
        int visibleSquad = visible.mintSquad(Faction.MARINE, UnitType.MARINE);
        long visibleUnit = visible.spawn(new EntitySpec("visible-observer", Faction.MARINE,
                UnitType.MARINE, 4, 5).squad(visibleSquad));
        visible.queueProjectile(hazard(Faction.DEFENDER, 7.5f, 5.5f,
                visible.world().x(visibleUnit), visible.world().y(visibleUnit)));
        assertTrue(FragGrenadeTactics.evadeKnownGrenade(visibleUnit, visible));
        assertTrue(visible.world().path(visibleUnit).length > 0);

        BattleSimulation friendly = openArena(20, 12);
        int friendlySquad = friendly.mintSquad(Faction.MARINE, UnitType.MARINE);
        long friendlyUnit = friendly.spawn(new EntitySpec("friendly-known", Faction.MARINE,
                UnitType.MARINE, 4, 5).squad(friendlySquad));
        for (int y = 0; y < 12; y++) friendly.getGrid().setWalkable(8, y, false);
        friendly.queueProjectile(hazard(Faction.MARINE, 12.5f, 5.5f,
                friendly.world().x(friendlyUnit), friendly.world().y(friendlyUnit)));
        assertTrue(FragGrenadeTactics.evadeKnownGrenade(friendlyUnit, friendly));
    }

    @Test
    void generatedFragAssetHasRealTransparency() throws Exception {
        BufferedImage image = ImageIO.read(Path.of(
                "mod/graphics/ui/armory/special-frag-grenade.png").toFile());
        assertNotNull(image);
        assertTrue(image.getColorModel().hasAlpha());
        boolean transparent = false;
        boolean visible = false;
        for (int y = 0; y < image.getHeight(); y += 8) {
            for (int x = 0; x < image.getWidth(); x += 8) {
                int alpha = image.getRGB(x, y) >>> 24;
                transparent |= alpha == 0;
                visible |= alpha > 0;
            }
        }
        assertTrue(transparent);
        assertTrue(visible);
    }

    private static Projectile hazard(Faction faction, float fromX, float fromY,
                                     float toX, float toY) {
        PendingDetonation payload = new PendingDetonation(0L, toX, toY, 1f,
                MarineSecondary.FRAG_GRENADE.aoeRadius(), 32f, 2f, 0,
                faction, true);
        return new Projectile(fromX, fromY, toX, toY, false, 1.8f,
                faction, true, 1f, payload, "weapon.frag-grenade");
    }

    private static Fixture fixture() {
        return fixture(Faction.MARINE);
    }

    private static Fixture fixture(Faction faction) {
        BattleSimulation sim = openArena(24, 18);
        int squadId = sim.mintSquad(faction, UnitType.MARINE);
        long carrier = sim.spawn(new EntitySpec("frag", faction,
                UnitType.MARINE, 5, 5).squad(squadId)
                .secondary(MarineSecondary.FRAG_GRENADE,
                        MarineSecondary.FRAG_GRENADE.startingAmmo()));
        return new Fixture(sim, sim.getSquad(squadId), carrier);
    }

    private static long enemy(BattleSimulation sim, int x, int y) {
        return enemy(sim, Faction.DEFENDER, x, y);
    }

    private static long enemy(BattleSimulation sim, Faction faction, int x, int y) {
        return sim.spawn(new EntitySpec("enemy-" + sim.liveUnitCount(),
                faction, UnitType.MARINE_RED, x, y));
    }

    private static void observe(Fixture fixture, long... enemies) {
        for (long enemy : enemies) {
            fixture.squad.observeDirectContact(enemy,
                    fixture.sim.world().cellX(enemy), fixture.sim.world().cellY(enemy),
                    fixture.sim.getSimTickIndex());
        }
        fixture.squad.publishBeliefSnapshot();
    }

    private static BattleSimulation openArena(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }

    private record Fixture(BattleSimulation sim, Squad squad, long carrier) {
    }
}
