@echo off
rem Serve the authoring tools over MCP stdio. This is what .mcp.json should
rem point at, not the generated launcher underneath it.
rem
rem It exists for the same reason tools\authoring.cmd does: the generated
rem launcher runs whatever was compiled last. For the one-shot CLI that means a
rem single stale call; here it is worse, because an MCP stdio server is spawned
rem once and then answers for the whole session, so one stale spawn serves stale
rem tools until the client is restarted. Building first at least guarantees the
rem session starts current.
rem
rem It cannot guarantee more than that. A server already running holds its
rem classes loaded, and no wrapper can reload them - after changing tool code,
rem restart the MCP server, or use tools\authoring.cmd, which rebuilds per call.
rem
rem STDOUT IS THE PROTOCOL. Every line below that could print sends its output
rem to stderr, which MCP clients surface as server logs.
setlocal
set "ROOT=%~dp0.."
set "LAUNCHER=%ROOT%\build\authoring\starsector-authoring-mcp.cmd"

call "%ROOT%\gradlew.bat" -p "%ROOT%" installAuthoringTools --quiet 1>&2
if errorlevel 1 (
    echo starsector-authoring: the build failed, so the compiled classes are of 1>&2
    echo unknown age. Refusing to serve stale tools - fix the build and restart. 1>&2
    exit /b 3
)

call "%LAUNCHER%"
exit /b %ERRORLEVEL%
