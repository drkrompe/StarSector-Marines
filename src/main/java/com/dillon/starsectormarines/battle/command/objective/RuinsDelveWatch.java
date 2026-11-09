package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.objective.RuinsDelveRow.Picture;
import com.dillon.starsectormarines.battle.command.objective.RuinsDelveRow.Stand;
import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective.RuinsPhase;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.ruins.RuinsPressure;
import com.dillon.starsectormarines.battle.ruins.RuinsWorks;
import com.dillon.starsectormarines.battle.salvage.FieldSalvage;
import com.dillon.starsectormarines.battle.salvage.SalvageCache;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.WithdrawalExtraction;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What the Delve row reads, kept through the battle ({@code ruins-expedition.md}, Judging the forces, and Losses are
 * felt at the stands): the company's strength against what it landed, its finds, its depth, the way back, and the
 * stands it has held. Taking the entry is the first stand; each layer's works junction is the next, held as a court
 * is. At each stand the watch takes the tally since the last one and the next layer's close reading.
 */
public final class RuinsDelveWatch {

    /** How close to a layer's junction the company must hold to take its stand. */
    static final int STAND_RADIUS = 2;

    /** How long a stand's tally leads the row before it gives way to the company at a glance. */
    static final float STAND_REPORT_SECONDS = 20f;

    /** Living hostiles in a layer up to which it reads quiet, and up to which it reads held. */
    static final int QUIET_AT_MOST = 2;
    static final int HELD_AT_MOST = 7;

    /** A layer's strength band, as the close reading words it. */
    public enum Band {
        QUIET("ruinsCloseQuiet"), HELD("ruinsCloseHeld"), STRONGLY_HELD("ruinsCloseStronglyHeld");

        public final String key;

        Band(String key) { this.key = key; }
    }

    private final int layers;
    private final int firstInside;
    private final RuinsDelve.Area[] grounds;
    private final int[][] junctions;
    private final UncontestedHold[] stands;
    private final List<String> signs;
    private final long seed;
    /** The vault prize's sign, read in place of the occupant's at the stand before the vault's layer; null for none. */
    private String vaultSign;

    private final Set<Long> landed = new HashSet<>();
    private final Set<Long> machines = new HashSet<>();
    private int effectives;
    private int machinesAlive;
    private int squadsBelowHalf;
    private int insideCircle;
    private int deepest;
    private int lostAtStand;
    private int machinesLostAtStand;
    private int findsAtStand;
    private boolean entryStood;
    private Stand stand;
    private int standAt;
    private int routeBack = -1;
    private int vaultRoute = -1;
    private int distanceFrom = -1;
    private int[] distance;

    /**
     * @param grounds   each layer's ground, outermost first
     * @param junctions each layer's stand cell as {x, y}, or null for a layer with none
     * @param firstInside the first layer inside the interior
     * @param signs     the occupant's readings pool, the signs a close reading may name
     */
    RuinsDelveWatch(List<RuinsDelve.Area> grounds, int[][] junctions, int firstInside, List<String> signs,
                    long seed) {
        if (grounds.isEmpty() || junctions.length != grounds.size() || signs.isEmpty()) {
            throw new IllegalArgumentException("a delve watch needs every layer, its junctions and a readings pool");
        }
        this.layers = grounds.size();
        this.grounds = grounds.toArray(new RuinsDelve.Area[0]);
        this.junctions = junctions;
        this.firstInside = Math.max(0, Math.min(firstInside, layers - 1));
        this.stands = new UncontestedHold[layers];
        for (int i = 0; i < layers; i++) {
            if (junctions[i] != null) stands[i] = new UncontestedHold(RuinsVaultObjective.COURT_HOLD_SECONDS);
        }
        this.signs = List.copyOf(signs);
        this.seed = seed;
    }

