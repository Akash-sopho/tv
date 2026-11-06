package dev.tvassistant;
import android.service.notification.NotificationListenerService;
/** Access is used on request for media controllers; notification contents are not collected. */
public final class MediaAccessService extends NotificationListenerService {
 @Override public void onListenerConnected(){Diagnostics.count(this,"media_access_ready");}
}
