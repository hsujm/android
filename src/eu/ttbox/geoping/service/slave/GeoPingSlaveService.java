package eu.ttbox.geoping.service.slave;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.RemoteViews;
import eu.ttbox.geoping.R;
import eu.ttbox.geoping.core.AppConstants;
import eu.ttbox.geoping.core.Intents;
import eu.ttbox.geoping.domain.GeoTrack;
import eu.ttbox.geoping.domain.geotrack.GeoTrackHelper;
import eu.ttbox.geoping.service.core.WorkerService;
import eu.ttbox.geoping.service.encoder.SmsMessageActionEnum;
import eu.ttbox.geoping.service.encoder.SmsMessageIntentEncoderHelper;
import eu.ttbox.geoping.service.slave.receiver.PhoneAuthorizeTypeEnum;
import eu.ttbox.geoping.ui.map.mylocation.sensor.MyLocationListenerProxy;

public class GeoPingSlaveService extends WorkerService {

    private static final String TAG = "GeoPingSlaveService";

    private static final int SHOW_GEOPING_REQUEST_NOTIFICATION_ID = R.id.show_notification_new_geoping_request_confirm;

    private final IBinder binder = new LocalBinder();
    // Constant

    // Services
    private LocationManager locationManager;
    private MyLocationListenerProxy myLocation;
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private SharedPreferences appPreferences;

    // Instance Data
    private List<GeoPingRequest> geoPingRequestList;
    private MultiGeoRequestLocationListener multiGeoRequestListener;

    private int batterLevelInPercent = -1;

    // Config
    boolean displayGeopingRequestNotification = false;
    Set<String> secuAuthorizeNeverPhoneSet;
    Set<String> secuAuthorizeAlwaysPhoneSet;

    // ===========================================================
    // Constructors
    // ===========================================================

    public GeoPingSlaveService() {
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // service
        this.appPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        this.myLocation = new MyLocationListenerProxy(locationManager);
        this.geoPingRequestList = new ArrayList<GeoPingRequest>();
        this.multiGeoRequestListener = new MultiGeoRequestLocationListener(geoPingRequestList);
        loadPrefConfig();
        Log.d(TAG, "#################################");
        Log.d(TAG, "### GeoPing Service Started.");
        Log.d(TAG, "#################################");
    }

    private void loadPrefConfig() {
        this.displayGeopingRequestNotification = appPreferences.getBoolean(AppConstants.PREFS_GEOPING_REQUEST_NOTIFYME, false);

        // Read Security Set
        this.secuAuthorizeNeverPhoneSet = readPrefPhoneSet(AppConstants.PREFS_PHONES_SET_AUTHORIZE_NEVER);
        this.secuAuthorizeAlwaysPhoneSet = readPrefPhoneSet(AppConstants.PREFS_PHONES_SET_AUTHORIZE_ALWAYS);
    }

    @Override
    public void onDestroy() {
        this.myLocation.stopListening();
        geoPingRequestList.clear();
        super.onDestroy();
        Log.d(TAG, "#################################");
        Log.d(TAG, "### GeoPing Service Destroyed.");
        Log.d(TAG, "#################################");
    }

