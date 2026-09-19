<#
.SYNOPSIS
    Builds release jars and collects them in one folder.

.DESCRIPTION
    The PowerShell twin of build-release.sh, for Windows without Git Bash.
    Output lands in build/release/<mod_version>/.

    Filters exist because a full build remaps Minecraft for every node, which is slow and
    memory-hungry. When iterating on one loader there is no reason to pay for the rest.

.EXAMPLE
    scripts\build-release.ps1
.EXAMPLE
    scripts\build-release.ps1 -Mc 1.20.1
.EXAMPLE
    scripts\build-release.ps1 -Mc 1.21.1 -Loader neoforge
#>
[CmdletBinding()]
param(
    [string] $Mc,
    [string] $Loader
)

$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

$modVersion = (Select-String -Path 'gradle.properties' -Pattern '^mod_version\s*=\s*(.+)$').Matches[0].Groups[1].Value.Trim()
$out = "build/release/$modVersion"

# Node names come from settings.gradle.kts rather than being listed here, so adding a
# Minecraft version or a loader never needs this script edited to match.
$nodes = Select-String -Path 'settings.gradle.kts' -Pattern '"([0-9][^"]*-[a-z]+)"\s+to\s+"' |
    ForEach-Object { $_.Matches[0].Groups[1].Value }

$selected = $nodes | Where-Object {
    $nodeMc = $_ -replace '-[a-z]+$', ''
    $nodeLoader = ($_ -split '-')[-1]
    (-not $Mc -or $nodeMc -eq $Mc) -and (-not $Loader -or $nodeLoader -eq $Loader)
}

if (-not $selected) {
    Write-Error "No nodes match those filters. Known nodes: $($nodes -join ', ')"
}

Write-Host "Building $modVersion`: $($selected -join ', ')"

$tasks = $selected | ForEach-Object { ":${_}:build" }
& .\gradlew.bat @tasks
if ($LASTEXITCODE -ne 0) { Write-Error "Gradle build failed." }

if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Path $out -Force | Out-Null

foreach ($node in $selected) {
    # Sources jars are published to Maven, not shipped to players.
    Get-ChildItem "versions/$node/build/libs/*.jar" |
        Where-Object { $_.Name -notlike '*-sources.jar' } |
        Copy-Item -Destination $out
}

Write-Host ''
Write-Host "Release jars in ${out}:"
Get-ChildItem $out | Format-Table Name, @{ Name = 'Size'; Expression = { '{0:N0} KB' -f ($_.Length / 1KB) } }
