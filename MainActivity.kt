package com.example.ultimatetictactoe

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.Random

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var prefs: SharedPreferences

    // UI Components
    private lateinit var btnCreateRoom: Button
    private lateinit var btnJoinRoom: Button
    private lateinit var etRoomCode: EditText

    // Random generator for room codes
    private val random = Random()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_join)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        prefs = getSharedPreferences("GamePrefs", MODE_PRIVATE)

        // Bind UI components
        btnCreateRoom = findViewById(R.id.btnCreateRoom)
        btnJoinRoom = findViewById(R.id.btnJoinRoom)
        etRoomCode = findViewById(R.id.etRoomCode)

        setupClickListeners()
        checkPreviousRoom()
    }

    private fun setupClickListeners() {
        btnCreateRoom.setOnClickListener {
            createNewRoom()
        }

        btnJoinRoom.setOnClickListener {
            val enteredCode = etRoomCode.text.toString().trim()
            when {
                enteredCode.length != 5 -> showError("Code must be 5 digits!")
                else -> joinExistingRoom(enteredCode)
            }
        }
    }

    private fun createNewRoom() {
        val roomCode = generateRoomCode()
        val roomRef = database.getReference("rooms/$roomCode")
        val userId = getUserId()

        val roomData = hashMapOf(
            "player1" to userId,
            "player2" to "",
            "status" to "waiting",
            "currentTurn" to "player1",
            "board" to HashMap<String, Any>()
        )

        roomRef.setValue(roomData).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                prefs.edit().putString("last_room", roomCode).apply()
                showToast("Room created: $roomCode")
                navigateToGame(roomCode, "player1")
            } else {
                showError("Failed to create room: ${task.exception?.message}")
            }
        }
    }

    private fun joinExistingRoom(roomCode: String) {
        val roomRef = database.getReference("rooms/$roomCode")

        roomRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                when (snapshot.child("status").value.toString()) {
                    "waiting" -> joinAvailableRoom(roomRef)
                    "in-game" -> showError("Game already started!")
                    "ended" -> showError("Game finished!")
                    else -> showError("Invalid room state")
                }
            } else {
                showError("Room not found!")
            }
        }
    }

    private fun joinAvailableRoom(roomRef: DatabaseReference) {
        val userId = getUserId()

        roomRef.child("player2").get().addOnSuccessListener { snapshot ->
            if (snapshot.value.toString().isEmpty()) {
                roomRef.child("player2").setValue(userId).addOnSuccessListener {
                    prefs.edit().putString("last_room", roomRef.key).apply()
                    navigateToGame(roomRef.key!!, "player2")
                }
            } else {
                showError("Room is full!")
            }
        }
    }

    private fun generateRoomCode(): String {
        return String.format("%05d", random.nextInt(100000)).also {
            if (it.toInt() < 10000) return generateRoomCode() // Ensure 5 digits
        }
    }

    private fun navigateToGame(roomCode: String, playerRole: String) {
        Intent(this, GameActivity::class.java).apply {
            putExtra("ROOM_CODE", roomCode)
            putExtra("PLAYER_ROLE", playerRole)
            startActivity(this)
        }
    }

    private fun checkPreviousRoom() {
        prefs.getString("last_room", null)?.let { previousRoom ->
            etRoomCode.setText(previousRoom)
            showToast("Found previous room: $previousRoom")
        }
    }

    private fun getUserId(): String {
        return auth.currentUser?.uid ?: "guest_${System.currentTimeMillis()}"
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        etRoomCode.error = message
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}