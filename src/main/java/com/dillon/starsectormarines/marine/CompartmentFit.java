package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.marine.MechCompartment.Placement;
import com.dillon.starsectormarines.marine.MechCompartment.Spot;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * What a machine's auxiliary compartment holds and where each part sits, the same record on a mech and a vehicle
 * (mechs-nouns.md, one machine, one part catalog). Each part keeps its place: taking one out moves nothing else, and a
 * part goes in the bay it is dropped on, at the first cell there that takes it. A place that no longer holds (a part
 * from a save from before places were kept, or bays that changed) is found again by {@link MechCompartment#pack}, and
 * kept from then on.
 *
 * <p>The caller has checked a part goes ({@link MechCompartment#fits}); this only decides where.</p>
 */
final class CompartmentFit implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The parts by {@link MechInternal#id}, in the order fitted. */
    private List<String> partIds = new ArrayList<>();
    /** Where each part sits, lined up with {@link #partIds}: {@code GRID:column:row}, or null where not yet placed. */
    private List<String> spots = new ArrayList<>();

    private Object readResolve() {
        if (partIds == null) partIds = new ArrayList<>();
        if (spots == null || spots.size() != partIds.size()) {
            spots = new ArrayList<>();
            for (int i = 0; i < partIds.size(); i++) spots.add(null);
        }
        return this;
    }

    /** A compartment holding {@code ids}, in order, none of them placed yet: the input of a save from before places. */
    static CompartmentFit of(List<String> ids) {
        CompartmentFit fit = new CompartmentFit();
        if (ids != null) for (String id : ids) fit.addUnplaced(id);
        return fit;
    }

    /** The parts, in the order fitted; an id that names no compartment part is skipped. */
    List<MechInternal> parts() {
        List<MechInternal> parts = new ArrayList<>();
        for (String id : partIds) {
            MechInternal part = partOf(id);
            if (part != null) parts.add(part);
        }
        return List.copyOf(parts);
    }

    /** The ids, in order: migration input, read only. */
    List<String> ids() { return List.copyOf(partIds); }

    /** Each part where it sits in {@code grids}, in fitted order; null when they do not all fit. */
    List<Placement> placements(List<SocketDef> grids) {
        List<MechInternal> parts = new ArrayList<>();
        List<Spot> wanted = new ArrayList<>();
        for (int i = 0; i < partIds.size(); i++) {
            MechInternal part = partOf(partIds.get(i));
            if (part == null) continue;
            parts.add(part);
            wanted.add(decode(spots.get(i)));
        }
        return MechCompartment.arrange(grids, parts, wanted);
    }

    /**
     * Fits {@code part} at {@code at} (a cell or a bay; null for anywhere), a one-of role (a bank, a loader) taking the
     * place of the part of that role, which comes out. The rest stay where they are where they can.
     */
    void add(List<SocketDef> grids, MechInternal part, Spot at) {
        if (part == null) return;
        List<Placement> placed = placements(grids);
        if (placed == null) {
            if (MechCompartment.limit(part.role) == 1) removeUnplacedRole(part.role);
            addUnplaced(part.id);
            return;
        }
        List<MechInternal> parts = new ArrayList<>();
        List<Spot> wanted = new ArrayList<>();
        for (Placement p : placed) {
            if (MechCompartment.limit(part.role) == 1 && p.part().role == part.role) continue;
            parts.add(p.part());
            wanted.add(p.spot());
        }
        parts.add(part);
        wanted.add(at);
        List<Placement> arranged = MechCompartment.arrange(grids, parts, wanted);
        if (arranged == null) {
            partIds.clear();
            spots.clear();
            for (MechInternal p : parts) addUnplaced(p.id);
            return;
        }
        record(arranged);
    }

    /**
     * Takes out one {@code part}: the one at {@code from} (where it was lifted; the grid alone will do), else the first
     * of it fitted. Nothing else moves. False when none is fitted.
     */
    boolean remove(List<SocketDef> grids, MechInternal part, Spot from) {
        if (part == null) return false;
        List<Placement> placed = placements(grids);
        if (placed == null) {
            int index = partIds.indexOf(part.id);
            if (index < 0) return false;
            partIds.remove(index);
            spots.remove(index);
            return true;
        }
        int index = -1;
        for (int i = 0; i < placed.size() && index < 0; i++) {
            Placement p = placed.get(i);
            if (p.part() == part && from != null && p.grid() == from.grid()
                    && (!from.exact() || p.column() == from.column() && p.row() == from.row())) index = i;
        }
        for (int i = 0; i < placed.size() && index < 0; i++) if (placed.get(i).part() == part) index = i;
        if (index < 0) return false;
        List<Placement> kept = new ArrayList<>(placed);
        kept.remove(index);
        record(kept);
        return true;
    }

    /** Takes out every part of {@code role}; the rest stay where they are. */
    void removeRole(List<SocketDef> grids, MechInternal.Role role) {
        List<Placement> placed = placements(grids);
        if (placed == null) {
            removeUnplacedRole(role);
            return;
        }
        List<Placement> kept = new ArrayList<>();
        for (Placement p : placed) if (p.part().role != role) kept.add(p);
        record(kept);
    }

    void clear() {
        partIds.clear();
        spots.clear();
    }

    /** Puts {@code id} last with no place yet: migration only, and a compartment that no longer packs. */
    void addUnplaced(String id) {
        if (id == null) return;
        partIds.add(id);
        spots.add(null);
    }

    /** Takes out the part at {@code index} in fitted order, places and all: migration only. */
    void removeAt(int index) {
        partIds.remove(index);
        spots.remove(index);
    }

    /** Writes down where every part now sits, so a compartment read from an old save keeps the places it shows. */
    void settle(List<SocketDef> grids) {
        List<Placement> placed = placements(grids);
        if (placed != null) record(placed);
    }

    private void removeUnplacedRole(MechInternal.Role role) {
        for (int i = partIds.size() - 1; i >= 0; i--) {
            MechInternal part = partOf(partIds.get(i));
            if (part == null || part.role == role) removeAt(i);
        }
    }

    private void record(List<Placement> placed) {
        partIds.clear();
        spots.clear();
        for (Placement p : placed) {
            partIds.add(p.part().id);
            spots.add(encode(p.spot()));
        }
    }

    private static MechInternal partOf(String id) {
        MechInternal part = MechInternal.findById(id);
        return part != null && part.role.compartment() ? part : null;
    }

    static String encode(Spot spot) {
        return spot == null ? null : spot.grid().name() + ":" + spot.column() + ":" + spot.row();
    }

    static Spot decode(String text) {
        if (text == null) return null;
        String[] parts = text.split(":");
        if (parts.length != 3) return null;
        try {
            return new Spot(SocketId.valueOf(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
