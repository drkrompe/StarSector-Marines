package com.dillon.starsectormarines.battle.setpiece;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.MainStreet;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.List;

/**
 * The ways into a main street from the side ({@code set-piece-blocks.md}, Lanes and pressure): where a cross
 * street or a gap between the shopfronts opens off the carriageway. The district publishes the street's spine
 * and width, not its gaps, so they are read off the map: a step along the spine where open outdoor ground runs
 * straight out from the kerb for {@code depth} cells is open on that side, and each run of open steps is one
 * way in, at its middle.
 */
public final class StreetMouths {

    /**
     * One way in: the kerb cell where it meets the carriageway, the step {@code (outX, outY)} that leaves the
     * street through it, and the spine cell across from it.
     */
    public record Mouth(int kerbX, int kerbY, int outX, int outY, int spineX, int spineY) { }

    private StreetMouths() { }

    /** Every side way into {@code street}, along the spine from its start, the left side's before the right's at a step. */
    public static List<Mouth> find(NavigationGrid grid, CellTopology topology, MainStreet street, int depth) {
        float dx = street.endX() - street.startX(), dy = street.endY() - street.startY();
        float length = (float) Math.hypot(dx, dy);
        List<Mouth> mouths = new ArrayList<>();
        if (length < 1f) return mouths;
        float ux = dx / length, uy = dy / length;
        int steps = (int) Math.floor(length);
        float kerb = street.width() / 2f + 1f;
        for (int sign : new int[]{1, -1}) {
            float nx = -uy * sign, ny = ux * sign;
            int outX = Math.round(nx), outY = Math.round(ny);
            int runStart = -1;
            for (int s = 0; s <= steps + 1; s++) {
                boolean open = s <= steps && open(grid, topology, street.startX() + ux * s, street.startY() + uy * s,
                        nx, ny, kerb, depth);
                if (open && runStart < 0) runStart = s;
                if (!open && runStart >= 0) {
                    float mid = (runStart + s - 1) / 2f;
                    float px = street.startX() + ux * mid, py = street.startY() + uy * mid;
                    mouths.add(new Mouth((int) Math.floor(px + nx * kerb), (int) Math.floor(py + ny * kerb),
                            outX, outY, (int) Math.floor(px), (int) Math.floor(py)));
                    runStart = -1;
                }
            }
        }
        return mouths;
    }

    /** Whether ground runs open straight out from the kerb at spine point {@code (px, py)} along {@code (nx, ny)}. */
    private static boolean open(NavigationGrid grid, CellTopology topology, float px, float py, float nx, float ny,
                                float kerb, int depth) {
        for (int k = 0; k < depth; k++) {
            int x = (int) Math.floor(px + nx * (kerb + k)), y = (int) Math.floor(py + ny * (kerb + k));
            if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || topology.hasBuildingKindHint(x, y)
                    || topology.getGroundKind(x, y) == CellTopology.GroundKind.INDOOR) return false;
        }
        return true;
    }
}
