# Mechs

Status: ACTIVE — distinct chassis, persistent support loadouts, and production composition share one mech authority; battlefield tuning continues.

Written: 2026-08-23

Updated: 2026-08-30 — defined the four battle doctrines and the battle-local
override that selects among them without replacing mission authority.

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
- A **deployed role** is the campaign mech's authored role frozen into one
  battle payload. A live mech may carry a nullable **battle role override**;
  its **effective role** is that override when present and otherwise its
  deployed role. Clearing the override returns to the deployed role, not the
  variant's recommendation. The override ends with the battle.
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
  configured together in the Mech Lab. One selected active squad becomes
  the payload when a sourced Mech Support power is committed. It is distinct
  from the battle-lifetime lance that realizes that payload after landing.
- **Subsystem inventory** is finite fleet stock. Installed components count
  against owned quantity; a refit transaction returns the target mech's
  current component before evaluating the replacement.
- The **Mech Lab** is the shipboard room that selects the active mech squad,
  parks its assets across four fabrication gantries, frames one selected asset as
  a top-down hardpoint doll, and performs inventory-checked refits. Asset/lance browsing is
  a separate internal bay screen so the fitting gantry can spend its width on
  equipment, the physical workspace, and sockets. Selecting a location changes
  catalog context; it does not itself change hardware. It is an authoring surface
  over campaign authorities, not a second inventory or a battle debug picker.
- A **socket** is one spatial equipment location on a heavy asset. Custom-refit
  sockets will declare a compatibility type—ballistic, energy, missile, or
  omni—and a sized capacity. A component will declare compatible types and a
  slot cost. Each asset-class layout owns the socket's physical mount anchor,
  its equipment-dock center around the doll, and its doll-relative interaction
  footprint as well as compatibility and capacity. A leader preserves the
  relationship between a remote dock and the mount it configures.
  Presentation may enforce a minimum pointer hit area for usability without
  changing that capacity or making the asset physically larger. An empty translucent
  footprint means an authored socket is unoccupied; an omitted socket is still
  genuinely absent. The drag gesture is presentation; the validated resulting
  placement is domain intent.
- A **fitting doll** is the asset-authored maintenance presentation of one mech
  or vehicle class. It references the same ordered appearance and physical scale
  used in battle, while owning the maintenance facing and every socket's physical
  mount anchor, remote equipment dock, interaction footprint, compatibility, and
  capacity. The renderer consumes that definition; it does not guess mount
  locations from a generic chassis shape. Future external authoring may move the
  definition out of code without changing that ownership boundary.
- A **workshop task route** is a battle-owned ambient assignment for one Mech Lab
  worker. Fabrication, parts-running, inspection, and coordination routes own the
  actor's stations, walking, dwell activity, facing, and any task-local tool or
  effect. The worker remains a real room entity rendered by the battle unit
  pipeline. A workshop route communicates a busy facility but owns no refit
  duration, inventory movement, or campaign outcome.
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
- A **force budget** is the ordinary-encounter authority that admits defender
  mechs and static defenses only when the attacker-side force can support them.
  Authored set pieces may explicitly decline that protection; Conquest does.

## Family and doctrine

The Bulwark is the durable all-band anchor and compatibility control case. The
Hound is a quick close-assault strider that lacks long-range pressure. The
Sirocco is a fragile long-range specialist whose cannon is an anti-hardened
fallback rather than a replacement close-range saturation weapon. Those are
hardware identities; any deployed chassis may receive any of these doctrines:

- **Brawler** seeks the close or direct-fire band and may pursue a local threat
  inside its assignment leash. Allied support may shape that advance but is not
  permission to act, so losing the screen does not stop a legal attack.
- **Frontline Support** attaches to a nearby allied infantry squad or mech
  element and takes the threat-facing side of that group. It absorbs pressure
  without inventing an independent attack; without a legal anchor it holds and
  defends itself. The battle UI may call this doctrine **Tank**.
- **Long-Range Support** prefers the outer useful weapon band, retained line of
  sight, and a friendly screen between itself and perceived danger. If a threat
  closes, it fights while opening distance rather than treating the old perch
  as sacred.
- **Balanced** serves its assignment from the middle or outer portion of its
  installed direct-fire band. It accepts a close threat already upon it, then
  resumes the assignment, and has no special ally-geometry dependency.

The role changes how a mech serves an assignment; it never supplies an
assignment, hidden contact, or permission to leave mission-command bounds.
Different effective roles in one battle lance execute per member rather than
competing to turn the lance's shared plan into one role.

Long-range doctrine still evaluates whether a prospective screen is physically
credible. A fragile Sirocco is not made into armor merely because it receives
another doctrine. Once long-range racks can no longer apply pressure, their
carrier may close to the outer edge of its installed arms range while the
onboard subsystem replenishes them, returning to long-range posture only when
every rack is full; individual restored triggers cannot make it oscillate
between bands. Exhausted ammunition may change doctrine positioning, but never
grants access to an absent mount or another role's withheld weapon.

