# Fog of War

Status: ACTIVE — shipped visibility spine; deferred time-of-day direction

Written: 2026-08-23

Read `stories.md` for open work.

## Purpose

Fog of war is the player-facing account of what a battle-side observer can
currently see. It darkens terrain outside player vision, hides hostile units
outside that vision, and opens a building roof only when the same vision reaches
its interior. It also gates enemy-spotted radio presentation. It is
presentation authority, not a substitute for combat or AI perception.

The shipped system supplies current visibility, not persistent intelligence.
When a hostile leaves sight it fades away; it does not yet leave a last-known
position, and shots remain visible through fog.

## Vocabulary and ownership

- A **vision contributor** is a live unit from a player-visible faction. Its
  sight range and close-wall air line-of-sight radius are live unit attributes
  owned by `VisionService`; `PlayerVisionState` owns which factions contribute.
- An **observation footprint** is the set of cells one contributor can presently
  see. `Shadowcast` derives it from map line-of-sight, range, and any air
  clearance; it is not a tactical target-selection result.
- The **reveal bitmap** is the union of contributor and temporary-source
  footprints. `FogOfWarService` owns its reference count and derived revealed
  cells, so overlapping sources cannot erase one another's sight.
- A **temporary source** is a short-lived, externally projected observer such
  as a shuttle, flyby fighter, or recon ping. It participates in the same
  bitmap but has no battle-unit visibility row or cohort membership.
- A **unit visibility state** is the renderer's HIDDEN, VISIBLE, or FADING
  presentation of a live non-contributor. It is distinct from a unit's own
  tactical line-of-sight and from whether a cell has ever been explored.
- A **roof reveal** is a building presentation state derived from its interior
  cells in the reveal bitmap. It is neither a separate line-of-sight algorithm
  nor a building gameplay state.

`FogOfWarService` is the one owner of player reveal composition and visual
unit state. The map owns opacity; `VisionService` owns mutable sight inputs;
render systems consume the resulting state. Mission setup and command powers
must route through that service rather than maintaining a rival visible-area
calculation. Contributor factions are fixed during setup before their units
spawn; command powers may add temporary sources. Runtime faction-membership
changes are not supported until they can enroll or remove every affected live
unit and footprint atomically.

## Flow

After map setup, a newly spawned contributor immediately casts a footprint so
its surroundings do not begin falsely dark. On the vision cadence, the fog
service refreshes one rotating cohort of mobile contributors, rebuilds the
temporary-source contribution, derives hostile unit visibility, and projects
the same bitmap into roof targets. A reference count preserves a cell until its
last source leaves it.

The renderer places fog over terrain and below units. It suppresses hidden
unit and drone draws, fades units that have just left sight, and renders roofs
over interiors until their target alpha falls. Radio presentation consumes the
same visibility state for enemy-spotted callouts. Hosts advance those visual
fades on real frame time, independently of simulation speed, while the
simulation continues to own reveal and visibility transitions.

## Standing laws

1. Player-visible sight is the union of all legal contributors and temporary
   sources. Removing one footprint must not conceal a cell still revealed by
   another.
2. Player faction is an explicit contributor-set decision, not an inference
   from renderer ownership. Mission setup must add allied factions before their
   units spawn; enemy AI perception remains separate.
3. Fog controls presentation only. It must not secretly change firing,
   pathfinding, collision, damage, or tactical perception. Shot visibility is
   deliberately outside the V1 gate.
4. A roof's visibility follows the bitmap's interior-cell rule. A roof may not
   stay opaque when that same player vision reaches an interior cell; temporary
   overhead observation follows the same rule.
5. Temporary sources must be replaced as a set, not accumulated across frames
   or cohort turns. Their expiry must remove only their own footprint.
6. A change to any footprint input — position, vision range, air clearance,
   map opacity, or contributor membership — must invalidate/recompute the
   affected observation footprint even for a stationary unit.
7. Visibility and fade records follow the live entity across dense-roster
   compaction and are cleared at release. A newly moved dense occupant must
   never inherit the released occupant's visual state.
8. Current observation is not memory. Last-known ghosts, projectile gating,
   and ambient lighting are separate extensions and must state their own
   authority rather than changing the V1 meaning of revealed.

## Current shipped behavior

V1 uses line-of-sight shadowcasting with an incremental contributor cadence,
a ref-counted reveal union, a soft-edged terrain-darkening overlay, and hidden /
fading hostile render states. The building pass now reads that same union rather
than sampling a different roof-specific sight test, so terrain, unit, and roof
presentation agree.

The current dense-slot lifecycle and stationary-footprint cache do not yet
fully satisfy laws 6 and 7. `stories.md` tracks those two bounded authority
cleanups; they are not missing V1 features.

Temporary vision currently comes from friendly visible shuttles, friendly
flyby fighters, and active recon pings. It can therefore open a roof briefly
when its footprint sees an interior; that is intentional.

## Time of day and adjacent systems

`time-of-day.md` remains a deferred story, but no time-of-day gameplay
or lighting implementation is currently installed: the dormant lightmap
experiment was removed on 2026-06-29. If revived, time of day owns the mission
clock and its dawn event; fog consumes its sight multiplier through the
`VisionService` inputs, and reinforcement owns the resulting arrival policy.
It must satisfy the footprint-invalidation law rather than rely on unit motion
to refresh a night range change.

Fog is adjacent to the render pipeline because it chooses paint order and
consumes visual state, and to map generation because buildings provide interior
cells. Neither adjacent system owns player sight. The combat bridge is another
render host and must advance the same roof and unit fade presentation as the
standalone battle screen.
