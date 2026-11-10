<#
.SYNOPSIS
  CLAUDE.md's integration step for one session branch, with a hard stop on any failure.

.DESCRIPTION
  From the main workspace: require `main` checked out and clean, fetch origin,
  fast-forward local main to origin/main, fast-forward-merge the session branch,
  push main to origin, and only then push main to hub (skipped with a message
  when the Mac is unreachable). Stops with a non-zero exit at the first failure.
  Never force-pushes and never pushes anything but `main`.

  One run at a time: it holds an exclusive lock (`integrate.lock` in the git
  common dir) for the whole run, waiting up to five minutes for another run to
  finish; the OS drops the lock if the process dies. Each fast-forward moves
  `refs/heads/main` first, as a compare-and-swap against the commit it read,
  and only then checks the new tree out, so a run that loses a race to
  anything else moving `main` fails with the workspace untouched. (`git merge
  --ff-only` checks out first and swaps the ref last, which once left the
  workspace holding a session's tree on another session's HEAD.)

  Run it from anywhere inside the repo or one of its worktrees; it locates the
  main workspace itself. -Repo overrides that (used by the failure-path check).

.EXAMPLE
  powershell -File tools/integrate.ps1 session/my-change
#>
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Branch,
    [string]$Repo
)
$ErrorActionPreference = 'Stop'

function Fail([string]$Message) {
    Write-Host "integrate: $Message" -ForegroundColor Red
    exit 1
}

# Run a git command; stop the whole script unless it exits 0. Output is shown, never piped.
function Invoke-Git {
    Write-Host "> git $($args -join ' ')"
    & git @args
    if ($LASTEXITCODE -ne 0) { Fail "git $($args -join ' ') failed (exit $LASTEXITCODE)" }
}

# Run a git command and return its trimmed stdout, stopping on failure.
function Get-GitOut {
    $out = & git @args
    if ($LASTEXITCODE -ne 0) { Fail "git $($args -join ' ') failed (exit $LASTEXITCODE)" }
    return ($out -join "`n").Trim()
}

# Fast-forward main to $Target: the ref moves first, compared and swapped against the commit read here, and only
# then is the new tree checked out over the clean workspace. A lost race leaves the workspace as it was.
function Move-Main([string]$Target) {
    $old = Get-GitOut rev-parse --verify 'refs/heads/main^{commit}'
    $new = Get-GitOut rev-parse --verify "$Target^{commit}"
    if ($old -eq $new) { Write-Host "integrate: main is already at $Target"; return }
    & git merge-base --is-ancestor $old $new
    if ($LASTEXITCODE -ne 0) { Fail "$Target is not a fast-forward of main; merge main into it and rerun" }
    Invoke-Git update-ref -m "integrate: fast-forward to $Target" refs/heads/main $new $old
    Write-Host "> git read-tree -u -m $old $new"
    & git read-tree -u -m $old $new
    if ($LASTEXITCODE -ne 0) {
        Fail "main moved to $new but its tree did not check out; from the main workspace run: git read-tree -u -m $old $new"
    }
}

if (-not $Repo) {
    $common = & git rev-parse --path-format=absolute --git-common-dir
    if ($LASTEXITCODE -ne 0) { Fail 'not inside a git repository' }
    $Repo = Split-Path -Parent ($common -join '')
}
Set-Location -LiteralPath $Repo

# One run at a time. An open handle shared with nobody: a second run waits, and a crashed run's lock dies with it.
$lockPath = Join-Path (Get-GitOut rev-parse --path-format=absolute --git-common-dir) 'integrate.lock'
$lock = $null
$deadline = (Get-Date).AddMinutes(5)
while (-not $lock) {
    try {
        $lock = [System.IO.File]::Open($lockPath, 'OpenOrCreate', 'ReadWrite', 'None')
    } catch {
        if ((Get-Date) -gt $deadline) { Fail "another integrate still holds $lockPath after five minutes" }
        Write-Host 'integrate: another run is integrating; waiting.'
        Start-Sleep -Seconds 3
    }
}

if ($Branch -eq 'main') { Fail 'the branch to integrate must not be main' }
Invoke-Git rev-parse --verify --quiet "refs/heads/$Branch^{commit}"

$current = Get-GitOut rev-parse --abbrev-ref HEAD
if ($current -ne 'main') { Fail "main workspace is on '$current', not main" }
$dirty = Get-GitOut status --porcelain
if ($dirty) { Fail "main workspace is not clean:`n$dirty" }

Invoke-Git fetch origin
Move-Main origin/main
Move-Main "refs/heads/$Branch"

Invoke-Git push origin main

$ErrorActionPreference = 'Continue'
& git ls-remote hub 2>$null | Out-Null
$hubStatus = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($hubStatus -eq 0) {
    Invoke-Git push hub main
} else {
    Write-Host 'integrate: hub (Mac) unreachable, skipping the hub push.' -ForegroundColor Yellow
}
Write-Host 'integrate: done.' -ForegroundColor Green
