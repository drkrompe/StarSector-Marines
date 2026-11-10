package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;

/**
 * What a stood-up building's walls are dressed in ({@link BuildingFaces}; {@code voids.md}, buildings in depth, wall
 * styles): its material and trim, chosen by what it was built as and the world it stands on; the rhythm of its
 * facade, laid out by its own number; and how its walls break. GL-free: every choice here is a number the renderer
 * paints ({@code FacePainter}).
 *
 * <p>The faces stand one to two cells tall at play zoom under the 120 m eye, so the language is bold and
 * low-frequency: a material's tone and course, a dark plinth at the foot, a trim line at the eave, each storey and
 * each corner, windows in their building's own rhythm, and the world's weather at the foot (snow, sand, ash, sludge).
 */
final class WallDress {

    private WallDress() { }

    /** A wall's surface pattern, drawn as lines across the face. */
    enum Course {
        /** Rendered plaster or adobe: no lines, only the tone. */
        PLAIN,
        /** Stone or block coursing: horizontal joints, three a storey. */
        BLOCK,
        /** Cladding panels or ceramic: a seam at every cell and every storey. */
        PANEL,
        /** Corrugated sheet: vertical ribs, four a cell. */
        RIB
    }

    /** What a world leaves on a wall's foot and sills. */
    enum Weather { NONE, MOSS, SAND, SNOW, ASH, SLUDGE, DUST }

    /** What one storey of one cell's length of wall shows. */
    enum Bay {
        /** A window with a frame and a sill. */
        WINDOW,
        /** A tall civic window, most of the storey. */
        TALL,
        /** A firing or shield slit under a concrete hood. */
        SLIT,
        /** A sealed town's small square port. */
        PORT,
        /** A shopfront: glass most of the cell, an awning over it. */
        SHOP,
        /** A works' high glazed band. */
        CLERESTORY,
        /** A louvred vent. */
        VENT,
        /** Wall. */
        BLIND
    }

    /**
     * A wall's material: its colour, its trim's (plinth, eave, storey lines, corners, frames), its glass's and its
     * doors', its course, how deep its plinth and eave bands are (metres), the weather its world leaves on it, how
     * many of its cells are stained from the top (a toxic world's run-off), and whether its doors are marked with
     * hazard stripes. UNTUNED.
     */
    record Material(float[] wall, float[] trim, float[] glass, float[] door, Course course, float plinthMeters,
                    float eaveMeters, Weather weather, float stain, boolean hazard) { }

    /**
     * How a building's windows and fittings are cut: a window's width and height as shares of its cell and storey,
     * whether windows carry shutters, and a downpipe every {@code pipeEvery} cells from {@code phase} (0 none). Where
     * the windows stand is the battle's own ({@link BuildingFaces#storeyFeature}): every pane on a face is over an
     * opening sight and fire pass.
     */
    record Rhythm(int phase, float windowWidth, float windowHeight, boolean shutters, int pipeEvery) { }

    /** The weather a world leaves at a wall's foot. */
    static Weather weather(SurfacePalette palette) {
        if (palette == null) return Weather.NONE;
        switch (palette) {
            case VERDANT: return Weather.MOSS;
            case ARID: return Weather.SAND;
            case FROZEN: return Weather.SNOW;
            case VOLCANIC: return Weather.ASH;
            case TOXIC: return Weather.SLUDGE;
            case IRRADIATED: case ROCK: return Weather.DUST;
            default: return Weather.NONE;
        }
    }

    /** The colour a world's weather lays at the foot of a wall and on its sills. UNTUNED. */
    static float[] weatherColour(Weather weather) {
        switch (weather) {
            case MOSS: return rgb(.30f, .36f, .22f);
            case SAND: return rgb(.80f, .68f, .50f);
            case SNOW: return rgb(.92f, .94f, .97f);
            case ASH: return rgb(.20f, .19f, .19f);
            case SLUDGE: return rgb(.52f, .55f, .24f);
            case DUST: return rgb(.62f, .57f, .48f);
            default: return rgb(0f, 0f, 0f);
        }
    }

    /** How high a world's weather climbs a wall's foot, metres. UNTUNED. */
    static float weatherMeters(Weather weather) {
        switch (weather) {
            case SNOW: return .9f;
            case SAND: return .7f;
            case ASH: return .6f;
            case SLUDGE: return .8f;
            case MOSS: return .5f;
            case DUST: return .5f;
            default: return 0f;
        }
    }

