package dev.tvassistant.tests;
import dev.tvassistant.catalog.Catalog;
import java.util.*;
/** Explicit live probe, deliberately not part of offline builds/tests. */
public final class CatalogProbe {
 public static void main(String[]args)throws Exception{
  Catalog c=new Catalog();
  if(args.length>1 && args[0].equals("youtube")){
   List<Catalog.Video> videos=c.youtube(args[1]);
   for(int i=0;i<Math.min(3,videos.size());i++){Catalog.Video v=videos.get(i);System.out.println(v.id+" | "+v.title+" | "+v.channel);}
   if(videos.isEmpty())throw new AssertionError("No live YouTube videos returned");return;
  }
  String q=args.length==0?"Mad Max: Fury Road":args[0];
  List<Catalog.Title> titles=c.search(q,0);if(titles.isEmpty())throw new AssertionError("No live titles returned");
  Catalog.Title t=titles.get(0);System.out.println(t.id+" | "+t.label());
  List<Catalog.Offer> offers=c.offers(t);for(Catalog.Offer o:offers)System.out.println(o.provider+" | "+o.kind+" | "+o.url+" | "+o.source);
  if(offers.isEmpty())throw new AssertionError("No live India offers returned");
 }
}
