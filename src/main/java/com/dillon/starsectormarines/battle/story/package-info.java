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
