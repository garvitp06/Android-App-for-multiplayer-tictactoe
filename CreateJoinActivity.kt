package com.example.ultimatetictactoe.ui.theme

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.ultimatetictactoe.R
import com.example.ultimatetictactoe.WaitingActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class CreateJoinActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var prefs: SharedPreferences
    private lateinit var tvPlayerName: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_join)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        prefs = getSharedPreferences("GamePrefs", MODE_PRIVATE)

        val btnCreateRoom: Button = findViewById(R.id.btnCreateRoom)
        val btnJoinRoom: Button = findViewById(R.id.btnJoinRoom)
        val etRoomCode: EditText = findViewById(R.id.etRoomCode)
        val btnShareCode: Button = findViewById(R.id.btnShareCode)
        tvPlayerName = findViewById(R.id.tvPlayerName)

        // Load saved player name or use default
        val playerName = prefs.getString("player_name", "Player") ?: "Player"
        tvPlayerName.text = "Hello, $playerName"

        btnCreateRoom.setOnClickListener {
            createRoom(playerName)
        }

        btnJoinRoom.setOnClickListener {
            val enteredCode = etRoomCode.text.toString().trim()
            when {
                enteredCode.length != 5 -> showToast("Room code must be 5 digits!")
                else -> joinRoom(enteredCode, playerName)
            }
        }

        // Optional: Share room code feature
        btnShareCode.setOnClickListener {
            etRoomCode.text.toString().takeIf { it.length == 5 }?.let { code ->
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Join my Ultimate Tic-Tac-Toe game! Room code: $code")
                    startActivity(Intent.createChooser(this, "Share Room Code"))
                } ?: showToast("Enter a valid room code first")
            }
        }

        // Optional: Change player name
        tvPlayerName.setOnClickListener {
            showNameChangeDialog(playerName)
        }
    }

    private fun createRoom(playerName: String) {
        val roomCode = (10000..99999).random().toString()
        val userId = auth.currentUser?.uid ?: "guest_${System.currentTimeMillis()}"

        // Initialize empty board structure
        val initialBoard = HashMap<String, Any>().apply {
            for (i in 0..8) {
                put(i.toString(), HashMap<String, String>().apply {
                    for (j in 0..8) {
                        put(j.toString(), "")
                    }
                })
            }
        }

        val roomRef = database.getReference("rooms/$roomCode")
        val roomData = hashMapOf(
            "player1" to userId,
            "player1Name" to playerName,
            "player2" to "",
            "player2Name" to "",
            "status" to "waiting",
            "currentTurn" to "player1",
            "currentSubGrid" to -1,
            "board" to initialBoard,
            "createdAt" to System.currentTimeMillis()
        )

        roomRef.setValue(roomData).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Save room code to preferences
                prefs.edit().putString("last_room", roomCode).apply()

                // Navigate to WaitingActivity as player1
                Intent(this, WaitingActivity::class.java).apply {
                    putExtra("ROOM_CODE", roomCode)
                    putExtra("PLAYER_ROLE", "player1")
                    startActivity(this)
                }
                showToast("Room $roomCode created!")
            } else {
                showToast("Failed to create room: ${task.exception?.message}")
            }
        }
    }

    private fun joinRoom(roomCode: String, playerName: String) {
        val roomRef = database.getReference("rooms/$roomCode")
        roomRef.get().addOnSuccessListener { snapshot ->
            when {
                !snapshot.exists() -> showToast("Room not found!")
                snapshot.child("status").getValue(String::class.java) == "ended" ->
                    showToast("This game has ended")
                snapshot.child("player2").getValue(String::class.java)?.isNotEmpty() == true ->
                    showToast("Room is full!")
                else -> {
                    val userId = auth.currentUser?.uid ?: "guest_${System.currentTimeMillis()}"

                    // Save room code to preferences
                    prefs.edit().putString("last_room", roomCode).apply()

                    // Update room with player2 info
                    roomRef.child("player2").setValue(userId)
                    roomRef.child("player2Name").setValue(playerName)
                    roomRef.child("status").setValue("in-game")

                    // Navigate to WaitingActivity as player2
                    Intent(this, WaitingActivity::class.java).apply {
                        putExtra("ROOM_CODE", roomCode)
                        putExtra("PLAYER_ROLE", "player2")
                        startActivity(this)
                    }
                }
            }
        }.addOnFailureListener { e ->
            showToast("Error: ${e.message}")
        }
    }

    // Optional: Player name change dialog
    private fun showNameChangeDialog(currentName: String) {
        val input = EditText(this).apply {
            setText(currentName)
        }

        AlertDialog.Builder(this)
            .setTitle("Change Your Name")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    prefs.edit().putString("player_name", newName).apply()
                    tvPlayerName.text = "Hello, $newName"
                    showToast("Name updated!")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Optional: Back button confirmation
    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("Leave Room Setup")
            .setMessage("Are you sure you want to go back?")
            .setPositiveButton("Yes") { _, _ -> super.onBackPressed() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}