package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.ship.BayAperture;
import java.util.List;

/** A berth's orthogonal hover corridor, through its bay's actual opening. */
public record ShipBayRoute(List<Point> points, BayAperture aperture, float homeFacing) {
    public record Point(float x, float y) {}

    public ShipBayRoute {
        points = List.copyOf(points);
        if (points.size() < 3) throw new IllegalArgumentException("incomplete bay route");
        for (int i = 1; i < points.size(); i++) {
            Point a = points.get(i - 1), b = points.get(i);
            if (a.x != b.x && a.y != b.y) throw new IllegalArgumentException("diagonal bay route");
        }
    }

    public static ShipBayRoute from(Gantry berth, BayAperture door, float outsideCells) {
        Point home = new Point(berth.worldCenterX(), berth.worldCenterY());
        Point opening = new Point(door.centerX(), door.centerY());
        Point outside = new Point(opening.x + door.outDx() * outsideCells,
                opening.y + door.outDy() * outsideCells);
        boolean bank = berth.facing.dx * door.outDx() + berth.facing.dy * door.outDy() == 0;
        if (bank) {
            Point aisle = door.outDy() != 0 ? new Point(opening.x, home.y)
                    : new Point(home.x, opening.y);
            return new ShipBayRoute(List.of(home, aisle, opening, outside), door, berth.facing.degrees());
        }
        // A single rank first clears its noses into the apron, then aligns
        // with the opening; sliding across the occupied rank is never a route.
        Point apron = new Point(opening.x - door.outDx() * 4f,
                opening.y - door.outDy() * 4f);
        Point nose = door.outDy() != 0 ? new Point(home.x, apron.y)
                : new Point(apron.x, home.y);
        return new ShipBayRoute(List.of(home, nose, apron, opening, outside), door, berth.facing.degrees());
    }

    public Point home() { return points.get(0); }
    public Point outside() { return points.get(points.size() - 1); }
}
