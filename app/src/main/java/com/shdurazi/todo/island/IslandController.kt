package com.shdurazi.todo.island

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.WindowManager
import com.shdurazi.todo.MainActivity
import com.shdurazi.todo.ToDoApp
import com.shdurazi.todo.core.AppData
import com.shdurazi.todo.core.FocusSession
import com.shdurazi.todo.core.IslandRing
import com.shdurazi.todo.notify.Notifier
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

/**
 * Puts the island on the screen while a focus session runs and the app is not in front.
 *
 * Two windows can carry it:
 *  - the accessibility service's overlay (above the status bar, tappable, also on the lock screen), and
 *  - a plain "display over other apps" overlay (below the status bar, hidden on the lock screen).
 * The accessibility one wins when it is connected.
 *
 * Everything that touches a window runs on the main thread; [sync] and [refresh] may be called from anywhere.
 * Every step is wrapped so that a problem here can only ever lose the island, never take the app down.
 */
object IslandController {
    private const val TAG = "ToDoIsland"

    private const val KIND_REAL = 1
    private const val KIND_PREVIEW = 2
    private const val KIND_DONE = 3
    private const val PREVIEW_KEY = -1L

    private const val INTRO_DELAY_MS = 450L
    private const val HOLD_MS = 5_200L
    private const val DONE_HOLD_MS = 4_200L
    private const val PREVIEW_MS = 9_500L

    private val main = Handler(Looper.getMainLooper())

    /** The accessibility service is connected (readable from any thread). */
    @Volatile
    private var a11yUp = false

    // ---------------------------------------------------------------- state (main thread only)
    private var app: Context? = null
    private var a11y: AccessibilityService? = null
    private var view: IslandView? = null
    private var wm: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null
    private var hostName = ""
    private var geo: IslandView.Geo? = null
    private var shownKind = 0
    private var shownKey = 0L
    private var lastModel: IslandView.Model? = null
    private var previewUntil = 0L
    private var finishUntil = 0L
    private var introPending = false
    private var windowOpen = false
    private var fading = false
    private var wanted = false
    private var tickScheduled = false
    private var ticks = 0
    private var lastDecision = ""

    private class Host(val name: String, val context: Context, val manager: WindowManager, val type: Int, val overlay: Boolean)

    // ---------------------------------------------------------------- entry points
    /** The data changed (called after every write). Any thread. */
    fun sync(context: Context, @Suppress("UNUSED_PARAMETER") d: AppData) = post(context, "sync")

    /** Something other than the data changed: the switch, a permission, the app going to the front or back. Any thread. */
    fun refresh(context: Context) = post(context, "refresh")

    /** The switch was flipped or a permission changed: redo the island and the notification's Live Update choice. Any thread. */
    fun settingsChanged(context: Context) {
        val a = context.applicationContext
        post(a, "settings")
        resyncNotification(a)
    }

    /** Shows a demo island for a few seconds, whatever the app is doing. Any thread. */
    fun preview(context: Context) {
        val a = context.applicationContext
        main.post {
            app = a
            previewUntil = System.currentTimeMillis() + PREVIEW_MS
            Log.i(TAG, "preview requested")
            evaluate("preview")
            main.removeCallbacks(reevaluate)
            main.postDelayed(reevaluate, PREVIEW_MS + 150L)
        }
    }

    /** Will the island be what shows a running session? Then the notification does not ask to be a Live Update. */
    fun active(context: Context): Boolean = IslandPrefs.usable(context) && (a11yUp || canOverlay(context))

    fun accessibilityOn(): Boolean = a11yUp

    fun canOverlay(context: Context): Boolean = try {
        Settings.canDrawOverlays(context)
    } catch (e: Throwable) {
        false
    }

    /** Called by the accessibility service when it is connected. */
    fun a11yConnected(s: AccessibilityService) {
        val a = s.applicationContext
        a11yUp = true
        main.post {
            app = a
            a11y = s
            Log.i(TAG, "accessibility service connected")
            if (view != null && hostName != "a11y") detach("moving to the accessibility window")
            evaluate("accessibility connected")
        }
        resyncNotification(a)
    }

