package dev.tvassistant;

import android.content.*;
import android.os.*;
import android.widget.Toast;
import dev.tvassistant.catalog.Catalog;
import dev.tvassistant.core.*;
import java.util.*;
import java.util.concurrent.*;

/** Short-lived request orchestration; catalog data never becomes executable instructions. */
final class ContentCoordinator {
 interface Listener {void working(String title,String detail);void choices(String title,List<Choice> choices);void done(boolean ok,String message);void launching();}
 static final class Choice {
  final String label;final String[] aliases;final Runnable action;
  Choice(String label,String[] aliases,Runnable action){this.label=label;this.aliases=aliases;this.action=action;}
 }
 private final Context context;private final Listener listener;private final SessionState session;
 private final Catalog catalog=new Catalog();private final MediaActions media;
 private final ExecutorService worker=Executors.newFixedThreadPool(2);private final Handler main=new Handler(Looper.getMainLooper());
 private Future<?> task;private int generation;private List<Choice> pending=Collections.emptyList();private long pendingUntil;
 private Runnable timeout;
 ContentCoordinator(Context c,SessionState s,Listener l){context=c.getApplicationContext();session=s;listener=l;media=new MediaActions(c);}
 void cancel(){generation++;clearDeadline();if(task!=null)task.cancel(true);media.cancel();}
 private void clearDeadline(){if(timeout!=null){main.removeCallbacks(timeout);timeout=null;}}
 private void deadline(final int token){clearDeadline();timeout=new Runnable(){public void run(){if(token==generation){cancel();done(false,"The lookup timed out. No unverified title was selected.");}}};main.postDelayed(timeout,25000);}
 private void status(String value){context.getSharedPreferences("diagnostics",0).edit().putBoolean("content_action_ok",false).putString("content_action_status",value).apply();}
 void release(){cancel();worker.shutdownNow();}
 private void show(final String heading,List<Choice> choices){
  clearDeadline();status("choice");
  pending=new ArrayList<Choice>(choices.subList(0,Math.min(3,choices.size())));pendingUntil=SystemClock.elapsedRealtime()+SessionState.TTL_MS;
  listener.choices(heading,pending);
 }
 boolean selection(String utterance){
  if(SystemClock.elapsedRealtime()>pendingUntil || pending.isEmpty())return false;
  String text=utterance.toLowerCase(Locale.ROOT).trim().replaceAll("[.!?]+$","");
  String clean=text.replaceFirst("^(?:play |open |choose |the )","").replaceAll("^(?:the )|(?: please)$","").trim();
  int index=-1;
  if(clean.matches("1|one|first|first one|number one"))index=0;
  if(clean.matches("2|two|second|second one|number two"))index=1;
  if(clean.matches("3|three|third|third one|number three"))index=2;
  if(index<0){
   List<Integer> matches=new ArrayList<Integer>();String normalized=Catalog.normalize(clean.replaceFirst(" one$",""));
   for(int n=0;n<pending.size();n++)for(String alias:pending.get(n).aliases)if(!normalized.isEmpty() && normalized.equals(Catalog.normalize(alias))){matches.add(n);break;}
   if(matches.size()==1)index=matches.get(0);
  }
  if(index<0 || index>=pending.size())return false;
  Choice picked=pending.get(index);pending=Collections.emptyList();picked.action.run();return true;
 }
 void execute(final Command command){
  cancel();pending=Collections.emptyList();final int token=generation;
  Diagnostics.count(context,"content_requests");status("working");
  context.getSharedPreferences("diagnostics",0).edit().remove("content_launch_pkg").remove("content_verify_method").remove("content_launch_latency_ms").apply();
  if(command.kind==Command.Kind.SWITCH_INPUT){
   final InputActions inputs=new InputActions(context);List<InputActions.Input> all=inputs.list(),matches=new ArrayList<InputActions.Input>();
   for(InputActions.Input input:all)if(input.matches(command.query))matches.add(input);
   if(matches.size()==1){switchInput(inputs,matches.get(0));return;}
   if(all.isEmpty()){done(false,"The TV reported no supported passthrough inputs.");return;}
   List<Choice> choices=new ArrayList<Choice>();
   for(final InputActions.Input input:matches.isEmpty()?all:matches)choices.add(new Choice(input.label+(input.custom.isEmpty()?"":" · "+input.custom),new String[]{input.label,input.custom},new Runnable(){public void run(){switchInput(inputs,input);}}));
   show("Choose the TV input",choices);return;
  }
  if(command.kind==Command.Kind.OPEN_APP){
  listener.launching();try{media.openVerified(command.app,new MediaActions.Result(){public void done(boolean ok,String message){if(token==generation)ContentCoordinator.this.done(ok,message);}});}catch(RuntimeException e){done(false,"The app could not open.");}return;
  }
  if(command.kind==Command.Kind.VOLUME || command.kind==Command.Kind.PAUSE || command.kind==Command.Kind.RESUME || command.kind==Command.Kind.SEEK){
   listener.launching();try{media.control(command,new MediaActions.Result(){public void done(boolean ok,String message){if(token==generation)ContentCoordinator.this.done(ok,message);}});}catch(RuntimeException e){done(false,"The media app rejected that control.");}return;
  }
  if(command.kind!=Command.Kind.FIND_TITLE && command.kind!=Command.Kind.SEARCH_APP){done(false,"That request is not connected yet.");return;}
  session.remember(command,SystemClock.elapsedRealtime());
  boolean youtubeApp=command.app.equals("com.google.android.youtube.tv") || command.app.equals("com.google.android.youtube.tvmusic");
  if(!youtubeApp && (command.mediaType.equals("song") || command.mediaType.equals("video"))){done(false,"For a song or video, specify YouTube or YouTube Music. I have not changed your app choice.");return;}
  if(youtubeApp && !command.mediaType.equals("movie") && !command.mediaType.equals("show")){
   deadline(token);
   listener.working("Searching YouTube",command.query);
   task=worker.submit(new Runnable(){public void run(){try{
    final List<Catalog.Video> videos=catalog.youtube(command.query);
    main.post(new Runnable(){public void run(){if(token!=generation)return;if(videos.isEmpty()){done(false,"YouTube returned no verified video results. Try a more specific title.");return;}
     final Catalog.Video video=Catalog.popularVideo(videos,command.query);
     if(video==null){done(false,"No matching version was found. Try a more specific title.");return;}
       session.remember(Command.content(Command.Kind.SEARCH_APP,command.query,command.app,0,"video"),SystemClock.elapsedRealtime());
       String host=command.app.equals("com.google.android.youtube.tvmusic")?"music.youtube.com":"www.youtube.com";
       launch(command.app,"https://"+host+"/watch?v="+video.id,video.title,0,true);
    }});
   }catch(final Exception e){failure(token,"YouTube search is unavailable. No video was selected.");}}});return;
  }
  listener.working("Finding the title",command.query+"  ·  India");
  deadline(token);
  task=worker.submit(new Runnable(){public void run(){try{
   List<Catalog.Title> found=catalog.search(command.query,command.year);final List<Catalog.Title> titles=new ArrayList<Catalog.Title>();
   for(Catalog.Title title:found){if(command.mediaType.equals("movie") && !title.type.equals("movie"))continue;if(command.mediaType.equals("show") && !title.type.equals("series"))continue;titles.add(title);}
   List<Catalog.Title> exact=new ArrayList<Catalog.Title>();for(Catalog.Title t:titles)if(Catalog.normalize(t.name).equals(Catalog.normalize(command.query)))exact.add(t);
   final Catalog.Title picked=catalog.popularTitle(exact.isEmpty()?titles:exact);
   main.post(new Runnable(){public void run(){if(token!=generation)return;if(titles.isEmpty()){done(false,"No matching movie or show was found. Try its title and year.");return;}
    resolve(picked,command.app);
   }});
  }catch(Exception e){failure(token,"Title lookup is unavailable. Please try again.");}}});
 }
 private void switchInput(InputActions inputs,InputActions.Input input){final int token=generation;listener.launching();inputs.select(input,new MediaActions.Result(){public void done(boolean ok,String message){if(token==generation)ContentCoordinator.this.done(ok,message);}});}
 private void resolve(final Catalog.Title title,final String explicitApp){
  cancel();pending=Collections.emptyList();final int token=generation;
  session.remember(Command.content(explicitApp.isEmpty()?Command.Kind.FIND_TITLE:Command.Kind.SEARCH_APP,title.name,explicitApp,title.year,title.type.equals("movie")?"movie":"show"),SystemClock.elapsedRealtime());
  if(explicitApp.equals("com.stremio.one")){
   launch(explicitApp,"stremio:///detail/"+title.type+"/"+title.id,title.name,title.year,false);return;
  }
  listener.working("Checking India availability",title.label());
  deadline(token);
  task=worker.submit(new Runnable(){public void run(){try{
   List<Catalog.Offer> all=catalog.offers(title);final List<Catalog.Offer> available=new ArrayList<Catalog.Offer>();
   for(Catalog.Offer o:all){String pkg=Catalog.packageFor(o.provider);if(!explicitApp.isEmpty() && !explicitApp.equals(pkg))continue;if(media.installed(pkg))available.add(o);}
   Collections.sort(available,new Comparator<Catalog.Offer>(){public int compare(Catalog.Offer a,Catalog.Offer b){int included=Boolean.compare(b.included(),a.included());return included!=0?included:Integer.compare(rank(Catalog.packageFor(a.provider)),rank(Catalog.packageFor(b.provider)));}});
   main.post(new Runnable(){public void run(){if(token!=generation)return;
    if(available.isEmpty()){done(false,explicitApp.isEmpty()?"No verified India offer was found in your installed apps. No title was opened.":"No verified India offer was found in "+friendly(explicitApp)+". I have kept your app choice.");return;}
    final Catalog.Offer first=available.get(0);
    if(first.included()){openOffer(title,first);return;}
    List<Choice> choices=new ArrayList<Choice>();
    for(final Catalog.Offer offer:available)choices.add(new Choice(offer.provider+" · "+offer.kind+" · "+offer.price+"\nOpen title page only; no purchase",new String[]{offer.provider,friendly(Catalog.packageFor(offer.provider))},new Runnable(){public void run(){openOffer(title,offer);}}));
    show("Only paid offers found",choices);
   }});
  }catch(Exception e){failure(token,"I could not verify current India availability for "+title.name+". No provider was guessed.");}}});
 }
 private void openOffer(Catalog.Title title,Catalog.Offer offer){
  String pkg=Catalog.packageFor(offer.provider);
  session.remember(Command.content(Command.Kind.SEARCH_APP,title.name,pkg,title.year,title.type.equals("movie")?"movie":"show"),SystemClock.elapsedRealtime());
  listener.working("Opening "+offer.provider,title.name+" · "+(offer.kind.equals("subscription")?"subscription required":offer.kind)+"\nIndia availability: JustWatch, checked now");
  launch(pkg,offer.url,title.name,title.year,offer.included());
 }
 private void launch(String pkg,String url,String title,int year,boolean autoplay){
  clearDeadline();status("opening");final int token=generation;listener.launching();
  media.launch(pkg,url,title,year,autoplay,new MediaActions.Result(){public void done(boolean ok,String message){if(token==generation)ContentCoordinator.this.done(ok,message);}});
 }
 private void failure(final int token,final String message){main.post(new Runnable(){public void run(){if(token==generation)done(false,message);}});}
 private void done(boolean ok,String message){
  clearDeadline();
  context.getSharedPreferences("diagnostics",0).edit().putBoolean("content_action_ok",ok).putString("content_action_status",ok?"verified":"unverified").apply();
  listener.done(ok,message);Toast.makeText(context,message,Toast.LENGTH_LONG).show();
 }
 private static int rank(String pkg){if(pkg.equals("com.amazon.amazonvideo.livingroom"))return 0;if(pkg.equals("in.startv.hotstar"))return 1;if(pkg.equals("com.netflix.ninja"))return 2;return 3;}
 static String friendly(String pkg){for(Map.Entry<String,String> e:Interpreter.apps().entrySet())if(e.getValue().equals(pkg))return e.getKey();return "the selected app";}
}