    // ===========================================================
    // Intent Handler
    // ===========================================================

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, String.format("onHandleIntent for action %s : %s", action, intent));
        if (Intents.ACTION_SMS_GEOPING_REQUEST_HANDLER.equals(action)) {
            String phone = intent.getStringExtra(Intents.EXTRA_SMS_PHONE);
            Bundle params = intent.getBundleExtra(Intents.EXTRA_SMS_PARAMS);
            // Request
            // registerGeoPingRequest(phone, params);
            if (isAuthorizePhoneAlways(phone)) {
                registerGeoPingRequest(phone, params);
            } else if (!isAuthorizePhoneNever(phone)) {
                showNotificationNewPingRequestConfirm(phone, params);
            } else {
                Log.i(TAG, "Ignore Never Authorize Geoping request from phone " + phone);
            }

        } else if (Intents.ACTION_SLAVE_GEOPING_PHONE_AUTHORIZE.equals(action)) {
            manageAuthorizeIntent(intent.getExtras());
        } else if (Intents.ACTION_SMS_PAIRING_RESQUEST.equals(action)) {
            String phone = intent.getStringExtra(Intents.EXTRA_SMS_PHONE);
            Bundle params = intent.getBundleExtra(Intents.EXTRA_SMS_PARAMS);
            if (isAuthorizePhoneAlways(phone)) {

            } else if (!isAuthorizePhoneNever(phone)) {
                showNotificationPairingRequest(phone, params);
            }
        }
    }

    // ===========================================================
    // GeoPing Security
    // ===========================================================

    private void manageAuthorizeIntent(Bundle extras) {
        String phone = extras.getString(Intents.EXTRA_SMS_PHONE);
        Bundle params = extras.getBundle(Intents.EXTRA_SMS_PARAMS);
        int typeOrdinal = extras.getInt(Intents.EXTRA_AUTHORIZE_PHONE_TYPE_ORDINAL);
        PhoneAuthorizeTypeEnum type = PhoneAuthorizeTypeEnum.values()[typeOrdinal];
        Log.d(TAG, String.format("manageAuthorizeIntent for phone %s with security policy %s (%s)", phone, type, typeOrdinal));
        switch (type) {
        case NEVER:
            Log.d(TAG, "Need to authorizePhoneNever case Never");
            // authorizePhoneNever(phone);
        case NO:
            Log.d(TAG, "Need to authorizePhoneNever case No");
            Log.i(TAG, "Ignore Geoping request from phone " + phone);
            break;
        case ALWAYS:
            Log.d(TAG, "Need to authorizePhoneNever case ALWAYS");
            // authorizePhoneAlways(phone);
        case YES:
            Log.d(TAG, "Need to authorizePhoneNever case Yes");
            registerGeoPingRequest(phone, params);
            break;
        default:
            Log.w(TAG, "Not manage PhoneAuthorizeTypeEnum for " + type);
            break;
        }
    }

    private void authorizePhoneNever(String phone) {
        if (!secuAuthorizeNeverPhoneSet.contains(phone)) {
            secuAuthorizeNeverPhoneSet.add(phone);
            Editor editor = appPreferences.edit();
            // Never
            String neverPhoneString = convertPhoneSetAsString(secuAuthorizeNeverPhoneSet);
            editor.putString(AppConstants.PREFS_PHONES_SET_AUTHORIZE_NEVER, neverPhoneString);
            // Always
            if (secuAuthorizeAlwaysPhoneSet.contains(phone)) {
                secuAuthorizeAlwaysPhoneSet.remove(phone);
                String alwaysPhoneString = convertPhoneSetAsString(secuAuthorizeAlwaysPhoneSet);
                editor.putString(AppConstants.PREFS_PHONES_SET_AUTHORIZE_ALWAYS, alwaysPhoneString);
            }
            Log.i(TAG, "Security Phone Authorize NEVER for Phone : " + phone);
            editor.commit();
        }
    }

    private boolean isAuthorizePhoneAlways(String phone) {
        return secuAuthorizeAlwaysPhoneSet.contains(phone);
    }

    private boolean isAuthorizePhoneNever(String phone) {
        return secuAuthorizeNeverPhoneSet.contains(phone);
    }

    private void authorizePhoneAlways(String phone) {
        if (!secuAuthorizeAlwaysPhoneSet.contains(phone)) {
            secuAuthorizeAlwaysPhoneSet.add(phone);
            Editor editor = appPreferences.edit();
            // Never
            String alwayPhoneString = convertPhoneSetAsString(secuAuthorizeAlwaysPhoneSet);
            editor.putString(AppConstants.PREFS_PHONES_SET_AUTHORIZE_ALWAYS, alwayPhoneString);
            // Always
            if (secuAuthorizeNeverPhoneSet.contains(phone)) {
                secuAuthorizeNeverPhoneSet.remove(phone);
                String neverPhoneString = convertPhoneSetAsString(secuAuthorizeNeverPhoneSet);
                editor.putString(AppConstants.PREFS_PHONES_SET_AUTHORIZE_NEVER, neverPhoneString);
            }
            Log.i(TAG, "Security Phone Authorize ALWAYS for Phone : " + phone);
            editor.commit();
        }
    }

    private Set<String> readPrefPhoneSet(String key) {
        String phoneSet = appPreferences.getString(key, null);
        HashSet<String> result = new HashSet<String>();
        if (phoneSet != null && phoneSet.length() > 0) {
            int phoneSetSize = phoneSet.length();
            int pos = 0;
            int end = 0;
            while ((end = phoneSet.indexOf(AppConstants.PHONE_SEP, pos)) >= 0) {
                result.add(phoneSet.substring(pos, end));
                pos = end + 1;
            }
            if (pos < phoneSetSize) {
                result.add(phoneSet.substring(pos, phoneSetSize));
            }
        }
        return result;
    }

    private String convertPhoneSetAsString(Set<String> phoneSet) {
        StringBuilder sb = new StringBuilder();
        boolean addSep = false;
        for (String phone : phoneSet) {
            if (addSep) {
                sb.append(AppConstants.PHONE_SEP);
            }
            sb.append(phone);
            addSep = true;
        }
        return sb.toString();
    }

    // ===========================================================
    // Other
    // ===========================================================

    public boolean registerGeoPingRequest(String phoneNumber, Bundle params) {
        Location initLastLoc = myLocation.getLastKnownLocation();
        GeoPingRequest request = new GeoPingRequest(phoneNumber, params);
        geoPingRequestList.add(request);
        // TODO Bad for multi request
        boolean locProviderEnabled = myLocation.startListening(multiGeoRequestListener);
        // schedule it for time out
        int timeOutInSeconde = 30;
        executorService.schedule(request, timeOutInSeconde, TimeUnit.SECONDS);
        return locProviderEnabled;
    }

    public void unregisterGeoPingRequest(GeoPingRequest request) {
        boolean isRemove = geoPingRequestList.remove(request);
        if (isRemove) {

        } else {
            Log.e(TAG, "Could not remove expected GeoPingRequest. /!\\ Emmergency Stop Service /!\\");
            geoPingRequestList.clear();
        }
        if (geoPingRequestList.isEmpty()) {
            Log.d(TAG, "No GeoPing Request in list, do Stop Service");
            myLocation.stopListening();
            stopSelf();
        }
    }

    private void sendSmsLocation(String phone, Location location) {
        GeoTrack geotrack = new GeoTrack(null, location);
        Bundle params = GeoTrackHelper.getBundleValues(geotrack); 
        sendSms(phone, SmsMessageActionEnum.ACTION_GEO_LOC, params);
    }

    private void sendSms(String phone, SmsMessageActionEnum action, Bundle params) {
        String encrypedMsg = SmsMessageIntentEncoderHelper.encodeSmsMessage(  action,   params);
        if (encrypedMsg != null && encrypedMsg.length() > 0 && encrypedMsg.length() <= AppConstants.SMS_MAX_SIZE) {
            SmsManager.getDefault().sendTextMessage(phone, null, encrypedMsg, null, null);
        } else {
            Log.e(TAG, String.format("Too long SmsMessage (%s chars, args) : %s", encrypedMsg.length() , encrypedMsg));
        }
    }

    /**
     * Computes the battery level by registering a receiver to the intent
     * triggered by a battery status/level change. {link
     * http://mobile.dzone.com/news/getting-battery-level-android}
     */

    private void batteryLevel() {
        BroadcastReceiver batteryLevelReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                context.unregisterReceiver(this);
                int rawlevel = intent.getIntExtra("level", -1);
                int scale = intent.getIntExtra("scale", -1);
                int level = -1;
                if (rawlevel >= 0 && scale > 0) {
                    level = (rawlevel * 100) / scale;
                }
                batterLevelInPercent = level;
            }
        };

        IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryLevelReceiver, batteryLevelFilter);
    }

    public class GeoPingRequest implements Callable<Boolean>, LocationListener {

        public String smsPhoneNumber;
        public Bundle params;

        public GeoPingRequest() {
            super();
        }

        public GeoPingRequest(String phoneNumber, Bundle params) {
            super();
            this.smsPhoneNumber = phoneNumber;
            this.params = params;
        }

        @Override
        public Boolean call() throws Exception {
            Location lastLocation = myLocation.getLastFix();
            if (lastLocation != null) {
                sendSmsLocation(smsPhoneNumber, lastLocation);
                unregisterGeoPingRequest(this);
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        }

        @Override
        public void onLocationChanged(Location location) {
            // TODO check expected accuracy
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

    }

    // ===========================================================
    // Notification
    // ===========================================================

    private void showNotificationPairingRequest(String phone, Bundle params) {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Style
       
        RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.notif_pairing_request);
        contentView.setTextViewText(R.id.notif_geoping_phone, phone);
        contentView.setOnClickPendingIntent(R.id.notif_geoping_confirm_button_no, PendingIntent.getService(this, 0, //
                Intents.authorizePhone(this, phone, params, PhoneAuthorizeTypeEnum.NO),//
                PendingIntent.FLAG_UPDATE_CURRENT));
        contentView.setOnClickPendingIntent(R.id.notif_geoping_confirm_button_never, PendingIntent.getService(this, 1, //
                Intents.authorizePhone(this, phone, params, PhoneAuthorizeTypeEnum.NEVER),//
                PendingIntent.FLAG_UPDATE_CURRENT)); 
        contentView.setOnClickPendingIntent(R.id.notif_geoping_confirm_button_always, PendingIntent.getService(this, 3, //
                Intents.authorizePhone(this, phone, params, PhoneAuthorizeTypeEnum.ALWAYS),//
                PendingIntent.FLAG_UPDATE_CURRENT));
        
        // Create Notifiation
      
        Notification notification = new NotificationCompat.Builder(this) //
                .setDefaults(Notification.DEFAULT_ALL) //
                .setSmallIcon(R.drawable.icon_notif) //
                .setWhen(System.currentTimeMillis()) //
                .setAutoCancel(true) //
                .setContentTitle("GeoPing Request" + phone) //
                .setContentText("GeoTrack point") //
                // .setContent(contentView) //
                .build();
        // Show
        mNotificationManager.notify(SHOW_GEOPING_REQUEST_NOTIFICATION_ID, notification);

    }
    private void showNotificationNewPingRequestPost(String phone, Bundle params) {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // Create Notifiation 
        Notification notification = new NotificationCompat.Builder(this) //
                .setDefaults(Notification.DEFAULT_ALL) //
                .setSmallIcon(R.drawable.icon_notif) // 
                .setWhen(System.currentTimeMillis()) //
                .setAutoCancel(true) //
                .setContentTitle(getString(R.string.notif_geoping_response)) //
                .setContentText(phone) //
                 .setPriority(Notification.PRIORITY_DEFAULT) //
                .build();
        // Show
        mNotificationManager.notify(SHOW_GEOPING_REQUEST_NOTIFICATION_ID, notification);
    }

    private void showNotificationNewPingRequestConfirm(String phone, Bundle params) {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
        // Intents.showOnMap(this, geoTrackData, values),
        // PendingIntent.FLAG_CANCEL_CURRENT);

        // remote View
        RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.notif_geoping_request_register);
        contentView.setTextViewText(R.id.notif_geoping_phone, phone);
        // Manage Button Confirmation
        contentView.setOnClickPendingIntent(R.id.notif_geoping_confirm_button_no, PendingIntent.getService(this, 0, //
                Intents.authorizePhone(this, phone, params, PhoneAuthorizeTypeEnum.NO),//
                PendingIntent.FLAG_UPDATE_CURRENT));
        contentView.setOnClickPendingIntent(R.id.notif_geoping_confirm_button_never, PendingIntent.getService(this, 1, //
                Intents.authorizePhone(this, phone, params, PhoneAuthorizeTypeEnum.NEVER),//
                PendingIntent.FLAG_UPDATE_CURRENT));
        contentView.setOnClickPendingIntent(R.id.notif_geoping_confirm_button_yes, PendingIntent.getService(this, 2, //
                Intents.authorizePhone(this, phone, params, PhoneAuthorizeTypeEnum.YES),//
                PendingIntent.FLAG_UPDATE_CURRENT));
        contentView.setOnClickPendingIntent(R.id.notif_geoping_confirm_button_always, PendingIntent.getService(this, 3, //
                Intents.authorizePhone(this, phone, params, PhoneAuthorizeTypeEnum.ALWAYS),//
                PendingIntent.FLAG_UPDATE_CURRENT));

        // Create Notifiation
        Notification notification = new NotificationCompat.Builder(this) //
                .setDefaults(Notification.DEFAULT_ALL) //
                .setSmallIcon(R.drawable.icon_notif) //
                .setWhen(System.currentTimeMillis()) //
                .setAutoCancel(true) //
                .setContentTitle("GeoPing Request" + phone) //
                .setContentText("GeoTrack point") //
                .setContent(contentView) //
                .build();
        // Show
        mNotificationManager.notify(SHOW_GEOPING_REQUEST_NOTIFICATION_ID, notification);
    }

    // ===========================================================
    // Binder
    // ===========================================================

    public class LocalBinder extends Binder {
        public GeoPingSlaveService getService() {
            return GeoPingSlaveService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

}