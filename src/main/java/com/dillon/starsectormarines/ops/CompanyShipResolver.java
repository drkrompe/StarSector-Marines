package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;
import com.dillon.starsectormarines.battle.world.gen.ship.HullRole;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a ship in the player's fleet as the facts a deck is generated from.
 *
 * <p>The bridge from a fleet the player assembled to a {@link CompanyShip}, so
 * that any hull bought in the ordinary way can become the company's home
 * without anybody authoring rooms for it. Everything here is read; nothing is
 * modelled. The base game already knows a hull's class, what it is for, how
 * many hands it needs and holds, and how big it is drawn, and reimplementing
 * any of that is the kind of thing that stays subtly wrong for a year.
 *
 * <p><b>Form is read whole, and damage is read separately.</b> A ship's
 * effective numbers fold in refits and battle damage alike, and generating from
 * them would rebuild the ship around its own injuries: a hull that came home
 * with Compromised Storage would come back with a smaller hold designed in,
 * and the player would see a tidy small room where they should see their own
 * bad afternoon. So the reading is taken from the ship as she would be with her
 * d-mods lifted, which the game will compute for us — the variant cloned, its
 * d-mods removed, and a throwaway member built from the clone. Refits still
 * count, because they are deliberate acts by the owner and the deck should come
 * out different. See {@code company-ship.md}.
 *
 * <p>Only the resolution is here. What makes a hull a candidate, and which one
 * the company lives aboard, are campaign decisions recorded elsewhere.
 */
public final class CompanyShipResolver {

    private static final Logger LOG = Global.getLogger(CompanyShipResolver.class);

    /**
     * Beam over length for a hull whose spec could not be read.
     *
     * <p>The median of every cruiser and capital in the base game, measured
     * rather than chosen, so an unreadable hull generates a deck of ordinary
     * proportions instead of a corridor or a square.
     */
    private static final float TYPICAL_ASPECT = 0.88f;

    /** Base hull id to beam-over-length, scraped once per hull. */
    private static final Map<String, Float> ASPECT_BY_HULL = new HashMap<>();

    private CompanyShipResolver() { }

    /**
     * The company-ship facts for one member of a fleet, or null for a member
     * with no hull to read.
     */
    public static CompanyShip read(FleetMemberAPI member) {
        if (member == null) return null;
        ShipHullSpecAPI hull = member.getHullSpec();
        if (hull == null) return null;
        FleetMemberAPI whole = undamaged(member);
        return shipOf(
                hull.getHullSize() == null ? null : hull.getHullSize().name(),
                hull.getDesignation(),
                whole.getMinCrew(), whole.getMaxCrew(), whole.getCargoCapacity(),
                aspectOf(hull.getBaseHullId()));
    }

    /**
     * Assemble the facts, having read them from wherever they come from.
     *
     * <p>Separated from the reading so the arithmetic is exercised without a
     * running game. Crew and hold arrive as effective floats and a deck wants
     * whole people and whole cargo, and a hull whose minimum reads above its
     * maximum — which stacked crew-raising mods can produce — is taken at its
     * maximum rather than rejected, since a ship the player owns has to
     * generate something.
     */
    static CompanyShip shipOf(String hullSize, String designation,
                              float minCrew, float maxCrew, float cargo, float aspect) {
        int holds = Math.max(0, Math.round(cargo));
        int carries = Math.max(0, Math.round(maxCrew));
        int works = Math.min(carries, Math.max(0, Math.round(minCrew)));
        return new CompanyShip(
                HullClass.fromHullSize(hullSize),
                HullRole.fromDesignation(designation),
                works, carries, holds,
                aspect > 0f ? aspect : TYPICAL_ASPECT);
    }

    /**
     * The same ship with her battle damage lifted, for reading her form off.
     *
     * <p>Falls back to the member as she stands when the clone cannot be built,
     * which reads a damaged ship as a smaller one — wrong, but wrong in the
     * direction of still producing a deck.
     */
    private static FleetMemberAPI undamaged(FleetMemberAPI member) {
        ShipVariantAPI variant = member.getVariant();
        if (variant == null) return member;
        List<String> injuries = damage(variant);
        if (injuries.isEmpty()) return member;
        try {
            ShipVariantAPI whole = variant.clone();
            for (String dmod : injuries) DModManager.removeDMod(whole, dmod);
            FleetMemberAPI restored =
                    Global.getFactory().createFleetMember(FleetMemberType.SHIP, whole);
            return restored == null ? member : restored;
        } catch (Exception cannotRestore) {
            LOG.warn("CompanyShipResolver: could not read " + member.getHullId()
                    + " undamaged (" + cannotRestore.getClass().getSimpleName() + ": "
                    + cannotRestore.getMessage() + "); reading her as she stands");
            return member;
        }
    }

    /** Every d-mod on a variant, by id. */
    private static List<String> damage(ShipVariantAPI variant) {
        List<String> injuries = new ArrayList<>();
        for (String mod : variant.getHullMods()) {
            if (DModManager.getMod(mod).hasTag(Tags.HULLMOD_DMOD)) injuries.add(mod);
        }
        return injuries;
    }

    /**
     * A hull's beam over its length, from the drawn hull rather than the stats.
     *
     * <p>Nothing on the fleet member reports proportions, but every hull's
     * {@code .ship} spec carries the dimensions it is drawn at, and vanilla art
     * is bow-up — so height is her length and width her beam. Loaded through
     * the settings API, which follows the game's own mod load order, so a
     * modded hull is read the same way a base one is.
     */
    private static float aspectOf(String baseHullId) {
        if (baseHullId == null || baseHullId.isEmpty()) return TYPICAL_ASPECT;
        Float cached = ASPECT_BY_HULL.get(baseHullId);
        if (cached != null) return cached;
        float resolved = scrapeAspect(baseHullId);
        ASPECT_BY_HULL.put(baseHullId, resolved);
        return resolved;
    }

    private static float scrapeAspect(String baseHullId) {
        String path = "data/hulls/" + baseHullId + ".ship";
        try {
            JSONObject spec = Global.getSettings().loadJSON(path);
            float beam = (float) spec.optDouble("width", 0.0);
            float length = (float) spec.optDouble("height", 0.0);
            if (beam <= 0f || length <= 0f) {
                LOG.warn("CompanyShipResolver: " + baseHullId + " (" + path + ") — "
                        + "missing width/height; using typical proportions");
                return TYPICAL_ASPECT;
            }
            return beam / length;
        } catch (Exception unreadable) {
            LOG.warn("CompanyShipResolver: " + baseHullId + " (" + path + ") — "
                    + unreadable.getClass().getSimpleName() + ": " + unreadable.getMessage());
            return TYPICAL_ASPECT;
        }
    }
}
