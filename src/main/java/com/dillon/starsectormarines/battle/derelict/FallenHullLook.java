package com.dillon.starsectormarines.battle.derelict;

import java.util.Map;

/**
 * How a fallen hull's own structures are drawn (`fallen-hull-nouns.md`; art in {@code art-source/fallen-hull}): the
 * autoforge's housing, lit or put out, and a survey stake by its stage, the planted stake's folded pylon, the raised
 * stake's opened pylon round its node mast, the established stake's feedstock store at its service bay. Each is one
 * unrotated square sprite {@link Piece#cells} across. Presentation only: no rule reads it. The looks are the picks'
 * recommended options until the owner answers (`long-survey-arc.md`, picks {@code autoforge-look},
 * {@code stake-look}). The hull's walls, roof, trench and debris are map tiles, not structures.
 */
public final class FallenHullLook {

    private static final String ROOT = "graphics/battle/fallen-hull/";
    public static final Piece FORGE_LIT = new Piece(ROOT + "forge-lit.png", 6f);
    public static final Piece FORGE_OUT = new Piece(ROOT + "forge-out.png", 6f);
    public static final Piece STAKE_PLANTED = new Piece(ROOT + "stake-planted.png", 3.2f);
    public static final Piece STAKE_RAISED = new Piece(ROOT + "stake-raised.png", 4.8f);
    /** Drawn to its slot in the stake's footprint ({@link StakeFootprint}): 3 cells. */
    public static final Piece STAKE_STORE = new Piece(ROOT + "stake-store.png", 3f);
    /** The widest piece, cells, for culling. */
    public static final float MAX_CELLS = 6f;

    /** A sprite and how many cells across it is drawn. */
    public record Piece(String path, float cells) { }

    private FallenHullLook() { }

    /** The piece the structure {@code id} of {@code director}'s fallen hull is drawn as, or null when it is none. */
    public static Piece of(DerelictSecurityDirector director, long id) {
        FallenHull hull = director == null ? null : director.hull();
        if (hull == null) return null;
        return pick(id, hull.forgeId(), hull.forge().lit(), hull.pylons(), director.nodes(), director.serviceBays());
    }

    /**
     * The piece for structure {@code id}: the forge, lit or not; a planted pylon; a stake zone's node (raised); a stake
     * zone's service bay (the established stake's store). A zone below {@link FallenHull#STAKE_ZONE_BASE} is the hull's
     * own, drawn as the network draws it.
     */
    static Piece pick(long id, long forgeId, boolean lit, Map<Integer, Long> pylons, Map<Integer, Long> nodes,
                      Map<Integer, Long> bays) {
        if (id == 0L) return null;
        if (id == forgeId) return lit ? FORGE_LIT : FORGE_OUT;
        if (pylons.containsValue(id)) return STAKE_PLANTED;
        if (stakeZoneHolds(nodes, id)) return STAKE_RAISED;
        if (stakeZoneHolds(bays, id)) return STAKE_STORE;
        return null;
    }

    private static boolean stakeZoneHolds(Map<Integer, Long> byZone, long id) {
        for (Map.Entry<Integer, Long> e : byZone.entrySet()) {
            if (e.getKey() >= FallenHull.STAKE_ZONE_BASE && e.getValue() == id) return true;
        }
        return false;
    }
}
