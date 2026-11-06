package dev.tvassistant;

import android.content.Context;
import android.os.*;
import android.service.voice.VoiceInteractionSession;
import android.view.*;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;

public final class AssistantSession extends VoiceInteractionSession {
    private AssistantPanel panel;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable listen=new Runnable(){public void run(){if(panel!=null)panel.startSpeech();}};
    public AssistantSession(Context context){super(context);setTheme(R.style.AssistantOverlayTheme);}
    @Override public View onCreateContentView(){
        setDisabledShowContext(SHOW_WITH_ASSIST|SHOW_WITH_SCREENSHOT);
        Window window=getWindow().getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(0f);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);window.setLayout(-1,-1);
        panel=new AssistantPanel(getContext());panel.setOnActionStart(new Runnable(){public void run(){hide();}});return OverlayLayout.wrap(getContext(),panel);
    }
    @Override public void onShow(Bundle args,int flags){
        PictureActionService.cancelActive();
        super.onShow(args,flags);Diagnostics.count(getContext(),"session_shown");
        if(panel!=null)panel.reveal();
        if(args!=null && args.getBoolean("ui_preview",false)){handler.removeCallbacks(listen);if(panel!=null)panel.preview();return;}
        int inputId=args==null?-1:args.getInt(android.content.Intent.EXTRA_ASSIST_INPUT_DEVICE_ID,-1);
        InputDevice device=InputDevice.getDevice(inputId);
        getContext().getSharedPreferences("diagnostics",0).edit().putInt("assist_input_id",inputId)
          .putInt("assist_input_vendor",device==null?-1:device.getVendorId())
          .putInt("assist_input_product",device==null?-1:device.getProductId()).apply();
        handler.removeCallbacks(listen);handler.postDelayed(listen,300);
    }
    @Override public void onHide(){handler.removeCallbacks(listen);if(panel!=null)panel.onHidden();super.onHide();}
    @Override public void onComputeInsets(Insets out){
        super.onComputeInsets(out);if(panel==null)return;
        int[] location=new int[2];panel.getLocationInWindow(location);
        out.contentInsets.set(0,location[1],0,0);out.touchableInsets=Insets.TOUCHABLE_INSETS_REGION;
        out.touchableRegion.set(location[0],location[1],location[0]+panel.getWidth(),location[1]+panel.getHeight());
    }
    @Override public void onDestroy(){handler.removeCallbacks(listen);if(panel!=null)panel.release();super.onDestroy();}
    @Override public void onBackPressed(){if(panel!=null)panel.cancelRequest();hide();}
}
