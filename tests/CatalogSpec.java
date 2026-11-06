package dev.tvassistant.tests;
import dev.tvassistant.catalog.Catalog;
import dev.tvassistant.catalog.LaunchPolicy;
import java.util.*;
import org.json.*;

/** Synthetic schemas test identity, region, paid-offer separation and untrusted links. */
public final class CatalogSpec {
 static int passed;
 static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;}
 static JSONObject action(String region,String url,String function,boolean billing)throws Exception{
  JSONObject o=new JSONObject().put("eligibleRegion",new JSONObject().put("name",region))
   .put("availability","https://schema.org/InStock").put("offeredBy",new JSONObject().put("name","Amazon Prime Video"))
   .put("businessFunction","http://purl.org/goodrelations/v1#"+function).put("price",129).put("priceCurrency","INR");
  if(billing)o.put("additionalProperty",new JSONArray().put(new JSONObject().put("name","BillingPeriod").put("value","Monthly")));
  return new JSONObject().put("@type","WatchAction").put("target",new JSONObject().put("urlTemplate",url)).put("expectsAcceptanceOf",o);
 }
 static String page(JSONArray actions)throws Exception{
  JSONObject movie=new JSONObject().put("@type","Movie").put("@id","https://www.justwatch.com/in/movie/example")
   .put("name","Example").put("dateCreated","2015-01-01").put("potentialAction",actions);
  return "<script type=\"application/ld+json\">"+new JSONObject().put("@graph",new JSONArray().put(movie))+"</script>";
 }
 public static void main(String[]args)throws Exception{
  Catalog.Title expected=new Catalog.Title("tt1234567","Example","movie",2015);
  String url="https://app.primevideo.com/detail?gti=example";
  List<Catalog.Offer> offers=Catalog.parseOffers(page(new JSONArray().put(action("IN",url,"ProvideService",true)).put(action("IN",url,"LeaseOut",false))),expected,"source",10);
  check(offers.size()==2,"subscription and rental remain distinct");
  check(offers.get(0).included() && !offers.get(1).included(),"rental never becomes included access");
  check(offers.get(1).price.equals("INR 129"),"price uses the source currency, never an invented conversion");
  check(Catalog.parseOffers(page(new JSONArray().put(action("US",url,"ProvideService",true))),expected,"source",10).isEmpty(),"wrong country excluded");
  check(Catalog.parseOffers(page(new JSONArray().put(action("IN","https://evil.test/steal","ProvideService",true))),expected,"source",10).isEmpty(),"untrusted target excluded");
  check(Catalog.parseOffers(page(new JSONArray()),new Catalog.Title("tt1234567","Example","movie",2021),"source",10)==null,"remake year mismatch cannot verify identity");
  check(Catalog.parseOffers(page(new JSONArray()),new Catalog.Title("tt1234567","Other","movie",2015),"source",10)==null,"title mismatch cannot verify identity");
  check(!Catalog.safeProviderUrl("Amazon Prime Video","https://app.primevideo.com.evil.test/detail"),"host suffix attack rejected");
  check(!Catalog.safeProviderUrl("Amazon Prime Video","https://user:password@app.primevideo.com/detail"),"userinfo rejected");
  check(!Catalog.safeProviderUrl("Amazon Prime Video","javascript:alert(1)"),"executable URL rejected");
  JSONObject v=new JSONObject().put("videoId","YlUKcNNmywk").put("title",new JSONObject().put("simpleText","Song {live}"));
  JSONObject primary=new JSONObject().put("items",new JSONArray().put(new JSONObject().put("videoRenderer",v)));
  JSONObject renderer=new JSONObject().put("primaryContents",primary).put("secondaryContents",new JSONObject().put("videoRenderer",new JSONObject().put("videoId","GcHHjq8pXec").put("title",new JSONObject().put("simpleText","Unrelated recommendation"))));
  JSONObject data=new JSONObject().put("contents",new JSONObject().put("twoColumnSearchResultsRenderer",renderer));
  List<Catalog.Video> videos=Catalog.parseYoutube("<script>var ytInitialData = "+data+";</script>");
  check(videos.size()==1 && videos.get(0).title.equals("Song {live}"),"balanced JSON extraction respects string braces");
  check(videos.size()==1,"secondary recommendations excluded from title search choices");
  check(Catalog.parseYoutube("<script>someOtherData={};</script>").isEmpty(),"no fabricated results when source changes");
  boolean malformed=false;try{Catalog.parseYoutube("var ytInitialData = {broken}");}catch(JSONException e){malformed=true;}
  check(malformed,"malformed provider data fails without fabricated results");
  check(LaunchPolicy.allowed("com.google.android.youtube.tv","https://www.youtube.com/watch?v=YlUKcNNmywk"),"validated YouTube identity link");
  check(!LaunchPolicy.allowed("com.google.android.youtube.tv","https://www.youtube.com/watch?v=YlUKcNNmywk&redirect=https://evil.test"),"extra untrusted launch parameters rejected");
  check(LaunchPolicy.allowed("com.stremio.one","stremio:///detail/movie/tt1392190"),"validated Stremio title identity");
  check(!LaunchPolicy.allowed("com.stremio.one","stremio:///settings/account"),"account routes excluded");
  check(!LaunchPolicy.allowed("com.netflix.ninja","https://www.netflix.com/YourAccount"),"provider account routes excluded");
  check(Catalog.viewCount("1,234,567 views")==1234567 && Catalog.viewCount("1.4B views")==1400000000L,"full and abbreviated view counts");
  check(Catalog.viewCount("2.5 crore views")==25000000 && Catalog.viewCount("No views")==-1,"regional counts and missing data");
  List<Catalog.Video> ranked=Arrays.asList(new Catalog.Video("YlUKcNNmywk","Song official","Artist","4:00",800),new Catalog.Video("GcHHjq8pXec","Song live","Artist","5:00",900),new Catalog.Video("Z0AXjUy1_gY","Song lyrics","Artist","4:00",100));
  check(Catalog.popularVideo(ranked,"Song").title.equals("Song live"),"highest views chosen without a selection screen");
  check(Catalog.popularVideo(ranked,"Song official").title.equals("Song official"),"requested version overrides popularity");
  check(Catalog.popularVideo(ranked,"Song remix")==null,"missing explicit version never silently changes version");
  Catalog popular=new Catalog(new Catalog.Http(){public String get(String url){boolean newer=url.contains("tt2222222");return "{\"meta\":{\"id\":\""+(newer?"tt2222222":"tt1111111")+"\",\"name\":\"Example\",\"type\":\"movie\",\"year\":\""+(newer?2021:1984)+"\",\"popularity\":"+(newer?20:2)+"}}";}});
  List<Catalog.Title> remakes=Arrays.asList(new Catalog.Title("tt1111111","Example","movie",1984),new Catalog.Title("tt2222222","Example","movie",2021));
  check(popular.popularTitle(remakes).year==2021,"catalog popularity resolves remakes rather than oldest-first list order");
  Catalog unavailable=new Catalog(new Catalog.Http(){public String get(String url)throws Exception{throw new java.io.IOException("unavailable");}});
  check(unavailable.popularTitle(remakes).year==1984,"missing popularity preserves source ranking instead of inventing scores");
  check(popular.popularTitle(Collections.singletonList(remakes.get(0))).year==1984,"explicit constrained candidate is never replaced by another popular title");
  check(Catalog.songMetadataMatches("Red Hot Chili Peppers - Californication [Official Music Video]","Californication","Red Hot Chili Peppers"),"Premium song and artist metadata confirms official video caption");
  check(Catalog.songMetadataMatches("Red Hot Chili Peppers - Californication (Official Music Video) [HD UPGRADE]","Californication","Red Hot Chili Peppers"),"observed official HD-upgrade caption matches Premium metadata");
  check(!Catalog.songMetadataMatches("Other Artist - Californication [Official Video]","Californication","Red Hot Chili Peppers"),"different artist cannot verify playback");
  check(!Catalog.songMetadataMatches("Red Hot Chili Peppers - Californication [Live]","Californication","Red Hot Chili Peppers"),"requested live version cannot verify a studio recording");
  System.out.println("PASS: "+passed+" catalog behavior/contract checks");
 }
}
