package com.kf7mxe.inglenook

import com.lightningkite.kiteui.views.canvas.DrawingContext2D
import com.lightningkite.kiteui.views.canvas.clear
import com.lightningkite.kiteui.views.canvas.fillPaint
import com.lightningkite.kiteui.views.canvas.height
import com.lightningkite.kiteui.views.canvas.strokePaint
import com.lightningkite.kiteui.views.canvas.width
import com.lightningkite.kiteui.views.direct.CanvasDelegate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Cozy reading pattern (books, headphones, fireplace, armchair, lamp, mug)
 * as a KiteUI CanvasDelegate. Port of the JS "Cozy Reading Pattern Generator".
 *
 * Icons are defined as SVG path strings in a 48x48 box, parsed once into
 * polylines, and drawn with moveTo/lineTo/stroke only.
 */

// ---------- icon set ----------

enum class CozyIcon { OPEN_BOOK, STACK, PHONES, FIRE, NOOK, LAMP, MUG }
enum class TiltMode { RANDOM, ALTERNATING, SAME }

private data class Pt(val x: Double, val y: Double)
private class IconShape(val polys: List<List<Pt>>)

/** rounded-rect as an SVG path string */
private fun rr(x: Double, y: Double, w: Double, h: Double, r: Double): String =
    "M${x + r} ${y}H${x + w - r}A${r} ${r} 0 0 1 ${x + w} ${y + r}V${y + h - r}" +
            "A${r} ${r} 0 0 1 ${x + w - r} ${y + h}H${x + r}A${r} ${r} 0 0 1 ${x} ${y + h - r}" +
            "V${y + r}A${r} ${r} 0 0 1 ${x + r} ${y}Z"

private val ICON_PATHS: Map<CozyIcon, List<String>> = mapOf(
    CozyIcon.OPEN_BOOK to listOf(
        "M24 14C19 10 11 9 5 11V38C11 36 19 37 24 41C29 37 37 36 43 38V11C37 9 29 10 24 14ZM24 14V41",
        "M10 17c4-1 7-.5 10 1M10 23c4-1 7-.5 10 1M10 29c4-1 7-.5 10 1M38 17c-4-1-7-.5-10 1M38 23c-4-1-7-.5-10 1M38 29c-4-1-7-.5-10 1"
    ),
    CozyIcon.STACK to listOf(
        rr(6.0, 32.0, 36.0, 9.0, 1.5),
        rr(10.0, 22.0, 30.0, 10.0, 1.5),
        rr(8.0, 13.0, 28.0, 9.0, 1.5),
        "M12 32v9M15 32v9M33 22v10M36 22v10M14 17.5h10"
    ),
    CozyIcon.PHONES to listOf(
        "M9 31V25a15 15 0 0 1 30 0v6",
        rr(5.0, 28.0, 10.0, 14.0, 4.0),
        rr(33.0, 28.0, 10.0, 14.0, 4.0),
        rr(18.0, 27.0, 12.0, 15.0, 1.5),
        "M24 27v15M20.5 32h1.5M20.5 36h1.5"
    ),
    CozyIcon.FIRE to listOf(
        rr(4.0, 9.0, 40.0, 5.0, 1.5),
        "M8 14v28M40 14v28M4 42h40M14 42V29a10 10 0 0 1 20 0v13",
        "M24 39c-5 0-7-4-5-8 1-2 3-3 3-6 3 2 6 5 6 9 0 3-1 5-4 5z",
        "M24 39c-2 0-3-2-2-4 1 1 2 1 2 0 1 1 2 2 2 3 0 1-1 1-2 1z"
    ),
    CozyIcon.NOOK to listOf(
        "M15 23V14a3 3 0 0 1 3-3h12a3 3 0 0 1 3 3v9",
        rr(5.0, 23.0, 10.0, 16.0, 4.0),
        rr(33.0, 23.0, 10.0, 16.0, 4.0),
        rr(13.0, 29.0, 22.0, 10.0, 2.0),
        "M10 39v5M38 39v5M24 15l3 3-3 3-3-3z"
    ),
    CozyIcon.LAMP to listOf(
        "M16 8h16l5 15H11z",
        "M24 23v18M16 42h16M18 41h12",
        "M6 14l3 2M42 14l-3 2M24 3v2"
    ),
    CozyIcon.MUG to listOf(
        "M9 19h22v14a8 8 0 0 1-8 8h-6a8 8 0 0 1-8-8z",
        "M31 22h3a5 5 0 0 1 0 10h-3",
        "M15 8c-2 3 2 4 0 8M20 8c-2 3 2 4 0 8M25 8c-2 3 2 4 0 8",
        "M6 43h28"
    )
)

