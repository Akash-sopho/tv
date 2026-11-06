package dev.tvassistant;

import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.speech.*;
import java.util.*;

/** Google's installed recognizer owns audio capture; this app receives transient text. */
final class GoogleSpeech {
 private final Context context;
 private final SpeechController.Listener listener;
 private final Handler main=new Handler(Looper.getMainLooper());
 private SpeechRecognizer recognizer;
 private int generation;
 private boolean ready;
 private static boolean firstInvocation=true;
 private boolean coldRun;private long begunAt;
 private Runnable timeout;
 private static final ComponentName TV=new ComponentName("com.google.android.katniss","com.google.android.apps.tvsearch.voice.recognition.KatnissRecognitionService");
 private static final ComponentName TTS=new ComponentName("com.google.android.tts","com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService");
 GoogleSpeech(Context c,SpeechController.Listener l){context=c.getApplicationContext();listener=l;}
 void start(){
  cancel();
  begunAt=SystemClock.elapsedRealtime();coldRun=firstInvocation;firstInvocation=false;
  if(context.checkSelfPermission("android.permission.RECORD_AUDIO")!=PackageManager.PERMISSION_GRANTED){listener.state("Microphone permission is required.");return;}
  List<ComponentName> providers=new ArrayList<ComponentName>();
  for(ComponentName candidate:new ComponentName[]{TV,TTS})
   for(ResolveInfo r:context.getPackageManager().queryIntentServices(new Intent(RecognitionService.SERVICE_INTERFACE).setComponent(candidate),0))
    if(r.serviceInfo!=null){providers.add(candidate);break;}
  if(providers.isEmpty()){listener.state("Google speech recognition is unavailable on this TV.");return;}
  listen(providers,0,generation);
 }
 private void destroy(){
  if(timeout!=null){main.removeCallbacks(timeout);timeout=null;}
  if(recognizer!=null){SpeechRecognizer old=recognizer;recognizer=null;try{old.cancel();}finally{old.destroy();}}
 }
 void cancel(){generation++;destroy();}
 void stop(){if(recognizer!=null)recognizer.stopListening();}
 private void diagnostic(String key,int value){context.getSharedPreferences("diagnostics",0).edit().putInt(key,value).apply();}
 private void listen(final List<ComponentName> providers,final int index,final int token){
  if(token!=generation)return;
  destroy();ready=false;
  final ComponentName provider=providers.get(index);
  context.getSharedPreferences("diagnostics",0).edit().putString("google_provider",provider.flattenToShortString()).apply();
  listener.state("Loading Google speech...");
  try{
   recognizer=SpeechRecognizer.createSpeechRecognizer(context,provider);
   final SpeechRecognizer current=recognizer;
   recognizer.setRecognitionListener(new RecognitionListener(){
    private boolean live(){return token==generation && recognizer==current;}
    public void onReadyForSpeech(Bundle params){if(!live())return;ready=true;Diagnostics.count(context,"google_ready");context.getSharedPreferences("diagnostics",0).edit().putLong(coldRun?"google_cold_ready_ms":"google_warm_ready_ms",SystemClock.elapsedRealtime()-begunAt).apply();listener.state("Listening with Google. Speak into your microphone.");}
    public void onBeginningOfSpeech(){if(live())Diagnostics.count(context,"google_speech_started");}
    public void onRmsChanged(float rms){if(live())listener.level(Math.max(0,Math.min(100,(int)((rms+2)*8))));}
    public void onBufferReceived(byte[] buffer){} // Do not retain, log, forward, or write recognizer audio buffers.
    public void onEndOfSpeech(){if(live())listener.state("Transcribing with Google...");}
    public void onError(int error){
     if(!live())return;
     diagnostic("google_error",error);
     boolean providerUnavailable=!ready && (error==SpeechRecognizer.ERROR_CLIENT || error==SpeechRecognizer.ERROR_SERVER_DISCONNECTED || error==SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error==SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE);
     if(providerUnavailable && index+1<providers.size()){listen(providers,index+1,token);return;}
     destroy();listener.state(errorMessage(error));
    }
    public void onResults(Bundle data){
     if(!live())return;
     String text=first(data);destroy();
     if(text.isEmpty()){listener.state("Google returned no words. Press the mic button to try again.");return;}
     diagnostic("google_error",0);context.getSharedPreferences("diagnostics",0).edit().putLong("google_last_result_ms",SystemClock.elapsedRealtime()-begunAt).apply();Diagnostics.count(context,"google_results");Diagnostics.count(context,"speech_results");listener.result(text);
    }
    public void onPartialResults(Bundle data){if(live()){String text=first(data);if(!text.isEmpty())listener.partial(text);}}
    public void onEvent(int type,Bundle params){}
   });
   Intent request=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
   request.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
   request.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-IN");
   request.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
   request.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
   request.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,false);
   int input=context.getSharedPreferences("diagnostics",0).getInt("assist_input_id",-1);
   if(input>=0)request.putExtra(Intent.EXTRA_ASSIST_INPUT_DEVICE_ID,input);
   timeout=new Runnable(){public void run(){if(token==generation && recognizer==current){diagnostic("google_error",SpeechRecognizer.ERROR_SPEECH_TIMEOUT);destroy();listener.state("Google speech timed out. Press the mic button to try again.");}}};
   main.postDelayed(timeout,25000);
   recognizer.startListening(request);
  }catch(RuntimeException error){
   diagnostic("google_error",SpeechRecognizer.ERROR_CLIENT);destroy();
   if(index+1<providers.size())listen(providers,index+1,token);
   else listener.state("Google speech could not start. Press the mic button to try again.");
  }
 }
 private static String first(Bundle data){
  if(data==null)return "";ArrayList<String> values=data.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
  if(values!=null)for(String text:values)if(text!=null && !text.trim().isEmpty())return text.trim();return "";
 }
 private static String errorMessage(int error){
  switch(error){
   case SpeechRecognizer.ERROR_NETWORK: case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:return "Google speech needs a working internet connection. Try again.";
   case SpeechRecognizer.ERROR_NO_MATCH: case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:return "Google could not hear a clear request. Press the mic button and try again.";
   case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:return "Google speech is busy. Wait a moment, then press the mic button.";
   case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:return "Google speech could not access the microphone. Permission needs checking.";
   case SpeechRecognizer.ERROR_AUDIO:return "Google could not open the microphone. Press the mic button to retry.";
   default:return "Google speech failed (code "+error+"). No TV action was taken.";
  }
 }
}
