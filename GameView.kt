package com.example.chamarider

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceView
import java.util.Random

class GameView(ctx: Context) : SurfaceView(ctx), Runnable {
    private class Obj(val lane: Int, var y: Float, val enemy: Boolean)
    private class Shot(val lane: Int, var y: Float)

    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var pendingMove = 0
    @Volatile private var pendingFire = false
    @Volatile private var pendingRestart = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rnd = Random()
    private val objs = ArrayList<Obj>()
    private val shots = ArrayList<Shot>()

    private var lane = 1
    private var lives = 3
    private var score = 0
    private var best = 0
    private var frame = 0
    private var spawnT = 40
    private var invuln = 0
    private var over = false
    private var dashOffset = 0f

    fun resumeGame() { running = true; thread = Thread(this).also { it.start() } }
    fun pauseGame() { running = false; thread?.join(); thread = null }

    override fun run() {
        while (running) {
            if (!holder.surface.isValid) { Thread.sleep(16); continue }
            update()
            val c = holder.lockCanvas() ?: continue
            try { draw2(c) } finally { holder.unlockCanvasAndPost(c) }
            Thread.sleep(16)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_DOWN) return true
        if (over) { pendingRestart = true; return true }
        val third = width / 3f
        when {
            e.x < third -> pendingMove = -1
            e.x > 2 * third -> pendingMove = 1
            else -> pendingFire = true
        }
        return true
    }

    private fun reset() {
        objs.clear(); shots.clear()
        lane = 1; lives = 3; score = 0; frame = 0; spawnT = 40; invuln = 0; over = false
    }

    private fun update() {
        if (width == 0) return
        val h = height.toFloat()
        if (pendingRestart) { pendingRestart = false; reset() }
        if (over) return
        val m = pendingMove; pendingMove = 0
        lane = (lane + m).coerceIn(0, 2)
        if (pendingFire) { pendingFire = false; shots.add(Shot(lane, h * 0.78f)) }

        frame++
        val speed = h * 0.006f * (1f + score / 400f)
        dashOffset = (dashOffset + speed) % (h * 0.1f)
        if (frame % 6 == 0) score++
        if (invuln > 0) invuln--

        if (--spawnT <= 0) {
            objs.add(Obj(rnd.nextInt(3), -h * 0.1f, rnd.nextInt(3) == 0))
            spawnT = (70 - minOf(40, score / 15)) + rnd.nextInt(20)
        }
        val py = h * 0.8f
        val it = objs.iterator()
        while (it.hasNext()) {
            val o = it.next()
            o.y += speed
            if (o.y > h + 100) { it.remove(); continue }
            if (o.lane == lane && Math.abs(o.y - py) < h * 0.07f && invuln == 0) {
                it.remove(); lives--; invuln = 90
                if (lives <= 0) { over = true; best = maxOf(best, score) }
            }
        }
        val si = shots.iterator()
        while (si.hasNext()) {
            val s = si.next()
            s.y -= h * 0.02f
            if (s.y < -50) { si.remove(); continue }
            val hit = objs.firstOrNull { it.lane == s.lane && Math.abs(it.y - s.y) < h * 0.05f }
            if (hit != null) {
                si.remove()
                if (hit.enemy) { objs.remove(hit); score += 10 }
            }
        }
    }

    private fun laneX(l: Int) = width * (l + 0.5f) / 3f

    private fun draw2(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawColor(Color.rgb(20, 20, 28))
        paint.color = Color.rgb(60, 60, 70)
        paint.style = Paint.Style.FILL
        var y = -h * 0.1f + dashOffset
        while (y < h) {
            c.drawRect(w / 3f - 4, y, w / 3f + 4, y + h * 0.05f, paint)
            c.drawRect(2 * w / 3f - 4, y, 2 * w / 3f + 4, y + h * 0.05f, paint)
            y += h * 0.1f
        }
        for (o in objs) {
            val x = laneX(o.lane)
            if (o.enemy) {
                paint.color = Color.rgb(140, 40, 190)
                c.drawCircle(x, o.y, w * 0.07f, paint)
                paint.color = Color.RED
                c.drawCircle(x - w * 0.025f, o.y - 6, w * 0.014f, paint)
                c.drawCircle(x + w * 0.025f, o.y - 6, w * 0.014f, paint)
            } else {
                paint.color = Color.rgb(120, 130, 140)
                c.drawRect(x - w * 0.07f, o.y - h * 0.045f, x + w * 0.07f, o.y + h * 0.045f, paint)
            }
        }
        paint.color = Color.rgb(255, 140, 0)
        for (s in shots) c.drawCircle(laneX(s.lane), s.y, w * 0.025f, paint)

        if (invuln % 10 < 6) {
            val x = laneX(lane); val py = h * 0.8f
            paint.color = Color.rgb(40, 40, 40)
            c.drawRect(x - w * 0.03f, py - h * 0.035f, x + w * 0.03f, py + h * 0.05f, paint)
            paint.color = Color.rgb(255, 90, 0)
            c.drawCircle(x, py - h * 0.05f, w * 0.045f, paint)
            paint.color = Color.rgb(255, 210, 60)
            c.drawCircle(x, py - h * 0.05f, w * 0.025f, paint)
            paint.color = Color.rgb(255, 90, 0)
            val f = Path()
            f.moveTo(x - w * 0.03f, py + h * 0.05f)
            f.lineTo(x, py + h * 0.05f + h * 0.04f + rnd.nextInt(20))
            f.lineTo(x + w * 0.03f, py + h * 0.05f)
            f.close(); c.drawPath(f, paint)
        }

        paint.color = Color.WHITE
        paint.textSize = w * 0.05f
        c.drawText("Pontos: $score   Vidas: $lives   Recorde: $best", 20f, w * 0.08f, paint)
        if (over) {
            paint.textSize = w * 0.1f
            paint.textAlign = Paint.Align.CENTER
            c.drawText("FIM DE JOGO", w / 2, h * 0.4f, paint)
            paint.textSize = w * 0.05f
            c.drawText("Toque para recomeçar", w / 2, h * 0.47f, paint)
            paint.textAlign = Paint.Align.LEFT
        }
    }
}
