package com.example.ultimatetictactoe

import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.auth.FirebaseAuth
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.content.Intent
class CreateJoinActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_join)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Initialize buttons
        val btnCreateRoom: Button = findViewById(R.id.btnCreateRoom)
        val btnJoinRoom: Button = findViewById(R.id.btnJoinRoom)
        val etRoomCode: EditText = findViewById(R.id.etRoomCode)

        // --- Create Room ---
        btnCreateRoom.setOnClickListener {
            // Generate a 5-digit room code
            val roomCode = (10000..99999).random().toString()
            createRoom(roomCode)
        }

        // --- Join Room ---
        btnJoinRoom.setOnClickListener {
            val enteredCode = etRoomCode.text.toString().trim()
            if (enteredCode.length != 5) {
                Toast.makeText(this, "Invalid code!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            joinRoom(enteredCode)
        }
    }

    // Function to create a new room
    private fun createRoom(roomCode: String) {
        // Get current user ID (or use anonymous auth)
        val currentUser = auth.currentUser
        val userId = currentUser?.uid ?: "guest_${System.currentTimeMillis()}"

        // Save room to Firebase
        val roomRef = database.getReference("rooms/$roomCode")
        val roomData = hashMapOf(
            "player1" to userId,
            "player2" to "",
            "status" to "waiting" // waiting, in_progress, ended
        )
        roomRef.setValue(roomData).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Navigate to GameActivity (pass room code and player role)
                val intent = Intent(this, GameActivity::class.java).apply {
                    putExtra("ROOM_CODE", roomCode)
                    putExtra("PLAYER_ROLE", "player1")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Failed to create room!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Function to join an existing room
    private fun joinRoom(roomCode: String) {
        val roomRef = database.getReference("rooms/$roomCode")
        roomRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val player2 = snapshot.child("player2").value.toString()
                if (player2.isEmpty()) {
                    // Assign current user as player2
                    val currentUser = auth.currentUser
                    val userId = currentUser?.uid ?: "guest_${System.currentTimeMillis()}"
                    roomRef.child("player2").setValue(userId).addOnSuccessListener {
                        // Navigate to GameActivity (pass room code and player role)
                        val intent = Intent(this, GameActivity::class.java).apply {
                            putExtra("ROOM_CODE", roomCode)
                            putExtra("PLAYER_ROLE", "player2")
                        }
                        startActivity(intent)
                    }
                } else {
                    Toast.makeText(this, "Room is full!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Room not found!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}