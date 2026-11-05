package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.coins

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import io.paritytech.polkadotapp.common.utils.FeatureOption
import io.paritytech.polkadotapp.common.utils.isEnabled

/**
 * Every holding, as real coins, in one of two arrangements.
 *
 * Collapsed, they are the summary strip: a fixed height however many there are, face on with margins while
 * they fit, turning about their vertical axis as they multiply, thinning once fully edge-on. Expanded, the
 * same coins spread into a honeycomb, one cell each however many there are, and the card grows and scrolls.
 * In the grid one coin can be picked out into a band, large enough to read, with the rest faded back.
 *
 * One view, one set of coins. A toggle only moves targets, so the coins fly between the two arrangements
 * rather than one view cutting to another.
 *
 * A [TextureView] rather than a `SurfaceView`: the strip sits inside a rounded, scrolling card, and a surface
 * punches its own hole through the window rather than being composited with what is drawn over and under it.
 *
 * The frame around it is what makes the surface hold still. The card animates *this* view, which is an
 * ordinary one and costs nothing to resize; the texture inside keeps the height the coins were laid out for
 * and is clipped by it. Asking Compose to clip an oversized interop child did not do it — the texture was
 * placed off its own top and the coins vanished under the clip — and a view group clipping its own child is
 * not a thing that can be got wrong.
 *
 * The texture is at most one screen tall and pinned to the visible part of the field, so a frame costs the same
 * however many coins the wallet holds. It is moved to the window each frame was drawn for as that frame is
 * latched, rather than when the page scrolls, so a coin never shows a frame's worth of scroll out of place.
 */
class CoinageCoinsView(context: Context) : FrameLayout(context), TextureView.SurfaceTextureListener {
    private val main = Handler(Looper.getMainLooper())
    private val texture = TextureView(context)

    private var thread: CoinageRenderThread? = null
    private var coins: List<CoinageScene.Coin> = emptyList()
    private var isExpanded = false
    private var areaWidth = 0f
    private var stripHeight = CoinageStripLayout.Options().height
    private var gridBudget = DEFAULT_GRID_BUDGET
    private var focus: CoinageArrangement.Focus? = null
    private var window = 0f
    private var coinSet = CoinageScene.CoinSet.SHIPPED
    private var previousMode: Int? = null

    private val overlay = TextView(context).apply {
        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, OVERLAY_TEXT_SIZE)
        setTextColor(Color.WHITE)
        setBackgroundColor(OVERLAY_BACKGROUND)
    }

    /** On the window rather than in the strip, which is too short for it and draws the coins over it. */
    private val probe = if (FeatureOption.COINAGE_DEBUG_TOOLS.isEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        CoinageLatencyProbe { overlay.text = it }
    } else {
        null
    }

    var onMetrics: ((CoinageCoinsMetrics) -> Unit)? = null

    init {
        clipChildren = true
        texture.isOpaque = false
        // The display's own rate, 120 on a fast panel: every stage between the wrist and the pixel quantises
        // to a frame, and the light is meant to feel attached to the hand. Earlier releases have no per-view
        // request and run at whatever the window gets.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            texture.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        }
        texture.surfaceTextureListener = this
        addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, 0))
    }

    fun update(
        coins: List<CoinageScene.Coin>,
        isExpanded: Boolean,
        areaWidth: Float,
        stripHeight: Float,
        gridBudget: Float,
        surfaceHeight: Int,
        focus: CoinageArrangement.Focus?,
        window: Float,
        coinSet: CoinageScene.CoinSet
    ) {
        this.coins = coins
        this.isExpanded = isExpanded
        this.areaWidth = areaWidth
        this.stripHeight = stripHeight
        this.gridBudget = gridBudget
        this.focus = focus
        this.window = window
        this.coinSet = coinSet

        if (texture.layoutParams.height != surfaceHeight) {
            texture.layoutParams = texture.layoutParams.apply { height = surfaceHeight }
        }

        push()
    }

    /** The coin under a point in field coordinates, in points, or null for empty ground. */
    fun tap(x: Float, y: Float, onResult: (String?) -> Unit) {
        thread?.tap(x, y) { id -> main.post { onResult(id) } }
    }

    private fun push() {
        thread?.update(coins, isExpanded, areaWidth, stripHeight, gridBudget, focus, window, coinSet)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        val started = CoinageRenderThread(context, surface, probe = probe != null) { metrics ->
            main.post { onMetrics?.invoke(metrics) }
        }

        thread = started
        started.start()
        push()
        started.resize(width, height, resources.displayMetrics.density)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        thread?.resize(width, height, resources.displayMetrics.density)
    }

    /** The thread releases the surface itself once its context is gone, so this never does. */
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        thread?.shutdown()
        thread = null

        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        val thread = thread ?: return

        texture.translationY = thread.drawnWindow * resources.displayMetrics.density

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) probe?.latched(thread.stampedFrame, thread.drawInterval)
    }

    /**
     * Before Android 15 there is no per-view request, so the window asks for the display's fastest mode at its
     * own resolution while the coins are on screen, and gives the mode back after.
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (probe != null) {
            context.activity()?.findViewById<FrameLayout>(android.R.id.content)?.addView(
                overlay,
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL or Gravity.END)
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) return

        val window = context.activity()?.window ?: return
        val screen = display ?: return
        val current = screen.mode
        val fastest = screen.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return

        previousMode = window.attributes.preferredDisplayModeId
        window.attributes = window.attributes.apply { preferredDisplayModeId = fastest.modeId }
    }

    override fun onDetachedFromWindow() {
        (overlay.parent as? FrameLayout)?.removeView(overlay)

        val mode = previousMode

        if (mode != null) {
            context.activity()?.window?.let { it.attributes = it.attributes.apply { preferredDisplayModeId = mode } }
            previousMode = null
        }

        super.onDetachedFromWindow()
    }

    /**
     * Covers the window going away as well as this view being hidden, which is what makes it the right hook
     * for the app being backgrounded — the view stays attached through that, so the surface callbacks say
     * nothing about it.
     */
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)

        thread?.setVisible(isVisible)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (isVisible) probe?.start() else probe?.stop()
        }
    }

    private companion object {
        const val OVERLAY_BACKGROUND = 0xCC000000.toInt()
        const val OVERLAY_TEXT_SIZE = 10f
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