// ---------- tiny SVG path parser / flattener ----------

private fun tokenizePath(d: String): List<Any> {
    val tokens = ArrayList<Any>()
    var i = 0
    while (i < d.length) {
        val c = d[i]
        when {
            c.isLetter() -> { tokens.add(c); i++ }
            c == ',' || c == ' ' || c == '\n' || c == '\t' -> i++
            else -> {
                val start = i
                if (c == '-' || c == '+') i++
                var dot = false
                while (i < d.length) {
                    val ch = d[i]
                    if (ch.isDigit()) i++
                    else if (ch == '.' && !dot) { dot = true; i++ }
                    else break
                }
                if (i == start) { i++ } else tokens.add(d.substring(start, i).toDouble())
            }
        }
    }
    return tokens
}

private fun flattenArc(
    x1: Double, y1: Double, rxIn: Double, ryIn: Double, phiDeg: Double,
    largeArc: Boolean, sweep: Boolean, x2: Double, y2: Double
): List<Pt> {
    if (x1 == x2 && y1 == y2) return emptyList()
    var rx = abs(rxIn); var ry = abs(ryIn)
    if (rx == 0.0 || ry == 0.0) return listOf(Pt(x2, y2))
    val phi = phiDeg * PI / 180.0
    val cosP = cos(phi); val sinP = sin(phi)
    val dx = (x1 - x2) / 2; val dy = (y1 - y2) / 2
    val x1p = cosP * dx + sinP * dy
    val y1p = -sinP * dx + cosP * dy
    val lam = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if (lam > 1) { val s = sqrt(lam); rx *= s; ry *= s }
    val num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    var co = sqrt(max(0.0, num / den))
    if (largeArc == sweep) co = -co
    val cxp = co * rx * y1p / ry
    val cyp = -co * ry * x1p / rx
    val cx = cosP * cxp - sinP * cyp + (x1 + x2) / 2
    val cy = sinP * cxp + cosP * cyp + (y1 + y2) / 2
    fun ang(ux: Double, uy: Double, vx: Double, vy: Double) = atan2(ux * vy - uy * vx, ux * vx + uy * vy)
    val th1 = atan2((y1p - cyp) / ry, (x1p - cxp) / rx)
    var dth = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if (!sweep && dth > 0) dth -= 2 * PI else if (sweep && dth < 0) dth += 2 * PI
    val steps = max(4, ceil(abs(dth) / (PI / 12)).toInt())
    val out = ArrayList<Pt>()
    for (i in 1..steps) {
        if (i == steps) { out.add(Pt(x2, y2)); break }
        val th = th1 + dth * i / steps
        out.add(Pt(
            cosP * rx * cos(th) - sinP * ry * sin(th) + cx,
            sinP * rx * cos(th) + cosP * ry * sin(th) + cy
        ))
    }
    return out
}

