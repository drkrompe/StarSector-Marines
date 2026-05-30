package com.dillon.starsectormarines.battle.power;

/**
 * The S1 skeleton power: a recon sweep that lifts fog in a radius around the
 * targeted cell for a short window, then fades. Chosen because it touches only
 * the fog-of-war system (nothing combat-critical), so the
 * invoke&rarr;target&rarr;resolve&rarr;cooldown loop can be proven end-to-end
 * before any damage/AI plumbing.
 *
 * <p>{@link #resolve} registers an {@link CommandPowerService.ActivePing} on the
 * service; the view layer ({@code ops.BattleScreen.advance}) projects active
 * pings into the fog each frame as ephemeral vision sources, exactly as it does
 * for shuttles. The ping's time-to-live is aged down by
 * {@link CommandPowerSystem}; when it expires the reveal lapses and the fog
 * returns.
 */
public final class ReconPing extends CommandPower {

    public static final String ID = "recon_ping";

    /** Placeholder S1 tuning — balance is S5's job. */
    private static final float CP_COST = 2f;
    private static final float COOLDOWN_SECONDS = 8f;
    private static final int REVEAL_RADIUS_CELLS = 8;
    private static final float REVEAL_SECONDS = 15f;

    public ReconPing() {
        super(ID, "Recon Ping", CP_COST, COOLDOWN_SECONDS);
    }

    @Override
    public float previewRadiusCells() {
        return REVEAL_RADIUS_CELLS;
    }

    @Override
    public void resolve(int cellX, int cellY, CommandPowerService service) {
        service.addActivePing(cellX, cellY, REVEAL_RADIUS_CELLS, REVEAL_SECONDS);
    }
}
