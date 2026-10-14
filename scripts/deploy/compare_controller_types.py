"""Read controller evidence; run ONLY the candidate type check, never baseline."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys


def sha(data):
    return hashlib.sha256(data).hexdigest()


def diagnostics(text):
    result = Counter()
    for line in text.splitlines():
        match = re.match(r'(?:web/pc-app/)?(src/.+?)\(\d+,\d+\): (error TS\d+: .+)', line)
        if match:
            result[match[1] + ': ' + match[2]] += 1
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--log', type=Path, required=True)
    args = parser.parse_args()
    report_bytes, log_bytes = args.report.read_bytes(), args.log.read_bytes()
    baseline = json.loads(report_bytes)
    evidence = next(item for item in baseline['checks'] if item['check'] == 'types-baseline')
    if baseline['status'] != 'passed' or sha(log_bytes) != evidence['sha256']:
        raise RuntimeError('BLOCKED: missing/invalid controller baseline evidence')
    baseline_diagnostics = diagnostics(log_bytes.decode('utf-8-sig'))
    if baseline_diagnostics != Counter(baseline['diagnostics']):
        raise RuntimeError('BLOCKED: controller diagnostic evidence mismatch')
    repo = Path(__file__).resolve().parents[2]
    run = subprocess.run(['node', 'web/node_modules/vue-tsc/bin/vue-tsc.js', '--noEmit',
                          '-p', 'web/pc-app/tsconfig.json'], cwd=repo,
                         capture_output=True, timeout=180)
    candidate = run.stdout + run.stderr
    sys.stdout.buffer.write(candidate)
    sys.stdout.buffer.flush()
    final_diagnostics = diagnostics(candidate.decode('utf-8-sig'))
    same = final_diagnostics == baseline_diagnostics and run.returncode == baseline['type_exit_code']
    result = {
        'status': 'NO_NEW_DIAGNOSTICS' if same else 'BLOCKED_COMPARISON',
        'baseline_base': baseline['base'], 'baseline_report_sha256': sha(report_bytes),
        'baseline_log_sha256': sha(log_bytes), 'baseline_exit_code': baseline['type_exit_code'],
        'candidate_exit_code': run.returncode, 'baseline_count': sum(baseline_diagnostics.values()),
        'candidate_count': sum(final_diagnostics.values()), 'diagnostics_equal': final_diagnostics == baseline_diagnostics,
        'added': dict(final_diagnostics - baseline_diagnostics),
        'removed': dict(baseline_diagnostics - final_diagnostics), 'candidate_log_sha256': sha(candidate),
    }
    (Path(__file__).parent / 'types-final.log').write_bytes(candidate)
    (Path(__file__).parent / 'types-comparison.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result))
    return 0 if same else 1


if __name__ == '__main__':
    sys.exit(main())
