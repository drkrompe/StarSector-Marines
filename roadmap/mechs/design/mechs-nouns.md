# Mechs

Status: ACTIVE — the specialist family, campaign support squad, first subsystem inventory, and production composition are implemented; battlefield tuning remains active.

Written: 2026-08-23

Updated: 2026-08-23 — the Fleet Armory Mech Lab now owns a persistent support squad and finite replenisher inventory.

## Purpose

Mechs are a readable family of armored battlefield actors, not one universal
heavy unit scaled by encounter difficulty. A chassis gives up or gains a
specific combination of mobility, durability, weapon bands, silhouette, and
physical presence. Doctrine decides how that chassis uses its equipment. The
family creates target-priority and counterplay decisions without quietly
increasing an encounter's total armored threat.

## Vocabulary and ownership

- A **variant** is persistent hardware identity: chassis body, physical
  dimensions, default loadout, silhouette, and default doctrine. It owns what
  a live mech is.
- A **role** is tactical doctrine. It owns where a mech attempts to operate,
  what it supports, and when it advances, holds, or withdraws. A recommended
  role is not a chassis lock; the same hardware may receive another doctrine.
- A **loadout** is the live mount configuration. It owns installed hardpoint
  components and their independent ammunition, cooldown, salvo, lock, and
  resupply state.
- A **mount** is physical hardware at an arm or shoulder position. An absent
  mount is genuinely absent, never a dummy weapon or a hidden firing band.
- A **missile replenisher** is an installed onboard subsystem. It owns how
  quickly finite SRM and LRM mounts regain trigger packs, independently of
  their weapon cooldown and ready-ammunition capacity. Replenishment repeats
  for the life of the mech; the installed component is the future item-upgrade
  seam for changing that cadence.
- A **campaign mech** is one persistent player-owned chassis plus its doctrine
  and installed components. It is the campaign authority that produces a
  frozen battle loadout; the live battle mech never reaches back into it.
- A **mech squad** is the player-facing group of up to four campaign mechs
  configured together in the Fleet Armory. One selected active squad becomes
  the payload when a sourced Mech Support power is committed. It is distinct
  from the battle-lifetime lance that realizes that payload after landing.
- **Subsystem inventory** is finite fleet stock. Installed components count
  against owned quantity; a refit transaction returns the target mech's
  current component before evaluating the replacement.
- The **Mech Lab** is the Fleet Armory workspace that selects the active mech
  squad, presents each chassis and installed loadout, and performs inventory-
  checked refits. It is an authoring surface over campaign authorities, not a
  second inventory or a battle debug picker.
- A **weapon family** owns projectile behavior and presentation; a component
  turns it into a mountable rack/arm with capacity and appearance. The gun,
  mount, and chassis remain distinct authorities.
- A **capability tag** is the compatibility classification that routes a
  strider through shared mech construction. It is not the source of a
  variant's live stats, geometry, or loadout.
- A **battle lance** is the current combat grouping for one coherent mech
  squad. Formation and separation apply within that moving squad; unrelated
  squads do not become one formation. This does not define campaign ownership
  or lift organization.
- A **force budget** is the encounter authority that admits defender mechs and
  static defenses only when the attacker-side force can support them.

## Family and doctrine

The Bulwark is the durable all-band anchor and compatibility control case. The
Hound is a quick close-assault strider: it lacks long-range pressure and may
advance only with nearby combat infantry or a different live mech chassis, so
other Hounds cannot bootstrap a solo rush. The Sirocco is a fragile screened
support strider: long-range fire is primary, while its cannon is an
anti-hardened fallback rather than a replacement close-range saturation
weapon. Its overwatch seeks a friendly, non-Sirocco screen and re-evaluates
that screen as the battle changes. Once its long-range racks can no longer
apply pressure, it abandons a cached distant perch and closes only to the outer
edge of its installed arms range. It remains there while its onboard subsystem
replenishes the racks, then returns to long-range posture only when every rack
is full; individual restored triggers cannot make it oscillate between bands.
Exhausted ammunition may change doctrine positioning, but never grants access
to an absent mount or another role's withheld weapon.

