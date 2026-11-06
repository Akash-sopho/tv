package dev.tvassistant.core;

/** Short in-memory context; no audio/transcript archive or viewing history. */
public final class SessionState {
    private String query = "";
    private String app = "";
    private String mediaType="";
    private int year;
    private long lastContentAt;
    public static final long TTL_MS = 5 * 60 * 1000L;
    public void remember(Command command, long now) {
        if (command.kind != Command.Kind.FIND_TITLE && command.kind != Command.Kind.SEARCH_APP) return;
        query = command.query; app = command.app; year = command.year; lastContentAt = now;
        mediaType=command.mediaType;
    }
    public boolean hasContent(long now) { return !query.isEmpty() && now >= lastContentAt && now-lastContentAt < TTL_MS; }
    public String query() { return query; }
    public String app() { return app; }
    public int year() { return year; }
    public String mediaType(){return mediaType;}
    public void clear() { query = ""; app = ""; mediaType="";year = 0; lastContentAt = 0; }
}
