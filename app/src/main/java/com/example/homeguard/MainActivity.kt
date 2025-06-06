package com.example.homeguard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.database.Cursor
import android.location.LocationListener
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.TranslateAnimation
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener


class MainActivity : AppCompatActivity() {

    // companion objects to store noti channels & ids
    companion object {
        private const val FIRE_CHANNEL_ID = "fire_alert_channel"
        private const val FIRE_NOTIFICATION_ID = 1

        private const val TEMP_CHANNEL_ID = "temperature_alert_channel"
        private const val TEMP_NOTIFICATION_ID = 2

        private const val GAS_CHANNEL_ID = "gas_alert_channel"
        private const val GAS_NOTIFICATION_ID = 3

        private const val FLOOD_CHANNEL_ID = "flood_alert_channel"
        private const val FLOOD_NOTIFICATION_ID = 4
    }

    // permission request codes
    private val SMS_PERMISSION_REQUEST = 101
    private val LOCATION_PERMISSION_REQUEST_CODE = 102
    private val CONTACT_PICKER_REQUEST = 1

    // location properties
    private lateinit var locationManager: LocationManager
    private var lastKnownLocation: Location? = null
    private var locationListener: LocationListener? = null

    // ui elements (statuses)
    private lateinit var mainStatus: TextView
    private lateinit var fireStatus: TextView
    private lateinit var gasStatus: TextView
    private lateinit var floodStatus: TextView
    private lateinit var tempStatus: TextView

    // firebase db references
    private lateinit var tempRef: DatabaseReference
    private lateinit var humidityRef: DatabaseReference
    private lateinit var waterLevelRef: DatabaseReference
    private lateinit var gasRef: DatabaseReference
    private lateinit var fireRef: DatabaseReference
    private lateinit var triggerRef: DatabaseReference
    private lateinit var buzzerRef: DatabaseReference

    // sensor values
    private var humidity: Double = 0.0
    private var temp: Double = 0.0
    private var waterLevel: Double = 0.0
    private var gasLevel: Double = 0.0

    // dialogs for additional details
    private var tempDialog: AlertDialog? = null
    private lateinit var dialogMessage: TextView
    private var gasDialog: AlertDialog? = null
    private lateinit var gasMessage: TextView

    private lateinit var blurOverlay: View

    private val lastNotificationTimes = HashMap<String, Long>()

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        if (!isNetworkAvailable(this)) {
            Toast.makeText(this, "No internet", Toast.LENGTH_LONG).show()
        }


        // creates notis channel
        createNotificationChannel()

        // initialise location manager to track device lcoation
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        setupLocationUpdates()

        // ui elements
        val fireTile = findViewById<CardView>(R.id.fireTile)
        val gasTile = findViewById<CardView>(R.id.gasTile)
        val floodTile = findViewById<CardView>(R.id.floodTile)
        val tempTile = findViewById<CardView>(R.id.temperatureTile)
        val callBtn = findViewById<ImageView>(R.id.phoneBtn)
        val sendBtn = findViewById<Button>(R.id.sendBtn)
        val infoBtn = findViewById<ImageView>(R.id.infoBtn)


        mainStatus = findViewById(R.id.statusText)
        mainStatus.text = "All Systems Normal"  // default main status
        fireStatus = findViewById(R.id.fireStatus)
        fireStatus.text = "Safe"  // default fire status
        gasStatus = findViewById(R.id.gasStatus)
        gasStatus.text = "Safe"
        floodStatus = findViewById(R.id.floodStatus)
        floodStatus.text = "Safe"
        tempStatus = findViewById(R.id.temperatureStatus)
        tempStatus.text = "Safe"

        blurOverlay = findViewById(R.id.blurOverlay)
        blurOverlay.visibility = View.GONE

