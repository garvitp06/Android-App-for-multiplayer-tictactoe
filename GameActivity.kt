package com.example.ultimatetictactoe.ui.theme

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ultimatetictactoe.R
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class GameActivity : AppCompatActivity() {

    // Game state variables
    private lateinit var database: FirebaseDatabase
    private lateinit var roomRef: DatabaseReference
    private lateinit var roomCode: String
    private lateinit var playerRole: String
    private lateinit var gameMode: GameMode
    private lateinit var tvTurnStatus: TextView
    private var isAgainstCpu = false
    private var currentSubGrid = -1
    private var isPlayer1Turn = true
    private val boardState = Array(9) { Array(9) { "" } }
    enum class GameMode { VS_CPU, LOCAL_MULTI, ONLINE_MULTI }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        // Initialize turn status display
        tvTurnStatus = findViewById(R.id.tvTurnStatus)

        // Handle game mode initialization
        when (val mode = intent.getStringExtra("GAME_MODE")) {
            "VS_CPU" -> {
                gameMode = GameMode.VS_CPU
                isAgainstCpu = true
            }
            "LOCAL_MULTI" -> {
                gameMode = GameMode.LOCAL_MULTI
                isAgainstCpu = false
            }
            "ONLINE_MULTI" -> setupOnlineGame()
            else -> {
                // Default to local multiplayer if no mode specified
                gameMode = GameMode.LOCAL_MULTI
                isAgainstCpu = false
            }
        }

        // Common initialization
        initializeBoard()
        resetGame()
        updateTurnUI()
    }
    private fun setupOnlineGame() {
        roomCode = intent.getStringExtra("ROOM_CODE") ?: run {
            Toast.makeText(this, "Invalid room code", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        playerRole = intent.getStringExtra("PLAYER_ROLE") ?: run {
            Toast.makeText(this, "Player role not specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        database = FirebaseDatabase.getInstance()
        roomRef = database.getReference("rooms/$roomCode")
        setupFirebaseListeners()
    }

    private fun setupLocalGame() {
        // Reset game state
        currentSubGrid = -1
        isPlayer1Turn = true

        // Enable all cells
        for (mainGridIndex in 0..8) {
            for (cellIndex in 0..8) {
                findViewById<Button>(resources.getIdentifier(
                    "cell${mainGridIndex}_${cellIndex}",
                    "id",
                    packageName
                )).isEnabled = true
            }
        }

        updateTurnUI()
    }

    private fun setupFirebaseListeners() {
        roomRef.child("board").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { mainGridSnap ->
                    val mainGridIndex = mainGridSnap.key?.toInt() ?: return@forEach
                    mainGridSnap.children.forEach { cellSnap ->
                        val cellIndex = cellSnap.key?.toInt() ?: return@forEach
                        boardState[mainGridIndex][cellIndex] = cellSnap.value.toString()
                        updateCellUI(mainGridIndex, cellIndex)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                showError("Board sync failed: ${error.message}")
            }
        })

        roomRef.child("currentTurn").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val turn = snapshot.value.toString()
                updateTurnUI(turn)
            }
            override fun onCancelled(error: DatabaseError) {
                showError("Turn sync failed: ${error.message}")
            }
        })

        roomRef.child("currentSubGrid").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentSubGrid = snapshot.value.toString().toIntOrNull() ?: -1
            }
            override fun onCancelled(error: DatabaseError) {
                showError("Subgrid sync failed: ${error.message}")
            }
        })

        roomRef.child("status").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                when (snapshot.getValue(String::class.java)) {
                    "in-game" -> roomRef.child("currentTurn").get().addOnSuccessListener {
                        updateTurnUI(it.getValue(String::class.java) ?: "player1")
                    }
                    "ended" -> showGameOverDialog()
                    "waiting" -> resetLocalGameState()
                }
            }
            override fun onCancelled(error: DatabaseError) {
                showError("Status sync failed: ${error.message}")
            }
        })
    }

    private fun initializeBoard() {
        for (mainGridIndex in 0..8) {
            val subGrid = findViewById<GridLayout>(
                resources.getIdentifier("subGrid$mainGridIndex", "id", packageName)
            )

            for (cellIndex in 0..8) {
                val cell = subGrid.findViewById<Button>(
                    resources.getIdentifier("cell${mainGridIndex}_${cellIndex}", "id", packageName)
                )
                cell.setOnClickListener {
                    when (gameMode) {
                        GameMode.ONLINE_MULTI -> handleOnlineMove(mainGridIndex, cellIndex)
                        else -> handleLocalMove(mainGridIndex, cellIndex)
                    }
                }
            }
        }
    }

    private fun handleOnlineMove(mainGrid: Int, cell: Int) {
        lifecycleScope.launch {
            if (isValidOnlineMove(mainGrid, cell)) {
                val symbol = if (playerRole == "player1") "X" else "O"
                roomRef.child("board/$mainGrid/$cell").setValue(symbol)
                roomRef.child("currentTurn").setValue(if (playerRole == "player1") "player2" else "player1")
                roomRef.child("currentSubGrid").setValue(cell)
                checkSubGridWin(mainGrid)
                checkMainGridWin()
            }
        }
    }

    private suspend fun isValidOnlineMove(mainGrid: Int, cell: Int): Boolean {
        return try {
            val currentTurnTask = roomRef.child("currentTurn").get()
            val statusTask = roomRef.child("status").get()

            val currentTurn = currentTurnTask.await().getValue(String::class.java) ?: ""
            val roomStatus = statusTask.await().getValue(String::class.java) ?: ""

            (currentSubGrid == -1 || mainGrid == currentSubGrid) &&
                    boardState[mainGrid][cell].isEmpty() &&
                    currentTurn == playerRole &&
                    roomStatus == "in-game"
        } catch (e: Exception) {
            false
        }
    }

    private fun handleLocalMove(mainGrid: Int, cell: Int) {
        try {
            if (!isValidLocalMove(mainGrid, cell)) return

            val symbol = if (isPlayer1Turn) "X" else "O"
            boardState[mainGrid][cell] = symbol
            updateCellUI(mainGrid, cell)
            checkLocalGameEnd()

            // Only switch turns if not against CPU
            if (gameMode == GameMode.LOCAL_MULTI) {
                isPlayer1Turn = !isPlayer1Turn
                updateTurnUI()
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Local move error: ${e.message}")
            Toast.makeText(this, "Move failed!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun makeCpuMove() {
        lifecycleScope.launch {
            delay(1000) // Simulate thinking time

            val validMoves = mutableListOf<Pair<Int, Int>>().apply {
                for (i in 0..8) {
                    for (j in 0..8) {
                        if (boardState[i][j].isEmpty() && (currentSubGrid == -1 || i == currentSubGrid)) {
                            add(Pair(i, j))
                        }
                    }
                }
            }

            validMoves.randomOrNull()?.let { (mainGrid, cell) ->
                boardState[mainGrid][cell] = "O"
                updateCellUI(mainGrid, cell)
                isPlayer1Turn = true
                updateTurnUI()
                checkLocalGameEnd()
            }
        }
    }

    private fun isValidLocalMove(mainGrid: Int, cell: Int): Boolean {
        return boardState[mainGrid][cell].isEmpty() &&
                (currentSubGrid == -1 || mainGrid == currentSubGrid) &&
                (isPlayer1Turn || gameMode == GameMode.LOCAL_MULTI)
    }

    private fun updateCellUI(mainGrid: Int, cell: Int) {
        findViewById<Button>(
            resources.getIdentifier("cell${mainGrid}_${cell}", "id", packageName)
        ).apply {
            text = boardState[mainGrid][cell]
            isEnabled = boardState[mainGrid][cell].isEmpty()
        }
    }

    private fun updateTurnUI(turn: String? = null) {
        val status = when {
            gameMode == GameMode.ONLINE_MULTI -> turn?.let {
                if (it == playerRole) "Your turn (${if(playerRole=="player1")"X" else "O"})"
                else "Opponent's turn"
            }
            gameMode == GameMode.VS_CPU -> if (isPlayer1Turn) "Your turn (X)" else "CPU's turn (O)"
            else -> if (isPlayer1Turn) "Player 1's turn (X)" else "Player 2's turn (O)"
        }
        tvTurnStatus.text = status ?: ""
    }

    private fun checkLocalGameEnd() {
        checkMainGridWin(Array(9) { checkSubGridWin(it) })
    }

    private fun checkSubGridWin(mainGrid: Int): String? {
        val subGrid = boardState[mainGrid]
        // Check rows
        for (i in 0..2) {
            if (subGrid[i*3].isNotEmpty() && subGrid[i*3] == subGrid[i*3+1] && subGrid[i*3] == subGrid[i*3+2]) {
                return subGrid[i*3]
            }
        }
        // Check columns
        for (i in 0..2) {
            if (subGrid[i].isNotEmpty() && subGrid[i] == subGrid[i+3] && subGrid[i] == subGrid[i+6]) {
                return subGrid[i]
            }
        }
        // Check diagonals
        if (subGrid[0].isNotEmpty() && subGrid[0] == subGrid[4] && subGrid[0] == subGrid[8]) return subGrid[0]
        if (subGrid[2].isNotEmpty() && subGrid[2] == subGrid[4] && subGrid[2] == subGrid[6]) return subGrid[2]
        return null
    }

    private fun checkMainGridWin(mainGridWinners: Array<String?> = Array(9) { checkSubGridWin(it) }) {
        // Check rows
        for (i in 0..2) {
            if (mainGridWinners[i*3] != null && mainGridWinners[i*3] == mainGridWinners[i*3+1] &&
                mainGridWinners[i*3] == mainGridWinners[i*3+2]) {
                endGame(mainGridWinners[i*3]!!)
                return
            }
        }
        // Check columns
        for (i in 0..2) {
            if (mainGridWinners[i] != null && mainGridWinners[i] == mainGridWinners[i+3] &&
                mainGridWinners[i] == mainGridWinners[i+6]) {
                endGame(mainGridWinners[i]!!)
                return
            }
        }
        // Check diagonals
        if (mainGridWinners[0] != null && mainGridWinners[0] == mainGridWinners[4] &&
            mainGridWinners[0] == mainGridWinners[8]) {
            endGame(mainGridWinners[0]!!)
            return
        }
        if (mainGridWinners[2] != null && mainGridWinners[2] == mainGridWinners[4] &&
            mainGridWinners[2] == mainGridWinners[6]) {
            endGame(mainGridWinners[2]!!)
            return
        }
    }

    private fun endGame(winnerSymbol: String) {
        if (gameMode == GameMode.ONLINE_MULTI) {
            val winner = if (winnerSymbol == "X") "player1" else "player2"
            roomRef.child("winner").setValue(winner)
            roomRef.child("status").setValue("ended")
        } else {
            showGameOverDialog(winnerSymbol)
        }
    }

    private fun showGameOverDialog(winnerSymbol: String? = null) {
        val message = when {
            gameMode == GameMode.ONLINE_MULTI -> {
                val winner = intent.getStringExtra("winner") ?: return
                if (winner == playerRole) "You won!" else "You lost!"
            }
            winnerSymbol == "X" -> if (gameMode == GameMode.VS_CPU) "You won!" else "Player 1 wins!"
            winnerSymbol == "O" -> if (gameMode == GameMode.VS_CPU) "CPU wins!" else "Player 2 wins!"
            else -> "It's a draw!"
        }

        AlertDialog.Builder(this)
            .setTitle("Game Over")
            .setMessage(message)
            .setPositiveButton("Rematch") { _, _ -> resetGame() }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .show()
    }

    private fun resetGame() {
        for (i in 0 until 9) {
            boardState[i].fill("")
        }
        currentSubGrid = -1
        isPlayer1Turn = true

        for (mainGridIndex in 0..8) {
            for (cellIndex in 0..8) {
                updateCellUI(mainGridIndex, cellIndex)
            }
        }

        if (gameMode == GameMode.ONLINE_MULTI) {
            roomRef.child("board").removeValue()
            roomRef.child("currentTurn").setValue("player1")
            roomRef.child("currentSubGrid").setValue(-1)
            roomRef.child("status").setValue("in-game")
            roomRef.child("winner").removeValue()
        } else {
            updateTurnUI()
        }
    }

    private fun resetLocalGameState() {
        resetGame()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        AlertDialog.Builder(this)
            .setTitle("Leave Game")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Yes") { _, _ -> finish() }
            .setNegativeButton("No", null)
            .show()
    }
}