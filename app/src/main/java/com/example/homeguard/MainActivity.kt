package com.example.homeguard

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.Nullable
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
            showTempDetailsDialog()
        }

        }

    private fun showTempDetailsDialog() {
        // builds/shows detailed AlertDialog
    }
    }