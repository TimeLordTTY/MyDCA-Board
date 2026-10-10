"""Offline only: real Bash/filesystem; fake processes, curl, ownership and lock.

No SSH/SCP binaries, Java process or database are invoked by the simulation.
All fixtures, fake PID state and archives live beneath scripts/deploy.
"""
import hashlib
import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("probe", HERE / "health_probe.py")
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


def bash_binary():
    binary = shutil.which("bash")
    if binary:
        return binary
    git = shutil.which("git")
    if git:
        binary = Path(git).resolve().parent.parent / "bin/bash.exe"
        if binary.is_file():
            return str(binary)
    raise RuntimeError("Bash unavailable: rollback simulation NOT_RUN")


def shell_path(path):
    path = Path(path).resolve().as_posix()
    if os.name == "nt":
        return "/" + path[0].lower() + path[2:]
    return path


HARNESS = r'''
source <(sed 's/\r$//' "$1")
sandbox="$2"
scenario="$3"
export TMPDIR="$sandbox/tmp"
export PATH="$sandbox/bin:$PATH"
mkdir -p "$TMPDIR" "$sandbox/pids" "$sandbox/bin"
# Deny network executables even if a regression accidentally calls one.
for name in ssh scp curl wget nc; do
    printf '#!/bin/bash\nexit 99\n' > "$sandbox/bin/$name"
    chmod +x "$sandbox/bin/$name"
done
printf old > "$sandbox/pids/100"
process_matches() { [[ -f "$sandbox/pids/$1" ]]; }
service_pids() { for f in "$sandbox"/pids/*; do [[ -f "$f" ]] && echo "${f##*/}"; done; return 0; }
kill() {
    [[ "$1" == -TERM && "$2" != 0 ]] || return 99
    echo "stop:$2" >> "$sandbox/events"
    if [[ "$scenario" != stuck_process ]]; then rm -f "$sandbox/pids/$2"; fi
}
sleep() { :; }
flock() { :; }
chown() { :; }
check_java() { :; }
start_service() {
    started_pid=200
    [[ "$(cat "$jar")" == old ]] && started_pid=300
    echo "start:$started_pid" >> "$sandbox/events"
    if [[ "$scenario" != dead_process || "$started_pid" == 300 ]]; then
        printf running > "$sandbox/pids/$started_pid"
    fi
}
install() {
    if [[ "$scenario" == install_failure && ! -f "$sandbox/install-failed" ]]; then
        touch "$sandbox/install-failed"; return 1
    fi
    command cp "${@: -2:1}" "${@: -1}"
}
cp() {
    if [[ "$scenario" == backup_copy_failure && "${@: -1}" == */frontend-dist ]]; then return 1; fi
    if [[ "$scenario" == remote_copy_failure && "${@: -1}" == */dist.new ]]; then return 1; fi
    if [[ "$scenario" == rollback_copy_failure && "${@: -1}" == */dist.restore ]]; then return 1; fi
    command cp "$@"
}
rm() {
    if [[ "$scenario" == cleanup_failure && "$*" == *mydca-release.* && ! -f "$sandbox/cleanup-failed" ]]; then
        command rm -rf "$work"
        touch "$sandbox/cleanup-failed"; return 1
    fi
    command rm "$@"
}
curl() {
    local out='' url="${@: -1}"
    echo "curl:$url" >> "$sandbox/events"
    [[ "$url" == http://127.0.0.1:8766/actuator/health || "$url" == https://frontend.invalid/ ]] || return 99
    while (($#)); do
        if [[ "$1" == -o ]]; then out="$2"; shift; fi
        shift
    done
    if [[ "$url" == https://frontend.invalid/ ]]; then
        [[ "$scenario" != frontend_tls_failure ]] || return 60
        printf 200; return 0
    fi
    if [[ "$(cat "$jar")" == old && "$scenario" != rollback_down ]]; then
        printf '{"status":"UP"}' > "$out"; printf 200; return 0
    fi
    case "$scenario" in
        success|frontend_tls_failure|cleanup_failure) printf '{"status":"UP"}' > "$out"; printf 200 ;;
        html) printf '<html>SPA/login</html>' > "$out"; printf 200 ;;
        malformed) printf '{"status":' > "$out"; printf 200 ;;
        error_json) printf '{"error":"failed"}' > "$out"; printf 200 ;;
        unauthorized) printf '{"status":"UP"}' > "$out"; printf 401 ;;
        forbidden) printf '{"status":"UP"}' > "$out"; printf 403 ;;
        server_error) printf '{"status":"UP"}' > "$out"; printf 503 ;;
        unreachable|timeout) return 28 ;;
        *) printf '{"status":"DOWN"}' > "$out"; printf 200 ;;
    esac
}
deploy "$sandbox/release.tar.gz" "$sandbox/live" https://frontend.invalid/
'''


