package com.peebers.placebook.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PointOfInterest
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.peebers.placebook.R
import com.peebers.placebook.adapter.BookmarkInfoWindowAdapter
import com.peebers.placebook.viewmodel.MapsViewModel

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var placesClient: PlacesClient
    private val mapsViewModel by viewModels<MapsViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupLocationClient()
        setupPlacesClient()
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        setupMapListeners()
        getCurrentLocation()
    }

    private fun setupPlacesClient() {
        Places.initialize(applicationContext, getString(R.string.google_maps_key))
        placesClient = Places.createClient(this)
    }

    private fun setupLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION)
    }

    companion object {
        private const val REQUEST_LOCATION = 1
        private const val TAG = "MapsActivity"
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissions()
        } else {
            mMap.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnCompleteListener {
                val location = it.result
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    val update = CameraUpdateFactory.newLatLngZoom(latLng, 16.0f)
                    mMap.moveCamera(update)
                } else {
                    Log.e(TAG, "No location found.")
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray) {
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                Log.e(TAG, "Location permission denied.")
            }
        }
    }

    private fun displayPoi(pointOfInterest: PointOfInterest) {
        displayPoiGetPlaceStep(pointOfInterest)
    }

    private fun displayPoiGetPlaceStep(pointOfInterest: PointOfInterest) {
        val placeId = pointOfInterest.placeId
        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.PHONE_NUMBER,
            Place.Field.PHOTO_METADATAS,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG)

        val request = FetchPlaceRequest
            .builder(placeId, placeFields)
            .build()

        placesClient.fetchPlace(request).addOnSuccessListener { response ->
            val place = response.place
            displayPoiGetPhotoStep(place)
        }.addOnFailureListener { exception ->
            if (exception is ApiException) {
                val statusCode = exception.statusCode
                Log.e(
                    TAG,
                    "Place not found: " +
                            exception.message + ", " +
                            "statusCode: " + statusCode
                )
            }
        }
    }

    private fun displayPoiGetPhotoStep(place: Place) {
        val photoMetadata = place.photoMetadatas?.get(0)
        if (photoMetadata == null) {
            displayPoiDisplayStep(place, null)
            return
        }
        val photoRequest = FetchPhotoRequest
            .builder(photoMetadata)
            .setMaxWidth(resources.getDimensionPixelSize(R.dimen.default_image_width))
            .setMaxHeight(resources.getDimensionPixelSize(R.dimen.default_image_height))
            .build()

        placesClient.fetchPhoto(photoRequest)
            .addOnSuccessListener { fetchPhotoResponse -> val bitmap = fetchPhotoResponse.bitmap
            displayPoiDisplayStep(place, bitmap)
            }.addOnFailureListener { exception -> if (exception is ApiException) {
                val statusCode = exception.statusCode
                Log.e(TAG, "Place not found: " + exception.message + ", " + "statusCode: " + statusCode)
            }
            }
    }

    private fun displayPoiDisplayStep(place: Place, photo: Bitmap?) {
        val marker = mMap.addMarker(MarkerOptions()
            .position(place.latLng as LatLng)
            .title(place.name)
            .snippet(place.phoneNumber))
        marker?.tag = PlaceInfo(place, photo)
    }

    private fun setupMapListeners() {
        mMap.setInfoWindowAdapter(BookmarkInfoWindowAdapter(this))
        mMap.setOnPoiClickListener {
            displayPoi(it)
        }
        mMap.setOnInfoWindowClickListener {
            handleInfoWindowClick(it)
        }
    }

    private fun handleInfoWindowClick(marker: Marker) {
        val placeInfo = (marker.tag as PlaceInfo)
        if (placeInfo.place != null) {
            mapsViewModel.addBookmarkFromPlace(placeInfo.place, placeInfo.image)
        }
        marker.remove()
    }

    class PlaceInfo(val place: Place? = null, val image: Bitmap? = null)
}