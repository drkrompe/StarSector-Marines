package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The works' machines ({@code raid-demolish-the-works.md}, slice 6): security adds them on top of the market's own
 * force (owner, on {@code raid-works-machines-source} and {@code raid-works-vehicles} c). A guarded works has machine
 * posts at its gates and a fortified one a yard reserve as well ({@code WorksDefences}); each post is filled with
 * mechs of the market's own line, and a hull of its faction parks beside it: a light one at a gate, a heavy one in the
 * yard.
 */
final class RaidWorksMachines {
    private RaidWorksMachines() {}

    /** Cells a hull parks clear of its post's own cell, so the post's mechs stand by it, not under it. UNTUNED. */
    static final int HULL_STANDOFF = 3;

    /** The light hull a faction posts at a works gate (owner pick {@code raid-works-hulls} a, recommended). */
    static VehicleType gateHull(String factionId) {
        return switch (key(factionId)) {
            case "tritachyon" -> VehicleType.HOVER_SCOUT;
            case "pirates", "luddic_path" -> VehicleType.MILITIA_TECHNICAL;
            default -> VehicleType.HEAVY_APC;
        };
    }

    /** The heavy hull a faction keeps in a works' yard reserve (owner pick {@code raid-works-hulls} a, recommended). */
    static VehicleType yardHull(String factionId) {
        return switch (key(factionId)) {
            case "tritachyon" -> VehicleType.HEAVY_SKIMMER;
            case "sindrian_diktat", "lions_guard" -> VehicleType.LASER_TANK;
            case "pirates", "luddic_path" -> VehicleType.SRM_TECHNICAL;
            default -> VehicleType.MEDIUM_TANK;
        };
    }

    /** The hull parked at {@code post}, or null for a node that is not one of the works' machine posts. */
    static VehicleType hullFor(TacticalNode post, String factionId) {
        if (RaidSiteTemplate.WORKS_RESERVE.equals(post.place())) return yardHull(factionId);
        if (RaidSiteTemplate.WORKS_MACHINES.equals(post.place())) return gateHull(factionId);
        return null;
    }

    /**
     * The mechs security adds for the works' machine posts: as many as the posts hold, of the market's own line
     * ({@code heavySupport}, cycled), or Bulwarks for a line with none. Empty when the works has no posts (an open
     * works, or no works).
     */
    static List<MechVariant> extraMechs(List<TacticalNode> machinePosts, List<MechVariant> heavySupport) {
        int count = 0;
        for (TacticalNode post : machinePosts) count += Math.max(0, post.garrisonSize);
        List<MechVariant> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(heavySupport == null || heavySupport.isEmpty() ? MechVariant.BULWARK
                    : heavySupport.get(i % heavySupport.size()));
        }
        return out;
    }

    /** The riflemen security adds for the works' forward posts: as many as the posts hold, of the market's bulk. */
    static List<GroundRosterProfile.ForceTier> extraRiflemen(List<TacticalNode> forwardPosts) {
        int count = 0;
        for (TacticalNode post : forwardPosts) count += Math.max(0, post.garrisonSize);
        return Collections.nCopies(count, GroundRosterProfile.ForceTier.BULK);
    }

    /** Parks a hull of {@code factionId}'s at each of the works' machine posts among {@code nodes}. */
    static void parkHulls(BattleSimulation sim, List<TacticalNode> nodes, String factionId) {
        for (TacticalNode post : RaidWorksGuard.machinePosts(nodes)) {
            VehicleType type = hullFor(post, factionId);
            if (type == null) continue;
            VehicleClearance mask = VehicleClearance.erode(sim.getGrid(),
                    VehicleClearance.radiusForWidth(type.visualWidthCells));
            int[] at = clearBeside(mask, post.anchorX, post.anchorY);
            if (at == null) continue;
            long id = sim.convoy().spawn(type, Faction.DEFENDER, VehicleMission.deployed(at[0] + .5f, at[1] + .5f));
            sim.convoy().body(id).teleport(at[0] + .5f, at[1] + .5f, 0f);
        }
    }

    /** The clear cell nearest {@code (x, y)} at least {@link #HULL_STANDOFF} from it, or null within twelve cells. */
    private static int[] clearBeside(VehicleClearance mask, int x, int y) {
        for (int r = HULL_STANDOFF; r < 12; r++)
            for (int dy = -r; dy <= r; dy++)
                for (int dx = -r; dx <= r; dx++)
                    if (Math.max(Math.abs(dx), Math.abs(dy)) == r && mask.isPassable(x + dx, y + dy))
                        return new int[]{x + dx, y + dy};
        return null;
    }

    private static String key(String factionId) {
        return factionId == null ? "" : factionId.trim().toLowerCase(Locale.ROOT);
    }
}
