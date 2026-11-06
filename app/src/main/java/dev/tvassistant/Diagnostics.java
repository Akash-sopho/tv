package dev.tvassistant;

import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.*;
import android.media.*;
import android.os.Build;
import android.provider.Settings;
import android.speech.SpeechRecognizer;
import org.json.*;

public final class Diagnostics {
    private Diagnostics() {}
    public static JSONObject snapshot(Context context) {
        JSONObject report=new JSONObject();
        try {
            report.put("protocol",2);
            report.put("tclCurrentSource",InputActions.currentSource(context));
            JSONArray tvInputs=new JSONArray();
            for(InputActions.Input input:new InputActions(context).list())tvInputs.put(new JSONObject().put("id",input.id).put("label",input.label).put("custom",input.custom).put("state",input.state));
            report.put("tvInputs",tvInputs);
            report.put("appVersionCode",context.getPackageManager().getPackageInfo(context.getPackageName(),0).getLongVersionCode());
            report.put("sdk",Build.VERSION.SDK_INT);
            report.put("country","IN");
            report.put("microphoneGranted",context.checkSelfPermission("android.permission.RECORD_AUDIO")==PackageManager.PERMISSION_GRANTED);
            report.put("onDeviceRecognition",Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context));
            report.put("recognitionAvailable",SpeechRecognizer.isRecognitionAvailable(context));
            if(Build.VERSION.SDK_INT>=29) {
                RoleManager roles=context.getSystemService(RoleManager.class);
                report.put("assistantRoleAvailable",roles!=null && roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT));
                report.put("assistantRoleHeld",roles!=null && roles.isRoleHeld(RoleManager.ROLE_ASSISTANT));
            }
            report.put("voiceService",Settings.Secure.getString(context.getContentResolver(),"voice_interaction_service"));
            report.put("recognitionService",Settings.Secure.getString(context.getContentResolver(),"voice_recognition_service"));
            JSONArray recognizers=new JSONArray();
            for(ResolveInfo r:context.getPackageManager().queryIntentServices(new Intent("android.speech.RecognitionService"),PackageManager.GET_META_DATA))
                recognizers.put(r.serviceInfo.packageName+"/"+r.serviceInfo.name);
            report.put("recognizers",recognizers);
            JSONArray inputs=new JSONArray();
            AudioManager audio=context.getSystemService(AudioManager.class);
            if(audio!=null)for(AudioDeviceInfo d:audio.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
                JSONObject input=new JSONObject(); input.put("type",d.getType()); input.put("source",d.isSource()); inputs.put(input);
            }
            report.put("audioInputTypes",inputs);
            SharedPreferences p=context.getSharedPreferences("diagnostics",Context.MODE_PRIVATE);
            report.put("voiceServiceReadyCount",p.getInt("voice_ready",0));
            report.put("sessionInvocationCount",p.getInt("session_shown",0));
            report.put("recognitionResultCount",p.getInt("speech_results",0));
            report.put("lastRecognitionError",p.getInt("speech_error",0));
            report.put("speechBackend","google-installed-recognition-service");
            report.put("googleProvider",p.getString("google_provider","not-invoked"));
            report.put("googleReadyCount",p.getInt("google_ready",0));
            report.put("googleResultCount",p.getInt("google_results",0));
            report.put("googleSpeechStartedCount",p.getInt("google_speech_started",0));
            report.put("googleLastError",p.getInt("google_error",0));
            report.put("googleColdReadyMs",p.getLong("google_cold_ready_ms",-1));
            report.put("googleWarmReadyMs",p.getLong("google_warm_ready_ms",-1));
            report.put("googleLastResultMs",p.getLong("google_last_result_ms",-1));
            report.put("contentActionOk",p.getBoolean("content_action_ok",false));
            report.put("contentActionStatus",p.getString("content_action_status","not-tested"));
            report.put("contentRequestCount",p.getInt("content_requests",0));
            report.put("interpretedRequestCount",p.getInt("interpreted_requests",0));
            report.put("contentLaunchPackage",p.getString("content_launch_pkg","not-tested"));
            report.put("contentVerifyMethod",p.getString("content_verify_method","not-tested"));
            report.put("contentLaunchLatencyMs",p.getLong("content_launch_latency_ms",-1));
            report.put("pictureModeBefore",p.getString("picture_mode_before","not-tested"));
            report.put("pictureModeAfter",p.getString("picture_mode_after","not-tested"));
            report.put("pictureServiceConnected",PictureActionService.available());
            report.put("pictureActionInProgress",PictureActionService.busy());
            report.put("pictureActionOk",p.getBoolean("picture_action_ok",false));
            report.put("pictureActionMessage",p.getString("picture_action_message","not-tested"));
            report.put("pictureBefore",p.getInt("picture_before",-1));
            report.put("pictureAfter",p.getInt("picture_after",-1));
            report.put("separateCloudServiceEnabled",false);
            report.put("storedAudioOrScreenshotFiles",mediaFiles(context.getFilesDir())+mediaFiles(context.getCacheDir()));
            report.put("lastLocalAudioRouteType",p.getInt("audio_route_type",-1));
            report.put("assistInputDeviceId",p.getInt("assist_input_id",-1));
            report.put("assistInputVendor",p.getInt("assist_input_vendor",-1));
            report.put("assistInputProduct",p.getInt("assist_input_product",-1));
            report.put("lastAudioPeak",p.getInt("audio_peak",0));
            report.put("lastAudioSampleCount",p.getLong("audio_sample_count",0));
        } catch(Exception e) {
            try{report.put("error",e.getClass().getSimpleName());}catch(JSONException ignored){}
        }
        return report;
    }
    public static void count(Context context,String event) {
        SharedPreferences p=context.getSharedPreferences("diagnostics",Context.MODE_PRIVATE);
        p.edit().putInt(event,p.getInt(event,0)+1).apply();
    }
    private static int mediaFiles(java.io.File directory){
        int count=0;java.io.File[] files=directory.listFiles();if(files==null)return 0;
        for(java.io.File file:files){
            if(file.isDirectory())count+=mediaFiles(file);
            else if(file.getName().toLowerCase(java.util.Locale.ROOT).matches(".*\\.(wav|pcm|mp3|m4a|flac|ogg|webm|png|jpe?g)$"))count++;
        }return count;
    }
}