private fun parseSvgPath(d: String): List<List<Pt>> {
    val tokens = tokenizePath(d)
    val polys = ArrayList<MutableList<Pt>>()
    var cur: MutableList<Pt>? = null
    var idx = 0
    var cmd = ' '
    var cx = 0.0; var cy = 0.0
    var sx = 0.0; var sy = 0.0

    fun num(): Double = tokens[idx++] as Double
    fun ensureCur(): MutableList<Pt> {
        val c = cur ?: ArrayList<Pt>().also { it.add(Pt(cx, cy)); polys.add(it); cur = it }
        return c
    }

    while (idx < tokens.size) {
        val t = tokens[idx]
        if (t is Char) {
            cmd = t; idx++
            if (cmd == 'z' || cmd == 'Z') {
                cur?.add(Pt(sx, sy))
                cx = sx; cy = sy
                cur = null
                continue
            }
        } else if (cmd == 'z' || cmd == 'Z' || cmd == ' ') {
            idx++; continue // stray number, skip
        }
        val rel = cmd.isLowerCase()
        when (cmd.uppercaseChar()) {
            'M' -> {
                var x = num(); var y = num()
                if (rel) { x += cx; y += cy }
                cx = x; cy = y; sx = x; sy = y
                cur = ArrayList<Pt>().also { it.add(Pt(x, y)); polys.add(it) }
                cmd = if (rel) 'l' else 'L'
            }
            'L' -> {
                var x = num(); var y = num()
                if (rel) { x += cx; y += cy }
                ensureCur().add(Pt(x, y)); cx = x; cy = y
            }
            'H' -> {
                var x = num(); if (rel) x += cx
                ensureCur().add(Pt(x, cy)); cx = x
            }
            'V' -> {
                var y = num(); if (rel) y += cy
                ensureCur().add(Pt(cx, y)); cy = y
            }
            'C' -> {
                var x1 = num(); var y1 = num(); var x2 = num(); var y2 = num(); var x = num(); var y = num()
                if (rel) { x1 += cx; y1 += cy; x2 += cx; y2 += cy; x += cx; y += cy }
                val list = ensureCur()
                val n = 14
                for (i in 1..n) {
                    val u = i.toDouble() / n; val v = 1 - u
                    list.add(Pt(
                        v * v * v * cx + 3 * v * v * u * x1 + 3 * v * u * u * x2 + u * u * u * x,
                        v * v * v * cy + 3 * v * v * u * y1 + 3 * v * u * u * y2 + u * u * u * y
                    ))
                }
                cx = x; cy = y
            }
            'Q' -> {
                var x1 = num(); var y1 = num(); var x = num(); var y = num()
                if (rel) { x1 += cx; y1 += cy; x += cx; y += cy }
                val list = ensureCur()
                val n = 10
                for (i in 1..n) {
                    val u = i.toDouble() / n; val v = 1 - u
                    list.add(Pt(v * v * cx + 2 * v * u * x1 + u * u * x, v * v * cy + 2 * v * u * y1 + u * u * y))
                }
                cx = x; cy = y
            }
            'A' -> {
                val rx = num(); val ry = num(); val rot = num()
                val fa = num() != 0.0; val fs = num() != 0.0
                var x = num(); var y = num()
                if (rel) { x += cx; y += cy }
                val list = ensureCur()
                list.addAll(flattenArc(cx, cy, rx, ry, rot, fa, fs, x, y))
                cx = x; cy = y
            }
            else -> idx++ // unknown command, skip token
        }
    }
    return polys.filter { it.size >= 2 }
}

private val ICON_SHAPES: Map<CozyIcon, IconShape> by lazy {
    ICON_PATHS.mapValues { (_, paths) -> IconShape(paths.flatMap { parseSvgPath(it) }) }
}

// ---------- layout (hex-ish grid, neighbours avoid matching) ----------

private const val REF_W = 1080.0

class CozyPlacement(val x: Double, val y: Double, val angleDeg: Double, val icon: CozyIcon)

/**
 * Generates placements in REFERENCE units (REF_W wide, height scaled by aspect),
 * so the result can be scaled to any pixel size at draw time.
 */
fun generateCozyPlacements(
    seed: Long,
    aspect: Double,          // width / height of the canvas
    spacing: Double,         // reference-unit gap between icon centres (~60..260)
    tiltAmount: Double,      // degrees (0..40)
    tiltMode: TiltMode,
    randomness: Double,      // 0.0..0.5 position jitter
    icons: Set<CozyIcon>
): List<CozyPlacement> {
    val w = REF_W
    val h = REF_W / aspect
    val r = Random(seed)
    val types = (if (icons.isEmpty()) setOf(CozyIcon.OPEN_BOOK) else icons).toList()
    val rowH = spacing * 0.866

    val placed = HashMap<Pair<Int, Int>, CozyIcon>()
    val used = HashMap<CozyIcon, Int>().also { m -> types.forEach { m[it] = 0 } }
    fun off(rw: Int) = if ((rw and 1) == 1) 0.5 else 0.0

    fun pickType(row: Int, col: Int): CozyIcon {
        val o = off(row); val op = off(row - 1)
        val a = col + o - op
        fun c(v: Double) = v.roundToInt()
        val ring1 = listOf(
            row to (col - 1), (row - 1) to c(a - 0.5), (row - 1) to c(a + 0.5)
        )
        val ring2 = listOf(
            row to (col - 2), (row - 2) to col, (row - 1) to c(a - 1.5), (row - 1) to c(a + 1.5),
            (row - 2) to (col - 1), (row - 2) to (col + 1)
        )
        var best = types[0]
        var bestScore = Double.POSITIVE_INFINITY
        for (t in types) {
            var sc = (used[t] ?: 0) * 0.05 + r.nextDouble()
            ring1.forEach { if (placed[it] == t) sc += 10.0 }
            ring2.forEach { if (placed[it] == t) sc += 2.0 }
            if (sc < bestScore) { bestScore = sc; best = t }
        }
        placed[row to col] = best
        used[best] = (used[best] ?: 0) + 1
        return best
    }

    val out = ArrayList<CozyPlacement>()
    var row = -1
    while (row * rowH < h + spacing) {
        var col = -1
        while (col * spacing < w + spacing) {
            val x = (col + off(row)) * spacing + (r.nextDouble() - 0.5) * spacing * randomness * 2
            val y = row * rowH + (r.nextDouble() - 0.5) * spacing * randomness * 2
            val type = pickType(row, col)
            val rnd = (r.nextDouble() - 0.5) * 2 * tiltAmount
            val angle = when (tiltMode) {
                TiltMode.RANDOM -> rnd
                TiltMode.ALTERNATING -> if (((row + col) and 1) == 1) tiltAmount else -tiltAmount
                TiltMode.SAME -> tiltAmount
            }
            out.add(CozyPlacement(x, y, angle, type))
            col++
        }
        row++
    }
    return out
}

