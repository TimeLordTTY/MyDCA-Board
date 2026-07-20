param(
    [string]$SshTarget = 'baota-124',
    [string]$DeployRoot = '/www/wwwroot/wealth-hub',
    [string]$HealthUrl = 'https://www.timelordtty.cn/wealth-hub/',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$jar = Join-Path $repoRoot 'backend\target\wealth-hub-1.0.0.jar'
$webDist = Join-Path $repoRoot 'web\dist'
$stage = Join-Path ([IO.Path]::GetTempPath()) ('mydca-deploy-' + [guid]::NewGuid().ToString('N'))

try {
    if (-not $SkipBuild) {
        & mvn -q -f (Join-Path $repoRoot 'backend\pom.xml') test package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw 'Backend package failed.' }
        Push-Location (Join-Path $repoRoot 'web')
        try { & npm run build; if ($LASTEXITCODE -ne 0) { throw 'Web build failed.' } } finally { Pop-Location }
    }
    if (-not (Test-Path -LiteralPath $jar)) { throw "Missing artifact: $jar" }
    if (-not (Test-Path -LiteralPath $webDist)) { throw "Missing artifact: $webDist" }

    New-Item -ItemType Directory -Force (Join-Path $stage 'frontend') | Out-Null
    Copy-Item -LiteralPath $jar -Destination (Join-Path $stage 'wealth-hub-1.0.0.jar')
    Copy-Item -Path (Join-Path $webDist '*') -Destination (Join-Path $stage 'frontend') -Recurse -Force
    $sha = (Get-FileHash -LiteralPath (Join-Path $stage 'wealth-hub-1.0.0.jar') -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText((Join-Path $stage 'SHA256SUMS'), "$sha  wealth-hub-1.0.0.jar`n", [Text.UTF8Encoding]::new($false))
    $archive = "$stage.tar.gz"
    & tar -czf $archive -C $stage .
    if ($LASTEXITCODE -ne 0) { throw 'Unable to create deployment archive.' }

    $remoteArchive = "/tmp/mydca-deploy-$([guid]::NewGuid().ToString('N')).tar.gz"
    & scp -q $archive "${SshTarget}:$remoteArchive"
    if ($LASTEXITCODE -ne 0) { throw 'Artifact upload failed.' }

    $remoteScript = @'
set -euo pipefail
archive="$1"
deploy_root="$2"
health_url="$3"
stamp="$(date +%Y%m%d-%H%M%S)"
work="$(mktemp -d /tmp/mydca-release.XXXXXX)"
backup="$deploy_root/backups/$stamp"
jar="$deploy_root/backend/wealth-hub-1.0.0.jar"
java_bin="/www/server/java/jdk-17.0.8/bin/java"
pid=""
cleanup() { rm -rf "$work" "$archive"; }
trap cleanup EXIT
mkdir -p "$work" "$backup" "$deploy_root/frontend"
tar -xzf "$archive" -C "$work"
(cd "$work" && sha256sum -c SHA256SUMS)
cp -a "$jar" "$backup/wealth-hub-1.0.0.jar"
if [ -d "$deploy_root/frontend/dist" ]; then cp -a "$deploy_root/frontend/dist" "$backup/frontend-dist"; fi
pid="$(pgrep -f "[w]ealth-hub-1.0.0.jar" | head -n1 || true)"
if [ -n "$pid" ]; then kill "$pid"; for _ in $(seq 1 30); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done; fi
install -o www -g www -m 0644 "$work/wealth-hub-1.0.0.jar" "$jar"
rm -rf "$deploy_root/frontend/dist.new"
mkdir -p "$deploy_root/frontend/dist.new"
cp -a "$work/frontend/." "$deploy_root/frontend/dist.new/"
chown -R www:www "$deploy_root/frontend/dist.new"
rm -rf "$deploy_root/frontend/dist"
mv "$deploy_root/frontend/dist.new" "$deploy_root/frontend/dist"
start_service() {
  cd "$deploy_root/backend"
  nohup "$java_bin" -Xmx1024M -Xms256M -jar "$jar" >> logs/wealth-hub.log 2>&1 &
}
start_service
healthy=false
for _ in $(seq 1 60); do
  code="$(curl -ksS -o /dev/null -w '%{http_code}' "$health_url" || true)"
  if [ "$code" = 200 ]; then healthy=true; break; fi
  sleep 2
done
if [ "$healthy" != true ]; then
  new_pid="$(pgrep -f "[w]ealth-hub-1.0.0.jar" | head -n1 || true)"
  [ -z "$new_pid" ] || kill "$new_pid" || true
  install -o www -g www -m 0644 "$backup/wealth-hub-1.0.0.jar" "$jar"
  if [ -d "$backup/frontend-dist" ]; then rm -rf "$deploy_root/frontend/dist"; cp -a "$backup/frontend-dist" "$deploy_root/frontend/dist"; fi
  start_service
  echo 'deployment_status=rolled_back'
  exit 1
fi
echo 'deployment_status=success'
echo "backup_path=$backup"
echo 'health_status=200'
'@
    $remoteScript | ssh $SshTarget 'bash -s' -- $remoteArchive $DeployRoot $HealthUrl
    if ($LASTEXITCODE -ne 0) { throw 'Remote deployment failed or rolled back.' }
    Write-Output "artifact_sha256=$sha"
} finally {
    if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
    if (Test-Path -LiteralPath "$stage.tar.gz") { Remove-Item -LiteralPath "$stage.tar.gz" -Force }
}
