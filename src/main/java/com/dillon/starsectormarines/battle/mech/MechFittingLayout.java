package com.dillon.starsectormarines.battle.mech;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Asset-authored fitting doll used by the Mech Lab.
 *
 * <p>The doll definition owns its maintenance facing and sockets. Physical
 * anchors, equipment-dock centers, and drop-target footprints are expressed in
 * chassis-width local coordinates: right is positive X and forward is positive
 * Y. The dock may sit away from its physical mount so the Mech Lab can use the
 * gantry around the doll as an interaction workspace. The Mech Lab may enforce
 * a minimum pointer target in UI pixels, but authored proportions remain
 * relative to the doll rather than to its room or camera.</p>
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
        OMNI("OMNI"),
        AMMO("AMMO"),
        UTILITY("UTILITY");

        private final String label;

        SocketType(String label) { this.label = label; }

        public String label() { return label; }
    }

    public record SocketDef(SocketId id, SocketType type, int capacity,
                            float anchorRight, float anchorForward,
                            float dockRight, float dockForward,
                            float footprintWidthHull, float footprintHeightHull,
                            boolean factoryLocked) {
        public SocketDef {
            if (id == null || type == null) {
                throw new IllegalArgumentException("socket id and type are required");
            }
            if (capacity < 1 || footprintWidthHull <= 0f || footprintHeightHull <= 0f) {
                throw new IllegalArgumentException("socket capacity and footprint must be positive");
            }
        }
    }

    public record DollDef(float facingDegrees, List<SocketDef> sockets) {
        public DollDef {
            if (!Float.isFinite(facingDegrees)) {
                throw new IllegalArgumentException("doll facing must be finite");
            }
            sockets = List.copyOf(sockets);
            if (sockets.isEmpty()) {
                throw new IllegalArgumentException("doll requires at least one socket");
            }
        }
    }

    private static final Map<MechVariant, MechFittingLayout> LAYOUTS = buildLayouts();

    private final MechVariant variant;
    private final DollDef doll;
    private final Map<SocketId, SocketDef> byId;

    private MechFittingLayout(MechVariant variant, DollDef doll) {
        this.variant = variant;
        this.doll = doll;
        EnumMap<SocketId, SocketDef> index = new EnumMap<>(SocketId.class);
        for (SocketDef socket : doll.sockets()) {
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

    public DollDef doll() { return doll; }

    public List<SocketDef> sockets() { return doll.sockets(); }

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
        layouts.put(MechVariant.BULWARK, layout(MechVariant.BULWARK, 180f, List.of(
                socket(SocketId.CORE, SocketType.CORE, 4,
                        0f, 0.03f, 0f, -1.50f, 1.70f, 0.68f, true),
                socket(SocketId.ARMS, SocketType.BALLISTIC, 4,
                        0f, 0.72f, 0f, 1.50f, 2.20f, 0.68f, true),
                socket(SocketId.LEFT_SHOULDER, SocketType.MISSILE, 3,
                        -0.72f, 0.08f, -1.38f, -0.48f, 1.40f, 0.72f, true),
                socket(SocketId.RIGHT_SHOULDER, SocketType.OMNI, 3,
                        0.72f, 0.08f, 1.38f, -0.48f, 1.40f, 0.72f, true),
                socket(SocketId.AMMO_RESERVE, SocketType.AMMO, 3,
                        0f, -0.58f, -1.38f, 0.72f, 1.70f, 0.68f, true),
                socket(SocketId.MINI_FAB, SocketType.UTILITY, 1,
                        0f, -0.92f, 1.38f, 0.72f, 1.40f, 0.68f, false))));
        layouts.put(MechVariant.HOUND, layout(MechVariant.HOUND, 180f, List.of(
                socket(SocketId.CORE, SocketType.CORE, 3,
                        0f, 0.02f, 0f, -1.45f, 1.70f, 0.68f, true),
                socket(SocketId.ARMS, SocketType.BALLISTIC, 2,
                        0f, 0.66f, 0f, 1.45f, 2.00f, 0.68f, true),
                socket(SocketId.LEFT_SHOULDER, SocketType.MISSILE, 1,
                        -0.62f, 0.04f, -1.34f, -0.46f, 1.36f, 0.72f, true),
                socket(SocketId.RIGHT_SHOULDER, SocketType.MISSILE, 1,
                        0.62f, 0.04f, 1.34f, -0.46f, 1.36f, 0.72f, false),
                socket(SocketId.AMMO_RESERVE, SocketType.AMMO, 1,
                        0f, -0.54f, -1.34f, 0.68f, 1.64f, 0.68f, true),
                socket(SocketId.MINI_FAB, SocketType.UTILITY, 1,
                        0f, -0.84f, 1.34f, 0.68f, 1.36f, 0.68f, false))));
        layouts.put(MechVariant.SIROCCO, layout(MechVariant.SIROCCO, 180f, List.of(
                socket(SocketId.CORE, SocketType.CORE, 3,
                        0f, 0.02f, 0f, -1.45f, 1.70f, 0.68f, true),
                socket(SocketId.ARMS, SocketType.BALLISTIC, 3,
                        0f, 0.72f, 0f, 1.45f, 2.08f, 0.68f, true),
                socket(SocketId.LEFT_SHOULDER, SocketType.MISSILE, 2,
                        -0.64f, 0.04f, -1.34f, -0.46f, 1.36f, 0.72f, true),
                socket(SocketId.RIGHT_SHOULDER, SocketType.MISSILE, 2,
                        0.64f, 0.04f, 1.34f, -0.46f, 1.36f, 0.72f, true),
                socket(SocketId.AMMO_RESERVE, SocketType.AMMO, 2,
                        0f, -0.55f, -1.34f, 0.68f, 1.64f, 0.68f, true),
                socket(SocketId.MINI_FAB, SocketType.UTILITY, 1,
                        0f, -0.86f, 1.34f, 0.68f, 1.36f, 0.68f, false))));
        return Map.copyOf(layouts);
    }

    private static MechFittingLayout layout(MechVariant variant, float facingDegrees,
                                            List<SocketDef> sockets) {
        return new MechFittingLayout(variant, new DollDef(facingDegrees, sockets));
    }

    private static SocketDef socket(SocketId id, SocketType type, int capacity,
                                    float anchorRight, float anchorForward,
                                    float dockRight, float dockForward,
                                    float widthHull, float heightHull,
                                    boolean factoryLocked) {
        return new SocketDef(id, type, capacity, anchorRight, anchorForward,
                dockRight, dockForward,
                widthHull, heightHull, factoryLocked);
    }
}