        // check if certain dialogs to be shown based on intent extras
        val openTempDialog = intent.getBooleanExtra("openTempDialog", false)
        if (openTempDialog) {
            showTempDetailsDialog()
        }
        val openGasDialog = intent.getBooleanExtra("openGasDialog", false)
        if (openGasDialog) {
            showGasDetailsDialog()
        }
        val openFloodDialog = intent.getBooleanExtra("openFloodDialog", false)
        if (openFloodDialog) {
            showFloodDetailsDialog()
        }

        // firebase db references
        tempRef = FirebaseDatabase.getInstance().getReference("sensors/temperature")
        humidityRef = FirebaseDatabase.getInstance().getReference("sensors/humidity")
        waterLevelRef = FirebaseDatabase.getInstance().getReference("sensors/water_level")
        gasRef = FirebaseDatabase.getInstance().getReference("sensors/gas")
        fireRef = FirebaseDatabase.getInstance().getReference("sensors/fire_detection")
        triggerRef = FirebaseDatabase.getInstance().getReference("trigger")
        buzzerRef = FirebaseDatabase.getInstance().getReference("buzzer_trigger")

        // listen for fire updates on firebase
        fireRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val fireStatusData = snapshot.child("status").getValue(String::class.java)

                updateFireStatus(fireStatusData)

