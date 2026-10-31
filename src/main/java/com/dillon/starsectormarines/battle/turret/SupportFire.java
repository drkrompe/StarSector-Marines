package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.SideSpotting;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Support emplacements: indirect-fire guns a mission seats behind its own line to fire for the side (a base's rocket
 * artillery), rather than on whatever they happen to see. Mission-neutral; a mission enrols an emplacement and
 * {@link TurretBehavior} aims it by these rules:
 * <ul>
 *   <li>it fires on a <b>mass</b>: the hostile body in its band with the most hostile bodies within
 *   {@link #MASS_CELLS} of it, and at least {@link #MIN_MASS}; a straggler is not worth a salvo;</li>
 *   <li>only on what the side has <b>sighted</b>: the gun's own line of sight, or somebody of its side looking at the
 *   body now ({@link SideSpotting}, the spotting indirect-fire hulls use);</li>
 *   <li>never <b>danger close</b>: the field mortar's veto, for this weapon from this gun
 *   ({@code FieldMortarService.friendlyUnder}).</li>
 * </ul>
 * It keeps a target while all three hold, and looks for a new one at most every {@link #LOOK_TICKS}.
 */
public final class SupportFire {

    /** Cells round a body its mass is counted in. */
    public static final float MASS_CELLS = 5f;
    /** Fewest hostile bodies a mass holds, the target among them. */
    public static final int MIN_MASS = 3;
    /** Ticks between an idle gun's looks for a mass. */
    public static final int LOOK_TICKS = 20;

    private final Set<Long> enrolled = new HashSet<>();
    /** Turrets may be aimed in parallel: each gun's look is its own entry, and the scratch buckets are per thread. */
    private final Map<Long, Integer> nextLook = new ConcurrentHashMap<>();
    private SideSpotting spotting = SideSpotting.NONE;
    private final ThreadLocal<LongBucket> bands = ThreadLocal.withInitial(LongBucket::new);
    private final ThreadLocal<LongBucket> nears = ThreadLocal.withInitial(LongBucket::new);

    /** Enrols an emplacement as a support gun. */
    public void enrol(long gun) {
        if (gun != 0L) enrolled.add(gun);
    }

    public boolean enrolled(long gun) {
        return !enrolled.isEmpty() && enrolled.contains(gun);
    }

    /** Who is looking at what, for each side. */
    public void setSpotting(SideSpotting spotting) {
        this.spotting = spotting != null ? spotting : SideSpotting.NONE;
    }

    /** The rule: a body is a support gun's target when the side has it sighted, no friend is under the round, and it is a mass. */
    public static boolean admits(boolean sighted, boolean dangerClose, int mass) {
        return sighted && !dangerClose && mass >= MIN_MASS;
    }

    /**
     * The gun's target this tick: {@code current} while the rules still admit it, else the biggest admissible mass in
     * its band, or 0 when there is none or it is not yet time to look.
     */
    public long target(BattleSimulation sim, long gun, long current, Faction side, float x, float y,
                       float minRange, float range, WeaponDef weapon) {
        long held = sim.resolveUnit(current);
        if (held != 0L && sim.world().isAlive(held) && inBand(sim, held, x, y, minRange, range)
                && admits(sighted(sim, side, held, x, y), dangerClose(sim, gun, held, side, x, y, weapon),
                mass(sim, side, held))) {
            return held;
        }
        int tick = sim.getSimTickIndex();
        Integer next = nextLook.get(gun);
        if (next != null && tick < next) return 0L;
        nextLook.put(gun, tick + LOOK_TICKS);
        LongBucket band = bands.get();
        sim.getUnitIndex().gather(x, y, range, band);
        long best = 0L;
        int bestMass = 0;
        for (int i = 0, n = band.size; i < n; i++) {
            long id = band.ids[i];
            if (!hostile(sim, side, id) || !inBand(sim, id, x, y, minRange, range)) continue;
            int mass = mass(sim, side, id);
            if (mass < MIN_MASS || mass < bestMass || mass == bestMass && best != 0L && id > best) continue;
            if (!admits(sighted(sim, side, id, x, y), dangerClose(sim, gun, id, side, x, y, weapon), mass)) continue;
            best = id;
            bestMass = mass;
        }
        return best;
    }

    /** Whether a round of {@code weapon} from the gun at {@code target} would land on a friend. */
    public boolean dangerClose(BattleSimulation sim, long gun, long target, Faction side, float x, float y,
                               WeaponDef weapon) {
        return sim.fieldMortars().friendlyUnder(weapon, side, x, y, gun, target, sim);
    }

    private boolean sighted(BattleSimulation sim, Faction side, long id, float x, float y) {
        if (spotting.spotted(side, id)) return true;
        return sim.getGrid().hasLineOfSight((int) Math.floor(x), (int) Math.floor(y),
                sim.world().cellX(id), sim.world().cellY(id));
    }

    private static boolean inBand(BattleSimulation sim, long id, float x, float y, float minRange, float range) {
        float d = (float) Math.hypot(sim.world().x(id) - x, sim.world().y(id) - y);
        return d >= minRange && d <= range;
    }

    private static boolean hostile(BattleSimulation sim, Faction side, long id) {
        if (!sim.world().isAlive(id) || !sim.identity().has(id)) return false;
        UnitType type = sim.identity().type(id);
        if (type.isFlier() || type.isTurret() || sim.getUnitIndex().withheld(id)) return false;
        return side.hostileTo(sim.identity().faction(id));
    }

    /** Hostile bodies within {@link #MASS_CELLS} of {@code id}, itself included. */
    private int mass(BattleSimulation sim, Faction side, long id) {
        LongBucket near = nears.get();
        sim.getUnitIndex().gather(sim.world().x(id), sim.world().y(id), MASS_CELLS, near);
        int n = 0;
        for (int i = 0; i < near.size; i++) if (hostile(sim, side, near.ids[i])) n++;
        return n;
    }
}
