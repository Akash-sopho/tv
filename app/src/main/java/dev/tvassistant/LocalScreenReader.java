package dev.tvassistant;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.os.*;
import android.view.Display;
import com.googlecode.tesseract.android.*;
import dev.tvassistant.catalog.Catalog;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Request-only local OCR for opaque provider screens. No image files, network or text archive. */
final class LocalScreenReader {
 interface Callback {void result(Facts facts);}
 static final class Facts {
  boolean title,year,member,profile,profileFocused,playFocused,secondaryFocused,account;
  String playLabel="",debug="";long elapsedMs;
 }
 private static final ExecutorService worker=Executors.newSingleThreadExecutor();
 private static TessBaseAPI engine;
 private static boolean busy;
 private static final Handler main=new Handler(Looper.getMainLooper());
 private static final Set<String> PLAY=new HashSet<String>(Arrays.asList("play","resume","watchnow","watchagain","resumewatching","playmovie","watchfrombeginning","startwatching","playfrombeginning","playwithprime","watchwithprime"));
 private static final class Line {String text;Rect box;float confidence;Line(String t,Rect b,float c){text=t;box=b;confidence=c;}}
 private static final class Region {String text;List<Line> lines;Region(String t,List<Line> l){text=t;lines=l;}}
 static boolean inspect(final PictureActionService service,final String pkg,final String title,final int year,final Callback callback){
  if(busy || !pkg.equals("com.amazon.amazonvideo.livingroom") || !PictureActionService.foreground(pkg))return false;
  busy=true;final long started=SystemClock.elapsedRealtime();
  try{service.takeScreenshot(Display.DEFAULT_DISPLAY,service.getMainExecutor(),new AccessibilityService.TakeScreenshotCallback(){
   public void onFailure(int code){busy=false;Facts f=new Facts();f.debug="Screenshot unavailable: "+code;callback.result(f);}
   public void onSuccess(final AccessibilityService.ScreenshotResult screenshot){
    if(!PictureActionService.foreground(pkg)){screenshot.getHardwareBuffer().close();busy=false;callback.result(new Facts());return;}
    worker.submit(new Runnable(){public void run(){Facts facts=new Facts();Bitmap bitmap=null;
     try{
      HardwareBuffer buffer=screenshot.getHardwareBuffer();Bitmap wrapped=Bitmap.wrapHardwareBuffer(buffer,screenshot.getColorSpace());
      try{if(wrapped!=null)bitmap=wrapped.copy(Bitmap.Config.ARGB_8888,false);}finally{buffer.close();if(wrapped!=null)wrapped.recycle();}
      if(bitmap==null)throw new IOException("Empty screenshot");
      if(bitmap.getWidth()!=1920 || bitmap.getHeight()!=1080){Bitmap resized=Bitmap.createScaledBitmap(bitmap,1920,1080,true);bitmap.recycle();bitmap=resized;}
      init(service.getApplicationContext());
      Region header=read(bitmap,new Rect(0,0,1200,520));Region body=read(bitmap,new Rect(0,400,1650,880));
      Region names=read(bitmap,new Rect(325,80,850,650));
      String logo=readHighContrast(bitmap,new Rect(145,100,650,203),true)+" "+readHighContrast(bitmap,new Rect(145,202,650,265),true);
      String cta=readHighContrast(bitmap,new Rect(170,545,560,590),false);
      String all=(header.text+"\n"+body.text+"\n"+names.text+"\n"+cta).toLowerCase(Locale.ROOT),compact=Catalog.normalize(all),wanted=Catalog.normalize(title==null?"":title);
      String normalizedHeader=Catalog.normalize(header.text+" "+logo);
      facts.title=!wanted.isEmpty() && (wanted.length()<5?header.text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").matches("(?s).*\\b"+java.util.regex.Pattern.quote(title.toLowerCase(Locale.ROOT))+"\\b.*"):normalizedHeader.contains(wanted));
      facts.year=year==0 || java.util.regex.Pattern.compile("\\b"+year+"\\b").matcher(body.text).find();
      // One-character OCR noise is accepted only for a long complete logo plus a verified release year.
      if(!facts.title && year>0 && facts.year && wanted.length()>=12)facts.title=oneEdit(Catalog.normalize(logo),wanted);
      facts.member=compact.contains("includedwithprime");
      String ctaLabel=Catalog.normalize(cta);
      if(ctaLabel.startsWith("p") && PLAY.contains(ctaLabel.substring(1)))ctaLabel=ctaLabel.substring(1);
      if(PLAY.contains(ctaLabel) && whiteFraction(bitmap,new Rect(160,540,550,590))>.6f){facts.playFocused=true;facts.playLabel=ctaLabel;}
      facts.account=compact.contains("signin") || compact.contains("amazoncommytv") || compact.contains("password") || compact.contains("registrationcode") || compact.contains("createaccount");
      int rightLines=0,profilesFocused=0;
      for(Line line:body.lines)if(line.box.right>1000 && line.text.trim().split("\\s+").length>4)rightLines++;
      List<Line> lines=new ArrayList<Line>(names.lines);Set<String> seen=new HashSet<String>();
      for(Line line:lines){
       String normalized=Catalog.normalize(line.text);String key=line.box.top+":"+normalized;if(!seen.add(key))continue;
       float white=whiteFraction(bitmap,line.box);
       if(line.confidence>=55 && PLAY.contains(normalized) && line.box.left<700 && line.box.top>350 && white>.55f){facts.playFocused=true;facts.playLabel=normalized;}
       if(line.confidence>=55 && (normalized.equals("goadfree") || normalized.equals("morewaystowatch")) && line.box.left<700 && white>.55f)facts.secondaryFocused=true;
       if(line.confidence>=65 && line.box.left>=300 && line.box.right<850 && line.box.top>75 && line.box.bottom<690 && white>.025f && white<.5f && !normalized.matches("kids?|new") && line.text.trim().matches("[\\p{L}\\p{N} ._-]{1,40}"))profilesFocused++;
      }
      facts.profile=!facts.title && !facts.account && rightLines<2 && java.util.regex.Pattern.compile("\\bkids\\b").matcher(all).find() && java.util.regex.Pattern.compile("\\bnew\\b").matcher(all).find();
      facts.profileFocused=facts.profile && profilesFocused==1;
      facts.debug="LOGO: "+logo+" CTA: "+cta+" NAMES: "+names.text+" FOCUSCOUNT: "+profilesFocused+" HEADER:\n"+header.text+"\nBODY:\n"+body.text;
     }catch(Exception e){facts.debug="Local screen reader failed: "+e.getClass().getSimpleName();}
      catch(LinkageError e){facts.debug="Local OCR library unavailable.";}
     finally{if(bitmap!=null)bitmap.recycle();try{if(engine!=null)engine.clear();}catch(RuntimeException ignored){}}
     facts.elapsedMs=SystemClock.elapsedRealtime()-started;final Facts result=facts;
     main.post(new Runnable(){public void run(){busy=false;callback.result(result);}});
    }});
   }
  });return true;}catch(RuntimeException error){busy=false;return false;}
 }
 private static void init(Context context)throws IOException{
  if(engine!=null)return;
  File root=new File(context.getFilesDir(),"local-ocr"),data=new File(root,"tessdata");
  if(!data.isDirectory() && !data.mkdirs())throw new IOException("OCR folder unavailable");
  File model=new File(data,"eng.traineddata");
  if(!model.isFile() || model.length()!=4113088)try(InputStream in=context.getAssets().open("ocr-eng.traineddata");OutputStream out=new FileOutputStream(model)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  TessBaseAPI created=new TessBaseAPI();if(!created.init(root.getAbsolutePath(),"eng")){created.recycle();throw new IOException("OCR model initialization failed");}
  created.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT);engine=created;
 }
 private static Region read(Bitmap whole,Rect area){
  Bitmap crop=Bitmap.createBitmap(whole,area.left,area.top,area.width(),area.height());
  try{
   engine.setImage(crop);String text=engine.getUTF8Text();List<Line> lines=new ArrayList<Line>();ResultIterator iterator=engine.getResultIterator();
   if(iterator!=null)try{iterator.begin();do{String value=iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE);Rect box=iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE);if(value!=null && box!=null){box.offset(area.left,area.top);lines.add(new Line(value.trim(),box,iterator.confidence(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)));}}while(iterator.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE));}finally{iterator.delete();}
   engine.clear();return new Region(text==null?"":text,lines);
  }finally{crop.recycle();}
 }
 private static String readHighContrast(Bitmap bitmap,Rect area,boolean gold){
  Bitmap crop=Bitmap.createBitmap(area.width(),area.height(),Bitmap.Config.ARGB_8888);
  for(int y=0;y<area.height();y++)for(int x=0;x<area.width();x++){
   int c=bitmap.getPixel(area.left+x,area.top+y),r=Color.red(c),g=Color.green(c),b=Color.blue(c);
   boolean ink=gold?r>130 && g>90 && b<130:r<130 && g<130 && b<130;
   crop.setPixel(x,y,ink?Color.BLACK:Color.WHITE);
  }
  Bitmap large=Bitmap.createScaledBitmap(crop,crop.getWidth()*2,crop.getHeight()*2,true);crop.recycle();
  try{engine.setPageSegMode(gold?TessBaseAPI.PageSegMode.PSM_SINGLE_LINE:TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT);engine.setImage(large);return engine.getUTF8Text();}finally{engine.clear();engine.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT);large.recycle();}
 }
 private static boolean oneEdit(String a,String b){
  if(Math.abs(a.length()-b.length())>1)return false;int i=0,j=0,errors=0;
  while(i<a.length() && j<b.length()){if(a.charAt(i)==b.charAt(j)){i++;j++;continue;}if(++errors>1)return false;if(a.length()>=b.length())i++;if(b.length()>=a.length())j++;}
  return errors+(a.length()-i)+(b.length()-j)<=1;
 }
 private static float whiteFraction(Bitmap bitmap,Rect box){
  int white=0,total=0;Rect r=new Rect(box);if(!r.intersect(0,0,bitmap.getWidth(),bitmap.getHeight()))return 0;
  for(int y=r.top;y<r.bottom;y+=2)for(int x=r.left;x<r.right;x+=2){int c=bitmap.getPixel(x,y);if(Color.red(c)>230 && Color.green(c)>230 && Color.blue(c)>230)white++;total++;}
  return total==0?0:white/(float)total;
 }
}