    /**
     * A building's wall material from what it was built as, its town's habitat ({@link BuildingFaces.Look}) and its
     * world. A habitat decides the construction (an open town's plaster, brick and sheet; an insulated town's clad
     * panels; a sealed town's ceramic; a shielded town's poured concrete); the world decides its tones and weather;
     * the building's own number {@code v} in [0, 1) picks among its kind's colours, so a street is not one colour.
     */
    static Material material(BuildingKind kind, BuildingFaces.Look look, SurfacePalette palette, float v) {
        Weather weather = weather(palette);
        BuildingKind k = kind == null ? BuildingKind.OTHER : kind;
        boolean works = k == BuildingKind.INDUSTRIAL;
        boolean military = k == BuildingKind.FORTIFIED;
        float stain = palette == SurfacePalette.TOXIC ? .35f : 0f;
        switch (look) {
            case SEALED: {
                // Ceramic panels on a frame, the works in a darker steel-grey shell, the garrison's in olive.
                float[] wall = works ? rgb(.62f, .64f, .66f) : military ? rgb(.52f, .55f, .48f)
                        : pick(v, rgb(.86f, .87f, .86f), rgb(.82f, .84f, .86f), rgb(.88f, .86f, .80f));
                return new Material(wall, rgb(.46f, .48f, .50f), rgb(.12f, .20f, .24f), rgb(.40f, .42f, .44f),
                        Course.PANEL, 1.0f, .5f, weather, stain, works || military);
            }
            case SHIELDED: {
                // Poured concrete, board-marked, heavy plinth; hazard paint at every door.
                float[] wall = works ? rgb(.50f, .50f, .48f) : military ? rgb(.46f, .47f, .43f)
                        : pick(v, rgb(.60f, .60f, .57f), rgb(.56f, .56f, .54f), rgb(.62f, .60f, .55f));
                return new Material(wall, rgb(.36f, .36f, .35f), rgb(.10f, .13f, .14f), rgb(.32f, .33f, .33f),
                        Course.BLOCK, 1.6f, .7f, weather, stain, true);
            }
            case INSULATED: {
                // Timber-clad homes in deep reds, ochres and blues; clad grey-blue works; white trim against the snow.
                float[] wall = works ? rgb(.42f, .48f, .54f) : military ? rgb(.44f, .46f, .44f)
                        : k == BuildingKind.CIVIC || k == BuildingKind.MEDICAL ? rgb(.74f, .72f, .66f)
                        : pick(v, rgb(.55f, .22f, .18f), rgb(.70f, .52f, .24f), rgb(.28f, .36f, .46f),
                        rgb(.36f, .40f, .30f));
                return new Material(wall, rgb(.88f, .88f, .85f), rgb(.14f, .18f, .22f), rgb(.30f, .22f, .16f),
                        works || military ? Course.PANEL : Course.RIB, .8f, .5f, weather, 0f, military);
            }
            default:
                return openMaterial(k, palette, v, weather, stain);
        }
    }

