# Authoring MCP server

Status: IN PROGRESS

Written: 2026-08-28

Read `moddable-tilesets-nouns.md` before implementing this story. The authoring
pipeline this exposes is described there under "Authoring: raw sheet to packed
atlas"; nothing here amends that model, and no new noun is introduced.

## Problem

The ingest procedure in `.claude/skills/ingest-tileset/SKILL.md` is written for a
model to follow, and a model cannot follow it. Its measured half needs Python
with NumPy and Pillow; its annotation half needs a Swing window. So a session
that is asked to ingest a sheet can write a seed by hand — guessing the numbers
the measurement would have supplied — and then has to stop and ask a person to
open the workbench.

That is the wrong split. The judged half of the procedure is exactly the half a
model is good at: what the sheet is for, what each piece is, which pieces form a
block, what the next reader needs warning about. The mechanical half is the part
that needs a tool. Today the tool is reachable only by hand and the judgement is
reachable only by a model, and they cannot meet.

Everything the mechanical half actually does is already headless Java:
`TilesetLibrary` scans, `SheetSlicer` slices, `TilesetExport` packs and writes,
`TilesetMapPreview` renders a comparison without an OpenGL context, and
`SnapshotRunner` renders the visual-evidence catalog. The Swing page is a view
over that code, not the code itself.

## Scope

Expose the authoring pipeline over the Model Context Protocol so a session can
run the mechanical half itself and spend its own effort on the judged half.

- A generic MCP protocol host in `:layer-authoring`, with a discoverable tool
  SPI mirroring the existing `AuthoringPageProvider` / `SnapshotSuite` pattern.
- Mod-domain tools in root test sources covering list, measure, read document,
  write document, slice, split on grid, export, and map preview.
- Snapshot-catalog tools beside the catalog they drive.
- A launcher the user registers in `.mcp.json`.

Out of scope: driving a running workbench window, editing `GenMappingRegistry`
mappings, and any tool that would write outside the paths the workbench already
writes.

## Decisions

### Headless entry point, not embedded in the running workbench

**Decision: a separate headless entry point sharing the same domain code.**

The tempting version is an MCP server inside the Swing process, so a session can
drive a live editor and see what the operator sees. It is the wrong first cut for
four reasons.

1. **Lifetime.** An MCP stdio server is spawned and owned by the client. An
   embedded server cannot be spawned — it would need HTTP or SSE on a port, the
   port discovered somehow, and the workbench already open. That makes every
   tool call conditional on a human having a window up, which is precisely the
   dependency this story exists to remove.
2. **Two writers.** The page holds unsaved edits in Swing models. A tool that
   writes an authoring document while the page holds a dirty copy of it is a
   lost update, and the page has no reload path. Keeping the server outside the
   editor makes the contract "the server reads and writes files, the editor
   reopens them", which is a merge the operator can see.
3. **Testability.** A headless server is a pure function of the repository and
   its arguments, and can be exercised in JUnit with no display and no EDT.
4. **The GUI-only capability is not actually GUI-only.** "Show me what this
   looks like in a map" is already answered better headlessly:
   `TilesetMapPreview` renders baseline and candidate to images without a
   window, and a session can read the resulting PNG directly. A screenshot of a
   Swing panel would be strictly worse.

Both is the eventual answer, not the first one. Embedding becomes worth its cost
when there is a live-editor capability with no headless equivalent — a selection
to inspect, an operator pointing at a piece. Until then the shared domain layer
is the integration, and it is the one that must not be duplicated: the tools
extracted here (`TilesetOperations`) are called by the page as well, so there is
one export path rather than two.

### Transport: stdio

**Decision: stdio, newline-delimited JSON-RPC.**

The server is a local developer tool acting on a local checkout for a single
session. Stdio gives that process lifetime, isolation, and authorization for
free: the client spawns it, owns it, and reaps it, and there is no port, no
listener reachable from anything else on the machine, and no credential to
invent. HTTP/SSE buys multi-client and long-lived attachment, neither of which
this needs, and costs an auth story this should not have.

The stdio stream must carry nothing but protocol, which rules out invoking it
through Gradle: `JavaExec` output passes through the daemon, and daemon stdin
forwarding is unreliable. A Gradle task therefore *installs* a launcher rather
than *being* the launcher — it resolves the tool classpath into an argument file
and writes a script that execs `java` directly. See "Registering it" below.

### Protocol: hand-rolled JSON-RPC, no new dependency

**Decision: hand-rolled over `org.json`, which is already on the tool classpath.**

The official SDK was checked rather than assumed. It resolves from this
project's configured repositories (`mavenCentral()`); `io.modelcontextprotocol.sdk:mcp:2.0.1`
resolves to thirteen artifacts:

```
mcp, mcp-core, mcp-json-jackson3, json-schema-validator,
tools.jackson.core:jackson-core / jackson-databind,
jackson-dataformat-yaml, jackson-annotations, snakeyaml-engine,
reactor-core, reactive-streams, slf4j-api, ethlo itu
```

