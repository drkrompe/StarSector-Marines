package com.dillon.starsectormarines.battle.salvage;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/** Battle-local recovery ledger; no cargo, mission victory, or automatic squad-order authority. */
public final class FieldSalvage {
    public static final float REACH = 3f;
    public static final float CONTEST_RADIUS = 8f;
    private final List<SalvageCache> caches;

    public FieldSalvage(List<SalvageCache> caches) {
        this.caches = List.copyOf(caches);
        if (caches.stream().map(c -> c.id).distinct().count() != caches.size())
            throw new IllegalArgumentException("Duplicate salvage identity");
    }

    public List<SalvageCache> caches() { return caches; }
    public List<SalvageCache.Recovered> recovered() {
        return caches.stream().filter(SalvageCache::secured).map(SalvageCache::recovered).toList();
    }

    /** A short unobstructed walking reach; transparent windows/fences cannot be looted through. */
    static boolean canReach(NavigationGrid grid, int x, int y, int tx, int ty) {
        if (Math.abs(tx - x) > 3 || Math.abs(ty - y) > 3) return false;
        while (x != tx || y != ty) {
            int nx = x + Integer.signum(tx - x), ny = y + Integer.signum(ty - y);
            if (!grid.canTraverseCellStep(x, y, nx, ny)) return false;
            x = nx; y = ny;
        }
        return grid.isWalkable(x,y);
    }

    public void tick(BattleSimulation sim) {
        if (sim.isComplete()) return;
        for (var cache : caches) {
            if (cache.secured()) continue;
            boolean visible = sim.playerSeesCell(cache.cellX, cache.cellY);
            boolean nearby = false, hostile = false;
            if (visible) for (int i = 0; i < sim.liveUnitCount(); i++) {
                long id = sim.liveUnitAt(i);
                var type = sim.identity().type(id);
                if (!type.combatant) continue;
                float x = sim.world().x(id), y = sim.world().y(id);
                float dx = x - cache.cellX - .5f, dy = y - cache.cellY - .5f;
                float d2 = dx * dx + dy * dy;
                var faction = sim.identity().faction(id);
                if (faction == Faction.MARINE && type.usesInfantryTraining() && d2 <= REACH * REACH
                        && canReach(sim.getGrid(), (int) x, (int) y, cache.cellX, cache.cellY)) nearby = true;
                if (Faction.MARINE.hostileTo(faction) && d2 <= CONTEST_RADIUS * CONTEST_RADIUS
                        && sim.getGrid().hasLineOfFire(x, y, cache.cellX + .5f, cache.cellY + .5f)) hostile = true;
            }
            cache.advance(BattleSimulation.TICK_DT, visible, nearby, hostile);
        }
    }
}
