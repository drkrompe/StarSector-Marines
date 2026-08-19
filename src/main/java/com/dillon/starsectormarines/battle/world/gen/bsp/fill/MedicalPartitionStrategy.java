package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.Random;

/**
 * Infantry-scale clinic plan. A two-cell spine joins the public ambulance-side
 * entrance to an opposed service exit. Reception spans the public threshold;
 * triage and treatment rooms flank the middle of the spine, while the deeper
 * ward and pharmacy remain directly accessible without serial room traversal.
 */
final class MedicalPartitionStrategy implements PartitionStrategy {

    static final MedicalPartitionStrategy DEFAULT = new MedicalPartitionStrategy();
    static final int MIN_LONG_DIM = 15;
    static final int MIN_SHORT_DIM = 12;

    private static final int RECEPTION_DEPTH = 3;

    private MedicalPartitionStrategy() {}

    @Override
    public PartitionLayout partition(NavigationGrid grid, CellTopology topology,
                                     int bl, int bt, int br, int bb,
                                     Random rng, GroundKind interiorGround) {
        return partition(grid, topology, bl, bt, br, bb, rng, interiorGround,
                BuildingPlacement.DEFAULT);
    }

    @Override
    public PartitionLayout partition(NavigationGrid grid, CellTopology topology,
                                     int bl, int bt, int br, int bb,
                                     Random rng, GroundKind interiorGround,
                                     BuildingPlacement placement) {
        int width = br - bl + 1;
        int height = bb - bt + 1;
        if (Math.max(width, height) < MIN_LONG_DIM
                || Math.min(width, height) < MIN_SHORT_DIM) {
            return BinaryPartitionStrategy.DEFAULT.partition(
                    grid, topology, bl, bt, br, bb, rng, interiorGround, placement);
        }

        boolean vertical = placement.frontage == BuildingPlacement.Side.TOP
                || placement.frontage == BuildingPlacement.Side.BOTTOM
                || (placement.frontage == null && height >= width);
        return vertical
                ? carveVertical(grid, topology, bl, bt, br, bb,
                        interiorGround, placement.frontage)
                : carveHorizontal(grid, topology, bl, bt, br, bb,
                        interiorGround, placement.frontage);
    }

    private static PartitionLayout carveVertical(NavigationGrid grid,
                                                   CellTopology topology,
                                                   int bl, int bt, int br, int bb,
                                                   GroundKind ground,
                                                   BuildingPlacement.Side frontage) {
        int corridorLow = (bl + br - 1) / 2;
        int corridorHigh = corridorLow + 1;
        int leftWall = corridorLow - 1;
        int rightWall = corridorHigh + 1;
        boolean frontAtLow = frontage != BuildingPlacement.Side.BOTTOM;
        int receptionMin = frontAtLow ? bt + 1 : bb - RECEPTION_DEPTH;
        int receptionMax = frontAtLow ? bt + RECEPTION_DEPTH : bb - 1;
        int roomsMin = frontAtLow ? receptionMax + 1 : bt + 1;
        int roomsMax = frontAtLow ? bb - 1 : receptionMin - 1;
        int splitWall = (roomsMin + roomsMax) / 2;

        for (int y = roomsMin; y <= roomsMax; y++) {
            grid.setWalkable(leftWall, y, false);
            grid.setWalkable(rightWall, y, false);
        }
        for (int x = bl + 1; x < leftWall; x++) grid.setWalkable(x, splitWall, false);
        for (int x = rightWall + 1; x <= br - 1; x++) grid.setWalkable(x, splitWall, false);

        int clinicalMin = frontAtLow ? roomsMin : splitWall + 1;
        int clinicalMax = frontAtLow ? splitWall - 1 : roomsMax;
        int supportMin = frontAtLow ? splitWall + 1 : roomsMin;
        int supportMax = frontAtLow ? roomsMax : splitWall - 1;
        openVerticalRoomDoors(grid, topology, leftWall, rightWall,
                clinicalMin, clinicalMax, supportMin, supportMax, ground);

        labelRect(grid, topology, bl + 1, receptionMin, br - 1, receptionMax,
                RoomPurpose.MEDICAL_RECEPTION);
        labelRect(grid, topology, corridorLow, bt + 1, corridorHigh, bb - 1,
                RoomPurpose.MEDICAL_CORRIDOR);
        labelRect(grid, topology, bl + 1, clinicalMin, leftWall - 1, clinicalMax,
                RoomPurpose.TRIAGE);
        labelRect(grid, topology, rightWall + 1, clinicalMin, br - 1, clinicalMax,
                RoomPurpose.TREATMENT_ROOM);
        labelRect(grid, topology, bl + 1, supportMin, leftWall - 1, supportMax,
                RoomPurpose.PATIENT_WARD);
        labelRect(grid, topology, rightWall + 1, supportMin, br - 1, supportMax,
                RoomPurpose.PHARMACY);

        return new PartitionLayout(PartitionLayout.Orient.VERTICAL,
                new int[]{leftWall, rightWall}, false, false, true, corridorLow);
    }

