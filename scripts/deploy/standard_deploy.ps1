param(
    [string]$SshTarget = 'baota-124',
    [string]$DeployRoot = '/www/wwwroot/wealth-hub',
    [string]$HealthUrl = 'https://www.timelordtty.cn/wealth-hub/',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$jar = Join-Path $repoRoot 'backend\target\wealth-hub-1.0.0.jar'
$pcDist = Join-Path $repoRoot 'web\pc-app\dist'
$mobileDist = Join-Path $repoRoot 'web\mobile-app\dist'
$stage = Join-Path ([IO.Path]::GetTempPath()) ('mydca-deploy-' + [guid]::NewGuid().ToString('N'))

try {
    if (-not $SkipBuild) {
        & mvn -q -f (Join-Path $repoRoot 'backend\pom.xml') test package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw 'Backend package failed.' }
        Push-Location (Join-Path $repoRoot 'web')
        try { & npm run build; if ($LASTEXITCODE -ne 0) { throw 'Web build failed.' } } finally { Pop-Location }
    }
    if (-not (Test-Path -LiteralPath $jar)) { throw "Missing artifact: $jar" }
    foreach ($dist in @($pcDist, $mobileDist)) {
        if (-not (Test-Path -LiteralPath (Join-Path $dist 'index.html') -PathType Leaf)) { throw "Missing frontend index.html: $dist" }
    }

    New-Item -ItemType Directory -Force (Join-Path $stage 'frontend') | Out-Null
    Copy-Item -LiteralPath $jar -Destination (Join-Path $stage 'wealth-hub-1.0.0.jar')
    # 使用本次构建的产物，按生产 Nginx 的两个目录打包。
    Copy-Item -LiteralPath $pcDist -Destination (Join-Path $stage 'frontend\wealth-hub') -Recurse
    Copy-Item -LiteralPath $mobileDist -Destination (Join-Path $stage 'frontend\wealth-hub-mobile') -Recurse
    $sha = (Get-FileHash -LiteralPath (Join-Path $stage 'wealth-hub-1.0.0.jar') -Algorithm SHA256).Hash.ToLowerInvariant()
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'health_probe.py') -Destination $stage
    $manifest = Get-ChildItem -LiteralPath $stage -Recurse -File | Sort-Object FullName | ForEach-Object {
        $relative = $_.FullName.Substring($stage.Length + 1).Replace('\', '/')
        $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash  $relative"
    }
    [IO.File]::WriteAllText((Join-Path $stage 'SHA256SUMS'), (($manifest -join "`n") + "`n"), [Text.UTF8Encoding]::new($false))
    $archive = "$stage.tar.gz"
    & tar -czf $archive -C $stage .
    if ($LASTEXITCODE -ne 0) { throw 'Unable to create deployment archive.' }

    $remoteArchive = "/tmp/mydca-deploy-$([guid]::NewGuid().ToString('N')).tar.gz"
    & scp -q $archive "${SshTarget}:$remoteArchive"
    if ($LASTEXITCODE -ne 0) { throw 'Artifact upload failed.' }

    # Send the same function library exercised by the offline Bash simulation.
    $remoteScript = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'remote_deploy.sh')).Replace("`r", '')
    $remoteScript += "`ndeploy " + '"$@"' + "`n# end of script"
    function ConvertTo-BashLiteral([string]$Value) {
        if ($Value.Contains("`n") -or $Value.Contains("`r")) { throw 'Invalid remote argument.' }
        return "'" + $Value.Replace("'", "'" + '"' + "'" + '"' + "'") + "'"
    }
    $remoteCommand = 'bash -s -- ' + ((@($remoteArchive, $DeployRoot, $HealthUrl) | ForEach-Object { ConvertTo-BashLiteral $_ }) -join ' ')
    $remoteScript | ssh $SshTarget $remoteCommand
    if ($LASTEXITCODE -ne 0) { throw 'Remote deployment failed or rolled back.' }
    Write-Output "artifact_sha256=$sha"
} finally {
    if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
    if (Test-Path -LiteralPath "$stage.tar.gz") { Remove-Item -LiteralPath "$stage.tar.gz" -Force }
}
