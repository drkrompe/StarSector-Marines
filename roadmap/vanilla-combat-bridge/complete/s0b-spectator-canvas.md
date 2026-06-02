# S0b — Spectator-canvas probe — ✅ SHIPPED

> **Verified in playtest.** Commit chain: `21bebd6` (initial) → `cec0580` (empty context
> fleet — superseded) → `819731b` (stash the real player fleet) → `beb3c2e` (restore
> *during* combat, not after).
>
> **Verdict:** all five canvas facts (8–12) compose cleanly. No deploy picker, free
> camera (WASD + RMB-drag + scroll), below-ships backdrop renders under the ships, UI
> overlay is the top visible layer with the HUD starved, and **F10 exits to the campaign
> with no game-over and the full player fleet restored** (Tab mid-battle does not
> re-arm/respawn the fleet).
>
> **Landed vs planned:** the spectator lever was *not* the in-combat APIs we expected
> (`setPlayerShipExternal` etc.) — it was the **player fleet itself**. `startBattle`
> sources deployable ships from the *real* player fleet (ignoring the context fleet), so
> the working approach is `PlayerFleetStash`: detach the real fleet's members for the
> battle, then **re-attach them ~0.5s into combat** (not on exit — restoring after
> combat-end makes the resolution read an empty fleet and declare a defeat/game-over).
> See the `startbattle_plugin_pick_deferred` memory.

---

> **Built, awaiting playtest.** Builds directly on S0. Proves that a launched vanilla
> combat instance can be reduced to a **blank, sim-driven canvas** — the host
> environment Direction A / the hybrid needs. Exercises verified facts 8–12
> (overview § round 2) in one probe. Throwaway dev scaffolding.

## Trigger / scale

**Ctrl+Shift+N** on the campaign map (vs Ctrl+Shift+B for the basic S0 battle).
Canvas is built around `S0BattleProbe.WORLD_UNITS_PER_CELL = 50` (overview size
gut-check) over the MEDIUM 144×80 grid → a 7200×4000-unit plate; the vanilla map is
padded to 1.5× that so the free camera can pan past the edges.

## Goal

Take the S0 battle and strip it down to a canvas we own:

1. **Spectator** — no player-controlled ship, zero command points, so vanilla's
   command/weapon/flux HUD has nothing to display (the only way to "draw over" the
   command widgets is to starve them — verified fact 12).
2. **Free camera** — WASD pan + RMB-drag pan + scroll zoom, via
   `ViewportAPI.setExternalControl(true)` then per-frame `setCenter` / `setViewMult`.
3. **Below-ships render layer** — a `CombatLayeredRenderingPlugin` drawing a flat
   backdrop quad under the ships (stand-in for the ground-battle plate).
4. **UI overlay** — a `renderInUICoords` pass drawing a screen-space marker (e.g. a
   corner panel) to confirm we own the top visible layer once the HUD is starved.
5. **No-dialog setup** — ships spawned directly via
   `engine.getFleetManager(side).spawnShipOrWing(...)` in `afterDefinitionLoad`, so
   the deploy dialog never appears.

## Why this exists

The five gating questions (free cam, draw-over-UI, skip setup UI, override input,
render behind ships) all came back feasible (overview facts 8–12). S0b is the
cheapest way to confirm they *compose* — that you can have all of them live at once
without them fighting each other — before committing to the real hybrid. The one
known hard limit (fact 12: can't overlay a populated command bar) is sidestepped by
the spectator approach, and this probe is where we confirm the starve-don't-cover
result actually looks clean.

## Scope

**In:**
- Reuse S0's launch + `BattleCreationContext` plumbing. Add a variant launch (or a
  flag on `S0BattleProbe`) that requests the spectator canvas.
- `S0BattleCreationPlugin` (or a sibling) spawns both sides via `spawnShipOrWing`
  instead of `addFleetMember`; sets player command points to 0; no player ship
  (`setPlayerShipExternal(null)` / never assign one).
- A single `EveryFrameCombatPlugin` (extend `S0CompletionPlugin` or a new one):
  - `processInputPreCoreControls` — consume WASD / RMB / scroll, drive the camera.
  - `advance` — keep `setExternalControl(true)`; keep the F10 end-combat from S0.
  - `renderInUICoords` — draw the corner overlay marker.
- A `CombatLayeredRenderingPlugin` on `BELOW_SHIPS_LAYER` drawing a backdrop quad,
  registered via `engine.addLayeredRenderingPlugin(...)`.

**Out:**
- Any real sim coupling — the ships are still throwaway vanilla hulls, the backdrop
  is a flat color/texture, not the actual `battle/` tile render.
- Proxies / damage drain (that's S2).
- Pretty art. This is a feel/compose check, not a renderer.

## Design notes

- **GL state.** `renderInUICoords` / layered render run against Starsector's polluted
  GL state — bracket per [[gl_state_gotchas]] and the mod's existing `GlStateBracket`
  / `render2d` batching ([[render2d_batching]]).
- **Camera detach.** Without a player ship the camera may default-follow something;
  `setExternalControl(true)` every frame is what keeps it ours. Verify the engine
  doesn't re-grab it on events (pause, objective capture).
- **Input ordering.** Consume in `processInputPreCoreControls` (pre-core) so WASD
  never leaks to a ship/command action. Spectator means there's no ship to drive
  anyway, but consume defensively.
- **Residual chrome.** Expect pause text + time-flow indicator to survive the starve
  (fact 12). Note where they land; lay the overlay out to avoid them.

## Dependencies

- S0 (landed) — launch path, arm-gated battle-creation plugin, completion plugin.
- None on `battle/`.

## Acceptance

Launch the spectator canvas from the campaign. Verify, and record verdicts in
`next-session.md`:

- [ ] No deploy dialog; battle starts with ships already on the field.
- [ ] HUD is effectively empty (no command bar / weapon groups / flux-hull); only
      residual chrome remains.
- [ ] WASD pans, RMB-drag pans, scroll zooms; camera stays ours across pause and
      combat events.
- [ ] Backdrop quad renders *under* the ships; ships and their FX draw on top.
- [ ] `renderInUICoords` overlay is the topmost visible layer (modulo residual
      chrome).
- [ ] F10 still ends combat on our terms (S0 carry-over).

A clean pass greenlights treating vanilla combat as a render/interaction host for
the ground sim (Direction A), and feeds the coordinate-mapping open question
(overview § Open questions #1).
