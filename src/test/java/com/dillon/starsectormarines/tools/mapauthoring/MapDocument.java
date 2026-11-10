package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.world.gen.OwnedMap;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Cell;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Drop;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Rise;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Settlement;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Site;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * What the Maps page edits: one owned map ({@code owned-maps.md}) and the road document it names, since its roads are
 * kept in their own document for the Roads page. Immutable, so one edit is one undo step, and equal when nothing
 * changed, so the page knows whether it has unsaved edits. {@code roads} is null for a map that names none.
 */
public record MapDocument(OwnedMap map, RoadPlan roads) {

    public MapDocument withMap(OwnedMap next) { return new MapDocument(next, roads); }
    public MapDocument withRoads(RoadPlan next) { return new MapDocument(map, next); }

    public MapDocument withDrops(UnaryOperator<List<Drop>> f) {
        OwnedMap m = map;
        return withMap(new OwnedMap(m.id(), m.width(), m.height(), m.roads(), m.roadGraphFrom(), m.terrain(), m.origin(),
                m.objective(), m.grounds(), m.pads(), m.sites(), m.settlements(), m.walls(), m.props(),
                f.apply(new ArrayList<>(m.drops())), m.rises(), m.routes()));
    }

    public MapDocument withRises(UnaryOperator<List<Rise>> f) {
        OwnedMap m = map;
        return withMap(new OwnedMap(m.id(), m.width(), m.height(), m.roads(), m.roadGraphFrom(), m.terrain(), m.origin(),
                m.objective(), m.grounds(), m.pads(), m.sites(), m.settlements(), m.walls(), m.props(), m.drops(),
                f.apply(new ArrayList<>(m.rises())), m.routes()));
    }

    public MapDocument withSites(UnaryOperator<List<Site>> f) {
        OwnedMap m = map;
        return withMap(new OwnedMap(m.id(), m.width(), m.height(), m.roads(), m.roadGraphFrom(), m.terrain(), m.origin(),
                m.objective(), m.grounds(), m.pads(), f.apply(new ArrayList<>(m.sites())), m.settlements(), m.walls(),
                m.props(), m.drops(), m.rises(), m.routes()));
    }

    public MapDocument withSettlements(UnaryOperator<List<Settlement>> f) {
        OwnedMap m = map;
        return withMap(new OwnedMap(m.id(), m.width(), m.height(), m.roads(), m.roadGraphFrom(), m.terrain(), m.origin(),
                m.objective(), m.grounds(), m.pads(), m.sites(), f.apply(new ArrayList<>(m.settlements())), m.walls(),
                m.props(), m.drops(), m.rises(), m.routes()));
    }

    /** The map with its origin, where the attacker arrives and every route is proved from, at {@code at}. */
    public MapDocument withOrigin(Cell at) {
        OwnedMap m = map;
        return withMap(new OwnedMap(m.id(), m.width(), m.height(), m.roads(), m.roadGraphFrom(), m.terrain(), at,
                m.objective(), m.grounds(), m.pads(), m.sites(), m.settlements(), m.walls(), m.props(), m.drops(),
                m.rises(), m.routes()));
    }

    /** {@code drop} with a crossing at {@code at}, decked {@code width} cells along it (one width serves all its crossings). */
    public static Drop withCrossing(Drop drop, Cell at, int width) {
        List<Cell> crossings = new ArrayList<>(drop.crossings());
        crossings.add(at);
        return new Drop(drop.name(), drop.role(), drop.path(), drop.width(), crossings,
                Math.max(drop.crossingWidth(), width), drop.depths());
    }

    /** {@code drop} with point {@code k} of its path at {@code at}, its depth there moved with it. */
    public static Drop withPoint(Drop drop, int k, Cell at) {
        List<Cell> path = new ArrayList<>(drop.path());
        Cell was = path.set(k, at);
        List<OwnedMap.Depth> depths = new ArrayList<>();
        for (OwnedMap.Depth d : drop.depths()) depths.add(d.at().equals(was) ? new OwnedMap.Depth(at, d.share()) : d);
        return new Drop(drop.name(), drop.role(), path, drop.width(), drop.crossings(), drop.crossingWidth(), depths);
    }

    /** {@code drop} with its depth share at point {@code k} of its path set to {@code share}. */
    public static Drop withDepth(Drop drop, int k, float share) {
        Cell at = drop.path().get(k);
        List<OwnedMap.Depth> depths = new ArrayList<>();
        for (OwnedMap.Depth d : drop.depths()) if (!d.at().equals(at)) depths.add(d);
        depths.add(new OwnedMap.Depth(at, Math.max(0f, Math.min(1f, share))));
        return new Drop(drop.name(), drop.role(), drop.path(), drop.width(), drop.crossings(), drop.crossingWidth(), depths);
    }

    /** The depth share at point {@code k} of {@code drop}'s path, or -1 where it states none. */
    public static float depthAt(Drop drop, int k) {
        Cell at = drop.path().get(k);
        for (OwnedMap.Depth d : drop.depths()) if (d.at().equals(at)) return d.share();
        return -1f;
    }

    /** Removes whatever edit is called {@code name}: a drop, a rise, a site, a settlement or a road. */
    public MapDocument without(String name) {
        MapDocument out = withDrops(l -> { l.removeIf(d -> d.name().equals(name)); return l; })
                .withRises(l -> { l.removeIf(r -> r.name().equals(name)); return l; })
                .withSites(l -> { l.removeIf(s -> s.name().equals(name)); return l; })
                .withSettlements(l -> { l.removeIf(s -> s.name().equals(name)); return l; });
        if (roads != null && roads.routes().stream().anyMatch(r -> r.id().equals(name)) && roads.routes().size() > 1) {
            out = out.withRoads(new RoadPlan(roads.width(), roads.height(),
                    roads.routes().stream().filter(r -> !r.id().equals(name)).toList()));
        }
        return out;
    }

    /** A name with this prefix that no edit here uses. */
    public String freshName(String prefix) {
        Set<String> names = new HashSet<>();
        map.drops().forEach(d -> names.add(d.name()));
        map.rises().forEach(r -> names.add(r.name()));
        map.sites().forEach(s -> names.add(s.name()));
        map.settlements().forEach(s -> names.add(s.name()));
        if (roads != null) roads.routes().forEach(r -> names.add(r.id()));
        for (int i = 1; ; i++) if (!names.contains(prefix + "-" + i)) return prefix + "-" + i;
    }

    /** The drop nearest {@code (x, y)} within its own width of its path, or null. */
    public Drop dropAt(float x, float y) {
        Drop best = null;
        float bestDistance = Float.MAX_VALUE;
        for (Drop d : map.drops()) {
            float distance = distance(d.path(), x, y);
            if (distance <= d.width() && distance < bestDistance) { bestDistance = distance; best = d; }
        }
        return best;
    }

    /** How far {@code (x, y)} lies from a path of cell centres. */
    static float distance(List<Cell> path, float x, float y) {
        float best = Float.MAX_VALUE;
        for (int k = 1; k < path.size(); k++) {
            float ax = path.get(k - 1).x() + .5f, ay = path.get(k - 1).y() + .5f;
            float bx = path.get(k).x() + .5f, by = path.get(k).y() + .5f;
            float dx = bx - ax, dy = by - ay, len2 = dx * dx + dy * dy;
            float t = len2 == 0f ? 0f : Math.max(0f, Math.min(1f, ((x - ax) * dx + (y - ay) * dy) / len2));
            best = Math.min(best, (float) Math.hypot(x - ax - dx * t, y - ay - dy * t));
        }
        return best;
    }
}
