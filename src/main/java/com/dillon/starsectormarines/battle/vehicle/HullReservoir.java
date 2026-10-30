package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechFlux;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;

import java.util.List;

/**
 * A hull's one flux reservoir (`convoy-nouns.md`, hull flux): every mount on the hull draws its rounds' flux from it,
 * under the mech's own admission rule ({@link MechFlux}), so a machine is a machine whatever it rolls on. The frame is
 * frozen at the drop from the hull and its rack.
 */
public final class HullReservoir {

    /**
     * {@code -Dbattle.vehicle.hullReservoir=false} restores the per-mount control path: each mount its own bank
     * ({@link TurretMountDef.ControlBank}), overloading at full and venting to empty. Temporary: remove it with that
     * path, the control banks in the mount data and {@link GroundTurret#overloaded}, once the vehicle flux rework's
     * armorMatchup and convoy evidence has been read against it (`switches.md`).
     */
    static final String PROPERTY = "battle.vehicle.hullReservoir";
    static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    private HullReservoir() { }

    /** A hull's flux frame at the drop: what its reservoir holds and sheds a second. */
    public record Frame(float capacity, float dissipationPerSecond) { }

    /**
     * The frame a hull of {@code type} with {@code kits} on its rack is set down with, or null for a hull whose gun
     * builds no flux. Today it is the hull's own gun's bank, half again with a capacitor pack.
     */
    public static Frame frame(VehicleType type, List<VehicleKit> kits) {
        if (type == null || !type.hasTurretWeapon()) return null;
        StructureDef stock = type.turretStructure();
        TurretMountDef.Flux flux = stock != null ? stock.mount.flux : null;
        if (flux == null) return null;
        float capacity = flux.control().capacity() * VehicleKit.Effect.of(kits).fluxCapacityFactor();
        return new Frame(capacity, flux.control().dissipationPerSec());
    }

    /** A fresh reservoir on {@code frame}, or null on the control path or for no frame. */
    public static MechFlux create(Frame frame) {
        return ENABLED && frame != null ? new MechFlux(frame.capacity(), frame.dissipationPerSecond()) : null;
    }

    /** Puts one reservoir on the hull's main gun and every secondary mount, so they all draw on it. */
    public static void install(GroundTurret main, MechFlux reservoir) {
        main.reservoir = reservoir;
        for (MountedGun gun : main.secondaries) gun.turret().reservoir = reservoir;
    }
}
