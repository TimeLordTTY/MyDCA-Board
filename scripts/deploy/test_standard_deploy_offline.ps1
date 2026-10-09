# Only fake scp/ssh functions are used. No remote command is executed.
$ErrorActionPreference = 'Stop'
$fixtureRoot = Join-Path $PSScriptRoot ('offline-ps-' + [guid]::NewGuid().ToString('N'))
$originalTemp = $env:TEMP
$originalTmp = $env:TMP
try {
    New-Item -ItemType Directory -Path "$fixtureRoot/scripts/deploy", "$fixtureRoot/backend/target", "$fixtureRoot/web/dist", "$fixtureRoot/tmp" -Force | Out-Null
    foreach ($name in @('standard_deploy.ps1', 'remote_deploy.sh', 'health_probe.py')) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination "$fixtureRoot/scripts/deploy/$name"
    }
    [IO.File]::WriteAllText("$fixtureRoot/backend/target/wealth-hub-1.0.0.jar", 'fixture-new-jar')
    [IO.File]::WriteAllText("$fixtureRoot/web/dist/index.html", 'fixture-new-ui')
    $env:TEMP = "$fixtureRoot/tmp"
    $env:TMP = "$fixtureRoot/tmp"
    $global:mydcaOfflineUploadFails = $true
    $global:mydcaOfflineSshCalled = $false
    function tar {
        # Windows tar cannot chdir into this long worktree fixture path. Use
        # Python's local archive implementation; Bash tests exercise real tar.
        $tarCode = "import pathlib,sys,tarfile; mode,path,flag,root=sys.argv[1:5]; t=tarfile.open(path,'w:gz' if mode=='-czf' else 'r:gz'); [t.add(p,arcname=p.name) for p in pathlib.Path(root).iterdir()] if mode=='-czf' else t.extractall(root,filter='data'); t.close()"
        & python -c $tarCode @args
        if ($LASTEXITCODE -ne 0) { throw 'Local archive fixture failed.' }
    }
    function scp {
        # Capture the local archive for verification; never invoke native scp.
        Copy-Item -LiteralPath $args[1] -Destination "$fixtureRoot/captured.tar.gz" -Force
        Set-Variable -Name LASTEXITCODE -Value $(if ($global:mydcaOfflineUploadFails) { 1 } else { 0 }) -Scope 1
    }
    function ssh {
        param([string]$Target, [string]$RemoteCommand, [Parameter(ValueFromPipeline=$true)][string]$ScriptText)
        process {
            $global:mydcaOfflineSshCalled = $true
            if ($Target -ne 'offline.invalid') { throw 'Unexpected mock target.' }
            if (-not $RemoteCommand.Contains("'" + '/tmp/mydca-deploy-')) { throw 'Archive argument not quoted.' }
            $expectedRoot = "'/fixture/a'" + '"' + "'" + '"' + "'b;literal'"
            if (-not $RemoteCommand.Contains($expectedRoot)) { throw 'Remote path quotes/metacharacters are not escaped.' }
            if (-not $ScriptText.Contains('deploy "$@"')) { throw 'Missing function-library entry point.' }
            [IO.File]::WriteAllText("$fixtureRoot/captured-script.sh", $ScriptText.Replace("`r", '') + "`n")
            Set-Variable -Name LASTEXITCODE -Value 0 -Scope 1
        }
    }
    $blocked = $false
    try {
        & "$fixtureRoot/scripts/deploy/standard_deploy.ps1" -SshTarget offline.invalid -SkipBuild
    } catch {
        if ($_.Exception.Message -ne 'Artifact upload failed.') { throw }
        $blocked = $true
    }
    if (-not $blocked -or $global:mydcaOfflineSshCalled) { throw 'Failed upload must block SSH.' }
    $global:mydcaOfflineUploadFails = $false
    # Quotes/metacharacters are data in the one remote command argument.
    & "$fixtureRoot/scripts/deploy/standard_deploy.ps1" -SshTarget offline.invalid -DeployRoot "/fixture/a'b;literal" -SkipBuild
    if (-not $global:mydcaOfflineSshCalled) { throw 'Mock SSH entry point was not exercised.' }
    & tar -xzf "$fixtureRoot/captured.tar.gz" -C "$fixtureRoot/tmp"
    if ($LASTEXITCODE -ne 0) { throw 'Fixture archive extraction failed.' }
    foreach ($line in [IO.File]::ReadAllLines("$fixtureRoot/tmp/SHA256SUMS")) {
        $hash, $path = $line -split '  ', 2
        if ((Get-FileHash -LiteralPath (Join-Path "$fixtureRoot/tmp" $path) -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hash) {
            throw 'Fixture SHA mismatch.'
        }
    }
    if ([IO.File]::ReadAllText("$fixtureRoot/tmp/SHA256SUMS").Contains("`r")) { throw 'Manifest must use LF.' }
    Write-Output 'offline_powershell_tests=PASS (upload failure, mock SSH, full SHA manifest)'
} finally {
    Remove-Variable -Name mydcaOfflineUploadFails,mydcaOfflineSshCalled -Scope Global -ErrorAction SilentlyContinue
    $env:TEMP = $originalTemp
    $env:TMP = $originalTmp
    # Resolve and check containment before recursive fixture cleanup.
    $resolvedFixture = [IO.Path]::GetFullPath($fixtureRoot)
    $allowedRoot = [IO.Path]::GetFullPath($PSScriptRoot) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolvedFixture.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe fixture cleanup path.' }
    if (Test-Path -LiteralPath $resolvedFixture) { Remove-Item -LiteralPath $resolvedFixture -Recurse -Force }
}
