package com.example.homeguard

import android.os.Bundle
import android.webkit.WebView
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

        fireRef = FirebaseDatabase.getInstance().getReference("sensors/fire_detection")

        val videoUrl = "http://192.168.1.178:5000/video_feed"
        camWebView.loadUrl(videoUrl)

        // firebase listener
        fireRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val fireStatusData = snapshot.child("status").getValue(String::class.java)
                when (fireStatusData) {
                    "fire detected" -> {
                        fireStatus.text = "Possible Fire"
                        // Keep fire advice text as it is
                    }
                    else -> {
                        fireStatus.text = "No Fire Detected"
                        fireAdviceText.text = "All clear, no fire detected."
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })
        }

    }
