package com.example.homeguard

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.Nullable
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var mainStatus: TextView
    private lateinit var fireStatus: TextView
    private lateinit var gasStatus: TextView
    private lateinit var floodStatus: TextView
    private lateinit var tempStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val fireTile = findViewById<CardView>(R.id.fireTile)
        val gasTile = findViewById<CardView>(R.id.gasTile)
        val floodTile = findViewById<CardView>(R.id.floodTile)
        val tempTile = findViewById<CardView>(R.id.temperatureTile)

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

        fireTile.setOnClickListener {

        }

        gasTile.setOnClickListener {

        }

        floodTile.setOnClickListener {

        }

        tempTile.setOnClickListener {

            val temp = "17°C"
            val humidity = "45%"
            val status = "Normal"
            showTempDetailsDialog(temp, humidity, status)
        }

        }

    private fun showTempDetailsDialog(temp: String, humidity: String, status: String) {
        // builds/shows detailed AlertDialog
        val builder = AlertDialog.Builder(this)

        // inflate custom layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_temperature_details, null)

        val title = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val message = dialogView.findViewById<TextView>(R.id.dialogMessage)
        val closeBtn = dialogView.findViewById<TextView>(R.id.closeBtn)

        // texts to be changed
        title.text = "Temperature Details"
        message.text = "Current temperature: $temp\n" +
                       "Humidity level: $humidity\n" +
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
    }