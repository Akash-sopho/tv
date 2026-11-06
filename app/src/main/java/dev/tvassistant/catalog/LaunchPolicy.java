package dev.tvassistant.catalog;
import java.net.*;
/** Independent broker validation: only known title routes, never account/checkout URLs. */
public final class LaunchPolicy {
 private LaunchPolicy(){}
 public static boolean allowed(String pkg,String url){
  try{
   URI u=new URI(url);String path=u.getPath()==null?"":u.getPath();
   if(pkg.equals("com.stremio.one"))return "stremio".equals(u.getScheme()) && u.getHost()==null && u.getQuery()==null && u.getFragment()==null && path.matches("/detail/(?:movie|series)/tt\\d{5,12}");
   String provider=pkg.equals("com.amazon.amazonvideo.livingroom")?"Amazon Prime Video":pkg.equals("in.startv.hotstar")?"JioHotstar":pkg.equals("com.netflix.ninja")?"Netflix":pkg.equals("com.sonyliv")?"Sony LIV":pkg.equals("com.apple.atve.androidtv.appletv")?"Apple TV":"";
   if(pkg.equals("com.google.android.youtube.tv") || pkg.equals("com.google.android.youtube.tvmusic")){
    String host=pkg.equals("com.google.android.youtube.tvmusic")?"music.youtube.com":"www.youtube.com";
    if(!"https".equals(u.getScheme()) || !host.equals(u.getHost()) || u.getUserInfo()!=null || u.getFragment()!=null || !path.equals("/watch"))return false;
    String query=u.getRawQuery();if(query==null || !query.startsWith("v="))return false;
    return query.substring(2).matches("[A-Za-z0-9_-]{11}");
   }
   if(provider.isEmpty() || !Catalog.safeProviderUrl(provider,url))return false;
   if(pkg.equals("com.amazon.amazonvideo.livingroom")){
    if(path.matches("/detail/[A-Z0-9]{10}"))return true;
    if(!path.equals("/detail"))return false;
    String query=u.getRawQuery();if(query==null)return false;
    for(String part:query.split("&"))if(part.startsWith("gti="))return URLDecoder.decode(part.substring(4),"UTF-8").matches("amzn1\\.dv\\.gti\\.[A-Za-z0-9.-]{8,120}");
    return false;
   }
   if(pkg.equals("com.netflix.ninja"))return path.matches("/(?:in/)?(?:title|watch)/\\d{5,15}");
   if(pkg.equals("in.startv.hotstar"))return path.matches("/in/(?:(?:movies|tv|shows)/[^/]+/)?\\d{5,15}");
   if(pkg.equals("com.sonyliv"))return path.matches("/(?:movies|shows)/[^/]+-\\d{5,15}");
   return path.matches("/(?:in/)?(?:movie|show)/[^/]+/umc\\.cmc\\.[a-z0-9]+");
  }catch(Exception e){return false;}
 }
}
