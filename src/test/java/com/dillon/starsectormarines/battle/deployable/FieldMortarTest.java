package com.dillon.starsectormarines.battle.deployable;

import com.dillon.starsectormarines.battle.deployable.FieldMortarService.Phase;
import com.dillon.starsectormarines.battle.deployable.FieldMortarService.Tube;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.DeployableEmplacementSpec;
import com.dillon.starsectormarines.marine.SpecialAiPolicy;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The field mortar's weapon data and its crew-served life cycle, driven through a real
 * {@link BattleSimulation} on open ground with the production tube, turret and AI. Every
 * expectation is derived from the installed catalog, so retuning the mortar does not edit this
 * file.
 */
class FieldMortarTest {

    /** The nests a fort stamps (FortStage.NEST_STRUCTURES); their weapons set the reach a mortar must outrange. */
    private static final String[] NEST_STRUCTURES = {"structure.turret-heavy-mg", "structure.turret-dual-flak"};
    private static final String RIFLE = "weapon.field-rifle";
    private static final int MAP = 120;
    private static final int ROW = 40;

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
    }

    // ---- the weapon ----

    @Test
    void theTubeOutreachesARifleAndEveryNestAndHasADeadZone() {
        WeaponDef mortar = WeaponRegistry.require("weapon.field-mortar");
        assertTrue(mortar.indirectFire, "a mortar lobs onto ground it cannot see");
        assertTrue(mortar.arcHeight > 0f, "a mortar shell travels on an arc");
        assertTrue(mortar.minRange > 0f, "a tube has a dead zone");
        assertTrue(mortar.aoeRadius > 0f && mortar.wallDamage > 0, "a warhead with splash and structure damage");
        assertTrue(mortar.range > WeaponRegistry.require(RIFLE).range);
        for (String nest : NEST_STRUCTURES) {
            float reach = TurretCatalogRegistry.requireStructure(nest).mount.weapon.range;
            assertTrue(mortar.range > reach + mortar.minRange * 0.5f,
                    "outranges " + nest + " (" + reach + ") with room to stand off");
            assertTrue(mortar.minRange < mortar.range, "min range sits inside max range");
        }
    }

    @Test
    void theItemIsACrewServedDeployableThatNamesNoWeaponOfItsOwn() {
        SpecialEquipmentDef item = mortarItem();
        assertEquals(SpecialAiPolicy.CREW_SERVED_FIRE_SUPPORT, item.aiPolicy());
        assertTrue(item.isCrewServed());
        DeployableEmplacementSpec spec = item.deployableEmplacementSpec();
        assertTrue(spec.recoverable(), "a mortar is lifted again, a pod is not");
        assertTrue(spec.deployDuration() > 0f && spec.packDuration() > 0f && spec.redeployCooldown() > 0f);
        StructureDef structure = TurretCatalogRegistry.requireStructure(spec.structureId());
        assertEquals(structure.mount.weapon, item.deployedWeaponDef(),
                "the item's capability is read off the structure it sets down");
        assertTrue(structure.mount.ammoCapacity > 0, "a carried tube has a finite magazine");
    }

    @Test
    void aMountedTubeRefusesWhatIsInsideItsDeadZoneAndFiresAtWhatIsBeyondIt() {
        WeaponDef weapon = WeaponRegistry.require("weapon.field-mortar");
        int near = Math.round(weapon.minRange * 0.5f);
        int far = Math.round((weapon.minRange + weapon.range) * 0.5f);
        assertEquals(0, firedAt(near), "a target inside the dead zone is not engaged");
        assertTrue(firedAt(far) > 0, "a target between min and max range is");
        assertEquals(0, firedAt(Math.round(weapon.range) + 6), "a target beyond reach is not");
    }

    /** Rounds a free-standing field-mortar turret fires at one visible defender {@code distance} cells away over eight seconds. */
    private static int firedAt(int distance) {
        SceneBuilder builder = SceneBuilder.openGround(MAP, ROW).missionCompletion(false);
        builder.unit("target", Faction.DEFENDER, UnitType.MILITIA, 10 + distance, ROW / 2,
                spec -> spec.moveSpeed(0f).health(10_000f));
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        long gun = sim.spawn(MapTurret.create("gun", Faction.MARINE, "structure.deployable-field-mortar", 10, ROW / 2));
        for (int i = 0; i < 240; i++) sim.advance(BattleSimulation.TICK_DT);
        return sim.telemetry().roundsFired(gun);
    }

    // ---- the crew-served life cycle ----

    @Test
    void aHaltedSectionWithAKnownTargetInBandSetsTheTubeDownAndFiresForItsSquad() {
        SceneWorld world = section(30);
        BattleSimulation sim = world.sim();
        long gunner = world.members("team")[0];
        SpecialEquipmentDef item = mortarItem();

        assertNull(sim.fieldMortars().tubeOfCarrier(gunner));
        Tube tube = awaitTube(sim, gunner, 120);
        assertNotNull(tube, "the section halted with a target it could see in band, and set the tube down");
        assertEquals(Phase.SETTING_UP, tube.phase());
        assertEquals(0L, tube.entityId(), "nothing stands on the field while it is being set up");

        advanceSeconds(sim, item.deployableEmplacementSpec().deployDuration() + 0.5f);
        assertEquals(Phase.SERVING, tube.phase());
        long entity = sim.resolveUnit(tube.entityId());
        assertTrue(entity != 0L, "the emplacement exists once set-up is done");
        assertTrue(sim.identity().type(entity).isTurret());
        assertTrue(Math.abs(sim.world().cellX(gunner) - sim.world().cellX(entity)) <= 1,
                "set down where the gunner stands (soft collision may nudge him a cell)");
        assertEquals(tube.squadId, world.squadId("team"));

        advanceSeconds(sim, 20f);
        assertTrue(tube.roundsFired() > 0, "the tube fired on what its squad believed");
        assertTrue(tube.rounds() < tube.structure.mount.ammoCapacity, "and the rounds came out of its magazine");
        assertEquals(tube.roundsFired(), tube.structure.mount.ammoCapacity - tube.rounds());
        long victim = world.members("enemy")[0];
        assertTrue(!sim.world().isAlive(victim) || sim.world().hp(victim) < sim.world().maxHp(victim),
                "the target was hurt by a round it could never have fired back at");
    }

    @Test
    void nothingIsSetDownForATargetInsideTheDeadZoneOrOneNobodyHasSeen() {
        int deadZone = Math.round(WeaponRegistry.require("weapon.field-mortar").minRange * 0.5f);
        assertNull(runSection(deadZone), "a target on top of the section is a job for rifles");
        int beyondSight = 44;
        assertNull(runSection(beyondSight), "a target nobody has seen is not a target");
    }

    @Test
    void aTubeWhoseGunnerDiesIsLostWithHim() {
        SceneWorld world = section(30);
        BattleSimulation sim = world.sim();
        long gunner = world.members("team")[0];
        Tube tube = awaitTube(sim, gunner, 120);
        advanceSeconds(sim, mortarItem().deployableEmplacementSpec().deployDuration() + 0.5f);
        long entity = sim.resolveUnit(tube.entityId());
        assertTrue(entity != 0L);

        sim.applyExternalDamage(gunner, 10_000f, Float.MAX_VALUE);
        advanceSeconds(sim, 1f);
        assertNull(sim.fieldMortars().tubeOfCarrier(gunner));
        assertEquals(0L, sim.resolveUnit(tube.entityId()), "the emplacement went with its gunner");
        assertEquals(1, sim.fieldMortars().lostWithGunner());
    }

    @Test
    void aTubeShotOutFromUnderItsGunnerReleasesHimToFight() {
        SceneWorld world = section(30);
        BattleSimulation sim = world.sim();
        long gunner = world.members("team")[0];
        Tube tube = awaitTube(sim, gunner, 120);
        advanceSeconds(sim, mortarItem().deployableEmplacementSpec().deployDuration() + 0.5f);
        long entity = sim.resolveUnit(tube.entityId());

        sim.applyExternalDamage(entity, 10_000f, Float.MAX_VALUE);
        advanceSeconds(sim, 1f);
        assertNull(sim.fieldMortars().tubeOfCarrier(gunner));
        assertEquals(1, sim.fieldMortars().destroyedStanding());
        assertTrue(sim.world().isAlive(gunner));
        assertTrue(sim.world().secondaryActionTimer(gunner) <= 0f, "the gunner is no longer held at a tube that is not there");
        assertTrue(sim.world().secondaryCooldownTimer(gunner) > 0f, "and does not set up again in the same breath");
    }

    @Test
    void aTubeWithNothingToShootAtIsLiftedKeepingTheRoundsItHasLeft() {
        SceneWorld world = section(30);
        BattleSimulation sim = world.sim();
        long gunner = world.members("team")[0];
        DeployableEmplacementSpec spec = mortarItem().deployableEmplacementSpec();
        Tube tube = awaitTube(sim, gunner, 120);
        advanceSeconds(sim, spec.deployDuration() + 0.5f);
        assertEquals(Phase.SERVING, tube.phase());
        // The work is done: remove everything it could shoot at.
        for (long id : world.members("enemy")) sim.applyExternalDamage(id, 10_000f, Float.MAX_VALUE);
        int rounds = tube.rounds();

        advanceSeconds(sim, FieldMortarService.IDLE_PACK_SECONDS + spec.packDuration() + 3f);
        assertNull(sim.fieldMortars().tubeOfCarrier(gunner), "the gunner lifted the tube");
        assertEquals(1, sim.fieldMortars().packs());
        assertEquals(0L, sim.resolveUnit(tube.entityId()), "the emplacement is off the field");
        assertEquals(rounds, sim.fieldMortars().stashFor(gunner, tube.structure),
                "lifting a half-spent tube does not refill it");
        assertTrue(sim.world().secondaryCooldownTimer(gunner) > 0f);
    }

    @Test
    void theCrewWillNotCallARoundOntoItsOwn() {
        SceneWorld world = section(30);
        BattleSimulation sim = world.sim();
        // A friendly marine standing beside the target: a splash would catch him.
        long enemy = world.members("enemy")[0];
        sim.spawn(new EntitySpec("friend", Faction.MARINE, UnitType.MARINE,
                sim.world().cellX(enemy) + 1, sim.world().cellY(enemy)).moveSpeed(0f).health(1_000_000f));
        long gunner = world.members("team")[0];
        Tube tube = awaitTube(sim, gunner, 120);
        advanceSeconds(sim, mortarItem().deployableEmplacementSpec().deployDuration() + 6f);
        assertEquals(0, tube.roundsFired(), "every enemy has a friendly inside the warhead's reach");
    }

    // ---- fixtures ----

    /** The first tube the section's gunner registers within {@code ticks}, or null. */
    private static Tube awaitTube(BattleSimulation sim, long gunner, int ticks) {
        for (int i = 0; i < ticks; i++) {
            sim.advance(BattleSimulation.TICK_DT);
            Tube tube = sim.fieldMortars().tubeOfCarrier(gunner);
            if (tube != null) return tube;
        }
        return null;
    }

    private static Tube runSection(int distance) {
        SceneWorld world = section(distance);
        BattleSimulation sim = world.sim();
        return awaitTube(sim, world.members("team")[0], 360);
    }

    private static void advanceSeconds(BattleSimulation sim, float seconds) {
        int ticks = Math.max(1, Math.round(seconds / BattleSimulation.TICK_DT));
        for (int i = 0; i < ticks; i++) sim.advance(BattleSimulation.TICK_DT);
    }

    /** A three-marine section (the first carries the tube) and a stationary rifle squad {@code distance} cells east of it. */
    private static SceneWorld section(int distance) {
        return SceneBuilder.openGround(MAP, ROW).missionCompletion(false)
                .squad("team").faction(Faction.MARINE).size(3).at(12, ROW / 2).primary(RIFLE)
                .special(SpecialEquipmentRegistry.FIELD_MORTAR_ID, 1).stationary().done()
                .squad("enemy").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(3)
                .at(12 + distance, ROW / 2).primary(RIFLE).stationary().done()
                .build();
    }

    private static SpecialEquipmentDef mortarItem() {
        return SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FIELD_MORTAR_ID);
    }
}
