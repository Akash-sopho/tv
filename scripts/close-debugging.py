import device as tv
"""Close TV debugging and verify its previously advertised network endpoints close."""
import importlib.util,json,socket,time
from pathlib import Path
workspace=Path(__file__).resolve().parents[1]
if not tv.HOST:raise SystemExit('Set TV_HOST for the network reboot/closure verification script.')
device=tv.connect()
ports={tv.PORT}
try:
 value=device.shell('getprop service.adb.tls.port').strip()
 if value.isdigit() and 0<int(value)<65536:ports.add(int(value))
 device.shell('settings put global adb_wifi_enabled 0')
 if device.shell('settings get global adb_wifi_enabled').strip()!='0':raise SystemExit('Wireless debugging setting did not verify off.')
 try:device.shell('settings put global adb_enabled 0',read_timeout_s=5)
 except Exception:pass  # adbd may terminate its own transport; port checks decide success.
finally:
 try:device.close()
 except Exception:pass
closed=False
for attempt in range(5):
 time.sleep(1)
 opened=[]
 for port in sorted(ports):
  try:
   with socket.create_connection((tv.HOST,port),timeout=1):opened.append(port)
  except OSError as error:
   if getattr(error,'winerror',None)==10013:raise SystemExit('Local network access denied; cannot claim debugging ports closed.')
 if not opened:closed=True;break
report={'host':tv.HOST,'checked_ports':sorted(ports),'all_closed':closed,'time_utc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())}
(workspace/'.local/debugging-closure.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
if not closed:raise SystemExit('Some debugging endpoints remain reachable. Restart the TV from its Settings and verify again.')
print('Debugging disabled; previously advertised network debugging endpoints are closed.')