                if (fireStatusData == "fire detected") {
                    sendFireNotification()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to read fire data", error.toException())
            }
        })
        // listen for temp updates on firebase
        tempRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // get value for temp
                temp = snapshot.child("value").getValue(Double::class.java) ?: 0.0

                updateTempStatus(temp)
                updateDialog()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to read temp data", error.toException())
            }
        })

        // listen for humidity updates on firebase
        humidityRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                humidity = snapshot.child("value").getValue(Double::class.java) ?: 0.0
                updateDialog()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to read humidity data", error.toException())
            }
        })

        // listen for flood updates on firebase
        waterLevelRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                waterLevel = snapshot.child("value").getValue(Double::class.java) ?: 0.0
                updateFloodStatus(waterLevel)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to read water data", error.toException())
            }
        })

        // listen for gas updates on firebase
        gasRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                gasLevel = snapshot.child("value").getValue(Double::class.java) ?: 0.0
                updateGasStatus(gasLevel)
                updateGasDialog()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to read gas data", error.toException())
            }
        })

        // on click listeners for tiles and buttons
        tempTile.setOnClickListener {
            tempTile.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .alpha(0.5f)
                .setDuration(150)
                .withEndAction {
                    tempTile.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).start()
                    showTempDetailsDialog()
                }
                .start()
        }

        fireTile.setOnClickListener {
            fireTile.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .alpha(0.5f)
                .setDuration(150)
                .withEndAction {
                    fireTile.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).start()
                    val intent = Intent(this, CameraFeedActivity::class.java)
                    startActivity(intent)
                }
                .start()
        }

        gasTile.setOnClickListener {
            gasTile.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .alpha(0.5f)
                .setDuration(150)
                .withEndAction {
                    gasTile.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).start()
                    showGasDetailsDialog()
                }
                .start()
        }

        floodTile.setOnClickListener {
            floodTile.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .alpha(0.5f)
                .setDuration(150)
                .withEndAction {
                    floodTile.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).start()
                    showFloodDetailsDialog()
                }
                .start()
        }

        callBtn.setOnClickListener {
            callBtn.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .alpha(0.5f)
                .setDuration(150)
                .withEndAction {
                    callBtn.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).start()
                    val intent = Intent(this, EmergencyServicesActivity::class.java)
                    startActivity(intent)
                }
        }

        sendBtn.setOnClickListener {
            // check sms/location perms before sending
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.SEND_SMS),
                    SMS_PERMISSION_REQUEST)
            } else {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                    // request location permissions if not granted
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                } else {
                    // if both are given, continue
                    val intent =
                        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                    startActivityForResult(intent, CONTACT_PICKER_REQUEST)
                }
            }
        }

        infoBtn.setOnClickListener {

            infoBtn.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .alpha(0.5f)
                .setDuration(150)
                .withEndAction {
                    infoBtn.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).start()
                    val intent = Intent(this, InfoActivity::class.java)
                    startActivity(intent)
                }
                .start()
        }

    }

    private fun showDim() {
        blurOverlay.visibility = View.VISIBLE
    }

    private fun sendOpenWindowsTrigger() {

        val triggerData = mapOf(
            "status" to "triggered",
            "timestamp" to System.currentTimeMillis())

        triggerRef.setValue(triggerData).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("Firebase", "Trigger sent successfully")

                Handler(Looper.getMainLooper()).postDelayed({
                    val resetData = mapOf(
                        "status" to "reset",
                        "timestamp" to System.currentTimeMillis()
                    )
                    triggerRef.setValue(resetData)
                }, 2000)

            } else {
                Log.e("Firebase", "Failed to send trigger", task.exception)
            }
            }
        }

    private fun sendBuzzerTrigger() {

        val triggerData = mapOf(
            "status" to "triggered",
            "timestamp" to System.currentTimeMillis()
        )

        buzzerRef.setValue(triggerData).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("Firebase", "Buzzer trigger sent successfully")

                Handler(Looper.getMainLooper()).postDelayed({
                    val resetData = mapOf(
                        "status" to "reset",
                        "timestamp" to System.currentTimeMillis()
                    )
                    buzzerRef.setValue(resetData)
                }, 4000)
            } else {
                Log.e("Firebase", "Failed to send buzzer trigger...", task.exception)
            }
        }
    }


    // set up location updates with location manager
    private fun setupLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    lastKnownLocation = location  // update last known location
                }
                override fun onProviderDisabled(provider: String) {}
                override fun onProviderEnabled(provider: String) {}
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000, // update every 5 seconds
                10f,  // or when device moves 10m
                locationListener!!
            )
        }
    }

        // deals with result of the contact picker
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if (requestCode == CONTACT_PICKER_REQUEST && resultCode == RESULT_OK) {
                val contactUri: Uri? = data?.data
                val cursor: Cursor? = contentResolver.query(contactUri!!, null, null, null, null)

                if (cursor != null && cursor.moveToFirst()) {
                    val phoneNumberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val phoneNumber = cursor.getString(phoneNumberIndex)

                    sendStatusAndLocation(phoneNumber)  // send status and location to contact
                }
                cursor?.close()
            }
        }

    // send current status/location to number
    private fun sendStatusAndLocation(phoneNumber: String) {
        val statusMessage = "HomeGuard Status:\n" +
                "Fire: ${fireStatus.text}\n" +
                "Gas: ${gasStatus.text}\n" +
                "Flood: ${floodStatus.text}\n" +
                "Temperature: ${tempStatus.text}"

        // check location permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            // make sure gps is enabled
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(this, "Please enable GPS", Toast.LENGTH_SHORT).show()
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                return
            }

            // get last known gps location
            lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

            // if location is available, include it in message
            lastKnownLocation?.let {
                val locationMessage = "Location: https://maps.google.com/?q=${it.latitude},${it.longitude}"
                sendSms(phoneNumber, "$statusMessage\n\n$locationMessage") // Replace with the desired phone number
            } ?: run {
                Toast.makeText(this, "Unable to get location.", Toast.LENGTH_SHORT).show()
                sendSms(phoneNumber, statusMessage) // send status without location
            }
        } else {
            Toast.makeText(this, "Location permission not granted.", Toast.LENGTH_SHORT).show()
            sendSms(phoneNumber, statusMessage) // send status without location
        }
    }

    // send sms to number
    private fun sendSms(phoneNumber: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            // send sms
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Toast.makeText(this, "Status sent successfully.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send status", Toast.LENGTH_SHORT).show()
        }
    }

    // handle result of perms request
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == SMS_PERMISSION_REQUEST) {
            // checks if sms perms are granted
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            } else {
                Toast.makeText(this, "SMS permission denied.", Toast.LENGTH_SHORT).show()
            }
        }

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            // check if location perms are granted
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendStatusAndLocation("phoneNumber")
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // update main status based off different detections
    private fun updateMainStatus() {
        val dangers = mutableListOf<String>()

        if (fireStatus.text == "Warning") {
            dangers.add("Fire")  // add to list
        }
        if (gasStatus.text == "Warning") {
            dangers.add("Gas")
        }
        if (tempStatus.text == "Warning") {
            dangers.add("Temperature")
        }
        if (floodStatus.text == "Warning") {
            dangers.add("Flood")
        }

        mainStatus.text = when {
            dangers.isEmpty() -> "All Systems Normal"  // no dangers detected
            dangers.size == 1 -> "${dangers[0]} Detected"  // single danger detected
            else -> "Multiple Dangers Detected: ${dangers.joinToString(", ")}"  // multiple dangers detected
        }
    }

    // notis channel for different alert types
    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val fireName = "Fire Alerts"
            val fireDescription = "Notifications for fire events"
            val fireImportance = NotificationManager.IMPORTANCE_HIGH
            val fireChannel = NotificationChannel(FIRE_CHANNEL_ID, fireName, fireImportance).apply {
                description = fireDescription
            }
            val tempName = "Temperature Alerts"
            val tempDescription = "Notifications for temperature changes"
            val tempImportance = NotificationManager.IMPORTANCE_HIGH
            val tempChannel = NotificationChannel(TEMP_CHANNEL_ID, tempName, tempImportance).apply {
                description = tempDescription
            }
            val gasName = "Gas Alerts"
            val gasDescription = "Notifications for gas level changes"
            val gasImportance = NotificationManager.IMPORTANCE_HIGH
            val gasChannel = NotificationChannel(GAS_CHANNEL_ID, gasName, gasImportance).apply {
                description = gasDescription
            }
            val floodName = "Flood Alerts"
            val floodDescription = "Notifications for flood warnings"
            val floodImportance = NotificationManager.IMPORTANCE_HIGH
            val floodChannel = NotificationChannel(FLOOD_CHANNEL_ID, floodName, floodImportance).apply {
                description = floodDescription
            }

            // register channels with system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(fireChannel)
            notificationManager.createNotificationChannel(tempChannel)
            notificationManager.createNotificationChannel(gasChannel)
            notificationManager.createNotificationChannel(floodChannel)
        }
    }

    // send fire alert notification
    fun sendFireNotification() {
        val currentTime = System.currentTimeMillis()
        val delay = 15000L

        val lastTime = lastNotificationTimes["FIRE"] ?: 0

        if (currentTime - lastTime < delay) {
            return
        }

        lastNotificationTimes["FIRE"] = currentTime

        // intent to open camera feed
        val intent = Intent(this, CameraFeedActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // properties of notification
        val notificationBuilder = NotificationCompat.Builder(this, FIRE_CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_fire)
            .setContentTitle("Fire Alert!")
            .setContentText("Possible fire detected in your home. Tap to view live feed.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // send notification
        notificationManager.notify(FIRE_NOTIFICATION_ID, notificationBuilder.build())

        sendBuzzerTrigger()
    }

    // sends temp alert notification
    fun sendTemperatureNotification(message: String) {
        val currentTime = System.currentTimeMillis()
        val delay = 15000L

        val lastTime = lastNotificationTimes["TEMP"] ?:0

        if (currentTime - lastTime < delay) {
            return
        }
        lastNotificationTimes["TEMP"] = currentTime

        // intent to open temp dialog
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("openTempDialog", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // properties of noti
        val notificationBuilder = NotificationCompat.Builder(this, TEMP_CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_temperature)
            .setContentTitle("Temperature Alert!")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // send noti
        notificationManager.notify(TEMP_NOTIFICATION_ID, notificationBuilder.build())

        sendBuzzerTrigger()
    }

    // sends gas alert notification
    fun sendGasNotification(message: String) {
        val currentTime = System.currentTimeMillis()
        val delay = 15000L

        val lastTime = lastNotificationTimes["GAS"] ?:0

        if (currentTime - lastTime < delay) {
            return
        }
        lastNotificationTimes["GAS"] = currentTime
        // intent to open gas dialog
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("openGasDialog", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // properties of gas noti
        val notificationBuilder = NotificationCompat.Builder(this, "gas_alert_channel")
            .setSmallIcon(R.drawable.icon_gas)
            .setContentTitle("Gas Alert!")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // send noti
        notificationManager.notify(GAS_NOTIFICATION_ID, notificationBuilder.build())

        //sendBuzzerTrigger()
    }

    // sends flood alert noti
    fun sendFloodNotification(level: Double) {
        val currentTime = System.currentTimeMillis()
        val delay = 15000L

        val lastTime = lastNotificationTimes["FLOOD"] ?:0

        if (currentTime - lastTime < delay) {
            return
        }
        lastNotificationTimes["FLOOD"] = currentTime
        // intent to open flood dialog
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("openFloodDialog", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // properties of noti
        val notificationBuilder = NotificationCompat.Builder(this, FLOOD_CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_flood)
            .setContentTitle("Flood Alert!")
            .setContentText("Water level is $level%. Tap to view details.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // send noti
        notificationManager.notify(FLOOD_NOTIFICATION_ID, notificationBuilder.build())

        sendBuzzerTrigger()
    }

    // updates temp status and triggers notis if needed
    private fun updateTempStatus(temperature: Double) {
        tempStatus.text = when {
            temperature < 10  -> "Warning"
            temperature in 10.0..28.0 -> "Safe"
            else -> "Warning"
        }

        tempStatus.setTextColor(
            when {
                temperature < 10 || temperature > 28 -> Color.RED
                else -> Color.parseColor("#4CAF50")
            }
        )

        when {
            temperature <= 10 -> {
                sendTemperatureNotification("Temperature is too low! $temperature°C. Tap to view more.")
            }
            temperature >= 28 -> {
                sendTemperatureNotification("Temperature is too high! $temperature°C. Tap to view more.")
            }
        }
        updateMainStatus()
    }

    // update fire status
    private fun updateFireStatus(fireStatusData: String?) {
        fireStatus.text = when (fireStatusData) {
            "fire detected" -> "Warning"
            else -> "Safe"
        }

        fireStatus.setTextColor(
            when (fireStatusData) {
                "fire detected" -> Color.RED
                else -> Color.parseColor("#4CAF50")
            }
        )
        updateMainStatus()
    }

    // update flood status and send notis if necessary
    private fun updateFloodStatus(level: Double) {
        floodStatus.text = when {
            level < 5.0 -> "Safe"
            level in 5.0..75.0 -> "Warning"
            else -> "Warning"
        }

        floodStatus.setTextColor(
            when {
                level < 5.0 -> Color.parseColor("#4CAF50")
                else -> Color.RED
            }
        )

        if (level > 5.0) {
            sendFloodNotification(level)
        }
        updateMainStatus()
    }

    // update gas status and sends notis if needed
    private fun updateGasStatus(level: Double) {
        gasStatus.text = when {
            level < 0.30 -> "Safe"
            level in 0.30..1.0 -> "Warning"
            else -> "Warning"
        }

        gasStatus.setTextColor(
            when {
                level < 0.30 -> Color.parseColor("#4CAF50")
                else -> Color.RED
            }
        )

        if (level >= 0.30) {
            sendGasNotification("Gas levels are high! $level%. Immediate action required!")
        }
        updateMainStatus()
    }

    // displays dialog with temp details
    private fun showTempDetailsDialog() {
        // creates dialog if it hasnt been created
        if (tempDialog == null) {
            val builder = AlertDialog.Builder(this)
            val dialogView = layoutInflater.inflate(R.layout.dialog_temperature_details, null)

            dialogMessage = dialogView.findViewById(R.id.dialogMessage)
            val closeBtn = dialogView.findViewById<TextView>(R.id.closeBtn)

            // builds dialog with properties
            builder.setView(dialogView)
            tempDialog = builder.create()
            tempDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            closeBtn.setOnClickListener {
                tempDialog?.dismiss()
                blurOverlay.visibility = View.GONE
            }
        }

        // show the dialog if it's not already showing
        if (tempDialog?.isShowing == false) {
            tempDialog?.show()

            val fadeIn = ObjectAnimator.ofFloat(tempDialog?.window?.decorView, "alpha", 0f, 1f)
            fadeIn.duration = 200
            fadeIn.start()

            showDim()
        }

        // show latest values in dialog
        updateDialog()
    }

    // update temp details in dialog
    @SuppressLint("SetTextI18n")
    private fun updateDialog() {
        if (::dialogMessage.isInitialized) {
            val status = when {
                temp < 10 -> "Low Temperature"
                temp in 10.0..30.0 -> "Normal"
                else -> "High Temperature"
            }

            // update dialog message
            dialogMessage.text = "Current temperature: $temp°C\n" +
                    "Humidity level: $humidity%\n" +
                    "Status: $status"
        }
    }

    // update gas details in dialog
    @SuppressLint("SetTextI18n")
    private fun updateGasDialog() {
        if (::gasMessage.isInitialized) {
            val gasStatusText = when {
                gasLevel < 0.30 -> "Air Quality Stable - No immediate risk."
                gasLevel in 0.30..1.0 -> "Warning! Elevated gas levels detected - Please monitor."
                else -> "Critical Alert! High gas levels detected - Immediate action needed!"
            }

            // update dialog message
            gasMessage.text = "Gas Level: ${gasLevel}%\nStatus: $gasStatusText"  // Update dialog message
        }
    }

    // update flood details in dialog
    @SuppressLint("SetTextI18n")
    private fun showFloodDetailsDialog() {
        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_flood_details, null)

        val title = dialogView.findViewById<TextView>(R.id.dialogFloodTitle)
        val floodMessage = dialogView.findViewById<TextView>(R.id.dialogFloodMessage)
        val closeBtn = dialogView.findViewById<Button>(R.id.closeBtn)

        title.text = "Water Level Details"

        val waterLevelStatus = when {
            waterLevel < 5.0 -> "Low"
            waterLevel in 5.0..10.0 -> "Moderate - Monitor the levels"
            else -> "High - Risk of flooding!"
        }

        // set flood message
        floodMessage.text = "Level: ${waterLevel}%\nStatus: $waterLevelStatus"

        builder.setView(dialogView)

        // create dialog
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        closeBtn.setOnClickListener {
            dialog.dismiss()
            blurOverlay.visibility = View.GONE
        }

        dialog.show()

        val fadeIn = ObjectAnimator.ofFloat(dialog.window?.decorView, "alpha", 0f, 1f)
        fadeIn.duration = 200
        fadeIn.start()

        showDim()
    }

    // update gas details in dialog
    @SuppressLint("SetTextI18n")
    private fun showGasDetailsDialog() {
        if (gasDialog == null) {
            val builder = AlertDialog.Builder(this)
            val dialogView = layoutInflater.inflate(R.layout.dialog_gas_details, null)

            val title = dialogView.findViewById<TextView>(R.id.dialogGasTitle)
            gasMessage = dialogView.findViewById(R.id.dialogGasMessage)  // Store reference for updates
            val closeBtn = dialogView.findViewById<Button>(R.id.closeGasBtn)
            val openWindowsBtn = dialogView.findViewById<Button>(R.id.openWindowsBtn)

            title.text = "Gas Level Details"

            builder.setView(dialogView)
            gasDialog = builder.create()
            gasDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            closeBtn.setOnClickListener {
                gasDialog?.dismiss()
                blurOverlay.visibility = View.GONE
            }

            openWindowsBtn.setOnClickListener {
                sendOpenWindowsTrigger()
            }
        }

        if (gasDialog?.isShowing == false) {
            gasDialog?.show()

            val fadeIn = ObjectAnimator.ofFloat(gasDialog?.window?.decorView, "alpha", 0f, 1f)
            fadeIn.duration = 200
            fadeIn.start()
            showDim()
        }
        updateGasDialog()

    }
    }
