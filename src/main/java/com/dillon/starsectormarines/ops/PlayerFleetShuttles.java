package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Inspects the player's current fleet and returns the typed list of marine
 * transports they can field. Each entry corresponds to one ship in their fleet
 * whose hull id matches a recognized {@link ShuttleType}. Atlas, Prometheus,
 * Hermes, Shepherd, etc. all return nothing — we deliberately whitelist real
 * troop-carrying hulls instead of the broader {@code isCivilian()} predicate
 * we used before, so the gate has real teeth.
 *
 * <p>Ordered by tier (best transports first) so when the briefing only consumes
 * part of the list the player's best assets are picked first — a player with a
 * Valkyrie + a Buffalo for a 2-transport requirement gets Valkyrie + Buffalo,
 * not "two random ones."
 */
public final class PlayerFleetShuttles {

    private static final Logger LOG = Global.getLogger(PlayerFleetShuttles.class);

    /**
     * Priority order — best transports first. The manifest prefers high-capacity
     * dedicated transports (Valkyrie, Nebula) when cycling is needed. Aeroshuttle
     * stays at the back since it's the employer's default; the player's actual
     * hulls take precedence visually.
     */
    private static final ShuttleType[] PRIORITY = {
            ShuttleType.VALKYRIE,
            ShuttleType.NEBULA,
            ShuttleType.BUFFALO,
            ShuttleType.MULE,
            ShuttleType.TARSUS,
            ShuttleType.WAYFARER,
            ShuttleType.MUDSKIPPER,
            ShuttleType.SHEPHERD,
            ShuttleType.KITE,
            ShuttleType.HERMES,
            ShuttleType.AEROSHUTTLE,
    };

    private PlayerFleetShuttles() {}

    /**
     * Returns the player's available transports, sorted best-first via
     * {@link #PRIORITY}. Returns an empty list outside campaign or when the
     * fleet has no matching hulls.
     */
    public static List<ShuttleType> queryAvailable() {
        if (Global.getSector() == null) return Collections.emptyList();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || fleet.getFleetData() == null) return Collections.emptyList();
        List<ShuttleType> out = new ArrayList<>();
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member == null || member.getHullSpec() == null) continue;
            // Use base hull id so the (D) and other suffixed variants
            // (e.g. buffalo_d, mudskipper_d) match the same way the pristine
            // hull does — getHullId returns the variant-specific id and would
            // miss them.
            String baseId = member.getHullSpec().getBaseHullId();
            ShuttleType t = ShuttleType.forHullId(baseId);
            LOG.info("PlayerFleetShuttles: scan " + member.getShipName()
                    + " baseHullId=" + baseId
                    + " → " + (t != null ? t.name() : "(not a transport)"));
            if (t != null) out.add(t);
        }
        // Dev seed — pretend the player fields N Valkyries so the commitment UI
        // + gate + manifest all exercise the real path without a curated fleet.
        // See DevConfig.DEBUG_SEED_PLAYER_VALKYRIES.
        for (int i = 0; i < com.dillon.starsectormarines.DevConfig.DEBUG_SEED_PLAYER_VALKYRIES; i++) {
            out.add(ShuttleType.VALKYRIE);
        }
        out.sort((a, b) -> Integer.compare(priorityIndex(a), priorityIndex(b)));
        return out;
    }

    /** Total number of player transports — convenience for the gating check. */
    public static int countAvailable() {
        return queryAvailable().size();
    }

    private static int priorityIndex(ShuttleType t) {
        for (int i = 0; i < PRIORITY.length; i++) if (PRIORITY[i] == t) return i;
        return PRIORITY.length;
    }
}
