package com.android.wujiahui.flickrview;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import java.util.List;

/**
 * Created by 武家辉 on 2015/12/31.
 */
public class PollService extends IntentService {
    private static final String TAG = "PollService";

    private static final int POLL_INTERVAL = 1000*60;
//    private static final long POLL_INTERVAL = AlarmManager.INTERVAL_FIFTEEN_MINUTES;

    public static final String  ACTION_SHOW_NOTIFICCATION =
            "com.android.wujiahui.flickrview.SHOW_NOTIFICATION";
    public static final String PERM_PRIVATE =
            "com.android.wujiahui.flickrview.PRIVATE";
    public static final String REQUEST_CODE = "REQUEST_CODE";
    public static final String NOTIFICATION = "NOTIFICATION";
    public static Intent newIntent(Context context) {
        return new Intent(context, PollService.class);
    }

    public static void setServiceAlarm (Context context, boolean isOn) {
        Intent i = PollService.newIntent(context);
        PendingIntent pi = PendingIntent.getService(context, 0, i, 0);

        AlarmManager alarmManager = (AlarmManager)
                context.getSystemService(Context.ALARM_SERVICE);

        if (isOn) {
            alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime(), POLL_INTERVAL, pi);
        } else {
            alarmManager.cancel(pi);
            pi.cancel();
        }

        QueryPreferences.setAlarmOn(context, isOn);
    }

    public static boolean isServiceAlarmOn(Context context) {
        Intent i = PollService.newIntent(context);
        PendingIntent pi = PendingIntent.getService(context, 0, i,PendingIntent.FLAG_NO_CREATE);
        return pi != null;
    }

    public PollService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
//        Log.i(TAG, "Received an intent: " + intent);
        if (!isNetworkAvailableAndConnected()) {
            return;
        }

        String query = QueryPreferences.getStoredQuery(this);
        String lastResulted = QueryPreferences.getLastResultedId(this);
        List<FlickrItem> items;

        if (query == null) {

            items = new FlickrFetchr().fetchRecentPhotos(0);
        } else {
            items = new FlickrFetchr().searchPhotos(query, 0);
        }

        if (items.size() == 0) {
            return;
        }

        String resultId = items.get(0).getId();

        if (resultId.equals(lastResulted)) {
            Log.i(TAG, "Got an old result: "+resultId);
        } else {
            Log.i(TAG, "Got a new result: "+resultId);

            Resources resources = getResources();
            Intent i = FlickrViewActivity.newIntent(this);
            PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);

            Notification notification = new NotificationCompat.Builder(this)
                    .setTicker(resources.getString(R.string.new_pictures_title))
                    .setSmallIcon(android.R.drawable.ic_menu_report_image)
                    .setContentTitle(resources.getString(R.string.new_pictures_title))
                    .setContentText(resources.getString(R.string.new_pictures_text))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();

//            NotificationManagerCompat notificationManager =
//                    NotificationManagerCompat.from(this);
//
//            notificationManager.notify(0, notification);
//
//            sendBroadcast(new Intent(ACTION_SHOW_NOTIFICCATION), PERM_PRIVATE);

            showBackgroundNotification(0, notification);
        }

        QueryPreferences.setLastResultId(this, resultId);
    }

    private void showBackgroundNotification(int requestcode, Notification notification) {
        Intent i = new Intent(ACTION_SHOW_NOTIFICCATION);
        i.putExtra(REQUEST_CODE, requestcode);
        i.putExtra(NOTIFICATION, notification);
        sendOrderedBroadcast(i, PERM_PRIVATE, null, null, Activity.RESULT_OK, null, null);
    }

    private boolean isNetworkAvailableAndConnected() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        boolean isNetworkAvailabel = cm.getActiveNetworkInfo() != null;
        boolean isNetworkConnected = isNetworkAvailabel &&
                cm.getActiveNetworkInfo().isConnected();

        return isNetworkConnected;
    }
}
