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
import android.content.res.Configuration
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.hilt.navigation.compose.hiltViewModel
import com.culturedcode.things3.data.repository.SYNC_KEY_LOCAL_ONLY_DB_MODE
import com.culturedcode.things3.data.repository.syncDataStore
import com.culturedcode.things3.ui.components.SmartViewType
import com.culturedcode.things3.ui.components.ThingsTooltipHostBox
import com.culturedcode.things3.ui.screens.ThingsSmartViewScreen
import com.culturedcode.things3.ui.theme.ProvideLiveAppLocale
import com.culturedcode.things3.ui.theme.Things3Theme
import com.culturedcode.things3.ui.viewmodel.ThingsbookViewModel
import com.culturedcode.things3.ui.widget.ThingsbookWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.ref.WeakReference
import java.util.Locale

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var launchIntentState by mutableStateOf<Intent?>(null)
    private var systemConfigRevision by mutableIntStateOf(0)
    private var externalLocaleSyncCallback: (() -> Unit)? = null
    var hardwareKeyShortcutHandler: ((android.view.KeyEvent) -> Boolean)? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (hardwareKeyShortcutHandler?.invoke(event) == true) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemConfigRevision += 1
        externalLocaleSyncCallback?.invoke()
    }

    override fun onResume() {
        super.onResume()
        activeActivityRef = WeakReference(this)
        externalLocaleSyncCallback?.invoke()
    }

    override fun onDestroy() {
        if (activeActivityRef?.get() === this) {
            activeActivityRef = null
        }
        externalLocaleSyncCallback = null
        super.onDestroy()
    }

    private fun mountComposeContent() {
        try {
            setContent {
                Things3Theme {
                    val viewModel: ThingsbookViewModel = hiltViewModel()
                    val appLanguageTag by viewModel.appLanguageTag.collectAsState()
                    LaunchedEffect(viewModel) {
                        externalLocaleSyncCallback = { viewModel.syncAppLanguageFromSystemIfIdle() }
                    }
                    val currentIntent = launchIntentState
                    LaunchedEffect(currentIntent) {
                        if (currentIntent != null) {
                            runCatching {
                                if (currentIntent.action == Intent.ACTION_SEND &&
                                    currentIntent.type?.startsWith("text/") == true
                                ) {
                                    val sharedSubject = currentIntent.getStringExtra(Intent.EXTRA_SUBJECT)
                                    val sharedText = currentIntent.getStringExtra(Intent.EXTRA_TEXT)
                                    if (!sharedSubject.isNullOrBlank() || !sharedText.isNullOrBlank()) {
                                        viewModel.openQuickShareDraft(sharedSubject, sharedText)
                                    }
                                    currentIntent.action = Intent.ACTION_MAIN
                                    currentIntent.removeExtra(Intent.EXTRA_SUBJECT)
                                    currentIntent.removeExtra(Intent.EXTRA_TEXT)
                                }

                                val shortcutAction = currentIntent.getStringExtra("extra_shortcut_action")
                                if (!shortcutAction.isNullOrBlank()) {
                                    when (shortcutAction) {
                                        "NEW_TODO" -> viewModel.handleWidgetLaunchIntent("INBOX", null, true)
                                        "TODAY" -> viewModel.selectSmartView(SmartViewType.TODAY)
                                        "INBOX" -> viewModel.selectSmartView(SmartViewType.INBOX)
                                        "QUICK_FIND" -> {
                                            if (!viewModel.biometricAppLockEnabled.value || viewModel.isAppUnlocked.value) {
                                                viewModel.setQuickFindOpen(true)
                                            }
                                        }
                                        "VOICE_CAPTURE" -> viewModel.openVoiceQuickCapture()
                                    }
                                    currentIntent.removeExtra("extra_shortcut_action")
                                }

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
                    ProvideLiveAppLocale(
                        languageTag = appLanguageTag,
                        systemConfigRevision = systemConfigRevision
                    ) {
                        ThingsTooltipHostBox(modifier = Modifier.fillMaxSize()) {
                            ThingsSmartViewScreen(
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
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

            val lang = runCatching {
                ContextCompat.getContextForLanguage(this).resources.configuration.locales[0]?.language
            }.getOrNull()?.takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT)?.take(2)
                ?: Locale.getDefault().language.lowercase(Locale.ROOT).take(2)

            val safeTitle = when (lang) {
                "de" -> "Thingsbook — Sicherheitsmodus aktiv"
                "fr" -> "Thingsbook — Mode sans échec actif"
                "it" -> "Thingsbook — Modalità provvisoria attiva"
                "es" -> "Thingsbook — Modo seguro activo"
                else -> "Thingsbook — Safe Mode Active"
            }
            val safeSubtitle = when (lang) {
                "de" -> "Ein Laufzeitfehler wurde abgefangen, damit die App nicht abstürzt. Deine Aufgaben in der Datenbank sind sicher."
                "fr" -> "Une erreur d'exécution a été interceptée pour éviter un plantage. Vos tâches dans la base de données sont en sécurité."
                "it" -> "È stato intercettato un errore di esecuzione per evitare l'arresto dell'app. Le tue attività nel database sono al sicuro."
                "es" -> "Se interceptó un error en tiempo de ejecución para evitar un cierre inesperado. Tus tareas en la base de datos están seguras."
                else -> "A runtime error was caught to prevent a crash. Your tasks in the database are safe."
            }
            val reloadBtnLabel = when (lang) {
                "de" -> "🔄 Oberfläche neu starten (ohne Datenverlust)"
                "fr" -> "🔄 Redémarrer l'interface (sans perte de données)"
                "it" -> "🔄 Riavvia l'interfaccia (senza perdita di dati)"
                "es" -> "🔄 Reiniciar la interfaz (sin pérdida de datos)"
                else -> "🔄 Restart Interface (no data loss)"
            }
            val localModeBtnLabel = when (lang) {
                "de" -> "🛠️ Auf Lokalen Modus umschalten & starten"
                "fr" -> "🛠️ Basculer en mode local et démarrer"
                "it" -> "🛠️ Passa alla modalità locale e avvia"
                "es" -> "🛠️ Cambiar a modo local e iniciar"
                else -> "🛠️ Switch to Local-Only Mode & Launch"
            }
            val copyBtnLabel = when (lang) {
                "de" -> "📋 Fehlerdetails in Zwischenablage kopieren"
                "fr" -> "📋 Copier les détails de l'erreur"
                "it" -> "📋 Copia dettagli errore negli appunti"
                "es" -> "📋 Copiar detalles del error al portapapeles"
                else -> "📋 Copy Error Details to Clipboard"
            }
            val copiedToastLabel = when (lang) {
                "de" -> "Fehlerbericht kopiert!"
                "fr" -> "Rapport d'erreur copié !"
                "it" -> "Report dell'errore copiato!"
                "es" -> "¡Informe de error copiado!"
                else -> "Crash report copied!"
            }

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
                text = safeTitle
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#1A1D21"))
            }
            root.addView(titleView)

            val subtitleView = TextView(this).apply {
                text = safeSubtitle
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(Color.parseColor("#5D6573"))
                setPadding(0, 8.dp(), 0, 16.dp())
            }
            root.addView(subtitleView)

            val buttonRow = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            val reloadBtn = Button(this).apply {
                text = reloadBtnLabel
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
                text = localModeBtnLabel
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
                        runBlocking {
                            applicationContext.syncDataStore.edit { prefs ->
                                prefs[SYNC_KEY_LOCAL_ONLY_DB_MODE] = true
                            }
                        }
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
                text = copyBtnLabel
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
                        Toast.makeText(this@MainActivity, copiedToastLabel, Toast.LENGTH_SHORT).show()
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

