"""Build the native companion with official Android SDK tools and Eclipse ECJ.

ECJ avoids javac's failing Path.toRealPath calls in this Windows sandbox. No elevation,
filesystem permission changes, Gradle daemon or app runtime dependencies are needed.
All intermediate files stay under build/portable; the signed APK is under build/.
"""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile

from bootstrap import prepare, ROOT as project, CACHE
prepare()
build=project/'build/portable'
sdk_value=os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
if not sdk_value:raise SystemExit('Set ANDROID_SDK_ROOT to an Android SDK with platform 34 and build-tools 34.0.0.')
sdk=Path(sdk_value)
tools=sdk/'build-tools/34.0.0'
android=sdk/'platforms/android-34/android.jar'
java_home=os.environ.get('JAVA_HOME')
java=Path(java_home)/'bin'/('java.exe' if os.name=='nt' else 'java') if java_home else Path(shutil.which('java') or 'java')
compiler=CACHE/'ecj.jar'
libraries=[CACHE/'ocr-classes.jar']
d8=Path(os.environ.get('TV_ASSISTANT_D8',str(tools/'lib/d8.jar')))
ocr_deps=CACHE
exe='.exe' if os.name=='nt' else ''

def run(label, args, capture=False):
    print(label, flush=True)
    result = subprocess.run([str(x) for x in args], check=True, cwd=project,
                            capture_output=capture, text=capture)
    if capture:
        print(result.stdout, end='', flush=True)
    return result

def compile_sources(destination, sources):
    destination.mkdir(parents=True, exist_ok=True)
    run('Compiling Java: '+destination.name, [java, '-jar', compiler, '-source', '8', '-target', '8',
        '-encoding', 'UTF-8', '-proc:none', '-bootclasspath', android,
        '-classpath', os.pathsep.join(str(p) for p in libraries), '-d', destination, *sources])

def main():
    for path in (java, compiler, android, tools/('aapt'+exe), tools/('zipalign'+exe), tools/'lib/d8.jar', tools/'lib/apksigner.jar'):
        if not path.is_file():
            raise SystemExit('Missing tool: '+str(path))
    build.mkdir(parents=True, exist_ok=True)
    core=list((project/'app/src/main/java/dev/tvassistant/core').glob('*.java'))
    catalog=list((project/'app/src/main/java/dev/tvassistant/catalog').glob('*.java'))
    compile_sources(build/'tests', core+catalog+list((project/'tests').glob('*.java')))
    test_classpath=os.pathsep.join([str(build/'tests'),str(CACHE/'json.jar')])
    core_checks=0
    for spec in ('CoreSpec','CatalogSpec'):
        checks=run('Checking '+spec,[java,'-cp',test_classpath,'dev.tvassistant.tests.'+spec],capture=True)
        core_checks+=int(re.search(r'PASS: (\d+)',checks.stdout).group(1))
    if '--test-only' in sys.argv:return
    generated=build/'generated'
    classes=build/'classes'
    dex=build/'dex'
    for p in (generated,classes,dex):
        p.mkdir(parents=True,exist_ok=True)
    for old_class in classes.rglob('*.class'):
        old_class.unlink()
    import xml.etree.ElementTree as ET
    manifest=ET.parse(project/'app/src/main/AndroidManifest.xml')
    manifest.getroot().set('package','dev.tvassistant')
    manifest.getroot().set('{http://schemas.android.com/apk/res/android}versionCode','2')
    manifest.getroot().set('{http://schemas.android.com/apk/res/android}versionName','0.2-content-and-picture')
    sdk_element=ET.SubElement(manifest.getroot(),'uses-sdk')
    sdk_element.set('{http://schemas.android.com/apk/res/android}minSdkVersion','33')
    sdk_element.set('{http://schemas.android.com/apk/res/android}targetSdkVersion','34')
    manifest_file=build/'AndroidManifest.xml';manifest.write(manifest_file,encoding='utf-8')
    base=build/'resources.apk'
    run('Packaging Android resources', [tools/('aapt'+exe),'package','-f','-m','-J',generated,
        '-M',manifest_file,'-S',project/'app/src/main/res','-I',android,'-F',base])
    sources=list((project/'app/src/main/java').rglob('*.java'))+list(generated.rglob('*.java'))
    compile_sources(classes,sources)
    classjar=build/'classes.jar'
    with zipfile.ZipFile(classjar,'w',zipfile.ZIP_DEFLATED) as archive:
        for p in classes.rglob('*.class'):
            archive.write(p,p.relative_to(classes).as_posix())
    run('Converting to Android bytecode', [java,'-cp',d8,'com.android.tools.r8.D8',
        '--lib',android,'--min-api','33','--output',dex,classjar,*libraries])
    unsigned=build/'unsigned.apk'
    shutil.copyfile(base,unsigned)
    with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as archive:
        archive.write(dex/'classes.dex','classes.dex')
        for asset in (CACHE/'assets').rglob('*'):
            if asset.is_file():archive.write(asset,'assets/'+asset.relative_to(CACHE/'assets').as_posix())
        for library in (ocr_deps/'jni/armeabi-v7a').glob('*.so'):
            archive.write(library,'lib/armeabi-v7a/'+library.name)
    aligned=build/'aligned.apk'
    run('Aligning APK', [tools/('zipalign'+exe),'-f','-p','4',unsigned,aligned])
    apk=project/'build/tv-assistant.apk'
    key=Path(os.environ.get('TV_SIGNING_KEY',str(project/'.local/debug.keystore')))
    if not key.is_file():
        key.parent.mkdir(parents=True,exist_ok=True)
        keytool=Path(java_home)/'bin'/('keytool.exe' if os.name=='nt' else 'keytool') if java_home else Path(shutil.which('keytool') or 'keytool')
        run('Creating a local development signing key',[keytool,'-genkeypair','-keystore',key,'-alias','androiddebugkey','-storepass','android','-keypass','android','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Android Debug,O=Android,C=US'])
    run('Signing local development APK', [java,'-jar',tools/'lib/apksigner.jar','sign','--ks',key,
        '--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--out',apk,aligned])
    run('Verifying APK signature', [java,'-jar',tools/'lib/apksigner.jar','verify','--verbose',apk])
    report={'apk':apk.name,'bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
        'compiler':'Eclipse ECJ 3.38.0','compiler_sha256':hashlib.sha256(compiler.read_bytes()).hexdigest(),
        'sdk':34,'min_sdk':33,'core_checks':core_checks,'device_verification':'not proven by this build'}
    (project/'build/build-report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print('BUILT: '+str(apk),flush=True)

if __name__=='__main__':
    main()
