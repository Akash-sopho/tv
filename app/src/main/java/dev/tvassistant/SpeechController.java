package dev.tvassistant;
import android.content.Context;
/** Google handles speech; our app receives text and interprets bounded TV requests. */
final class SpeechController {
 interface Listener { void state(String message); void result(String text); void partial(String text); void level(int value); }
 private final GoogleSpeech speech;
 SpeechController(Context c,Listener listener){speech=new GoogleSpeech(c,listener);}
 void start(){speech.start();}
 void cancel(){speech.cancel();}
}
