@echo off
rem Call one authoring tool and exit.
rem
rem   tools\authoring.cmd --list
rem   tools\authoring.cmd tileset_list
rem   tools\authoring.cmd tileset_measure "{\"sheet\":\"urban-tileset\"}"
rem
rem See tools/authoring.sh for why this exists, and for why the freshness build
rem below runs on every call rather than only when the launcher is missing. In
rem short: the generated launcher runs whatever was compiled last, that has
rem already silently destroyed hand-authored work once, and Gradle answers the
rem staleness question correctly in under a second against a warm daemon.
setlocal
set "ROOT=%~dp0.."
set "LAUNCHER=%ROOT%\build\authoring\authoring.cmd"

if not exist "%LAUNCHER%" (
    rem Only on a cold checkout, where silence would look like a hang.
    echo building the authoring tools for the first time in this checkout... 1>&2
)

rem Build output to stderr: stdout belongs to the tool result.
call "%ROOT%\gradlew.bat" -p "%ROOT%" installAuthoringTools --quiet 1>&2
if errorlevel 1 (
    echo authoring tools: the build failed, so the compiled classes are of unknown 1>&2
    echo age. Refusing to call a tool with them - fix the build and try again. 1>&2
    exit /b 3
)

call "%LAUNCHER%" %*
exit /b %ERRORLEVEL%
