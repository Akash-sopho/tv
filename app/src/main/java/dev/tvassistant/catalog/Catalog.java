package dev.tvassistant.catalog;

import java.io.*;
import java.net.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Public catalog data only. No login, API credentials, script execution or purchase actions. */
public final class Catalog {
 public interface Http {String get(String url) throws Exception;}
 public static final class Title {
  public final String id,name,type;public final int year;
  public Title(String id,String name,String type,int year){this.id=id;this.name=name;this.type=type;this.year=year;}
  public String label(){return name+" ("+year+", "+("series".equals(type)?"show":"movie")+")";}
 }
 public static final class Offer {
  public final String provider,url,kind,price,source;public final long checkedAt;
  public Offer(String provider,String url,String kind,String price,String source,long checkedAt){this.provider=provider;this.url=url;this.kind=kind;this.price=price;this.source=source;this.checkedAt=checkedAt;}
  public boolean included(){return kind.equals("subscription") || kind.equals("free");}
 }
 public static final class Video {
  public final String id,title,channel,duration;public final long views;
  public Video(String id,String title,String channel,String duration){this(id,title,channel,duration,-1);}
  public Video(String id,String title,String channel,String duration,long views){this.id=id;this.title=title;this.channel=channel;this.duration=duration;this.views=views;}
 }
 private final Http http;
 public Catalog(){this(new Http(){public String get(String url)throws Exception{return download(url);}});}
 public Catalog(Http http){this.http=http;}
 public static String normalize(String s){return Normalizer.normalize(s,Normalizer.Form.NFKD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
 /** YouTube Premium may expose song + artist instead of the public video caption. */
 public static boolean songMetadataMatches(String caption,String song,String artist){
  if(caption==null || song==null || artist==null || artist.trim().isEmpty() || song.trim().isEmpty())return false;
  Matcher prefix=Pattern.compile("(?i)^"+Pattern.quote(artist.trim())+"\\s*[-–—]\\s*(.+)$").matcher(caption.trim());
  if(!prefix.matches())return false;
  String name=prefix.group(1).replaceFirst("(?i)\\s*[\\[(](?:hd|4k|8k)(?: upgrade| remaster(?:ed)?)?[\\])]\\s*$","")
   .replaceFirst("(?i)\\s*[\\[(]official(?: music)?(?: video| audio)?[\\])]\\s*$","").trim();
  return normalize(name).equals(normalize(song));
 }
 private static String encode(String s)throws Exception{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}
 public List<Title> search(String query,int year)throws Exception{
  List<Title> out=new ArrayList<Title>();
  Exception last=null;
  for(String type:new String[]{"movie","series"}){
   try{
    JSONObject data=new JSONObject(http.get("https://v3-cinemeta.strem.io/catalog/"+type+"/top/search="+encode(query)+".json"));
    JSONArray metas=data.optJSONArray("metas");if(metas==null)continue;
    for(int i=0;i<metas.length() && i<15;i++){
     JSONObject m=metas.optJSONObject(i);if(m==null)continue;
     String id=m.optString("id",""),name=m.optString("name","");
     Matcher date=Pattern.compile("(?:18|19|20)\\d{2}").matcher(m.optString("releaseInfo",m.optString("year","")));
     int released=date.find()?Integer.parseInt(date.group()):0;
     if(id.matches("tt\\d{5,12}") && name.length()>0 && name.length()<250 && released>0 && (year==0 || released==year))
      out.add(new Title(id,name,type,released));
    }
   }catch(Exception e){last=e;}
  }
  if(out.isEmpty() && last!=null)throw last;
  Collections.sort(out,new Comparator<Title>(){public int compare(Title a,Title b){return Boolean.compare(normalize(b.name).equals(normalize(query)),normalize(a.name).equals(normalize(query)));}});
  return out;
 }
 public List<Offer> offers(Title title)throws Exception{
  String slug=Normalizer.normalize(title.name,Normalizer.Form.NFKD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","");
  String base="https://www.justwatch.com/in/"+(title.type.equals("series")?"tv-show/":"movie/")+slug;
  Exception last=null;
  for(String url:new String[]{base,base+"-"+title.year}){
   try{List<Offer> found=parseOffers(http.get(url),title,url,System.currentTimeMillis());if(found!=null)return found;}catch(Exception e){last=e;}
  }
  if(last!=null)throw last;
  throw new IOException("Exact India title page could not be verified");
 }
 /** Compare only already relevant, year/type-filtered title candidates. Rating is not popularity. */
 public Title popularTitle(List<Title> candidates)throws Exception{
  if(candidates.isEmpty())return null;if(candidates.size()==1)return candidates.get(0);
  Title best=candidates.get(0);double highest=-1;
  for(int i=0;i<Math.min(3,candidates.size());i++){
   if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Request cancelled");
   Title t=candidates.get(i);
   try{
    JSONObject m=new JSONObject(http.get("https://v3-cinemeta.strem.io/meta/"+t.type+"/"+t.id+".json")).optJSONObject("meta");
    if(m==null || !t.id.equals(m.optString("id",m.optString("imdb_id",""))) || !normalize(t.name).equals(normalize(m.optString("name",""))) || !t.type.equals(m.optString("type","")))continue;
    String release=m.optString("releaseInfo",m.optString("year",""));if(!release.startsWith(Integer.toString(t.year)))continue;
    double score=m.optDouble("popularity",-1);
    if(!Double.isNaN(score) && !Double.isInfinite(score) && score>=0 && score>highest){highest=score;best=t;}
   }catch(InterruptedIOException e){throw e;}catch(Exception ignored){} // Preserve source ranking if popularity is unavailable.
  }
  return best;
 }
 public static long viewCount(String value){
  if(value==null)return -1;
  String s=value.toLowerCase(Locale.ROOT).replace(",","").trim();
  Matcher m=Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(billion|million|thousand|crore|lakh|[kmb])?\\b").matcher(s);
  if(!m.find())return -1;
  double n=Double.parseDouble(m.group(1));String unit=m.group(2);
  if(unit!=null){if(unit.matches("b|billion"))n*=1e9;else if(unit.matches("m|million"))n*=1e6;else if(unit.matches("k|thousand"))n*=1e3;else if(unit.equals("crore"))n*=1e7;else if(unit.equals("lakh"))n*=1e5;}
  return Double.isFinite(n) && n<=Long.MAX_VALUE?(long)n:-1;
 }
 public static Video popularVideo(List<Video> candidates,String query){
  Video best=null;String q=query.toLowerCase(Locale.ROOT);List<String> required=new ArrayList<String>();
  for(String kind:new String[]{"official","live","lyrics","cover","remix","karaoke"})if(Pattern.compile("\\b"+kind+"\\b").matcher(q).find())required.add(kind);
  for(int i=0;i<Math.min(3,candidates.size());i++){
   Video video=candidates.get(i);String name=video.title.toLowerCase(Locale.ROOT);boolean matches=true;
   for(String kind:required)if(!Pattern.compile("\\b"+kind+"\\b").matcher(name).find())matches=false;
   if(matches && (best==null || video.views>best.views))best=video;
  }
  return best;
 }
 /** null = wrong/missing identity, empty list = verified title with no usable offers. */
 public static List<Offer> parseOffers(String html,Title expected,String source,long now)throws Exception{
  Matcher scripts=Pattern.compile("<script\\b([^>]*)>(.*?)</script>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(html);
  while(scripts.find()){
   if(!scripts.group(1).toLowerCase(Locale.ROOT).contains("application/ld+json"))continue;
   JSONObject document;try{document=new JSONObject(scripts.group(2));}catch(JSONException e){continue;}
   JSONArray graph=document.optJSONArray("@graph");if(graph==null)graph=new JSONArray().put(document);
   for(int i=0;i<graph.length();i++){
    JSONObject item=graph.optJSONObject(i);if(item==null)continue;
    String type=item.optString("@type","");
    if(!(type.equals("Movie") || type.equals("TVSeries")))continue;
    if(!normalize(item.optString("name","")).equals(normalize(expected.name)))continue;
    if(!item.optString("dateCreated","").startsWith(Integer.toString(expected.year)))continue;
    if(expected.type.equals("movie")!=type.equals("Movie"))continue;
    URI canonical;try{canonical=new URI(item.optString("@id",""));}catch(Exception e){continue;}
    if(!"https".equals(canonical.getScheme()) || !"www.justwatch.com".equals(canonical.getHost()) || !canonical.getPath().startsWith("/in/"))continue;
    List<Offer> offers=new ArrayList<Offer>();Set<String> seen=new HashSet<String>();
    JSONArray actions=item.optJSONArray("potentialAction");if(actions==null)return offers;
    for(int n=0;n<actions.length();n++){
     JSONObject action=actions.optJSONObject(n);if(action==null || !"WatchAction".equals(action.optString("@type")))continue;
     JSONObject offer=action.optJSONObject("expectsAcceptanceOf"),target=action.optJSONObject("target");
     if(offer==null || target==null)continue;
     JSONObject region=offer.optJSONObject("eligibleRegion"),provider=offer.optJSONObject("offeredBy");
     if(region==null || !"IN".equals(region.optString("name")) || provider==null)continue;
     if(!"https://schema.org/InStock".equals(offer.optString("availability","")))continue;
     String url=target.optString("urlTemplate",""),name=provider.optString("name","");
     if(!safeProviderUrl(name,url))continue;
     String function=offer.optString("businessFunction","");String kind="unknown";
     if(function.endsWith("#LeaseOut"))kind="rent";else if(function.endsWith("#Sell"))kind="buy";
     else if(function.endsWith("#ProvideService")){
      JSONArray properties=offer.optJSONArray("additionalProperty");boolean billing=false;
      if(properties!=null)for(int x=0;x<properties.length();x++){JSONObject p=properties.optJSONObject(x);if(p!=null && "BillingPeriod".equals(p.optString("name")))billing=true;}
      if(billing)kind="subscription";else if(offer.optDouble("price",-1)==0)kind="free";
     }
     if(kind.equals("unknown"))continue;
     String key=packageFor(name)+"|"+kind+"|"+url;if(!seen.add(key))continue;
     String currency=offer.optString("priceCurrency","");String amount=offer.optString("price","");
     String price=currency.matches("[A-Z]{3}") && amount.matches("[0-9]+(?:\\.[0-9]+)?")?currency+" "+amount:"price not listed";
     offers.add(new Offer(name,url,kind,price,canonical.toString(),now));
    }
    return offers;
   }
  }
  return null;
 }
 public static String packageFor(String provider){
  String p=provider.toLowerCase(Locale.ROOT);
  if(p.equals("amazon prime video") || p.equals("amazon prime video with ads") || p.equals("amazon video"))return "com.amazon.amazonvideo.livingroom";
  if(p.equals("jiohotstar") || p.equals("hotstar"))return "in.startv.hotstar";
  if(p.equals("netflix"))return "com.netflix.ninja";
  if(p.equals("sony liv") || p.equals("sonyliv"))return "com.sonyliv";
  if(p.equals("youtube"))return "com.google.android.youtube.tv";
  if(p.equals("apple tv") || p.equals("apple tv store"))return "com.apple.atve.androidtv.appletv";
  return "";
 }
 public static boolean safeProviderUrl(String provider,String url){
  try{
   URI u=new URI(url);if(!"https".equals(u.getScheme()) || u.getUserInfo()!=null || u.getHost()==null || (u.getPort()!=-1 && u.getPort()!=443))return false;
   String host=u.getHost().toLowerCase(Locale.ROOT),pkg=packageFor(provider);if(pkg.isEmpty())return false;
   if(pkg.equals("com.amazon.amazonvideo.livingroom"))return host.equals("app.primevideo.com") || host.equals("www.primevideo.com") || host.equals("primevideo.com");
   if(pkg.equals("in.startv.hotstar"))return host.equals("www.hotstar.com") || host.equals("hotstar.com") || host.equals("www.jiohotstar.com");
   if(pkg.equals("com.netflix.ninja"))return host.equals("www.netflix.com") || host.equals("netflix.com");
   if(pkg.equals("com.sonyliv"))return host.equals("www.sonyliv.com") || host.equals("sonyliv.com");
   if(pkg.equals("com.google.android.youtube.tv"))return host.equals("www.youtube.com") || host.equals("youtube.com");
   return pkg.equals("com.apple.atve.androidtv.appletv") && host.equals("tv.apple.com");
  }catch(Exception e){return false;}
 }
 public List<Video> youtube(String query)throws Exception{return parseYoutube(http.get("https://www.youtube.com/results?search_query="+encode(query)));}
 public static List<Video> parseYoutube(String html)throws Exception{
  Matcher marker=Pattern.compile("(?:var\\s+)?ytInitialData\\s*=\\s*").matcher(html);
  if(!marker.find())return Collections.emptyList();
  int start=html.indexOf('{',marker.end());if(start<0)return Collections.emptyList();
  boolean string=false,escape=false;int depth=0,end=-1;
  for(int i=start;i<html.length();i++){
   char c=html.charAt(i);if(string){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')string=false;}
   else if(c=='"')string=true;else if(c=='{')depth++;else if(c=='}' && --depth==0){end=i+1;break;}
  }
  if(end<0)return Collections.emptyList();
  JSONObject data=new JSONObject(html.substring(start,end));List<Video> found=new ArrayList<Video>();
  JSONObject contents=data.optJSONObject("contents"),renderer=contents==null?null:contents.optJSONObject("twoColumnSearchResultsRenderer");
  JSONObject primary=renderer==null?null:renderer.optJSONObject("primaryContents");
  walkVideos(primary,found,new HashSet<String>(),0);return found;
 }
 private static String text(JSONObject object){
  if(object==null)return "";String text=object.optString("simpleText","");if(!text.isEmpty())return text;
  JSONArray runs=object.optJSONArray("runs");StringBuilder b=new StringBuilder();if(runs!=null)for(int i=0;i<runs.length();i++){JSONObject r=runs.optJSONObject(i);if(r!=null)b.append(r.optString("text",""));}return b.toString();
 }
 private static void walkVideos(Object node,List<Video> found,Set<String> seen,int depth){
  if(node==null || depth>35 || found.size()>=8)return;
  if(node instanceof JSONObject){JSONObject o=(JSONObject)node;JSONObject v=o.optJSONObject("videoRenderer");
   if(o.has("adSlotRenderer") || o.has("promotedVideoRenderer") || o.has("promotedSparklesWebRenderer"))return;
   if(v!=null){String id=v.optString("videoId","");String name=text(v.optJSONObject("title"));
    if(id.matches("[A-Za-z0-9_-]{11}") && !name.isEmpty() && name.length()<300 && seen.add(id))found.add(new Video(id,name,text(v.optJSONObject("ownerText")),text(v.optJSONObject("lengthText")),viewCount(text(v.optJSONObject("viewCountText")))));
    return;
   }
   for(Iterator<String> keys=o.keys();keys.hasNext();)walkVideos(o.opt(keys.next()),found,seen,depth+1);
  }else if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++)walkVideos(a.opt(i),found,seen,depth+1);}
 }
 public static String download(String url)throws Exception{
  if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Request cancelled");
  URL target=new URL(url);String host=target.getHost();
  if(!target.getProtocol().equals("https") || !(host.equals("v3-cinemeta.strem.io") || host.equals("www.justwatch.com") || host.equals("www.youtube.com")))throw new IOException("Unsupported catalog endpoint");
  HttpURLConnection c=(HttpURLConnection)target.openConnection();c.setConnectTimeout(8000);c.setReadTimeout(10000);c.setInstanceFollowRedirects(false);
  c.setRequestProperty("User-Agent","Mozilla/5.0");c.setRequestProperty("Accept-Language","en-IN,en;q=0.9");
  try{
   int status=c.getResponseCode();if(status!=200)throw new IOException("Catalog status "+status);
   try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
    byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Request cancelled");if(out.size()+n>4_000_000)throw new IOException("Catalog response too large");out.write(buffer,0,n);}return out.toString("UTF-8");
   }
  }finally{c.disconnect();}
 }
}
