package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import java.util.List;

/** Shared cargo assemblies for authored magazines, fitted stores and civilian warehouses. */
public final class LogisticsKit {
    private LogisticsKit() {}

    public enum Stock {
        CASES("cases"), CARTONS("cartons");
        private final String id;
        Stock(String id) { this.id = "main-stores." + id; }
    }

    /** Dimensions describe the base; hosts reserve handling space separately. */
    public record Module(int width, int height, String base, List<LayoutOp.Layer> layers) {
        public Module { layers = List.copyOf(layers); }
        public LayoutOp.Assembly at(int x, int y) { return new LayoutOp.Assembly(x, y, base, layers); }
        public boolean place(RoomFloor floor, int x, int y) {
            return floor.placeAssembly(x, y, base, layers);
        }
    }

    public static Module rack(Stock stock) {
        return new Module(2, 6, "main-stores.rack", List.of(
                new LayoutOp.Layer(0, 0, stock.id), new LayoutOp.Layer(0, 2, stock.id),
                new LayoutOp.Layer(0, 4, stock.id), new LayoutOp.Layer(0, 0, "main-stores.frame")));
    }

    public static Module pallet(Stock stock) {
        return new Module(2, 2, "main-stores.pallet", List.of(new LayoutOp.Layer(0, 0, stock.id)));
    }

    /** A sealed 1.0 x 0.6 m cabinet, its doors on its bottom edge: stand it where it is worked from the row below. */
    public static Module ammunition() { return new Module(2, 1, "armory-workshop.ammo", List.of()); }
    public static Module desk(boolean packing) {
        return new Module(3, 1, packing ? "main-stores.packing" : "main-stores.tally", List.of());
    }
}
