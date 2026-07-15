#!/bin/bash
set -euo pipefail
python3 - <<'PY'
import json, time, subprocess, urllib.request

pw_path = '/opt/mindustry-server/config/mods/server-admin/admin.password'
password = ''
for line in open(pw_path, encoding='utf-8'):
    t = line.strip()
    if t and not t.startswith('#'):
        password = t
        break
if not password:
    raise SystemExit('no password in file')

req = urllib.request.Request(
    'http://127.0.0.1:6569/api/v1/admin/login',
    data=json.dumps({'password': password}).encode(),
    headers={'Content-Type': 'application/json'},
    method='POST',
)
with urllib.request.urlopen(req, timeout=5) as r:
    data = json.loads(r.read().decode())
token = data.get('token')
print('token_len', len(token or ''))
if not token:
    print('login_resp', data)
    raise SystemExit(1)

def pgrep_java():
    out = subprocess.check_output(['pgrep', '-f', 'java.*server.jar'], text=True).strip().splitlines()
    return out[0] if out else ''

before = pgrep_java()
print('before_pid', before)
req = urllib.request.Request(
    'http://127.0.0.1:6569/api/v1/admin/restart',
    data=b'',
    headers={'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'},
    method='POST',
)
with urllib.request.urlopen(req, timeout=5) as r:
    print('restart_resp', r.read().decode())
time.sleep(12)
after = pgrep_java()
print('after_pid', after)
subprocess.run(['systemctl', 'is-active', 'mindustry-server'], check=False)
subprocess.run(
    "journalctl -u mindustry-server --since '25 sec ago' --no-pager -o cat | grep -iE 'Restart|Shutting|Forcing|Suite plugin|HTTP listening' || true",
    shell=True,
)
if before and after and before != after:
    print('RESTART_OK: pid changed')
else:
    print('RESTART_CHECK: compare pids manually')
PY
