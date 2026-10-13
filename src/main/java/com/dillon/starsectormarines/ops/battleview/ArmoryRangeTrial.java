package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.List;

/** A disposable copy of the actual ship's range. No campaign objects or settlement. */
public final class ArmoryRangeTrial implements AutoCloseable {
    private final BattleSimulation simulation;
    private final long marine;

    public static DeckGraph.Compartment availableRoom(CompanyDeck deck) {
        if (deck == null || !deck.ready()) return null;
        return deck.rooms(RoomPurpose.FIRING_RANGE).stream()
                .filter(room -> !deck.rooms().damaged(room.id()))
                .filter(room -> !lanes(deck.map(), room).isEmpty())
                .findFirst().orElse(null);
    }

    public static List<FixtureTask> lanes(MapResult map, DeckGraph.Compartment room) {
        return map.fixtureTasks.stream().filter(task -> task.inService()
                && task.affordance() == Affordance.PRACTICE
                && room.contains(task.cellX(), task.cellY())
                && map.grid.isWalkable(task.cellX(), task.cellY())).toList();
    }

    public ArmoryRangeTrial(MapResult source, DeckGraph.Compartment room,
                            String name, MarineLoadout loadout) {
        List<FixtureTask> lanes = lanes(source, room);
        if (lanes.isEmpty()) throw new IllegalArgumentException("range has no working firing point");
        FixtureTask start = lanes.get(0);
        int left = room.left() - 1, top = room.top() - 1;
        MapResult map = copyRoom(source, room, start);
        simulation = BattleSetup.buildMap(map, List.of(), List.of(), 0x52414e4745L).sim();
        simulation.setMissionCompletionEnabled(false);
        EntitySpec spec = new EntitySpec(name, Faction.MARINE, UnitType.MARINE,
                start.cellX() - left, start.cellY() - top);
        loadout.seedInto(spec);
        marine = simulation.spawn(spec);
        for (FixtureTask lane : lanes) {
            simulation.spawn(new EntitySpec("Range target", Faction.DEFENDER,
                    UnitType.RANGE_TARGET, lane.fixtureX() - left, lane.fixtureY() - top)
                    .role(UnitRole.STRUCTURE));
        }
        simulation.getFogOfWar().tick(0, simulation.getRoster());
    }

    public BattleSimulation simulation() { return simulation; }
    public long marine() { return marine; }

    /** Copies the fitted room and its bulkhead ring; outside the room is closed. */
    static MapResult copyRoom(MapResult source, DeckGraph.Compartment room, FixtureTask start) {
        int left = room.left() - 1, top = room.top() - 1;
        int width = room.width() + 2, height = room.depth() + 2;
        NavigationGrid grid = new NavigationGrid(width, height);
        CellTopology topology = new CellTopology(width, height);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int sx = left + x, sy = top + y;
            if (!source.grid.inBounds(sx, sy)) continue;
            for (NavigationGrid.CellTag tag : NavigationGrid.CellTag.values())
                grid.setTag(x, y, tag, source.grid.hasTag(sx, sy, tag));
            for (Direction direction : Direction.values())
                grid.setEdgePassable(x, y, direction, source.grid.isEdgePassable(sx, sy, direction));
            for (int facing = 0; facing < NavigationGrid.FACING_COUNT; facing++)
                grid.setCoverAtFacing(x, y, facing, source.grid.getCellCoverAtFacing(sx, sy, facing),
                        source.grid.getCellCoverCatchHalfHeightAtFacing(sx, sy, facing));
            grid.setWallHp(x, y, source.grid.getWallHp(sx, sy));
            topology.setGroundKind(x, y, source.topology.getGroundKind(sx, sy));
            for (CellTopology.Tag tag : CellTopology.Tag.values())
                topology.setTag(x, y, tag, source.topology.hasTag(sx, sy, tag));
            topology.setWallDirMask(x, y, source.topology.getWallDirMask(sx, sy));
            topology.setRoomPurpose(x, y, source.topology.getRoomPurpose(sx, sy));
            topology.setNatureOverlayIndex(x, y, source.topology.getNatureOverlayIndex(sx, sy));
            topology.setSurface(x, y, topology.surfaceIndex(source.topology.getSurfaceId(sx, sy)));
            topology.setWallGroundSurface(x, y, source.topology.getWallGroundSurfaceId(sx, sy));
            topology.setWallRemnant(x, y, source.topology.getWallRemnant(sx, sy));
            if (!room.contains(sx, sy)) {
                // The trial cannot wander through a hatch into a partial neighboring room.
                grid.setWalkable(x, y, false);
                grid.setSeeThrough(x, y, false);
                grid.setDoorway(x, y, false);
                grid.setWallHp(x, y, 0);
                for (CellTopology.Tag tag : CellTopology.Tag.values()) topology.setTag(x, y, tag, false);
                boolean boundary = false;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++)
                    boundary |= room.contains(sx + dx, sy + dy);
                topology.setWall(x, y, boundary);
                topology.setGroundKind(x, y, boundary ? GroundKind.INDOOR : GroundKind.VOID);
                topology.setSurface(x, y, boundary ? topology.surfaceIndex("ship.neutral-bulkhead") : 0);
                topology.setRoomPurpose(x, y, null);
                topology.setNatureOverlayIndex(x, y, -1);
                topology.setWallGroundSurface(x, y, null);
                topology.setWallRemnant(x, y, null);
            }
        }
        for (var barrier : source.grid.getEdgeBarriers()) {
            int x = barrier.cellX() - left, y = barrier.cellY() - top;
            Direction direction = barrier.direction();
            if (!grid.isWalkable(x, y) || !grid.isWalkable(x + direction.dx, y + direction.dy)) continue;
            grid.openSharedEdge(x, y, direction);
            grid.placeEdgeBarrier(x, y, direction, barrier.kind());
            grid.damageEdgeBarrier(x, y, direction, barrier.maxStructure() - barrier.structure());
        }
        List<Doodad> doodads = new ArrayList<>();
        for (Doodad doodad : source.doodads) {
            boolean inside = true;
            for (int dy = 0; dy < doodad.footprintCellsY; dy++)
                for (int dx = 0; dx < doodad.footprintCellsX; dx++)
                    inside &= room.contains(doodad.cellX + dx, doodad.cellY + dy);
            if (inside) doodads.add(doodad.translated(-left, -top));
        }
        int spawnX = start.cellX() - left, spawnY = start.cellY() - top;
        return new MapResult(grid, topology, spawnX, spawnY, spawnX, spawnY, List.of(), doodads);
    }

    @Override public void close() {
        simulation.directControl().exit();
        simulation.close();
    }
}
