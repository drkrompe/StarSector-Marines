package com.dillon.starsectormarines.battle.mech;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The mechs on the field whose compartment brings a system of its own ({@link MachineSystems}: a sensor pair's drone
 * wing, active protection's point-defence mount), by entity id in the order they landed. The drone bays and the
 * point-defence service host these mechs exactly as they host a hull whose compartment carries the same part
 * (mechs-nouns.md, one machine, one part catalog), so a part does the same thing on every machine. A mech whose
 * compartment brings no system is not here.
 */
public final class MachineHosts {

    private final Map<Long, MachineSystems> hosts = new LinkedHashMap<>();

    /** Enrols mech {@code id} with what its compartment brings; ignored for none or a compartment with no system. */
    public void register(long id, MachineSystems systems) {
        if (id == 0L || systems == null) return;
        if (systems.droneBay().isEmpty() && !systems.activeProtection()) return;
        hosts.put(id, systems);
    }

    /** What mech {@code id}'s compartment brings, or {@link MachineSystems#NONE} for a mech not hosted here. */
    public MachineSystems systems(long id) {
        return hosts.getOrDefault(id, MachineSystems.NONE);
    }

    /** Every hosted mech in landing order; entries outlive the mech, so a reader checks it still stands. */
    public Collection<Map.Entry<Long, MachineSystems>> entries() {
        return hosts.entrySet();
    }
}
