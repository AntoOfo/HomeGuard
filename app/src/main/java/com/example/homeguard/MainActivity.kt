package com.example.homeguard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.Nullable
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.getValue


class MainActivity : AppCompatActivity() {

    companion object {
        private const val FIRE_CHANNEL_ID = "fire_alert_channel"
        private const val FIRE_NOTIFICATION_ID = 1
    }

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

    private var humidity: Double = 0.0
    private var temp: Double = 0.0
    private var waterLevel: Double = 0.0
    private var gasLevel: Double = 0.0

    private var tempDialog: AlertDialog? = null
    private lateinit var dialogMessage: TextView
    private var gasDialog: AlertDialog? = null
    private lateinit var gasMessage: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // creates notis channel
        createNotificationChannel()

        val fireTile = findViewById<CardView>(R.id.fireTile)
        val gasTile = findViewById<CardView>(R.id.gasTile)
        val floodTile = findViewById<CardView>(R.id.floodTile)
        val tempTile = findViewById<CardView>(R.id.temperatureTile)
        val callBtn = findViewById<ImageView>(R.id.phoneBtn)

        mainStatus = findViewById(R.id.statusText)
        mainStatus.text = "Status: All Systems Normal"
        fireStatus = findViewById(R.id.fireStatus)
        fireStatus.text = "No"
        gasStatus = findViewById(R.id.gasStatus)
        gasStatus.text = "Safe"
        floodStatus = findViewById(R.id.floodStatus)
        floodStatus.text = "Low"
        tempStatus = findViewById(R.id.temperatureStatus)
        tempStatus.text = "Normal"
        
        // firebase db references
        tempRef = FirebaseDatabase.getInstance().getReference("sensors/temperature")
        humidityRef = FirebaseDatabase.getInstance().getReference("sensors/humidity")
        waterLevelRef = FirebaseDatabase.getInstance().getReference("sensors/water_level")
        gasRef = FirebaseDatabase.getInstance().getReference("sensors/gas")
        fireRef = FirebaseDatabase.getInstance().getReference("sensors/fire_detection")

        fireRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val fireStatusData = snapshot.child("status").getValue(String::class.java)

                updateFireStatus(fireStatusData)

