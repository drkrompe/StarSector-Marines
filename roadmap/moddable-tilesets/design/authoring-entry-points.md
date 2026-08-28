# Authoring entry points

Status: SHIPPED

Written: 2026-08-28

Updated: 2026-08-28 — why the checked-in wrappers build before every call

Read `moddable-tilesets-nouns.md` first. This doc holds the reasoning behind
*how* the authoring pass is reached, not what it does; the pass itself and its
standing laws live in the noun doc under "Authoring: raw sheet to packed atlas".

## Why the tools are not inside the workbench

The tempting design is an MCP server inside the Swing process, so a session can
drive a live editor and see what the operator sees. It is the wrong first cut.

- **Lifetime.** An MCP stdio server is spawned and owned by its client. An
  embedded one cannot be spawned: it would need a listener on a port, the port
  discovered somehow, and the workbench already open — making every tool call
  conditional on a human having a window up, which is the dependency the
  headless entry point exists to remove.
- **Two writers.** The page holds unsaved edits in Swing models and has no
  reload path, so a tool writing a document the page holds dirty is a lost
  update. Keeping the tools outside the editor makes the contract "the tools
  read and write files, the editor reopens them" — a merge the operator can see.
- **Testability.** A headless entry point is a pure function of the repository
  and its arguments, exercisable with no display and no event thread.
- **The GUI-only capability is not GUI-only.** "Show me what this looks like on
  a map" is answered better without a window: the map preview renders baseline
  and candidate to images a session can read directly, and a screenshot of a
  Swing panel would be strictly worse.

Both is the eventual answer, not the first one. Embedding earns its cost when
there is a live-editor capability with no headless equivalent — a selection to
inspect, an operator pointing at a piece. Until then the shared domain layer is
the integration, and it is the thing that must not be duplicated.

## Why there are two front doors, and which is the default

**The shell one-shot is the default; MCP registration is the optimization.**

An MCP stdio server has to be registered before the session that wants it
starts. A session that discovers mid-task that it needs to look at a tileset
cannot go back and register one, and a long-lived HTTP server would only move
the same "must already be running" constraint behind a port number. One process,
one call, one answer, no lifecycle is therefore the form that is always
available; a session that already has the server registered should use it, since
it is the same catalog and the same arguments.

Neither front door may become a second implementation. Both resolve the same
discovered tool catalog and call the same tools; what differs is presentation —
protocol frames on one side, a text summary and an exit status on the other.

## Why stdio, and why a Gradle task installs a launcher rather than being one

The server is a local developer tool acting on a local checkout for a single
session. Stdio gives process lifetime, isolation and authorization for free: the
client spawns it, owns it and reaps it, with no port, no listener reachable from
anything else on the machine, and no credential to invent. HTTP/SSE buys
multi-client and long-lived attachment, neither of which this needs, and costs
an auth story it should not have.

The stdio stream must carry nothing but protocol, which rules out invoking it
through Gradle: `JavaExec` output passes through the daemon, whose stdin
forwarding is unreliable and whose stdout would put build chatter on the wire.
The one-shot CLI avoids Gradle for a duller reason — a call that pays for a
daemon handshake is not a command anyone will reach for. So the task *installs*
launchers rather than being one, resolving the tool classpath into an argument
file and writing scripts that exec `java` directly. They are generated rather
than checked in because they embed an absolute classpath, so each checkout and
each worktree registers its own.

## Why the checked-in wrapper builds before every call

A generated launcher execs `java -classpath build/classes/java/test`. It
therefore runs whatever was compiled last, and there is nothing in its output —
by construction, since it is one `java` line — to say how old that is.

This is not a theoretical hazard. A `tileset_slice` call made immediately after
a new safety guard was merged ran pre-guard classes; the guard did not fire, and
it destroyed a hand-authored hundred-cell cut and its block-slot assignments.
Run against current classes the same call correctly refuses and writes nothing.
The failure mode is the bad one on every axis: silent, indistinguishable from a
bug in the tool, unattended, and worst precisely when it matters most — right
after a change, which is exactly when someone reaches for the tool.

