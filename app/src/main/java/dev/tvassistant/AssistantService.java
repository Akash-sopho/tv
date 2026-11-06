package dev.tvassistant;

import android.service.voice.VoiceInteractionService;

/** System-bound assistant endpoint. Being bound does not start microphone capture. */
public final class AssistantService extends VoiceInteractionService {
    private static AssistantService connected;
    static boolean preview(){if(connected==null)return false;android.os.Bundle args=new android.os.Bundle();args.putBoolean("ui_preview",true);connected.showSession(args,0);return true;}
    @Override public void onReady(){super.onReady();connected=this;Diagnostics.count(this,"voice_ready");}
    @Override public void onShutdown(){if(connected==this)connected=null;super.onShutdown();}
}
