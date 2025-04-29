package com.example.ultimatetictactoe

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ultimatetictactoe.ui.theme.GameActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class WaitingActivity : AppCompatActivity() {

    private lateinit var database: FirebaseDatabase
    private lateinit var roomRef: DatabaseReference
    private lateinit var roomCode: String
    private lateinit var tvRoomCode: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnCancelWaiting: Button
    private var roomListener: ValueEventListener? = null
    private var playerRole = "player1" // Default role is player1 (room creator)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_waiting)

        // Initialize views
        tvRoomCode = findViewById(R.id.tvRoomCode)
        tvStatus = findViewById(R.id.tvStatus)
        btnCancelWaiting = findViewById(R.id.btnCancelWaiting)

        // Get room code and player role from intent
        roomCode = intent.getStringExtra("ROOM_CODE") ?: ""
        playerRole = intent.getStringExtra("PLAYER_ROLE") ?: "player1"
        tvRoomCode.text = roomCode

        // Initialize Firebase
        database = FirebaseDatabase.getInstance()
        roomRef = database.getReference("rooms/$roomCode")

        // Setup button listeners
        setupButtons()

        // Setup Firebase listeners
        setupRoomListeners()
    }

    private fun setupButtons() {
        btnCancelWaiting.setOnClickListener {
            // If creator cancels, delete the room
            if (playerRole == "player1") {
                roomRef.removeValue()
            }
            finish()
        }
    }

    private fun setupRoomListeners() {
        // Listen for room status changes
        roomListener = roomRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    // Room was deleted
                    showRoomDeletedDialog()
                    return
                }

                val status = snapshot.child("status").getValue(String::class.java) ?: "waiting"
                updateStatusUI(status)

                when (status) {
                    "in-game" -> {
                        // Make sure both players are in game before navigating
                        val player1 = snapshot.child("player1").getValue(String::class.java) ?: ""
                        val player2 = snapshot.child("player2").getValue(String::class.java) ?: ""

                        if (player1.isNotEmpty() && player2.isNotEmpty()) {
                            navigateToGame()
                        }
                    }
                    "ended" -> showRoomEndedDialog()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showError("Connection error: ${error.message}")
            }
        })
    }

    private fun updateStatusUI(status: String) {
        when (status) {
            "waiting" -> {
                tvStatus.text = if (playerRole == "player1") {
                    "Waiting for another player to join..."
                } else {
                    "Joining room..."
                }
            }
            "in-game" -> tvStatus.text = "Game starting..."
            "ended" -> tvStatus.text = "Game has ended"
        }
    }

    private fun navigateToGame() {
        // Remove listeners to prevent multiple callbacks
        roomListener?.let { roomRef.removeEventListener(it) }

        // Navigate to game activity
        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra("ROOM_CODE", roomCode)
            putExtra("PLAYER_ROLE", playerRole)
            // Add flags to ensure we don't create multiple instances
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun showRoomEndedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Room Closed")
            .setMessage("This room has been closed")
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun showRoomDeletedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Room Deleted")
            .setMessage("The room was deleted by the host")
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up listener when activity is destroyed
        roomListener?.let { roomRef.removeEventListener(it) }
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("Leave Room")
            .setMessage("Are you sure you want to leave this room?")
            .setPositiveButton("Yes") { _, _ ->
                if (playerRole == "player1") {
                    // Room creator is leaving, delete the room
                    roomRef.removeValue()
                }
                super.onBackPressed()
            }
            .setNegativeButton("No", null)
            .show()
    }
}