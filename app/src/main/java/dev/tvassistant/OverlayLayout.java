package dev.tvassistant;

import android.content.Context;
import android.graphics.Color;
import android.view.*;
import android.widget.FrameLayout;

/** A transparent compositor surface, not a screenshot of the underlying app. */
final class OverlayLayout {
 static FrameLayout wrap(Context context,AssistantPanel panel){
  FrameLayout root=new FrameLayout(context);root.setBackgroundColor(Color.TRANSPARENT);
  float density=context.getResources().getDisplayMetrics().density;
  int width=Math.min((int)(760*density),context.getResources().getDisplayMetrics().widthPixels-(int)(80*density));
  FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(width,ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
  params.bottomMargin=(int)(28*density);root.addView(panel,params);return root;
 }
}