    /** Called by the accessibility service when it goes away. */
    fun a11yDisconnected(s: AccessibilityService) {
        val a = try {
            s.applicationContext
        } catch (e: Throwable) {
            null
        }
        val work = Runnable {
            if (a11y === s) {
                a11y = null
                a11yUp = false
            }
            if (a != null) app = a
            Log.i(TAG, "accessibility service gone")
            if (view != null && hostName == "a11y") detach("accessibility service gone")
            evaluate("accessibility gone")
        }
        if (Looper.myLooper() == Looper.getMainLooper()) work.run() else main.post(work)
        if (a != null) resyncNotification(a)
    }

    private fun post(context: Context, reason: String) {
        val a = context.applicationContext
        main.post {
            app = a
            evaluate(reason)
        }
    }

    /** The focus notification asks to be promoted only while the island is not there: post it again with the right choice. */
    private fun resyncNotification(a: Context) {
        ToDoApp.scope.launch {
            try {
                Notifier.syncOngoing(a, ToDoApp.repo(a).data)
            } catch (e: Throwable) {
                // not worth failing over
            }
        }
    }

    // ---------------------------------------------------------------- deciding what to show
    private val reevaluate = Runnable { evaluate("timer") }
    private val closeCard = Runnable { collapseCard() }
    private val introOpen = Runnable { openCard(HOLD_MS) }

    private val tick = Runnable {
        tickScheduled = false
        val v = view
        if (v != null) {
            v.invalidate()
            ticks++
        }
        val ctx = app
        if (v != null && introPending && ctx != null && visibleNow(ctx)) startIntro(ctx, shownKind, shownKey, false)
        if (v == null || ticks % 5 == 0) evaluate("tick") else scheduleTick()
    }

    private fun scheduleTick() {
        if (tickScheduled) return
        if (view == null && !wanted) return
        tickScheduled = true
        main.postDelayed(tick, 1_000L - System.currentTimeMillis() % 1_000L + 20L)
    }

    private fun evaluate(reason: String) {
        try {
            decide(reason)
        } catch (e: Throwable) {
            Log.w(TAG, "evaluate failed: $e")
        }
        scheduleTick()
    }

    private fun decide(reason: String) {
        val ctx = app ?: return
        val now = System.currentTimeMillis()
        val d = ToDoApp.repo(ctx).data
        val f = d.focus
        val usable = IslandPrefs.usable(ctx)
        val fg = ToDoApp.of(ctx).foreground
        val running = f != null && f.endsAt > now

        var model: IslandView.Model? = null
        var key = 0L
        var kind = 0
        if (previewUntil > now) {
            model = previewModel(now)
            key = PREVIEW_KEY
            kind = KIND_PREVIEW
        } else if (usable && !fg && running && f != null) {
            model = buildModel(d, f, now)
            key = f.startedAt
            kind = KIND_REAL
            lastModel = model
            finishUntil = 0L
        } else if (usable && !fg) {
            val lm = lastModel
            if (finishUntil == 0L && lm != null && shownKind == KIND_REAL && lm.endsAt <= now + 2_000L) {
                // the session ran out by itself: show "Done" for a moment
                finishUntil = now + DONE_HOLD_MS + 700L
                main.removeCallbacks(reevaluate)
                main.postDelayed(reevaluate, DONE_HOLD_MS + 750L)
            }
            if (finishUntil > now && lm != null) {
                model = IslandView.Model(lm.title, lm.iconKey, lm.colorKey, lm.startedAt, lm.endsAt, lm.segs, lm.degPerMin, true)
                key = lm.startedAt
                kind = KIND_DONE
            } else {
                lastModel = null
                finishUntil = 0L
            }
        } else {
            lastModel = null
            finishUntil = 0L
        }

        val host = if (model != null) pickHost(ctx) else null
        wanted = model != null && host != null
        val decision = "kind=$kind key=$key usable=$usable fg=$fg running=$running host=${host?.name} attached=${view != null}"
        if (decision != lastDecision) {
            lastDecision = decision
            Log.i(TAG, "decide($reason): $decision")
        }

        if (model == null || host == null) {
            if (view != null) hide()
            return
        }

        val v = view
        if (v == null) {
            if (!attach(ctx, host, model)) return
            shownKind = kind
            shownKey = key
            introPending = kind == KIND_PREVIEW || (kind == KIND_REAL && IslandPrefs.introFor(ctx) != key)
            if (kind == KIND_DONE) {
                introPending = false
                openCard(DONE_HOLD_MS)
            } else if (introPending && visibleNow(ctx)) {
                startIntro(ctx, kind, key, true)
            } else {
                view?.setAppearInstant()
            }
            return
        }

        if (fading) {
            fading = false
            v.setAppearInstant()
        }
        v.setModel(model)
        if (kind != shownKind || key != shownKey) {
            shownKind = kind
            shownKey = key
            if (kind == KIND_DONE) {
                introPending = false
                openCard(DONE_HOLD_MS)
            } else {
                introPending = kind == KIND_PREVIEW || (kind == KIND_REAL && IslandPrefs.introFor(ctx) != key)
                if (introPending && visibleNow(ctx)) startIntro(ctx, kind, key, false)
            }
        }
        checkGeometry(ctx)
    }

    private fun buildModel(d: AppData, f: FocusSession, now: Long): IslandView.Model {
        val segs = IslandRing.segments(d, f, now, ZoneId.systemDefault())
        return IslandView.Model(
            f.title, f.iconKey, f.colorKey, f.startedAt, f.endsAt, segs,
            IslandRing.degPerMin(f.endsAt - f.startedAt), false
        )
    }

    /** What the Anchor picture shows: a 30 minute block that has just begun, and the block before it, dimmed. */
    private fun previewModel(now: Long): IslandView.Model {
        val minute = 60_000L
        val start = now - 4_000L
        val end = start + 30L * minute
        val segs = listOf(
            IslandRing.Seg("focus", start, end, "green", "briefcase", true),
            IslandRing.Seg("before", start - 24L * minute, start - 2L * minute, "green", "pencil", false)
        )
        return IslandView.Model("Afternoon Push", "briefcase", "green", start, end, segs, IslandRing.degPerMin(end - start), false)
    }

    /** Is somebody looking at the screen (awake, and not behind a lock the window cannot be seen through)? */
    private fun visibleNow(ctx: Context): Boolean {
        val pm = ctx.getSystemService(PowerManager::class.java)
        if (pm != null && !pm.isInteractive) return false
        if (hostName == "a11y") return true
        val km = ctx.getSystemService(KeyguardManager::class.java)
        return km == null || !km.isKeyguardLocked
    }

    private fun startIntro(ctx: Context, kind: Int, key: Long, popIn: Boolean) {
        introPending = false
        if (kind == KIND_REAL) IslandPrefs.setIntroFor(ctx, key)
        Log.i(TAG, "intro (kind=$kind)")
        if (popIn) view?.popIn()
        main.removeCallbacks(introOpen)
        main.postDelayed(introOpen, if (popIn) INTRO_DELAY_MS else 0L)
    }

    // ---------------------------------------------------------------- the window
    private fun pickHost(ctx: Context): Host? {
        val s = a11y
        if (s != null) {
            val w = s.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (w != null) return Host("a11y", s, w, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, false)
        }
        if (canOverlay(ctx)) {
            val w = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (w != null) return Host("overlay", ctx, w, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, true)
        }
        return null
    }