    /** An open town's walls: plaster homes, brick or sheet works, concrete forts, stone civic halls, by world. */
    private static Material openMaterial(BuildingKind k, SurfacePalette palette, float v, Weather weather,
                                         float stain) {
        boolean arid = palette == SurfacePalette.ARID;
        boolean dark = palette == SurfacePalette.VOLCANIC;
        boolean stone = palette == SurfacePalette.ROCK || palette == SurfacePalette.IRRADIATED;
        switch (k) {
            case INDUSTRIAL: {
                if (arid || dark) {
                    float[] wall = dark ? rgb(.36f, .36f, .38f) : pick(v, rgb(.66f, .60f, .50f), rgb(.58f, .46f, .36f));
                    return new Material(wall, rgb(.28f, .28f, .29f), rgb(.16f, .20f, .22f), rgb(.42f, .42f, .40f),
                            Course.RIB, .6f, .4f, weather, stain, true);
                }
                float[] wall = pick(v, rgb(.56f, .32f, .25f), rgb(.50f, .36f, .30f), rgb(.56f, .56f, .54f));
                return new Material(wall, rgb(.36f, .33f, .31f), rgb(.16f, .20f, .22f), rgb(.42f, .42f, .40f),
                        v < .67f ? Course.BLOCK : Course.RIB, .7f, .5f, weather, stain, true);
            }
            case FORTIFIED: {
                float[] wall = arid ? rgb(.66f, .60f, .50f) : dark ? rgb(.40f, .40f, .40f) : rgb(.54f, .55f, .51f);
                return new Material(wall, rgb(.34f, .34f, .32f), rgb(.10f, .12f, .12f), rgb(.34f, .36f, .32f),
                        Course.PANEL, 1.4f, .7f, weather, stain, true);
            }
            case CIVIC: {
                float[] wall = arid ? rgb(.84f, .76f, .62f) : dark ? rgb(.46f, .44f, .42f) : rgb(.78f, .76f, .70f);
                return new Material(wall, rgb(.90f, .88f, .84f), rgb(.14f, .18f, .24f), rgb(.30f, .22f, .16f),
                        Course.BLOCK, 1.0f, .8f, weather, stain, false);
            }
            case MEDICAL: {
                return new Material(rgb(.88f, .90f, .91f), rgb(.30f, .58f, .60f), rgb(.16f, .22f, .28f),
                        rgb(.60f, .64f, .66f), Course.PANEL, .7f, .5f, weather, stain, false);
            }
            default: {
                boolean shop = k == BuildingKind.COMMERCIAL;
                float[] wall;
                Course course = Course.PLAIN;
                if (arid) {
                    // Adobe and sandstone render.
                    wall = pick(v, rgb(.82f, .66f, .48f), rgb(.76f, .58f, .42f), rgb(.86f, .76f, .60f),
                            rgb(.72f, .52f, .38f));
                } else if (dark) {
                    // Basalt block, the joints picked out.
                    wall = pick(v, rgb(.40f, .38f, .37f), rgb(.46f, .42f, .38f), rgb(.34f, .34f, .36f));
                    course = Course.BLOCK;
                } else if (stone) {
                    wall = pick(v, rgb(.64f, .62f, .58f), rgb(.58f, .57f, .55f), rgb(.68f, .64f, .56f));
                    course = Course.BLOCK;
                } else {
                    // Rendered plaster in a street's worth of colours.
                    wall = pick(v, rgb(.88f, .84f, .74f), rgb(.84f, .70f, .58f), rgb(.74f, .80f, .76f),
                            rgb(.90f, .86f, .80f), rgb(.80f, .62f, .52f));
                }
                float[] trim = arid ? rgb(.94f, .90f, .80f) : dark ? rgb(.66f, .62f, .56f) : rgb(.95f, .94f, .90f);
                float[] door = pick(v * 7f % 1f, rgb(.36f, .22f, .14f), rgb(.20f, .34f, .40f), rgb(.42f, .16f, .14f));
                return new Material(wall, trim, rgb(.16f, .22f, .30f), door, course, shop ? .5f : .6f, .45f,
                        weather, stain, false);
            }
        }
    }

    /**
     * How a building's windows are cut, from what it was built as and its own number {@code v} in [0, 1): homes'
     * windows narrow to broad and some shuttered in an open town, shops' broad, civic halls' tall, forts' slits;
     * works and a sealed town's blocks piped every few cells. UNTUNED.
     */
    static Rhythm rhythm(BuildingKind kind, BuildingFaces.Look look, float v) {
        int phase = (int) (v * 997f) % 3;
        boolean sealed = look == BuildingFaces.Look.SEALED;
        int pipes = sealed ? 3 + phase : 0;
        BuildingKind k = kind == null ? BuildingKind.OTHER : kind;
        float w = v * 13f % 1f;
        switch (k) {
            case INDUSTRIAL: return new Rhythm(phase, .8f, .5f, false, sealed ? 3 : 5);
            case FORTIFIED: return new Rhythm(phase, .5f, .14f, false, pipes);
            case CIVIC: return new Rhythm(phase, .4f, .72f, false, pipes);
            case COMMERCIAL: return new Rhythm(phase, .5f + .14f * w, .5f, false, pipes);
            case MEDICAL: return new Rhythm(phase, .6f, .45f, false, pipes);
            default: {
                boolean shutters = look == BuildingFaces.Look.OPEN && v * 31f % 1f < .45f;
                return new Rhythm(phase, .34f + .24f * w, .44f + .1f * (v * 7f % 1f), shutters, pipes);
            }
        }
    }

