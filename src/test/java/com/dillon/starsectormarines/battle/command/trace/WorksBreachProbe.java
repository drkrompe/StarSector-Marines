package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.cordon.CordonRedoubt;
import com.dillon.starsectormarines.battle.cordon.CordonSequence;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prints one {@code [works] <run> <json>} line per battle: how long the walls of its forts and objectives
 * ({@code FortifiedWorks}) held once something started on them. Per place (a Cordon redoubt by number, else
 * every works cell together as {@code works}): the cells standing at the start, the first tick one was hit,
 * the first tick one opened, how many opened, and the seconds from a cell's first hit to its opening (the
 * least and the median over the cells that opened). Whatever did the damage counts; the swarm does nearly all
 * of it. Read off the grid every {@link #EVERY} ticks; it draws no random number.
 */
final class WorksBreachProbe {

    private static final int EVERY = 15;

    private final int[] cells;
    private final int[] startHp;
    private final int[] hitTick;
    private final int[] openTick;
    private final String[] place;
    private final int width;

    WorksBreachProbe(BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        width = grid.getWidth();
        CordonSequence cordon = sim.getCordon();
        List<Integer> found = new ArrayList<>();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < width; x++) {
                if (grid.hasTag(x, y, NavigationGrid.CellTag.WORKS) && !grid.isWalkable(x, y)
                        && grid.getWallHp(x, y) > 0) found.add(y * width + x);
            }
        }
        cells = found.stream().mapToInt(Integer::intValue).toArray();
        startHp = new int[cells.length];
        hitTick = new int[cells.length];
        openTick = new int[cells.length];
        place = new String[cells.length];
        Arrays.fill(hitTick, -1);
        Arrays.fill(openTick, -1);
        for (int i = 0; i < cells.length; i++) {
            int x = cells[i] % width, y = cells[i] / width;
            startHp[i] = grid.getWallHp(x, y);
            place[i] = "works";
            if (cordon == null) continue;
            for (CordonRedoubt r : cordon.redoubts()) {
                if (x >= r.left() - 1 && x <= r.right() + 1 && y >= r.top() - 1 && y <= r.bottom() + 1) {
                    place[i] = "redoubt-" + r.number() + "-" + r.construction().name().toLowerCase();
                }
            }
        }
    }

    void afterAdvance(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        if (tick % EVERY != 0) return;
        NavigationGrid grid = sim.getGrid();
        for (int i = 0; i < cells.length; i++) {
            if (openTick[i] >= 0) continue;
            int x = cells[i] % width, y = cells[i] / width;
            if (grid.isWalkable(x, y)) {
                openTick[i] = tick;
                if (hitTick[i] < 0) hitTick[i] = tick;
            } else if (hitTick[i] < 0 && grid.getWallHp(x, y) < startHp[i]) {
                hitTick[i] = tick;
            }
        }
    }

    void print(String run) throws Exception {
        Map<String, List<Integer>> byPlace = new LinkedHashMap<>();
        for (int i = 0; i < cells.length; i++) byPlace.computeIfAbsent(place[i], k -> new ArrayList<>()).add(i);
        JSONArray places = new JSONArray();
        float secs = BattleSimulation.TICK_DT;
        for (Map.Entry<String, List<Integer>> e : byPlace.entrySet()) {
            int firstHit = -1, firstOpen = -1, opened = 0;
            List<Integer> held = new ArrayList<>();
            for (int i : e.getValue()) {
                if (hitTick[i] >= 0 && (firstHit < 0 || hitTick[i] < firstHit)) firstHit = hitTick[i];
                if (openTick[i] < 0) continue;
                opened++;
                if (firstOpen < 0 || openTick[i] < firstOpen) firstOpen = openTick[i];
                held.add(openTick[i] - hitTick[i]);
            }
            held.sort(null);
            places.put(new JSONObject().put("place", e.getKey()).put("cells", e.getValue().size())
                    .put("firstHitTick", firstHit).put("firstOpenTick", firstOpen).put("opened", opened)
                    .put("minHoldSeconds", held.isEmpty() ? -1 : Math.round(held.get(0) * secs * 10f) / 10f)
                    .put("medianHoldSeconds", held.isEmpty() ? -1
                            : Math.round(held.get(held.size() / 2) * secs * 10f) / 10f));
        }
        System.out.println("[works] " + run + " " + new JSONObject().put("places", places));
    }
}
