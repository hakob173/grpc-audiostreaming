package com.language.wiz.myapplication

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.RectangularBounds
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder


class MapActivity : FragmentActivity(), OnMapReadyCallback {
    private val MY_PERMISSIONS_REQUEST_LOCATION = 99

    private var mMap: GoogleMap? = null

    lateinit var channel: ManagedChannel

    override fun onCreate(savedInstanceState: Bundle?) {


        val token: AutocompleteSessionToken = AutocompleteSessionToken.newInstance()
// Create a RectangularBounds object.

        // Create a RectangularBounds object.
        val bounds = RectangularBounds.newInstance(
            LatLng(-33.880490, 151.184363),
            LatLng(-33.858754, 151.229596)
        )
        // Use the builder to create a FindAutocompletePredictionsRequest.
        // Use the builder to create a FindAutocompletePredictionsRequest.

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment!!.getMapAsync(this)
        checkLocationPermission()

        channel = ManagedChannelBuilder
            .forAddress("192.168.5.14", 9090)
            .usePlaintext()
            .build()
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        mMap = googleMap
        mMap!!.uiSettings.isMyLocationButtonEnabled = true;
        mMap!!.isMyLocationEnabled = true;
        mMap!!.setMinZoomPreference(16.0f);
        mMap!!.setMaxZoomPreference(18.0f);
        if (checkLocationPermission()) {
            val locationManager =
                getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val criteria = Criteria()
            val location =  getLastKnownLocation()
            val sydney = location?.latitude?.let { LatLng(it, location.longitude) }?.let {

                mMap!!.addMarker(MarkerOptions().position(it).title("my position"))
                mMap!!.moveCamera(CameraUpdateFactory.newLatLng(it))

            }
        }
    }
    private fun getLastKnownLocation(): Location? {
        val mLocationManager =
            applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers: List<String> = mLocationManager.getProviders(true)
        var bestLocation: Location? = null
        if (checkLocationPermission()) {
            for (provider in providers) {
                val l: Location = mLocationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                    // Found best last known location: %s", l);
                    bestLocation = l

                    val position = PositionRequest
                        .newBuilder()
                        .setLat(l.latitude)
                        .setLong(l.longitude)
                        .build()
                    val sendPosition = CommunicationServiceGrpc
                        .newBlockingStub(channel)
                        .sendPosition(position)

                }
            }
        }
        return bestLocation
    }
    private fun checkLocationPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
        ) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                AlertDialog.Builder(this)
                    .setTitle("We need your location")
                    .setMessage("Just give it to us")
                    .setPositiveButton(
                        "OK"
                    ) { dialogInterface, i -> //Prompt the user once explanation has been shown
                        ActivityCompat.requestPermissions(
                            this@MapActivity,
                            arrayOf<String>(Manifest.permission.ACCESS_FINE_LOCATION),
                            MY_PERMISSIONS_REQUEST_LOCATION
                        )
                    }
                    .create()
                    .show()
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(
                    this, arrayOf<String>(Manifest.permission.ACCESS_FINE_LOCATION),
                    MY_PERMISSIONS_REQUEST_LOCATION
                )
            }
            false
        } else {
            true
        }
    }
}
