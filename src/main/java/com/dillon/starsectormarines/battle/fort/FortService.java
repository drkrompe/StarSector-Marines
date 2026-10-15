package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.battery.FortressBattery;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortRoom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * State owner for a battle's forts: which exist and what each is made of. Parts reach it as they spawn, tagged with
 * the fort they belong to ({@link FortTag}); {@link FortSystem} advances it.
 */
public final class FortService {

    private final Map<Integer, Fort> byId = new HashMap<>();
    private final Map<Long, Fort> airMountFort = new HashMap<>();
    private final List<Fort> forts = new ArrayList<>();
    private final List<Fort> view = Collections.unmodifiableList(forts);
    private boolean spotting = true;

    /** Whether a fort's radar spots for its holder's air ({@link FortSensors}); on unless a scene turns it off for a control. */
    public boolean spotting() {
        return spotting;
    }

    /** The comparison control, per battle: with spotting off the forts keep their radar but call nothing in. */
    public void setSpotting(boolean spotting) {
        this.spotting = spotting;
    }

    private Fort fort(int id) {
        return byId.computeIfAbsent(id, key -> {
            Fort fort = new Fort(key, FortKind.BATTERY_FORT);
            forts.add(fort);
            return fort;
        });
    }

    /** Records that the battery platform {@code battery} is the guns of fort {@code fortId}. */
    public void tagPlatform(int fortId, FortressBattery battery) {
        Fort fort = fort(fortId);
        fort.become(FortKind.BATTERY_FORT);
        fort.attach(battery);
    }

    /** Records that structure {@code generatorId}, held by {@code holder}, is the shield generator of fort {@code fortId}. */
    public void tagEmitter(int fortId, long generatorId, Faction holder) {
        Fort fort = fort(fortId);
        fort.become(FortKind.SHIELD_FORT);
        fort.addPlace(new Fort.Place(FortAspect.EMITTER, generatorId, holder));
    }

    /** Records that structure {@code coreId}, held by {@code holder}, is the core of one of the fort's aspects. */
    public void tagAspect(int fortId, FortAspect aspect, long coreId, Faction holder) {
        fort(fortId).addPlace(new Fort.Place(aspect, coreId, holder));
    }

    /** As {@link #tagAspect(int, FortAspect, long, Faction)}, also recording the core's structure, which names its art. */
    public void tagAspect(int fortId, FortAspect aspect, long coreId, Faction holder, String structureId) {
        Fort.Place place = new Fort.Place(aspect, coreId, holder);
        place.structureId(structureId);
        fort(fortId).addPlace(place);
    }

    /** Records where fort {@code fortId} stands, from any of its tagged parts. */
    public void tagFrame(int fortId, FortTag tag) {
        if (tag.placed()) fort(fortId).place(tag.originX(), tag.originY(), tag.quarterTurns());
    }

    /** Records one of the anti-air mounts standing in the fort's yard. */
    public void tagAirMount(int fortId, long mountId) {
        Fort fort = fort(fortId);
        fort.addAirMount(mountId);
        airMountFort.put(mountId, fort);
    }

    /** The fort whose yard mount {@code id} is, or {@code null} for a gun that is no fort's anti-air. */
    public Fort airMountFort(long id) {
        return airMountFort.get(id);
    }

    /** The fort whose bunker core is {@code coreId} (where its radar stands), or {@code null}. */
    public Fort sensorFort(long coreId) {
        for (Fort fort : forts) {
            Fort.Place bunker = FortSensors.bunker(fort);
            if (bunker != null && bunker.coreId() == coreId) return fort;
        }
        return null;
    }

    /** Records one of the fort's wall nests. */
    public void tagNest(int fortId, long nestId) {
        fort(fortId).addNest(nestId);
    }

    /**
     * Whether a fort's own roof art covers this cell, so the ordinary roof must not be drawn over it. Only buildings
     * whose core names building art are covered; the answer is fixed once the fort is stamped.
     */
    public boolean roofArtCovers(int x, int y, TurretCatalogRegistry catalog) {
        for (Fort fort : forts) {
            FortFrame frame = fort.frame();
            if (frame == null) continue;
            for (Fort.Place place : fort.places()) {
                if (place.structureId() == null) continue;
                StructureDef def = catalog.getStructure(place.structureId());
                if (def == null || def.buildingArt == null) continue;
                int[] b = place.floorBounds(frame);
                if (x >= b[0] && x <= b[2] && y >= b[1] && y <= b[3]) return true;
                for (FortRoom room : def.buildingArt.annexRooms(frame.plan(), place.aspect())) {
                    int[] f = room.floor();
                    int[] a = frame.bounds(f[0], f[1], f[2], f[3]);
                    if (x >= a[0] && x <= a[2] && y >= a[1] && y <= a[3]) return true;
                }
            }
        }
        return false;
    }

    public List<Fort> forts() {
        return view;
    }

    public boolean isEmpty() {
        return forts.isEmpty();
    }
}
