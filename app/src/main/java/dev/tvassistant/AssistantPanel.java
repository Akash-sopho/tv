package dev.tvassistant;

import android.content.*;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import dev.tvassistant.core.*;
import java.util.List;
import java.util.Locale;
import java.lang.ref.WeakReference;

/** Compact voice card over the existing app, with live words and request-scoped feedback. */
final class AssistantPanel extends LinearLayout implements SpeechController.Listener {
 private final TextView headline, transcript, detail;
 private final VoicePulse meter;
 private final EditText keyboard;
 private final Button speak;
 private final SpeechController speech;
 private final Interpreter interpreter=new Interpreter();
 private final SessionState session=new SessionState();
 private final ContentCoordinator content;
 private final LinearLayout options;
 private final LinearLayout actions;private final ScrollView scroll;
 private boolean actionLaunched;
 private static WeakReference<AssistantPanel> active=new WeakReference<AssistantPanel>(null);
 static boolean testRequest(String value){AssistantPanel panel=active.get();if(panel==null)return false;panel.result(value);return true;}
 void activate(){active=new WeakReference<AssistantPanel>(this);}
 private Runnable onActionStart;
 void setOnActionStart(Runnable callback){onActionStart=callback;}
 AssistantPanel(Context context) {
  super(context);setOrientation(VERTICAL);setPadding(dp(22),dp(14),dp(22),dp(14));
  android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();background.setColor(0xf2181a1d);background.setCornerRadius(dp(20));background.setStroke(dp(1),0x26ffffff);setBackground(background);setElevation(dp(10));
  LinearLayout top=new LinearLayout(context);top.setGravity(Gravity.CENTER_VERTICAL);addView(top);
  headline=text("Ready",14,0xffbfc2c7);top.addView(headline,new LayoutParams(0,-2,1));
  top.addView(text("Back to cancel",12,0xff92969d));
  LinearLayout words=new LinearLayout(context);words.setGravity(Gravity.CENTER_VERTICAL);LayoutParams wordParams=new LayoutParams(-1,-2);wordParams.topMargin=dp(8);addView(words,wordParams);
  meter=new VoicePulse(context);words.addView(meter,new LayoutParams(dp(44),dp(42)));
  transcript=text("What would you like to watch?",24,Color.WHITE);transcript.setMaxLines(2);transcript.setEllipsize(android.text.TextUtils.TruncateAt.END);
  LayoutParams transcriptParams=new LayoutParams(0,-2,1);transcriptParams.leftMargin=dp(14);words.addView(transcript,transcriptParams);
  detail=text("",14,0xffbfc2c7);detail.setMaxLines(3);detail.setPadding(dp(58),dp(7),0,dp(4));detail.setVisibility(GONE);addView(detail);
  actions=new LinearLayout(context);actions.setPadding(dp(48),dp(8),0,0);actions.setVisibility(GONE);addView(actions);
  speak=button("Try again",actions);
  Button cancel=button("Stop listening",actions);
  Button type=button("Type instead",actions);
  keyboard=new EditText(context);keyboard.setTextSize(20);keyboard.setSingleLine(true);
  keyboard.setTextColor(Color.WHITE);keyboard.setHintTextColor(0xffa8b9ce);keyboard.setHint("Type a request, then press Enter");
  keyboard.setVisibility(GONE);addView(keyboard,new LayoutParams(-1,dp(52)));
  scroll=new ScrollView(context);options=new LinearLayout(context);options.setOrientation(VERTICAL);scroll.addView(options);scroll.setVisibility(GONE);
  addView(scroll,new LayoutParams(-1,dp(168)));
  speech=new SpeechController(context,this);
  content=new ContentCoordinator(context,session,new ContentCoordinator.Listener(){
   public void working(String title,String info){options.removeAllViews();scroll.setVisibility(GONE);actions.setVisibility(GONE);headline.setText(title);detail.setVisibility(VISIBLE);detail.setText(info);meter.active(true);}
   public void choices(String title,List<ContentCoordinator.Choice> choices){
    meter.active(false);scroll.setVisibility(VISIBLE);actions.setVisibility(VISIBLE);detail.setVisibility(VISIBLE);headline.setText(title);transcript.setText("Choose an option");detail.setText("Use the remote, or say first, second, or third.");options.removeAllViews();
    for(int i=0;i<choices.size();i++){final String number=Integer.toString(i+1);Button choice=new Button(getContext());choice.setText(number+". "+choices.get(i).label);choice.setAllCaps(false);choice.setTextSize(18);options.addView(choice,new LayoutParams(-1,-2));choice.setOnClickListener(new OnClickListener(){public void onClick(View v){content.selection(number);}});}
    if(options.getChildCount()>0)options.getChildAt(0).requestFocus();
   }
   public void done(boolean ok,String message){meter.active(false);headline.setText(ok?"Done":"Could not verify");detail.setVisibility(VISIBLE);detail.setText(message);if(!actionLaunched)actions.setVisibility(VISIBLE);}
   public void launching(){hideForAction();}
  });
  active=new WeakReference<AssistantPanel>(this);
  speak.setOnClickListener(new OnClickListener(){public void onClick(View v){startSpeech();}});
  cancel.setText("Cancel");
  cancel.setOnClickListener(new OnClickListener(){public void onClick(View v){cancelRequest();headline.setText("Cancelled");detail.setVisibility(VISIBLE);detail.setText("Press Back to close.");}});
  type.setOnClickListener(new OnClickListener(){public void onClick(View v){stopSpeech();keyboard.setVisibility(VISIBLE);keyboard.requestFocus();}});
  keyboard.setOnEditorActionListener(new TextView.OnEditorActionListener(){public boolean onEditorAction(TextView v,int action,KeyEvent event){if(event==null || event.getAction()==KeyEvent.ACTION_DOWN){stopSpeech();result(v.getText().toString());}return true;}});
  speak.requestFocus();
 }
 private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
 private TextView text(String value,int size,int color){TextView v=new TextView(getContext());v.setText(value);v.setTextSize(size);v.setTextColor(color);return v;}
 private Button button(String title,LinearLayout parent){Button b=new Button(getContext(),null,android.R.attr.borderlessButtonStyle);b.setText(title);b.setAllCaps(false);b.setTextSize(14);parent.addView(b,new LayoutParams(-2,dp(40)));return b;}
 private void hideForAction(){actionLaunched=true;if(onActionStart!=null)onActionStart.run();}
 void reveal(){setAlpha(0);setTranslationY(dp(10));animate().alpha(1).translationY(0).setDuration(160).start();}
 void preview(){actions.setVisibility(GONE);detail.setVisibility(GONE);headline.setText("Listening");transcript.setText("A little dimmer");meter.active(true);}
 void startSpeech(){actionLaunched=false;content.cancel();keyboard.setVisibility(GONE);actions.setVisibility(GONE);scroll.setVisibility(GONE);detail.setVisibility(GONE);transcript.setText("What would you like to do?");meter.level(0);speech.start();}
 void stopSpeech(){speech.cancel();meter.level(0);meter.active(false);}
 void cancelRequest(){stopSpeech();content.cancel();PictureActionService.cancelActive();}
 void onHidden(){stopSpeech();if(!actionLaunched)content.cancel();}
 void release(){cancelRequest();content.release();if(active.get()==this)active.clear();}
 public void state(String message){
  meter.level(0);meter.active(true);headline.setTextColor(0xffbfc2c7);
  if(message.startsWith("Loading")){headline.setText("Getting ready");}
  else if(message.startsWith("Listening")){headline.setText("Listening");}
  else if(message.startsWith("Transcribing")){headline.setText("Understanding");}
  else {meter.active(false);headline.setText("Try again");detail.setVisibility(VISIBLE);detail.setText(message);actions.setVisibility(VISIBLE);speak.requestFocus();}
 }
 public void partial(String value){transcript.setText(value);}
 public void level(int value){meter.level(value);}
 public void result(String value){
  Diagnostics.count(getContext(),"interpreted_requests");
  meter.level(0);meter.active(false);headline.setText("Heard you");headline.setTextColor(0xffbfc2c7);transcript.setText(value);
  if(content.selection(value))return;
  options.removeAllViews();
  long now=SystemClock.elapsedRealtime();Command c=interpreter.parse(value,session,now);session.remember(c,now);
  if(c.kind==Command.Kind.ADJUST_BACKLIGHT || c.kind==Command.Kind.SET_BACKLIGHT || c.kind==Command.Kind.SET_PICTURE_MODE || c.kind==Command.Kind.UNDO){
   if(!PictureActionService.available()){detail.setText("Picture control is not connected. No setting was changed.");return;}
   detail.setText("Changing the picture setting. Checking the result...");
   hideForAction();
   PictureActionService.execute(c,new PictureActionService.Result(){public void done(boolean ok,String message){headline.setText(ok?"Done":"Could not finish");detail.setText(message);}});
   return;
  }
  if(c.kind!=Command.Kind.CLARIFY && c.kind!=Command.Kind.UNSUPPORTED){content.execute(c);return;}
  getContext().getSharedPreferences("diagnostics",0).edit().putBoolean("content_action_ok",false).putString("content_action_status",c.kind==Command.Kind.CLARIFY?"clarify":"unsupported").apply();
  String explanation;
  switch(c.kind){
   case ADJUST_BACKLIGHT: explanation=c.amount<0?"Request: make the picture dimmer.":"Request: make the picture brighter.";break;
   case SET_BACKLIGHT: explanation="Request: set brightness to "+c.amount+"%.";break;
   case SET_PICTURE_MODE: explanation="Request: change the picture preset.";break;
   case FIND_TITLE: explanation="Request: find "+c.query+" in India.";break;
   case SEARCH_APP: explanation="Request: find "+c.query+" on "+c.app+".";break;
   case CLARIFY: case UNSUPPORTED: explanation=c.message;break;
   default: explanation="Request recognized: "+c.kind.toString().toLowerCase(Locale.ROOT).replace('_',' ')+".";
  }
  detail.setVisibility(VISIBLE);detail.setText(explanation);actions.setVisibility(VISIBLE);speak.requestFocus();
 }
 @Override protected void onDetachedFromWindow(){speech.cancel();super.onDetachedFromWindow();}
}
