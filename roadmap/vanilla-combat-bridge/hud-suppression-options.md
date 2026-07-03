# Vanilla combat HUD suppression — options (fresh look)

> The bridge hosts our ground sim inside a real `CombatEngineAPI` instance. Vanilla's
> combat chrome (its HUD widgets, pause text, time-flow indicator, kill feed) renders on
> top of / around our canvas and reads as distracting — it belongs to a game we aren't
> playing here. This doc re-surveys what actually renders, what levers exist, and picks a
> direction. Supersedes the scattered "starve, don't cover / lay content around the
> residuals" notes in `overview.md` fact 12 + `complete/s0b-spectator-canvas.md`.

## The hard constraint (why there's no clean "HUD off")

- **No public off-switch.** `CombatEngineAPI.isUIShowingHUD()` is **read-only**
  (`.api/.../CombatEngineAPI.java:249`). There is no `setShowHUD(false)`. `CombatUIAPI`
  exposes only piecemeal getters + `hideShipInfo()` (`CombatUIAPI.java:43`) — no master
  toggle.
- **No above-HUD draw hook.** Combat's only screen-space hook is
  `EveryFrameCombatPlugin.renderInUICoords`, which draws *beneath* the widgets; the layer
  stack caps at `JUST_BELOW_WIDGETS` (overview fact 12). So we cannot **cover** the top
  chrome with an opaque panel — only starve it.
