package com.mehmetesen.smssender

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*

class MainActivity : AppCompatActivity() {

    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private val LOCATION_SETTINGS_REQUEST_CODE = 2
    private val SMS_PERMISSION_REQUEST_CODE = 3
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationSettingsRequest: LocationSettingsRequest
    private lateinit var settingsClient: SettingsClient
    private lateinit var locationManager: LocationManager
    private var lastKnownLocation: Location? = null
    // Değişkenleri tanımla
    private lateinit var spinnerCountryCode: Spinner
    private lateinit var etPhoneNumber: EditText
    private lateinit var btnSendSMS: Button
    private lateinit var fullPhoneNumber:String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // findViewById ile XML'deki bileşenleri bağla
        spinnerCountryCode = findViewById(R.id.spinnerCountryCode)
        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        btnSendSMS = findViewById(R.id.btnSendSMS)
        // strings.xml içindeki ülke kodlarını al
        val countryCodes: Array<String> = resources.getStringArray(R.array.country_codes)

        // Spinner için adapter oluştur ve bağla
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, countryCodes)
        spinnerCountryCode.adapter = adapter



        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        settingsClient = LocationServices.getSettingsClient(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationSettingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .build()
        btnSendSMS.setOnClickListener {
            val selectedCode = spinnerCountryCode.selectedItem.toString()
            val phoneNumber = etPhoneNumber.text.toString()
            fullPhoneNumber="$selectedCode$phoneNumber"
            try {
                if(fullPhoneNumber.isEmpty() || phoneNumber.isEmpty()){
                    Toast.makeText(this, "Lütfen telefon numarası giriniz!", Toast.LENGTH_SHORT).show()
                }else{
                    val intent = Intent(this, LocationService::class.java)
                    intent.putExtra("PHONE_NUMBER", fullPhoneNumber)
                    startForegroundService(intent)
                    checkPermissions()
                }


            }catch (e:Exception){
                e.printStackTrace()
            }


        }


    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            checkLocationSettings()
        }
    }

    private fun checkLocationSettings() {
        settingsClient.checkLocationSettings(locationSettingsRequest)
            .addOnSuccessListener {
                getLocation()
            }
            .addOnFailureListener { e ->
                if (e is ResolvableApiException) {
                    try {
                        e.startResolutionForResult(this, LOCATION_SETTINGS_REQUEST_CODE)
                    } catch (sendEx: IntentSender.SendIntentException) {
                        Toast.makeText(this, "Konum doğruluğu etkinleştirilemedi", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun getLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location: Location? ->
                        location?.let {
                            lastKnownLocation = it
                            val latitude = it.latitude
                            val longitude = it.longitude

                            Log.d("LocationInfo", "Latitude: $latitude, Longitude: $longitude")
                            Toast.makeText(this@MainActivity, "Konum: Latitude: $latitude, Longitude: $longitude", Toast.LENGTH_LONG).show()

                            requestSMSPermission()
                        } ?: run {
                            Toast.makeText(this@MainActivity, "Konum bilgisi alınamadı, tekrar deneniyor...", Toast.LENGTH_SHORT).show()
                            retryGetLocation()
                        }
                    }
            } else {
                Toast.makeText(this, "Konum servisi etkin değil. Lütfen GPS'i açın.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun retryGetLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    lastKnownLocation = it
                    val latitude = it.latitude
                    val longitude = it.longitude

                    Log.d("LocationInfo", "Latitude: $latitude, Longitude: $longitude")
                    Toast.makeText(this@MainActivity, "Konum: Latitude: $latitude, Longitude: $longitude", Toast.LENGTH_LONG).show()

                    requestSMSPermission()
                } ?: run {
                    Toast.makeText(this@MainActivity, "Konum bilgisi hala alınamıyor, lütfen GPS'in tam açıldığını kontrol edin.", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun requestSMSPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            lastKnownLocation?.let { sendSMS(it.latitude, it.longitude) }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), SMS_PERMISSION_REQUEST_CODE)
        }
    }

    private fun sendSMS(latitude: Double, longitude: Double) {
        try {
            val phoneNumber = fullPhoneNumber
            val message = "Konum: https://www.google.com/maps?q=$latitude,$longitude"
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Toast.makeText(this, "SMS gönderildi", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "SMS gönderilirken hata oluştu", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkLocationSettings()
                } else {
                    Toast.makeText(this, "Konum izni verilmedi", Toast.LENGTH_SHORT).show()
                }
            }
            SMS_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    lastKnownLocation?.let { sendSMS(it.latitude, it.longitude) }
                } else {
                    Toast.makeText(this, "SMS izni verilmedi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LOCATION_SETTINGS_REQUEST_CODE) {
            getLocation()
        }
    }
}
