---
name: authoring-tools
description: Call the headless authoring tools from a shell - list/measure/slice/annotate/export a tileset, render a map-gen comparison, or create visual snapshots - without launching the workbench. Use whenever you need to inspect or change tileset art, authoring documents, or snapshot evidence, and whenever an MCP tool named tileset_* or snapshot_* is unavailable.
---

# Calling the authoring tools

The authoring tools are the headless half of the `layerAuthoring` workbench:
they list and measure raw art sheets, slice them, read and write authoring
documents, export a packed tileset, render a map-gen comparison, and create the
visual-snapshot suites.

**Default to the shell.** One command, one answer, no server:

```bash
tools/authoring.sh tileset_list
```

A call costs about 0.2s. There is nothing to start, nothing to stop, and
nothing that had to be arranged before this session began.

## Why not MCP by default

The same tools are also served over MCP stdio, and that is a fine way to reach
them — but an MCP stdio server has to be **registered before the session that
wants it starts**. A session that discovers mid-task that it needs to look at a
tileset cannot go back and register one, and a long-running HTTP server would
only move the same "must already be running" problem behind a port number.

So the shell is the default and MCP is the optimization. If `tileset_*` tools
are already in your tool list, use them; they are the same code and the same
arguments. Otherwise use `tools/authoring.sh`.

To register the MCP server for *future* sessions:

```bash
gradlew.bat installAuthoringTools
```

That prints the `.mcp.json` snippet with absolute paths filled in. Neither the
launcher nor `.mcp.json` is checked in, because the launcher embeds an absolute
classpath — each checkout registers its own.

## Usage

```
tools/authoring.sh <tool> [arguments] [--json]
tools/authoring.sh --list
tools/authoring.sh --describe <tool>
```

- Arguments are **one JSON object**. Omit it for a tool that takes none.
- `--json` prints the structured result instead of the text summary. The text
  is written for you to read; the structured payload is for piping into `jq`.
- Exit status: `0` succeeded, `1` the tool reported a failure, `2` you got the
  command line wrong.
- `tools/authoring.cmd` is the same thing for `cmd.exe`. The first call of
  either generates the launcher, which takes a few seconds; after that it is
  immediate.

`--list` is the authority on what exists. As of writing:

| Tool | What it does |
|---|---|
| `tileset_list` | Every sheet in the project with its state, work-to-do first. **Start here.** |
| `tileset_measure` | Size, alpha, per-threshold piece counts, and a drafted seed. Writes nothing. |
| `tileset_read_document` | One sheet's authoring document — settings, note, blocks, pieces. |
| `tileset_write_document` | Replace that document. Not merged: read, edit, write back. |
| `tileset_slice` | Find pieces at a threshold, carrying existing annotations onto them. |
| `tileset_export` | Pack the kept pieces into an atlas and write the tileset the game loads. |
| `tileset_map_preview` | Render a generated map as it ships and with this art substituted. |
| `snapshot_list_suites` | The deterministic visual-evidence suites in this checkout. |
| `snapshot_create` | Render suites to PNG/GIF. |

Run `--describe <tool>` before first use of one. The description is written for
a reader who has never seen this repository and says what the tool *writes*,
which is the part worth knowing before calling it.

## Passing arguments

Three forms, because quoting JSON on a command line is a real hazard:

```bash
# inline - fine for small objects in bash
tools/authoring.sh tileset_measure '{"sheet":"urban-tileset","gridCols":10,"gridRows":10}'

# from a file - use this for anything with nested structure
tools/authoring.sh tileset_write_document @/tmp/document.json

# from stdin
cat document.json | tools/authoring.sh tileset_write_document -
```

**In PowerShell, prefer `@file`.** PowerShell rewrites quotes on their way to a
native executable, and a mangled JSON argument does not always fail loudly — it
can parse into something that means something else. Writing the object to a
file and passing `@path` sidesteps the whole class of problem. The Bash tool
with single quotes is also safe.

`tileset_write_document` replaces a whole document. Always
`tileset_read_document` first, edit that object, and write it back; a partial
document silently drops every annotation it omits.

## Read before you write

Every tool that changes something takes `apply` (default `false`) or is
explicitly named as a writer. Look at the preview first — that is what the
default is for.

Two things are genuinely destructive and worth pausing over:

- **`tileset_export` writes into `mod/`**, which is what ships. Exporting over
  a shipped tileset rewrites ids and atlas coordinates that `GenMappingRegistry`
  references. Read the sheet's note first; a sheet that must not be re-exported
  says so there.
- **`tileset_write_document` replaces the record of judgements** that cannot be
  re-derived from the pixels — what a sheet is for, what each piece is, what
  the next reader needs warning about.

## Ingesting a new sheet

That is a procedure of its own, with a judged half these tools deliberately do
not perform. Use the `ingest-tileset` skill.
