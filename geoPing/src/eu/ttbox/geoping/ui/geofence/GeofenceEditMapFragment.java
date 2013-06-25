package eu.ttbox.geoping.ui.geofence;


import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.BoundingBoxE6;
import org.osmdroid.util.GeoPoint;

import eu.ttbox.geoping.domain.model.CircleGeofence;
import eu.ttbox.geoping.ui.map.ShowMapFragment;
import eu.ttbox.geoping.ui.map.geofence.GeofenceEditOverlay;

public class GeofenceEditMapFragment extends ShowMapFragment {

    private static final String TAG = "GeofenceEditMapFragment";

    private CircleGeofence editGeofence ;

    // ===========================================================
    // Constructors
    // ===========================================================
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);
        return v;
    }

    @Override
    public void loadDefaultDatas() {
        // Activate
        if (editGeofence!=null) {
            displayGeofence(editGeofence);
        }
    }

    private  void displayGeofence(CircleGeofence editGeofence) {
        if (mapController !=null) {
            // Prepare Inser
            if (editGeofence.id== -1 ) {
                // Compute the default fence Size
                BoundingBoxE6 boundyBox = mapView.getBoundingBox();
                IGeoPoint center = boundyBox.getCenter();
                int radiusInMeters = boundyBox.getDiagonalLengthInMeters() / 8;
                radiusInMeters = Math.max(50, radiusInMeters);
                // Define to default Point
                editGeofence.setCenter(center);
                editGeofence.setRadiusInMeters(radiusInMeters);
                Log.d(TAG, "Prepare Insert for : " + editGeofence);
            }
            //Define Center
            mapController.setCenter(editGeofence.getCenterAsGeoPoint() );
            // Do Edit
            GeofenceEditOverlay mapOverlay =  super.showGeofenceOverlays();
            mapOverlay.doEditCircleGeofenceWithoutMenu(editGeofence);
        }
    }

    // ===========================================================
    // Life Cycle
    // ===========================================================
    public void handleIntent(Intent intent) {

    }

    // ===========================================================
    // Load Data
    // ===========================================================
    public void onGeofencePrepareInsert(CircleGeofence fence) {
        Log.d(TAG, "onGeofencePrepareInsert");
        // Do Edit
        this.editGeofence = fence;
        displayGeofence(fence);
    }


    public void onGeofenceSelect(Uri id, CircleGeofence fence) {
        this.editGeofence = fence;
        displayGeofence(fence);
    }

}