It is available. It is still the wrong dependency here:

- The classpath it would join is `sourceSets.test.runtimeClasspath` of the root
  project, which is shared by `:test`, `createSnapshots`, and `layerAuthoring`.
  Thirteen artifacts including Reactor and a Jackson 3 preview line is a wide
  blast radius for one tool entry point.
- The whole domain layer already speaks `org.json`, from the game's own
  `json.jar`. Using the SDK means marshalling every payload between Jackson
  trees and `JSONObject` at the boundary.
- The subset a tools-only server needs is small and closed: `initialize`,
  `notifications/initialized`, `ping`, `tools/list`, `tools/call`. Framing is
  one JSON object per line.
- A dispatcher that maps one `JSONObject` request to one `JSONObject` response
  is directly testable. A Reactor-based session is not, without a transport
  harness.

The cost is owning protocol correctness. It is bounded by the request set above
and pinned by tests that assert the contract rather than the implementation.

## Constraints

- Tool and test infrastructure must never enter the shipped mod jar. Generic
  host code lives in `:layer-authoring`; mod-domain tools live in root test
  sources; nothing is added to `implementation` or the shadow configuration.
- No new external dependency, on any configuration.
- Java 17 bytecode; no language feature newer than 17.
- Nothing writes under `mod/` except through the export path the workbench
  already uses (`TilesetExport.write` plus the generated catalog card).
  `RawArtStaysOutOfModTest` continues to hold.
- A sheet name arriving from a model is not a path. Every write target is
  derived from a validated name, and a caller-supplied output directory must
  resolve inside the project root.
- The export path must not be duplicated. It is extracted from the Swing page
  and the page delegates to the extraction.

## Acceptance

- [x] `initialize` negotiates a protocol version and advertises a tools
  capability; `notifications/initialized` produces no response line.
- [x] `tools/list` returns every discovered tool with a JSON Schema for its
  arguments.
- [x] An unknown method answers JSON-RPC error `-32601`; a malformed request
  line answers `-32700` without terminating the loop.
- [x] A tool that throws reports `isError` in its result rather than killing
  the session.
- [x] The tileset tools cover list, measure, read, write, slice, split, export, and
  map preview, and the snapshot tools cover list and create.
- [x] A sheet name containing a path separator or `..` is refused.
- [x] A caller-supplied output directory outside the project root is refused.
- [x] `:test` and `:layer-authoring:test` stay green.
- [x] The tool classpath additions are absent from the shadow jar.

## Plan

1. Generic host in `:layer-authoring`: `McpTool`, `McpToolProvider`,
   `McpToolContext`, `McpToolResult`, `McpToolCatalog`, `McpServer` (dispatch),
   `McpStdioServer` (loop).
2. Extract `TilesetOperations` from `TilesetAuthoringPage` — export and
   stand-in bindings — and delegate the page to it.
3. Add `SheetMeasurement`, the Java equivalent of `measure_sheet.py`'s measured
   half, so ingestion does not need Python.
4. Mod-domain `TilesetMcpToolProvider`; snapshot `SnapshotMcpToolProvider`.
5. `AuthoringMcpCli` (stdio server) and `AuthoringToolCli` (one call, one
   answer) beside `CreateSnapshotsCli`, and an `installAuthoringTools` Gradle
   task that writes both launchers.
6. Contract tests for the protocol and for each tool's request/response shape.

## Reaching the tools

Two front doors onto one catalog. The shell is the default:

```bash
tools/authoring.sh tileset_list
```

MCP registration is the optimization, and it is only available to a session
that was started with the server registered. That is the whole reason the
one-shot CLI exists: an MCP stdio server has to be running before the session
that wants it begins, so a session that discovers mid-task that it needs to
look at a tileset cannot reach one. An HTTP server with a long life would only
move the same constraint behind a port number.

## Registering it

Once, and again after a dependency change:

```powershell
gradlew.bat installAuthoringTools
```

The task prints the snippet with its own absolute path filled in. In the
repository's `.mcp.json`:

```json
{
  "mcpServers": {
    "starsector-authoring": {
      "command": "C:/Users/Dillon/IdeaProjects/starsectormarines/build/authoring/starsector-authoring-mcp.cmd",
      "args": []
    }
  }
}
```

Neither the launcher nor `.mcp.json` is checked in: the launcher embeds an
absolute resolved classpath, so it is per-checkout, and each worktree therefore
registers its own. `build/` is cleaned, so a `gradlew.bat clean` is also a
"re-run the install task".

The launchers bake in the toolchain's own `java` rather than reading
`%JAVA_HOME%`: they are invoked from whatever shell a session happens to hold,
and a launcher that works only in a prepared shell is the setup step this entry
point exists to remove. The classpath is written with forward slashes
because the JDK treats a backslash inside a quoted `@argfile` entry as an
escape, which silently strips every separator out of a native Windows path and
produces a bare `ClassNotFoundException`.
