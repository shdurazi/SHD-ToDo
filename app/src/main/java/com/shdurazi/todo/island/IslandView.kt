package com.shdurazi.todo.island

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.shdurazi.todo.R
import com.shdurazi.todo.core.IslandRing
import com.shdurazi.todo.ui.Res
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The island: a black pill around the camera that springs open into a card with the dial of the day.
 * Everything is drawn by hand. The numbers in the "reference" block come from the Anchor picture
 * (a card 1008 units wide, a ring whose centre sits below the card); [u] scales them to the real card.
 *
 * Coordinates handed in by the controller are screen coordinates; the view shifts them by the position of its window.
 */
class IslandView(context: Context) : View(context) {

    class Model(
        val title: String,
        val iconKey: String,
        val colorKey: String,
        val startedAt: Long,
        val endsAt: Long,
        val segs: List<IslandRing.Seg>,
        val degPerMin: Float,
        val done: Boolean
    )

    /** Screen-space geometry: the open card, the closed pill, the camera hole, the density and the line the pill's content sits on. */
    class Geo(val card: RectF, val pill: RectF, val cutout: RectF, val density: Float, val cy: Float)

    var onTapPill: (() -> Unit)? = null
    var onTapCard: (() -> Unit)? = null
    var onOutside: (() -> Unit)? = null
    var onSwipeUp: (() -> Unit)? = null
    var onCollapsed: (() -> Unit)? = null

    /** Called (once) if drawing fails, so that the controller can take the island down instead of crashing the app. */
    var onError: ((Throwable) -> Unit)? = null
    private var broken = false

    private var model: Model? = null
    private var geo: Geo? = null
    private val loc = IntArray(2)

    /** 0 = pill, 1 = card; goes a little past 1 while the card springs open. */
    private var morph = 0f

    /** 0..1: the dial turning into place after the card opened. */
    private var sweep = 1f

    /** 0..1: the pill popping in / fading out. */
    private var appear = 1f
    private var morphAnim: ValueAnimator? = null
    private var sweepAnim: ValueAnimator? = null
    private var appearAnim: ValueAnimator? = null
    var expandedTarget = false
        private set

    // ---------------------------------------------------------------- paints and helpers (no allocation while drawing)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val washPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val pillTimePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val body = Path()
    private val sector = Path()
    private val disc = Path()
    private val radii = FloatArray(8)
    private val rect = RectF()
    private val oval = RectF()
    private val glowAlpha = FloatArray(GLOW_LAYERS + 1)
    private val glyphs = HashMap<String, Drawable?>()

    private var shaderAccent = 0
    private var shaderU = -1f
    private var shaderCx = 0f
    private var shaderCy = 0f
    private var washShader: RadialGradient? = null

    private var titleSource = ""
    private var titleWidth = -1f
    private var titleShown = ""
    private var lastSecond = -1L
    private var countdownText = ""
    private var lastMinute = -1L
    private var remainingText = ""

