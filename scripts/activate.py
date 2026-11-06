"""Select TV Assistant and verify binding. Run only for a TV you control."""
import device
d=device.connect()
for command in ['cmd appops set dev.tvassistant AUTO_START allow','appops write-settings',
 'cmd role add-role-holder --user 0 android.app.role.ASSISTANT com.google.android.katniss',
 'cmd role add-role-holder --user 0 android.app.role.ASSISTANT dev.tvassistant']:
 result=d.shell(command)
 if any(x in result for x in ['SecurityException','Permission Denial','Error:']):raise SystemExit('Activation rejected: '+result)
state=d.shell('dumpsys voiceinteraction')
if 'mComponent=dev.tvassistant/.AssistantService' not in state or 'mBound=true mService=' not in state:
 raise SystemExit('Assistant binding not verified. Inspect the device before testing the mic.')
print('Assistant binding verified. A physical remote mic test is still required.')
