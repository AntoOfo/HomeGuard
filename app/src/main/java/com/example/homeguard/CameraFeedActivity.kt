package com.example.homeguard

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class CameraFeedActivity : AppCompatActivity() {

    // ui components and firebase reference
    private lateinit var camWebView: WebView
    private lateinit var fireStatus: TextView
    private lateinit var fireRef: DatabaseReference
    private lateinit var fireAdviceText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_camera_feed)

        fireStatus = findViewById<TextView>(R.id.firefeedStatus)
        camWebView = findViewById(R.id.camWebView)
        fireAdviceText = findViewById(R.id.fireAdviceText)
        val backBtn = findViewById<Button>(R.id.backBtnFire)

        // initialising firebase db reference
        fireRef = FirebaseDatabase.getInstance().getReference("sensors/fire_detection")

        // loading video feed url
        val videoUrl = "http://192.168.1.178:5000"
        camWebView.loadUrl(videoUrl)

        // firebase listener
        fireRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // get fire status data from db
                val fireStatusData = snapshot.child("status").getValue(String::class.java)

                when (fireStatusData) {
                    "fire detected" -> {
                        fireStatus.text = "Status: Possible"
                        fireAdviceText.text = "Please investigate immediately!"
                    }
                    else -> {
                        fireStatus.text = "Status: Clear"
                        fireAdviceText.text = "All clear, no fire detected."
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })

        backBtn.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }
}
