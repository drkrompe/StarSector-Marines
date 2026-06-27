package com.dillon.starsectormarines.battle.air;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Drop-zone scatter — picks the landing cells a wave of dropships touches down on within a designated
 * zone. The paratrooper-scatter half of the S3d drop-ship invasion (overview pillar 5): instead of one
 * LZ, a wave lands spread across the zone, biased toward low-threat ground and spaced apart so squads
 * don't stack on a single cell.
 *
 * <p><b>Pure + dependency-inverted.</b> The caller supplies {@code walkable} (which cells are valid
 * landing ground) and {@code threat} (how dangerous a cell is — higher = worse), so this engine has no
 * compile dependency on the grid or {@link com.dillon.starsectormarines.battle.decision.TacticalScoring}
 * and is unit-testable with plain lambdas. The bridge host wires {@code threat} to enemy-combatant
 * density via {@code TacticalScoring.countCombatantsWithin}.
 *
 * <p><b>Cold spread (D2).</b> Threat only <em>ranks</em> cells here; the zone radius is fixed, so a
 * safe DZ and a dangerous one scatter equally wide. D3 (AA / hot drops) widens the radius with threat
 * so a hot DZ lands wider and more isolating.
 */
public final class DropZoneScatter {

    private DropZoneScatter() {}

    /** Tests whether a cell is valid landing ground (in-bounds + walkable). */
    @FunctionalInterface
    public interface CellWalkable {
        boolean test(int cellX, int cellY);
    }

    /** Danger of landing on a cell — higher is worse. The engine ranks ascending (safest first). */
    @FunctionalInterface
    public interface CellThreat {
        float at(int cellX, int cellY);
    }

    /**
     * Jitter added to each cell's threat score so cells of equal threat scatter instead of always
     * resolving to the same cluster. Smaller than the threat unit (enemy-count), so threat dominates
     * the ranking and jitter only breaks ties within a threat level.
     */
    private static final float THREAT_JITTER = 0.5f;

    /**
     * Picks up to {@code count} distinct walkable landing cells within {@code radius} cells of
     * ({@code centerX},{@code centerY}), preferring low-threat cells and keeping each at least
     * {@code minSpacing} cells from every other pick (the scatter). Returns fewer than {@code count}
     * — possibly empty — when the zone can't supply that many spaced walkable cells.
     *
     * @param rng jitters the threat score so equally-safe cells don't always resolve to the same
     *            layout across launches — the chaos of a paratrooper drop. Pass a seeded instance for
     *            reproducibility (e.g. in tests).
     */
    public static List<int[]> sample(int centerX, int centerY, float radius, int count, float minSpacing,
                                     CellWalkable walkable, CellThreat threat, Random rng) {
        List<int[]> picks = new ArrayList<>();
        if (count <= 0 || radius <= 0f) return picks;

        int r = (int) Math.ceil(radius);
        float r2 = radius * radius;

        // Candidate walkable cells in the disc, each scored by threat + a little jitter.
        List<Scored> candidates = new ArrayList<>();
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy > r2) continue;
                int cx = centerX + dx;
                int cy = centerY + dy;
                if (!walkable.test(cx, cy)) continue;
                candidates.add(new Scored(cx, cy, threat.at(cx, cy) + rng.nextFloat() * THREAT_JITTER));
            }
        }
        candidates.sort((a, b) -> Float.compare(a.score, b.score));

        // Greedy safest-first pick, rejecting any candidate too close to one already taken.
        float spacing2 = minSpacing * minSpacing;
        for (Scored c : candidates) {
            if (picks.size() >= count) break;
            boolean tooClose = false;
            for (int[] p : picks) {
                int sdx = p[0] - c.cellX;
                int sdy = p[1] - c.cellY;
                if (sdx * sdx + sdy * sdy < spacing2) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) picks.add(new int[]{c.cellX, c.cellY});
        }
        return picks;
    }

    private static final class Scored {
        final int cellX;
        final int cellY;
        final float score;

        Scored(int cellX, int cellY, float score) {
            this.cellX = cellX;
            this.cellY = cellY;
            this.score = score;
        }
    }
}
