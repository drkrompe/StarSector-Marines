/**
 * Confirmed mission facts for future dialogue, subtitles and audio.
 *
 * <p>After each host advance, read
 * {@code sim.getPlayerStoryBeatsAfter(lastSequence)}. Enqueue or deliberately
 * discard each returned event, then retain its sequence. Each host owns its
 * cursor; reset it to zero when the battle instance changes. The read is
 * non-destructive and survives several simulation ticks per render frame.
 * Neutral evidence uses {@code sim.getStoryBeats()}, which includes private
 * events and must not be passed directly to player radio.
 *
 * <p>Opening keys start with {@code opening.relief.} or
 * {@code opening.counterattack.}. Common suffixes: {@code started},
 * {@code player_landed}, {@code employer_landed}, {@code victory},
 * {@code defeat}, {@code retreat}. Relief adds {@code checkpoint_reached},
 * {@code militia_line_weakened} (half or less of the original line, still alive),
 * {@code militia_line_lost}, {@code freight_wave_arrived} and
 * {@code service_wave_arrived}. Counterattack adds {@code gate_reached},
 * {@code yard_reached}, {@code warehouse_reached}, {@code reserve_committed}
 * and {@code defenders_falling_back}. Wave and defender-decision events are
 * observer-only. Reached means a player combatant within six cells, not capture.
 *
 * <p>Junction keys use {@code supply_route.junction.} with the common arrival
 * and terminal suffixes, {@code militia_hold_established}, and the private
 * {@code reserve_committed}. Each of {@code turnout}, {@code weigh_station},
 * {@code freight_yard}, {@code dispatch} can report {@code _retaken}; the three
 * initially hostile sites report {@code _secured}. These mean completed
 * territory transitions, not proximity. Retaken fires once per site per attempt.
 * The hold beat requires a live ally under the accepted hold directive inside
 * the secured capture room. These hooks never settle the campaign chapter.
 *
 * <p>Last Lift uses {@code supply_route.last_lift.}: {@code start},
 * {@code crew_contacted}, {@code crew_released}, {@code crossing_cleared},
 * {@code first_boarding}, {@code last_boarding}, {@code cohort_resolved},
 * {@code escort_hold}, and the three terminal keys. Last boarding requires an
 * actual new boarding on the cohort's resolution tick; if the last person dies,
 * only cohort resolution fires. Abandonment is not cohort resolution. The two
 * {@code road_arrival_1/2} beats are observer-only. These observations do not
 * drive road timing, which reads physical progress independently.
 *
 * <p>To add a beat, give it a stable key, consume a confirmed post-tick fact in
 * the mission policy, classify its audience and exercise the policy directly.
 * Do not infer an earlier stage from a later one, or expose an enemy decision
 * merely because the mission knows it. Consumer content should also use the
 * enclosing mission identity: a recurring job shares the scenario keys but
 * is not a replay of Wayline's history.
 *
 * <p>Audio generation, provider credentials, clip lookup and playback belong
 * outside this package. A future audio adapter must coordinate with the shared
 * battle-radio voice budget and handle stale or superseded lines. The log
 * retains facts; it does not mean every old fact should be spoken immediately.
 */
package com.dillon.starsectormarines.battle.story;
