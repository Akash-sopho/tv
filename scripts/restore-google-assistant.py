"""Restore the tested stock Google route; preserve unrelated services/listeners."""
import runpy
import device
d=device.connect()
for command in [
 'cmd role add-role-holder --user 0 android.app.role.ASSISTANT com.google.android.katniss',
 'settings put secure assistant com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService',
 'settings put secure voice_interaction_service com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService',
 'settings put secure voice_recognition_service com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService',
 'appops set --user 0 com.google.android.katniss RECORD_AUDIO_HOTWORD ignore','appops write-settings']:
 result=d.shell(command)
 if any(x in result for x in ['SecurityException','Permission Denial','Error:']):raise SystemExit('Rollback rejected: '+result)
runpy.run_path(str(device.ROOT/'scripts/disable-picture-control.py'),run_name='__main__')
import subprocess,sys
subprocess.run([sys.executable,str(device.ROOT/'scripts/media-access.py'),'--disable'],check=True)
if d.shell('settings get secure voice_interaction_service').strip()!='com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService':
 raise SystemExit('Stock voice component did not verify.')
print('Stock Google assistant restored; only our control/listener entries removed.')
