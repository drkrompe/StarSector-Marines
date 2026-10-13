package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.battery.FortressBattery;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;

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
    private final List<Fort> forts = new ArrayList<>();
    private final List<Fort> view = Collections.unmodifiableList(forts);

    private Fort fort(int id) {
        return byId.computeIfAbsent(id, key -> {
            Fort fort = new Fort(key, FortKind.BATTERY_FORT);
            forts.add(fort);
            return fort;
        });
    }

    /** Records that the battery platform {@code battery} is the guns of fort {@code fortId}. */
    public void tagPlatform(int fortId, FortressBattery battery) {
        fort(fortId).attach(battery);
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
        if (tag.placed()) fort(fortId).frame(new FortFrame(tag.originX(), tag.originY(), tag.quarterTurns()));
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
