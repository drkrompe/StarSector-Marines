@echo off
rem Call one authoring tool and exit.
rem
rem   tools\authoring.cmd --list
rem   tools\authoring.cmd tileset_list
rem   tools\authoring.cmd tileset_measure "{\"sheet\":\"urban-tileset\"}"
rem
rem See tools/authoring.sh for why this exists. The real launcher is generated
rem because it embeds an absolute classpath; this wrapper generates it on first
rem use so that calling a tool never requires a setup step first.
setlocal
set "ROOT=%~dp0.."
set "LAUNCHER=%ROOT%\build\authoring\authoring.cmd"

if not exist "%LAUNCHER%" (
    echo installing the authoring launchers ^(one-time^)... 1>&2
    rem Build output to stderr: stdout belongs to the tool result.
    call "%ROOT%\gradlew.bat" -p "%ROOT%" installAuthoringTools --quiet 1>&2
    if errorlevel 1 exit /b 1
)

call "%LAUNCHER%" %*
exit /b %ERRORLEVEL%
