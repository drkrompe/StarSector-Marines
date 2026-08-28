package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;
import com.dillon.starsectormarines.battle.world.gen.ship.HullOutline;
import com.dillon.starsectormarines.battle.world.gen.ship.HullRole;
import com.dillon.starsectormarines.battle.world.gen.ship.HullSilhouette;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import org.apache.log4j.Logger;
import org.json.JSONArray;
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

    /**
     * Base hull id to its outline, scraped once per hull. Holds nulls for hulls
     * whose spec could not be read, so an unreadable hull is not re-read on
     * every comparison.
     */
    private static final Map<String, HullSilhouette> OUTLINE_BY_HULL = new HashMap<>();

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
                outlineOf(hull.getBaseHullId()), hull.getSpriteName());
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
                              float minCrew, float maxCrew, float cargo,
                              HullSilhouette outline) {
        return shipOf(hullSize, designation, minCrew, maxCrew, cargo, outline, null);
    }

    /** @see #shipOf(String, String, float, float, float, HullSilhouette) */
    static CompanyShip shipOf(String hullSize, String designation,
                              float minCrew, float maxCrew, float cargo,
                              HullSilhouette outline, String art) {
        int holds = Math.max(0, Math.round(cargo));
        int carries = Math.max(0, Math.round(maxCrew));
        int works = Math.min(carries, Math.max(0, Math.round(minCrew)));
        HullClass hullClass = HullClass.fromHullSize(hullSize);
        HullRole role = HullRole.fromDesignation(designation);
        return outline != null
                ? new CompanyShip(hullClass, role, works, carries, holds, outline, art)
                : new CompanyShip(hullClass, role, works, carries, holds,
                        TYPICAL_ASPECT, null, art);
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
     * A hull's own outline, or null for one whose spec could not be read.
     *
     * <p>Nothing on the fleet member describes a hull's form, but every
     * {@code .ship} spec carries the collision polygon the game itself uses.
     * Loaded through the settings API, which follows the game's mod load order,
     * so a modded hull's deck comes out the shape of that hull without anybody
     * preparing it.
     *
     * <p>Keyed on the base hull id so a skin and the hull it is a skin of share
     * one reading: a (D) variant is the same shape as the ship it was made
     * from, and only its condition differs.
     */
    private static HullSilhouette outlineOf(String baseHullId) {
        if (baseHullId == null || baseHullId.isEmpty()) return null;
        if (OUTLINE_BY_HULL.containsKey(baseHullId)) return OUTLINE_BY_HULL.get(baseHullId);
        HullSilhouette outline = scrapeOutline(baseHullId);
        OUTLINE_BY_HULL.put(baseHullId, outline);
        return outline;
    }

    private static HullSilhouette scrapeOutline(String baseHullId) {
        String path = "data/hulls/" + baseHullId + ".ship";
        try {
            JSONObject spec = Global.getSettings().loadJSON(path);
            JSONArray bounds = spec.optJSONArray("bounds");
            if (bounds == null || bounds.length() < 6) {
                LOG.warn("CompanyShipResolver: " + baseHullId + " (" + path + ") — "
                        + "no usable bounds; her deck takes a synthetic taper");
                return null;
            }
            float[] polygon = new float[bounds.length()];
            for (int corner = 0; corner < polygon.length; corner++) {
                polygon[corner] = (float) bounds.getDouble(corner);
            }
            return HullOutline.fromBounds(polygon, baseHullId);
        } catch (Exception unreadable) {
            LOG.warn("CompanyShipResolver: " + baseHullId + " (" + path + ") — "
                    + unreadable.getClass().getSimpleName() + ": " + unreadable.getMessage());
            return null;
        }
    }
}
