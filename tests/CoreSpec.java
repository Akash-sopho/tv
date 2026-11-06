package dev.tvassistant.tests;

import dev.tvassistant.core.*;
import static dev.tvassistant.core.Command.Kind.*;

/** Executable domain/contract checks, not Android or end-to-end claims. */
public final class CoreSpec {
    private static int passed;
    private static final Interpreter parser=new Interpreter();
    private static final SessionState state=new SessionState();
    private static void check(boolean ok,String name) { if(!ok)throw new AssertionError(name); passed++; }
    private static Command parse(String text){return parser.parse(text,state,1000);}
    public static void main(String[] args) {
        Command c=parse("Watch Mad Max: Fury Road");
        check(c.kind==FIND_TITLE && c.query.equals("Mad Max: Fury Road"),"exact title is preserved");
        c=parse("Please play Californication on YouTube.");
        check(c.kind==SEARCH_APP && c.app.equals("com.google.android.youtube.tv") && c.query.equals("Californication"),"explicit YouTube routing");
        c=parse("Play a song on YouTube Music");
        check(c.app.equals("com.google.android.youtube.tvmusic"),"longer app alias wins");
        check(parse("open Stremio").kind==OPEN_APP,"installed app alias");
        check(parse("Play a film on an unknown platform").kind==CLARIFY,"unknown app does not silently change provider");
        check(parse("a little dimmer").amount==-10,"owner-selected brightness default");
        check(parse("make it a little dimmer").amount==-10,"natural relative brightness");
        check(parse("brighter").amount==10,"larger default increase");
        check(parse("slightly dimmer").amount==-5,"explicit fine adjustment");
        check(parse("slightly brighter by 10 points").amount==10,"explicit amount overrides fine default");
        check(parse("increase the brightness by 3 points").amount==3,"natural bounded brightness");
        check(parse("watch Brighter Days").kind==FIND_TITLE,"title words must not adjust brightness");
        check(parse("play Darker on YouTube").kind==SEARCH_APP,"explicit title never mutates picture");
        check(parse("don't make it brighter").kind!=ADJUST_BACKLIGHT,"negation must not mutate brightness");
        check(parse("make it brighter and reset the picture").kind!=ADJUST_BACKLIGHT,"compound request cannot partially mutate settings");
        check(parse("make it brighter by 10 points").amount==10,"relative backlight amount");
        check(parse("reduce brightness by 200").kind==CLARIFY,"oversized adjustment rejected");
        check(parse("set brightness to 101 percent").kind==CLARIFY,"absolute bound");
        check(parse("set brightness to 40 percent").amount==40,"absolute backlight value");
        check(parse("switch to Movie mode").kind==SET_PICTURE_MODE,"picture mode");
        check(parse("switch input to HDMI 1").kind==SWITCH_INPUT && parse("switch input to HDMI 1").query.equals("HDMI 1"),"native input name preserved");
        check(parse("switch to PlayStation").kind==SWITCH_INPUT,"named household input remains a lookup, not a guessed port");
        check(parse("pause it").kind==PAUSE,"pause intent");
        check(parse("resume playback").kind==RESUME,"resume intent");
        check(parse("back 30 seconds").amount==-30,"relative seek");
        check(parse("rewind 20 seconds").kind==SEEK && parse("rewind 20 seconds").amount==-20,"ordinary rewind request");
        check(parse("fast forward 30 seconds").kind==SEEK && parse("fast forward 30 seconds").amount==30,"ordinary fast forward request");
        check(parse("skip ahead 10 seconds").amount==10 && parse("skip back 15 seconds").amount==-15,"skip direction preserved");
        check(parse("rewind 999 seconds").kind==CLARIFY,"seek bounds survive paraphrases");
        check(parse("watch Rewind on YouTube").kind==SEARCH_APP,"seek verb in a title stays content");
        check(parse("volume down 2").amount==-2,"bounded volume");
        check(parse("increase volume").kind==VOLUME && parse("increase volume").amount==1,"natural volume request");
        c=parse("watch the movie Dune");check(c.query.equals("Dune") && c.mediaType.equals("movie"),"explicit movie type preserved");
        c=parse("play show Californication on Hotstar");check(c.query.equals("Californication") && c.mediaType.equals("show"),"show type and app preserved");
        check(parse("volume up 99").kind==CLARIFY,"oversized volume rejected");
        check(parse("undo that").kind==UNDO,"undo intent");
        check(parse("and do that").kind==UNDO,"observed Google undo transcription");
        check(parse("un do that").kind==UNDO,"split undo transcription");
        check(parse("put it back").kind==UNDO,"clear spoken undo alternative");
        check(parse("restore brightness").kind==UNDO,"explicit brightness restoration");
        check(parse("watch And Do That").kind==FIND_TITLE,"undo alias never overrides a title request");
        check(parse("and do that then reset everything").kind!=UNDO,"undo aliases stay exact and bounded");
        check(parse("use Stremio instead").kind==CLARIFY,"follow-up requires context");
        state.remember(parse("watch Mad Max: Fury Road"),900);
        c=parse("use Stremio instead");
        check(c.kind==SEARCH_APP && c.query.equals("Mad Max: Fury Road") && c.app.equals("com.stremio.one"),"app correction retains title");
        state.remember(c,950);
        c=parse("the 2015 one");
        check(c.year==2015 && c.app.equals("com.stremio.one") && c.query.equals("Mad Max: Fury Road"),"year correction retains app/title");
        check(parser.parse("the 2015 one",state,SessionState.TTL_MS+2000).kind==CLARIFY,"old context expires");
        check(parse("run shell reboot").kind==UNSUPPORTED,"no shell intent");
        check(parse("watch x\nreboot").kind==CLARIFY,"control characters rejected");
        c=parse("play title; reboot on YouTube");
        check(c.kind==SEARCH_APP && c.query.equals("title; reboot"),"title remains data, never executable command");
        boolean rejected=false;
        try{Command.adjustment(ADJUST_BACKLIGHT,-500);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"broker schema independently enforces ranges");
        state.clear();
        check(!state.hasContent(1000),"session context clear");
        System.out.println("PASS: "+passed+" core behavior/contract checks");
    }
}