All variants share movement-aware targeting and a planted-hip torso envelope:
near visible danger can interrupt a distant engagement, but the rear blind
wedge still makes body facing matter. When a lance moves in open terrain,
roles arrange its members forward, shoulder, and rearward; terrain may relax
that arrangement while retaining a minimum allied-mech separation. Idle posts
are not re-formed merely for visual tidiness.

Facing communicates intent without granting knowledge or changing locomotion.
An active target owns upper-chassis aim. Without one, the mech looks toward its
squad's primary remembered contact at the last-known cell; without contact, it
looks a short horizon along its queued route so an approaching turn reads before
the hips reach it. Remembered facing never reads the hidden unit's live position
and never becomes permission to fire. Moving hips remain aligned to the path;
stationary hips may settle toward the same remembered contact.

Hip and upper-chassis turns accelerate and brake within separate angular
budgets. A changed target or route therefore arrests the old swing before
reversing it instead of snapping to a new turn direction. Each foot retains a
world-space plant until reach or yaw requires a step; the lifted foot advances
toward a stance-constrained landing predicted around the body's touchdown
position and adopts the hip bearing only when it plants. A completed moving
stride leaves one pad behind the body and the new pad ahead, rather than letting
the chassis overrun both supports. Pivoting uses the same rule, so one pad
supports the body while the other walks around the turn. The rendered waist and
complete upper assembly respond through a damped, two-dimensional weight
transfer toward the single support pad or the segment between two planted pads.
Composition preserves the physical depth stack: feet first, thigh linkages over
the feet, then the ordinary upper assembly and its equipment layers.
This gait is fixed-tick presentation state only: it does not move collision,
pathing, aim, or targeting authority.

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

Selecting an exact friendly mech during battle may request another effective
role. The battle applies that request at its serialized command boundary,
invalidates role-owned movement and planning state, and replans immediately.
The interrupt preserves the mech's assignment, legal contact picture, morale,
damage, ammunition, cooldowns, and deployed role. It cannot target an enemy,
non-mech, stale entity, or rescue payload, and it never writes back to campaign
state. Persistent doctrine authoring remains a Mech Lab responsibility.

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
The debug roster count has no scenario-authored maximum; every requested chassis
becomes part of the deterministic roster and therefore another four-chassis-or-less
support sortie, subject only to practical runtime resources.

## Laws

- Variant, physical geometry, and visible silhouette must agree. A lighter
  render scale may not retain heavy-sized picking, collision, hit, blast, or
  morale behavior.
- Hardware and doctrine are independent. Do not encode a planner behavior in
  a variant name or infer a chassis solely from its assigned role.
- A battle role override changes only local tactical manner. It does not author
  mission destination, reveal a contact, waive survival law, or mutate the
  deployed or campaign role.
- Mixed-role lances apply doctrine per live mech. One member's role may not
  starve another member's doctrine through a shared squad plan.
- Every specialist loses a meaningful capability as well as durability; a
  lighter chassis cannot be a discounted all-range Bulwark.
- Chassis structure, armor capacity, and armor rating are separate values. Bulwark
  remains the high-capacity, high-rating anchor; Hound trades armor endurance for
  mobility; Sirocco has the least protection and depends on range and a screen.
- Mount absence is a tactical weakness. Firing, continuation, AI utility,
  resupply, and rendering must operate only on installed components.
- Missile weapon hardware owns capacity and projectile behavior; the installed
  replenisher owns refill cadence. Upgrade content changes the subsystem rather
  than encoding reload speed in a chassis, role, or weapon definition.
- Owned subsystem quantity includes installed copies. A failed refit changes
  neither inventory accounting nor the target mech's installed loadout.
- A future custom refit must validate socket type, sized capacity, component
  inventory, and any chassis budgets in one atomic command. An omni socket
  accepts several equipment types; it does not waive slot cost or budgets.
- Spatial selection and drag previews have no mutation authority. Dropping a
  component may propose a placement, but only a successful campaign command
  changes the installed loadout.
- The fitting doll consumes the same ordered layer composition and hull-relative
  transforms as battle rendering. A preview may choose a static maintenance pose,
  but it may not approximate mount scale, pivots, absence, or above/below-chassis
  order independently. The gantry rotates that complete composition south toward
  the player; it does not rotate individual equipment layers independently.
- The fitting room is one lance-scale facility battle scene rendered through the
  shared battle camera and ordered render systems. Its four striped-and-grated
  gantry pads, service gallery, storage machinery, and connected ship corridor are
  real grid-aligned room cells and registered fixtures; its south edge is the vehicle
  entrance. Responsive layout may change the camera's fitted cell size or choose
  a closer framing, but it may not canvas-fit the
  selected asset or distort its physical size relative to technicians, tiles,
  props, or another chassis.
