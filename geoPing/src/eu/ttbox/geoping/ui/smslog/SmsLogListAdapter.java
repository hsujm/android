package eu.ttbox.geoping.ui.smslog;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import eu.ttbox.geoping.GeoPingApplication;
import eu.ttbox.geoping.R;
import eu.ttbox.geoping.domain.model.SmsLogTypeEnum;
import eu.ttbox.geoping.domain.smslog.SmsLogHelper;
import eu.ttbox.geoping.service.core.ContactHelper;
import eu.ttbox.geoping.service.encoder.SmsMessageActionEnum;
import eu.ttbox.geoping.ui.person.PhotoThumbmailCache;

/**
 * @see platform_packages_apps_contacts/res/layout/call_log_list_item.xml
 * @see platform_packages_apps_contacts/res/layout/call_detail_history_item.xml
 * @see com.android.contacts.PhoneCallDetailsHelper#callTypeAndDate
 * @see android.text.format.DateUtils#getRelativeTimeSpanString(long time, long now, long minResolution,  int flags)
 *
 */
public class SmsLogListAdapter extends android.support.v4.widget.ResourceCursorAdapter {

    private static final String TAG = "SmsLogListAdapter";

	private SmsLogHelper helper;

    private boolean isNotBinding = true;

    private Resources mResources;

    private android.content.res.Resources resources;

    // Cache
    private PhotoThumbmailCache photoCache;

    // ===========================================================
    // Constructor
    // ===========================================================

    public SmsLogListAdapter(Context context, Cursor c, int flags) {
        super(context, R.layout.smslog_list_item, c, flags);
        this.resources = context.getResources();
        this.mResources = new Resources(context);
        // Cache
        photoCache = ((GeoPingApplication) context.getApplicationContext()).getPhotoThumbmailCache();
    }

    private void intViewBinding(View view, Context context, Cursor cursor) {
        // Init Cursor
        helper = new SmsLogHelper().initWrapper(cursor);
        isNotBinding = false;
    }