                if (fireStatusData == "fire detected") {
                    sendFireNotification()
                }
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })
        // read temp from firebase
        tempRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // get value for temp
                temp = snapshot.child("value").getValue(Double::class.java) ?: 0.0

                updateTempStatus(temp)
                updateDialog()
                }

            override fun onCancelled(error: DatabaseError) {
                TODO("Not yet implemented")
            }
        })

        // read humidity from firebase
        humidityRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                humidity = snapshot.child("value").getValue(Double::class.java) ?: 0.0
                updateDialog()
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle errors if needed
            }
        })

        // read water level from firebase
        waterLevelRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                waterLevel = snapshot.child("value").getValue(Double::class.java) ?: 0.0
                updateFloodStatus(waterLevel)
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle any database error
            }
        })

        // read gas level from firebase
        gasRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                gasLevel = snapshot.child("value").getValue(Double::class.java) ?: 0.0
                updateGasStatus(gasLevel)
                updateGasDialog()
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        tempTile.setOnClickListener {
            showTempDetailsDialog()
        }

        fireTile.setOnClickListener {
            val intent = Intent(this, CameraFeedActivity::class.java)
            startActivity(intent)
        }

        gasTile.setOnClickListener {
            showGasDetailsDialog()
        }

        floodTile.setOnClickListener {

            showFloodDetailsDialog()
        }


        callBtn.setOnClickListener {
            val intent = Intent(this, EmergencyServicesActivity::class.java)
            startActivity(intent)
        }

        }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Fire Alerts"
            val descriptionText = "Notifications for fire events"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(FIRE_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendFireNotification() {
        val notificationBuilder = NotificationCompat.Builder(this, FIRE_CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_fire) // Replace with your app's fire icon
            .setContentTitle("Fire Alert!")
            .setContentText("Possible fire detected in your home. Tap to view live feed.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // send notification
        notificationManager.notify(FIRE_NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun updateTempStatus(temperature: Double) {
        // update temp status from values
        tempStatus.text = when {
            temperature < 10 -> "Low"
            temperature in 10.0..30.0 -> "Normal"
            else -> "High"
        }
    }

    private fun updateFireStatus(fireStatusData: String?) {
        fireStatus.text = when (fireStatusData) {
            "fire detected" -> "Yes"
            else -> "No"
        }
    }

    private fun updateFloodStatus(level: Double) {
        floodStatus.text = when {
            level < 25 -> "Low"
            level in 25.0..75.0 -> "Moderate"
            else -> "High"
        }
    }

    private fun updateGasStatus(level: Double) {
        gasStatus.text = when {
            level < 25 -> "Safe"
            level in 25.0..75.0 -> "Warning"
            else -> "Danger"
        }
    }


    private fun showTempDetailsDialog() {

        if (tempDialog == null) {
            // build the dialog only if it hasn't been created
            val builder = AlertDialog.Builder(this)
            val dialogView = layoutInflater.inflate(R.layout.dialog_temperature_details, null)
            dialogMessage = dialogView.findViewById(R.id.dialogMessage)
            val closeBtn = dialogView.findViewById<TextView>(R.id.closeBtn)

            builder.setView(dialogView)
            tempDialog = builder.create()
            tempDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            closeBtn.setOnClickListener {
                tempDialog?.dismiss()
            }
        }

        // show the dialog if it's not already showing
        if (tempDialog?.isShowing == false) {
            tempDialog?.show()
        }

        // show latest values in dialog
        updateDialog()
    }

    private fun updateDialog() {
        // This method updates the dialog with the latest temperature and humidity
        if (::dialogMessage.isInitialized) {
            val status = when {
                temp < 10 -> "Low Temperature"
                temp in 10.0..30.0 -> "Normal"
                else -> "High Temperature"
            }

            dialogMessage.text = "Current temperature: $temp°C\n" +
                    "Humidity level: $humidity%\n" +
                    "Status: $status"
        }
    }

    private fun updateGasDialog() {
        if (::gasMessage.isInitialized) {
            val gasStatusText = when {
                gasLevel < 25 -> "Air Quality Stable - No immediate risk."
                gasLevel in 25.0..75.0 -> "Warning! Elevated gas levels detected - Please monitor."
                else -> "Critical Alert! High gas levels detected - Immediate action needed!"
            }

            gasMessage.text = "Gas Level: ${gasLevel}%\nStatus: $gasStatusText"  // Update dialog message
        }
    }

    private fun showFloodDetailsDialog() {

        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_flood_details, null)

        val title = dialogView.findViewById<TextView>(R.id.dialogFloodTitle)
        val floodMessage = dialogView.findViewById<TextView>(R.id.dialogFloodMessage)
        val closeBtn = dialogView.findViewById<Button>(R.id.closeBtn)

        title.text = "Water Level Details"

        val waterLevelStatus = when {
            waterLevel < 25 -> "Low"
            waterLevel in 25.0..75.0 -> "Moderate - Monitor the levels"
            else -> "High - Risk of flooding!"
        }

        floodMessage.text = "Level: ${waterLevel}%\nStatus: $waterLevelStatus"

        builder.setView(dialogView)

        // create dialog
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        closeBtn.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showGasDetailsDialog() {

        if (gasDialog == null) {  // Only create the dialog if it hasn't been created
            val builder = AlertDialog.Builder(this)
            val dialogView = layoutInflater.inflate(R.layout.dialog_gas_details, null)

            val title = dialogView.findViewById<TextView>(R.id.dialogGasTitle)
            gasMessage = dialogView.findViewById(R.id.dialogGasMessage)  // Store reference for updates
            val closeBtn = dialogView.findViewById<Button>(R.id.closeGasBtn)

            title.text = "Gas Level Details"

            builder.setView(dialogView)
            gasDialog = builder.create()
            gasDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            closeBtn.setOnClickListener {
                gasDialog?.dismiss()
            }
        }

        if (gasDialog?.isShowing == false) {
            gasDialog?.show()
        }

        // Initial call to update with current data
        updateGasDialog()
    }
    }
