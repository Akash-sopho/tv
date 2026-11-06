package dev.tvassistant;

import android.app.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.KeyEvent;

public final class MainActivity extends Activity {
    private AssistantPanel panel;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);panel=new AssistantPanel(this);setContentView(OverlayLayout.wrap(this,panel));panel.reveal();
        panel.setOnActionStart(new Runnable(){public void run(){moveTaskToBack(true);}});
        if(checkSelfPermission("android.permission.RECORD_AUDIO")!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.RECORD_AUDIO"},1);
    }
    @Override protected void onStop(){panel.onHidden();super.onStop();}
    @Override protected void onResume(){super.onResume();panel.activate();}
    @Override protected void onDestroy(){panel.release();super.onDestroy();}
    @Override public boolean onKeyDown(int code,KeyEvent event){
        if(code==KeyEvent.KEYCODE_VOICE_ASSIST || code==KeyEvent.KEYCODE_SEARCH){panel.startSpeech();return true;}
        return super.onKeyDown(code,event);
    }
}
