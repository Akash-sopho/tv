package dev.tvassistant;

import android.os.Bundle;
import android.service.voice.*;

public final class AssistantSessionService extends VoiceInteractionSessionService {
    @Override public VoiceInteractionSession onNewSession(Bundle args){return new AssistantSession(this);}
}
