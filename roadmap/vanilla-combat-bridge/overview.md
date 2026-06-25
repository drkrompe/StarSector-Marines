# Vanilla Combat Bridge

> Can our headless ground sim and Starsector's real-time combat engine talk to
> each other? Feasibility/exploration doc — concept + verified API facts +
> decomposition into de-risking probes. Nothing here is committed code yet.

## What this is

The mod's whole architecture so far runs the *other* way: we built a **separate
headless ground-battle sim** (`battle/`) that borrows vanilla *art* but touches
zero combat APIs. This track explores the reverse — **hooking our sim and the
vanilla `CombatEngineAPI` together**, in whichever direction turns out tractable.

The motivating community ask: *operate an Armatura-style walking mech (which is
a vanilla combat ship) while respecting wall collisions* — i.e. urban/interior
mech combat with terrain, keeping the vanilla piloting feel. That single ask
actually splits into two opposite bridges, and the realistic near-term win is a
**third** framing (proxy targets) that sidesteps the hardest part of both.

## The ask conflates two opposite bridges

The mech-ness and the walls live in different engines, and neither engine owns
both halves:

- **The mech behaviour** — leg-walk animation, weapon mounts, ship systems, the
  real-time *piloting feel* — lives entirely as **vanilla combat scripts** that
  only run inside `CombatEngineAPI`. We cannot run those headless.
- **The walls** — walkability, cover, tile collision — live entirely in **our
  headless tile sim** (`battle/`), which is decoupled from the combat engine by
  design.

So you can't get both for free. The directions:

- **Direction A — walls *into* vanilla combat.** Keep Armatura mechs intact; add
  terrain to the vanilla engine. Natural fit for the literal ask. Feasible as a
  *prototype* (see § Verified facts: positions are mutable), but the honest scope
  is a long tail — every vanilla mechanic that assumes open space (projectiles,
  beams, ship AI pathing, ship systems, every other mod's ships) ignores walls
  until individually patched. Never "done."
- **Direction B — Armatura mechs *into* our sim.** Keep walls; reimplement the
  mech as our units. We already ingest vanilla art + `.ship`/`.variant` stats
  ([[vanilla_ship_spec_scraping]]), and walls come free. But the *behaviour* is
  vanilla script we'd reimplement per mech, and our sim is an **auto-battler**,
  not a piloting sim — "operate a mech" implies real-time player control we don't
  have. Bigger, different product.

## The tractable third framing: proxy targets (the recommended entry)

Most of what people actually want from cross-engine interaction doesn't need
terrain collision *or* a full mech port. It needs **sim entities to be visible
and reactive in vanilla's targeting graph**: e.g. a carrier floating "above" the
ground battle launching fighters that strafe our sim's turrets and infantry
squads.

That's done with **proxy / avatar entities**. Each sim entity that must interact
with vanilla gets a lightweight, invisible vanilla `ShipAPI` proxy:

```
sim turret / squad  ──►  invisible ShipAPI proxy (owner = enemy side)
   each frame:  proxy.getLocation().set(simX, simY)   // sim owns position
                proxy.getVelocity().set(0, 0)         // vanilla never drifts it
   on damage:   drain proxy hitpoints delta → sim entity HP; despawn on death
   render:      proxy is invisible (extraAlphaMult = 0); our renderer draws it
```

To vanilla, a proxy is just an enemy ship — so **carrier AI, fighter launches,
and strafing runs work against it natively, with zero targeting AI written by
us.** This is the compelling near-term feature *and* the cheapest de-risk of the
whole bridge: it dodges the terrain long-tail entirely.

### Architecture decision: sim-authoritative, vanilla-targeting

- **The sim stays authoritative** for the ground entities' position and combat
  state. Proxies are slaved avatars, not independent ships — we re-`set()` their
  position every frame, so a stray hull bump or weapon impulse self-corrects.
- **Vanilla owns its own ships** (the carrier, its fighters, their weapons).
- **Pick one authority per interaction** to avoid double-resolving the same
  fight. For air-to-ground, the carrier side is vanilla-authoritative; the proxy
  just exposes a hittable hull + HP and drains damage back into the sim. For
  return fire, either mount a real vanilla weapon on the proxy and let the
  engine resolve it against the carrier's shields/armor/flux (cheap, looks
  great), *or* drive `engine.spawnProjectile(...)` from the sim's fire logic.

## Verified API facts (read from `.api/com/fs/starfarer/api/combat/`)

These are the load-bearing truths the whole track rests on — confirmed against
the unzipped API source, not recalled:

1. **No terrain in vanilla combat.** No walls, impassable geometry, or nav grid.
   Collision is purely entity-vs-entity (circle `collisionRadius` or polygon
   `BoundsAPI`). `getMapWidth/Height` are *soft* retreat boundaries. The only
   collidable static-ish objects are asteroids (real entities).
