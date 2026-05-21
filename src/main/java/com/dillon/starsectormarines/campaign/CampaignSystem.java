package com.dillon.starsectormarines.campaign;

import java.util.EnumSet;

/**
 * One tick-time behavior on the campaign-tier state. Systems are stateless —
 * all persistent data lives on {@link CampaignState}, all systems do is
 * compute one tick's worth of changes against it.
 *
 * <p>See <code>roadmap/campaign/architecture.md</code> §2 (Systems) and §3
 * (read/write declarations) for the design rationale. Short version:
 *
 * <ul>
 *   <li>One system per simulation phase. Promotion, relationship interactions,
 *       chain advancement, garrison defaults, discovery propagation are five
 *       separate System classes.</li>
 *   <li>Systems are reconstructed on every game load — they hold no
 *       persistent state. {@link CampaignStateScript} keeps them in a
 *       {@code transient} list.</li>
 *   <li>Every system declares its {@link #reads()} and {@link #writes()}
 *       table sets so a future scheduler can determine safe parallelism
 *       (currently the scheduler runs serially in registration order).</li>
 * </ul>
 */
public interface CampaignSystem {

    /** Display name for logging / debug intel. */
    String name();

    /** Tables this system reads. Required — empty means "this system is a no-op." */
    EnumSet<CampaignTable> reads();

    /** Tables this system writes. Empty = observation-only system. */
    EnumSet<CampaignTable> writes();

    /**
     * Run one tick of this system. The script guarantees this method is
     * invoked at most once per day-boundary crossing of the sector clock.
     *
     * @param state campaign-tier persistent state
     * @param day   current sector day (truncated from {@code SectorClock.getDay()})
     */
    void tick(CampaignState state, int day);
}
