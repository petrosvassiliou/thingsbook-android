package com.culturedcode.things3

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.culturedcode.things3.ui.screens.ThingsSmartViewScreen
import com.culturedcode.things3.ui.theme.Things3Theme
import com.culturedcode.things3.ui.viewmodel.ThingsbookViewModel
import com.culturedcode.things3.ui.widget.ThingsbookWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.ref.WeakReference

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var launchIntentState by mutableStateOf<Intent?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashGuardian.install(applicationContext)
        activeActivityRef = WeakReference(this)
        super.onCreate(savedInstanceState)
        launchIntentState = intent
        runCatching { enableEdgeToEdge() }
        requestNotificationPermissionIfNeeded()
        mountComposeContent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchIntentState = intent
    }

    override fun onResume() {
        super.onResume()
        activeActivityRef = WeakReference(this)
    }

    override fun onDestroy() {
        if (activeActivityRef?.get() === this) {
            activeActivityRef = null
        }
        super.onDestroy()
    }

    private fun mountComposeContent() {
        try {
            setContent {
                Things3Theme {
                    val viewModel: ThingsbookViewModel = hiltViewModel()
                    val currentIntent = launchIntentState
                    LaunchedEffect(currentIntent) {
                        if (currentIntent != null) {
                            runCatching {
                                val widgetView = currentIntent.getStringExtra(
                                    ThingsbookWidgetProvider.EXTRA_WIDGET_SMART_VIEW
                                )
                                val widgetTaskId = currentIntent.getStringExtra(
                                    ThingsbookWidgetProvider.EXTRA_TASK_ID
                                )
                                val widgetQuickAdd = currentIntent.getBooleanExtra(
                                    ThingsbookWidgetProvider.EXTRA_WIDGET_QUICK_ADD,
                                    false
                                )

                                if (widgetView != null || widgetTaskId != null || widgetQuickAdd) {
                                    viewModel.handleWidgetLaunchIntent(widgetView, widgetTaskId, widgetQuickAdd)
                                    currentIntent.removeExtra(ThingsbookWidgetProvider.EXTRA_WIDGET_SMART_VIEW)
                                    currentIntent.removeExtra(ThingsbookWidgetProvider.EXTRA_TASK_ID)
                                    currentIntent.removeExtra(ThingsbookWidgetProvider.EXTRA_WIDGET_QUICK_ADD)
                                }
                            }
                            launchIntentState = null
                        }
                    }
                    ThingsSmartViewScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } catch (t: Throwable) {
            CrashGuardian.recordCrash(applicationContext, Thread.currentThread(), t)
            mountNativeRecoveryScreen(t)
        }
    }

    private fun mountNativeRecoveryScreen(throwable: Throwable) {
        if (isFinishing || isDestroyed) return
        runCatching {
            val dp = resources.displayMetrics.density
            fun Int.dp(): Int = (this * dp).toInt()

            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val traceText = CrashGuardian.lastCaughtCrashTrace ?: sw.toString()

            val scroll = ScrollView(this).apply {
                setBackgroundColor(Color.parseColor("#F5F7FA"))
                isFillViewport = true
            }

            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24.dp(), 48.dp(), 24.dp(), 32.dp())
            }

            val titleView = TextView(this).apply {
                text = "Thingsbook — Sicherheitsmodus aktiv"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#1A1D21"))
            }
            root.addView(titleView)

            val subtitleView = TextView(this).apply {
                text = "Ein Laufzeitfehler wurde abgefangen, damit die App nicht abstürzt. Deine Aufgaben in der Datenbank sind sicher."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(Color.parseColor("#5D6573"))
                setPadding(0, 8.dp(), 0, 16.dp())
            }
            root.addView(subtitleView)

            val buttonRow = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            val reloadBtn = Button(this).apply {
                text = "🔄 Oberfläche neu starten (ohne Datenverlust)"
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#2B7BF6"))
                    cornerRadius = 10.dp().toFloat()
                }
                setPadding(16.dp(), 12.dp(), 16.dp(), 12.dp())
                setOnClickListener {
                    CrashGuardian.clearCrashLog(applicationContext)
                    recreate()
                }
            }
            buttonRow.addView(
                reloadBtn,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 10.dp() }
            )

            val repairSyncBtn = Button(this).apply {
                text = "🛠️ Auf Lokalen Modus umschalten & starten"
                isAllCaps = false
                setTextColor(Color.parseColor("#1A1D21"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#E3E8F0"))
                    cornerRadius = 10.dp().toFloat()
                }
                setPadding(16.dp(), 12.dp(), 16.dp(), 12.dp())
                setOnClickListener {
                    runCatching {
                        getSharedPreferences("thingsbook_settings_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("local_only_db_mode", true)
                            .commit()
                        getSharedPreferences("thingsbook_ui_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("local_only_db_mode", true)
                            .commit()
                    }
                    CrashGuardian.clearCrashLog(applicationContext)
                    recreate()
                }
            }
            buttonRow.addView(
                repairSyncBtn,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 10.dp() }
            )

            val copyBtn = Button(this).apply {
                text = "📋 Fehlerdetails in Zwischenablage kopieren"
                isAllCaps = false
                setTextColor(Color.parseColor("#2B7BF6"))
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    setStroke(1.dp(), Color.parseColor("#CBD2DF"))
                    cornerRadius = 10.dp().toFloat()
                }
                setPadding(16.dp(), 10.dp(), 16.dp(), 10.dp())
                setOnClickListener {
                    runCatching {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("Thingsbook Crash Report", traceText))
                        Toast.makeText(this@MainActivity, "Fehlerbericht kopiert!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            buttonRow.addView(
                copyBtn,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 16.dp() }
            )

            root.addView(buttonRow)

            val traceBox = TextView(this).apply {
                text = traceText
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#B00020"))
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    setStroke(1.dp(), Color.parseColor("#E0E4EC"))
                    cornerRadius = 10.dp().toFloat()
                }
                setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
                setTextIsSelectable(true)
                gravity = Gravity.TOP or Gravity.START
            }
            root.addView(traceBox)

            scroll.addView(root)
            setContentView(scroll)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                runCatching {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    companion object {
        @Volatile
        private var activeActivityRef: WeakReference<MainActivity>? = null

        @Volatile
        private var lastRecoveryAttemptMillis: Long = 0L

        fun notifyMainThreadCrash(throwable: Throwable) {
            val activity = activeActivityRef?.get() ?: return
            Handler(Looper.getMainLooper()).post {
                runCatching {
                    if (activity.isFinishing || activity.isDestroyed) return@runCatching
                    val now = System.currentTimeMillis()
                    if (now - lastRecoveryAttemptMillis > 4000L) {
                        lastRecoveryAttemptMillis = now
                        activity.mountNativeRecoveryScreen(throwable)
                    }
                }
            }
        }
    }
}