class ProbeTests(unittest.TestCase):
    def test_strict_probe(self):
        self.assertTrue(probe.healthy("200", b'{"status":"UP","components":{}}'))
        bodies = [b'<html>200 SPA</html>', b'{"status":"DOWN"}', b'{}', b'[]',
                  b'{"components":{"status":"UP"}}', b'{"status":', b'{"status":"up"}',
                  b'{"status":"DOWN","status":"UP"}', b'{"status":"UP","error":"failed"}',
                  b'{"status":"UP","invalid":NaN}', b'\xff', b'x' * 65537]
        for body in bodies:
            with self.subTest(body=body[:50]):
                self.assertFalse(probe.healthy("200", body))
        for code in ["000", "201", "301", "401", "403", "500", "503"]:
            self.assertFalse(probe.healthy(code, b'{"status":"UP"}'))


class DeploymentTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.bash = bash_binary()

    def simulate(self, scenario, nested=False):
        with tempfile.TemporaryDirectory(prefix="offline-", dir=HERE) as directory:
            sandbox = Path(directory)
            live = sandbox / "live"
            (live / "backend/logs").mkdir(parents=True)
            (live / "backend/wealth-hub-1.0.0.jar").write_text("old")
            (live / "frontend/dist/assets").mkdir(parents=True)
            (live / "frontend/dist/index.html").write_text("old-ui")
            (live / "frontend/dist/assets/app.js").write_text("old-asset")
            stage = sandbox / "stage"
            (stage / "frontend").mkdir(parents=True)
            (stage / "wealth-hub-1.0.0.jar").write_text("new")
            (stage / "frontend/index.html").write_text("new-ui")
            shutil.copyfile(HERE / "health_probe.py", stage / "health_probe.py")
            manifest = "".join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(stage).as_posix()}\n"
                               for p in sorted(stage.rglob("*")) if p.is_file())
            (stage / "SHA256SUMS").write_text(manifest, newline="\n")
            if scenario == "bad_sha":
                (stage / "wealth-hub-1.0.0.jar").write_text("corrupt")
            if scenario == "bad_frontend_sha":
                (stage / "frontend/index.html").write_text("corrupt")
            if scenario == "missing_backup":
                (live / "frontend/dist/index.html").unlink()
            if nested:
                for base in (live / "frontend/dist", stage / "frontend"):
                    (base / "wealth-hub").mkdir()
                    (base / "index.html").rename(base / "wealth-hub/index.html")
                    (base / "wealth-hub-mobile").mkdir()
                    (base / "wealth-hub-mobile/index.html").write_text("mobile-ui")
                # 清单必须包含两个真实生产目录中的全部文件。
                manifest = "".join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(stage).as_posix()}\n"
                                   for p in sorted(stage.rglob("*")) if p.is_file() and p.name != "SHA256SUMS")
                (stage / "SHA256SUMS").write_text(manifest, newline="\n")
            with tarfile.open(sandbox / "release.tar.gz", "w:gz") as archive:
                for p in stage.iterdir():
                    archive.add(p, arcname=p.name)
            harness = sandbox / "harness.sh"
            harness.write_text(HARNESS, encoding="utf-8", newline="\n")
            # Git Bash can invoke Windows Python via a python3 shim, no downloads.
            (sandbox / "bin").mkdir()
            python_path = shell_path(sys.executable).replace("'", "'\"'\"'")
            shim = sandbox / "bin/python3"
            shim.write_text(f"#!/bin/bash\nexec '{python_path}' \"$@\"\n", newline="\n")
            shim.chmod(0o755)
            result = subprocess.run([self.bash, shell_path(harness), shell_path(HERE / "remote_deploy.sh"),
                                     shell_path(sandbox), scenario], capture_output=True, text=True, timeout=90)
            events = (sandbox / "events").read_text() if (sandbox / "events").exists() else ""
            jar = (live / "backend/wealth-hub-1.0.0.jar").read_text()
            ui_path = live / ("frontend/dist/wealth-hub/index.html" if nested else "frontend/dist/index.html")
            ui = ui_path.read_text() if ui_path.exists() else ""
            self.assertNotIn("Traceback", result.stderr)
            if result.returncode:
                self.assertTrue((sandbox / "release.tar.gz").exists(), "failed release archive must be retained")
                self.assertTrue(list((sandbox / "tmp").rglob("failure-status.txt")), "failure evidence must be retained")
            return result, events, jar, ui

    def test_production_pc_mobile_layout_deploys_and_rolls_back(self):
        for scenario, expected_code, expected in [("success", 0, ("new", "new-ui")), ("down", 1, ("old", "old-ui"))]:
            with self.subTest(scenario=scenario):
                result, _, jar, ui = self.simulate(scenario, nested=True)
                self.assertEqual(expected_code, result.returncode, result.stdout + result.stderr)
                self.assertEqual(expected, (jar, ui))

    def test_success_requires_backend_and_frontend(self):
        result, events, jar, ui = self.simulate("success")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("deployment_status=success", result.stdout)
        self.assertIn("curl:http://127.0.0.1:8766/actuator/health", events)
        self.assertIn("curl:https://frontend.invalid/", events)
        self.assertEqual(("new", "new-ui"), (jar, ui))

    def test_start_child_does_not_inherit_deploy_lock_or_ssh_input(self):
        with tempfile.TemporaryDirectory(prefix="offline-start-", dir=HERE) as directory:
            root = Path(directory)
            (root / "backend/logs").mkdir(parents=True)
            script = root / "launch.sh"
            script.write_text(r'''
source <(sed 's/\r$//' "$1")
deploy_root="$2"
java_bin=offline_java_stub
jar="$deploy_root/fixture.jar"
exec 9>"$deploy_root/fixture-lock"
nohup() {
    [[ "$1" == offline_java_stub ]] || return 99
    # Only a local fake nohup Bash function runs, never Java or native nohup.
    if ( : >&9 ) 2>/dev/null; then return 98; fi
    if IFS= read -r input; then return 97; fi
    return 0
}
start_service
wait "$started_pid"
''', encoding="utf-8", newline="\n")
            result = subprocess.run([self.bash, shell_path(script), shell_path(HERE / "remote_deploy.sh"), shell_path(root)],
                                     input="ssh-input-must-not-reach-child\n", capture_output=True, text=True, timeout=10)
            self.assertEqual(0, result.returncode, result.stderr)

    def test_cleanup_failure_cannot_emit_success_and_restores_old_service(self):
        result, events, jar, ui = self.simulate("cleanup_failure")
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("deployment_status=rolled_back", result.stdout)
        self.assertNotIn("deployment_status=success", result.stdout)
        self.assertIn("rollback_backend_status=UP", result.stdout)
        self.assertIn("start:300", events)
        self.assertEqual(("old", "old-ui"), (jar, ui))

    def test_invalid_health_and_post_stop_errors_restore_and_probe_old_service(self):
        for scenario in ["down", "html", "malformed", "error_json", "unauthorized", "forbidden",
                         "server_error", "unreachable", "timeout", "dead_process", "install_failure",
                         "frontend_tls_failure"]:
            with self.subTest(scenario=scenario):
                result, events, jar, ui = self.simulate(scenario)
                self.assertEqual(1, result.returncode, result.stdout + result.stderr)
                self.assertIn("deployment_status=rolled_back", result.stdout)
                self.assertIn("rollback_backend_status=UP", result.stdout)
                self.assertNotIn("deployment_status=success", result.stdout)
                self.assertIn("start:300\ncurl:http://127.0.0.1:8766/actuator/health", events)
                self.assertEqual(("old", "old-ui"), (jar, ui))

    def test_pre_stop_failures_preserve_running_old_service(self):
        for scenario in ["missing_backup", "bad_sha", "bad_frontend_sha", "backup_copy_failure", "remote_copy_failure"]:
            with self.subTest(scenario=scenario):
                result, events, jar, _ = self.simulate(scenario)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("deployment_status=blocked_before_stop", result.stdout)
                self.assertNotIn("deployment_status=success", result.stdout)
                self.assertNotIn("stop:", events)
                self.assertNotIn("start:", events)
                self.assertEqual("old", jar)

    def test_unrecoverable_failures_are_loud(self):
        for scenario in ["rollback_down", "rollback_copy_failure", "stuck_process"]:
            with self.subTest(scenario=scenario):
                result, _, jar, _ = self.simulate(scenario)
                self.assertEqual(2, result.returncode, result.stdout + result.stderr)
                self.assertIn("ROLLBACK_FAILED_MANUAL_RECOVERY_REQUIRED", result.stdout)
                self.assertIn("evidence_path=", result.stdout)
                self.assertNotIn("deployment_status=success", result.stdout)
                self.assertEqual("old", jar)


if __name__ == "__main__":
    unittest.main()
