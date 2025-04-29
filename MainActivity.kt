    package com.example.ultimatetictactoe

    import android.content.Intent
    import android.content.SharedPreferences
    import android.os.Bundle
    import android.util.Log
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

        private val random = Random()
        private val TAG = "MainActivity"

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_create_join)

            // Initialize Firebase
            auth = FirebaseAuth.getInstance()
            database = FirebaseDatabase.getInstance()

            // Initialize SharedPreferences
            prefs = getSharedPreferences("GamePrefs", MODE_PRIVATE)

            // Initialize UI components
            btnCreateRoom = findViewById(R.id.btnCreateRoom)
            btnJoinRoom = findViewById(R.id.btnJoinRoom)
            etRoomCode = findViewById(R.id.etRoomCode)

            setupClickListeners()
            checkPreviousRoom()

            // Ensure user is signed in (anonymously if needed)
            ensureUserSignedIn()
        }

        private fun ensureUserSignedIn() {
            if (auth.currentUser == null) {
                auth.signInAnonymously()
                    .addOnSuccessListener {
                        Log.d(TAG, "Anonymous sign-in successful")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Anonymous sign-in failed", e)
                    }
            }
        }

        private fun setupClickListeners() {
            btnCreateRoom.setOnClickListener { createNewRoom() }
            btnJoinRoom.setOnClickListener { handleJoinRoom() }
        }

        private fun handleJoinRoom() {
            val enteredCode = etRoomCode.text.toString().trim()
            when {
                enteredCode.length != 5 -> showError("Code must be 5 digits!")
                else -> joinExistingRoom(enteredCode)
            }
        }

        private fun createNewRoom() {
            try {
                val roomCode = generateValidRoomCode()
                Log.d(TAG, "Generated code: $roomCode")

                val roomRef = database.getReference("rooms/$roomCode")
                val userId = getUserId()

                val roomData = hashMapOf(
                    "player1" to userId,
                    "player2" to "",
                    "status" to "waiting",
                    "currentTurn" to "player1",
                    "currentSubGrid" to -1,
                    "board" to hashMapOf<String, Any>()
                )

                roomRef.setValue(roomData).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        prefs.edit().putString("last_room", roomCode).apply()
                        showToast("Room created: $roomCode")

                        // Navigate to WaitingActivity
                        Intent(this@MainActivity, WaitingActivity::class.java).apply {
                            putExtra("ROOM_CODE", roomCode)
                            putExtra("PLAYER_ROLE", "player1")
                            startActivity(this)
                        }
                    } else {
                        showError("Failed to create room: ${task.exception?.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating room: ${e.stackTraceToString()}")
                showError("Error: ${e.message}")
            }
        }

        private fun joinExistingRoom(enteredCode: String) {
            val roomReference = database.getReference("rooms/$enteredCode")

            roomReference.get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val status = snapshot.child("status").getValue(String::class.java) ?: ""

                    when (status) {
                        "waiting" -> {
                            val player2 = snapshot.child("player2").getValue(String::class.java) ?: ""

                            if (player2.isEmpty()) {
                                // Save room code to preferences
                                prefs.edit().putString("last_room", enteredCode).apply()

                                // First navigate to waiting room (don't change status yet)
                                Intent(this@MainActivity, WaitingActivity::class.java).apply {
                                    putExtra("ROOM_CODE", enteredCode)
                                    putExtra("PLAYER_ROLE", "player2")
                                    startActivity(this)
                                }

                                // Then update player2 in database
                                val currentUserId = getUserId()
                                roomReference.child("player2").setValue(currentUserId)
                                    .addOnSuccessListener {
                                        // Only update status once player2 is set
                                        roomReference.child("status").setValue("in-game")
                                    }
                                    .addOnFailureListener { e ->
                                        showError("Failed to join room: ${e.message}")
                                    }
                            } else {
                                showError("Room is already full!")
                            }
                        }
                        "in-game" -> showError("Game already in progress!")
                        "ended" -> showError("Game has ended!")
                        else -> showError("Invalid room state")
                    }
                } else {
                    showError("Room not found!")
                }
            }.addOnFailureListener { e ->
                showError("Error joining room: ${e.message}")
            }
        }

        private fun generateValidRoomCode(): String {
            var code: String
            do {
                code = String.format("%05d", random.nextInt(100000))
            } while (code.toInt() < 10000)
            return code
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
            etRoomCode.requestFocus()
        }

        private fun showToast(message: String) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }