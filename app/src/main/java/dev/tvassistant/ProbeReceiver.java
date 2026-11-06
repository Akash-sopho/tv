package dev.tvassistant;

import android.content.*;

/** Manifest DUMP permission confines diagnostics/configuration to system/debug shell. */
public final class ProbeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if("dev.tvassistant.UI_PREVIEW".equals(intent.getAction())){setResultCode(AssistantService.preview()?0:1);return;}
        if("dev.tvassistant.MEDIA_ONCE".equals(intent.getAction())){setResultData(new MediaActions(context).inspect());return;}
        if("dev.tvassistant.OCR_ONCE".equals(intent.getAction())){
            final PendingResult pending=goAsync();
            boolean started=PictureActionService.inspectPrime(intent.getStringExtra("title"),intent.getIntExtra("year",0),new LocalScreenReader.Callback(){public void result(LocalScreenReader.Facts f){
                pending.setResultData("title="+f.title+" year="+f.year+" member="+f.member+" profile="+f.profile+" focusedProfile="+f.profileFocused+" play="+f.playFocused+" secondary="+f.secondaryFocused+" ms="+f.elapsedMs+" "+f.debug);pending.finish();
            }});
            if(!started){pending.setResultData("Screen reader unavailable or Prime not foreground");pending.finish();}return;
        }
        if("dev.tvassistant.TEST_REQUEST".equals(intent.getAction())){
            String text=intent.getStringExtra("text");
            if(text==null || text.length()>300 || !AssistantPanel.testRequest(text)){setResultCode(1);setResultData("Open TV Assistant before replaying a bounded test request");return;}
        }
        if("dev.tvassistant.TEST_PICTURE".equals(intent.getAction())){
            try{dev.tvassistant.core.Command c=intent.getBooleanExtra("undo",false)?dev.tvassistant.core.Command.simple(dev.tvassistant.core.Command.Kind.UNDO):dev.tvassistant.core.Command.adjustment(dev.tvassistant.core.Command.Kind.ADJUST_BACKLIGHT,intent.getIntExtra("delta",-1));
                PictureActionService.execute(c,new PictureActionService.Result(){public void done(boolean ok,String message){}});
            }catch(RuntimeException e){setResultCode(1);setResultData("Picture test rejected");return;}
        }
        setResultCode(0);setResultData(Diagnostics.snapshot(context).toString());
    }
}
