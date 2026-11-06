package dev.tvassistant.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static dev.tvassistant.core.Command.Kind.*;

/** Bounded domain interpreter. A future model must return the same validated action types. */
public final class Interpreter {
    private static final Map<String,String> APPS;
    static {
        Map<String,String> m = new LinkedHashMap<String,String>();
        m.put("youtube music", "com.google.android.youtube.tvmusic");
        m.put("you tube music", "com.google.android.youtube.tvmusic");
        m.put("amazon prime video", "com.amazon.amazonvideo.livingroom");
        m.put("prime video", "com.amazon.amazonvideo.livingroom");
        m.put("prime", "com.amazon.amazonvideo.livingroom");
        m.put("youtube", "com.google.android.youtube.tv");
        m.put("you tube", "com.google.android.youtube.tv");
        m.put("netflix", "com.netflix.ninja");
        m.put("stremio", "com.stremio.one");
        m.put("jiohotstar", "in.startv.hotstar");
        m.put("jio hotstar", "in.startv.hotstar");
        m.put("hotstar", "in.startv.hotstar");
        m.put("sony liv", "com.sonyliv");
        m.put("sonyliv", "com.sonyliv");
        APPS = Collections.unmodifiableMap(m);
    }
    public static Map<String,String> apps() { return APPS; }
    private static Command clarify(String s) { return Command.explain(CLARIFY,s); }
    private static Command unsupported(String s) { return Command.explain(UNSUPPORTED,s); }
    private static Matcher match(String regex, String text) { return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text); }

    public Command parse(String utterance, SessionState state, long now) {
        if (utterance == null || utterance.trim().isEmpty()) return clarify("What would you like to do?");
        if (utterance.length() > 300 || utterance.matches("(?s).*[\\p{Cntrl}].*"))
            return clarify("Please give one short TV request.");
        String text = utterance.trim().replaceAll("\\s+", " ").replaceAll("[.!?]+$", "");
        text = text.replaceFirst("(?i)^(?:please |could you |can you |would you )", "");
        text = text.replaceFirst("(?i),? please$", "");
        String lower = text.toLowerCase(Locale.ROOT);
        // Exact aliases for Google's observed segmentation; the action adapter still
        // requires an unexpired receipt and verifies the unchanged brightness/preset.
        if (lower.matches("(?:undo|undo that|undo it|un do|un do that|and do|and do that|put it back|restore the previous setting|restore brightness|restore the brightness)")) return Command.simple(UNDO);
        if (lower.matches("(?:pause|pause it|pause the video|stop playback)")) return Command.simple(PAUSE);
        if (lower.matches("(?:resume|resume playback|continue playing|play)")) return Command.simple(RESUME);

        Matcher m = match("^(?:use|try|open it (?:on|in)) (.+?)(?: instead)?$", text);
        if (m.matches()) {
            String app = APPS.get(m.group(1).toLowerCase(Locale.ROOT));
            if (app == null) return clarify("Which installed app should I use?");
            if (!state.hasContent(now)) return clarify("What title should I open there?");
            return Command.content(SEARCH_APP,state.query(),app,state.year(),state.mediaType());
        }
        m = match("^(?:the )?((?:19|20)\\d{2})(?: one| version| film| movie)?$",text);
        if (m.matches()) {
            if (!state.hasContent(now)) return clarify("Which title do you mean?");
            return Command.content(state.app().isEmpty()?FIND_TITLE:SEARCH_APP,state.query(),state.app(),Integer.parseInt(m.group(1)),state.mediaType());
        }

        m = match("^(?:(?:switch|change|set)(?: the picture)? to |use )?(movie|cinema|standard|sports|game)(?: (?:picture )?mode)?$",text);
        if (m.matches()) return Command.mode(m.group(1).toLowerCase(Locale.ROOT));
        m=match("^(?:switch|change)(?: (?:the )?(?:input|source))? to (.+)$",text);
        if(m.matches())return m.group(1).length()<=60?Command.input(m.group(1)):clarify("Use the name of one TV input.");
        if(lower.matches("hdmi ?[1-4]|av"))return Command.input(text);
        m = match("^(?:set |change )?(?:the )?(?:backlight|brightness) to (\\d{1,3})(?: ?%| percent)?$",text);
        if (m.matches()) {
            int value = Integer.parseInt(m.group(1));
            return value<=100?Command.adjustment(SET_BACKLIGHT,value):clarify("Choose a backlight level between 0 and 100.");
        }
        m=match("^(?:(?:make|turn) (?:it|the (?:screen|picture|tv)) )?(?:a little |slightly |a bit )?(dimmer|darker|brighter)(?: by (\\d{1,3})(?: points| percent|%)?)?$",text);
        if(m.matches()) {
            int amount=m.group(2)==null?(lower.contains("slightly")?5:10):Integer.parseInt(m.group(2));
            if(amount<1 || amount>20)return clarify("Try a brightness change between 1 and 20 points.");
            return Command.adjustment(ADJUST_BACKLIGHT,m.group(1).equalsIgnoreCase("brighter")?amount:-amount);
        }
        m=match("^(lower|reduce|decrease|increase|raise) (?:the )?(?:screen |picture )?(?:brightness|backlight)(?: by (\\d{1,3})(?: points| percent|%)?)?$",text);
        if(m.matches()) {
            int amount=m.group(2)==null?10:Integer.parseInt(m.group(2));
            if(amount<1 || amount>20)return clarify("Try a brightness change between 1 and 20 points.");
            boolean down=m.group(1).matches("(?i)lower|reduce|decrease");
            return Command.adjustment(ADJUST_BACKLIGHT,down?-amount:amount);
        }
        m = match("^(?:turn (?:the )?)?volume (up|down)(?: (\\d{1,2}))?$",text);
        if(lower.matches("(?:increase|raise|turn up)(?: the)? volume|louder|turn it up"))return Command.adjustment(VOLUME,1);
        if(lower.matches("(?:decrease|lower|reduce|turn down)(?: the)? volume|quieter|turn it down"))return Command.adjustment(VOLUME,-1);
        if (m.matches()) {
            int amount = m.group(2)==null?1:Integer.parseInt(m.group(2));
            if (amount<1 || amount>5) return clarify("Try one to five volume steps.");
            return Command.adjustment(VOLUME,m.group(1).equalsIgnoreCase("down")?-amount:amount);
        }
        m=match("^(rewind|fast forward|skip ahead|skip back|go back|go forward) (\\d{1,3}) seconds?$",text);
        if(m.matches()){
            int seconds=Integer.parseInt(m.group(2));
            if(seconds<1 || seconds>600)return clarify("Try a seek within ten minutes.");
            boolean backwards=m.group(1).toLowerCase(Locale.ROOT).matches("rewind|skip back|go back");
            return Command.adjustment(SEEK,backwards?-seconds:seconds);
        }
        m = match("^(?:go |skip |rewind |fast forward )?(back|backward|forward) (\\d{1,3}) seconds?$",text);
        if (m.matches()) {
            int seconds=Integer.parseInt(m.group(2));
            if(seconds<1 || seconds>600) return clarify("Try a seek within ten minutes.");
            return Command.adjustment(SEEK,m.group(1).toLowerCase(Locale.ROOT).startsWith("back")?-seconds:seconds);
        }
        m=match("^(?:open|launch|start) (.+)$",text);
        if(m.matches() && APPS.containsKey(m.group(1).toLowerCase(Locale.ROOT)))
            return Command.content(OPEN_APP,"",APPS.get(m.group(1).toLowerCase(Locale.ROOT)),0);
        m=match("^(?:play|watch|find|search for|search)(?: (?:the )?(movie|film|show|series|song|video))? (.+?) (?:on|in|using) (.+)$",text);
        if(m.matches()) {
            String app=APPS.get(m.group(3).toLowerCase(Locale.ROOT));
            if(app==null) return clarify("I don't have a verified adapter for that app yet.");
            return Command.content(SEARCH_APP,m.group(2),app,extractYear(m.group(2)),mediaType(m.group(1)));
        }
        m=match("^(?:play|watch|find|search for|search)(?: (?:the )?(movie|film|show|series|song|video))? (.+)$",text);
        if(m.matches()) return Command.content(FIND_TITLE,m.group(2),"",extractYear(m.group(2)),mediaType(m.group(1)));
        return unsupported("Try a title, an app, a picture setting, or a playback command.");
    }
    private static String mediaType(String value){if(value==null)return "";value=value.toLowerCase(Locale.ROOT);return value.equals("film")?"movie":value.equals("series")?"show":value;}
    private static int extractYear(String title) {
        Matcher m=Pattern.compile("\\b((?:19|20)\\d{2})\\b").matcher(title);
        return m.find()?Integer.parseInt(m.group(1)):0;
    }
}
