package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.contact.CloseContactService;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural cover for the close-contact activation. Expected magnitudes are
 * read back from the registries rather than written here, so this file pins
 * behaviour and never becomes a second copy of the authored balance table.
 */
class CloseContactWeaponTest {

    private static final float EPS = 1e-3f;

    private static SpecialEquipmentDef cutter() {
        return SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.BREACHING_CUTTER_ID);
    }

    private static SpecialEquipmentDef blade() {
        return SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.VIBRO_BLADE_ID);
    }

    private static BattleSimulation openArena(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }

    private static long carrier(BattleSimulation sim, SpecialEquipmentDef tool,
                                Faction faction, int x, int y) {
        UnitType type = faction == Faction.MARINE ? UnitType.MARINE : UnitType.MARINE_RED;
        return sim.spawn(new EntitySpec("carrier-" + sim.liveUnitCount(), faction, type, x, y)
                .specialEquipment(tool, 0));
    }

    private static long infantry(BattleSimulation sim, Faction faction, int x, int y) {
        UnitType type = faction == Faction.MARINE ? UnitType.MARINE : UnitType.MARINE_RED;
        return sim.spawn(new EntitySpec("soft-" + sim.liveUnitCount(), faction, type, x, y));
    }

    private static long turret(BattleSimulation sim, int x, int y) {
        return sim.spawn(MapTurret.create("turret-" + sim.liveUnitCount(),
                Faction.DEFENDER, TurretCatalogRegistry.VULCAN_STRUCTURE_ID, x, y));
    }

    /** Runs the committed channel to its end without advancing the whole sim. */
    private static void runChannel(long unit, SpecialEquipmentDef tool, BattleSimulation sim) {
        int ticks = (int) Math.ceil(tool.closeContactSpec().channelSeconds()
                / BattleSimulation.TICK_DT) + 1;
        for (int i = 0; i < ticks; i++) {
            if (!InfantryUnitPrep.tickAimAndShortCircuit(unit, sim)) return;
        }
    }

    @Test
    void aMarineCarryingNeitherToolHasNoContactAttack() {
        BattleSimulation sim = openArena(16, 12);
        long plain = infantry(sim, Faction.MARINE, 5, 5);
        long enemy = infantry(sim, Faction.DEFENDER, 6, 5);

        assertFalse(sim.world().hasSecondaryWeapon(plain));
        assertFalse(InfantryUnitPrep.tryOpportunitySpecial(plain, sim));
        assertFalse(sim.applyContactStrike(plain, enemy),
                "contact damage exists only as a carried item, never as a universal melee");
        assertFalse(sim.applyContactBreach(plain, 6, 5));
        assertEquals(UnitType.MARINE_RED.maxHp, sim.world().hp(enemy), EPS);
    }

    @Test
    void anotherSpecialItemStillCannotStrikeAtContact() {
        BattleSimulation sim = openArena(16, 12);
        long satchelCarrier = carrier(sim,
                SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID),
                Faction.MARINE, 5, 5);
        long enemy = infantry(sim, Faction.DEFENDER, 6, 5);

        assertFalse(sim.applyContactStrike(satchelCarrier, enemy));
        assertEquals(UnitType.MARINE_RED.maxHp, sim.world().hp(enemy), EPS);
    }

    @Test
    void theBladeStrikesOnlyAnAdjacentLivingInfantryContact() {
        BattleSimulation sim = openArena(20, 12);
        long marine = carrier(sim, blade(), Faction.MARINE, 5, 5);
        long enemy = infantry(sim, Faction.DEFENDER, 6, 5);
        float before = sim.world().hp(enemy);

        assertTrue(InfantryUnitPrep.tryOpportunitySpecial(marine, sim));
        assertEquals(enemy, sim.closeContact().reservedContactOf(marine));
        runChannel(marine, blade(), sim);
        sim.advance(BattleSimulation.TICK_DT);

        // A blade strike carries more than an unarmored person has left, so the
        // shared calculation is legible in what it credits: applied damage is
        // clamped to what the target actually had, and the kill is attributed.
        assertFalse(sim.world().isAlive(enemy));
        assertTrue(blade().damage() > before);
        assertEquals(before, sim.telemetry().damageDealt(marine), EPS,
                "the payload resolves through the shared durability calculation");
        assertEquals(1, sim.telemetry().kills(marine));
        assertEquals(blade().closeContactSpec().cooldownSeconds(),
                sim.world().secondaryCooldownTimer(marine), 0.05f);
    }

    @Test
    void theBreachingChannelAppliesBoundedContactWorkWithNoSplash() {
        BattleSimulation sim = openArena(24, 12);
        long breacher = carrier(sim, cutter(), Faction.MARINE, 5, 5);
        long emplacement = turret(sim, 6, 5);
        long neighbour = turret(sim, 7, 5);
        float before = sim.world().hp(emplacement);
        float armorBefore = sim.world().armor(emplacement);
        float neighbourBefore = sim.world().hp(neighbour);
        float neighbourArmorBefore = sim.world().armor(neighbour);

        assertTrue(InfantryUnitPrep.tryOpportunitySpecial(breacher, sim));
        runChannel(breacher, cutter(), sim);
        sim.advance(BattleSimulation.TICK_DT);

        float armorLoss = armorBefore - sim.world().armor(emplacement);
        float structureLoss = before - sim.world().hp(emplacement);
        assertTrue(armorLoss > 0f, "the payload meets the target's armor first");
        assertTrue(structureLoss > 0f && structureLoss < cutter().damage(),
                "structure loss is what the shared durability calculation let through");
        assertEquals(armorLoss + structureLoss, sim.telemetry().damageDealt(breacher), EPS,
                "attribution credits resolved loss, not the requested payload");
        assertEquals(neighbourBefore, sim.world().hp(neighbour), EPS,
                "a cutter is local contact work with no area blast");
        assertEquals(neighbourArmorBefore, sim.world().armor(neighbour), EPS);
    }

    @Test
    void aBladeCarrierOutOfReachNeverCommitsAndNeverPathsToContact() {
        BattleSimulation sim = openArena(20, 12);
        long marine = carrier(sim, blade(), Faction.MARINE, 5, 5);
        infantry(sim, Faction.DEFENDER, 11, 5);

        assertFalse(InfantryUnitPrep.tryOpportunitySpecial(marine, sim));
        assertEquals(0, sim.world().path(marine).length,
                "a contact opportunity may never author an approach");
    }

    @Test
    void theBladeRefusesHardenedAndNonCombatantContacts() {
        BattleSimulation sim = openArena(20, 12);
        long marine = carrier(sim, blade(), Faction.MARINE, 5, 5);
        long emplacement = turret(sim, 6, 5);
        long civilian = sim.spawn(new EntitySpec("bystander", Faction.CIVILIAN,
                UnitType.CIVILIAN, 5, 6));

        assertFalse(InfantryUnitPrep.tryOpportunitySpecial(marine, sim));
        assertFalse(sim.applyContactStrike(marine, emplacement));
        assertFalse(sim.applyContactStrike(marine, civilian));
    }

    @Test
    void theBreacherRefusesSoftInfantryAndTakesTheHardenedContact() {
        BattleSimulation sim = openArena(20, 12);
        long soft = carrier(sim, cutter(), Faction.MARINE, 5, 8);
        infantry(sim, Faction.DEFENDER, 6, 8);
        assertFalse(InfantryUnitPrep.tryOpportunitySpecial(soft, sim),
                "a breaching tool is not an anti-personnel weapon");

        long breacher = carrier(sim, cutter(), Faction.MARINE, 5, 3);
        long emplacement = turret(sim, 6, 3);
        assertTrue(InfantryUnitPrep.tryOpportunitySpecial(breacher, sim));
        assertEquals(emplacement, sim.closeContact().reservedContactOf(breacher));
    }

    @Test
    void contactHasNoReachThroughABlockedEdge() {
        BattleSimulation sim = openArena(20, 12);
        long marine = carrier(sim, blade(), Faction.MARINE, 5, 5);
        long enemy = infantry(sim, Faction.DEFENDER, 6, 5);
        sim.getGrid().blockSharedEdge(5, 5, Direction.E);

        assertFalse(InfantryUnitPrep.tryOpportunitySpecial(marine, sim));
        assertFalse(sim.applyContactStrike(marine, enemy));

        sim.getGrid().openSharedEdge(5, 5, Direction.E);
        assertTrue(InfantryUnitPrep.tryOpportunitySpecial(marine, sim),
                "the same contact becomes legal once the barrier between the cells opens");
    }

    @Test
    void oneContactAcceptsOnlyOneCommittedCarrier() {
        BattleSimulation sim = openArena(20, 12);
        long first = carrier(sim, blade(), Faction.MARINE, 5, 5);
        long second = carrier(sim, blade(), Faction.MARINE, 6, 6);
        long enemy = infantry(sim, Faction.DEFENDER, 6, 5);

        assertTrue(InfantryUnitPrep.tryOpportunitySpecial(first, sim));
        assertFalse(InfantryUnitPrep.tryOpportunitySpecial(second, sim),
                "two carriers must not spend two payloads on the same contact");
        assertEquals(enemy, sim.closeContact().reservedContactOf(first));
        assertEquals(0L, sim.closeContact().reservedContactOf(second));
    }

    @Test
    void separationCancelsTheChannelBeforeItsPayload() {
        BattleSimulation sim = openArena(24, 12);
        long breacher = carrier(sim, cutter(), Faction.MARINE, 5, 5);
        long emplacement = turret(sim, 6, 5);
        float before = sim.world().hp(emplacement);
        assertTrue(InfantryUnitPrep.tryOpportunitySpecial(breacher, sim));

        sim.world().setCellPos(emplacement, 15, 5);
        runChannel(breacher, cutter(), sim);
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(before, sim.world().hp(emplacement), EPS);
        assertEquals(0f, sim.world().secondaryCooldownTimer(breacher), EPS,
                "an interrupted channel costs the carrier nothing");
        assertEquals(0L, sim.closeContact().reservedContactOf(breacher));
    }

    @Test
    void aDeadContactReleasesItsClaimWithoutSpendingThePayload() {
        BattleSimulation sim = openArena(20, 12);
        long marine = carrier(sim, blade(), Faction.MARINE, 5, 5);
        long enemy = infantry(sim, Faction.DEFENDER, 6, 5);
        assertTrue(InfantryUnitPrep.tryOpportunitySpecial(marine, sim));

        sim.applyExternalDamage(enemy, UnitType.MARINE_RED.maxHp * 4f, 100f);
        assertFalse(sim.world().isAlive(enemy));
        runChannel(marine, blade(), sim);

        assertEquals(0L, sim.closeContact().reservedContactOf(marine));
        assertEquals(0f, sim.world().secondaryCooldownTimer(marine), EPS);
        assertEquals(0f, sim.world().secondaryActionTimer(marine), EPS);
    }

    @Test
    void aDeadCarrierReleasesItsClaimForTheNextCarrier() {
        BattleSimulation sim = openArena(20, 12);
        long first = carrier(sim, blade(), Faction.MARINE, 5, 5);
        long second = carrier(sim, blade(), Faction.MARINE, 6, 6);
        long enemy = infantry(sim, Faction.DEFENDER, 6, 5);
        assertTrue(InfantryUnitPrep.tryOpportunitySpecial(first, sim));

        sim.applyExternalDamage(first, UnitType.MARINE.maxHp * 4f, 100f);
        assertFalse(sim.world().isAlive(first));
        sim.closeContact().tick(sim);

        assertTrue(InfantryUnitPrep.tryOpportunitySpecial(second, sim));
        assertEquals(enemy, sim.closeContact().reservedContactOf(second));
    }

    @Test
    void aHigherPrioritySurvivalResponseCancelsTheChannel() {
        BattleSimulation sim = openArena(24, 12);
        long breacher = carrier(sim, cutter(), Faction.MARINE, 5, 5);
        long emplacement = turret(sim, 6, 5);
        int squadId = sim.mintSquad(Faction.MARINE, breacher);
        sim.squad().assignSquad(breacher, squadId);
        float before = sim.world().hp(emplacement);
        assertTrue(InfantryUnitPrep.tryOpportunitySpecial(breacher, sim));

        Squad squad = sim.getSquad(squadId);
        squad.moraleBroken = true;
        runChannel(breacher, cutter(), sim);

        assertEquals(before, sim.world().hp(emplacement), EPS);
        assertEquals(0f, sim.world().secondaryCooldownTimer(breacher), EPS);
        assertEquals(0L, sim.closeContact().reservedContactOf(breacher));
    }

    @Test
    void aBreacherCutsOnlyAnAuthoredBreachPoint() {
        BattleSimulation sim = openArena(24, 12);
        wall(sim, 6, 5);
        wall(sim, 5, 6);
        long breacher = carrier(sim, cutter(), Faction.MARINE, 5, 5);

        assertFalse(InfantryUnitPrep.tryOpportunitySpecial(breacher, sim),
                "an ordinary adjacent wall is not a breaching target");
        assertFalse(sim.applyContactBreach(breacher, 6, 5));
        assertTrue(sim.getGrid().isWalkable(6, 5) == false);

        sim.closeContact().registerBreachPoint(6, 5);
        assertTrue(InfantryUnitPrep.tryOpportunitySpecial(breacher, sim));
        assertEquals(CloseContactService.breachPointKey(6, 5),
                sim.closeContact().reservedContactOf(breacher));
        runChannel(breacher, cutter(), sim);

        assertTrue(sim.getGrid().isWalkable(6, 5), "the authored breach point opens");
        assertFalse(sim.getGrid().isWalkable(5, 6),
                "the unauthored wall beside it is untouched");
        assertFalse(sim.closeContact().isBreachPoint(6, 5));
        assertEquals(cutter().closeContactSpec().cooldownSeconds(),
                sim.world().secondaryCooldownTimer(breacher), 0.05f);
    }

    @Test
    void aBreachPointIsNotReachableFromDiagonalOrDistantCells() {
        BattleSimulation sim = openArena(24, 12);
        wall(sim, 7, 6);
        sim.closeContact().registerBreachPoint(7, 6);
        long diagonal = carrier(sim, cutter(), Faction.MARINE, 6, 5);
        long distant = carrier(sim, cutter(), Faction.MARINE, 3, 3);

        assertFalse(InfantryUnitPrep.tryOpportunitySpecial(diagonal, sim));
        assertFalse(InfantryUnitPrep.tryOpportunitySpecial(distant, sim));
        assertTrue(sim.closeContact().isBreachPoint(7, 6));
    }

    @Test
    void defenderCarriersFollowTheSamePolicyAsPlayerCarriers() {
        BattleSimulation sim = openArena(20, 12);
        long defender = carrier(sim, blade(), Faction.DEFENDER, 5, 5);
        long marine = infantry(sim, Faction.MARINE, 6, 5);
        long friendly = infantry(sim, Faction.DEFENDER, 5, 4);
        float before = sim.world().hp(marine);
        float friendlyBefore = sim.world().hp(friendly);

        assertTrue(InfantryUnitPrep.tryOpportunitySpecial(defender, sim));
        assertEquals(marine, sim.closeContact().reservedContactOf(defender),
                "a defender never reaches for its own side");
        runChannel(defender, blade(), sim);
        sim.advance(BattleSimulation.TICK_DT);

        assertFalse(sim.world().isAlive(marine));
        assertEquals(before, sim.telemetry().damageDealt(defender), EPS);
        assertEquals(friendlyBefore, sim.world().hp(friendly), EPS);
    }

    @Test
    void generatedContactToolIconsHaveRealTransparency() throws Exception {
        for (Path path : new Path[]{
                Path.of("mod", cutter().armoryIconPath()),
                Path.of("mod", blade().armoryIconPath())}) {
            BufferedImage image = ImageIO.read(path.toFile());
            assertNotNull(image, path + " must decode");
            assertTrue(image.getColorModel().hasAlpha(), path + " must carry alpha");
            boolean transparent = false;
            boolean visible = false;
            for (int y = 0; y < image.getHeight(); y += 8) {
                for (int x = 0; x < image.getWidth(); x += 8) {
                    int alpha = image.getRGB(x, y) >>> 24;
                    transparent |= alpha == 0;
                    visible |= alpha > 0;
                }
            }
            assertTrue(transparent, path + " must include transparent background pixels");
            assertTrue(visible, path + " must include visible equipment pixels");
        }
    }

    private static void wall(BattleSimulation sim, int x, int y) {
        NavigationGrid grid = sim.getGrid();
        grid.setWalkable(x, y, false);
        grid.setWallHp(x, y, 100);
        sim.getTopology().setWall(x, y, true);
    }
}
