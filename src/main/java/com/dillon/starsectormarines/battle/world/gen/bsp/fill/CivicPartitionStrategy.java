package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.Random;

/**
 * Multi-axis civic headquarters plan. A two-cell spine joins aligned public
 * and service doors, a three-cell-deep reception lobby wraps the frontage,
 * and four directly accessible side rooms become two offices, a conference
 * room, and a secured server room.
 *
 * <p>This strategy labels its own rooms because the generic shell labeler is
 * intentionally one-dimensional. Keeping every side room directly connected
 * to the spine avoids serial room traversal and preserves squad circulation.
 */
final class CivicPartitionStrategy implements PartitionStrategy {

    static final CivicPartitionStrategy DEFAULT = new CivicPartitionStrategy();

    private static final int RECEPTION_DEPTH = 3;

    private CivicPartitionStrategy() {}

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
        boolean vertical = placement.frontage == BuildingPlacement.Side.TOP
                || placement.frontage == BuildingPlacement.Side.BOTTOM
                || (placement.frontage == null && (bb - bt) >= (br - bl));
        return vertical
                ? carveVertical(grid, topology, bl, bt, br, bb, rng,
                        interiorGround, placement.frontage)
                : carveHorizontal(grid, topology, bl, bt, br, bb, rng,
                        interiorGround, placement.frontage);
    }

    private static PartitionLayout carveVertical(NavigationGrid grid, CellTopology topology,
                                                   int bl, int bt, int br, int bb,
                                                   Random rng, GroundKind ground,
                                                   BuildingPlacement.Side frontage) {
        int corridorLow = (bl + br - 1) / 2;
        int corridorHigh = corridorLow + 1;
        int leftWall = corridorLow - 1;
        int rightWall = corridorHigh + 1;
        boolean frontAtLow = frontage != BuildingPlacement.Side.BOTTOM;
        int receptionMin = frontAtLow ? bt + 1 : bb - RECEPTION_DEPTH;
        int receptionMax = frontAtLow ? bt + RECEPTION_DEPTH : bb - 1;
        int roomMin = frontAtLow ? receptionMax + 1 : bt + 1;
        int roomMax = frontAtLow ? bb - 1 : receptionMin - 1;
        int split = (roomMin + roomMax) / 2;

        for (int y = roomMin; y <= roomMax; y++) {
            grid.setWalkable(leftWall, y, false);
            grid.setWalkable(rightWall, y, false);
        }
        for (int x = bl + 1; x < leftWall; x++) grid.setWalkable(x, split, false);
        for (int x = rightWall + 1; x <= br - 1; x++) grid.setWalkable(x, split, false);

        int officeMin = frontAtLow ? roomMin : split + 1;
        int officeMax = frontAtLow ? split - 1 : roomMax;
        int rearMin = frontAtLow ? split + 1 : roomMin;
        int rearMax = frontAtLow ? roomMax : split - 1;
        openVerticalRoomDoors(grid, topology, leftWall, rightWall,
                officeMin, officeMax, rearMin, rearMax, ground);

        labelRect(grid, topology, bl + 1, receptionMin, br - 1, receptionMax,
                RoomPurpose.CIVIC_RECEPTION);
        labelRect(grid, topology, corridorLow, bt + 1, corridorHigh, bb - 1,
                RoomPurpose.OFFICE_CORRIDOR);
        labelVerticalWings(grid, topology, bl, br, leftWall, rightWall,
                officeMin, officeMax, RoomPurpose.CIVIC_OFFICE, RoomPurpose.CIVIC_OFFICE);
        boolean serverOnLeft = rng.nextBoolean();
        labelVerticalWings(grid, topology, bl, br, leftWall, rightWall,
                rearMin, rearMax,
                serverOnLeft ? RoomPurpose.SERVER_ROOM : RoomPurpose.CONFERENCE_ROOM,
                serverOnLeft ? RoomPurpose.CONFERENCE_ROOM : RoomPurpose.SERVER_ROOM);

        return new PartitionLayout(PartitionLayout.Orient.VERTICAL,
                new int[]{leftWall, rightWall}, false, true, true, corridorLow);
    }

    private static PartitionLayout carveHorizontal(NavigationGrid grid, CellTopology topology,
                                                     int bl, int bt, int br, int bb,
                                                     Random rng, GroundKind ground,
                                                     BuildingPlacement.Side frontage) {
        int corridorLow = (bt + bb - 1) / 2;
        int corridorHigh = corridorLow + 1;
        int topWall = corridorLow - 1;
        int bottomWall = corridorHigh + 1;
        boolean frontAtLow = frontage != BuildingPlacement.Side.RIGHT;
        int receptionMin = frontAtLow ? bl + 1 : br - RECEPTION_DEPTH;
        int receptionMax = frontAtLow ? bl + RECEPTION_DEPTH : br - 1;
        int roomMin = frontAtLow ? receptionMax + 1 : bl + 1;
        int roomMax = frontAtLow ? br - 1 : receptionMin - 1;
        int split = (roomMin + roomMax) / 2;

        for (int x = roomMin; x <= roomMax; x++) {
            grid.setWalkable(x, topWall, false);
            grid.setWalkable(x, bottomWall, false);
        }
        for (int y = bt + 1; y < topWall; y++) grid.setWalkable(split, y, false);
        for (int y = bottomWall + 1; y <= bb - 1; y++) grid.setWalkable(split, y, false);

        int officeMin = frontAtLow ? roomMin : split + 1;
        int officeMax = frontAtLow ? split - 1 : roomMax;
        int rearMin = frontAtLow ? split + 1 : roomMin;
        int rearMax = frontAtLow ? roomMax : split - 1;
        openHorizontalRoomDoors(grid, topology, topWall, bottomWall,
                officeMin, officeMax, rearMin, rearMax, ground);

        labelRect(grid, topology, receptionMin, bt + 1, receptionMax, bb - 1,
                RoomPurpose.CIVIC_RECEPTION);
        labelRect(grid, topology, bl + 1, corridorLow, br - 1, corridorHigh,
                RoomPurpose.OFFICE_CORRIDOR);
        labelHorizontalWings(grid, topology, bt, bb, topWall, bottomWall,
                officeMin, officeMax, RoomPurpose.CIVIC_OFFICE, RoomPurpose.CIVIC_OFFICE);
        boolean serverOnTop = rng.nextBoolean();
        labelHorizontalWings(grid, topology, bt, bb, topWall, bottomWall,
                rearMin, rearMax,
                serverOnTop ? RoomPurpose.SERVER_ROOM : RoomPurpose.CONFERENCE_ROOM,
                serverOnTop ? RoomPurpose.CONFERENCE_ROOM : RoomPurpose.SERVER_ROOM);

        return new PartitionLayout(PartitionLayout.Orient.HORIZONTAL,
                new int[]{topWall, bottomWall}, false, true, true, corridorLow);
    }

    private static void openVerticalRoomDoors(NavigationGrid grid, CellTopology topology,
                                              int firstWall, int secondWall,
                                              int officeMin, int officeMax,
                                              int rearMin, int rearMax,
                                              GroundKind ground) {
        int officeDoor = (officeMin + officeMax) / 2;
        int rearDoor = (rearMin + rearMax) / 2;
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, firstWall, officeDoor, ground);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, secondWall, officeDoor, ground);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, firstWall, rearDoor, ground);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, secondWall, rearDoor, ground);
    }

    private static void openHorizontalRoomDoors(NavigationGrid grid, CellTopology topology,
                                                int firstWall, int secondWall,
                                                int officeMin, int officeMax,
                                                int rearMin, int rearMax,
                                                GroundKind ground) {
        int officeDoor = (officeMin + officeMax) / 2;
        int rearDoor = (rearMin + rearMax) / 2;
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, officeDoor, firstWall, ground);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, officeDoor, secondWall, ground);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, rearDoor, firstWall, ground);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, rearDoor, secondWall, ground);
    }

    private static void labelVerticalWings(NavigationGrid grid, CellTopology topology,
                                           int bl, int br, int leftWall, int rightWall,
                                           int minY, int maxY,
                                           RoomPurpose left, RoomPurpose right) {
        labelRect(grid, topology, bl + 1, minY, leftWall - 1, maxY, left);
        labelRect(grid, topology, rightWall + 1, minY, br - 1, maxY, right);
    }

    private static void labelHorizontalWings(NavigationGrid grid, CellTopology topology,
                                             int bt, int bb, int topWall, int bottomWall,
                                             int minX, int maxX,
                                             RoomPurpose top, RoomPurpose bottom) {
        labelRect(grid, topology, minX, bt + 1, maxX, topWall - 1, top);
        labelRect(grid, topology, minX, bottomWall + 1, maxX, bb - 1, bottom);
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
