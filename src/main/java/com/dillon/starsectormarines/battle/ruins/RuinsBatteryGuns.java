package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Branch;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.campaign.ruins.RuinsBatteries;

import java.util.ArrayList;
import java.util.List;

/**
 * The old colony's defence batteries in the battle ({@code ruins-expedition.md}, The vault and its prize; owner pick
 * {@code ruins-theme-defences} a; the rules are {@link RuinsBatteries}). Each is an existing gun emplacement
 * ({@link #STRUCTURE}) standing on the approach outside an entrance to the interior, toward the landing, its power cut.
 *
 * <p>They hang on the first junction's way on, beside the first layer's gate: restarting the hall wakes them; throwing
 * the first junction to its store cuts them, at the price of that gate's power. So the power that opens the vault
 * also arms the guns on the way out.
 *
 * <p>They stand on the defenders' side, so they fire on the company and its allies. Firing on the occupant too needs
 * a side hostile to everyone, which the battle does not have yet.
 */
public final class RuinsBatteryGuns {

    /** The emplacement a battery is built on: the heavy machine gun's gun and durability. */
    public static final String STRUCTURE = "structure.turret-heavy-mg";
    /** The junction whose way on feeds the batteries: the first layer's. */
    static final int JUNCTION = 0;
    /** How far out from an entrance's apron centre a battery stands, cells. UNTUNED. */
    static final int STANDOFF = 9;
    /** How much farther out the search for open ground may go, cells. */
    static final int REACH = 8;
    /** The fewest cells between two batteries. UNTUNED. */
    static final int SPACING = 5;
    /** The bearings tried about the line to the landing, degrees, nearest it first. */
    private static final int[] BEARINGS = {0, 25, -25, 50, -50, 75, -75, 100, -100};

    /** Whether a cell is open ground a battery may stand on. */
    public interface Open {
        boolean at(int x, int y);
    }

    private final List<Long> guns;
    private RuinsBatteries.State state = RuinsBatteries.State.DARK;

    private RuinsBatteryGuns(List<Long> guns) {
        this.guns = List.copyOf(guns);
    }

    /** The batteries' state now. */
    public RuinsBatteries.State state() { return state; }

    /** The batteries' unit ids. */
    public List<Long> guns() { return guns; }

    /**
     * Where {@code count} batteries stand: about each entrance's apron in turn, {@link #STANDOFF} cells out toward
     * the landing, on open ground outside every apron and the interior, {@link #SPACING} apart. Fewer when the ground
     * has no room; none without an interior.
     *
     * @param aprons   the entrances' aprons, whose ground stays clear for the company's hold
     * @param interior the interior's footprint, which no battery stands in
     * @param landingX the landing's centre, which the batteries face out toward
     */
    public static List<int[]> sites(List<RuinsDelve.Area> aprons, RuinsDelve.Area interior, int landingX,
                                    int landingY, int count, Open open) {
        List<int[]> out = new ArrayList<>(count);
        if (aprons.isEmpty() || count <= 0) return out;
        for (int i = 0; i < count; i++) {
            RuinsDelve.Area apron = aprons.get(i % aprons.size());
            int[] site = site(apron, aprons, interior, landingX, landingY, out, open);
            if (site != null) out.add(site);
        }
        return out;
    }

    private static int[] site(RuinsDelve.Area apron, List<RuinsDelve.Area> aprons, RuinsDelve.Area interior,
                              int landingX, int landingY, List<int[]> taken, Open open) {
        int cx = apron.centreX();
        int cy = apron.centreY();
        double toward = Math.atan2(landingY - cy, landingX - cx);
        for (int r = STANDOFF; r <= STANDOFF + REACH; r++) {
            for (int bearing : BEARINGS) {
                double a = toward + Math.toRadians(bearing);
                int x = cx + (int) Math.round(r * Math.cos(a));
                int y = cy + (int) Math.round(r * Math.sin(a));
                if (fits(x, y, aprons, interior, taken, open)) return new int[] {x, y};
            }
        }
        return null;
    }

    private static boolean fits(int x, int y, List<RuinsDelve.Area> aprons, RuinsDelve.Area interior,
                                List<int[]> taken, Open open) {
        if (!open.at(x, y) || (interior != null && interior.contains(x, y))) return false;
        for (RuinsDelve.Area apron : aprons) if (apron.contains(x, y)) return false;
        for (int[] other : taken) {
            int dx = other[0] - x;
            int dy = other[1] - y;
            if (dx * dx + dy * dy < SPACING * SPACING) return false;
        }
        return true;
    }

    /**
     * Stands a site's batteries on its approach, power cut, and returns them for the battle to tick; null when there
     * are none to stand. At setup only, after the works are installed.
     */
    public static RuinsBatteryGuns install(BattleSimulation sim, int count) {
        RuinsWorks works = sim.getRuinsWorks();
        if (count <= 0 || works == null || works.delve() == null || works.delve().interior() == null) return null;
        RuinsDelve delve = works.delve();
        List<RuinsDelve.Area> aprons = new ArrayList<>();
        for (RuinsDelve.Apron apron : delve.interior().aprons()) aprons.add(apron.ground());
        if (aprons.isEmpty()) aprons.addAll(delve.interior().entrances());
        NavigationGrid grid = sim.getGrid();
        RuinsDelve.Area landing = delve.landingGround();
        List<int[]> sites = sites(aprons, delve.interior().footprint(), landing.centreX(), landing.centreY(), count,
                (x, y) -> grid.inBounds(x, y) && grid.isWalkable(x, y));
        List<Long> guns = new ArrayList<>(sites.size());
        for (int i = 0; i < sites.size(); i++) {
            int x = sites.get(i)[0];
            int y = sites.get(i)[1];
            long gun = sim.spawn(MapTurret.create("ruins-battery-" + (i + 1), Faction.DEFENDER, STRUCTURE, x, y));
            if (gun == 0L) continue;
            grid.setWalkable(x, y, false);
            grid.setSeeThrough(x, y, true);
            grid.recomputeCoverAt(x + 1, y);
            grid.recomputeCoverAt(x - 1, y);
            grid.recomputeCoverAt(x, y + 1);
            grid.recomputeCoverAt(x, y - 1);
            sim.turretState().cutPower(gun);
            guns.add(gun);
        }
        return guns.isEmpty() ? null : new RuinsBatteryGuns(guns);
    }

    /** One step: the batteries follow the hall and their junction. */
    public void tick(BattleSimulation sim) {
        RuinsWorks works = sim.getRuinsWorks();
        if (works == null) return;
        PowerGraph graph = works.graph();
        RuinsBatteries.State next = RuinsBatteries.state(graph.running(),
                graph.junctionCount() > JUNCTION && graph.position(JUNCTION) == Branch.ONWARD);
        if (next == state) return;
        state = next;
        for (long gun : guns) {
            if (state == RuinsBatteries.State.AWAKE) sim.turretState().restorePower(gun);
            else sim.turretState().cutPower(gun);
        }
    }
}
