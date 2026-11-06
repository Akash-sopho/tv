"""Install a locally built APK. Does not enable assistant/accessibility/listener access."""
from pathlib import Path
import sys
import device
apk=Path(sys.argv[1]) if len(sys.argv)>1 else device.ROOT/'build/tv-assistant.apk'
if not apk.is_file():raise SystemExit('Build the APK first, or supply its path.')
d=device.connect()
result=d.run('install','-r',apk,timeout=90)
if 'Success' not in result:raise SystemExit('Install did not report success: '+result)
print('APK installed. Open TV Assistant to grant microphone access, then follow the setup steps in README.')