All variants share movement-aware targeting and a planted-hip torso envelope:
near visible danger can interrupt a distant engagement, but the rear blind
wedge still makes body facing matter. When a lance moves in open terrain,
roles arrange its members forward, shoulder, and rearward; terrain may relax
that arrangement while retaining a minimum allied-mech separation. Idle posts
are not re-formed merely for visual tidiness.

## Authority flow

A spawn or delivery names a variant. The chassis profile supplies the body
across movement, targeting geometry, collision/separation, blast contact,
morale footprint, picking, appearance, and wreck continuity. It creates a
loadout, whose installed mounts drive firing, ammunition, resupply, and the
visible hardpoint layers. The role attached to that loadout feeds the mech
planner, independently of the hardware profile. Campaign support first freezes
variant, role, and installed subsystem from the active mech squad into plain
deployment values. The delivery power transports those values; landing then
constructs the live loadout and installs the frozen subsystem.

Defender setup produces a deterministic sequence of variants, not an
interchangeable mech count. Risk, target conditions, and attacking force
decide which candidates are affordable; the selected sequence then reaches
battle setup unchanged. A DEBUG briefing holds a stable, explicitly rerollable
family roster; support preserves that order while partitioning it into physical
drops and coherent squads of up to four chassis. This deliberately exercises
the real air-delivery seam but does not confer ownership, inventory, salvage,
refit, lift, or campaign entitlement. Ordinary campaign state separately owns
a starter support squad and its subsystem stock. Fleet or employer sourcing
still determines whether Mech Support is available in an operation; ownership
determines the payload, not the entitlement to call it.

## Laws

- Variant, physical geometry, and visible silhouette must agree. A lighter
  render scale may not retain heavy-sized picking, collision, hit, blast, or
  morale behavior.
- Hardware and doctrine are independent. Do not encode a planner behavior in
  a variant name or infer a chassis solely from its assigned role.
- Every specialist loses a meaningful capability as well as durability; a
  lighter chassis cannot be a discounted all-range Bulwark.
- Mount absence is a tactical weakness. Firing, continuation, AI utility,
  resupply, and rendering must operate only on installed components.
- Missile weapon hardware owns capacity and projectile behavior; the installed
  replenisher owns refill cadence. Upgrade content changes the subsystem rather
  than encoding reload speed in a chassis, role, or weapon definition.
- Owned subsystem quantity includes installed copies. A failed refit changes
  neither inventory accounting nor the target mech's installed loadout.
- Campaign-to-battle deployment freezes values. Live battle code does not read
  or mutate the campaign mech, squad, or fleet inventory.
- Gun-launched HE is a ballistic shot whose timed detonation owns splash and
  structural damage. It must not be represented as a boost-ramping missile
  merely to obtain spectacle.
- Defender variants replace equivalent encounter allocation; they do not add
  bodies or bypass the defender-vs-attacker force budget. Static turrets spend
  from the remaining defended budget after mobile defenders.
- Production behavior is deterministic from its setup inputs. Debug variation
  is explicitly scoped iteration scaffolding and must not leak into campaign
  progression.

## Current direction

The live `s1-specialist-striders.md` story retains manual comparison and tuning
of the shipped family. A future recon strider needs actual information
mechanics and a doctrine before its hardware is added. Further chassis
acquisition, salvage, weapon-component inventory, and custom-hardpoint refit
are separate progression work; the first lab slice intentionally makes those
mounts visible but read-only. The broader weapon catalog
migration may later data-drive projectile and mount definitions, but it must
preserve this chassis/mount/weapon authority split.

## Boundaries

The AI feature owns general planner machinery; mechs provide role-specific
hardware and constraints for it to command. Air delivery owns when a payload
lands, while this feature owns the landed variant/loadout identity.
`moddable-weapons-nouns.md` owns the future shared weapon-catalog direction;
mechs owns the mechanical and tactical meaning of a chassis and hardpoint.
Rendering consumes the variant and installed mounts to show the same body that
simulation uses; it does not choose stats or doctrine.