- Socket overlays consume the selected asset's authored fitting layout. Their
  strongly translucent type color, large hull-relative drop footprint, and segmented capacity
  cells occupy the gantry around the physical doll, with a light leader returning
  to the authored mount anchor. The doll remains readable instead of becoming a
  pile of UI rectangles, and the rendered room becomes useful fitting space.
  The whole footprint is the pointer target; capacity cells are the ordered placement units
  a future multi-slot drag preview occupies. An authored empty socket stays visible
  while an absent socket produces no footprint.
- The garage is a flat top-down, non-advancing room simulation assembled from the
  battle renderer's indoor tileset cells and props. Its mech and workers are real
  battle entities consumed by the ordinary unit render system. The retained host
  supplies only a bounded viewport and camera: battle HUD, input, selection,
  audio, fog, combat decorators, and surface-relief targets remain detached.
  It does not advance combat AI, but deterministic workshop jobs may author real
  technician positions, walk poses, facing, tools, and effects as presentation time
  advances. Welding sparks exist only while a technician is dwelling at a welding
  job; they are not a fixed canvas ornament. Job motion continues while the player
  inspects equipment, but it never decides refit duration, stock, or command success.
- Live rendering and headless evidence consume one room layout: ground kind,
  perimeter walls, gantry overlays, props, workers, and every lance asset occupy authored
  battle-grid cells. Headless evidence collects the ordinary battle renderer's command
  list and substitutes only a Java2D drain; it may not invent percentage-positioned
  scenery, off-grid props, actor approximations, or a second gantry illustration.
- The room opens on a facility-wide camera frame that always includes all four
  physical pads—including vacant ones—plus the adjacent service and circulation
  space. The camera does not crop to the number of assigned assets. This is a
  non-selected state: it gives the room its full width, labels all four gantries,
  and exposes no equipment catalog, performance strip, or socket rack. Selecting
  an occupied gantry establishes the fitting asset and eases
  the same battle camera into it; only then does the fitting workspace reveal those
  chassis-scoped controls. Returning through the current Mech Lab room route clears
  that selection and restores the overview. No camera transition moves, respawns,
  pauses, or rebuilds garage contents to fake motion.
- The fitting header exposes previous/next gantry controls and a numbered
  `01 / 04` station position. Navigation wraps across the four physical pads; arriving
  at a vacant pad clears the fitting selection and exposes that vacancy in the overview
  rather than rendering chassis controls against nothing. Lance browsing remains a
  separate direct-jump surface.
- Campaign-to-battle deployment freezes values. Live battle code does not read
  or mutate the campaign mech, squad, or fleet inventory.
- Gun-launched HE is a ballistic shot whose timed detonation owns splash and
  structural damage. It must not be represented as a boost-ramping missile
  merely to obtain spectacle.
- Defender variants replace equivalent encounter allocation; they do not add
  bodies. Ordinary encounters apply the defender-vs-attacker force budget and
  let static turrets spend from the remainder after mobile defenders. Conquest
  deliberately bypasses that budget and retains its authored mechs and static
  weapons as part of the fixed siege.
- Production behavior is deterministic from its setup inputs. Debug variation
  is explicitly scoped iteration scaffolding and must not leak into campaign
  progression.

## Extension boundaries

A new chassis requires a distinct information or combat doctrine and a real
capability it gives up; hardware variety alone does not earn another variant.

Chassis acquisition, salvage, weapon-component inventory, engine cores, ammunition
modules, and custom-hardpoint refit belong to progression and economy authority.
The Mech Lab already exposes their spatial locations, socket vocabulary, and a
context catalog, but mounts remain read-only until those acquisition, compatibility,
capacity, budget, and component authorities exist. The missile mini-fab is currently
the only swappable socket because it is the only one with finite inventory and an
atomic install command.

The doll presentation may later host tanks and other scarce heavy armor, but it must
consume an asset-class-specific socket layout. Sharing selection, catalog, drag, and
validation presentation does not make a vehicle use a mech chassis or mech mount
schema.

The fitting gantry's reference composition is wide-screen: equipment catalog,
physical bay, and socket rack remain simultaneously visible. Narrower viewports and
larger user UI scales preserve the same commands through bounded scrolling and
shorter labels; they are not required to preserve the wide view's information density.

A future shared weapon catalog may data-drive projectile, weapon, and mount
definitions, but it must preserve the chassis/mount/weapon authority split.

## Boundaries

The AI feature owns general planner machinery; mechs provide role-specific
hardware and constraints for it to command. Air delivery owns when a payload
lands, while this feature owns the landed variant/loadout identity.
`moddable-weapons-nouns.md` owns the future shared weapon-catalog direction;
mechs owns the mechanical and tactical meaning of a chassis and hardpoint.
`combat-durability-nouns.md` owns how the chassis's authored structure and
armor profile receives damage and exposes an armor-break transition.
Rendering consumes the variant and installed mounts to show the same body that
simulation uses; it does not choose stats or doctrine.