    private fun attach(ctx: Context, host: Host, model: IslandView.Model): Boolean {
        val g = computeGeo(ctx, host.overlay)
        if (g == null) {
            Log.i(TAG, "no geometry (landscape?)")
            return false
        }
        var created: IslandView? = null
        var layout: WindowManager.LayoutParams? = null
        try {
            val v = IslandView(host.context)
            v.setModel(model)
            v.setGeo(g)
            wire(v)
            val p = newParams(host.type, windowRect(g, false))
            created = v
            layout = p
            host.manager.addView(v, p)
        } catch (t: Throwable) {
            Log.w(TAG, "could not add the window: $t")
            return false
        }
        view = created
        wm = host.manager
        params = layout
        hostName = host.name
        geo = g
        windowOpen = false
        fading = false
        Log.i(
            TAG,
            "attach host=${host.name} pill=${g.pill.toShortString()} card=${g.card.toShortString()} " +
                "cutout=${g.cutout.toShortString()} density=${g.density}"
        )
        return true
    }

    private fun wire(v: IslandView) {
        v.onTapPill = {
            Log.i(TAG, "tap on the pill")
            openCard(HOLD_MS)
        }
        v.onTapCard = {
            Log.i(TAG, "tap on the card")
            openApp()
            collapseCard()
        }
        v.onSwipeUp = { collapseCard() }
        v.onOutside = { if (windowOpen) collapseCard() }
        v.onCollapsed = { shrinkWindow() }
        v.onError = { t ->
            main.post {
                val a = app
                if (a != null) IslandPrefs.setFailed(a, true)
                Log.e(TAG, "island switched off after an error: $t")
                detach("error")
                a?.let { resyncNotification(it) }
            }
        }
    }

