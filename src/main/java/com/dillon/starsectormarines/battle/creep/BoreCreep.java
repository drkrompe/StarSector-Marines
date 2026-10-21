package com.dillon.starsectormarines.battle.creep;

import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.evacuation.SwarmBores;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creep spreads from living bores (`hive-creep.md`): each open bore is a creep
 * source at its mouth, with the growth of its tier, sped while biomass is
 * delivered into it, and removed when it collapses so its creep withers back.
 *
 * <p>Read from the bores rather than called by them, once per tick after the
 * bores have grown and collapsed: a bore opening is seen in
 * {@link SwarmBores#bores()}, a tier or a collapse in the append-only
 * {@link SwarmBores#events()}, and a delivery as the rise in its
 * {@link SwarmBore#biomassFed()}. The bores therefore know nothing of creep.
 * Source ids are the bore bodies' entity ids.
 */
public final class BoreCreep {

    /** Seconds of fed spreading one delivered body buys its bore's creep. */
    public static final float FEED_SECONDS_PER_BODY = 6f;

    private final Map<Long, Float> declared = new LinkedHashMap<>();
    private int eventCursor;

    /** Brings the creep field's sources in line with the bores. No-op, and no field, while no bore has opened. */
    public void sync(BattleSimulation sim) {
        SwarmBores bores = sim.swarmBores();
        List<SwarmBore> opened = bores.bores();
        if (opened.isEmpty()) return;
        CreepField field = sim.creepField();
        for (SwarmBore bore : opened) {
            if (bore.collapsed() || declared.containsKey(bore.entityId())) continue;
            field.putSource(bore.entityId(), bore.x(), bore.y(), growthFor(bore));
            declared.put(bore.entityId(), bore.biomassFed());
        }
        List<SwarmBores.Event> events = bores.events();
        for (; eventCursor < events.size(); eventCursor++) {
            SwarmBores.Event event = events.get(eventCursor);
            long id = event.bore().entityId();
            if (event.kind() == SwarmBores.EventKind.GREW) {
                if (declared.containsKey(id)) field.putSource(id, event.bore().x(), event.bore().y(), growthFor(event.bore()));
            } else if (event.kind() == SwarmBores.EventKind.COLLAPSED) {
                field.removeSource(id);
                declared.remove(id);
            }
        }
        for (Map.Entry<Long, Float> entry : declared.entrySet()) {
            SwarmBore bore = bores.byEntity(entry.getKey());
            if (bore == null) continue;
            float fed = bore.biomassFed() - entry.getValue();
            if (fed <= 0f) continue;
            field.feed(entry.getKey(), fed * FEED_SECONDS_PER_BODY);
            entry.setValue(bore.biomassFed());
        }
    }

    /** A bore's creep growth: its tier's preset. */
    static CreepGrowth growthFor(SwarmBore bore) {
        return CreepGrowth.forTier(bore.tier().ordinal());
    }
}
