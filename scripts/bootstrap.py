"""Fetch pinned dependencies into the ignored project cache. Python standard library only."""
from pathlib import Path
import hashlib,urllib.request,zipfile,shutil
ROOT=Path(__file__).resolve().parents[1]
CACHE=ROOT/'.cache/deps'
ARTIFACTS=[
 ('ecj.jar','https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.38.0/ecj-3.38.0.jar','97c566b120009c203a2fc8b291f4a9adbc171cf1ccb70f06f6b4e1828c00ce8e'),
 ('json.jar','https://repo.maven.apache.org/maven2/org/json/json/20250517/json-20250517.jar','3ea61b2a06e31edf1c91134fe9106b0ebb16628be169f3db75bc7a2b06b45796'),
 ('ocr.aar','https://jitpack.io/cz/adaptech/tesseract4android/tesseract4android/4.9.0/tesseract4android-4.9.0.aar','bce5d6413a1a5ae3d7240033fbbc851ba3217d0a08d9769400e17a077f42cb2a'),
 ('eng.traineddata','https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/eng.traineddata','7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2')]
def prepare():
 CACHE.mkdir(parents=True,exist_ok=True)
 for name,url,digest in ARTIFACTS:
  p=CACHE/name
  if not p.is_file():
   print('Downloading '+name,flush=True)
   with urllib.request.urlopen(url,timeout=60) as response:data=response.read()
   if hashlib.sha256(data).hexdigest()!=digest:raise SystemExit('Dependency hash mismatch: '+name)
   p.write_bytes(data)
  if hashlib.sha256(p.read_bytes()).hexdigest()!=digest:raise SystemExit('Dependency hash mismatch: '+name)
 with zipfile.ZipFile(CACHE/'ocr.aar') as archive:
  (CACHE/'ocr-classes.jar').write_bytes(archive.read('classes.jar'))
  for name in archive.namelist():
   if name.startswith('jni/armeabi-v7a/') and name.endswith('.so'):archive.extract(name,CACHE)
 assets=CACHE/'assets';assets.mkdir(exist_ok=True)
 shutil.copyfile(CACHE/'eng.traineddata',assets/'ocr-eng.traineddata')
 shutil.copytree(ROOT/'licenses',assets/'third-party-licenses',dirs_exist_ok=True)
 print('Pinned dependencies verified.',flush=True)
if __name__=='__main__':prepare()
