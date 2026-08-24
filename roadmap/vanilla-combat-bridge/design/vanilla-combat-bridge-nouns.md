# Vanilla Combat Bridge

Status: ACTIVE — a sim-authoritative ground layer runs inside a host-owned vanilla combat session.

Written: 2026-08-23

Updated: 2026-08-24 — replaced debug and story chronology with durable production-launch and extension boundaries.

## Vocabulary

- The **combat bridge** is the adapter boundary that lets one ground `BattleSimulation` coexist with a real vanilla `CombatEngineAPI` fight. It is neither a terrain system for vanilla combat nor a replacement renderer for the ground sim.
- The **ground simulation** owns ground entities, their position, health, combat, objectives, and completion. It remains headless and has no dependency on the combat adapter.
- The **vanilla host** owns the combat-engine session: fleet ships, fighters, their AI, the combat camera, and vanilla combat lifecycle policy.
- A **proxy** is an invisible vanilla combat entity representing a targetable ground structure. It is a targeting avatar and damage sensor, not a second ground unit or an authoritative health bar.
- The **targetable tier** is the intentionally limited set of sim entities that receive proxies. Defensive structures are direct vanilla targets; individual infantry are never directly proxied and remain an area-damage/ground-simulation concern.
- A **presentation sink** renders the ground scene through the combat world's camera beneath vanilla ships. It presents simulation state; it does not create an alternate ground authority.
- A **ground band** is the common two-dimensional region where the ground map is projected into vanilla combat. It is a spatial convention, not altitude: fleet and ground share one plane even when the fiction reads as "above" and "below."
- A **bridge session** is one configured handoff from a built simulation to a vanilla host. It owns spectator policy, camera/HUD suppression policy, and installation of the proxy and presentation adapters; scenario content supplies the actual ships and sim.
- **Internal air** means the simulation's own shuttles and flybys continue to run. This is the current bridge contract: vanilla carriers add pressure above a self-contained ground battle rather than replacing its air layer.
- A **drop invasion** is the bridge's fleet-to-ground commitment: a carrier establishes orbit over a player-designated zone, then launches sim-native dropship waves. Fleet marine capacity supplies depth, transport capacity and cadence supply throughput, and AA plus local ground threat make the choice hot or cold.

## Ownership and flow

A scenario builds one normal ground simulation, chooses targetable defensive structures, and gives the bridge session snapshotted adapter inputs plus the intentionally live simulation. The session starts a spectator-style vanilla combat host, frames the projected ground band, and installs two complementary adapters.

The presentation sink draws ground terrain, structures, units, objectives, vehicles, shuttles, fog, and camera-projected combat effects in the combat world beneath ships. The ground renderer is shared with the standalone host; changing the camera/projection chooses the sink rather than forking render behavior. Screen-space accumulators remain separate because they are not automatically meaningful in the combat-world projection.

The proxy mirror maps each targetable live sim structure to one invisible vanilla proxy at the same projected location. Vanilla weapons damage the proxy. The adapter forwards the observed damage delta into the simulation, resets proxy hitpoints so vanilla does not own ground health, advances the sim, then removes a proxy when the simulation reports its corresponding entity dead. Ground entities continue to use their normal simulation lifecycle and visual presentation.

The live bridge currently runs a real Conquest battle below the fleet, including ordinary defenders, objectives, reinforcement, vision, and internally owned air. Vanilla carriers and fighters are additive air-to-ground pressure against the targetable structure tier; they do not directly replace the sim's shuttles or turn infantry into vanilla ships.

The spectator host provides an externally controlled camera, consumes player-ship controls, and starves normal deployment and command widgets. It also hides the ship-info panel through the supported combat UI API. Pause/time indicators and the kill feed remain host-policy constraints rather than surfaces the bridge can cover from above.

For an invasion, the player selects a drop zone in the host. A carrier commits to orbit there, a scored scatter process chooses actual landing cells, and successive sim-native dropships deliver the available marine pool while the carrier remains alive and on station. The transport's loss forfeits its undeployed waves; extraction is deliberately a separate inverse capability.

## Standing laws

1. `combathybrid` depends on `battle`; `battle` never imports or otherwise learns about the combat bridge. All version-fragile combat API contact remains in the adapter/host boundary.
2. The simulation owns ground state. Vanilla proxies are expendable targeting surfaces, not mirrored health, position, or lifecycle authorities.
3. Translate events, do not synchronize mutable state. The shared vocabulary is projected position, incoming damage, and simulation-owned removal—not a per-frame two-engine state copy.
4. Proxy granularity follows targetability. Directly targetable structures may have proxies; individual infantry never acquire one merely to make vanilla lock-on convenient.
5. The ground band is a 2D convention. Never imply actual altitude, terrain collision, or a universal fleet-wall interaction that vanilla combat cannot provide.
6. The host may own camera, input, and combat-session policy, but it must not silently become a second ground-command or ground-completion authority.
7. Internal versus external air is an explicit host contract. The present bridge uses internal sim air plus additive vanilla pressure; a future external-air host must be introduced as a distinct authority decision, not by partially disabling one side.
8. The player commits a drop through fleet logistics, not abstract command points. Depth, throughput, AA exposure, local threat, and the risk to the orbiting transport remain visible consequences of that commitment.
9. Vanilla chrome can be starved only through supported host state and API levers. There is no supported master HUD-off switch or above-HUD bridge layer; reflection is not an escape hatch.

## Extension boundaries

`air-nouns.md` owns the simulation's air bodies and transport lifecycle; the bridge owns only the host-side commitment and projection seam. `command-powers-nouns.md` owns fleet-sourced player commitments and must not be bypassed by a production bridge launch. `skybattle-fleet-control.md` tracks durable fleet-AI command, enemy fleet behavior, and the carrier-death pressure that makes an orbiting transport's stake fully live. `ground-control-mode.md` tracks the future player interaction layer for selecting and commanding ground forces beneath the fleet.

A production mission entrypoint constructs a frozen `GroundBattleConfig` at
the campaign boundary and carries no live campaign objects into simulation.
The debug probe demonstrates the bridge but is never production launch
authority.

External-air or direct-injection ownership, fleet control, extraction, proxy
shapes or new targetable tiers, and FBO decal projection each require an
explicit authority decision. None is implied by the existing bridge.