    private static PartitionLayout carveHorizontal(NavigationGrid grid,
                                                     CellTopology topology,
                                                     int bl, int bt, int br, int bb,
                                                     GroundKind ground,
                                                     BuildingPlacement.Side frontage) {
        int corridorLow = (bt + bb - 1) / 2;
        int corridorHigh = corridorLow + 1;
        int topWall = corridorLow - 1;
        int bottomWall = corridorHigh + 1;
        boolean frontAtLow = frontage != BuildingPlacement.Side.RIGHT;
        int receptionMin = frontAtLow ? bl + 1 : br - RECEPTION_DEPTH;
        int receptionMax = frontAtLow ? bl + RECEPTION_DEPTH : br - 1;
        int roomsMin = frontAtLow ? receptionMax + 1 : bl + 1;
        int roomsMax = frontAtLow ? br - 1 : receptionMin - 1;
        int splitWall = (roomsMin + roomsMax) / 2;

        for (int x = roomsMin; x <= roomsMax; x++) {
            grid.setWalkable(x, topWall, false);
            grid.setWalkable(x, bottomWall, false);
        }
        for (int y = bt + 1; y < topWall; y++) grid.setWalkable(splitWall, y, false);
        for (int y = bottomWall + 1; y <= bb - 1; y++) grid.setWalkable(splitWall, y, false);

        int clinicalMin = frontAtLow ? roomsMin : splitWall + 1;
        int clinicalMax = frontAtLow ? splitWall - 1 : roomsMax;
        int supportMin = frontAtLow ? splitWall + 1 : roomsMin;
        int supportMax = frontAtLow ? roomsMax : splitWall - 1;
        openHorizontalRoomDoors(grid, topology, topWall, bottomWall,
                clinicalMin, clinicalMax, supportMin, supportMax, ground);

        labelRect(grid, topology, receptionMin, bt + 1, receptionMax, bb - 1,
                RoomPurpose.MEDICAL_RECEPTION);
        labelRect(grid, topology, bl + 1, corridorLow, br - 1, corridorHigh,
                RoomPurpose.MEDICAL_CORRIDOR);
        labelRect(grid, topology, clinicalMin, bt + 1, clinicalMax, topWall - 1,
                RoomPurpose.TRIAGE);
        labelRect(grid, topology, clinicalMin, bottomWall + 1, clinicalMax, bb - 1,
                RoomPurpose.TREATMENT_ROOM);
        labelRect(grid, topology, supportMin, bt + 1, supportMax, topWall - 1,
                RoomPurpose.PATIENT_WARD);
        labelRect(grid, topology, supportMin, bottomWall + 1, supportMax, bb - 1,
                RoomPurpose.PHARMACY);

        return new PartitionLayout(PartitionLayout.Orient.HORIZONTAL,
                new int[]{topWall, bottomWall}, false, false, true, corridorLow);
    }

    private static void openVerticalRoomDoors(NavigationGrid grid, CellTopology topology,
                                              int firstWall, int secondWall,
                                              int clinicalMin, int clinicalMax,
                                              int supportMin, int supportMax,
                                              GroundKind ground) {
        int clinicalDoor = (clinicalMin + clinicalMax) / 2;
        int supportDoor = (supportMin + supportMax) / 2;
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, firstWall, clinicalDoor, ground);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, secondWall, clinicalDoor, ground);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, firstWall, supportDoor, ground);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, secondWall, supportDoor, ground);
    }

    private static void openHorizontalRoomDoors(NavigationGrid grid, CellTopology topology,
                                                int firstWall, int secondWall,
                                                int clinicalMin, int clinicalMax,
                                                int supportMin, int supportMax,
                                                GroundKind ground) {
        int clinicalDoor = (clinicalMin + clinicalMax) / 2;
        int supportDoor = (supportMin + supportMax) / 2;
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, clinicalDoor, firstWall, ground);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, clinicalDoor, secondWall, ground);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, supportDoor, firstWall, ground);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, supportDoor, secondWall, ground);
    }

    private static void labelRect(NavigationGrid grid, CellTopology topology,
                                  int minX, int minY, int maxX, int maxY,
                                  RoomPurpose purpose) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (grid.isWalkable(x, y) && !grid.isDoorway(x, y)) {
                    topology.setRoomPurpose(x, y, purpose);
                }
            }
        }
    }
}
