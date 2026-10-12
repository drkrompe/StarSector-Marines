package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Route;

import java.util.List;
import java.util.ArrayList;

/** Buildings and shared outdoor space in one cardinally rotatable local frame. */
public record CompoundLayout(RoomShape footprint, List<Cluster> clusters, List<Yard> yards, List<Route> roads) {
    public CompoundLayout {
        clusters = List.copyOf(clusters);
        yards = List.copyOf(yards);
        roads = List.copyOf(roads);
    }

    public record Building(RoomPurpose purpose, RoomShape shape, int x, int y, List<Doorway> doors) {
        public Building { doors = List.copyOf(doors); }
    }

    /** Coordinates are local to the compound, so buildings keep their relationship to their court. */
    public record Cluster(String name, List<Building> buildings) {
        public Cluster { buildings = List.copyOf(buildings); }
    }

    public record Yard(String name, int x, int y, int width, int height) {}

    public Placement at(int x, int y, int quarterTurns) {
        return new Placement(this, x, y, new RoomPose(quarterTurns, false));
    }

    public record Placement(CompoundLayout layout, int x, int y, RoomPose pose) {
        public Placement {
            if (pose.mirrored()) throw new IllegalArgumentException("Compound placements use cardinal rotation");
        }
        public int[] cell(int localX, int localY) {
            int[] cell = pose.map(localX, localY, layout.footprint.width(), layout.footprint.height());
            return new int[]{x + cell[0], y + cell[1]};
        }

        public boolean contains(int worldX, int worldY) {
            int[] cell = pose.unmap(worldX - x, worldY - y,
                    layout.footprint.width(), layout.footprint.height());
            return layout.footprint.contains(cell[0], cell[1]);
        }

        /** The host may connect these outside gate mouths to its wider road network. */
        public List<Route> roads(long seed) {
            return layout.roads.stream().map(road -> new Route(road.id(), road.points().stream()
                    .map(this::point).toList(), road.width(), road.shoulder(), road.wear(),
                    seed, road.material(), road.endCap(), road.autoEdge())).toList();
        }

        private Point point(Point local) {
            double px = local.x(), py = local.y();
            int w = layout.footprint.width(), h = layout.footprint.height();
            for (int turn = 0; turn < pose.quarterTurns(); turn++) {
                double nextX = h - 1 - py;
                py = px;
                px = nextX;
                int nextW = h; h = w; w = nextW;
            }
            return new Point(x + px, y + py);
        }

        public List<RoomPacker.Placed> buildings() {
            var result = new ArrayList<RoomPacker.Placed>();
            for (var cluster : layout.clusters) for (var building : cluster.buildings) {
                int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE;
                for (int[] offset : building.shape.filled()) {
                    int[] cell = cell(building.x + offset[0], building.y + offset[1]);
                    left = Math.min(left, cell[0]);
                    top = Math.min(top, cell[1]);
                }
                var doors = new ArrayList<Doorway>();
                for (var door : building.doors) {
                    int[] cell = cell(building.x + door.x(), building.y + door.y());
                    doors.add(new Doorway(cell[0], cell[1]));
                }
                result.add(new RoomPacker.Placed(building.shape.posed(pose), pose, left, top,
                        building.purpose, doors));
            }
            return List.copyOf(result);
        }
    }
}