    /** The watch over a ruins delve, its close readings drawn from the occupant's readings pool. */
    public static RuinsDelveWatch of(RuinsDelve delve, List<String> signs, long seed) {
        List<RuinsDelve.Area> grounds = delve.layers().stream().map(RuinsDelve.Layer::ground).toList();
        int[][] junctions = new int[grounds.size()][];
        for (RuinsDelve.Junction junction : delve.junctions()) {
            if (junction.layer() >= 0 && junction.layer() < junctions.length) {
                junctions[junction.layer()] = new int[]{junction.stand().x(), junction.stand().y()};
            }
        }
        int firstInside = delve.interior() != null ? delve.interior().firstLayer() : 0;
        return new RuinsDelveWatch(grounds, junctions, firstInside, signs, seed);
    }

    /**
     * A layer's strength band from the hostiles living in it. The vault's layer, the last, always reads strongest.
     */
    static Band band(int hostiles, boolean vaultLayer) {
        if (vaultLayer || hostiles > HELD_AT_MOST) return Band.STRONGLY_HELD;
        return hostiles > QUIET_AT_MOST ? Band.HELD : Band.QUIET;
    }

    /**
     * Names the vault prize's sign ({@code ruins-expedition.md}, The vault and its prize): the close reading of the
     * vault's layer tells what the site was built round, the tell the survey promised seen up close.
     */
    public RuinsDelveWatch withVaultSign(String key) {
        vaultSign = key;
        return this;
    }

    /** The sign a stand reads of layer {@code next}: the vault's own at the last layer when it has one. */
    static String signAt(List<String> signs, String vaultSign, long seed, int next, int layers) {
        return vaultSign != null && next == layers - 1 ? vaultSign : sign(signs, seed, next);
    }

    /** The occupant's sign in a layer: one reading of the pool, the same for the same battle and layer. */
    static String sign(List<String> signs, long seed, int layer) {
        long h = seed * 0x9E3779B97F4A7C15L + (layer + 1) * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        return signs.get((int) Math.floorMod(h, (long) signs.size()));
    }

    public void tick(BattleSimulation sim, RuinsVaultObjective vault) {
        int point = vault.pullOutPoint().liftPoint();
        if (point != distanceFrom) {
            distanceFrom = point;
            distance = distances(sim.getGrid(), vault.liftPointCellX(point), vault.liftPointCellY(point));
        }
        countCompany(sim);
        // Once the vault is sighted (the company stands on its layer), the haul's route to its lift point.
        if (deepest == layers - 1 || vault.breached()) vaultRoute = vault.haulRouteCellCount() - vault.haulRouteIndex();
        int lost = landed.size() - effectives;
        int machinesLost = machines.size() - machinesAlive;
        int finds = finds(sim);
        if (vault.entry() >= 0 && !entryStood) {
            entryStood = true;
            stand(sim, Strings.get("ruinsDelveStandEntry"), firstInside, lost, machinesLost, finds);
        }
        if (entryStood) {
            for (int i = firstInside; i < layers; i++) {
                UncontestedHold hold = stands[i];
                if (hold == null || hold.held()) continue;
                if (hold.tick(sim, junctions[i][0], junctions[i][1], STAND_RADIUS, -1)) {
                    stand(sim, Strings.format("ruinsDelveStandLayer", i + 1), i + 1, lost, machinesLost, finds);
                }
            }
        }
    }

    /** The row's picture now. */
    public Picture picture(BattleSimulation sim, RuinsVaultObjective vault) {
        RuinsWorks works = sim.getRuinsWorks();
        int stores = 0;
        if (works != null) for (SalvageCache store : works.storeCaches()) if (store.discovered()) stores++;
        boolean fresh = stand != null
                && (sim.getSimTickIndex() - standAt) * BattleSimulation.TICK_DT < STAND_REPORT_SECONDS;
        return new Picture(vault.ruinsPhase(), insideCircle, effectives, landed.size(), squadsBelowHalf,
                machines.size() - machinesAlive, finds(sim), stores, routeBack, deepest + 1, layers, vaultRoute, stand,
                fresh, stir(sim));
    }

