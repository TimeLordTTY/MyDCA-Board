#!/usr/bin/env bash
# The former TERM/nohup and server-wide backup path is retired.
# This entry point performs no network/service/filesystem mutations.
set -Eeuo pipefail
deploy() {
    printf '%s\n' 'deployment_status=BLOCKED_LIVE_STREAM_ADAPTER_NOT_VERIFIED' >&2
    printf '%s\n' 'required=config_inventory,upstream_route,flock_session,restricted_local_backup_ACL' >&2
    return 1
}
stop_service() { return 1; }
start_service() { return 1; }
rollback() {
    printf '%s\n' 'rollback_status=NOT_RUN_LOCAL_STREAM_ADAPTER_REQUIRED' >&2
    return 2
}
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    deploy "$@"
fi
