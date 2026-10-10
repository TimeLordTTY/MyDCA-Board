#!/usr/bin/env bash
# Function library; standard_deploy.ps1 appends deploy "$@". Tests source the
# same library and replace process/network operations before invoking deploy.
set -Eeuo pipefail

process_matches() {
    local pid="$1" i candidate cwd
    local -a args=()
    kill -0 "$pid" 2>/dev/null || return 1
    [[ -r "/proc/$pid/cmdline" ]] || return 1
    mapfile -d '' -t args < "/proc/$pid/cmdline"
    [[ "${args[0]:-}" == *java ]] || return 1
    for ((i=0; i<${#args[@]}-1; i++)); do
        [[ "${args[i]}" == -jar ]] || continue
        candidate="${args[i+1]}"
        if [[ "$candidate" != /* ]]; then
            cwd="$(readlink -f "/proc/$pid/cwd")" || return 1
            candidate="$cwd/$candidate"
        fi
        [[ "$(readlink -f "$candidate")" == "$jar" ]] && return 0
    done
    return 1
}

service_pids() {
    local path pid
    for path in /proc/[0-9]*/cmdline; do
        pid="${path#/proc/}"; pid="${pid%/cmdline}"
        if process_matches "$pid"; then printf '%s\n' "$pid"; fi
    done
}

stop_service() {
    local pid i
    for pid in $(service_pids); do
        process_matches "$pid" || continue
        kill -TERM "$pid" || return 1
        for ((i=0; i<30; i++)); do
            process_matches "$pid" || break
            sleep 1
        done
        # Never replace a JAR beneath a surviving process; no unconfirmed SIGKILL.
        process_matches "$pid" && return 1
    done
    [[ -z "$(service_pids)" ]]
}

start_service() {
    cd "$deploy_root/backend"
    # The child must not retain SSH stdin or the deployment lock after shell exit.
    nohup "$java_bin" -Xmx1024M -Xms256M -jar "$jar" </dev/null >> logs/wealth-hub.log 2>&1 9>&- &
    started_pid=$!
}

check_java() { [[ -x "$java_bin" ]]; }

backend_healthy() {
    local code
    process_matches "$started_pid" || return 1
    mkdir -p "$work" || return 1
    # No proxy, redirects, public health URL, certificate bypass or response logging.
    code="$(curl --noproxy '*' -sS --connect-timeout 2 --max-time 5 \
        --max-filesize 65536 -o "$work/health.json" -w '%{http_code}' \
        http://127.0.0.1:8766/actuator/health 2>/dev/null)" || return 1
    python3 "$backup/health_probe.py" --http-code "$code" --file "$work/health.json" || return 1
    process_matches "$started_pid"
}

wait_backend() {
    local i
    for ((i=0; i<60; i++)); do
        backend_healthy && return 0
        sleep 2
    done
    return 1
}

frontend_healthy() {
    local code
    [[ "$health_url" == https://* ]] || return 1
    code="$(curl -sS --connect-timeout 2 --max-time 10 -o /dev/null \
        -w '%{http_code}' "$health_url" 2>/dev/null)" || return 1
    [[ "$code" == 200 ]]
}

frontend_present() {
    # 兼容旧平铺备份与生产中 PC/H5 各自的目录。
    [[ -s "$1/index.html" ]] || [[ -s "$1/wealth-hub/index.html" && -s "$1/wealth-hub-mobile/index.html" ]]
}

verify_backup() {
    [[ -s "$backup/wealth-hub-1.0.0.jar" && -s "$backup/health_probe.py" ]] && frontend_present "$backup/frontend-dist" &&
        (cd "$backup" && sha256sum -c SHA256SUMS >/dev/null 2>&1)
}

rollback() {
    # Called with errexit disabled: every recovery operation checks its result.
    verify_backup || return 1
    stop_service || return 1
    install -o www -g www -m 0644 "$backup/wealth-hub-1.0.0.jar" "$jar" || return 1
    cmp -s "$backup/wealth-hub-1.0.0.jar" "$jar" || return 1
    rm -rf "$deploy_root/frontend/dist.restore" || return 1
    cp -a "$backup/frontend-dist" "$deploy_root/frontend/dist.restore" || return 1
    chown -R www:www "$deploy_root/frontend/dist.restore" || return 1
    diff -qr "$backup/frontend-dist" "$deploy_root/frontend/dist.restore" >/dev/null || return 1
    rm -rf "$deploy_root/frontend/dist" || return 1
    mv "$deploy_root/frontend/dist.restore" "$deploy_root/frontend/dist" || return 1
    start_service || return 1
    wait_backend
}

finish() {
    local result=$?
    trap - EXIT HUP INT TERM
    if (( result == 0 )); then
        if rm -rf "$work" "$archive"; then
            echo 'deployment_status=success'
            echo 'health_status=200'
            echo 'backend_status=UP'
            exit 0
        fi
        # Even cleanup errors enter the same recovery gate. The verified backup
        # retains the parser so partially removed work files cannot disable it.
        result=1
    fi
    if (( result != 0 )); then
        set +e
        if [[ "$recovery_required" == true ]]; then
            if rollback; then
                echo 'deployment_status=rolled_back'
                echo 'rollback_backend_status=UP'
            else
                echo 'deployment_status=ROLLBACK_FAILED_MANUAL_RECOVERY_REQUIRED'
                result=2
            fi
        else
            echo 'deployment_status=blocked_before_stop'
        fi
        # Retain upload, working files, verified backup; never dump health bodies/logs.
        echo "evidence_path=$work"
        echo "backup_path=$backup"
        mkdir -p "$work"
        printf 'exit_code=%s\n' "$result" > "$work/failure-status.txt"
    fi
    exit "$result"
}

deploy() {
    archive="$1"
    deploy_root="$(readlink -f "$2")"
    health_url="$3"
    [[ -n "$deploy_root" && "$deploy_root" != / ]] || return 1
    java_bin=/www/server/java/jdk-17.0.8/bin/java
    jar="$deploy_root/backend/wealth-hub-1.0.0.jar"
    work="$(mktemp -d "${TMPDIR:-/tmp}/mydca-release.XXXXXX")"
    backup="$deploy_root/backups/$(date +%Y%m%d-%H%M%S)-${work##*.}"
    recovery_required=false
    started_pid=''
    trap finish EXIT
    trap 'exit 130' HUP INT TERM
    # Exclusive deployment lock remains held throughout rollback (flock releases on exit).
    exec 9>"$deploy_root/.standard-deploy.lock"
    flock -n 9
    for command in python3 curl sha256sum install diff; do command -v "$command" >/dev/null; done
    check_java
    [[ -s "$jar" && ! -L "$jar" ]] && frontend_present "$deploy_root/frontend/dist"
    [[ ! -L "$deploy_root/frontend/dist" && -z "$(find "$deploy_root/frontend/dist" -type l -print -quit)" ]]
    [[ -d "$deploy_root/backend/logs" && -w "$deploy_root/backend/logs" ]]
    tar -xzf "$archive" -C "$work"
    [[ -s "$work/wealth-hub-1.0.0.jar" && -s "$work/health_probe.py" ]] && frontend_present "$work/frontend"
    (cd "$work" && sha256sum -c SHA256SUMS >/dev/null)
    echo "artifact_sha256=$(sha256sum "$work/wealth-hub-1.0.0.jar" | cut -d ' ' -f1)"
    mkdir -p "$backup"
    cp -a "$jar" "$backup/wealth-hub-1.0.0.jar"
    cp -a "$deploy_root/frontend/dist" "$backup/frontend-dist"
    cp -a "$work/health_probe.py" "$backup/health_probe.py"
    cmp -s "$jar" "$backup/wealth-hub-1.0.0.jar"
    diff -qr "$deploy_root/frontend/dist" "$backup/frontend-dist" >/dev/null
    (cd "$backup" && find wealth-hub-1.0.0.jar frontend-dist health_probe.py -type f -exec sha256sum {} + > SHA256SUMS)
    verify_backup
    echo "backup_path=$backup"
    # Prepare the entire new frontend before disturbing the live service.
    rm -rf "$deploy_root/frontend/dist.new"
    cp -a "$work/frontend" "$deploy_root/frontend/dist.new"
    chown -R www:www "$deploy_root/frontend/dist.new"
    # A partial stop may already have stopped some old processes: recover on failure too.
    recovery_required=true
    stop_service
    install -o www -g www -m 0644 "$work/wealth-hub-1.0.0.jar" "$jar"
    cmp -s "$work/wealth-hub-1.0.0.jar" "$jar"
    rm -rf "$deploy_root/frontend/dist"
    mv "$deploy_root/frontend/dist.new" "$deploy_root/frontend/dist"
    start_service
    wait_backend
    frontend_healthy
    backend_healthy
}
