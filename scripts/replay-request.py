import device as tv
"""Replay synthetic requests through the installed production panel/interpreter/brokers.
Requires debug-shell DUMP authority. Does not synthesize or claim physical speech.
"""
import argparse,importlib.util,json,shlex,time
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('text');p.add_argument('--picture',action='store_true');p.add_argument('--keep-open',action='store_true');p.add_argument('--timeout',type=int,default=40);args=p.parse_args()
if len(args.text)>300 or any(ord(c)<32 for c in args.text):raise SystemExit('Use one bounded test request.')
root=Path(__file__).resolve().parents[1]
d=tv.connect()
def state():
 raw=d.shell('am broadcast -a dev.tvassistant.CAPABILITIES -n dev.tvassistant/.ProbeReceiver')
 try:
  value=raw.split('data="',1)[1].rsplit('"',1)[0]
  try:return json.loads(value)
  except json.JSONDecodeError:return json.loads(value.replace('\\"','"'))
 except Exception:raise SystemExit('Diagnostic response could not be parsed; no success claimed.')
try:
 initial=state()
 if initial.get('protocol')!=2:raise SystemExit('Install the protocol-2 development build before replay tests.')
 if not args.keep_open:
  result=d.shell('am start -W -n dev.tvassistant/.MainActivity')
  if 'Error' in result or 'Exception' in result:raise SystemExit('Assistant UI could not open.')
 result=d.shell('am broadcast -a dev.tvassistant.TEST_REQUEST -n dev.tvassistant/.ProbeReceiver --es text '+shlex.quote(args.text))
 if 'result=1' in result or 'Permission Denial' in result:raise SystemExit('Bounded replay was rejected.')
 deadline=time.monotonic()+args.timeout
 while time.monotonic()<deadline:
  s=state()
  if s.get('interpretedRequestCount',0)>initial.get('interpretedRequestCount',0)+1:
   raise SystemExit('Another request interrupted this replay; its outcome is not evidence for this request.')
  if args.picture:
   if not s.get('pictureServiceConnected'):raise SystemExit('Picture service is disconnected.')
   terminal=not s.get('pictureActionInProgress')
  else:terminal=s.get('contentActionStatus') in ('choice','verified','unverified','clarify','unsupported')
  if terminal:
   keys=('pictureActionOk','pictureActionMessage','pictureModeBefore','pictureModeAfter','pictureBefore','pictureAfter') if args.picture else ('contentActionOk','contentActionStatus','contentLaunchPackage','contentVerifyMethod','contentLaunchLatencyMs')
   print(json.dumps({k:s.get(k) for k in keys},indent=2))
   if s.get('interpretedRequestCount',0)<=initial.get('interpretedRequestCount',0):raise SystemExit('No new request was interpreted; stale state rejected.')
   if args.picture and not s.get('pictureActionOk'):raise SystemExit(3)
   if not args.picture and s.get('contentActionStatus') in ('unverified','clarify','unsupported'):raise SystemExit(3)
   break
  time.sleep(.4)
 else:raise SystemExit('Replay did not reach a verified/choice/failure state within its deadline.')
finally:d.close()
