package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Small inhabited buildings have a job and working space, even when a full room kit cannot fit. */
final class CompactBuildingFurnishing {
    private CompactBuildingFurnishing() {}

    static BuildingWorkGroups.Group single(String name) {
        return new BuildingWorkGroups.Group(1, 2, List.of(
                new BuildingWorkGroups.Part("colony-furnishings." + name, 0, 0, true)));
    }

    /** A joined work surface/storage pair with two clear rows facing it. */
    static BuildingWorkGroups.Group pair(String first, String second) {
        return new BuildingWorkGroups.Group(2, 3, List.of(
                new BuildingWorkGroups.Part("colony-furnishings." + first, 0, 0, true),
                new BuildingWorkGroups.Part("colony-furnishings." + second, 1, 0, true)));
    }

    static void furnish(NavigationGrid grid, CellTopology topology, int bl, int bt, int br, int bb,
                        String pool, BuildingLayouts.LayoutRecipe recipe, List<Doodad> doodads) {
        BuildingWorkGroups groups = new BuildingWorkGroups(grid, topology, doodads);
        Set<RoomPurpose> purposes = new LinkedHashSet<>();
        for (int y = bt + 1; y < bb; y++) for (int x = bl + 1; x < br; x++)
            if (grid.isWalkable(x, y) && !grid.isDoorway(x, y)) purposes.add(topology.getRoomPurpose(x, y));
        for (RoomPurpose purpose : purposes) {
            if (purpose == RoomPurpose.CORRIDOR || purpose == RoomPurpose.RESIDENTIAL_HALL
                    || purpose == RoomPurpose.OFFICE_CORRIDOR || purpose == RoomPurpose.INDUSTRIAL_SPINE
                    || purpose == RoomPurpose.MEDICAL_CORRIDOR) continue;
            boolean home = "RESIDENTIAL".equals(pool) || recipe == BuildingLayouts.LayoutRecipe.HOME
                    || recipe == BuildingLayouts.LayoutRecipe.APARTMENT_BLOCK;
            boolean shop = "COMMERCIAL".equals(pool) || recipe == BuildingLayouts.LayoutRecipe.SHOP;
            boolean storage = "WAREHOUSE".equals(pool) || "SKY_PORT".equals(pool)
                    || recipe == BuildingLayouts.LayoutRecipe.WAREHOUSE;
            if (home) {
                // Bed and personal locker form one sleeping nook; no furniture is placed in its apron.
                var sleeping = new BuildingWorkGroups.Group(4, 3, List.of(
                        new BuildingWorkGroups.Part("doodad.residential-bed-h", 0, 0, true),
                        new BuildingWorkGroups.Part("colony-furnishings.locker", 3, 0, true)));
                groups.fit(bl, bt, br, bb, purpose, sleeping, 1);
            }
            String first = home ? "galley" : shop ? "shelf" : storage ? "case" : "utility";
            String second = home ? "locker" : "desk";
            if (groups.fit(bl, bt, br, bb, purpose, pair(first, second), 1) == 0)
                groups.fit(bl, bt, br, bb, purpose, single(first), 1);
            if (home) {
                var study = new BuildingWorkGroups.Group(2, 3, List.of(
                        new BuildingWorkGroups.Part("colony-furnishings.desk", 0, 0, true),
                        new BuildingWorkGroups.Part("mess-galley.stool", 0, 1, false)));
                groups.fit(bl, bt, br, bb, purpose, study, 1);
            } else if (!shop && !storage) {
                // Larger service rooms carry spare parts beside their electrical equipment.
                int bays = Math.min(3, (br - bl - 1) * (bb - bt - 1) / 30);
                groups.fit(bl, bt, br, bb, purpose, pair("utility", "utility"), bays);
                if (bays > 0) groups.fit(bl, bt, br, bb, purpose, pair("shelf", "case"), 1);
            }
        }
    }
}
