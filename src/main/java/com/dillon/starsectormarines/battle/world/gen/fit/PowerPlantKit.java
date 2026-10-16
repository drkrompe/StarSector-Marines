package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import java.util.List;

/** Full-size power equipment shared by fort reactor halls and compact generator houses. */
public final class PowerPlantKit {
    private PowerPlantKit() {}

    public static final Machine TURBINE = new Machine("power-plant.turbine", 3, 3);
    public static final Machine COOLANT = new Machine("power-plant.coolant", 2, 3);

    /** Skids have service access at both short ends; hosts own the surrounding aisles. */
    public record Machine(String id, int width, int height) {
        public LayoutOp.Assembly at(int x, int y) {
            return new LayoutOp.Assembly(x, y, id, List.of());
        }

        public void station(RoomFloor floor, int x, int y, int standX, int standY, int fixtureX, int fixtureY) {
            if (floor.placeAssembly(x, y, id, List.of()))
                InstallationWorkshopFitting.task(floor, standX, standY, fixtureX, fixtureY, Affordance.TEND);
        }
    }
}
