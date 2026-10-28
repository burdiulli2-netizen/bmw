package com.example.bmwlauncher

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clockView: TextView
    private lateinit var dateView: TextView
    private var apps: List<ResolveInfo> = emptyList()

    // Apps que se fijan en la columna izquierda si su nombre contiene alguna de estas palabras
    private val pinKeywords = listOf("map", "navi", "gps", "music", "radio", "tel", "phone", "ajust", "setting")

    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apps = loadApps()
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    // Un launcher no debe cerrarse con "atrás"
    override fun onBackPressed() {}

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // Fondo: si existe res/drawable/wallpaper.(png|jpg), se usa; si no, degradado azul oscuro
        val wallId = resources.getIdentifier("wallpaper", "drawable", packageName)
        if (wallId != 0) {
            root.addView(ImageView(this).apply {
                setImageResource(wallId)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            root.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#0B1B33"), Color.parseColor("#000000"))
            )
        }

        // Capa oscura para que el texto se lea bien sobre cualquier fondo
        root.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#B3000000"))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        content.addView(buildLeftPanel(), LinearLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(buildAppGrid(), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            leftMargin = dp(24)
        })
        root.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        return root
    }

    private fun buildLeftPanel(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(buildHeader())
        col.addView(buildDivider())

        val pinned = apps.filter { info ->
            val label = info.loadLabel(packageManager).toString().lowercase(Locale.getDefault())
            pinKeywords.any { label.contains(it) }
        }.take(4)
        val list = if (pinned.isNotEmpty()) pinned else apps.take(4)

        val pinnedBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list.forEach { info ->
            pinnedBox.addView(buildTile(info, big = true), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(84)
            ).apply { setMargins(0, dp(6), 0, dp(6)) })
        }
        col.addView(pinnedBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { topMargin = dp(12) })

        return col
    }

    private fun buildAppGrid(): View {
        val grid = GridLayout(this).apply { columnCount = 4 }
        apps.forEach { info ->
            grid.addView(buildTile(info, big = false), GridLayout.LayoutParams().apply {
                width = 0
                height = dp(100)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(6), dp(6), dp(6), dp(6))
            })
        }
        return ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(grid)
        }
    }

    private fun buildHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }

        val title = TextView(this).apply {
            text = "INICIO"
            setTextColor(Color.WHITE)
            textSize = 14f
            letterSpacing = 0.3f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        clockView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 44f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        dateView = TextView(this).apply {
            setTextColor(Color.parseColor("#8E8E93"))
            textSize = 13f
        }

        header.addView(title)
        header.addView(clockView)
        header.addView(dateView)
        updateClock()
        return header
    }

    private fun buildDivider(): View {
        return View(this).apply {
            setBackgroundColor(Color.parseColor("#1C69D4"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)).apply {
                topMargin = dp(8)
            }
        }
    }

    private fun buildTile(info: ResolveInfo, big: Boolean): View {
        val pm = packageManager
        val radius = dp(14).toFloat()

        val normal = GradientDrawable().apply {
            setColor(Color.parseColor("#B3161A22"))
            cornerRadius = radius
            setStroke(dp(1), Color.parseColor("#33507A"))
        }
        val pressed = GradientDrawable().apply {
            setColor(Color.parseColor("#1C69D4"))
            cornerRadius = radius
        }
        val state = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }

        val iconSize = if (big) dp(44) else dp(48)
        val label = TextView(this).apply {
            text = info.loadLabel(pm)
            setTextColor(Color.WHITE)
            textSize = if (big) 15f else 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = if (big) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
        }
        val icon = ImageView(this).apply { setImageDrawable(info.loadIcon(pm)) }

        return LinearLayout(this).apply {
            orientation = if (big) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL or (if (big) Gravity.START else Gravity.CENTER)
            background = state
            isClickable = true
            isFocusable = true
            setPadding(dp(14), dp(6), dp(10), dp(6))

            addView(icon, LinearLayout.LayoutParams(iconSize, iconSize))
            if (big) {
                addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = dp(16)
                })
            } else {
                addView(label, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) })
            }

            setOnClickListener {
                pm.getLaunchIntentForPackage(info.activityInfo.packageName)?.let { startActivity(it) }
            }
        }
    }

    private fun loadApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.getDefault()) }
    }

    private fun updateClock() {
        val now = Date()
        clockView.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        dateView.text = SimpleDateFormat("EEEE d MMMM", Locale("es", "ES")).format(now)
            .replaceFirstChar { it.uppercase() }
    }
}
