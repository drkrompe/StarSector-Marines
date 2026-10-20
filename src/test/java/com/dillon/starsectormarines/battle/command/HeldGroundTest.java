package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.FightingPositionRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The attacker-side caller of the reactive assignment tier ({@link HeldGround}) on hand-built trenches and a hand-set
 * pressure field: no world, no battle. Trench 0 (x 10..25) and trench 1 (x 40..55) belong to fort 7, which the
 * marines hold; trench 2 (x 110..125) belongs to fort 9, which they do not.
 */
class HeldGroundTest {

    private static final int W = 160;
    private static final int H = 40;
    private static final int BLOCK = 8;
    private static final int BW = 20;
    private static final HeldGround.Reach ANYWHERE = (fx, fy, x, y) -> true;

    private final List<Doodad> props = new ArrayList<>();
    private final Map<Integer, Float> field = new HashMap<>();
    private GarrisonableRegistry registry;
    private HeldGround hold;

    private void trench(int fromX, int toX, int y) {
        for (int x = fromX; x <= toX; x++) {
            DoodadDef def = new DoodadDef("doodad.test.trench", "sheet", 0, 0, DoodadCover.HEAVY, 0.3f,
                    1, 1, null, 0, null, FightingPositionRole.TRENCH);
            props.add(new Doodad(x, y, def));
        }
    }

    @BeforeEach
    void setUp() {
        trench(10, 25, 12);
        trench(40, 55, 12);
        trench(110, 125, 12);
        registry = GarrisonableRegistry.build(FightingPositionIndex.build(W, H, props, (x, y) -> true),
                W, H, BLOCK, new int[]{7, 32, 20, 9, 118, 20});
        hold = new HeldGround();
        field.clear();
    }

    private void press(int place, float value) {
        for (int block : registry.coveredBlocks(place)) field.put(block, value);
    }

    private List<HeldGround.Decision> pulse(List<GarrisonDirector.Garrison> held, int... contactsAt) {
        int[] cells = new int[contactsAt.length * 2];
        for (int i = 0; i < contactsAt.length; i++) {
            cells[2 * i] = registry.centreX(contactsAt[i]);
            cells[2 * i + 1] = registry.centreY(contactsAt[i]);
        }
        return hold.standOrRelease(registry, (bx, by) -> field.getOrDefault(by * BW + bx, 0f), cells,
                contactsAt.length, held, Map.of(), Faction.MARINE, List.of(), null);
    }

    private static List<HeldGround.Body> lanePool(int count) {
        List<HeldGround.Body> free = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            free.add(new HeldGround.Body(100 + i, 30 + 4 * i, 14, 30 + 4 * i, 14, 6, 6, false, null));
        }
        return free;
    }

    private static DefenceStructureFact aspect(int fortId, Faction holder) {
        return new DefenceStructureFact(DefenceStructureFact.Kind.FIRE_CONTROL, 0, 0, holder, Faction.DEFENDER, false, 4,
                0, 0, fortId, DefenceStructureFact.Family.BATTERY_FORT);
    }

    @Test
    void theShareIsAQuarterOfThePoolAndNoneOfASmallOne() {
        assertEquals(0, HeldGround.cap(2), "two squads are all attack");
        assertEquals(1, HeldGround.cap(3));
        assertEquals(1, HeldGround.cap(7));
        assertEquals(2, HeldGround.cap(8));
        assertEquals(4, HeldGround.cap(16));
    }

    @Test
    void aFortIsHeldOnlyWhileNoneOfItsAspectsIsHostile() {
        Set<Integer> held = HeldGround.heldForts(Faction.MARINE, List.of(
                aspect(7, Faction.MARINE), aspect(7, Faction.MARINE),
                aspect(9, Faction.MARINE), aspect(9, Faction.DEFENDER)));
        assertEquals(Set.of(7), held, "fort 9 still has an aspect the defenders hold");
    }

    @Test
    void onlyAPressedRunOfAHeldFortIsManned() {
        press(0, GarrisonDirector.START_PRESSURE);
        press(2, GarrisonDirector.START_PRESSURE + 5f);
        pulse(List.of(), 0, 2);
        List<HeldGround.Sent> sent = hold.man(registry, lanePool(6), 2, Set.of(7), ANYWHERE);

        assertEquals(1, sent.size(), "the hotter run belongs to a fort the side does not hold");
        assertEquals(0, sent.get(0).garrisonable());
        assertTrue(sent.get(0).order().isGarrison(), "the order is the ordinary run-scoped area defence");
    }

    @Test
    void theBudgetBoundsWhatIsSentEvenWithMorePlacesPressed() {
        press(0, GarrisonDirector.START_PRESSURE);
        press(1, GarrisonDirector.START_PRESSURE);
        pulse(List.of(), 0, 1);
        assertEquals(1, hold.man(registry, lanePool(6), 1, Set.of(7), ANYWHERE).size());
    }

    @Test
    void groundHeldByAnotherTitleIsMannedWithNoFortTaken() {
        // Trench 2 fronts no fort the side holds; the caller answers that it holds it anyway, as it does the apron at
        // the door of a compound it has taken.
        press(2, GarrisonDirector.START_PRESSURE);
        pulse(List.of(), 2);
        List<HeldGround.Body> near = new ArrayList<>();
        for (int i = 0; i < 6; i++) near.add(new HeldGround.Body(200 + i, 105 + 3 * i, 14, 105 + 3 * i, 14, 6, 6, false, null));
        List<HeldGround.Sent> sent = hold.man(registry, near, 2, Set.of(), place -> place == 2, ANYWHERE);
        assertEquals(1, sent.size());
        assertEquals(2, sent.get(0).garrisonable());
    }

    @Test
    void aQuietTakenFortSendsNobody() {
        pulse(List.of());
        assertTrue(hold.man(registry, lanePool(6), 2, Set.of(7), ANYWHERE).isEmpty());
    }

    @Test
    void aHolderIsGivenBackOnlyAfterTheDwellOfCoolPulses() {
        press(0, GarrisonDirector.START_PRESSURE);
        pulse(List.of(), 0);
        HeldGround.Sent sent = hold.man(registry, lanePool(6), 1, Set.of(7), ANYWHERE).get(0);
        GarrisonDirector.Garrison onRun = new GarrisonDirector.Garrison(sent.squadId(), 0, true, true, 6,
                registry.centreX(0), registry.centreY(0), 6);
        field.clear();
        for (int i = 1; i < GarrisonDirector.DWELL_PULSES; i++) {
            assertEquals(HeldGround.Verdict.STAND, pulse(List.of(onRun)).get(0).verdict(), "cool pulse " + i);
            hold.man(registry, List.of(), 0, Set.of(7), ANYWHERE);
        }
        assertEquals(HeldGround.Verdict.RELEASE, pulse(List.of(onRun)).get(0).verdict());
    }
}
