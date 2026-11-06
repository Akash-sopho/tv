import device as tv
"""Reboot once, then verify a new boot and the selected assistant binding."""
from pathlib import Path
import importlib.util,json,os,socket,time
root=Path(__file__).resolve().parents[1]
host=os.environ.get('TV_HOST',tv.HOST)
if not tv.HOST:raise SystemExit('Set TV_HOST for the network reboot/closure verification script.')
d=tv.connect()
before=d.shell('cat /proc/sys/kernel/random/boot_id').strip()
if not before:raise SystemExit('Cannot identify the current boot; reboot not issued.')
start=time.monotonic()
try:d.shell('reboot')
except Exception:pass # Reboot normally closes the ADB transport.
finally:d.close()
print('Reboot issued; waiting for a new completed boot and assistant binding.',flush=True)
deadline=time.monotonic()+180
while time.monotonic()<deadline:
 time.sleep(2)
 try:
  with socket.create_connection((host,5555),timeout=1):pass
 except OSError:continue
 device=None
 try:
  device=tv.connect()
  boot=device.shell('cat /proc/sys/kernel/random/boot_id').strip()
  complete=device.shell('getprop sys.boot_completed').strip()
  role=device.shell('dumpsys voiceinteraction')
  if boot==before or complete!='1' or 'mComponent=dev.tvassistant/.AssistantService' not in role or 'mBound=true mService=' not in role:continue
  report={'new_boot_verified':True,'boot_completed':True,'assistant_role_and_binding':True,'elapsed_seconds':round(time.monotonic()-start,1),'physical_remote_test':'pending','debugging':'still enabled for final tests'}
  (root/'.local/reboot-check.json').write_text(json.dumps(report,indent=2)+'\n')
  print(json.dumps(report,indent=2),flush=True);break
 except Exception:continue
 finally:
  if device is not None:device.close()
else:raise SystemExit('Reboot/binding did not verify within 180 seconds. No success claimed.')
