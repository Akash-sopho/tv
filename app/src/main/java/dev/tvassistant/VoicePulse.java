package dev.tvassistant;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Small audio-reactive indicator; no audio sampling or background animation. */
final class VoicePulse extends View {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private boolean active;private float level;
 VoicePulse(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
 void active(boolean value){active=value;invalidate();}
 void level(int value){level=Math.max(0,Math.min(100,value))/100f;invalidate();}
 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);float d=getResources().getDisplayMetrics().density;
  float phase=SystemClock.uptimeMillis()/230f,cx=getWidth()/2f,cy=getHeight()/2f;
  paint.setColor(Color.WHITE);paint.setAlpha(active?235:130);
  for(int i=0;i<5;i++){
   float pulse=active?(float)(.5+.5*Math.sin(phase+i*.85)):0;
   float height=(5+(active?5*pulse+17*level*(.5f+.5f*pulse):0))*d;
   float x=cx+(i-2)*7*d;canvas.drawRoundRect(x-1.6f*d,cy-height/2,x+1.6f*d,cy+height/2,2*d,2*d,paint);
  }
  if(active && isShown())postInvalidateDelayed(50);
 }
 @Override protected void onDetachedFromWindow(){active=false;super.onDetachedFromWindow();}
}