So `tools/authoring.sh` and `tools/authoring.cmd` run `installAuthoringTools`
unconditionally and exec the launcher only if it succeeded. Three alternatives
were weighed and rejected:

- **A staleness check in the wrapper.** Attractive because it would keep the
  fast path fast, and wrong because a shell-side comparison of source and class
  mtimes cannot see a deleted source, a changed resource, a new dependency or an
  edited `build.gradle` — and every case it gets wrong is silently the original
  bug again. It would be a second, worse implementation of Gradle's up-to-date
  check, competing with one that is already correct.
- **Warn instead of fix.** A line on stderr is invisible in a piped call, and
  the damaging case was unattended.
- **Stamp the launcher and compare.** Real defence, but it makes a stale call
  *loud* rather than impossible, and the freshness build is cheap enough that
  there is no reason to settle for loud.

The cost was measured rather than assumed: against a warm daemon an up-to-date
`installAuthoringTools` is about 0.7s, so a call goes from ~0.15s to ~0.85s.
Under a second is still "one command, one answer"; correctness beats latency for
a tool whose whole job is rewriting authored files. There is deliberately no
bypass switch, because a named bypass is a hole someone will set once and forget.

Two consequences worth keeping:

- **A failed build refuses the call** (exit 3) rather than falling back on the
  last classes that compiled. Classes of unknown age are the thing being
  prevented; a broken build makes their age exactly that.
- **The MCP front door cannot be fixed as completely.** `tools/authoring-mcp.cmd`
  builds before spawning, so a session starts current, but a stdio server holds
  its classes for the whole session and no wrapper can reload them. Changing
  tool code means restarting the server or using the one-shot CLI, and the skill
  says so. This is a real asymmetry between the two front doors, and it is a
  further reason the one-shot is the default. The generated launchers say in
  their own headers that they do not check, so reaching past a wrapper is at
  least an informed choice.

Two Windows details that cost real time to find:

- Inside a quoted `@argfile` entry the JDK treats a backslash as an escape, so a
  native Windows classpath silently loses every separator and the class is then
  "not found" for no visible reason. Write forward slashes.
- The launchers bake in the toolchain's own `java` rather than reading
  `%JAVA_HOME%`. They are invoked from whatever shell a session happens to hold,
  and a launcher that works only in a prepared shell is the setup step this
  entry point exists to remove.

## Why the protocol is hand-rolled rather than the official SDK

**Hand-rolled over `org.json`, which is already on the tool classpath.**

The SDK was checked rather than assumed: `io.modelcontextprotocol.sdk:mcp`
resolves from this project's configured repositories, pulling thirteen artifacts
including Reactor, a Jackson 3 line, a JSON-schema validator and snakeyaml. It
is available, and it is still the wrong dependency here.

- The classpath it would join is the root project's test runtime classpath,
  shared by `:test`, `createSnapshots` and `layerAuthoring`. Thirteen artifacts
  is a wide blast radius for one entry point.
- The whole domain layer already speaks `org.json`, from the game's own
  `json.jar`. The SDK would mean marshalling every payload between Jackson trees
  and `JSONObject` at the boundary.
- The subset a tools-only server needs is small and closed: `initialize`,
  `notifications/initialized`, `ping`, `tools/list`, `tools/call`, one JSON
  object per line.
- A dispatcher from one request object to one response object is directly
  testable. A Reactor-based session is not, without a transport harness.

The cost is owning protocol correctness. It is bounded by that request set and
pinned by tests that assert the contract rather than the implementation.

## Standing rules for a new tool

- **A failing tool is a result, not a protocol error.** "That sheet is not in the
  project" is an answer the caller can act on; only genuine protocol faults
  become JSON-RPC errors. A failure reports its whole cause chain, because the
  caller reads text and cannot ask for a stack trace.
- **A name from a model is not a path.** Every write target is derived from a
  validated sheet name, and a caller-supplied directory must resolve inside the
  project root.
- **Writing previews by default.** A tool that changes a document takes
  `apply`, defaulting to false, so the caller can look before keeping.
- **Tool and test infrastructure never enters the shipped mod jar.** Generic
  host code lives in `:layer-authoring`, mod-domain tools in root test sources.
  `verifyModJarBoundary` enforces it.
