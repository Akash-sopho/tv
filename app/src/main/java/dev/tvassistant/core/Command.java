package dev.tvassistant.core;

/** Structured actions only. There is deliberately no command-string/shell action. */
public final class Command {
    public enum Kind { FIND_TITLE, SEARCH_APP, OPEN_APP, ADJUST_BACKLIGHT, SET_BACKLIGHT,
        SET_PICTURE_MODE, VOLUME, PAUSE, RESUME, SEEK, SWITCH_INPUT, UNDO, CLARIFY, UNSUPPORTED }
    public final Kind kind;
    public final String query;
    public final String app;
    public final int amount;
    public final int year;
    public final String message;
    public final String mediaType;

    private Command(Kind kind, String query, String app, int amount, int year, String message) {
        this(kind,query,app,amount,year,message,"");
    }
    private Command(Kind kind, String query, String app, int amount, int year, String message,String mediaType) {
        this.kind = kind;
        this.query = query == null ? "" : query;
        this.app = app == null ? "" : app;
        this.amount = amount;
        this.year = year;
        this.message = message == null ? "" : message;
        this.mediaType=mediaType;
    }
    public static Command content(Kind kind,String query,String app,int year,String mediaType){
        if(mediaType==null || !mediaType.matches("|movie|show|song|video"))throw new IllegalArgumentException("Unknown media type");
        Command c=content(kind,query,app,year);
        return new Command(c.kind,c.query,c.app,c.amount,c.year,c.message,mediaType);
    }
    public static Command content(Kind kind, String query, String app, int year) {
        if (kind != Kind.FIND_TITLE && kind != Kind.SEARCH_APP && kind != Kind.OPEN_APP)
            throw new IllegalArgumentException("Not a content action");
        if (query != null && query.length() > 240) throw new IllegalArgumentException("Query too long");
        if (year != 0 && (year < 1888 || year > 2100)) throw new IllegalArgumentException("Invalid year");
        return new Command(kind, query, app, 0, year, "");
    }
    public static Command adjustment(Kind kind, int value) {
        if (kind == Kind.ADJUST_BACKLIGHT && (value == 0 || Math.abs(value) > 20))
            throw new IllegalArgumentException("Relative backlight change exceeds safe range");
        if (kind == Kind.SET_BACKLIGHT && (value < 0 || value > 100))
            throw new IllegalArgumentException("Backlight must be 0..100");
        if (kind == Kind.VOLUME && (value == 0 || Math.abs(value) > 5))
            throw new IllegalArgumentException("Volume change exceeds safe range");
        if (kind == Kind.SEEK && (value == 0 || Math.abs(value) > 600))
            throw new IllegalArgumentException("Seek must be within ten minutes");
        if (kind != Kind.ADJUST_BACKLIGHT && kind != Kind.SET_BACKLIGHT && kind != Kind.VOLUME && kind != Kind.SEEK)
            throw new IllegalArgumentException("Not an adjustment action");
        return new Command(kind, "", "", value, 0, "");
    }
    public static Command mode(String mode) {
        if (!mode.matches("movie|cinema|standard|sports|game")) throw new IllegalArgumentException("Unknown preset");
        return new Command(Kind.SET_PICTURE_MODE, mode, "", 0, 0, "");
    }
    public static Command input(String name){
        if(name==null || name.trim().isEmpty() || name.length()>60 || name.matches("(?s).*[\\p{Cntrl}].*"))throw new IllegalArgumentException("Invalid input name");
        return new Command(Kind.SWITCH_INPUT,name.trim(),"",0,0,"");
    }
    public static Command simple(Kind kind) {
        if (kind != Kind.PAUSE && kind != Kind.RESUME && kind != Kind.UNDO) throw new IllegalArgumentException("Not a simple action");
        return new Command(kind, "", "", 0, 0, "");
    }
    public static Command explain(Kind kind, String message) {
        if (kind != Kind.CLARIFY && kind != Kind.UNSUPPORTED) throw new IllegalArgumentException("Not an explanation");
        return new Command(kind, "", "", 0, 0, message);
    }
}