    @Override
    public void bindView(View view, final Context context, Cursor cursor) {
        if (isNotBinding) {
            intViewBinding(view, context, cursor);
        }
        ViewHolder holder = (ViewHolder) view.getTag();
        // Bind Value
        SmsLogTypeEnum smLogType = helper.getSmsLogType(cursor);
        Drawable iconType = getCallTypeDrawable(smLogType);
        holder.smsType.setImageDrawable(iconType);
        // Text
        SmsMessageActionEnum action = helper.getSmsMessageActionEnum(cursor);
        String actionLabel;
        if (action!=null) {
        	  actionLabel = getSmsActionLabel(action); 
        } else {
        	 actionLabel = helper.getSmsMessageActionString(cursor);
        }
        holder.actionText.setText(actionLabel);
        // Phone
        helper.setTextSmsLogPhone(holder.phoneText, cursor);
        // Time
        long time = helper.getSmsLogTime(cursor);
        String timeFormat = String.format("%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS", time);
        holder.timeText.setText(timeFormat);
        // Time relative
        long currentTimeMillis = System.currentTimeMillis();
        CharSequence dateText =
                DateUtils.getRelativeTimeSpanString(time,
                        currentTimeMillis,
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE);
        holder.timeAgoText.setText(dateText);
        // Load Photos

    }

  


    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = super.newView(context, cursor, parent);
        // Then populate the ViewHolder
        ViewHolder holder = new ViewHolder();
        holder.timeAgoText = (TextView) view.findViewById(R.id.smslog_list_item_time_ago);
        holder.timeText = (TextView) view.findViewById(R.id.smslog_list_item_time);
        holder.phoneText = (TextView) view.findViewById(R.id.smslog_list_item_phone);
        holder.actionText = (TextView) view.findViewById(R.id.smslog_list_item_action);
        holder.smsType = (ImageView) view.findViewById(R.id.smslog_list_item_smsType_imgs);
        holder.photoImageView = (ImageView) view.findViewById(R.id.smslog_photo_imageView);
        // and store it inside the layout.
        view.setTag(holder);
        return view;
    }

    static class ViewHolder {
        TextView actionText;
        TextView timeText;
        TextView timeAgoText;
        TextView phoneText;
        ImageView smsType;
        ImageView photoImageView;
    }


    private Drawable getCallTypeDrawable(SmsLogTypeEnum callType) {
        switch (callType) {
        case RECEIVE:
            return mResources.incoming; 
        case SEND_REQ:
            return mResources.outgoing_request;
        case SEND_ACK:
            return mResources.outgoing;
        case SEND_DELIVERY_ACK: 
            return mResources.outgoing_delivery_ack;
        case SEND_ERROR:
            return mResources.outgoing_error;
        default:
            throw new IllegalArgumentException("invalid call type: " + callType);
        }
    }
    
    private String getSmsActionLabel(SmsMessageActionEnum action) {
//    	Log.d(TAG, "getSmsActionLabel : "  + action); 
    	 return action.getLabel(resources);
    	 	
    	// FIXME
//        switch (action) {
//        case GEOPING_REQUEST:
//            return mResources.actionGeoPingRequest;
//        case ACTION_GEO_LOC:
//            return mResources.actionGeoPingResponse;
//        case ACTION_GEO_PAIRING:
//            return mResources.actionPairingRequest;
//        case ACTION_GEO_PAIRING_RESPONSE:
//            return mResources.actionPairingResponse;
//         default:
//            return action.name();
//        }
    }
    
    private static class Resources {
        public final Drawable incoming;
        public final Drawable outgoing_request;
        public final Drawable outgoing;
//        public final Drawable missed;
//        public final Drawable voicemail;
        public final Drawable outgoing_delivery_ack;
        public final Drawable outgoing_error;
        
        public final String actionGeoPingRequest;
        public final String actionGeoPingResponse;
        public final String actionPairingRequest;
        public final String actionPairingResponse;
         
        public Resources(Context context) {
            final android.content.res.Resources r = context.getResources();
            incoming = r.getDrawable(R.drawable.ic_call_incoming);
            outgoing_request = r.getDrawable(R.drawable.ic_call_outgoing_request );
            outgoing = r.getDrawable(R.drawable.ic_call_outgoing );
            outgoing_error = r.getDrawable(R.drawable.ic_call_outgoing_error );
            outgoing_delivery_ack = r.getDrawable(R.drawable.ic_call_outgoing_delivery_ack );
//            missed = r.getDrawable(R.drawable.ic_call_missed );
//            voicemail = r.getDrawable(R.drawable.ic_call_voicemail_holo_dark);
            // Text
            actionGeoPingRequest = r.getString(R.string.sms_action_geoping_request);
            actionGeoPingResponse = r.getString(R.string.sms_action_geoping_response);
            actionPairingRequest = r.getString(R.string.sms_action_pairing_request);
            actionPairingResponse = r.getString(R.string.sms_action_pairing_response);
        }
        
        
    }




    // ===========================================================
    // Photo Loader
    // ===========================================================

    /**
     * Pour plus de details sur l'intégration dans les contacts consulter
     * <ul>
     * <li>item_photo_editor.xml</li>
     * <li>com.android.contacts.editor.PhotoEditorView</li>
     * <li>com.android.contacts.detail.PhotoSelectionHandler</li>
     * <li>com.android.contacts.editor.ContactEditorFragment.PhotoHandler</li>
     * </ul>
     *
     * @param contactId
     */
    private void loadPhoto(ViewHolder holder, String contactId, final String phone) {
        Bitmap photo = null;
        boolean isContactId = !TextUtils.isEmpty(contactId);
        boolean isContactPhone = !TextUtils.isEmpty(phone);
        // Search in cache
        if (photo == null && isContactId) {
            photo = photoCache.get(contactId);
        }
        if (photo == null && isContactPhone) {
            photo = photoCache.get(phone);
        }
        // Set Photo
        if (photo != null) {
            photoImageView.setImageBitmap(photo);
        } else if (isContactId || isContactPhone) {
            // Cancel previous Async
            final PhotoLoaderAsyncTask oldTask = (PhotoLoaderAsyncTask) holder.photoImageView.getTag();
            if (oldTask != null) {
                oldTask.cancel(false);
            }
            // Load photos
            PhotoLoaderAsyncTask newTask = new PhotoLoaderAsyncTask(holder.photoImageView);
            holder.photoImageView.setTag(newTask);
            newTask.execute(contactId, phone);
        }
        // photoImageView.setEditorListener(new EditorListener() {
        //
        // @Override
        // public void onRequest(int request) {
        // Toast.makeText(getActivity(), "Click to phone " + phone,
        // Toast.LENGTH_SHORT).show();
        // }
        //
        // });
    }

    public class PhotoLoaderAsyncTask extends AsyncTask<String, Void, Bitmap> {

        final ImageView holder;

        public PhotoLoaderAsyncTask(ImageView holder) {
            super();
            this.holder = holder;
        }

        @Override
        protected void onPreExecute() {
            holder.setTag(this);
        }

        @Override
        protected Bitmap doInBackground(String... params) {
            String contactIdSearch = params[0];
            String phoneSearch = null;
            if (params.length > 1) {
                phoneSearch = params[1];
            }
            Bitmap result = ContactHelper.openPhotoBitmap(mContext, photoCache, contactIdSearch, phoneSearch);
            Log.d(TAG, "PhotoLoaderAsyncTask load photo : " + (result != null));
            return result;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if (holder.getTag() == this) {
                holder.setImageBitmap(result);
                holder.setTag(null);
                Log.d(TAG, "PhotoLoaderAsyncTask onPostExecute photo : " + (result != null));
            }
        }
    }

    // ===========================================================
    // Others
    // ===========================================================

}