- **Reflection is sandboxed out.** The standard modding hack — reflect into Starsector's
  obfuscated combat-UI internals and null/disable the HUD renderer — is blocked here. This
  project's script sandbox throws `SecurityException: File access and reflection are not
  allowed to scripts` (`setAccessible` on obfuscated fields is exactly what it denies); the
  codebase uses no reflection. See [[starsector_script_sandbox]]. **Treat the reflection
  off-switch as unavailable**, not as a spike worth running.

Net: full removal via a single API call does not exist. Everything below is about
**owning the states that make chrome render** so it has nothing to draw.

## What actually renders today (and the lever for each)

Measured against the current spectator setup: `setPlayerShipExternal(null)`, both fleets
0 command points, no reserves (ships spawned directly, not deployed), no objectives added.

| Element | Where | Status now | Lever |
|---|---|---|---|
| **Ship-info widget (hull/flux/CR/weapon groups/system) + "press [X] to switch ships" prompt** | bottom-left | **Renders** — this is the actual complaint. `setPlayerShipExternal(null)` clears the *flagship* but the UI still shows the empty ship panel + offers ship control | `getCombatUI().hideShipInfo()` **each frame** (now wired in `SpectatorCanvasPlugin.advance`, 2026-07-02) — sandbox-safe. **Switch-prompt caveat below.** |
| Command UI / CP counter / "Tab to deploy" | top-left | Starved (0 CP, no reserves) | already handled |
| Objectives bar (nav buoy / comm relay) | top center | **Absent** — we own the `MissionDefinitionAPI`, add none | n/a |
| Radar / minimap | — | **Absent** — vanilla combat has none (that's the Combat Radar *mod*; `br_radar`/`showCampaignRadar` are campaign-only) | n/a |
| **Pause text** ("PAUSED") | center | **Renders only while paused** | own the pause state (`setPaused(false)`) |
| **Time-flow indicator** (speed-up glyph) | bottom | **Renders only when time-mult ≠ 1** — player speed-up *or* engine auto-fast-advance | pin `getTimeMult()` to 1× (player); auto-advance is edge-case (see below) |
| Kill-feed messages ("X destroyed") | top-left | Fires when a **vanilla** entity dies (fighters, lost carriers, **dying proxies**) | **no public suppress** — the one residual with no clean lever |

The key correction to the old docs: **pause text and the time indicator are
state-triggered, not always-on.** They aren't chrome we must lay out *around* — they're
chrome that only draws in engine states we can control.

### The primary target: the bottom-left ship-info widget (the actual complaint)

The player pilots nothing by design in these RTS battles, so the bottom-left ship panel
(hull/flux/CR/weapon groups) **and its "press [X] to switch ships" prompt** are pure noise.
We were never calling the lever for it. `CombatUIAPI.hideShipInfo()` (`CombatUIAPI.java:43`)
is a no-arg, one-frame hide — its sibling `reFanOutShipInfo()` means the UI re-asserts the
panel each tick, so it must be **called every frame**. Wired into
`SpectatorCanvasPlugin.advance()` alongside `setDisablePlayerShipControlOneFrame(true)`
(2026-07-02).

**Switch-prompt caveat (needs playtest):** `hideShipInfo()` clearly hides the *panel*.
Whether it also suppresses the **"press [X] to switch ships"** prompt is unverified — the
prompt exists because the player *owns controllable ships on the field* (the owner-0
carriers) with no active flagship. If it survives `hideShipInfo()`, the backup levers are
per-ship: `ShipAPI.setControlsLocked(true)` (`ShipAPI.java:167`) and/or
`setSelectableInWarroom(false)`-style flags (`isSelectableInWarroom()`, `ShipAPI.java:464`)
on every owner-0 ship each frame, so the engine has nothing the player can switch *to*.
Confirm which is needed under Ctrl+Shift+K before adding the heavier per-ship pass.

## The options

### A. Lock the time state (recommended)
`hideShipInfo()` each frame + `engine.setPaused(false)` + pin `getTimeMult()` to 1× in the
spectator plugin. Pause text and the time indicator then have no state to render in.
- **Pro:** erases exactly the two residuals the docs called permanent; zero sandbox risk;
  small (a few lines in `SpectatorCanvasPlugin`).
- **Con:** the player loses vanilla's pause / fast-forward. For an *own-the-canvas* ground
  mode that's arguably correct — if we want pause/speed later, we build our **own** clean
  controls (our overlay owns the time mult; no vanilla glyph), consistent with the mod's
  "own the canvas" stance.
- **Caveat (be honest):** pinning `timeMult` handles *player-initiated* speed-up. The
  engine's **auto-fast-advance** (`isInFastTimeAdvance()`, triggered when no enemies are in
  sensor contact) is a separate state with no public setter. In practice it only kicks in
  at the tail (all targetable proxies dead → vanilla sees nothing to fight), and keeping a
  live enemy proxy in contact holds `isFleetsInContact()` true and suppresses it. So its
  indicator is an **end-of-battle edge case**, not steady-state — verify in playtest.

### B. Starve only, keep time controls
Add `hideShipInfo()` but leave pause/fast-forward intact; accept their indicators when the
player deliberately pauses/speeds. Nothing removed passively.
- **Pro:** keeps familiar time controls; no behavior trade-off.
- **Con:** doesn't actually remove the two things flagged as distracting — it just accepts
  them as "only when you asked for it."

### C. Accept + design around (status quo)
Keep laying the overlay/content out to dodge the pause/time zones (overview fact 12's
current stance).
- **Pro:** zero work.
- **Con:** doesn't address the complaint; the chrome still reads as "another game's UI."

### D. ~~Reflection off-switch~~ — excluded
Blocked by the sandbox (above). Not a viable path in this environment.

## Recommendation

**Primary (done): hide the ship-info widget** — `hideShipInfo()` each frame. This is the
element the player actually flagged; the call was simply missing. Playtest whether the
"press [X] to switch ships" prompt goes with it or needs the per-ship backup lever above.

**Secondary (optional, Option A time-lock):** pause-text + time indicator are a separate,
lower-priority thread — pursue only if they turn out distracting in practice, and only after
the pause/fast-forward trade-off decision. Not bundled with the ship-info fix.

The kill-feed messages (dying proxies/fighters) remain the single un-leverable residual —
worth a playtest read on how noisy it actually is before deciding whether it's worth heavier
measures (e.g., aggregating proxy deaths, or a below-widget opaque strip over the message
zone since the feed sits *low* enough that some of it may be coverable — verify the exact band).

## Open decision (for the user)

Time controls vs a fully clean view: **do we lock time to 1× (kills pause-text + speed
indicator, but the player can't pause/fast-forward vanilla), or keep vanilla's time controls
and accept their indicators?** If we lock, a follow-up is *our own* time controls surfaced
through the ground-command overlay. This is the fork the implementation waits on.

## Pointers
- `overview.md` fact 12 — the "starve, don't cover" limit this refines.
- `complete/s0b-spectator-canvas.md` — where "residual chrome (pause + time) survives the
  starve" was first recorded (throwaway probe; the live Ctrl+Shift+K battle adds the
  kill-feed residual).
- `stories/ground-control-mode.md` — the player-facing mode where clean chrome matters most
  and where our own time controls would live.
- `combathybrid/host/SpectatorCanvasPlugin` — the plugin that would host the `hideShipInfo`
  + time-lock (its `advance()` already owns the per-frame camera/`setDisablePlayerShipControlOneFrame`).
