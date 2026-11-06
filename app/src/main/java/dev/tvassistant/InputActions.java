package dev.tvassistant;

import android.content.*;
import android.media.tv.*;
import android.net.Uri;
import android.os.*;
import dev.tvassistant.catalog.Catalog;
import java.util.*;

/** Uses only TV-reported passthrough IDs and native labels; no guessed port mapping. */
final class InputActions {
 static final class Input {
  final String id,label,custom;final int state;
  Input(String id,String label,String custom,int state){this.id=id;this.label=label;this.custom=custom;this.state=state;}
  boolean matches(String name){String n=Catalog.normalize(name);return n.equals(Catalog.normalize(label)) || (!custom.isEmpty() && n.equals(Catalog.normalize(custom)));}
 }
 private final Context context;
 private final Handler main=new Handler(Looper.getMainLooper());
 InputActions(Context c){context=c.getApplicationContext();}
 static String currentSource(Context context){try{return android.provider.Settings.Secure.getString(context.getContentResolver(),"tcl_sys_tv_input_current_device_id");}catch(RuntimeException e){return null;}}
 List<Input> list(){
  List<Input> out=new ArrayList<Input>();TvInputManager manager=context.getSystemService(TvInputManager.class);if(manager==null)return out;
  try{for(TvInputInfo info:manager.getTvInputList()){
   if(!info.isPassthroughInput() || info.isHidden(context))continue;
   CharSequence label=info.loadLabel(context),custom=info.loadCustomLabel(context);
   if(label!=null)out.add(new Input(info.getId(),label.toString(),custom==null?"":custom.toString(),manager.getInputState(info.getId())));
  }}catch(RuntimeException ignored){}
  return out;
 }
 void select(Input input,MediaActions.Result result){
  boolean known=false;for(Input current:list())if(current.id.equals(input.id)){known=true;break;}
  if(!known){result.done(false,"That input is no longer available.");return;}
  Uri uri=TvContract.buildChannelUriForPassthroughInput(input.id);
  Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,TvContract.Channels.CONTENT_ITEM_TYPE).setPackage("com.tcl.tv").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  if(context.getPackageManager().resolveActivity(intent,0)==null){result.done(false,"The TV does not expose a native route for this input.");return;}
  try{
   context.startActivity(intent);context.getSharedPreferences("picture_undo",0).edit().clear().apply();
   context.getSharedPreferences("diagnostics",0).edit().putString("input_requested_label",input.label).apply();
   verify(input,result,SystemClock.elapsedRealtime()+6000);
  }catch(RuntimeException e){result.done(false,"The TV did not accept the input switch.");}
 }
 private void verify(final Input input,final MediaActions.Result result,final long until){
  String expected=input.id.startsWith("com.tcl.tvinput/.TvPassThroughService/HW")?input.id.substring(input.id.lastIndexOf("HW")+2):"";
  String selected=currentSource(context);
  if(!expected.isEmpty() && expected.equals(selected)){
   context.getSharedPreferences("diagnostics",0).edit().putString("input_verified_label",input.label).apply();
   result.done(true,"Switched to "+input.label+(input.state==TvInputManager.INPUT_STATE_CONNECTED?".":". This input currently has no connected signal."));return;
  }
  if(SystemClock.elapsedRealtime()>=until){result.done(false,"Requested "+input.label+", but the TV did not confirm its current input.");return;}
  main.postDelayed(new Runnable(){public void run(){verify(input,result,until);}},300);
 }
}
