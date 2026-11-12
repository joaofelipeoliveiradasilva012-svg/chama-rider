package com.example.chamarider

import android.app.Activity
import android.os.Bundle

class MainActivity : Activity() {
    private lateinit var game: GameView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        game = GameView(this)
        setContentView(game)
    }
    override fun onResume() { super.onResume(); game.resumeGame() }
    override fun onPause() { super.onPause(); game.pauseGame() }
}