    private fun newParams(type: Int, r: Rect): WindowManager.LayoutParams {
        val p = WindowManager.LayoutParams(
            r.width(), r.height(), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = r.left
        p.y = r.top
        p.title = "ToDoIsland"
        if (Build.VERSION.SDK_INT >= 30) {
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            p.fitInsetsTypes = 0
        } else if (Build.VERSION.SDK_INT >= 28) {
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return p
    }

    /**
     * The window is only as big as it has to be: a transparent window would swallow the touches meant for whatever is
     * under it. Closed it is the pill, open it is the card (a little larger, for the spring overshoot).
     */
    private fun windowRect(g: IslandView.Geo, open: Boolean): Rect {
        val d = g.density
        return if (!open) {
            val pad = (6f * d).toInt()
            Rect(
                floor(g.pill.left).toInt() - pad,
                max(0, floor(g.pill.top).toInt() - pad / 2),
                ceil(g.pill.right).toInt() + pad,
                ceil(g.pill.bottom).toInt() + pad
            )
        } else {
            val pad = (14f * d).toInt()
            Rect(
                floor(g.card.left).toInt() - pad,
                max(0, floor(min(g.card.top, g.pill.top)).toInt() - pad / 2),
                ceil(g.card.right).toInt() + pad,
                ceil(g.card.bottom).toInt() + pad
            )
        }
    }

    private fun applyRect(p: WindowManager.LayoutParams, r: Rect) {
        p.x = r.left
        p.y = r.top
        p.width = r.width()
        p.height = r.height()
    }

    private fun relayout(open: Boolean) {
        val v = view ?: return
        val w = wm ?: return
        val p = params ?: return
        val g = geo ?: return
        applyRect(p, windowRect(g, open))
        try {
            w.updateViewLayout(v, p)
            windowOpen = open
        } catch (t: Throwable) {
            Log.w(TAG, "could not resize the window: $t")
        }
    }

    private fun shrinkWindow() {
        if (view == null) return
        relayout(false)
        Log.i(TAG, "closed")
    }

    // ---------------------------------------------------------------- opening and closing the card
    private fun openCard(hold: Long) {
        val v = view ?: return
        main.removeCallbacks(closeCard)
        if (!windowOpen) relayout(true)
        v.expand()
        Log.i(TAG, "open")
        if (hold > 0L) main.postDelayed(closeCard, hold)
    }

    private fun collapseCard() {
        main.removeCallbacks(closeCard)
        view?.collapse()
    }

    private fun openApp() {
        val ctx = app ?: return
        try {
            val i = Intent(ctx, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_FOCUS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ctx.startActivity(i)
        } catch (t: Throwable) {
            Log.w(TAG, "could not open the app: $t")
        }
    }

    private fun hide() {
        val v = view ?: return
        if (fading) return
        fading = true
        main.removeCallbacks(closeCard)
        main.removeCallbacks(introOpen)
        Log.i(TAG, "fade out")
        v.fadeOut { detach("faded out") }
    }

    private fun detach(reason: String) {
        main.removeCallbacks(closeCard)
        main.removeCallbacks(introOpen)
        val v = view
        if (v != null) {
            try {
                v.stopAnimations()
                wm?.removeViewImmediate(v)
            } catch (t: Throwable) {
                Log.w(TAG, "could not remove the window: $t")
            }
        }
        view = null
        wm = null
        params = null
        geo = null
        fading = false
        windowOpen = false
        shownKind = 0
        shownKey = 0L
        introPending = false
        Log.i(TAG, "detached: $reason")
    }

    // ---------------------------------------------------------------- geometry
    /** Looks again at the screen (rotation, display size, font size...) and moves the island if something changed. */
    private fun checkGeometry(ctx: Context) {
        val v = view ?: return
        val g = computeGeo(ctx, hostName == "overlay")
        if (g == null) {
            detach("no geometry")
            return
        }
        val old = geo
        if (old == null || !sameGeo(old, g)) {
            geo = g
            v.setGeo(g)
            relayout(windowOpen)
            Log.i(TAG, "geometry changed: pill=${g.pill.toShortString()} card=${g.card.toShortString()}")
        }
    }

    private fun sameGeo(a: IslandView.Geo, b: IslandView.Geo): Boolean =
        abs(a.density - b.density) < 0.001f && same(a.pill, b.pill) && same(a.card, b.card) && same(a.cutout, b.cutout)

    private fun same(a: RectF, b: RectF): Boolean =
        abs(a.left - b.left) < 0.5f && abs(a.top - b.top) < 0.5f && abs(a.right - b.right) < 0.5f && abs(a.bottom - b.bottom) < 0.5f

    /**
     * Where everything goes, in screen pixels: the camera hole (read from the display, or an imaginary one at the top
     * centre on phones without), the pill around it and the card that grows out of it. Null in landscape.
     */
    private fun computeGeo(ctx: Context, overlayHost: Boolean): IslandView.Geo? {
        val dm = ctx.getSystemService(DisplayManager::class.java) ?: return null
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
        val rotation = display.rotation
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) return null
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        val sw = metrics.widthPixels.toFloat()
        val sh = metrics.heightPixels.toFloat()
        if (sw <= 0f || sw > sh) return null
        val d = metrics.density

        var cut: RectF? = null
        if (Build.VERSION.SDK_INT >= 29) {
            val r = display.cutout?.boundingRectTop
            if (r != null && !r.isEmpty) cut = RectF(r)
        }
        val hole = cut ?: RectF(sw / 2f - 12f * d, 8f * d, sw / 2f + 12f * d, 8f * d + 24f * d)

        val res = ctx.resources
        val sbId = res.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (sbId > 0) res.getDimensionPixelSize(sbId).toFloat() else 24f * d

        val pillH = (hole.height() + 12f * d).coerceIn(30f * d, 38f * d)
        val top = max(0f, hole.centerY() - pillH / 2f)
        var bottom = top + pillH
        // under the status bar the part of the pill inside the bar can't be tapped: let a strip of it hang below
        if (overlayHost) bottom = max(bottom, statusBar + 8f * d)
        val pillW = max(hole.width() + 2f * 58f * d, 120f * d)
        val pcx = hole.centerX()
        val pill = RectF(pcx - pillW / 2f, top, pcx + pillW / 2f, bottom)

        val margin = 8f * d
        val cardW = min(sw - 2f * margin, sw * 0.92f)
        val half = cardW / 2f
        val ccx = pcx.coerceIn(half + margin, sw - half - margin)
        val card = RectF(ccx - half, top, ccx + half, top + IslandMetrics.cardHeight(cardW))
        return IslandView.Geo(card, pill, hole, d, hole.centerY())
    }
}
