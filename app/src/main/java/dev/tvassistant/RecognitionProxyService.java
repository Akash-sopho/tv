package dev.tvassistant;
import android.content.Intent;
import android.os.*;
import android.speech.*;
import java.util.ArrayList;
/** Metadata-required endpoint, explicitly delegates to installed Google, never itself. */
public final class RecognitionProxyService extends RecognitionService {
 private GoogleSpeech speech;
 protected void onStartListening(Intent request,final Callback callback){
  clear();
  speech=new GoogleSpeech(this,new SpeechController.Listener(){
   public void state(String message){
    try{
     if(message.startsWith("Listening"))callback.readyForSpeech(new Bundle());
     else if(message.startsWith("Transcribing"))callback.endOfSpeech();
     else if(!message.startsWith("Loading"))callback.error(SpeechRecognizer.ERROR_CLIENT);
    }catch(RemoteException ignored){}
   }
   private Bundle words(String text){Bundle b=new Bundle();ArrayList<String> values=new ArrayList<String>();values.add(text);b.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,values);return b;}
   public void result(String text){try{callback.results(words(text));}catch(RemoteException ignored){}}
   public void partial(String text){try{callback.partialResults(words(text));}catch(RemoteException ignored){}}
   public void level(int level){try{callback.rmsChanged(level/8f-2);}catch(RemoteException ignored){}}
  });
  speech.start();
 }
 protected void onStopListening(Callback callback){if(speech!=null)speech.stop();}
 protected void onCancel(Callback callback){clear();}
 public void onDestroy(){clear();super.onDestroy();}
 private void clear(){if(speech!=null){speech.cancel();speech=null;}}
}
