package dev.tvassistant;

import android.content.*;
import android.media.*;
import android.media.session.*;
import android.net.Uri;
import android.os.*;
import dev.tvassistant.catalog.Catalog;
import dev.tvassistant.catalog.LaunchPolicy;
import dev.tvassistant.core.*;
import java.util.*;

/** Allowlisted Android intents/transport controls, with observed playback verification. */
final class MediaActions {
 interface Result {void done(boolean verified,String message);}
 private final Context context;private final Handler main=new Handler(Looper.getMainLooper());private int generation;
 private boolean titleWasVerified,playIssued,profileIssued;private int navigationSteps;
 private long launchAt,preLaunchUpdate=-1,preLaunchPosition=-1,prePlayUpdate=-1;
 MediaActions(Context c){context=c.getApplicationContext();}
 String inspect(){
  org.json.JSONArray report=new org.json.JSONArray();
  for(MediaController c:controllers())try{
   MediaMetadata m=c.getMetadata();PlaybackState s=c.getPlaybackState();org.json.JSONObject item=new org.json.JSONObject().put("package",c.getPackageName());
   if(s!=null)item.put("state",s.getState()).put("position",s.getPosition()).put("updated",s.getLastPositionUpdateTime()).put("speed",s.getPlaybackSpeed()).put("actions",s.getActions());
   if(m!=null)for(String key:new String[]{MediaMetadata.METADATA_KEY_TITLE,MediaMetadata.METADATA_KEY_MEDIA_ID,MediaMetadata.METADATA_KEY_MEDIA_URI,MediaMetadata.METADATA_KEY_ARTIST})item.put(key,m.getString(key));
   report.put(item);
  }catch(org.json.JSONException ignored){}
  return report.toString();
 }
 void cancel(){generation++;}
 boolean installed(String pkg){try{return context.getPackageManager().getApplicationInfo(pkg,0).enabled;}catch(Exception e){return false;}}
 private List<MediaController> controllers(){
  try{return context.getSystemService(MediaSessionManager.class).getActiveSessions(new ComponentName(context,MediaAccessService.class));}
  catch(SecurityException e){return Collections.emptyList();}
 }
 private MediaController active(){
  List<MediaController> allowed=new ArrayList<MediaController>();
  for(MediaController c:controllers())if(Interpreter.apps().containsValue(c.getPackageName()))allowed.add(c);
  for(MediaController c:allowed){PlaybackState s=c.getPlaybackState();if(s!=null && s.getState()==PlaybackState.STATE_PLAYING)return c;}
  for(MediaController c:allowed){PlaybackState s=c.getPlaybackState();if(s!=null && s.getState()==PlaybackState.STATE_PAUSED)return c;}
  return null;
 }
 boolean openApp(String pkg){
  if(!Interpreter.apps().containsValue(pkg))return false;
  Intent intent=context.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
  if(intent==null)intent=context.getPackageManager().getLaunchIntentForPackage(pkg);
  if(intent==null)return false;context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return true;
 }
 void openVerified(final String pkg,final Result result){
  cancel();final int token=generation;
  if(!openApp(pkg)){result.done(false,"That app is not installed or has no TV launcher.");return;}
  verifyApp(pkg,result,token,SystemClock.elapsedRealtime()+12000);
 }
 private void verifyApp(final String pkg,final Result result,final int token,final long until){
  if(token!=generation)return;
  if(PictureActionService.foreground(pkg)){result.done(true,"Opened "+ContentCoordinator.friendly(pkg)+".");return;}
  if(SystemClock.elapsedRealtime()>=until){result.done(false,"The app launch was requested, but its foreground screen could not be verified.");return;}
  main.postDelayed(new Runnable(){public void run(){verifyApp(pkg,result,token,until);}},300);
 }
 void launch(String pkg,String url,String title,int year,boolean allowPlayback,Result result){
  if(!LaunchPolicy.allowed(pkg,url)){result.done(false,"The provider did not return a supported, safe title link.");return;}
  if(!Interpreter.apps().containsValue(pkg) && !pkg.equals("com.apple.atve.androidtv.appletv")){result.done(false,"That app is not supported.");return;}
  Intent request=new Intent(Intent.ACTION_VIEW,Uri.parse(url)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  if(context.getPackageManager().resolveActivity(request,0)==null){result.done(false,"The installed app does not accept this title link.");return;}
  preLaunchUpdate=-1;preLaunchPosition=-1;
  for(MediaController c:controllers())if(pkg.equals(c.getPackageName()) && c.getPlaybackState()!=null){preLaunchUpdate=c.getPlaybackState().getLastPositionUpdateTime();preLaunchPosition=c.getPlaybackState().getPosition();}
  try{context.startActivity(request);}catch(RuntimeException e){result.done(false,"The title link could not open.");return;}
  titleWasVerified=false;playIssued=false;profileIssued=false;navigationSteps=0;
  launchAt=SystemClock.elapsedRealtime();
  context.getSharedPreferences("diagnostics",0).edit().putString("content_launch_pkg",pkg).putString("content_verify_method","pending").apply();
  final int token=++generation;verifyLaunch(pkg,title,year,allowPlayback,result,token,SystemClock.elapsedRealtime()+(pkg.equals("com.amazon.amazonvideo.livingroom")?45000:16000));
 }
 private void verifyLaunch(final String pkg,final String title,final int year,final boolean allowPlayback,final Result result,final int token,final long until){
  if(token!=generation)return;
  for(MediaController c:controllers()){
   if(!pkg.equals(c.getPackageName()))continue;
   MediaMetadata metadata=c.getMetadata();PlaybackState state=c.getPlaybackState();
   String observed=metadata==null?"":metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
   String wanted=Catalog.normalize(title),actual=Catalog.normalize(observed==null?"":observed);
   if(!wanted.isEmpty() && wanted.equals(actual) && state!=null && state.getState()==PlaybackState.STATE_PLAYING){verified("media-session-playing");result.done(true,"Playing "+title+".");return;}
   if(pkg.equals("com.google.android.youtube.tv") && metadata!=null && state!=null &&
      state.getState()==PlaybackState.STATE_PLAYING && (state.getLastPositionUpdateTime()!=preLaunchUpdate || state.getPosition()!=preLaunchPosition) && PictureActionService.foreground(pkg) &&
      Catalog.songMetadataMatches(title,observed,metadata.getString(MediaMetadata.METADATA_KEY_ARTIST))){
    verified("fresh-youtube-song-and-artist");result.done(true,"Playing "+title+".");return;
   }
   // Prime reports the application name instead of the film name. Bind its fresh
   // playback transition to the title/year and primary Play button just verified locally.
   if(pkg.equals("com.amazon.amazonvideo.livingroom") && titleWasVerified && playIssued &&
      (actual.isEmpty() || actual.equals("primevideo")) && state!=null && state.getState()==PlaybackState.STATE_PLAYING &&
      state.getLastPositionUpdateTime()!=prePlayUpdate && PictureActionService.foreground(pkg)){
    verified("local-title-year-then-fresh-prime-playback");result.done(true,"Playing "+title+".");return;
   }
  }
  if(PictureActionService.verifiedContentTitle(pkg,title,year)){
   titleWasVerified=true;
   verified("primary-title-ui");
   if(allowPlayback && !playIssued && PictureActionService.playVerifiedTitle(pkg,title,year)){
    playIssued=true;
    main.postDelayed(new Runnable(){public void run(){verifyLaunch(pkg,title,year,allowPlayback,result,token,until);}},500);return;
   }
   if(!playIssued){result.done(true,"Opened "+title+" in the app. Playback is not confirmed.");return;}
  }
  if(SystemClock.elapsedRealtime()>=until){result.done(titleWasVerified,titleWasVerified?"Opened the verified title "+title+". Playback could not be confirmed.":"Opened the app link for "+title+", but could not verify the title or playback. Check the TV.");return;}
  if(pkg.equals("com.amazon.amazonvideo.livingroom") && !playIssued && PictureActionService.inspectPrime(title,year,new LocalScreenReader.Callback(){public void result(LocalScreenReader.Facts f){
   if(token!=generation)return;
   if(SystemClock.elapsedRealtime()>=until){result.done(false,"Prime took too long to open the requested title. Please check the TV.");return;}
   if(f.account){result.done(false,"Prime needs account attention. Please complete that on the TV.");return;}
   if(f.profileFocused && !profileIssued){profileIssued=PictureActionService.providerKey(pkg,false);}
   else if(f.title && f.year){
    titleWasVerified=true;verified("local-primary-title-and-year");
    if(!allowPlayback){result.done(true,"Opened "+title+". No purchase or playback was started.");return;}
    if(f.member && f.playFocused){prePlayUpdate=-1;for(MediaController c:controllers())if(pkg.equals(c.getPackageName()) && c.getPlaybackState()!=null)prePlayUpdate=c.getPlaybackState().getLastPositionUpdateTime();playIssued=PictureActionService.providerKey(pkg,false);}
    else if(f.member && f.secondaryFocused && navigationSteps<2){if(PictureActionService.providerKey(pkg,true))navigationSteps++;}
   }
   main.postDelayed(new Runnable(){public void run(){verifyLaunch(pkg,title,year,allowPlayback,result,token,until);}},700);
  }}))return;
  main.postDelayed(new Runnable(){public void run(){verifyLaunch(pkg,title,year,allowPlayback,result,token,until);}},500);
 }
 private void verified(String method){context.getSharedPreferences("diagnostics",0).edit().putString("content_verify_method",method).putLong("content_launch_latency_ms",SystemClock.elapsedRealtime()-launchAt).apply();}
 void control(Command command,final Result result){
  cancel();
  if(command.kind==Command.Kind.VOLUME){
   AudioManager audio=context.getSystemService(AudioManager.class);int old=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
   for(int i=0;i<Math.abs(command.amount);i++)audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,command.amount>0?AudioManager.ADJUST_RAISE:AudioManager.ADJUST_LOWER,AudioManager.FLAG_SHOW_UI);
   int now=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
   result.done(now!=old,now!=old?"Volume "+old+" to "+now+".":"The volume is unchanged or controlled by an external audio device.");return;
  }
  final MediaController controller=active();if(controller==null){result.done(false,"No supported active playback session is available.");return;}
  PlaybackState state=controller.getPlaybackState();long target=-1;
  if(command.kind==Command.Kind.PAUSE)controller.getTransportControls().pause();
  else if(command.kind==Command.Kind.RESUME)controller.getTransportControls().play();
  else if(command.kind==Command.Kind.SEEK){
   if(state==null || (state.getActions()&PlaybackState.ACTION_SEEK_TO)==0){result.done(false,"This app does not expose seeking.");return;}
   boolean playing=state.getState()==PlaybackState.STATE_PLAYING;
   if(!playing && state.getState()!=PlaybackState.STATE_PAUSED){result.done(false,"Wait for playback before seeking.");return;}
   if(playing)controller.getTransportControls().pause();
   seekPaused(controller,command.amount,playing,result,generation,SystemClock.elapsedRealtime()+3500);return;
  }else{result.done(false,"That media action is not connected.");return;}
  final int token=generation;final long seekTarget=target;final Command.Kind kind=command.kind;
  final long until=SystemClock.elapsedRealtime()+4000;
  main.postDelayed(new Runnable(){public void run(){verifyTransport(controller,kind,seekTarget,result,token,until);}},450);
 }
 private void seekPaused(final MediaController c,final int amount,final boolean restorePlaying,final Result result,final int token,final long until){
  if(token!=generation)return;PlaybackState state=c.getPlaybackState();
  if(state!=null && state.getState()==PlaybackState.STATE_PAUSED && state.getPosition()>=0){
   long position=state.getPosition(),wanted=Math.max(0,position+amount*1000L);MediaMetadata metadata=c.getMetadata();
   long duration=metadata==null?0:metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);if(duration>0)wanted=Math.min(wanted,Math.max(0,duration-1000));
   final long target=wanted;c.getTransportControls().seekTo(target);
   verifyPausedSeek(c,target,restorePlaying,result,token,SystemClock.elapsedRealtime()+4000);return;
  }
  if(SystemClock.elapsedRealtime()>=until){if(restorePlaying)c.getTransportControls().play();result.done(false,"The app did not provide a stable position for seeking.");return;}
  main.postDelayed(new Runnable(){public void run(){seekPaused(c,amount,restorePlaying,result,token,until);}},200);
 }
 private void verifyPausedSeek(final MediaController c,final long target,final boolean restorePlaying,final Result result,final int token,final long until){
  if(token!=generation)return;PlaybackState state=c.getPlaybackState();
  if(state!=null && state.getPosition()>=0 && Math.abs(state.getPosition()-target)<750){
   if(!restorePlaying){result.done(true,"Playback position changed.");return;}
   c.getTransportControls().play();
   verifyTransport(c,Command.Kind.RESUME,-1,new Result(){public void done(boolean ok,String message){result.done(ok,ok?"Playback position changed; continuing playback.":"Position changed, but the app did not confirm resuming.");}},token,SystemClock.elapsedRealtime()+4000);return;
  }
  if(SystemClock.elapsedRealtime()>=until){if(restorePlaying)c.getTransportControls().play();result.done(false,"The app did not confirm the requested seek.");return;}
  main.postDelayed(new Runnable(){public void run(){verifyPausedSeek(c,target,restorePlaying,result,token,until);}},200);
 }
 private void verifyTransport(final MediaController c,final Command.Kind kind,final long target,final Result result,final int token,final long until){
  if(token!=generation)return;PlaybackState state=c.getPlaybackState();boolean ok=false;
  if(state!=null){
   if(kind==Command.Kind.PAUSE)ok=state.getState()==PlaybackState.STATE_PAUSED;
   else if(kind==Command.Kind.RESUME)ok=state.getState()==PlaybackState.STATE_PLAYING;
   else ok=state.getPosition()>=0 && Math.abs(state.getPosition()-target)<3000;
  }
  if(ok){result.done(true,kind==Command.Kind.PAUSE?"Playback paused.":kind==Command.Kind.RESUME?"Playback resumed.":"Playback position changed.");return;}
  if(SystemClock.elapsedRealtime()>=until){result.done(false,"The app did not confirm the playback change.");return;}
  main.postDelayed(new Runnable(){public void run(){verifyTransport(c,kind,target,result,token,until);}},250);
 }
}
