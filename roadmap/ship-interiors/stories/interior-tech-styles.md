# Interior tech styles: midline and high-tech

Status: PROPOSED
Written: 2026-10-21

## Scope

Give midline and high-tech hulls interior styles of their own (`ship-interiors-nouns.md`,
interior tech style), each with its full condition range from wreck to Restored, the way
low-tech has one. Today every generated hull is drawn low-tech.

## Constraints

- A style is presentation. It never changes rooms, doors, berths, lanes, cover or capacity.
- A style is chosen from the hull's own family, as the game classes it, not by the company's progress.
- Each style is look-developed first (`art-source/ship-deck-lookdev` is the low-tech precedent):
  ImageGen concepts over the game's own framing, then a geometry-first kit, then the light model.
- Restoration stays inside a style. A restored low-tech ship never turns midline.

Each style is the interior expression of its tech family (`tech-families.md`); its materials,
light and wear follow that family's visual language.

## Open questions

- The light language for each. Low-tech is caged lamps strung on exposed cable. Midline might be
  recessed strip light; high-tech might be luminous panels.
- Where the hull's family enters generation. The deck generator does not know it yet.
