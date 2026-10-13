package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.power.HostileShipSupport;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

import java.util.List;

/**
 * Which ship, if any, the defending world fields. The single rule for a campaign mission:
 * the largest eligible hull of a <em>free</em> fleet of the defending faction in the target's
 * system. Eligibility is the company's own ({@link ShipSupportSource#of}: a cruiser or capital with
 * a ground-reaching gun), applied by the live adapter before a hull reaches here.
 *
 * <p>Everything else (debug, scenario, employer-scripted missions) declares its ship and skips the
 * search ({@link #resolve}); the battle gets a {@link ShipSupportSource} either way.
 */
public final class HostileShipSelection {

    private HostileShipSelection() {}

    /** One defending-faction fleet as the selection sees it. */
    public interface Fleet {
        String name();
        HostileFleetStatus status();
        List<Candidate> ships();
    }

    /** An eligible hull in a fleet, already frozen. {@code weight} breaks ties within a size. */
    public record Candidate(ShipSupportSource source, String hullName, HullSize size, float weight) { }

    /**
     * The declaration if there is one; otherwise, for a campaign mission, the search; otherwise
     * (a non-campaign mission that declares nothing) no ship.
     */
    public static HostileShipReport resolve(ShipSupportSource declared, boolean campaign,
                                            TargetProfile profile, List<? extends Fleet> fleets) {
        if (declared != null) return HostileShipReport.declared(declared);
        if (!campaign) return HostileShipReport.UNDEFENDED;
        return choose(profile, fleets);
    }

    /** The campaign search. The defence gate stays: a world below it cannot call for a ship. */
    public static HostileShipReport choose(TargetProfile profile, List<? extends Fleet> fleets) {
        if (!HostileShipSupport.warrants(profile)) return HostileShipReport.UNDEFENDED;
        Fleet freeFleet = null;
        Candidate free = null;
        Fleet heldFleet = null;
        Candidate held = null;
        if (fleets != null) {
            for (Fleet fleet : fleets) {
                if (fleet == null || fleet.ships() == null) continue;
                for (Candidate c : fleet.ships()) {
                    if (c == null || c.source() == null || c.source().tender()) continue;
                    if (fleet.status().free()) {
                        if (free == null || larger(c, free)) { free = c; freeFleet = fleet; }
                    } else if (held == null || larger(c, held)) { held = c; heldFleet = fleet; }
                }
            }
        }
        if (free != null) {
            ShipSupportSource s = free.source();
            return new HostileShipReport(HostileShipReport.Outcome.COMING, s, s.shipName(), free.hullName(),
                    freeFleet.name(), null);
        }
        if (held != null) {
            return new HostileShipReport(HostileShipReport.Outcome.HELD, null, held.source().shipName(),
                    held.hullName(), heldFleet.name(), heldFleet.status());
        }
        return HostileShipReport.ABSENT;
    }

    private static int rank(HullSize size) {
        return size == HullSize.CAPITAL_SHIP ? 2 : size == HullSize.CRUISER ? 1 : 0;
    }

    private static boolean larger(Candidate a, Candidate b) {
        int ra = rank(a.size());
        int rb = rank(b.size());
        return ra != rb ? ra > rb : a.weight() > b.weight();
    }
}
