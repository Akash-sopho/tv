import device as tv
"""Remove only TV Assistant's picture accessibility service; preserve every other entry."""
from pathlib import Path
import importlib.util,shlex
workspace=Path(__file__).resolve().parents[1]
device=tv.connect()
try:
 current=device.shell('settings get secure enabled_accessibility_services').strip()
 entries=[] if current in ('','null') else current.split(':')
 keep=[v for v in entries if v not in ('dev.tvassistant/.PictureActionService','dev.tvassistant/dev.tvassistant.PictureActionService')]
 result=device.shell('settings put secure enabled_accessibility_services '+shlex.quote(':'.join(keep)))
 actual=device.shell('settings get secure enabled_accessibility_services').strip()
 if result.strip() or actual!=':'.join(keep):raise SystemExit('Picture service removal was not verified.')
 print('TV Assistant picture control disabled; other accessibility entries preserved.')
finally:device.close()
