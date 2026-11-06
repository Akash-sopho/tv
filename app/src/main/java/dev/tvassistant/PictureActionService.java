package dev.tvassistant;

import android.accessibilityservice.AccessibilityService;
import android.content.*;
import android.os.*;
import android.view.accessibility.*;
import android.widget.Toast;
import dev.tvassistant.core.Command;
import java.util.*;
import dev.tvassistant.catalog.Catalog;
import dev.tvassistant.core.Interpreter;

/** Request-scoped picture and provider adapter. No passive UI collection or key filtering. */
public final class PictureActionService extends AccessibilityService {
 interface Result {void done(boolean ok,String message);}
 private static PictureActionService connected;
 private static final String PKG="com.android.tv.settings", PREFIX=PKG+":id/";
 private final Handler handler=new Handler(Looper.getMainLooper());
 private Command request;private Result completion;private long deadline;
 private int before=-1,target=-1,last=-1,stalls,steps;private boolean opened,undo;
 private String preset="";private boolean presetRead,enteredBrightness;private int readinessWaits;
 private int generation;
 private boolean modeChange;private String modeBefore="",modeTarget="";private final Set<String> modesSeen=new HashSet<String>();
 private String lastModeObserved="";private int modeStalls;private Map<String,?> replacedReceipt=Collections.emptyMap();
 private String sourceAtStart="";
 private final Runnable tick=new Runnable(){public void run(){step();}};
 static boolean available(){return connected!=null;}
 static boolean foreground(String pkg){
  if(connected==null)return false;
  AccessibilityNodeInfo root=connected.getRootInActiveWindow();
  return root!=null && pkg.contentEquals(root.getPackageName()==null?"":root.getPackageName());
 }
 static boolean inspectPrime(String title,int year,LocalScreenReader.Callback callback){return connected!=null && LocalScreenReader.inspect(connected,"com.amazon.amazonvideo.livingroom",title,year,callback);}
 static boolean providerKey(String pkg,boolean up){return foreground(pkg) && connected.performGlobalAction(up?GLOBAL_ACTION_DPAD_UP:GLOBAL_ACTION_DPAD_CENTER);}
 static boolean busy(){return connected!=null && connected.request!=null;}
 static boolean verifiedContentTitle(String pkg,String title,int year){
  if(connected==null || !Interpreter.apps().containsValue(pkg))return false;
  AccessibilityNodeInfo root=connected.getRootInActiveWindow();
  if(root==null || !pkg.contentEquals(root.getPackageName()==null?"":root.getPackageName()))return false;
  String wanted=Catalog.normalize(title);if(wanted.isEmpty())return false;
  if(pkg.equals("com.stremio.one")){
   for(AccessibilityNodeInfo label:root.findAccessibilityNodeInfosByViewId(pkg+":id/meta_details_label")){
    if(label.isVisibleToUser() && label.getText()!=null && year>0 &&
       Catalog.normalize(title+" ("+year+")").equals(Catalog.normalize(label.getText().toString())))return true;
   }
   return false;
  }
  AccessibilityWindowInfo window=root.getWindow();
  if(window!=null && window.getTitle()!=null && wanted.equals(Catalog.normalize(window.getTitle().toString())))return true;
  return paneMatches(root,wanted,0,new int[]{80});
 }
 private static boolean paneMatches(AccessibilityNodeInfo root,String wanted,int depth,int[] budget){
  if(depth>6 || --budget[0]<0)return false;
  if(root.getPaneTitle()!=null && wanted.equals(Catalog.normalize(root.getPaneTitle().toString())))return true;
  for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo child=root.getChild(i);if(child!=null && paneMatches(child,wanted,depth+1,budget))return true;}
  return false;
 }
 static boolean playVerifiedTitle(String pkg,String title,int year){
  if(!verifiedContentTitle(pkg,title,year))return false;
  AccessibilityNodeInfo root=connected.getRootInActiveWindow();if(root==null)return false;
  for(String payment:new String[]{"Buy","Rent","Subscribe","Start free trial","Join Prime"})if(connected.label(root,payment)!=null)return false;
  List<AccessibilityNodeInfo> buttons=new ArrayList<AccessibilityNodeInfo>();
  for(String text:new String[]{"Play","Resume","Watch Now"}){
   AccessibilityNodeInfo label=connected.label(root,text);if(label==null)continue;
   for(int i=0;label!=null && i<3;i++,label=label.getParent())if(label.isClickable()){buttons.add(label);break;}
  }
  return buttons.size()==1 && buttons.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
 }
 static void cancelActive(){if(connected!=null){connected.generation++;connected.finish(false,"Picture adjustment stopped. Any changes already applied remain visible in Settings.");}}
 static boolean execute(Command c,Result result){
  if(connected==null)return false;
  connected.begin(c,result);return true;
 }
 @Override protected void onServiceConnected(){connected=this;Diagnostics.count(this,"picture_service_ready");}
 @Override public void onDestroy(){if(connected==this)connected=null;finish(false,"Picture control disconnected.");super.onDestroy();}
 @Override public void onInterrupt(){finish(false,"Picture control interrupted.");}
 @Override public void onAccessibilityEvent(AccessibilityEvent event){} // No passive UI collection.
 private void begin(Command c,Result result){
  if(request!=null){result.done(false,"A picture change is already running.");return;}
  request=c;completion=result;deadline=SystemClock.elapsedRealtime()+25000;
  getSharedPreferences("diagnostics",0).edit().putBoolean("picture_action_ok",false).putString("picture_action_message","In progress").apply();
  generation++;
  before=-1;target=-1;last=-1;stalls=0;steps=0;opened=false;undo=c.kind==Command.Kind.UNDO;preset="";presetRead=false;enteredBrightness=false;readinessWaits=0;
  if(c.kind!=Command.Kind.ADJUST_BACKLIGHT && c.kind!=Command.Kind.SET_BACKLIGHT && c.kind!=Command.Kind.SET_PICTURE_MODE && !undo){finish(false,"That picture action is not connected yet.");return;}
  SharedPreferences receipt=getSharedPreferences("picture_undo",0);
  String source=InputActions.currentSource(this);sourceAtStart=source==null?"":source;
  String savedSource=receipt.getString("source","");
  if(undo && !savedSource.isEmpty() && !savedSource.equals(sourceAtStart)){finish(false,"The active TV input changed; undo was cancelled.");return;}
  modeChange=c.kind==Command.Kind.SET_PICTURE_MODE || (undo && "mode".equals(receipt.getString("kind","brightness")));
  modeBefore="";modeTarget=undo?receipt.getString("before_mode",""):c.query;modesSeen.clear();lastModeObserved="";modeStalls=0;
  replacedReceipt=new HashMap<String,Object>(receipt.getAll());
  long age=System.currentTimeMillis()-receipt.getLong("at",0);
  if(undo && ((!receipt.contains("before") && !receipt.contains("before_mode")) || age<0 || age>120000)){finish(false,"No recent picture change is available to undo.");return;}
  try{startActivity(new Intent("android.settings.DISPLAY_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));schedule(450);}
  catch(RuntimeException error){finish(false,"The TV picture menu could not open.");}
 }
 private void schedule(int delay){handler.removeCallbacks(tick);handler.postDelayed(tick,delay);}
 private String value(AccessibilityNodeInfo n){return n==null || n.getText()==null?"":n.getText().toString().trim();}
 private AccessibilityNodeInfo id(AccessibilityNodeInfo root,String name){
  List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByViewId(PREFIX+name);
  for(AccessibilityNodeInfo node:nodes)if(node.isVisibleToUser())return node;return null;
 }
 private AccessibilityNodeInfo label(AccessibilityNodeInfo root,String text){
  if(root==null)return null;
  for(AccessibilityNodeInfo found:root.findAccessibilityNodeInfosByText(text))if(found.isVisibleToUser() && text.equalsIgnoreCase(value(found)))return found;
  if(root.isVisibleToUser() && text.equalsIgnoreCase(value(root)))return root;
  for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo found=label(root.getChild(i),text);if(found!=null)return found;}
  return null;
 }
 private AccessibilityNodeInfo row(AccessibilityNodeInfo label){
  AccessibilityNodeInfo node=label;
  for(int i=0;node!=null && i<6;i++,node=node.getParent())if(node.isFocusable() && node.isClickable())return node;
  return null;
 }
 private boolean focus(AccessibilityNodeInfo node){return node!=null && (node.isFocused() || node.performAction(AccessibilityNodeInfo.ACTION_FOCUS));}
 private void step(){
  if(request==null)return;
  if(!sourceAtStart.isEmpty() && !sourceAtStart.equals(InputActions.currentSource(this))){finish(false,"The active TV input changed. No further picture steps were applied.");return;}
  if(SystemClock.elapsedRealtime()>deadline){finish(false,"Brightness change timed out. Check the value shown in Settings.");return;}
  AccessibilityNodeInfo root=getRootInActiveWindow();
  if(root==null || !PKG.contentEquals(root.getPackageName()==null?"":root.getPackageName())){
   if(opened){finish(false,"Stopped: the picture menu is no longer active.");return;}
   schedule(250);return;
  }
  opened=true;
  String page=value(id(root,"page_title"));
  if("Picture".equalsIgnoreCase(page)){
   if(enteredBrightness){finish(false,"Brightness adjustment stopped because you left its control.");return;}
   preset="";
   AccessibilityNodeInfo mode=label(root,"Picture Preset");
   if(mode!=null){AccessibilityNodeInfo parent=row(mode);if(parent!=null)preset=value(id(parent,"tv_element_switch_text"));}
   // TCL can omit intermediate layout nodes from an accessibility-service tree.
   // On the verified Picture page the preset is the first switch-text control.
   if(preset.isEmpty() && mode!=null)preset=value(id(root,"tv_element_switch_text"));
   if(preset.isEmpty()){if(readinessWaits++<6){schedule(250);return;}finish(false,"The active picture preset could not be verified. No brightness change was made.");return;}
   presetRead=true;
   if(modeChange){adjustMode(root,mode);return;}
   if(undo && !getSharedPreferences("picture_undo",0).getString("preset","").equals(preset)){finish(false,"Picture preset changed; undo was cancelled.");return;}
   AccessibilityNodeInfo brightness=row(label(root,"Brightness"));
   if(brightness==null || !brightness.performAction(AccessibilityNodeInfo.ACTION_CLICK)){finish(false,"The expected Brightness menu is unavailable.");return;}
   schedule(300);return;
  }
  if(!"Brightness".equalsIgnoreCase(page)){finish(false,"Stopped: the expected Brightness page is not visible.");return;}
  if(!presetRead){if(!performGlobalAction(GLOBAL_ACTION_BACK)){finish(false,"Could not verify the picture preset.");return;}schedule(250);return;}
  enteredBrightness=true;
  AccessibilityNodeInfo name=label(root,"Brightness");
  // Exclude the page heading; select the resource-labelled control.
  for(AccessibilityNodeInfo candidate:root.findAccessibilityNodeInfosByViewId(PREFIX+"tv_element_item_progress_left_text"))
   if(candidate.isVisibleToUser() && "Brightness".equalsIgnoreCase(value(candidate))){name=candidate;break;}
  AccessibilityNodeInfo control=row(name);
  if(control==null){finish(false,"The actual brightness control could not be identified.");return;}
  int current;
  try{current=Integer.parseInt(value(id(control,"tv_element_item_progress_right_text")));}
  catch(RuntimeException error){finish(false,"The current brightness value could not be read.");return;}
  if(current<0 || current>100){finish(false,"Unexpected brightness range. No further changes.");return;}
  if(before<0){
   before=current;
   SharedPreferences receipt=getSharedPreferences("picture_undo",0);
   if(undo && current!=receipt.getInt("after",-1)){finish(false,"Brightness changed since the last command; undo was cancelled.");return;}
   target=undo?receipt.getInt("before",-1):request.kind==Command.Kind.SET_BACKLIGHT?request.amount:Math.max(0,Math.min(100,current+request.amount));
   if(target<0 || target>100){finish(false,"Invalid target brightness.");return;}
   if(!undo && current!=target){
    // Save before the first key so a partial/failed change can also be undone.
    receipt.edit().clear().putString("kind","brightness").putString("source",sourceAtStart).putInt("before",before).putInt("after",before).putString("preset",preset).putLong("at",System.currentTimeMillis()).commit();
   }
  }
  if(!undo && current!=before)getSharedPreferences("picture_undo",0).edit().putInt("after",current).commit();
  if(current==target){
   if(undo)getSharedPreferences("picture_undo",0).edit().clear().commit();
   getSharedPreferences("diagnostics",0).edit().putInt("picture_before",before).putInt("picture_after",current).apply();
   finish(true,undo?"Brightness restored to "+current+".":"Brightness "+before+" to "+current+". Say undo within two minutes to restore.");return;
  }
  if(current==last)stalls++;else stalls=0;
  if(stalls>=4 || steps>=105){finish(false,"The brightness control did not respond reliably. Stopped.");return;}
  if(!focus(control)){finish(false,"The brightness control could not receive focus.");return;}
  int action=current<target?GLOBAL_ACTION_DPAD_RIGHT:GLOBAL_ACTION_DPAD_LEFT;
  if(!performGlobalAction(action)){finish(false,"This TV did not accept the picture adjustment.");return;}
  steps++;last=current;schedule(180);
 }
 private boolean sameMode(String a,String b){
  a=a.toLowerCase(Locale.ROOT);b=b.toLowerCase(Locale.ROOT);
  if(a.equals("cinema"))a="movie";if(b.equals("cinema"))b="movie";
  if(a.equals("sport"))a="sports";if(b.equals("sport"))b="sports";
  return a.equals(b);
 }
 private void adjustMode(AccessibilityNodeInfo root,AccessibilityNodeInfo label){
  SharedPreferences receipt=getSharedPreferences("picture_undo",0);
  if(modeBefore.isEmpty()){
   modeBefore=preset;
   if(undo && !sameMode(preset,receipt.getString("after_mode",""))){finish(false,"Picture preset changed since the last command; undo was cancelled.");return;}
  }
  if(sameMode(preset,modeTarget)){
   if(undo)receipt.edit().clear().commit();
   else if(!sameMode(modeBefore,preset))receipt.edit().clear().putString("kind","mode").putString("source",sourceAtStart).putString("before_mode",modeBefore).putString("after_mode",preset).putLong("at",System.currentTimeMillis()).commit();
   getSharedPreferences("diagnostics",0).edit().putString("picture_mode_before",modeBefore).putString("picture_mode_after",preset).apply();
   finish(true,undo?"Picture mode restored to "+preset+".":sameMode(modeBefore,preset)?"Picture mode is already "+preset+".":"Picture mode changed to "+preset+". Say undo within two minutes to restore.");return;
  }
  if(steps>0 && preset.equals(lastModeObserved)){
   if(modeStalls++<5){schedule(250);return;}finish(false,"Picture mode did not respond reliably. Check the mode shown in Settings.");return;
  }
  modeStalls=0;lastModeObserved=preset;
  if(steps>0 && sameMode(preset,modeBefore)){
   if(!undo)restoreReceipt(receipt);
   finish(false,"That preset is unavailable in this picture context. Original mode restored.");return;
  }
  if(!modesSeen.add(preset.toLowerCase(Locale.ROOT)) || steps>=12){finish(false,"Picture mode did not respond reliably. Check the mode shown in Settings.");return;}
  if(!undo){
   if(steps==0)receipt.edit().clear().putString("kind","mode").putString("source",sourceAtStart).putString("before_mode",modeBefore).putString("after_mode",preset).putLong("at",System.currentTimeMillis()).commit();
   else receipt.edit().putString("after_mode",preset).commit();
  }else if(steps>0)receipt.edit().putString("after_mode",preset).commit();
  AccessibilityNodeInfo control=row(label);
  if(!focus(control) || !performGlobalAction(GLOBAL_ACTION_DPAD_RIGHT)){finish(false,"The TV did not accept the picture mode change.");return;}
  steps++;schedule(350);
 }
 private void restoreReceipt(SharedPreferences receipt){
  SharedPreferences.Editor e=receipt.edit().clear();
  for(Map.Entry<String,?> entry:replacedReceipt.entrySet()){
   Object v=entry.getValue();String k=entry.getKey();
   if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);
  }
  e.commit();
 }
 private void finish(boolean ok,String message){
  handler.removeCallbacks(tick);
  if(request==null)return;
  request=null;Result result=completion;completion=null;
  getSharedPreferences("diagnostics",0).edit().putBoolean("picture_action_ok",ok).putString("picture_action_message",message).apply();
  Toast.makeText(this,message,Toast.LENGTH_LONG).show();
  if(result!=null)result.done(ok,message);
  if(ok)closePictureMenu(generation,0);
 }
 private void closePictureMenu(final int token,final int count){
  if(count>=3 || token!=generation || request!=null)return;
  handler.postDelayed(new Runnable(){public void run(){
   if(token!=generation || request!=null)return;
   AccessibilityNodeInfo root=getRootInActiveWindow();
   if(root==null || !PKG.contentEquals(root.getPackageName()==null?"":root.getPackageName()))return;
   String page=value(id(root,"page_title"));
   if(("Brightness".equalsIgnoreCase(page) || "Picture".equalsIgnoreCase(page)) && performGlobalAction(GLOBAL_ACTION_BACK))closePictureMenu(token,count+1);
  }},250);
 }
}
