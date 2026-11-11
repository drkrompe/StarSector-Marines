package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.salvage.SalvageCache;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A plant site's supply finds are its stores' own loads, the far share from finished goods. */
class StoreFindsTest {

    private static final String RAW = RuinsDelve.Interior.spaceName("raw-stock");
    private static final String GOODS = RuinsDelve.Interior.spaceName("finished-goods");
    private static final String SHOP = RuinsDelve.Interior.spaceName("maintenance");

    /** A 2x2 load at (x, y); every cell outside a load is open floor. */
    private static RuinsDelve.Load load(int x, int y, String goods, String space) {
        return new RuinsDelve.Load(new RuinsDelve.Cell(x, y), new RuinsDelve.Area(x, y, x + 1, y + 1),
                "doodad.ruins-store-" + goods, space);
    }

    private static boolean open(List<RuinsDelve.Load> loads, int x, int y) {
        for (RuinsDelve.Load l : loads) {
            RuinsDelve.Area a = l.footprint();
            if (x >= a.left() && x <= a.right() && y >= a.bottom() && y <= a.top()) return false;
        }
        return true;
    }

    private static List<RuinsDelve.Load> plant() {
        List<RuinsDelve.Load> loads = new ArrayList<>();
        for (int i = 0; i < 4; i++) loads.add(load(10 + i * 5, 40, i % 2 == 0 ? "supplies" : "domestic-goods", GOODS));
        loads.add(load(10, 10, "metals", RAW));
        loads.add(load(15, 10, "transplutonics", RAW));
        loads.add(load(20, 10, "fuel", RAW));
        loads.add(load(30, 20, "heavy-machinery", SHOP));
        return loads;
    }

    @Test
    void theFarShareComesFromFinishedGoodsAndTheRestFromTheOtherStores() {
        List<RuinsDelve.Load> loads = plant();
        List<StoreFinds.Pick> picks = StoreFinds.pick(loads, 5, 7L, (x, y) -> open(loads, x, y),
                StoreFinds.BASE_PRICE::get);

        assertEquals(5, picks.size());
        long far = picks.stream().filter(p -> GOODS.equals(p.load().space())).count();
        assertEquals(4, far, "two in three of five, rounded up, are finished goods");
        assertEquals(5, picks.stream().map(StoreFinds.Pick::load).distinct().count(), "a load is taken once");
    }

    @Test
    void aStoreShortOfLoadsIsToppedUpFromTheOther() {
        List<RuinsDelve.Load> loads = plant().stream().filter(l -> !GOODS.equals(l.space())).toList();
        List<StoreFinds.Pick> picks = StoreFinds.pick(loads, 3, 1L, (x, y) -> open(loads, x, y),
                StoreFinds.BASE_PRICE::get);
        assertEquals(3, picks.size(), "no finished goods: the near stores carry the whole count");

        List<StoreFinds.Pick> all = StoreFinds.pick(plant(), 20, 1L, (x, y) -> true, StoreFinds.BASE_PRICE::get);
        assertEquals(8, all.size(), "never more finds than loads");
    }

    @Test
    void eachFindIsWorthAboutOneFieldCacheOfItsOwnGoods() {
        assertEquals(6, StoreFinds.quantity(1200, 200), "transplutonics: a few");
        assertEquals(120, StoreFinds.quantity(1200, 10), "ore: many");
        assertEquals(1, StoreFinds.quantity(10, 5000), "never nothing");

        List<RuinsDelve.Load> loads = List.of(load(10, 10, "transplutonics", RAW));
        SalvageCache find = StoreFinds.caches(StoreFinds.pick(loads, 1, 0L, (x, y) -> open(loads, x, y),
                StoreFinds.BASE_PRICE::get)).get(0);
        assertEquals("rare_metals", find.goods());
        assertEquals("6 Transplutonics", find.label());
    }

    @Test
    void aFindIsRecoveredFromOpenFloorBesideItsLoad() {
        RuinsDelve.Area bay = new RuinsDelve.Area(10, 10, 11, 13);
        int[] at = StoreFinds.beside(bay, (x, y) -> x == 12 && y == 11);
        assertArrayEquals(new int[] {12, 11}, at);
        assertNull(StoreFinds.beside(bay, (x, y) -> x == 12 && y == 14), "never a corner");

        List<RuinsDelve.Load> walled = List.of(load(10, 10, "metals", RAW));
        assertTrue(StoreFinds.pick(walled, 1, 0L, (x, y) -> false, StoreFinds.BASE_PRICE::get).isEmpty(),
                "a load with no open floor beside it is no find");
    }
}
