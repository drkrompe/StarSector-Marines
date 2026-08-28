# Fog of War

Status: ACTIVE — shared observation composes current player visibility across contributors, temporary sources, terrain, units, and roofs.

Written: 2026-08-23

Updated: 2026-08-28 — temporary sources split into the host-projected and simulation-carried channels as the Janus sensor sweep joined them.

Read `stories.md` for open work.

## Purpose

Fog of war is the player-facing account of what a battle-side observer can
currently see. It darkens terrain outside player vision, hides hostile units
outside that vision, and opens a building roof only when the same vision reaches
its interior. It also gates enemy-spotted radio presentation. It is
presentation authority, not a substitute for combat or AI perception.

The system supplies current visibility, not persistent intelligence.
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
- A **temporary source** is a short-lived observer that participates in the same
  bitmap but has no battle-unit visibility row or cohort membership. It arrives
  through one of two channels. A **projected source** is pushed by a render host
  each frame — a shuttle, a flyby fighter, an active recon ping. A **carried
  sweep** is pushed by the simulation each tick from a live unit whose armour
  pattern is running a sensor system (`progression-nouns.md`), so it exists in
  every host and in a headless run alike. The two channels are separate because
  they are cleared by different owners at different cadences; each is replaced
  as a set, and both feed the one footprint rebuild.
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
   deliberately outside the fog presentation gate.
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
   authority rather than changing the current-observation meaning of revealed.

## Observation and lifecycle boundaries

Line-of-sight shadowcasting runs on an incremental contributor cadence and
builds a ref-counted reveal union. A soft-edged overlay darkens terrain, hostile
units transition through hidden and fading states, and the building pass reads
the same union rather than sampling a roof-specific sight test. Terrain, unit,
and roof presentation therefore agree on what the player can observe.

A contributor footprint may be reused only while every footprint input remains
compatible, not merely while its cell is unchanged. Dense-slot visibility and
fade state must likewise transfer or clear with roster compaction and release.
`stories.md` owns the two bounded compatibility corrections where the existing
cache and lifecycle do not yet satisfy those laws.

Friendly visible shuttles, friendly flyby fighters, active recon pings, and a
marine running a carried sensor sweep can all provide temporary vision. Their
footprints may therefore open a roof briefly when they see an interior; that is
intentional.

A carried sweep additionally uses the shadowcast's existing air-clearance
parameter as a bounded wall tolerance, which is the same "walls near the source
are transparent" rule a flier already relies on rather than a second visibility
algorithm. It is subject to every law above, and to one that matters especially
for it: a source that reveals must not also be a source that decides. Fog
remains presentation authority, so a capability's own trigger reads the carrier's
line of sight, never the reveal bitmap; and a source only ever adds, so nothing
projected into this bitmap can make a unit less visible than it already was.

## Adjacent authority boundaries

No time-of-day clock or ambient-lighting authority is installed. A future
mission-owned clock would own its dawn event, fog would consume any sight
multiplier through `VisionService`, mission policy would own whether dawn
authorizes reinforcements, and reinforcement would own delivery. Changing the
multiplier must satisfy the footprint-invalidation law rather than rely on unit
motion to refresh a night range change.

Fog is adjacent to the render pipeline because it chooses paint order and
consumes visual state, and to map generation because buildings provide interior
cells. Neither adjacent system owns player sight. The combat bridge is another
render host and must advance the same roof and unit fade presentation as the
standalone battle screen.

Smoke grenades are another adjacent input, not a fog feature. Battle
simulation owns each transient smoke field and the
shared tactical line-of-sight layer treats its cells as temporary opacity.
Fog responds by invalidating and recasting affected observation footprints,
including stationary contributors, so player reveal reflects the same
occlusion. It must not maintain a private smoke list or grant the player a
different view through the cloud than battle AI receives.
