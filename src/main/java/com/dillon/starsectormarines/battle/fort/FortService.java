package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.battery.FortressBattery;
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

    /** Records one of the fort's wall nests. */
    public void tagNest(int fortId, long nestId) {
        fort(fortId).addNest(nestId);
    }

    public List<Fort> forts() {
        return view;
    }

    public boolean isEmpty() {
        return forts.isEmpty();
    }
}