    private var downX = 0f
    private var downY = 0f

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        val medium = ResourcesCompat.getFont(context, R.font.inter_medium) ?: Typeface.DEFAULT
        val semi = ResourcesCompat.getFont(context, R.font.inter_semibold) ?: Typeface.DEFAULT_BOLD
        titlePaint.typeface = medium
        timePaint.typeface = semi
        pillTimePaint.typeface = semi
        timePaint.fontFeatureSettings = "tnum"
        pillTimePaint.fontFeatureSettings = "tnum"
        titlePaint.letterSpacing = -0.005f
        timePaint.letterSpacing = -0.012f
        pillTimePaint.letterSpacing = 0f
    }

    // ---------------------------------------------------------------- public API
    fun setModel(m: Model) {
        model = m
        invalidate()
    }

    fun setGeo(g: Geo) {
        geo = g
        invalidate()
    }

    fun popIn() {
        appearAnim?.cancel()
        appear = 0f
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 380
        a.interpolator = OvershootInterpolator(1.6f)
        a.addUpdateListener {
            appear = (it.animatedValue as Float)
            invalidate()
        }
        appearAnim = a
        a.start()
    }

    /** Fades the whole island out and then calls [end]. */
    fun fadeOut(end: () -> Unit) {
        appearAnim?.cancel()
        val a = ValueAnimator.ofFloat(appear, 0f)
        a.duration = 240
        a.interpolator = AccelerateDecelerateInterpolator()
        a.addUpdateListener {
            appear = (it.animatedValue as Float).coerceIn(0f, 1f)
            invalidate()
        }
        a.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) end()
            }
        })
        appearAnim = a
        a.start()
    }

    fun setAppearInstant() {
        appearAnim?.cancel()
        appear = 1f
        invalidate()
    }

    fun expand() {
        if (expandedTarget && morph >= 0.99f && (morphAnim?.isRunning != true)) return
        expandedTarget = true
        morphAnim?.cancel()
        val a = ValueAnimator.ofFloat(morph, 1f)
        a.duration = 560
        a.interpolator = OvershootInterpolator(1.0f)
        a.addUpdateListener {
            morph = it.animatedValue as Float
            invalidate()
        }
        morphAnim = a
        a.start()
        // the dial turns into place while the card opens
        sweepAnim?.cancel()
        sweep = 0f
        val s = ValueAnimator.ofFloat(0f, 1f)
        s.duration = 1200
        s.startDelay = 160
        s.interpolator = DecelerateInterpolator(2.4f)
        s.addUpdateListener {
            sweep = it.animatedValue as Float
            invalidate()
        }
        sweepAnim = s
        s.start()
    }

    fun collapse() {
        if (!expandedTarget && morph <= 0.001f) return
        expandedTarget = false
        morphAnim?.cancel()
        val a = ValueAnimator.ofFloat(morph, 0f)
        a.duration = 340
        a.interpolator = AccelerateDecelerateInterpolator()
        a.addUpdateListener {
            morph = (it.animatedValue as Float).coerceAtLeast(0f)
            invalidate()
        }
        a.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled && !expandedTarget) {
                    morph = 0f
                    onCollapsed?.invoke()
                }
            }
        })
        morphAnim = a
        a.start()
    }

    fun stopAnimations() {
        morphAnim?.cancel()
        sweepAnim?.cancel()
        appearAnim?.cancel()
    }

    // ---------------------------------------------------------------- touch
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val g = geo
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                val d = g?.density ?: resources.displayMetrics.density
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                if (dy < -28f * d && abs(dx) < 70f * d) {
                    onSwipeUp?.invoke()
                } else if (dy > 28f * d && abs(dx) < 70f * d && morph <= 0.6f) {
                    onTapPill?.invoke()
                } else if (abs(dx) < 16f * d && abs(dy) < 16f * d) {
                    if (morph > 0.6f) onTapCard?.invoke() else onTapPill?.invoke()
                }
                return true
            }
            MotionEvent.ACTION_OUTSIDE -> {
                onOutside?.invoke()
                return true
            }
        }
        return true
    }

    // ---------------------------------------------------------------- drawing
    override fun onDraw(canvas: Canvas) {
        if (broken) return
        try {
            drawAll(canvas)
        } catch (t: Throwable) {
            broken = true
            Log.e("ToDoIsland", "drawing failed", t)
            onError?.invoke(t)
        }
    }

    private fun drawAll(canvas: Canvas) {
        val m = model ?: return
        val g = geo ?: return
        val now = if (m.done) m.endsAt else System.currentTimeMillis()
        val e = morph
        val ec = e.coerceIn(0f, 1f)
        val u = g.card.width() / REF_W
        val pillH = g.pill.height()

        // the body: pill -> card
        rect.set(
            lerp(g.pill.left, g.card.left, e), lerp(g.pill.top, g.card.top, e),
            lerp(g.pill.right, g.card.right, e), lerp(g.pill.bottom, g.card.bottom, e)
        )
        if (appear != 1f && ec < 0.01f) {
            // pop-in: the pill grows out of its own centre
            val s = 0.55f + 0.45f * appear
            val cx = rect.centerX()
            val cy = rect.centerY()
            rect.set(cx - rect.width() / 2f * s, cy - rect.height() / 2f * s, cx + rect.width() / 2f * s, cy + rect.height() / 2f * s)
        }
        val rt = lerp(pillH / 2f, TOP_CORNER * u, ec)
        val rb = lerp(pillH / 2f, BOTTOM_CORNER * u, ec)
        radii[0] = rt; radii[1] = rt; radii[2] = rt; radii[3] = rt
        radii[4] = rb; radii[5] = rb; radii[6] = rb; radii[7] = rb
        body.rewind()
        body.addRoundRect(rect, radii, Path.Direction.CW)

        val a = appear.coerceIn(0f, 1f)
        // the window may sit a frame behind the layout the controller asked for: draw where it really is
        getLocationOnScreen(loc)
        canvas.save()
        canvas.translate(-loc[0].toFloat(), -loc[1].toFloat())
        fill.color = Color.argb((255f * a).toInt(), 0, 0, 0)
        canvas.drawPath(body, fill)

        val ce = smooth(0.38f, 0.82f, e)
        val cc = (1f - smooth(0f, 0.3f, e)) * a
        if (ce > 0.01f) {
            canvas.save()
            canvas.clipPath(body)
            drawCard(canvas, g, m, now, ce * a)
            canvas.restore()
        }
        if (cc > 0.01f) drawCompact(canvas, g, m, now, cc)
        canvas.restore()

        // keep animating while the card is open (the glow breathes); the pill is redrawn by the controller every second
        if (morph > 0.001f || morphAnim?.isRunning == true || sweepAnim?.isRunning == true || appearAnim?.isRunning == true) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawCard(c: Canvas, g: Geo, m: Model, now: Long, alpha: Float) {
        val card = g.card
        val u = card.width() / REF_W
        val cx = card.left + CX * u
        val cy = card.top + CY * u
        val accent = IslandStyle.accent(m.colorKey)
        val layer = alpha < 0.995f
        if (layer) c.saveLayerAlpha(card.left, card.top, card.right, card.bottom, (alpha * 255f).toInt())
        ensureShaders(accent, u, cx, cy)

        // the disc inside the ring, with a green wash that gets stronger towards the ring
        c.drawCircle(cx, cy, R_IN * u, discPaint)
        // the track
        stroke.color = TRACK
        stroke.strokeWidth = (R_OUT - R_IN) * u
        c.drawCircle(cx, cy, (R_IN + R_OUT) / 2f * u, stroke)

        // blocks that are not running
        val rot = (1f - sweep) * 48f
        var active: IslandRing.Seg? = null
        for (s in m.segs) {
            if (s.active) {
                active = s
                continue
            }
            var a0 = IslandRing.angle(s.startMs, now, m.degPerMin) + rot + GAP_DEG
            var a1 = IslandRing.angle(s.endMs, now, m.degPerMin) + rot - GAP_DEG
            if (a1 < -80f || a0 > 80f) continue
            a0 = max(a0, -110f)
            a1 = min(a1, 110f)
            if (a1 - a0 < MIN_ARC) {
                val mid = (a0 + a1) / 2f
                a0 = mid - MIN_ARC / 2f
                a1 = mid + MIN_ARC / 2f
            }
            fill.color = IslandStyle.dim(IslandStyle.accent(s.colorKey))
            buildSector(sector, cx, cy, PILL_IN * u, PILL_OUT * u, a0, a1, PILL_CORNER * u)
            c.drawPath(sector, fill)
            drawGlyphOnRing(c, s.iconKey, cx, cy, (PILL_IN + PILL_OUT) / 2f * u, (a0 + a1) / 2f, 46f * u, 52)
        }

        // the running block with its glow
        val act = active
        if (act != null) {
            // the bright part is what is left of the block: its start stays at "now" and its end moves towards it
            var a0 = max(IslandRing.angle(act.startMs, now, m.degPerMin), IslandRing.NOW_DEG) + rot + GAP_DEG
            var a1 = IslandRing.angle(act.endMs, now, m.degPerMin) + rot - GAP_DEG
            a0 = max(a0, -110f)
            a1 = min(a1, 110f)
            // while the dial turns in, the block also stretches out along the ring
            a1 = a0 + (a1 - a0) * (0.4f + 0.6f * sweep)
            if (a1 - a0 < MIN_ARC) {
                val mid = (a0 + a1) / 2f
                a0 = mid - MIN_ARC / 2f
                a1 = mid + MIN_ARC / 2f
            }
            val breathe = 0.86f + 0.14f * sin((SystemClock.uptimeMillis() % 2600L) / 2600f * 6.2831855f)
            val amp = breathe * min(1f, 0.35f + sweep)
            drawGlow(c, accent, cx, cy, u, a0, a1, amp)
            fill.color = accent
            buildSector(sector, cx, cy, PILL_IN * u, PILL_OUT * u, a0, a1, PILL_CORNER * u)
            c.drawPath(sector, fill)
            drawGlyphOnRing(c, act.iconKey, cx, cy, (PILL_IN + PILL_OUT) / 2f * u, (a0 + a1) / 2f, 54f * u, 255)
        }

        // texts
        titlePaint.textSize = 31f * u
        timePaint.textSize = 46f * u
        val titleX = card.left + 74f * u
        val maxW = max(g.cutout.left - 10f * g.density - titleX, 60f * g.density)
        if (m.title != titleSource || abs(maxW - titleWidth) > 0.5f) {
            titleSource = m.title
            titleWidth = maxW
            titleShown = TextUtils.ellipsize(m.title, titlePaint, maxW, TextUtils.TruncateAt.END).toString()
        }
        titlePaint.color = IslandStyle.lighten(accent, 0.25f)
        c.drawText(titleShown, titleX, card.top + 81.5f * u, titlePaint)
        val minute = (m.endsAt - now).coerceAtLeast(0L) / 60_000L
        if (minute != lastMinute || remainingText.isEmpty()) {
            lastMinute = minute
            remainingText = IslandRing.remainingLabel(m.endsAt - now)
        }
        timePaint.color = Color.WHITE
        c.drawText(remainingText, titleX, card.top + 139f * u, timePaint)
        if (layer) c.restore()
    }

    private fun ensureShaders(accent: Int, u: Float, cx: Float, cy: Float) {
        if (accent == shaderAccent && u == shaderU && cx == shaderCx && cy == shaderCy && washShader != null) return
        shaderAccent = accent
        shaderU = u
        shaderCx = cx
        shaderCy = cy
        val colors = intArrayOf(
            IslandStyle.withAlpha(accent, 0.00f), IslandStyle.withAlpha(accent, 0.10f), IslandStyle.withAlpha(accent, 0.13f),
            IslandStyle.withAlpha(accent, 0.17f), IslandStyle.withAlpha(accent, 0.245f), IslandStyle.withAlpha(accent, 0.32f),
            IslandStyle.withAlpha(accent, 0.30f)
        )
        val stops = floatArrayOf(0f, 0.6f, 0.67f, 0.78f, 0.88f, 0.94f, 1f)
        discPaint.shader = RadialGradient(cx, cy, R_IN * u, colors, stops, Shader.TileMode.CLAMP)
        washShader = RadialGradient(
            0f, 0f, WASH_R * u,
            intArrayOf(IslandStyle.withAlpha(accent, WASH_A), IslandStyle.withAlpha(accent, 0f)), null, Shader.TileMode.CLAMP
        )
        washPaint.shader = washShader
    }

    /** A soft halo around the running block: stacked, slightly larger copies whose alphas add up to an exponential falloff. */
    private fun drawGlow(c: Canvas, accent: Int, cx: Float, cy: Float, u: Float, a0: Float, a1: Float, amp: Float) {
        val midR = (PILL_IN + PILL_OUT) / 2f * u
        // a broad, faint wash that leans towards the start of the block
        val washAt = a0 + (a1 - a0) * 0.4f
        val wx = cx + midR * sin(Math.toRadians(washAt.toDouble())).toFloat()
        val wy = cy - midR * cos(Math.toRadians(washAt.toDouble())).toFloat()
        c.save()
        c.translate(wx, wy)
        washPaint.alpha = (255f * min(1f, amp)).toInt()
        c.drawCircle(0f, 0f, WASH_R * u, washPaint)
        c.restore()

        // never inside the disc: the disc has its own gradient
        disc.rewind()
        disc.addCircle(cx, cy, (PILL_IN - 4f) * u, Path.Direction.CW)
        c.save()
        c.clipOutPath(disc)
        var prev = 0f
        for (i in GLOW_LAYERS downTo 1) {
            val target = GLOW_A * amp * exp(-(GLOW_REACH * (i - 1) / GLOW_LAYERS) / GLOW_LEN)
            glowAlpha[i] = (1f - (1f - target) / (1f - prev)).coerceAtLeast(0f)
            prev = target
        }
        for (i in GLOW_LAYERS downTo 1) {
            val grow = GLOW_REACH * i / GLOW_LAYERS * u
            val da = Math.toDegrees((grow / midR).toDouble()).toFloat()
            buildSector(sector, cx, cy, max(PILL_IN * u - grow, 1f), PILL_OUT * u + grow, a0 - da, a1 + da, PILL_CORNER * u + grow)
            fill.color = IslandStyle.withAlpha(accent, glowAlpha[i])
            c.drawPath(sector, fill)
        }
        c.restore()
    }

    private fun drawGlyphOnRing(c: Canvas, key: String, cx: Float, cy: Float, r: Float, angleDeg: Float, size: Float, alpha: Int) {
        val d = glyph(key) ?: return
        val t = Math.toRadians(angleDeg.toDouble())
        val x = cx + r * sin(t).toFloat()
        val y = cy - r * cos(t).toFloat()
        val half = (size / 2f).toInt().coerceAtLeast(2)
        d.setBounds(-half, -half, half, half)
        d.alpha = alpha
        c.save()
        c.translate(x, y)
        c.rotate(angleDeg)
        d.draw(c)
        c.restore()
    }

    private fun glyph(key: String): Drawable? = glyphs.getOrPut(key) {
        try {
            ContextCompat.getDrawable(context, Res.glyph(key))?.mutate()?.also { it.setTint(Color.WHITE) }
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- the pill
    private fun drawCompact(c: Canvas, g: Geo, m: Model, now: Long, alpha: Float) {
        val p = g.pill
        val d = g.density
        val accent = IslandStyle.accent(m.colorKey)
        val a255 = (alpha * 255f).toInt()
        val ringD = min(p.height() - 12f * d, 24f * d)
        val leftPod = g.cutout.left - p.left
        val rightPod = p.right - g.cutout.right
        val textW = 54f * d
        val ringCx: Float
        val textCx: Float
        if (leftPod >= ringD + 14f * d && rightPod >= textW) {
            ringCx = p.left + leftPod / 2f
            textCx = g.cutout.right + rightPod / 2f
        } else if (rightPod >= ringD + textW + 14f * d) {
            ringCx = g.cutout.right + 8f * d + ringD / 2f
            textCx = ringCx + ringD / 2f + 6f * d + textW / 2f
        } else {
            ringCx = g.cutout.left - 8f * d - ringD / 2f
            textCx = ringCx - ringD / 2f - 6f * d - textW / 2f
        }
        val cy = g.cy

        // ring: what is left of the session
        val sw = 2.4f * d
        oval.set(ringCx - ringD / 2f + sw / 2f, cy - ringD / 2f + sw / 2f, ringCx + ringD / 2f - sw / 2f, cy + ringD / 2f - sw / 2f)
        stroke.strokeWidth = sw
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.color = IslandStyle.withAlpha(accent, 0.28f * alpha)
        c.drawArc(oval, 0f, 360f, false, stroke)
        val left = IslandRing.fractionLeft(m.startedAt, m.endsAt, now)
        stroke.color = IslandStyle.withAlpha(accent, alpha)
        if (left > 0.002f) c.drawArc(oval, -90f, 360f * left, false, stroke)
        stroke.strokeCap = Paint.Cap.BUTT

        val gl = glyph(m.iconKey)
        if (gl != null) {
            val half = (ringD * 0.27f).toInt().coerceAtLeast(2)
            gl.setBounds((ringCx - half).toInt(), (cy - half).toInt(), (ringCx + half).toInt(), (cy + half).toInt())
            gl.alpha = a255
            gl.draw(c)
        }

        // the time
        val second = (m.endsAt - now).coerceAtLeast(0L) / 1000L
        if (second != lastSecond || countdownText.isEmpty()) {
            lastSecond = second
            countdownText = if (m.done) "Done" else IslandRing.countdown(m.endsAt - now)
        }
        pillTimePaint.textSize = 13.5f * d
        pillTimePaint.color = IslandStyle.withAlpha(IslandStyle.lighten(accent, 0.2f), alpha)
        val tw = pillTimePaint.measureText(countdownText)
        val fm = pillTimePaint.fontMetrics
        c.drawText(countdownText, textCx - tw / 2f, cy - (fm.ascent + fm.descent) / 2f, pillTimePaint)
    }

    // ---------------------------------------------------------------- geometry of the rounded arcs
    /**
     * An annular sector (a bent bar) with rounded corners, angles in degrees clockwise from the top.
     * Each corner is a circle that touches the straight edge and the arc, see the browser prototype.
     */
    private fun buildSector(p: Path, cx: Float, cy: Float, r1: Float, r2: Float, a0: Float, a1: Float, cornerIn: Float) {
        p.rewind()
        var rc = min(cornerIn, (r2 - r1) / 2f - 0.01f)
        if (rc < 0.5f) rc = 0.5f
        var dO = 0f
        var dI = 0f
        while (true) {
            dO = Math.toDegrees(asin((rc / (r2 - rc)).toDouble().coerceIn(-1.0, 1.0))).toFloat()
            dI = Math.toDegrees(asin((rc / (r1 + rc)).toDouble().coerceIn(-1.0, 1.0))).toFloat()
            if (a1 - a0 >= 2f * max(dO, dI) + 1f || rc <= 1f) break
            rc *= 0.8f
        }
        val ro = r2 - rc
        val ri = r1 + rc
        val cosO = cos(Math.toRadians(dO.toDouble())).toFloat()
        val cosI = cos(Math.toRadians(dI.toDouble())).toFloat()

        // outer start corner: from the start line onto the outer arc
        val p1x = px(cx, ro * cosO, a0); val p1y = py(cy, ro * cosO, a0)
        p.moveTo(p1x, p1y)
        arcCw(p, px(cx, ro, a0 + dO), py(cy, ro, a0 + dO), rc, p1x, p1y, px(cx, r2, a0 + dO), py(cy, r2, a0 + dO))
        // outer arc
        oval.set(cx - r2, cy - r2, cx + r2, cy + r2)
        p.arcTo(oval, a0 + dO - 90f, (a1 - dO) - (a0 + dO), false)
        // outer end corner
        arcCw(p, px(cx, ro, a1 - dO), py(cy, ro, a1 - dO), rc, px(cx, r2, a1 - dO), py(cy, r2, a1 - dO), px(cx, ro * cosO, a1), py(cy, ro * cosO, a1))
        // the end line
        val pieX = px(cx, ri * cosI, a1)
        val pieY = py(cy, ri * cosI, a1)
        p.lineTo(pieX, pieY)
        // inner end corner
        arcCw(p, px(cx, ri, a1 - dI), py(cy, ri, a1 - dI), rc, pieX, pieY, px(cx, r1, a1 - dI), py(cy, r1, a1 - dI))
        // inner arc, back the other way round
        oval.set(cx - r1, cy - r1, cx + r1, cy + r1)
        p.arcTo(oval, a1 - dI - 90f, -((a1 - dI) - (a0 + dI)), false)
        // inner start corner
        arcCw(p, px(cx, ri, a0 + dI), py(cy, ri, a0 + dI), rc, px(cx, r1, a0 + dI), py(cy, r1, a0 + dI), px(cx, ri * cosI, a0), py(cy, ri * cosI, a0))
        p.close()
    }

    private fun px(cx: Float, r: Float, deg: Float): Float = cx + r * sin(Math.toRadians(deg.toDouble())).toFloat()
    private fun py(cy: Float, r: Float, deg: Float): Float = cy - r * cos(Math.toRadians(deg.toDouble())).toFloat()

    /** A clockwise arc of radius [r] around ([ccx], [ccy]) from the first point to the second. */
    private fun arcCw(p: Path, ccx: Float, ccy: Float, r: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
        val s = Math.toDegrees(atan2((y1 - ccy).toDouble(), (x1 - ccx).toDouble())).toFloat()
        val e = Math.toDegrees(atan2((y2 - ccy).toDouble(), (x2 - ccx).toDouble())).toFloat()
        var sweep = e - s
        while (sweep < 0f) sweep += 360f
        if (sweep > 200f) sweep = 0f // numerical noise on a (nearly) empty corner
        oval.set(ccx - r, ccy - r, ccx + r, ccy + r)
        p.arcTo(oval, s, sweep, false)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private companion object {
        // the Anchor picture, in its own pixels (the card is 1008 wide)
        const val REF_W = 1008f
        const val CX = 504f
        const val CY = 613f
        const val R_IN = 374f
        const val R_OUT = 448f
        const val PILL_IN = 378f
        const val PILL_OUT = 447f
        const val PILL_CORNER = 24f
        const val TOP_CORNER = 110f
        const val BOTTOM_CORNER = 125f
        const val GLOW_LAYERS = 24
        const val GLOW_REACH = 110f
        const val GLOW_LEN = 32f
        const val GLOW_A = 0.27f
        const val WASH_R = 210f
        const val WASH_A = 0.11f
        const val GAP_DEG = 0.55f
        const val MIN_ARC = 8f
        val TRACK = Color.rgb(21, 19, 20)
    }
}

/** Size of the open card relative to its width: the Anchor picture, 1008 x 392. */
object IslandMetrics {
    const val CARD_ASPECT = 392f / 1008f

    fun cardHeight(width: Float): Float = width * CARD_ASPECT
}