    /** The site's stir as the row reads it, or null on a site with none. */
    static RuinsDelveRow.Stir stir(BattleSimulation sim) {
        RuinsPressure pressure = sim.ruinsPressure();
        if (pressure == null) return null;
        String reserve = switch (pressure.reserveBand()) {
            case FULL -> "ruinsDelveRowReserveFull";
            case THINNED -> "ruinsDelveRowReserveThinned";
            case SPENT -> "ruinsDelveRowReserveSpent";
        };
        boolean held = pressure.fielded() != null && pressure.fielded().wayBackHeld(sim);
        return new RuinsDelveRow.Stir(pressure.level().key, pressure.towardNext(), reserve, pressure.dugIn(), held);
    }

    /** Takes a stand: the tally since the last one, and the close reading of {@code next} if a layer lies there. */
    private void stand(BattleSimulation sim, String where, int next, int lost, int machinesLost, int finds) {
        String bandKey = null;
        String signKey = null;
        if (next < layers) {
            bandKey = band(hostilesIn(sim, grounds[next]), next == layers - 1).key;
            signKey = signAt(signs, vaultSign, seed, next, layers);
        }
        stand = new Stand(where, lost - lostAtStand, machinesLost - machinesLostAtStand, finds - findsAtStand,
                bandKey, signKey);
        standAt = sim.getSimTickIndex();
        lostAtStand = lost;
        machinesLostAtStand = machinesLost;
        findsAtStand = finds;
    }

    private void countCompany(BattleSimulation sim) {
        effectives = 0;
        machinesAlive = 0;
        insideCircle = 0;
        routeBack = -1;
        NavigationGrid grid = sim.getGrid();
        var pads = sim.withdrawalPads();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE) continue;
            UnitType type = sim.identity().type(unit);
            if (!type.combatant || !sim.movement().has(unit)) continue;
            landed.add(unit);
            effectives++;
            if (type.isMech() || type.isGroundVehicle()) {
                machines.add(unit);
                machinesAlive++;
            }
            if (WithdrawalExtraction.extracts(pads, sim.world().x(unit), sim.world().y(unit))) insideCircle++;
            int x = sim.world().cellX(unit);
            int y = sim.world().cellY(unit);
            // The way back: walking cells from the company's farthest combatant to the pull-out point.
            if (grid.inBounds(x, y)) routeBack = Math.max(routeBack, distance[y * grid.getWidth() + x]);
            for (int layer = layers - 1; layer > deepest; layer--) {
                if (grounds[layer].contains(x, y)) {
                    deepest = layer;
                    break;
                }
            }
        }
        squadsBelowHalf = 0;
        for (Squad squad : sim.getSquads()) {
            if (squad.faction == Faction.MARINE && squad.originalSize > 0 && squad.aliveMembers > 0
                    && squad.aliveMembers * 2 < squad.originalSize) squadsBelowHalf++;
        }
    }

    private static int finds(BattleSimulation sim) {
        FieldSalvage salvage = sim.fieldSalvage();
        return salvage == null ? 0 : salvage.recovered().size();
    }

    private static int hostilesIn(BattleSimulation sim, RuinsDelve.Area ground) {
        int count = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.DEFENDER || !sim.identity().type(unit).combatant) continue;
            if (ground.contains(sim.world().cellX(unit), sim.world().cellY(unit))) count++;
        }
        return count;
    }

    /** Walking steps from a cell to every walkable cell, {@code -1} where none reaches. */
    static int[] distances(NavigationGrid grid, int fromX, int fromY) {
        int width = grid.getWidth();
        int[] out = new int[width * grid.getHeight()];
        Arrays.fill(out, -1);
        if (!grid.inBounds(fromX, fromY)) return out;
        ArrayDeque<Integer> open = new ArrayDeque<>();
        out[fromY * width + fromX] = 0;
        open.add(fromY * width + fromX);
        int[][] steps = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        while (!open.isEmpty()) {
            int cell = open.poll();
            int cx = cell % width;
            int cy = cell / width;
            for (int[] step : steps) {
                int x = cx + step[0];
                int y = cy + step[1];
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || out[y * width + x] >= 0) continue;
                out[y * width + x] = out[cell] + 1;
                open.add(y * width + x);
            }
        }
        return out;
    }
}
