package com.mehmetesen.smssender

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest  // locationRequest tanımlandı
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var phoneNumber: String


    override fun onCreate() {

        super.onCreate()
        startForeground(1, createNotification())

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Konum isteği için yapılandırma
        locationRequest = LocationRequest.create().apply {
            interval = 5 * 60 * 1000 // 5 dakika (300.000 ms)
            fastestInterval = 5 * 60 * 1000 // 5 dakika
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        // Her 5 dakikada bir konumu al ve SMS gönder
        handler.postDelayed(object : Runnable {
            override fun run() {
                getLocationAndSendSMS()
                handler.postDelayed(this, 5 * 60 * 1000) // 5 dakika (300.000 ms)
            }
        }, 0)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        phoneNumber = intent?.getStringExtra("PHONE_NUMBER")!!
        Log.d("LocationService", "Alınan Telefon Numarası: $phoneNumber")

        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "location_service"
        val channelName = "Konum Servisi"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Konum Servisi Çalışıyor")
            .setContentText("Her 5 dakikada bir konum SMS olarak gönderiliyor.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    private fun getLocationAndSendSMS() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("LocationService", "Konum izni yok!")
            return
        }

        // Güncel konum almak için requestLocationUpdates kullanıyoruz
        fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult?.let {
                    val location = it.lastLocation
                    location?.let { loc ->
                        val latitude = loc.latitude
                        val longitude = loc.longitude
                        sendSMS(phoneNumber, "Konum: https://www.google.com/maps?q=$latitude,$longitude")
                    }
                }
            }
        }, Looper.getMainLooper())
    }

    private fun sendSMS(phoneNumber: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.d("LocationService", "SMS Gönderildi: $message")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("LocationService", "SMS gönderme başarısız!")
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
