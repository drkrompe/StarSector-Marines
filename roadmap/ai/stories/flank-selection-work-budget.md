Status: IN PROGRESS
Written: 2026-09-27

# Bound optional flank selection

The convoy-deadline paced capture attributes 87.945 ms of host CPU to one
ReinforceContact plan. Repeated candidate routes are found and then rejected
by detour length; the existing admissible bounds do not limit total effort.

Use `ai-nouns.md` as the standing model. Give each selection a shared expansion
allowance across candidate A* and its optional minimum-step flood. Keep the
best fully verified candidate on exhaustion, otherwise use the existing origin
refusal and ordinary-contact handoff. Do not label exhaustion disconnected.
Budgeted searches must not trigger a whole-map connectivity flood before A*.

Acceptance: exact expansion-count tests for distant and local doglegs, single
and multiple proofs, gate accounting, incumbent retention, and ordinary choices;
independent same-build controls and profiling; focused regression tests and a
paced production-scheduler capture. This changes best-effort tactical choices,
not movement legality. It does not solve concurrent squad memo ownership,
shared tick admission, scratch allocation, or every remaining spike.
