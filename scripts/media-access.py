import device as tv
"""Grant/revoke only our notification listener for on-request media-session controls.
Does not collect notification contents; other enabled listeners are preserved.
"""
import importlib.util,json,sys,shlex,time
from pathlib import Path
root=Path(__file__).resolve().parents[1]
d=tv.connect();component='dev.tvassistant/dev.tvassistant.MediaAccessService'
revoke='--disable' in sys.argv
try:
 current=d.shell('settings get secure enabled_notification_listeners').strip()
 baseline=root/'.local/media-listener-baseline.json'
 if not baseline.exists():baseline.write_text(json.dumps({'enabled_notification_listeners':current},indent=2),encoding='utf-8')
 if revoke and not any(x in current.split(':') for x in (component,'dev.tvassistant/.MediaAccessService')):
  print('TV Assistant media access was already off; existing listeners preserved.');raise SystemExit(0)
 verb='disallow_listener' if revoke else 'allow_listener'
 result=d.shell('cmd notification '+verb+' '+shlex.quote(component)+' 0')
 if any(x in result.lower() for x in ('error','exception','unknown command')):raise SystemExit('Media listener command was rejected; no success claimed.')
 for attempt in range(15):
  after=d.shell('settings get secure enabled_notification_listeners').strip()
  present=any(item in after.split(':') for item in (component,'dev.tvassistant/.MediaAccessService'))
  if present!=revoke:break
  time.sleep(.2)
 entries=[] if current in ('','null') else current.split(':')
 actual=[] if after in ('','null') else after.split(':')
 own=lambda s:s in (component,'dev.tvassistant/.MediaAccessService')
 if any(item not in actual for item in entries if not own(item)):raise SystemExit('A pre-existing listener disappeared; inspect before proceeding.')
 if any(own(item) for item in actual)==revoke:raise SystemExit('Media listener access did not verify.')
 print('TV Assistant media-session access '+('removed' if revoke else 'enabled')+'; existing listeners preserved.')
finally:d.close()
