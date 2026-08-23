/**
 * The battle tier — the headless ground-combat simulation and its presentation.
 *
 * <p>This is the taxonomy root. Every top-level subpackage carries its own
 * {@code package-info.java} charter (category / charter / boundary / pointer);
 * this file defines the <em>categories</em> those charters sort themselves
 * into and the boundary rules that decide where new code lands. It replaces
 * the retired {@code roadmap/battle-reorg/overview.md}, whose feature-vertical
 * reorg shipped in full (the {@code unit/} &rarr; {@code entity/} rename alone
 * is still deferred to the ECS migration).
 *
 * <h2>Organizing principle: framework core vs feature domain</h2>
 *
 * The layout splits on <b>one</b> axis, deliberately. An earlier taxonomy split
 * on two at once — technical layers ({@code ai/}, {@code fx/}, {@code damage/})
 * beside feature domains ({@code drone/}, {@code air/}) — which gave a new
 * feature no rule for where it belonged, so it landed in both. That produced
 * conflation (one package holding several concepts) and fracture (one concept
 * spread across several packages) at the same time.
 *
 * <ul>
 *   <li><b>Framework core</b> — mechanism with no single feature owner: entity
 *       storage, the navigation substrate, the GOAP <em>planner</em> (not the
 *       behaviors), the tick orchestrator, shared scoring, profiling. Stays
 *       central and thin.</li>
 *   <li><b>Actor domain</b> — one kind of thing that fights: its entity data,
 *       weapons, behaviors, goals, lifecycle, and render hooks, together.</li>
 *   <li><b>Feature domain</b> — a cross-actor system: combat resolution, squad
 *       coordination, command, vision, the world model.</li>
 *   <li><b>Presentation</b> — HUD and render-facing code.</li>
 * </ul>
 *
 * <p>"No shared {@code ai/} or {@code fx/} layer" means <b>kill the
 * catch-alls</b>, not dissolve genuine mechanism. Behaviors and visuals
 * distribute into the domains that own them; engines stay in the core.
 *
 * <h2>The map</h2>
 *
 * <p><b>Framework core</b>
 * <ul>
 *   <li>{@code unit/} — the entity registry, dense SoA storage, spatial
 *       indices, {@code Faction} / {@code UnitType} / {@code UnitRole}.</li>
 *   <li>{@code component/} — the game's binding to the archetype-table
 *       {@code engine.ecs.EntityWorld}. Component <em>records</em> live in a
 *       {@code components} subpackage of their own domain, never here.</li>
 *   <li>{@code nav/} — navigation grid, pathfinder, zones and portals.</li>
 *   <li>{@code decision/} — tactical scoring, per-unit dispatch, and
 *       {@code goap/} (planner, built-in actions/goals, scoring, world state).</li>
 *   <li>{@code sim/} — {@code BattleSimulation}: the tick order and nothing
 *       else, long-term.</li>
 *   <li>{@code setup/} — battle construction and defender rosters.</li>
 *   <li>{@code profile/} — tick profiling and caches.</li>
 * </ul>
 *
 * <p><b>Actor domains</b> — {@code infantry/}, {@code mech/}, {@code drone/},
 * {@code air/}, {@code vehicle/}, {@code turret/}.
 *
 * <p><b>Feature domains</b> — {@code combat/} (fire &rarr; hit &rarr; damage
 * &rarr; fx, including {@code combat/fx/}), {@code squad/}, {@code command/}
 * (with {@code objective/}, {@code reinforcement/}, {@code compound/},
 * {@code influence/}), {@code vision/}, {@code world/} ({@code model/},
 * {@code gen/}, {@code tiles/}), {@code perception/}, {@code appearance/},
 * {@code power/}, {@code logistics/}, {@code evacuation/}, {@code colony/},
 * {@code audio/}.
 *
 * <p><b>Presentation</b> — {@code ui/} and {@code flyby/}.
 *
 * <h2>Boundary rules</h2>
 *
 * <ol>
 *   <li><b>The GOAP partition.</b> A <em>goal</em> follows its composer — the
 *       actor slice whose {@code Goap*Behavior} builds the plan. The composers
 *       import disjoint goal sets, so this is unambiguous. An <em>action</em>
 *       follows its goals: used by one slice, it moves with that slice; used
 *       across slices or part of the base posture set, it becomes a built-in
 *       in {@code decision/goap/action/}. A squad-coordination goal composed by
 *       more than one actor type promotes to {@code squad/}.</li>
 *   <li><b>One {@code EffectsService} sink.</b> Most visual fx are
 *       combat-caused, so the single sink lives in {@code combat/fx/}.
 *       Per-domain fx ownership can be split out later if it earns it.</li>
 *   <li><b>Mechanism to the core, configuration to the slice.</b>
 *       {@code HeavyWeapons} is the chassis-weapon firing mechanism and lives
 *       in {@code combat/}; the {@code Mech*} role/loadout config lives in
 *       {@code mech/}.</li>
 *   <li><b>Services own state; Systems are stateless tick consumers.</b> See
 *       {@code roadmap/ecs-migration/overview.md} for how that state is
 *       stored.</li>
 * </ol>
 *
 * <p>A new top-level subpackage gets a {@code package-info.java} stating its
 * category, charter, and boundary, and this map gains a line.
 */
package com.dillon.starsectormarines.battle;
