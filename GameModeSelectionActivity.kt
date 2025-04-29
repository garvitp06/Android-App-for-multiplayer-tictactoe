package com.example.ultimatetictactoe

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ultimatetictactoe.ui.theme.CreateJoinActivity
import com.example.ultimatetictactoe.ui.theme.GameActivity

class GameModeSelectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_mode_selection) // This matches the XML file name

        // Properly reference the buttons using their IDs
        val btnVsCpu: Button = findViewById(R.id.btnVsCpu)
        val btnLocalMulti: Button = findViewById(R.id.btnLocalMulti)
        val btnOnlineMulti: Button = findViewById(R.id.btnOnlineMulti)

        btnVsCpu.setOnClickListener {
            startActivity(Intent(this, GameActivity::class.java).apply {
                putExtra("GAME_MODE", "VS_CPU")
            })
        }

        btnLocalMulti.setOnClickListener {
            Intent(this@GameModeSelectionActivity, GameActivity::class.java).apply {
                putExtra("GAME_MODE", "LOCAL_MULTI")
                // Add these default values if needed by your game logic
                putExtra("PLAYER_ROLE", "local_player")
                putExtra("ROOM_CODE", "local_game")
                startActivity(this)
            }
        }

        btnOnlineMulti.setOnClickListener {
            startActivity(Intent(this, CreateJoinActivity::class.java))
        }
    }
}