2. **Positions and velocities ARE mutable.** `CombatEntityAPI.getLocation()` /
   `getVelocity()` return the **live LWJGL `Vector2f`**, not copies.
   `entity.getLocation().set(x, y)` is the canonical teleport; `getVelocity()
   .set(...)` likewise. This is what makes Direction A's post-physics wall-clamp
   and the proxy position-slaving possible. *(A first exploration pass wrongly
   reported "no position authority — hard blocker." It's wrong; positions are
   writable. This fact flips the verdict.)*
3. **Full AI replacement.** `ship.setShipAI(ShipAIPlugin)` swaps a ship's entire
   movement brain — our grid pathfinder could drive a mech's thrust commands.
4. **Per-frame plugin hook.** `EveryFrameCombatPlugin.advance(amount, events)`
   runs once per frame (downstream of the frame's physics integration) and can
   draw arbitrary GL in world space via `renderInWorldCoords(viewport)`.
5. **Runtime entity injection.** `engine.getFleetManager(owner).spawnShipOrWing(
   specId, location, facing)` returns a live `ShipAPI` you can inject mid-combat.
6. **Sprite suppression.** `ship.setExtraAlphaMult(0f)` makes a proxy invisible;
   `setLayer(CombatEngineLayers)` controls draw layer. Invisible hitbox + our own
   renderer = clean visual ownership.
7. **Collision classes are coarse** (`NONE, FIGHTER, SHIP, ASTEROID, PLANET,
   GAS_CLOUD, STAR, …`). There is **no** "shootable but not hull-bumpable" class —
   to be hittable by weapons a proxy must be a collidable class (`SHIP`/`FIGHTER`),
   which also collides hull-to-hull. We rely on per-frame position-`set()` to make
   bumps self-correct rather than a special class.

## Verified API facts — round 2: vanilla combat as a sim canvas

Confirmed against the unzipped API while scoping how much of vanilla's combat shell
we can bend (the gating questions for turning a combat instance into a sim-driven
canvas). Citations are file:line in `.api/com/fs/starfarer/api/combat/`.

8. **Free camera is a first-class hook.** `ViewportAPI.setExternalControl(true)`
   tells the engine to stop setting viewport params each frame. *Playtest correction:*
   under external control **`setViewMult` is inert** — it does not recompute the
   visible rectangle, so a camera built on `setCenter` + `setViewMult` reads as fixed
   and zoomed-in. Own the rectangle explicitly with **`set(llx, lly, visibleWidth,
   visibleHeight)`** each frame, deriving height from the screen aspect
   (`Display.getHeight()/getWidth()`) and treating *visibleWidth in world units* as the
   zoom state (`ViewportAPI.java:43-53`). WASD via `Keyboard.isKeyDown`, RMB-drag via
   `InputEventAPI.getDX/getDY` (world delta = pixels × visibleWidth/screenWidth), scroll
   to change visibleWidth. Detach from the ship by running spectator (no player ship) or
   `CombatUIAPI.setDisablePlayerShipControlOneFrame(true)` each frame.
9. **Full input override.** `EveryFrameCombatPlugin.processInputPreCoreControls(
   amount, events)` runs *before* core controls; `event.consume()` swallows any
   event before vanilla sees it (`EveryFrameCombatPlugin.java:9`). Plus
   `setDisablePlayerShipControlOneFrame`. (Campaign side already proven via S0's
   `CampaignInputListener`.)
10. **Render below ships / under base FX.** `engine.addLayeredRenderingPlugin(
    CombatLayeredRenderingPlugin)` (`CombatEngineAPI.java:334`); the plugin's
    `getActiveLayers()` picks from the full stack `BELOW_PLANETS → PLANET_LAYER →
    CLOUD_LAYER → BELOW_SHIPS_LAYER → UNDER_SHIPS_LAYER → … → JUST_BELOW_WIDGETS`
    (`CombatEngineLayers.java`). Plus `setBackgroundColor` / `setRenderStarfield`.
11. **Skip the deploy/command dialog (workaround).** *Corrected by playtest:* for a
    battle launched via `CampaignUIAPI.startBattle`, the player's deployable reserves
    come from **`context.getPlayerFleet()`**, NOT from the `BattleCreationPlugin`'s
    `loader.addFleetMember` calls (those are for mission-mode). So the lever is the
    **context fleet**: pass an *empty* player fleet → nothing to deploy → no
    deployment picker and no "press Tab to deploy" prompt. Spawn the actual owner-0
    combatants directly in `afterDefinitionLoad` via
    `engine.getFleetManager(side).spawnShipOrWing(specId, loc, facing)`
    (`CombatFleetManagerAPI.java:39`). Variant ids must be real (`vigilance_Standard`,
    `tempest_Attack`, …) and validated — `spawnShipOrWing` resolves eagerly and throws
    on a bad id (whereas `createFleetMember` resolves lazily).
12. **You CANNOT draw over the top-level command widgets.** Combat exposes a single
    screen-space hook, `EveryFrameCombatPlugin.renderInUICoords` (no "above-UI"
    variant), and the layer stack caps at `JUST_BELOW_WIDGETS` — both render
    *beneath* the HUD. Contrast the campaign, which *does* expose above-UI tiers
    (`CampaignUIRenderingListener.renderInUICoordsAboveUIBelowTooltips` /
    `...AboveUIAndTooltips`). The asymmetry is deliberate: there is no documented
    combat hook above the widgets. **Workaround: starve the widgets, don't cover
    them** — run spectator + zero command points so the command UI / weapon groups /
    flux-hull readouts have nothing to display; then `renderInUICoords` is
    effectively the topmost visible layer. Residual chrome (pause text, time-flow
    indicator) still pokes through; lay content out to avoid those zones. A
    raw-GL-after-UI hack is rejected — no clean post-UI combat callback, and combat
    hands us a polluted GL state ([[gl_state_gotchas]]).

**Takeaway:** facts 8–12 together mean a vanilla combat instance can be reduced to a
near-blank, sim-driven canvas (spectator + free cam + below-ships layers + UI
overdraw + scripted no-dialog setup + total input control). The single hard limit is
fact 12 — but a sim takeover doesn't want a populated command bar anyway, so starving
it is the right move, not a compromise.

## Reality checks (do not skip)

- **Vanilla combat is strictly 2D — there is no Z / altitude.** A carrier
  "floating above" ground units is a *spatial convention*, not real elevation.
  Everything shares one plane: targeting (2D distance) is fine, but hull
  collision is real. Mitigations: keep carriers in an "airspace" band our sim
  treats as overhead (the *fighters* do the air-to-ground work, the carrier
  stays back), and rely on per-frame position-`set()` so any overlap resolves
  to no net movement of the sim-owned entity.
- **Pick one combat authority per interaction** (above) — the single biggest
  correctness trap is two engines independently resolving the same damage.
- **Granularity:** one proxy per *squad* (aggregated HP) / per *turret*, never
  per soldier. Vanilla handles dozens of ships / hundreds of fighters fine, so
  squad-granularity proxies are cheap; per-soldier would not be.
- **Neuter the proxy:** no-op `ShipAIPlugin` (or an engineless spec), zeroed
  velocity each frame, high mass so weapon impulse barely moves it before the
  clamp catches it.

## Why *not* run our sim as the authority under vanilla's renderer

A tempting framing is "vanilla just renders; our tick loop is the truth,
position-`set()`ing every ship each frame." Avoid it as the primary model: you'd
be perpetually correcting vanilla's integrator against ours (jitter, desync), and
the moment the player takes *direct* control of a ship the authority model
breaks. Direction A's clean version is a *single* post-physics constraint pass,
not a second full simulation underneath. Proxies are deliberately the *opposite*:
sim-authoritative entities that vanilla only *targets*, never *drives*.

## Code structure (proposed)

The bridge code is **Starsector-combat-engine-facing** — it imports
`CombatEngineAPI`, `EveryFrameCombatPlugin`, `ShipAPI`. That is exactly the
dependency the headless sim (`battle/`) deliberately does **not** have. To keep
the sim's zero-combat-API-coupling invariant intact, the bridge lives in its
**own top-level package, not inside `battle/`**:

```
com.dillon.starsectormarines.combathybrid
  package-info.java        — charter: bridges headless battle sim ⇄ vanilla
                             CombatEngineAPI; owns proxies + per-frame plugin;
                             one-way dependency on battle/ (reads sim, never the
                             reverse). (per [[feedback_package_info_charters]])
  WallClampPlugin          — Direction A probe (S1)
  ProxyTargetPlugin        — proxy-target probe (S2)
  proxy/                   — SimEntityProxy, the avatar pattern + lifecycle
```

Dependency arrow points **one way**: `combathybrid` → `battle` (reads sim state,
slaves proxies, drains damage back via the sim's existing external-damage path).
`battle/` must never import `combathybrid` — that would re-couple the sim to the
combat engine and undo the decoupling the whole project rests on.

## Candidate first stories

All are throwaway probes whose only job is to answer the load-bearing unknowns
cheaply, before any real feature investment ([[feedback_ship_then_optimize]]):

- **S0 — Battle bootstrap probe.** ✅ *shipped + playtested.* Launches a vanilla
  `CombatEngineAPI` battle from the campaign (Ctrl+Shift+B) with a chosen roster, and
  the mod owns completion (suppress auto-end; F10 → `endCombat`). Sealed in
  [`complete/s0-battle-bootstrap.md`](complete/s0-battle-bootstrap.md).
- **S0b — Spectator-canvas probe.** ✅ *shipped + playtested.* Reduces the launched
  combat to a blank, sim-driven host: no deploy picker, free camera, below-ships
  backdrop, starved HUD with our overlay on top, clean F10 exit with the player fleet
  restored (via `PlayerFleetStash`). Proves facts 8–12 compose. Sealed in
  [`complete/s0b-spectator-canvas.md`](complete/s0b-spectator-canvas.md). **Answer: yes
  — vanilla combat can host our sim.**
- **S1 — Wall-clamp probe.** ⏸ *shelved (2026-06).* Direction A (walls into the
  vanilla plane) is not the chosen direction — the product is fleet-above /
  ground-below with cross-interaction (the proxy framing). Walls stay in the headless
  sim. Kept as a documented technique only. See
  [`stories/s1-wall-clamp-probe.md`](stories/s1-wall-clamp-probe.md).
- **S2 — Proxy-target probe.** ✅ *shipped + playtested — works.* Ctrl+Shift+J: vanilla
  carrier/fighter AI strafes a sim-slaved invisible proxy with zero targeting code from
  us. The cross-engine bridge is validated. Sealed in
  [`complete/s2-proxy-target-probe.md`](complete/s2-proxy-target-probe.md).
- **S3 — inject the 2nd engine layer.** ✅ *shipped through S3b/S3e/S3f–S3j + a live Conquest
  battle under the fleet.* The headless `battle/` sim runs below the vanilla fleet fight: our
  renderer draws the real ground scene on the below-ships layer, defense-post proxies take
  air-to-ground strafes that drain sim HP, and the sim owns its kills. The two engines co-exist.
  **The realized product the bridge delivers is now specified in S3d — the drop-ship invasion**
  (a transport establishes orbit over a painted DZ; sim-native dropships descend and land
  marines; continuous, diegetic fleet-as-currency, scored hot/cold LZ). See
  [`stories/s3d-shuttle-scaledown.md`](stories/s3d-shuttle-scaledown.md).

Sequencing: ~~**S0 first**~~ ✅ done (S0 + S0b shipped — a campaign-launched combat
instance that hosts a sim-driven spectator canvas). Next: **S2** — the chosen direction
(fleet-above / ground-below with cross-interaction); it now has a proven combat host to
spawn proxies into. ~~S1~~ shelved — Direction A (walls in the vanilla plane) is not the
product direction; walls stay in the headless sim.

## Open questions

1. **Coordinate mapping.** Sim cells ↔ vanilla world pixels. The sim has no
   inherent scale (cells are abstract); we pick a pixels-per-cell and an origin.
   Which band of the vanilla map is "airspace" vs "ground"?
2. **Damage drain path.** Reuse the sim's existing external-damage entry point
   (the flyby/command-powers work pokes `BattleSimulation` via an
   `applyExternalDamage`-style call) rather than inventing a new one. Confirm the
   exact method when S2 starts.
3. **Projectiles & beams through walls (Direction A only).** Out of scope for the
   probes; this is the long tail that decides if Direction A is ever more than a
   toy.
4. **Player-piloted mech vs sim authority.** If the player ever *directly* pilots
   a bridged mech, who owns its position? The proxy model assumes sim authority;
   direct piloting breaks it. Likely a separate, later design.
5. **CR / attrition coupling.** Does a proxy's destruction feed back to the
   campaign fleet the way [`../command-powers/`](../command-powers/overview.md)
   counterplay does? Natural tie-in, not needed for the probes.

## How this directory is laid out

- **`overview.md`** (this file) — concept, verified facts, architecture, probes.
- **`architecture.md`** — post-S2 north star: the event-adapter coupling decision,
  proxy/targetability model, spatial fork, and the S3a–d decomposition. **Read this
  before building any S3 story.**
- **`stories/`** — `s3d-shuttle-scaledown` (**the drop-ship invasion — full vision + D1–D5 ladder, next build**),
  `s3c-airspace-gating` + `skybattle-fleet-control` (parked), `s3f`–`s3j` render layers (resolved);
  `s3a`/`s3b` shipped, `s1-wall-clamp-probe` shelved.
- **`complete/`** — sealed shipped work (`s0-battle-bootstrap`, `s0b-spectator-canvas`, `s2-proxy-target-probe`,
  `s3a-sim-coupling-slice`, `s3b-cityscape-backdrop`, `s3e-build-map-host-seam`).
- **`next-session.md`** — handoff state; S0/S0b/S2/S3a/S3b/S3e shipped, the X1–X4b extraction done, render
  layers resolved. Next: **S3d D1** — the drop-ship invasion core (sim-dropship spawn from the orbiting carrier).
