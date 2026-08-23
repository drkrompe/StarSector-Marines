# ECS shipped ledger

Status: SHIPPED — ledger of retired migration stories folded into `ecs-nouns.md`.

Written: 2026-08-23

Completed ECS work is folded into `ecs-nouns.md`; this ledger keeps the evidence
needed to retire the migration journals without making the noun document a
commit log. Dates below are commit dates verified from Git.

| Folded story | Ship date | Verified ref(s) | Fold destination |
| --- | --- | --- | --- |
| `ai-timer-primitives.md` | 2026-05-28 | `b620e77`, `9104c85` | `ecs-nouns.md` — primitive state belongs to a component. |
| `aistate-onto-world.md` | 2026-06-25 | `8001f78` | `ecs-nouns.md` — capability and lifecycle membership. |
| `archetype-storage.md` | 2026-06-03 | `88d55117`, `955b6e54`, `0faa8bda` | `ecs-nouns.md` — storage, queries, and structural safety laws. |
| `burst-fire-primitives.md` | 2026-05-28 | `024344f` | `ecs-nouns.md` — component data, not handle fields. |
| `collapse-unit-handle.md` | 2026-06-01 → 2026-06-02 | `c50e50d`, `e038706`, `31058bf`, `335cce8`, `a708ce8` | `ecs-nouns.md` — bare-id identity. |
| `component-grouping.md` | 2026-06-27 | `88d55117`, `5a79941a` | `ecs-nouns.md` — capability grouping and presence. |
| `component-model.md` | 2026-06-27 | `88d55117`, `5a79941a` | `ecs-nouns.md` — completed composition model. |
| `components-by-capability.md` | 2026-06-03 | `13c59831`, `88d55117` | `ecs-nouns.md` — capability rather than carrier law. |
| `corpse-archetype-retrofit.md` | 2026-06-03 | `b98c706` | `ecs-nouns.md` — death transmute. |
| `dissolve-unit-registry.md` | 2026-06-27 | `751458a0`, `5a79941a` | `ecs-nouns.md` — one world and bare ids. |
| `drop-sim-facade-delegators.md` | 2026-05-28 → 2026-05-29 | `a734122`, `cb91e87`, `61e322a` | `ecs-nouns.md` — narrow view/control contracts. |
| `entity-field-migration.md` | 2026-06-29 → 2026-07-01 | `93602bbf`, `1634f995`, `b77e45a8` | `ecs-nouns.md` — service-owned component fields. |
| `entity-id-handle.md` | 2026-06-02 | `6f4e42b`, `5a3ffb3`, `38d25c8`, `4c19bb7`, `335cce8` | `ecs-nouns.md` — id references and tolerant liveness. |
| `firing-system.md` | 2026-07-01 | `c07a11ef`, `426f21db`, `b418d835` | `ecs-nouns.md`; residual behavior change is `fire-stance-normalization.md`. |
| `health-onto-world.md` | 2026-06-03 | `e720e988`, `adb4bc93` | `ecs-nouns.md` — live/dead lifecycle ownership. |
| `identity-collapse.md` | 2026-07-01 → 2026-07-05 | `a4180ef0`, `2d9eb894`, `38764ca7`, `1ed54dc4` | `ecs-nouns.md` — entity is a `long`. |
| `live-appearance.md` | 2026-07-01 | `9f1c33f0`, `ee215e14`, `9bd3c7fa` | `ecs-nouns.md`; residual work is `authored-walk-animation.md`, `secondary-aim-facing.md`, and `fx-child-entities.md`. |
| `map-service-coordinator-slice1.md` | 2026-05-28 | `c49eea7` | `ecs-nouns.md` — MapEditor runtime-mutation coordination only. |
| `map-service-coordinator.md` | 2026-05-28 | `c49eea7` | `ecs-nouns.md` — generation remains separate; its deferred slice is not queued. |
| `move-render-primitives.md` | 2026-05-27 | `489b1db` | historical only; ground position authority is `continuous-positions-nouns.md`. |
| `movement-aistate-membership-narrowing.md` | 2026-06-25 | `91380de4` | `ecs-nouns.md` — optional movement and decision membership. |
| `movement-onto-world.md` | 2026-06-25 | `42cc723` | `ecs-nouns.md` — component-owned motion state. |
| `movement-path-ref.md` | 2026-06-25 | `5ee1090`, `688a7568` | `ecs-nouns.md` — component-owned path state. |
| `path-mutation-to-navigation.md` | 2026-05-28 | `2f48c36` | `ecs-nouns.md` — navigation service owns path mutation. |
| `phase0-measurement.md` | 2026-06-29 | `dbeabd8c` | `ecs-nouns.md` — profile-gated column walking. |
| `phase1-registry-infra.md` | 2026-05-22 | `4b60ceb3`, `3d8bbec9` | `ecs-nouns.md` — historical predecessor to archetype tables. |
| `phase2-entity-ids.md` | 2026-05-22 | `14cf2e6`, `5e06311`, `2afee3d` | `ecs-nouns.md` — id reference law. |
| `phase3-soa-promotions.md` | 2026-05-22 → 2026-05-28 | `7972009`, `53ee895`, `a4df09b`, `489b1db`, `c929087`, `01fe905`, `024344f`, `7ae84e6`, `b620e77`, `9104c85` | `ecs-nouns.md` — historical predecessor to component columns. |
| `position-onto-world.md` | 2026-06-03 | `b92c8bd` | `continuous-positions-nouns.md` and `ecs-nouns.md`. |
| `retire-legacy-units-list.md` | 2026-06-01 → 2026-06-27 | `2e03ade`, `b98c706`, `5a79941a` | `ecs-nouns.md` — lifecycle data stays in the world. |
| `secondary-weapon-primitives.md` | 2026-05-28 | `01fe905` | `ecs-nouns.md` — optional capability data. |
| `seeded-battle-determinism.md` | 2026-08-22 | `852f23b6`, `418b3584`, `f952095a` | `ecs-nouns.md` — battle-owned random stream. |
| `store-folds-and-render-position.md` | 2026-06-25 | `dafaacaf`, `8f8a0d76`, `1cbf5b03` | `ecs-nouns.md`; position evolution is `continuous-positions-nouns.md`. |
| `systems-to-columns.md` | 2026-06-29 → 2026-07-01 | `dbeabd8c`, `50a6157b` | `ecs-nouns.md` — measurement gate and occupancy precedent. |
| `tactical-primitives.md` | 2026-05-27 | `c929087` | `ecs-nouns.md` — component data. |
| `target-id-primitive.md` | 2026-05-28 | `7ae84e6` | `ecs-nouns.md` — id references. |
| `vehicle-control-ecs.md` | 2026-07-05 | `7fe601c9`, `92842b4d`, `d485c3ea`, `63b1e1cc`, `a081900c`, `40aafd4e` | `ecs-nouns.md`; acceptance is `vehicle-control-lifecycle-playtest.md`. |
| `vehicle-into-world.md` | 2026-07-01 | `321cc047`, `730713d6`, `963d7987`, `1e128ce0`, `88bf85c6`, `f1ad8753`, `35840353` | `ecs-nouns.md` — convoy entity family. |
| `world-facade.md` | 2026-06-27 | `751458a0`, `5a79941a` | `ecs-nouns.md` — service ownership and narrow contracts. |

The old journals may be deleted only after their inbound roadmap and
source-Javadoc references redirect to this noun package or to the relevant
feature noun. Purely superseded handoff/direction notes are intentionally not
ledger rows.
