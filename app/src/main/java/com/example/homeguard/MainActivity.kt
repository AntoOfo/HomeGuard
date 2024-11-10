package com.example.homeguard

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.getValue


class MainActivity : AppCompatActivity() {

    private lateinit var mainStatus: TextView
    private lateinit var fireStatus: TextView
    private lateinit var gasStatus: TextView
    private lateinit var floodStatus: TextView
    private lateinit var tempStatus: TextView

    // firebase db references
    private lateinit var tempRef: DatabaseReference
    private lateinit var humidityRef: DatabaseReference

    private var humidity: Double = 0.0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

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

        // read temp from firebase
        tempRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // get value for temp
                val temperature = snapshot.child("value").getValue(Double::class.java) ?: 0.0


                updateTempStatus(temperature)
                tempTile.setOnClickListener {
                    showTempDetailsDialog(temperature, humidity)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                TODO("Not yet implemented")
            }
        })

        // read humidity from firebase
        humidityRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                humidity = snapshot.child("value").getValue(Double::class.java) ?: 0.0
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle errors if needed
            }
        })

        fireTile.setOnClickListener {

        }

        gasTile.setOnClickListener {

        }

        floodTile.setOnClickListener {

            // placeholders
            val levels = "10%"
            val message = "Monitor and stuff"
            showFloodDetailsDialog(levels, message)
        }


        callBtn.setOnClickListener {
            val intent = Intent(this, EmergencyServicesActivity::class.java)
            startActivity(intent)
        }

        }

    private fun updateTempStatus(temperature: Double) {
        // update temp status from values
        when {
            temperature < 10 -> {
                tempStatus.text = "Low"
            }
            temperature in 10.0..30.0 -> {
                tempStatus.text = "Normal"
            }
            temperature > 30 -> {
                tempStatus.text = "High"
            }
        }
    }

    private fun showTempDetailsDialog(temp: Double, humidity: Double) {
        // builds/shows detailed AlertDialog
        val builder = AlertDialog.Builder(this)

        // inflate custom layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_temperature_details, null)

        val title = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val message = dialogView.findViewById<TextView>(R.id.dialogMessage)
        val closeBtn = dialogView.findViewById<TextView>(R.id.closeBtn)

        // calculates status
        val status = when {
            temp < 10 -> "Low Temperature"
            temp in 10.0..30.0 -> "Normal"
            else -> "High Temperature"
        }
        // texts to be changed
        title.text = "Temperature Details"
        message.text = "Current temperature: $temp°C\n" +
                       "Humidity level: $humidity%\n" +
                       "Status: $status"

        builder.setView(dialogView)

        // create dialog
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        closeBtn.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showFloodDetailsDialog(levels: String, message: String) {

        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_flood_details, null)

        val title = dialogView.findViewById<TextView>(R.id.dialogFloodTitle)
        val message = dialogView.findViewById<TextView>(R.id.dialogFloodMessage)
        val closeBtn = dialogView.findViewById<Button>(R.id.closeBtn)

        title.text = "Water Level Details"
        message.text = "Level: $levels\n" +
                       "Monitor and stuff"

        builder.setView(dialogView)

        // create dialog
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        closeBtn.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
    }