package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;

import java.util.ArrayList;
import java.util.List;

/**
 * A folded defence post (`derelict-security-network.md`, slice 7): a gun emplacement of a zone that neither fires nor
 * takes a target until its zone is awake, and folds again when the zone settles. Folded, it still stands and can be
 * shot; damage to it wakes its zone like damage to any of the zone's units. Secondary to the flocks and walkers: a
 * few per zone, at the interior's doors and rooms where the site has one, else beside the route.
 *
 * <p>The gate is the emplacement's power ({@code TurretStateService.cutPower}): unpowered, a turret neither slews,
 * acquires nor fires.
 */
public final class DerelictPost {

    /** Posts each zone stands. UNTUNED. */
    public static final int PER_ZONE = 2;
    /** The emplacement a post is until the derelict post's own look and gun are picked. */
    public static final String STAND_IN_STRUCTURE = "structure.turret-vulcan";

    private DerelictPost() {}

    /** Whether a zone's posts are unfolded: only while it is awake. */
    public static boolean unfolded(SecurityZone zone) {
        return zone.state() == SecurityZone.State.AWAKE;
    }

    /**
     * Where a zone's posts are anchored, best first: the interior's entrances, then its chambers, that the zone keeps;
     * then the zone's own posts along the route, its node cell last. A post stands beside its anchor
     * ({@code DerelictSecurity.besideRoute}), never on it.
     */
    public static List<int[]> anchors(SecurityZone zone, List<int[]> routePosts, RuinsDelve delve) {
        List<int[]> out = new ArrayList<>();
        if (delve != null && delve.interior() != null) {
            for (RuinsDelve.Area area : delve.interior().entrances()) addInside(out, zone, area);
            for (RuinsDelve.Area area : delve.interior().chambers()) addInside(out, zone, area);
        }
        for (int i = routePosts.size() - 1; i >= 0; i--) out.add(routePosts.get(i));
        return out;
    }

    private static void addInside(List<int[]> out, SecurityZone zone, RuinsDelve.Area area) {
        if (zone.inside(area.centreX(), area.centreY())) out.add(new int[] {area.centreX(), area.centreY()});
    }
}
