# Combat bridge — production architecture (post-S3, pre-render-layers extraction)

> Decision record + target code structure for graduating the `combathybrid` spike from
> `@DebugOnly` probe scaffolding into a durable, mission-triggerable ground-battle host.
> Written when the render-layer thread (S3f–S3j) was paused after S3f: rather than pour
> more layer wiring into the throwaway classes, we extract the durable core first so the
> remaining layers land in code we keep. **Read after `architecture.md`** (this builds on
> the event-translated coupling decision, it doesn't replace it).

## Decision (2026-06)

The fleet-above / ground-below bridge is a **committed product mode**, not just an
exploration. So: **extract the durable core now, then resume render layers in it.** The
durable adapters (`GroundSceneBackdrop`, `GroundSimBridge`) are already close — the throwaway
part is the *launcher / mode-global / hardcoded rosters*, not the coupling itself.

This does **not** demote `BattleScreen`: the standalone full-canvas takeover stays the mature
render path. Both hosts already share the sim layer (S3e: `BattleSetup.buildMap` +
`BattleSimulation.advance(dt)` + `AirProvider`). This track makes *vanilla combat* a clean
second host for that same sim — "build a `BattleSimulation`, then choose a host."

## Three concerns the spike tangles

1. **Dev trigger** — `S0BattleProbe` (static `mode` global + hotkey rosters),
   `CombatHybridInputListener`, `CombatHybridCampaignPlugin` (tag-armed plugin pick).
2. **Vanilla session lifecycle** — spectator camera, HUD starve, mod-owned completion,
   player-fleet stash/restore (`SpectatorCanvasPlugin`, `S0CompletionPlugin`,
   `PlayerFleetStash`), plus the `setupSimCoupled` wiring blob in the creation plugin.
3. **The sim⇄vanilla bridge** (the durable core) — `GroundSceneBackdrop` (render sink) +
   `GroundSimBridge` (proxy mirror: damage-in via `applyExternalDamage`, death-out via
   `subscribeDeath`, position-slaving).

The `mode` enum + the 4-way branch in `S0BattleCreationPlugin.afterDefinitionLoad` is the
spike multiplexing one plugin across four probes. Production has **one** path (a sim-coupled
ground battle) parameterized by a config object — the branch dies.

## Durable vs throwaway

| Class | Verdict |
|---|---|
| `GroundSceneBackdrop` | **Durable** — render-layer set comes from config, not a hardcoded `SCENE_LAYERS` constant. |
| `GroundSimBridge` → `SimProxyMirror` | **Durable core** — strip per-frame `LOG.info` + the amber crosshair markers (the S3f UNITS layer is the real unit visual); `SIM_DAMAGE_SCALE` becomes a config field. |
| `subscribeDeath` / `applyExternalDamage` | already durable (in `battle/`). |
| `PlayerFleetStash`, `SpectatorCanvasPlugin`, `S0CompletionPlugin`, `NeverEndObjective` | **Durable-ish** — lifecycle / camera / completion *policy*; folded behind the session. |
| `S0BattleCreationPlugin` | **Split** — thin `BattleCreationPlugin` (durable) + `setupSimCoupled` wiring (→ session/config) + mode branch (dies). |
| `S0BattleProbe`, `CombatHybridInputListener`, `CombatHybridCampaignPlugin` | **Throwaway** dev trigger — stays `@DebugOnly`; production trigger is the mission flow. |
| `CanvasBackdropRenderer`, `ProxyTargetPlugin` | **Deletable** — superseded by `GroundSceneBackdrop` / `GroundSimBridge` (only S0b/S2 probe modes still reach them). |

## Target shape

```
combathybrid/
  bridge/   (durable, host-agnostic — "given a BattleSimulation, mirror it into a combat engine")
    GroundSceneBackdrop      render sink; configurable EnumSet<RenderLayer>
    SimProxyMirror           proxy + damage/death coupling (was GroundSimBridge, debug stripped)
    GroundBattleConfig       sim + grid + worldUnitsPerCell + sceneLayers + targetable + AirProvider + damageScale + proxyVariant
  host/     (durable — vanilla combat session lifecycle)
    CombatBridgeSession      installs the bridge + policy plugins, owns launch->run->restore
    CombatBridgeCreationPlugin   thin BattleCreationPlugin reading the config
    SpectatorCamera / CompletionPolicy / PlayerFleetStash
  probe/    (@DebugOnly — dev trigger only; deletable once mission flow lands)
    S0BattleProbe, CombatHybridInputListener, CombatHybridCampaignPlugin
```

Dependency arrow stays one-way and slots into existing seams:

**campaign mission → `TargetProfile` → `BattleSetup.buildMap` (S3e) → `GroundBattleConfig` →
`CombatBridgeSession.launch()` → vanilla `startBattle` → thin creation plugin installs the
`bridge/` + `host/` plugins.** `battle/` never learns the bridge exists (invariant 1 of
`architecture.md` holds).

## Extraction slices (each build-clean + independently committable)

- **X1 — `GroundBattleConfig` + configurable render layers.** Introduce the config; the
  backdrop reads its `EnumSet<RenderLayer>` from it (kills the hardcoded `SCENE_LAYERS`), the
  mirror reads `damageScale`/`proxyVariant` from it. `setupSimCoupled` builds the config. **This
  is what unblocks S3g–S3j landing as config edits, not new constants per class.**
- **X2 — debug strip + rename `GroundSimBridge` → `SimProxyMirror`.** Drop per-frame logging
  and the crosshair markers (UNITS covers it); IntelliJ `rename_refactoring`.
- **X3 — `CombatBridgeSession` host object.** Extract the lifecycle/wiring (the `setupSimCoupled`
  blob + plugin install + completion/spectator/stash) behind one object the creation plugin
  delegates to. Mode branch collapses to "config present → session.install(engine)".
- **X4 — package reorg** `bridge/` + `host/` + `probe/` (git-mv, package-infos per
  `feedback_package_info_charters`); delete `CanvasBackdropRenderer` + `ProxyTargetPlugin` if
  the S0b/S2 probe modes are retired, else leave isolated.

Then **resume S3g–S3j** against the durable `GroundSceneBackdrop` (now config-driven).

## Still open / not in this extraction

- **S3c (airspace banding / ship-AI gating)** is still the load-bearing *viability* unknown
  for the whole bridge. Extraction makes the code durable; it does not prove the spatial model.
  Sequence S3c independently — don't let render polish or this refactor stand in for it.
- **Mission-flow trigger.** X1–X4 leave the probe (`Ctrl+Shift+K`) as the only launcher. Wiring
  `CombatBridgeSession.launch()` into the real contract/mission-resolver flow is a later story
  (it's what finally retires `probe/`).