// ---------- drawing ----------

fun DrawingContext2D.drawCozyPattern(
    placements: List<CozyPlacement>,
    iconSize: Double,
    thickness: Double,
    linePaint: com.lightningkite.kiteui.models.Paint,
    backgroundPaint: com.lightningkite.kiteui.models.Paint,
) {
    clear()
    fillPaint = backgroundPaint
    fillRect(0.0, 0.0, width, height)

    val unit = width / REF_W          // reference units -> pixels
    val s = iconSize / 48.0           // icon box (48) -> reference units
    strokePaint = linePaint
    lineWidth = thickness * s * unit

    beginPath()
    for (p in placements) {
        val shape = ICON_SHAPES[p.icon] ?: continue
        val rad = p.angleDeg * PI / 180.0
        val c = cos(rad); val sn = sin(rad)
        for (poly in shape.polys) {
            for ((i, pt) in poly.withIndex()) {
                val px = (pt.x - 24.0) * s
                val py = (pt.y - 24.0) * s
                val rx = px * c - py * sn
                val ry = px * sn + py * c
                val X = (rx + p.x) * unit
                val Y = (ry + p.y) * unit
                if (i == 0) moveTo(X, Y) else lineTo(X, Y)
            }
        }
    }
    stroke()
}

/**
 * Drop-in CanvasDelegate, same pattern as ContourPatternDelegate.
 * Placements are cached and only regenerated when a layout input changes;
 * resizing at the same aspect ratio just rescales.
 */
class CozyPatternDelegate : CanvasDelegate() {
    var seed: Long = Random.nextLong()
        set(value) { field = value; invalidate() }
    /** icon size in reference units (1080 wide), ~30..140 */
    var iconSize: Double = 84.0
        set(value) { field = value; invalidate() }
    /** distance between icons in reference units, ~60..260 */
    var spacing: Double = 150.0
        set(value) { field = value; invalidate() }
    var tiltMode: TiltMode = TiltMode.RANDOM
        set(value) { field = value; invalidate() }
    /** degrees, 0..40 */
    var tiltAmount: Double = 14.0
        set(value) { field = value; invalidate() }
    /** position jitter 0.0..0.5 */
    var randomness: Double = 0.12
        set(value) { field = value; invalidate() }
    /** line weight in icon units, ~1..4 */
    var thickness: Double = 2.5
        set(value) { field = value; invalidate() }
    var icons: Set<CozyIcon> = CozyIcon.values().toSet()
        set(value) { field = value; invalidate() }
    var lineColor: com.lightningkite.kiteui.models.Paint = theme.foreground
        set(value) { field = value; invalidate() }
    var backgroundColor: com.lightningkite.kiteui.models.Paint = theme.background
        set(value) { field = value; invalidate() }

    private data class LayoutKey(
        val seed: Long, val aspect: Double, val spacing: Double, val tilt: Double,
        val mode: TiltMode, val randomness: Double, val icons: Set<CozyIcon>
    )
    private var cachedKey: LayoutKey? = null
    private var cachedPlacements: List<CozyPlacement> = emptyList()

    override fun onPointerDown(id: Int, x: Double, y: Double, width: Double, height: Double): Boolean = false
    override fun onPointerUp(id: Int, x: Double, y: Double, width: Double, height: Double): Boolean = false

    override fun draw(context: DrawingContext2D) = with(context) {
        if (width <= 0 || height <= 0) return@with
        val aspect = width / height
        val key = LayoutKey(seed, aspect, spacing, tiltAmount, tiltMode, randomness, icons)
        if (cachedKey != key) {
            cachedPlacements = generateCozyPlacements(seed, aspect, spacing, tiltAmount, tiltMode, randomness, icons)
            cachedKey = key
        }
        drawCozyPattern(cachedPlacements, iconSize, thickness, lineColor, backgroundColor)
    }
}