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
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import java.util.concurrent.atomic.AtomicBoolean

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var phoneNumber: String
    private val handler = Handler(Looper.getMainLooper())
    private val isSending = AtomicBoolean(false) // SMS gönderimi kontrolü için atomik flag
    private var locationRunnable: Runnable? = null
    private var locationCallback: LocationCallback? = null // Konum callback'i

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

        // Başlangıçta hemen çalıştır
        startLocationTask()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        phoneNumber = intent?.getStringExtra("PHONE_NUMBER") ?: ""
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

    // Konum alma ve SMS gönderme işlemi
    private fun startLocationTask() {
        // Zamanlayıcı, her 5 dakikada bir çalışacak şekilde ayarlandı
        locationRunnable = object : Runnable {
            override fun run() {
                // SMS gönderimi yapılmıyorsa, yeni bir SMS gönder
                if (!isSending.get()) {
                    getLocationAndSendSMS()
                }
                // SMS gönderimi bitene kadar yeni SMS gönderimi başlatılmasın
                handler.postDelayed(this, 5 * 60 * 1000) // Her 5 dakikada bir tekrar et
            }
        }

        // İlk başta hemen başlatıyoruz
        locationRunnable?.let {
            handler.post(it)
        }
    }

    private fun getLocationAndSendSMS() {
        // Konum izni kontrolü
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("LocationService", "Konum izni yok!")
            return
        }

        // Konum verisini al
        if (isSending.compareAndSet(false, true)) {  // Eğer daha önce SMS gönderilmiyorsa
            // Eğer daha önce locationCallback başlatılmadıysa, başlatıyoruz
            if (locationCallback == null) {
                locationCallback = object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        locationResult?.let {
                            val location = it.lastLocation
                            location?.let { loc ->
                                val latitude = loc.latitude
                                val longitude = loc.longitude
                                sendSMS(phoneNumber, "Konum: https://www.google.com/maps?q=$latitude,$longitude")
                                // Konum güncellemelerini durdur
                                fusedLocationClient.removeLocationUpdates(locationCallback!!)
                                locationCallback = null // Callback'i sıfırla
                            }
                        }
                        // SMS gönderimi tamamlandığında flag'i tekrar false yap
                        isSending.set(false)
                    }
                }
                // Konum güncellemelerini başlatıyoruz
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
            }
        }
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
        // Zamanlayıcıyı iptal et
        locationRunnable?.let {
            handler.removeCallbacks(it)
        }
        // Konum güncellemelerini durdur
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
    }
}
