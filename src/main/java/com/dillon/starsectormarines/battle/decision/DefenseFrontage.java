package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.decision.goap.world.GarrisonArea;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stateless derivation of a held place's <em>defense frontage</em> — the
 * apertures through which its interior can be seen into, shot through, or
 * walked into from outside, each paired with the interior cell a defender
 * stands on to cover it.
 *
 * <p>The map already authors this geometry and nothing tactical has been
 * reading it. {@code CompoundWallApertures} stamps paired firing windows into
 * compound perimeter walls, {@code BuildingShellCore} stamps them into
 * building shells, and doorways and fortress gates are ordinary walkable
 * openings. All of it was visible only to worldgen and the renderer, so a
 * garrison patrolled interior rooms with its back to its own firing line.
 *
 * <h2>Inside is a zone set, not a box</h2>
 * "Inside" is the set of {@link GarrisonArea} zones for the given footprint,
 * not the footprint rectangle. A rectangle cannot answer the question: a
 * compound's perimeter wall sits at or just past its persisted union bbox, so
 * a box test finds both sides of that wall "inside" and reports no frontage at
 * all. Zone membership is exact, survives an irregular footprint, and reuses
 * the size/containment gate that already separates rooms from open ground.
 *
 * <p>The consequence is the useful one: an aperture is only frontage when it
 * genuinely separates held ground from unheld ground. A window between two
 * interior rooms is not frontage, and a compound's outer wall is, without
 * either case needing to be special-cased.
 *
 * <h2>Scope decides which wall you man</h2>
 * {@link #forCompound} derives against a node's persisted compound bounds —
 * the whole base, so the frontage is the perimeter wall. {@link #forStructure}
 * derives against the node's own bbox — one building, so the frontage is that
 * building's shell. Nodes of one compound all carry the identical compound
 * bbox, so the two scopes are what distinguish a squad holding the outer wall
 * from a squad holding a building inside it, and a layered defense falls out
 * of running both. For a standalone post the two scopes coincide.
 *
 * <p>Nothing here is faction-scoped. A marine squad holding a captured
 * compound has the same frontage the defenders had; it is the same wall.
 *
 * <h2>A frontage exists only while the envelope does</h2>
 * Derivation is redone at replan time and reads live grid state, so it tracks
 * a wall that changes. What it tracks it to is worth stating plainly, because
 * it is not what you would guess: a breach is non-doorway rubble, so the zone
 * graph <em>merges</em> the interior into the open ground outside, and the
 * merged zone is then far too large to be interior. A breached place has no
 * frontage at all rather than a frontage with a new entrance in it.
 *
 * <p>That is the right answer and not a limitation to route around. Once a hole
 * is open, inside and outside are not distinguishable ground any more, and
 * posting people at the intact windows of a building that is being entered
 * elsewhere is precisely the failure the caller wants. The fight belongs to the
 * room-clearing behaviors from that moment. Doorways are the deliberate
 * exception — an opened doorway becomes its own singleton zone rather than
 * merging anything — which is why a door is frontage and a hole is not.
 */
public final class DefenseFrontage {

    private DefenseFrontage() {}

    /**
     * Cells the compound footprint is grown by before zones are gathered.
     * Matches {@code GarrisonCompound.GARRISON_MARGIN} — the same perimeter
     * wall ring / parade-ground rim has to be absorbed for the courtyard to
     * register as interior, and the two would disagree about what a garrison
     * holds if they drifted apart.
     */
    public static final int COMPOUND_MARGIN = 2;

    /** How far out from an aperture {@link #threatAt} samples believed hostile pressure. Covers the block the aperture opens onto plus the two beyond it. */
    public static final int THREAT_PROBE_CELLS = 24;

    /** Stride between {@link #threatAt} probes. Below the influence field's 8-cell block size, so no block along the outward ray is stepped over. */
    public static final int THREAT_PROBE_STRIDE = 6;

    /** Outward direction an aperture faces — from the held interior toward unheld ground. */
    public enum Facing {
        NORTH(0, -1),
        SOUTH(0, 1),
        WEST(-1, 0),
        EAST(1, 0);

        public final int dx;
        public final int dy;

        Facing(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    /** What kind of opening an aperture is. Both are covered from inside; only one can be walked through. */
    public enum Kind {
        /** A walkable opening — doorway, gate, or a breach blown through a wall. Ingress, so it outranks a window when posts are scarce. */
        ENTRANCE,
        /** A see-through, non-walkable firing aperture in an otherwise solid wall. Fire and sight cross it; bodies do not. */
        WINDOW
    }

    /**
     * One opening in a held place's outer envelope.
     *
     * @param x        aperture cell — the window or the doorway itself
     * @param y        aperture cell
     * @param stanceX  interior cell a defender occupies to cover the aperture; always walkable and inside the held zone set
     * @param stanceY  interior stance cell
     * @param outsideX the unheld walkable cell the aperture opens onto; the near end of the field of fire
     * @param outsideY unheld cell
     * @param facing   outward direction, interior toward exterior
     * @param kind     whether the opening admits bodies or only fire
     */
    public record Aperture(int x, int y, int stanceX, int stanceY,
                           int outsideX, int outsideY, Facing facing, Kind kind) {}

    /** Frontage of {@code node}'s whole compound — the perimeter wall of the base it belongs to. Empty for a null node/sim or a footprint with no interior zones. */
    public static List<Aperture> forCompound(TacticalNode node, BattleView sim) {
        if (node == null || sim == null) return List.of();
        return derive(node.compoundLeft(), node.compoundTop(),
                node.compoundRight(), node.compoundBottom(), COMPOUND_MARGIN, sim);
    }

    /** Frontage of {@code node}'s own building — its shell, which for a node inside a compound faces that compound's interior rather than the outside world. */
    public static List<Aperture> forStructure(TacticalNode node, BattleView sim) {
        if (node == null || sim == null) return List.of();
        return derive(node.left, node.top, node.right, node.bottom, COMPOUND_MARGIN, sim);
    }

    /**
     * Apertures in the envelope of the zone set gathered for
     * {@code [boxL..boxR] × [boxT..boxB]} expanded by {@code margin}. Scans
     * that expanded box one ring wider, since a perimeter wall may sit on the
     * boundary itself. Deterministically ordered by cell.
     */
    public static List<Aperture> derive(int boxL, int boxT, int boxR, int boxB,
                                        int margin, BattleView sim) {
        NavigationGrid grid = sim.getGrid();
        ZoneGraph graph = sim.getZoneGraph();
        CellTopology topology = sim.getTopology();
        if (grid == null || graph == null || topology == null) return List.of();

        Set<Integer> inside = insideZones(boxL - margin, boxT - margin,
                boxR + margin, boxB + margin, sim);
        if (inside.isEmpty()) return List.of();

        int scanL = Math.max(0, boxL - margin - 1);
        int scanT = Math.max(0, boxT - margin - 1);
        int scanR = Math.min(grid.getWidth() - 1, boxR + margin + 1);
        int scanB = Math.min(grid.getHeight() - 1, boxB + margin + 1);

        List<Aperture> out = new ArrayList<>();
        for (int y = scanT; y <= scanB; y++) {
            for (int x = scanL; x <= scanR; x++) {
                Aperture aperture = apertureAt(x, y, inside, grid, graph, topology);
                if (aperture != null) out.add(aperture);
            }
        }
        out.sort(Comparator.<Aperture>comparingInt(Aperture::y).thenComparingInt(Aperture::x));
        return out;
    }

    /**
     * Zone ids that count as held interior for this footprint — the same set
     * {@link GarrisonArea} hands the garrison behaviors, so "inside" means one
     * thing across the whole garrison stack. Single-cell doorway zones are
     * excluded there, which matters here for a second reason: a doorway is an
     * aperture candidate in its own right and must not satisfy its own inside
     * test.
     */
    public static Set<Integer> insideZones(int boxL, int boxT, int boxR, int boxB,
                                           BattleView sim) {
        return new HashSet<>(GarrisonArea.garrisonZones(boxL, boxT, boxR, boxB, sim));
    }

    /**
     * The aperture at {@code (x, y)}, or null. A cell qualifies when exactly
     * one axis through it has held interior on one side and unheld walkable
     * ground on the other — exactly one, because a cell straddling two axes is
     * a corner, and a corner post covers neither approach properly.
     *
     * <p>A walkable candidate must additionally be constricted: at least one
     * of its perpendicular neighbours has to be solid. Without that test every
     * cell along an unwalled boundary reads as an entrance and the frontage of
     * an open-sided compound becomes its whole perimeter.
     */
    private static Aperture apertureAt(int x, int y, Set<Integer> inside,
                                       NavigationGrid grid, ZoneGraph graph,
                                       CellTopology topology) {
        boolean walkable = grid.isWalkable(x, y);
        Kind kind;
        if (walkable) {
            kind = Kind.ENTRANCE;
        } else if (topology.isWindow(x, y)) {
            kind = Kind.WINDOW;
        } else {
            return null;
        }

        Facing found = null;
        for (Facing facing : Facing.values()) {
            int outX = x + facing.dx;
            int outY = y + facing.dy;
            int inX = x - facing.dx;
            int inY = y - facing.dy;
            if (!grid.inBounds(outX, outY) || !grid.inBounds(inX, inY)) continue;
            if (!grid.isWalkable(inX, inY) || !inside.contains(graph.zoneIdAt(inX, inY))) continue;
            if (!grid.isWalkable(outX, outY) || inside.contains(graph.zoneIdAt(outX, outY))) continue;
            // A doorway cell is its own singleton zone and so is excluded from
            // the held set — which would otherwise make the far half of every
            // interior door pair look like it opened onto unheld ground. An
            // aperture has to open onto real ground. Gates are unaffected: the
            // gate cell is the doorway, and what it opens onto is the street.
            if (grid.isDoorway(outX, outY)) continue;
            if (found != null) return null;
            found = facing;
        }
        if (found == null) return null;
        if (kind == Kind.ENTRANCE && !isConstricted(x, y, found, grid)) return null;

        return new Aperture(x, y, x - found.dx, y - found.dy,
                x + found.dx, y + found.dy, found, kind);
    }

    /** True when at least one cell perpendicular to {@code facing} is solid — the opening is a gap in something rather than open ground. */
    private static boolean isConstricted(int x, int y, Facing facing, NavigationGrid grid) {
        if (grid.isDoorway(x, y)) return true;
        int px = facing.dy;
        int py = facing.dx;
        return !walkableInBounds(x + px, y + py, grid) || !walkableInBounds(x - px, y - py, grid);
    }

    private static boolean walkableInBounds(int x, int y, NavigationGrid grid) {
        return grid.inBounds(x, y) && grid.isWalkable(x, y);
    }

    /**
     * Believed hostile pressure on the ground {@code aperture} covers, read
     * from {@code faction}'s own influence field.
     *
     * <p>This is the whole reason a garrison can stand to before it is
     * attacked, and the reason doing so stays legal. The hostile channel is
     * aggregated from that faction's believed contacts and propagated through
     * navigable topology, so pressure reaches a garrison that has seen nothing
     * itself — a forward patrol's contact is what mans the wall facing it. It
     * is belief, never live enemy occupancy, so the garrison can be wrong, and
     * a feint is supposed to be able to pull it.
     */
    public static float threatAt(Aperture aperture, Faction faction, BattleView sim) {
        CommanderInfluenceSnapshot snapshot = sim.getCommanderInfluence(faction);
        if (snapshot == null) return 0f;
        NavigationGrid grid = sim.getGrid();
        float best = 0f;
        for (int step = 1; step <= THREAT_PROBE_CELLS; step += THREAT_PROBE_STRIDE) {
            int px = aperture.x() + aperture.facing().dx * step;
            int py = aperture.y() + aperture.facing().dy * step;
            if (!grid.inBounds(px, py)) break;
            best = Math.max(best, snapshot.hostileAtWorld(px, py));
        }
        return best;
    }
}
