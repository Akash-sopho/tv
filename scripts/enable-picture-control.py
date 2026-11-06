import device as tv
"""Enable only our picture adapter, preserving all existing accessibility services."""
from pathlib import Path
import importlib.util,json,shlex,time
workspace=Path(__file__).resolve().parents[1]
device=tv.connect()
component='dev.tvassistant/.PictureActionService'
try:
 current=device.shell('settings get secure enabled_accessibility_services').strip()
 previous=[] if current in ('','null') else current.split(':')
 if any('/' not in item or any(c.isspace() for c in item) for item in previous):raise SystemExit('Unexpected service list; no changes made.')
 saved=workspace/'.local/picture-accessibility-baseline.json'
 if not saved.exists():saved.write_text(json.dumps({'enabled_accessibility_services':current},indent=2),encoding='utf-8')
 merged=list(dict.fromkeys(previous+[component]))
 response=device.shell('settings put secure enabled_accessibility_services '+shlex.quote(':'.join(merged)))
 if response.strip():raise SystemExit('Accessibility update returned an unexpected response.')
 device.shell('settings put secure accessibility_enabled 1')
 actual=device.shell('settings get secure enabled_accessibility_services').strip().split(':')
 if not all(item in actual for item in merged):raise SystemExit('Accessibility service list verification failed.')
 print('Enabled TV Assistant picture control; preserved existing services.')
 print(device.shell('am broadcast -a dev.tvassistant.CAPABILITIES -n dev.tvassistant/.ProbeReceiver'))
finally:device.close()
