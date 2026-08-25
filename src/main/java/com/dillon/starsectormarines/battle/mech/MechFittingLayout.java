package com.dillon.starsectormarines.battle.mech;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Asset-authored fitting silhouette used by the Mech Lab.
 *
 * <p>Socket centers are expressed in chassis-width local coordinates: right is
 * positive X and forward is positive Y. Footprints are expressed in battle-map
 * cells so the fitting overlay keeps the same physical relationship to the room
 * at every viewport and UI scale.</p>
 */
public final class MechFittingLayout {

    public enum SocketId {
        CORE("ENGINE CORE"),
        ARMS("ARM ASSEMBLY"),
        LEFT_SHOULDER("L. SHOULDER"),
        RIGHT_SHOULDER("R. SHOULDER"),
        AMMO_RESERVE("AMMO RESERVE"),
        MINI_FAB("MINI-FAB");

        private final String label;

        SocketId(String label) { this.label = label; }

        public String label() { return label; }
    }

    public enum SocketType {
        CORE("CORE"),
        BALLISTIC("BALLISTIC"),
        MISSILE("MISSILE"),
        AMMO("AMMO"),
        UTILITY("UTILITY");

        private final String label;

        SocketType(String label) { this.label = label; }

        public String label() { return label; }
    }

    public record SocketDef(SocketId id, SocketType type, int capacity,
                            float localRight, float localForward,
                            float footprintWidthCells, float footprintHeightCells,
                            boolean factoryLocked) {
        public SocketDef {
            if (id == null || type == null) {
                throw new IllegalArgumentException("socket id and type are required");
            }
            if (capacity < 1 || footprintWidthCells <= 0f || footprintHeightCells <= 0f) {
                throw new IllegalArgumentException("socket capacity and footprint must be positive");
            }
        }
    }

    private static final Map<MechVariant, MechFittingLayout> LAYOUTS = buildLayouts();

    private final MechVariant variant;
    private final List<SocketDef> sockets;
    private final Map<SocketId, SocketDef> byId;

    private MechFittingLayout(MechVariant variant, List<SocketDef> sockets) {
        this.variant = variant;
        this.sockets = List.copyOf(sockets);
        EnumMap<SocketId, SocketDef> index = new EnumMap<>(SocketId.class);
        for (SocketDef socket : sockets) {
            if (index.put(socket.id(), socket) != null) {
                throw new IllegalArgumentException("duplicate socket " + socket.id()
                        + " for " + variant);
            }
        }
        byId = Map.copyOf(index);
    }

    public static MechFittingLayout forVariant(MechVariant variant) {
        MechFittingLayout layout = LAYOUTS.get(variant);
        if (layout == null) throw new IllegalArgumentException("variant is required");
        return layout;
    }

    public MechVariant variant() { return variant; }

    public List<SocketDef> sockets() { return sockets; }

    public SocketDef socket(SocketId id) { return byId.get(id); }

    /** An authored empty socket is distinct from a socket omitted by the chassis. */
    public boolean occupied(SocketId id) {
        return switch (id) {
            case CORE, ARMS, AMMO_RESERVE, MINI_FAB -> true;
            case LEFT_SHOULDER -> variant.leftShoulder != null;
            case RIGHT_SHOULDER -> variant.rightShoulder != null;
        };
    }

    private static Map<MechVariant, MechFittingLayout> buildLayouts() {
        EnumMap<MechVariant, MechFittingLayout> layouts = new EnumMap<>(MechVariant.class);
        layouts.put(MechVariant.BULWARK, new MechFittingLayout(MechVariant.BULWARK, List.of(
                socket(SocketId.CORE, SocketType.CORE, 4, 0f, 0.03f, 0.80f, 0.80f, true),
                socket(SocketId.ARMS, SocketType.BALLISTIC, 4, 0f, 0.72f, 1.75f, 0.74f, true),
                socket(SocketId.LEFT_SHOULDER, SocketType.MISSILE, 3,
                        -0.72f, 0.08f, 0.72f, 1.15f, true),
                socket(SocketId.RIGHT_SHOULDER, SocketType.MISSILE, 3,
                        0.72f, 0.08f, 0.72f, 1.15f, true),
                socket(SocketId.AMMO_RESERVE, SocketType.AMMO, 3,
                        0f, -0.58f, 1.28f, 0.48f, true),
                socket(SocketId.MINI_FAB, SocketType.UTILITY, 1,
                        0f, -0.92f, 0.72f, 0.48f, false))));
        layouts.put(MechVariant.HOUND, new MechFittingLayout(MechVariant.HOUND, List.of(
                socket(SocketId.CORE, SocketType.CORE, 3, 0f, 0.02f, 0.68f, 0.68f, true),
                socket(SocketId.ARMS, SocketType.BALLISTIC, 2, 0f, 0.66f, 1.20f, 0.62f, true),
                socket(SocketId.LEFT_SHOULDER, SocketType.MISSILE, 1,
                        -0.62f, 0.04f, 0.62f, 0.86f, true),
                socket(SocketId.RIGHT_SHOULDER, SocketType.MISSILE, 1,
                        0.62f, 0.04f, 0.62f, 0.86f, false),
                socket(SocketId.AMMO_RESERVE, SocketType.AMMO, 1,
                        0f, -0.54f, 0.92f, 0.40f, true),
                socket(SocketId.MINI_FAB, SocketType.UTILITY, 1,
                        0f, -0.84f, 0.64f, 0.42f, false))));
        layouts.put(MechVariant.SIROCCO, new MechFittingLayout(MechVariant.SIROCCO, List.of(
                socket(SocketId.CORE, SocketType.CORE, 3, 0f, 0.02f, 0.70f, 0.70f, true),
                socket(SocketId.ARMS, SocketType.BALLISTIC, 3, 0f, 0.72f, 1.35f, 0.68f, true),
                socket(SocketId.LEFT_SHOULDER, SocketType.MISSILE, 2,
                        -0.64f, 0.04f, 0.66f, 0.96f, true),
                socket(SocketId.RIGHT_SHOULDER, SocketType.MISSILE, 2,
                        0.64f, 0.04f, 0.66f, 0.96f, true),
                socket(SocketId.AMMO_RESERVE, SocketType.AMMO, 2,
                        0f, -0.55f, 1.02f, 0.42f, true),
                socket(SocketId.MINI_FAB, SocketType.UTILITY, 1,
                        0f, -0.86f, 0.66f, 0.44f, false))));
        return Map.copyOf(layouts);
    }

    private static SocketDef socket(SocketId id, SocketType type, int capacity,
                                    float localRight, float localForward,
                                    float widthCells, float heightCells,
                                    boolean factoryLocked) {
        return new SocketDef(id, type, capacity, localRight, localForward,
                widthCells, heightCells, factoryLocked);
    }
}
