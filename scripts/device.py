"""Developer-only ADB adapter. The installed app never uses ADB or this module."""
from pathlib import Path
import os,subprocess,shutil
ROOT=Path(__file__).resolve().parents[1]
HOST=os.environ.get('TV_HOST')
PORT=int(os.environ.get('TV_ADB_PORT','5555'))
def adb_path():
 value=os.environ.get('ADB') or shutil.which('adb')
 if not value:
  sdk=os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
  if sdk:value=str(Path(sdk)/'platform-tools'/('adb.exe' if os.name=='nt' else 'adb'))
 if not value:raise SystemExit('Install Android platform-tools and put adb on PATH, or set ADB.')
 return value
class Device:
 def __init__(self,serial):self.serial=serial
 def run(self,*args,timeout=40):
  p=subprocess.run([adb_path(),'-s',self.serial,*map(str,args)],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=timeout)
  if p.returncode:raise RuntimeError((p.stderr or p.stdout).strip())
  return p.stdout
 def shell(self,command,read_timeout_s=30):return self.run('shell',command,timeout=read_timeout_s)
 def close(self):pass
def connect():
 (ROOT/'.local').mkdir(exist_ok=True)
 serial=os.environ.get('TV_SERIAL')
 if HOST:
  serial=HOST+':'+str(PORT)
  p=subprocess.run([adb_path(),'connect',serial],capture_output=True,text=True,timeout=15)
  if p.returncode:raise SystemExit('ADB connection failed. Check debugging and authorize this computer on the TV.')
 if not serial:
  p=subprocess.run([adb_path(),'devices'],capture_output=True,text=True,check=True,timeout=15)
  ready=[line.split()[0] for line in p.stdout.splitlines() if line.endswith('\tdevice')]
  if len(ready)!=1:raise SystemExit('Set TV_SERIAL or TV_HOST, or connect exactly one authorized USB device.')
  serial=ready[0]
 d=Device(serial)
 if d.run('get-state').strip()!='device':raise SystemExit('ADB device is not ready.')
 return d