    /**
     * What one storey of one cell's length of wall shows above its ground storey. A window stands over a window, on
     * a building whose upper storeys have them ({@link BuildingFaces#storeyFeature}), cut in its kind's and
     * habitat's way: a sealed town's port, a civic hall's tall window, a slit over a slit. A works is blind but for a
     * glazed band under its eave, high over any fire; a sealed town's blind wall carries a vent every few cells.
     *
     * @param ground       what stands at the cell's foot
     * @param upperWindows whether the building's upper storeys have windows ({@link BuildingFaces#upperWindows})
     * @param along        the cell's place along its wall, cells (its x along a wall running east, its y north)
     * @param storey       the storey, 1 the first above the ground
     * @param storeys      the wall's storeys
     */
    static Bay upper(Rhythm rhythm, BuildingKind kind, BuildingFaces.Look look, BuildingFaces.Segment ground,
                     boolean upperWindows, int along, int storey, int storeys) {
        BuildingKind k = kind == null ? BuildingKind.OTHER : kind;
        if (k == BuildingKind.INDUSTRIAL && storey == storeys - 1) return Bay.CLERESTORY;
        BuildingFaces.Segment above = BuildingFaces.storeyFeature(ground, storey, upperWindows);
        if (above == BuildingFaces.Segment.PANE) return Bay.SLIT;
        if (above == BuildingFaces.Segment.WINDOW) return style(k, look, false);
        boolean vent = look == BuildingFaces.Look.SEALED && Math.floorMod(along + rhythm.phase() + storey, 4) == 0;
        return vent ? Bay.VENT : Bay.BLIND;
    }

    /**
     * What the ground storey shows where the map has no door: a window where the battle has one, cut in its kind's
     * and habitat's way (an open town's shop has its front), a slit pane as a slit, and otherwise wall, a sealed
     * town's with a vent every few cells.
     */
    static Bay ground(Rhythm rhythm, BuildingKind kind, BuildingFaces.Look look, BuildingFaces.Segment ground,
                      int along) {
        BuildingKind k = kind == null ? BuildingKind.OTHER : kind;
        if (ground == BuildingFaces.Segment.PANE) return Bay.SLIT;
        if (ground == BuildingFaces.Segment.WINDOW) return style(k, look, true);
        boolean vent = look == BuildingFaces.Look.SEALED && Math.floorMod(along + rhythm.phase(), 4) == 0;
        return vent ? Bay.VENT : Bay.BLIND;
    }

    /** A window cut in its building's way. */
    private static Bay style(BuildingKind k, BuildingFaces.Look look, boolean groundStorey) {
        if (k == BuildingKind.FORTIFIED || look == BuildingFaces.Look.SHIELDED) return Bay.SLIT;
        if (look == BuildingFaces.Look.SEALED) return Bay.PORT;
        if (k == BuildingKind.CIVIC) return Bay.TALL;
        if (groundStorey && k == BuildingKind.COMMERCIAL && look == BuildingFaces.Look.OPEN) return Bay.SHOP;
        return Bay.WINDOW;
    }

    /** Whether a downpipe runs up a cell's length of wall, at its start. */
    static boolean pipe(Rhythm rhythm, int along) {
        return rhythm.pipeEvery() > 0 && Math.floorMod(along + rhythm.phase(), rhythm.pipeEvery()) == 0;
    }

    /**
     * How hurt a wall cell is, in [0, 1], from its hit points against the most it has been seen to hold. A map seeds
     * walls from 80 to 400 hit points, so a fixed scale reads a light wall as damaged before a shot is fired.
     */
    static float hurt(int hp, int fullHp) {
        if (fullHp <= 0) return 0f;
        return Math.max(0f, Math.min(1f, 1f - hp / (float) fullHp));
    }

    /**
     * Where a wall whose roof has fallen in breaks off, as a share of its full height, at {@code t} along its cell:
     * the top storeys gone, a ragged edge over the ones left. A one-storey wall keeps half to three quarters.
     */
    static float brokenTop(int cell, int storeys, float t) {
        float keep = storeys <= 1 ? .5f : (storeys - 1f) / storeys * .8f;
        float lo = Math.max(.25f, keep - .18f);
        return lo + (keep - lo + .12f) * ragged(cell, t);
    }

    /**
     * How high a razed building's broken shell stands at {@code t} along a cell, as a share of a storey: its corners
     * nearly a storey, the rest knee to chest high and ragged.
     */
    static float shellTop(int cell, boolean corner, float t) {
        float base = corner ? .7f : .12f + .4f * BuildingFaces.hash(cell, 61);
        return Math.min(1f, base + .22f * ragged(cell, t));
    }

    /** A ragged edge's height in [0, 1) at {@code t} along a cell: three teeth a cell, each its own height. */
    static float ragged(int cell, float t) {
        int tooth = Math.max(0, Math.min(2, (int) (t * 3f)));
        return BuildingFaces.hash(cell * 3 + tooth, 63);
    }

    private static float[] pick(float v, float[]... options) {
        return options[Math.max(0, Math.min(options.length - 1, (int) (v * options.length)))];
    }

    private static float[] rgb(float r, float g, float b) {
        return new float[]{r, g, b};
    }
}
