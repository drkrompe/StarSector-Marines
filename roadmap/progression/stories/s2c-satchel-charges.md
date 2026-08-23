# S2C — Satchel charges

> Reach the obstacle, plant the charge, and survive the answer.

Status: PLANNED — depends on the shared special-equipment identity established
by `s2a-anti-materiel-rifle.md` and reuses the shipped planter/cordon doctrine
without becoming a mission objective.

Written: 2026-08-23

Read `progression-nouns.md`, `company-view-nouns.md`, and the Stage 2 tactical
story bank in `10-tactical-stories.md` before implementing this story.

## Tactical identity

A **satchel charge** is a single carried demolition pack planted adjacent to a
wall, emplacement, drone hub, or authored demolition target. It has much more
localized structural bite than a rocket but requires an approach, an exposed
planting channel, a fixed fuse, and a safe withdrawal.

The first slice deliberately does not support arbitrary ground placement,
mines, booby traps, or attaching charges to moving infantry, vehicles, or
mechs. Those uses would turn a breaching tool into a separate trap system and
would require different counterplay.

## Battle execution

1. A legal stationary target and adjacent reachable plant cell are selected
   and reserved.
2. The carrier paths to that cell and completes a readable planting channel.
   Ammunition is consumed when placement completes, not when the approach
   begins.
3. The armed charge becomes a battle-local hazard with a fixed fuse. The
   placing faction knows its location; opponents learn it only through honest
   sight or an authored audible cue.
4. The carrier and nearby allies clear the blast footprint. On expiry, the
   ordinary detonation/wall-damage pipeline resolves the explosion and emits a
   normal hostile-noise event.

The blast may be lethal inside its small clearance area, but its purpose and
targeting policy are structural. It cannot be thrown as a superior frag
grenade. The standard rocket remains the standoff choice; the satchel buys a
larger, more reliable breach with time and exposure.

## Gameplay-AI integration

Satchels require a multi-step plan rather than an extension of the current
“hardened target in range” special-fire branch.

### Breach selection

- A squad considers a wall only when its current commander assignment or
  identified engagement is materially blocked and removing that wall cell
  creates a valid, shorter tactical connection. It does not make decorative
  holes or breach a wall beside an already-usable portal.
- A stationary hardened target is eligible when a reachable approach and exit
  exist and the projected charge damage is not already reserved by another
  planter or inbound demolition.
- The scoring result includes approach exposure, plant-channel exposure,
  clearance distance, fuse time, and the value of the route or target opened.
  A charge is rejected when the carrier cannot plausibly leave the hazard
  before detonation.

### Team execution

- Reuse the shipped per-member planter assignment and portal-cordon doctrine:
  one equipped marine channels while siblings occupy covering positions. A
  personal satchel is not a `ChargeSite` and does not advance or complete the
  mission objective.
- Planting is mission-aware. The squad may abandon an uncompleted approach when
  survival or a changed assignment invalidates it; a completed armed charge is
  never silently refunded.
- Once armed, friendly movement treats the known blast footprint as a
  temporary hazard. A teammate does not enter it for routine cover or pursuit,
  and the squad does not begin a new bound through it.
- Opponents that honestly detect the charge may clear the footprint or break
  the planter's supporting formation. Undetected opponents receive no magical
  avoidance. Disarming is out of scope for the first slice.
- All selection, cordon, hazard, and reaction policy is faction-neutral.
  Defender use remains an explicit loadout decision.

## Reuse and authority

- Placement/channel coordination reuses the same planner primitives that make
  sabotage planters and their escorts legible, without coupling inventory to a
  mission charge site.
- The charge is a special-equipment activation, not a `WeaponDef`. Its
  detonation references the shared damage resolver and wall-damage authority;
  presentation mirrors that result rather than authoring it.
- Wall destruction must update navigation, zone connectivity, LoS, and any
  active plan using the changed route through the existing map-mutation seam.

## Campaign and presentation integration

- One physical satchel supplies one billet and one battle placement. Add a
  Breach template card with an explicit acquisition/fabrication path in
  `s6-unlock-ladder-expansion.md`.
- The carrier receives a plant pose; the armed pack, fuse, and dangerous blast
  footprint are readable to the player whenever their faction has legal
  knowledge of it.
- The HUD reports one carried charge, then empty. It does not display rocket
  range or imply a direct-fire action.

## Acceptance

- A satchel can breach a qualifying wall and destroy a qualifying stationary
  hardened target, but cannot be planted on arbitrary ground or moving units.
- AI selects only a breach that improves a current route or a stationary target
  with a survivable approach/exit; it does not breach beside an equivalent open
  portal.
- One planter channels while siblings cover, and personal placement never
  completes a mission-owned charge objective.
- Completed charges are not refunded, friendly AI avoids its known hazard, and
  enemy AI reacts only after honest detection.
- Two carriers do not reserve redundant lethal demolition against the same
  target or wall opening.
- Wall mutation invalidates affected navigation, zone, LoS, and plan state.
- Armory stock, template cards, deployment freeze, HUD state, telemetry, save
  compatibility, and unlock reachability recognize the satchel special.
