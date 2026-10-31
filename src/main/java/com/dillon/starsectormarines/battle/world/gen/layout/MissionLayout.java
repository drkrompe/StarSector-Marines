package com.dillon.starsectormarines.battle.world.gen.layout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What a mission lays over the grown country: named pieces (lines of works,
 * reserved sites, and pieces that can only be resolved once the ground exists),
 * each reported back by name once generation has tried to lay it
 * ({@code mission-layout.md}).
 *
 * <p>Mission-neutral: nothing here knows which mission asked. A map with no
 * layout ({@link #NONE}) runs no layout stage at all, so every other map is
 * generated exactly as it was before layouts existed.
 */
public record MissionLayout(List<Piece> pieces) {

    /** The layout of every map that states none. */
    public static final MissionLayout NONE = new MissionLayout(List.of());

    public MissionLayout {
        pieces = pieces == null ? List.of() : List.copyOf(pieces);
        Set<String> names = new HashSet<>();
        for (Piece piece : pieces) {
            if (!names.add(piece.name())) throw new IllegalArgumentException("layout piece named twice: " + piece.name());
        }
    }

    public boolean isEmpty() { return pieces.isEmpty(); }

    /**
     * The sites stated in the layout itself rather than resolved later: known
     * before anything grows, so the places grow round them.
     */
    public List<Site> planSites() {
        List<Site> out = new ArrayList<>();
        for (Piece piece : pieces) {
            if (piece instanceof Site site) out.add(site);
            else if (piece instanceof Airfield airfield) out.add(airfield.reservation());
        }
        return out;
    }

    /**
     * Ground no road, place or field may grow onto: every {@linkplain #planSites
     * plan-level site}, clipped to the map. {@code null} when there is none, so a
     * layout without one leaves growth exactly as it was.
     */
    public boolean[][] keepClear(int width, int height) {
        List<Site> sites = planSites();
        if (sites.isEmpty()) return null;
        boolean[][] mask = new boolean[width][height];
        for (Site site : sites) {
            for (int x = Math.max(0, site.x0()); x <= Math.min(width - 1, site.x1()); x++) {
                for (int y = Math.max(0, site.y0()); y <= Math.min(height - 1, site.y1()); y++) mask[x][y] = true;
            }
        }
        return mask;
    }

    /** The piece of this name, or {@code null}. */
    public Piece find(String name) {
        for (Piece piece : pieces) if (piece.name().equals(name)) return piece;
        return null;
    }

    /** The same layout with the piece called {@code name} replaced; naming no piece is an authoring error. */
    public MissionLayout withOverride(String name, Piece piece) {
        List<Piece> out = new ArrayList<>(pieces.size());
        boolean found = false;
        for (Piece p : pieces) {
            if (p.name().equals(name)) {
                out.add(piece);
                found = true;
            } else {
                out.add(p);
            }
        }
        if (!found) throw new IllegalArgumentException("the layout has no piece " + name);
        return new MissionLayout(out);
    }
}
