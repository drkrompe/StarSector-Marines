package com.dillon.starsectormarines.battle.world.gen.damage;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan.Layer;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan.Mode;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan.Point;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoofDamage;
import java.util.BitSet;

/** Deterministic brush coverage and one structural hit per object per layer. No battle RNG draws. */
public final class DamageBrush {
    private DamageBrush() {}
    public static byte[] rasterize(DamagePlan plan, Layer layer) {
        byte[] cells = new byte[plan.width() * plan.height()];
        if (!layer.enabled()) return cells;
        for (var stroke : layer.strokes()) {
            BitSet touched = new BitSet(cells.length);
            for (int i = 0; i < stroke.points().size(); i++) {
                Point b = stroke.points().get(i), a = stroke.points().get(Math.max(0, i - 1));
                double r = stroke.radius(), vx = b.x() - a.x(), vy = b.y() - a.y(), length = vx * vx + vy * vy;
                for (int y = Math.max(0, (int)Math.floor(Math.min(a.y(), b.y()) - r)); y <= Math.min(plan.height() - 1, Math.ceil(Math.max(a.y(), b.y()) + r)); y++)
                    for (int x = Math.max(0, (int)Math.floor(Math.min(a.x(), b.x()) - r)); x <= Math.min(plan.width() - 1, Math.ceil(Math.max(a.x(), b.x()) + r)); x++) {
                        double t = length == 0 ? 0 : Math.max(0, Math.min(1, ((x - a.x()) * vx + (y - a.y()) * vy) / length));
                        double dx = x - a.x() - t * vx, dy = y - a.y() - t * vy;
                        // Boundary breakup is stable when a stroke is extended or resampled.
                        long hash = plan.seed() ^ (x * 0x9E3779B9L) ^ (y * 0x85EBCA77L);
                        hash ^= hash >>> 16; hash *= 0xC2B2AE3DL; hash ^= hash >>> 13;
                        double radius = stroke.mode() == Mode.ERASE ? r : r * (.82 + (hash & 255) / 255d * .18);
                        if (dx * dx + dy * dy <= radius * radius) touched.set(y * plan.width() + x);
                    }
            }
            for (int at = touched.nextSetBit(0); at >= 0; at = touched.nextSetBit(at + 1))
                cells[at] = stroke.mode() == Mode.ERASE ? 0 : (byte)Math.max(cells[at], stroke.mode().ordinal());
        }
        return cells;
    }
    public static void apply(DamagePlan plan, NavigationGrid grid, CellTopology topology, MapEditor editor) {
        if (plan.width() > grid.getWidth() || plan.height() > grid.getHeight()) throw new IllegalArgumentException("Damage layer exceeds map");
        for (Layer layer : plan.layers()) {
            byte[] cells = rasterize(plan, layer);
            // Separate strengths keep a brush drag from repeatedly spending multi-cell prop HP.
            for (Mode mode : new Mode[]{Mode.BATTERED, Mode.RUIN}) {
                BitSet hit = new BitSet(grid.getWidth() * grid.getHeight());
                for (int at = 0; at < cells.length; at++) if (cells[at] == mode.ordinal())
                    hit.set((at / plan.width()) * grid.getWidth() + at % plan.width());
                editor.damageSelectedCells(hit, mode == Mode.RUIN ? 100000 : 35);
            }
            for (int at = 0; at < cells.length; at++) if (cells[at] > 0) {
                int x = at % plan.width(), y = at / plan.width();
                if (cells[at] == Mode.RUIN.ordinal()) {
                    // An old ruin has fallen in, and here and there burnt: in patches of a few cells, not cell by cell.
                    long burn = plan.seed() ^ ((x >> 2) * 0x7FEB352DL) ^ ((y >> 2) * 0x846CA68BL);
                    burn ^= burn >>> 15; burn *= 0x2C1B3C6DL; burn ^= burn >>> 12;
                    editor.destroyRoof(x, y, (burn & 3) == 0 ? RoofDamage.BURNT : RoofDamage.COLLAPSED);
                    for (Direction side : Direction.CARDINALS) editor.damageEdgeBarrier(x, y, side, 100000);
                }
                // Old impacts are scattered marks, not a uniformly stamped damage carpet.
                long fleck = plan.seed() ^ (x * 0x632BE59BL) ^ (y * 0x9E3779B9L);
                fleck ^= fleck >>> 16; fleck *= 0x85EBCA77L; fleck ^= fleck >>> 13;
                if ((fleck & 255) < 150) editor.damageGround(x, y);
            }
        }
    }
}
