package com.culturedcode.things3.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.culturedcode.things3.data.local.entity.AreaEntity
import com.culturedcode.things3.data.local.entity.ChecklistItemEntity
import com.culturedcode.things3.data.local.entity.HeadingEntity
import com.culturedcode.things3.data.local.entity.ProjectEntity
import com.culturedcode.things3.data.local.entity.TagEntity
import com.culturedcode.things3.data.local.entity.TaskEntity
import com.culturedcode.things3.domain.model.ProjectStatus
import com.culturedcode.things3.domain.model.RecurrenceMode
import com.culturedcode.things3.domain.model.RecurrenceRule
import com.culturedcode.things3.domain.model.RecurrenceUnit
import com.culturedcode.things3.domain.model.ScheduleState
import com.culturedcode.things3.domain.model.TaskStatus
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Provider

/**
 * Language-sensitive `RoomDatabase.Callback` that safely installs FTS triggers on open
 * and seeds localized Things 3 GTD hierarchy data (Areas, Projects, Headings, Tasks, ChecklistItems, Tags)
 * in EN, DE, IT, FR, or ES on first database creation.
 */
class DatabaseSeeder(
    private val databaseProvider: Provider<ThingsDatabase>
) : RoomDatabase.Callback() {

    private val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val json = Json { encodeDefaults = true }

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        runCatching {
            ThingsDatabase.installFts5Triggers(db)
        }
        scope.launch {
            delay(250L)
            runCatching {
                seedIfNeeded(Locale.getDefault().language)
            }
        }
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        runCatching {
            ThingsDatabase.installFts5Triggers(db)
        }
    }

    suspend fun seedIfNeeded(languageCode: String = Locale.getDefault().language) = runCatching {
        val db = databaseProvider.get()
        if (db.taskDao().getTotalTaskCount() > 0) return@runCatching

        val zone = ZoneId.systemDefault()
        val todayEpoch = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = todayEpoch
        val tomorrowEpoch = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val inThreeDaysEpoch = LocalDate.now(zone).plusDays(3).atStartOfDay(zone).toInstant().toEpochMilli()
        val inFiveDaysEpoch = LocalDate.now(zone).plusDays(5).atStartOfDay(zone).toInstant().toEpochMilli()

        val bundle = buildLocalizedSeedBundle(
            lang = languageCode.lowercase(Locale.ROOT),
            now = now,
            todayEpoch = todayEpoch,
            tomorrowEpoch = tomorrowEpoch,
            inThreeDaysEpoch = inThreeDaysEpoch,
            inFiveDaysEpoch = inFiveDaysEpoch
        )

        db.tagDao().upsertAll(bundle.tags)
        db.areaDao().upsertAll(bundle.areas)
        bundle.projects.forEach { (project, tagIds) ->
            db.projectDao().upsertProjectWithTags(project, tagIds)
        }
        db.headingDao().upsertAll(bundle.headings)
        bundle.tasks.forEach { (task, tagIds) ->
            db.taskDao().upsertTaskWithTags(task, tagIds)
        }
        db.checklistDao().upsertAll(bundle.checklistItems)
    }

    private data class LocalizedSeedBundle(
        val tags: List<TagEntity>,
        val areas: List<AreaEntity>,
        val projects: List<Pair<ProjectEntity, List<String>>>,
        val headings: List<HeadingEntity>,
        val tasks: List<Pair<TaskEntity, List<String>>>,
        val checklistItems: List<ChecklistItemEntity>
    )

    private fun buildLocalizedSeedBundle(
        lang: String,
        now: Long,
        todayEpoch: Long,
        tomorrowEpoch: Long,
        inThreeDaysEpoch: Long,
        inFiveDaysEpoch: Long
    ): LocalizedSeedBundle {
        // Stable deterministic UUIDs so multiple devices or language tests stay clean
        val tagFocusId = "tag-focus-001"
        val tagQuickId = "tag-quick-002"
        val tagDeepWorkId = "tag-deepwork-003"
        val tagErrandsId = "tag-errands-004"

        val areaWorkId = "area-work-001"
        val areaPersonalId = "area-personal-002"

        val projLaunchId = "proj-launch-001"
        val projTripId = "proj-trip-002"

        val headArchId = "head-arch-001"
        val headDesignId = "head-design-002"
        val headLogisticsId = "head-logistics-003"

        val taskInbox1Id = "task-inbox-001"
        val taskToday1Id = "task-today-002"
        val taskToday2Id = "task-today-003"
        val taskEvening1Id = "task-evening-004"
        val taskUpcoming1Id = "task-upcoming-005"
        val taskAnytime1Id = "task-anytime-006"
        val taskSomeday1Id = "task-someday-007"
        val taskCompleted1Id = "task-completed-008"

        val strings = when (lang) {
            "de" -> SeedStrings(
                tagFocus = "Fokus",
                tagQuick = "5 Min.",
                tagDeepWork = "Deep Work",
                tagErrands = "Besorgungen",
                areaWork = "Arbeit & Architektur",
                areaPersonal = "Privat & Leben",
                projLaunchTitle = "Things 3 Android Release",
                projLaunchNotes = "Kompromisslose 120-Hz-Produktivitäts-App mit Offline-First Room FTS5 & Firebase Delta-Sync.",
                projTripTitle = "Wochenendtrip nach Südtirol",
                projTripNotes = "Hotel, Wanderroute und Packliste entspannt vorbereiten.",
                headArch = "Architektur & Cloud-Sync",
                headDesign = "UI/UX & 120-Hz-Animationen",
                headLogistics = "Reiseplanung & Buchung",
                taskInboxTitle = "Willkommen in Things 3 – Tippe hier zum Aufklappen",
                taskInboxNotes = "Ein Tap öffnet kein neues Fenster, sondern morpht diese Zeile per Feder-Animation direkt in der Liste zu einer Karte.",
                taskToday1Title = "Inline-Card-Expansion & Magic-Plus testen",
                taskToday1Notes = "Ziehe das blaue **Magic Plus (+)** unten rechts direkt zwischen zwei Aufgaben oder an den linken Rand für eine neue Überschrift.",
                taskToday2Title = "SQLite FTS5 Volltextsuche mit Umlauten prüfen (Äpfel, Öl, Überprüfung)",
                taskToday2Notes = "Wische auf der Liste nach unten für die Schnellsuche (`unicode61`-Tokenizer).",
                taskEveningTitle = "Abendlektüre & Tagesrückblick bei Tee",
                taskEveningNotes = "Erscheint in der Sektion **Heute Abend** mit sanftem Mond-Symbol.",
                taskUpcomingTitle = "Architektur-Review mit dem Team",
                taskUpcomingNotes = "Wiederkehrender Termin und Frist-Countdown im Blick behalten.",
                taskAnytimeTitle = "Haptisches Feedback für Checkbox Long-Press verfeinern",
                taskAnytimeNotes = "Ein langer Druck auf eine Checkbox markiert die Aufgabe als *Abgebrochen* (X-Symbol).",
                taskSomedayTitle = "Alpenüberquerung zu Fuß planen",
                taskSomedayNotes = "Ideensammlung für den nächsten Sommer.",
                taskCompletedTitle = "Room-Datenbank & Firebase Delta-Sync Architektur aufsetzen",
                check1 = "Aufgabe antippen, um die weiße Karte sanft zu öffnen",
                check2 = "Wann-Dialog öffnen und „morgen“ oder „heute abend“ tippen",
                check3 = "Magic Plus (+) zwischen zwei Zeilen ziehen"
            )
            "it" -> SeedStrings(
                tagFocus = "Focus",
                tagQuick = "5 min",
                tagDeepWork = "Lavoro profondo",
                tagErrands = "Commissioni",
                areaWork = "Lavoro & Architettura",
                areaPersonal = "Vita personale",
                projLaunchTitle = "Lancio Things 3 per Android",
                projLaunchNotes = "App di produttività nativa a 120 Hz con Room FTS5 Offline-First e sincronizzazione delta Firebase.",
                projTripTitle = "Weekend in Toscana",
                projTripNotes = "Itinerario, agriturismo e preparazione bagagli.",
                headArch = "Architettura & Cloud Sync",
                headDesign = "UI/UX & Micro-animazioni 120 Hz",
                headLogistics = "Logistica & Prenotazioni",
                taskInboxTitle = "Benvenuto in Things 3 – Tocca qui per espandere",
                taskInboxNotes = "Un tocco trasforma fluidamente la riga in una scheda bianca direttamente nella lista.",
                taskToday1Title = "Provare l'espansione inline e il pulsante Magic Plus",
                taskToday1Notes = "Trascina il pulsante blu **Magic Plus (+)** direttamente tra due attività nella lista.",
                taskToday2Title = "Verificare la ricerca FTS5 con accenti (città, perché, più)",
                taskToday2Notes = "Scorri verso il basso per aprire la Ricerca rapida in tempo reale.",
                taskEveningTitle = "Lettura serale e revisione della giornata",
                taskEveningNotes = "Pianificato per **Stasera** sotto l'icona della luna.",
                taskUpcomingTitle = "Revisione architetturale con il team",
                taskUpcomingNotes = "Controllare le scadenze e le attività ricorrenti.",
                taskAnytimeTitle = "Perfezionare il feedback aptico sulla casella di controllo",
                taskAnytimeNotes = "Una pressione prolungata segna l'attività come *Annullata* (icona X).",
                taskSomedayTitle = "Imparare la fotografia analogica",
                taskSomedayNotes = "Progetto ispirazionale per il futuro.",
                taskCompletedTitle = "Configurare il database Room e Firebase Delta-Sync",
                check1 = "Tocca l'attività per aprire la scheda con animazione a molla",
                check2 = "Apri Quando e digita \"domani\" o \"stasera\"",
                check3 = "Trascina il Magic Plus (+) nella lista"
            )
            "fr" -> SeedStrings(
                tagFocus = "Focus",
                tagQuick = "5 min",
                tagDeepWork = "Travail profond",
                tagErrands = "Courses",
                areaWork = "Travail & Architecture",
                areaPersonal = "Vie personnelle",
                projLaunchTitle = "Lancement Things 3 Android",
                projLaunchNotes = "Application de productivité native 120 Hz avec Room FTS5 Offline-First et synchronisation delta Firebase.",
                projTripTitle = "Week-end en Provence",
                projTripNotes = "Réservations, itinéraire et liste de voyage.",
                headArch = "Architecture & Synchronisation Cloud",
                headDesign = "UI/UX & Micro-animations 120 Hz",
                headLogistics = "Logistique & Réservations",
                taskInboxTitle = "Bienvenue dans Things 3 – Touchez ici pour agrandir",
                taskInboxNotes = "Un toucher transforme la ligne en une carte blanche directement dans la liste avec une animation fluide.",
                taskToday1Title = "Tester l'expansion de carte et le bouton Magic Plus",
                taskToday1Notes = "Faites glisser le bouton bleu **Magic Plus (+)** directement entre deux tâches.",
                taskToday2Title = "Vérifier la recherche FTS5 avec accents (échéance, être, à bientôt)",
                taskToday2Notes = "Balayez vers le bas pour ouvrir la Recherche rapide instantanée.",
                taskEveningTitle = "Lecture du soir et bilan de la journée",
                taskEveningNotes = "Planifié pour **Ce soir** avec l'icône de lune.",
                taskUpcomingTitle = "Revue d'architecture avec l'équipe",
                taskUpcomingNotes = "Suivre le compte à rebours de l'échéance.",
                taskAnytimeTitle = "Affiner le retour haptique lors d'un appui long",
                taskAnytimeNotes = "Un appui long sur la case marque la tâche comme *Annulée* (icône X).",
                taskSomedayTitle = "Planifier une traversée des Alpes",
                taskSomedayNotes = "Idées mises de côté pour un jour.",
                taskCompletedTitle = "Configurer la base Room et Firebase Delta-Sync",
                check1 = "Toucher la tâche pour déployer la carte en douceur",
                check2 = "Ouvrir Quand et saisir « demain » ou « ce soir »",
                check3 = "Glisser-déposer le Magic Plus (+) dans la liste"
            )
            "es" -> SeedStrings(
                tagFocus = "Enfoque",
                tagQuick = "5 min",
                tagDeepWork = "Trabajo profundo",
                tagErrands = "Recados",
                areaWork = "Trabajo y Arquitectura",
                areaPersonal = "Vida personal",
                projLaunchTitle = "Lanzamiento Things 3 Android",
                projLaunchNotes = "App de productividad nativa a 120 Hz con Room FTS5 Offline-First y sincronización delta en Firebase.",
                projTripTitle = "Escapada a la Costa Brava",
                projTripNotes = "Rutas, alojamiento y equipaje para el fin de semana.",
                headArch = "Arquitectura y Sincronización Cloud",
                headDesign = "UI/UX y Micro-animaciones 120 Hz",
                headLogistics = "Logística y Reservas",
                taskInboxTitle = "Bienvenido a Things 3 – Toca aquí para desplegar",
                taskInboxNotes = "Un toque transforma la fila directamente en una tarjeta blanca con animación elástica.",
                taskToday1Title = "Probar la expansión de tarjeta y el botón Magic Plus",
                taskToday1Notes = "Arrastra el botón azul **Magic Plus (+)** directamente entre dos tareas.",
                taskToday2Title = "Comprobar búsqueda FTS5 con acentos y eñes (mañana, rápido, acción)",
                taskToday2Notes = "Desliza hacia abajo en cualquier lista para abrir Búsqueda rápida.",
                taskEveningTitle = "Lectura nocturna y revisión del día",
                taskEveningNotes = "Programado para **Esta noche** bajo el icono de la luna.",
                taskUpcomingTitle = "Revisión de arquitectura con el equipo",
                taskUpcomingNotes = "Con fecha límite y cuenta atrás activa.",
                taskAnytimeTitle = "Ajustar respuesta háptica al mantener pulsada la casilla",
                taskAnytimeNotes = "Una pulsación larga marca la tarea como *Cancelada* (icono X).",
                taskSomedayTitle = "Aprender fotografía analógica",
                taskSomedayNotes = "Ideas guardadas para algún día.",
                taskCompletedTitle = "Configurar base de datos Room y Firebase Delta-Sync",
                check1 = "Tocar la tarea para abrir la tarjeta con animación fluida",
                check2 = "Abrir Cuándo y escribir «mañana» o «esta noche»",
                check3 = "Arrastrar el botón Magic Plus (+) a la lista"
            )
            else -> SeedStrings(
                tagFocus = "Focus",
                tagQuick = "5 min",
                tagDeepWork = "Deep Work",
                tagErrands = "Errands",
                areaWork = "Work & Architecture",
                areaPersonal = "Personal & Life",
                projLaunchTitle = "Things 3 Android Launch",
                projLaunchNotes = "Uncompromising 120Hz native productivity app with Offline-First Room FTS5 & Firebase Delta-Sync.",
                projTripTitle = "Weekend Trip to the Alps",
                projTripNotes = "Cabin booking, trail map, and packing essentials.",
                headArch = "Architecture & Cloud Sync",
                headDesign = "UI/UX & 120Hz Micro-Animations",
                headLogistics = "Logistics & Booking",
                taskInboxTitle = "Welcome to Things 3 – Tap here to expand inline",
                taskInboxNotes = "Tapping a To-Do never opens a jarring screen—it morphs smoothly right in the list using spring physics.",
                taskToday1Title = "Experience Inline Card Expansion & Magic Plus",
                taskToday1Notes = "Drag the glowing blue **Magic Plus (+)** button at the bottom right directly between two To-Dos or to the left edge for a Heading.",
                taskToday2Title = "Verify SQLite FTS5 Search with Diacritics (Café, Zürich, Mañana)",
                taskToday2Notes = "Swipe down on any list to open Quick Find powered by `unicode61` FTS5.",
                taskEveningTitle = "Evening reading & daily reflection",
                taskEveningNotes = "Scheduled for **This Evening** with the crescent moon icon.",
                taskUpcomingTitle = "Architecture review & release sign-off",
                taskUpcomingNotes = "Includes a hard deadline countdown and weekly recurrence.",
                taskAnytimeTitle = "Refine haptic feedback on checkbox long-press",
                taskAnytimeNotes = "Long-pressing any checkbox lets you mark the To-Do as *Canceled* (X icon).",
                taskSomedayTitle = "Plan a trans-alpine hiking expedition",
                taskSomedayNotes = "Saved in Someday for future inspiration.",
                taskCompletedTitle = "Set up Room Offline-First DB & Firebase Delta-Sync",
                check1 = "Tap this To-Do to expand the white card inline",
                check2 = "Open When dialog and type \"tomorrow\" or \"this evening\"",
                check3 = "Drag the Magic Plus (+) button into the list"
            )
        }

        val tags = listOf(
            TagEntity(
                id = tagFocusId,
                title = strings.tagFocus,
                parentTagId = null,
                shortcut = "f",
                sortOrder = 100.0,
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ),
            TagEntity(
                id = tagQuickId,
                title = strings.tagQuick,
                parentTagId = null,
                shortcut = "5",
                sortOrder = 200.0,
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ),
            TagEntity(
                id = tagDeepWorkId,
                title = strings.tagDeepWork,
                parentTagId = tagFocusId,
                shortcut = "d",
                sortOrder = 300.0,
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ),
            TagEntity(
                id = tagErrandsId,
                title = strings.tagErrands,
                parentTagId = null,
                shortcut = "e",
                sortOrder = 400.0,
                createdAt = now,
                updatedAt = now,
                isDirty = true
            )
        )

        val areas = listOf(
            AreaEntity(
                id = areaWorkId,
                title = strings.areaWork,
                sortOrder = 1000.0,
                tagIdsJson = json.encodeToString(listOf(tagFocusId)),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ),
            AreaEntity(
                id = areaPersonalId,
                title = strings.areaPersonal,
                sortOrder = 2000.0,
                tagIdsJson = "[]",
                createdAt = now,
                updatedAt = now,
                isDirty = true
            )
        )

        val projLaunchTags = listOf(tagFocusId, tagDeepWorkId)
        val projTripTags = listOf(tagErrandsId)

        val projects = listOf(
            ProjectEntity(
                id = projLaunchId,
                areaId = areaWorkId,
                title = strings.projLaunchTitle,
                notes = strings.projLaunchNotes,
                status = ProjectStatus.ACTIVE,
                scheduleState = ScheduleState.TODAY,
                whenDate = todayEpoch,
                deadline = inFiveDaysEpoch,
                completedAt = null,
                sortOrder = 1000.0,
                tagIdsJson = json.encodeToString(projLaunchTags),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ) to projLaunchTags,
            ProjectEntity(
                id = projTripId,
                areaId = areaPersonalId,
                title = strings.projTripTitle,
                notes = strings.projTripNotes,
                status = ProjectStatus.ACTIVE,
                scheduleState = ScheduleState.ANYTIME,
                whenDate = null,
                deadline = null,
                completedAt = null,
                sortOrder = 2000.0,
                tagIdsJson = json.encodeToString(projTripTags),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ) to projTripTags
        )

        val headings = listOf(
            HeadingEntity(
                id = headArchId,
                projectId = projLaunchId,
                title = strings.headArch,
                sortOrder = 1000.0,
                isArchived = false,
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ),
            HeadingEntity(
                id = headDesignId,
                projectId = projLaunchId,
                title = strings.headDesign,
                sortOrder = 2000.0,
                isArchived = false,
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ),
            HeadingEntity(
                id = headLogisticsId,
                projectId = projTripId,
                title = strings.headLogistics,
                sortOrder = 1000.0,
                isArchived = false,
                createdAt = now,
                updatedAt = now,
                isDirty = true
            )
        )

        val weeklyRecurrenceJson = json.encodeToString(
            RecurrenceRule(
                mode = RecurrenceMode.FIXED_SCHEDULE,
                interval = 1,
                unit = RecurrenceUnit.WEEKS,
                daysOfWeek = listOf(5) // Friday
            )
        )

        val tasks = listOf(
            TaskEntity(
                id = taskInbox1Id,
                title = strings.taskInboxTitle,
                notes = strings.taskInboxNotes,
                status = TaskStatus.OPEN,
                scheduleState = ScheduleState.INBOX,
                whenDate = null,
                deadline = null,
                reminderTime = null,
                completedAt = null,
                projectId = null,
                headingId = null,
                areaId = null,
                recurrenceRuleJson = null,
                sortOrder = 1000.0,
                tagIdsJson = json.encodeToString(listOf(tagQuickId)),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ) to listOf(tagQuickId),

            TaskEntity(
                id = taskToday1Id,
                title = strings.taskToday1Title,
                notes = strings.taskToday1Notes,
                status = TaskStatus.OPEN,
                scheduleState = ScheduleState.TODAY,
                whenDate = todayEpoch,
                deadline = inThreeDaysEpoch,
                reminderTime = null,
                completedAt = null,
                projectId = projLaunchId,
                headingId = headDesignId,
                areaId = areaWorkId,
                recurrenceRuleJson = null,
                sortOrder = 1000.0,
                tagIdsJson = json.encodeToString(listOf(tagFocusId, tagDeepWorkId)),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ) to listOf(tagFocusId, tagDeepWorkId),

            TaskEntity(
                id = taskToday2Id,
                title = strings.taskToday2Title,
                notes = strings.taskToday2Notes,
                status = TaskStatus.OPEN,
                scheduleState = ScheduleState.TODAY,
                whenDate = todayEpoch,
                deadline = todayEpoch,
                reminderTime = null,
                completedAt = null,
                projectId = projLaunchId,
                headingId = headArchId,
                areaId = areaWorkId,
                recurrenceRuleJson = null,
                sortOrder = 2000.0,
                tagIdsJson = json.encodeToString(listOf(tagQuickId)),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ) to listOf(tagQuickId),

            TaskEntity(
                id = taskEvening1Id,
                title = strings.taskEveningTitle,
                notes = strings.taskEveningNotes,
                status = TaskStatus.OPEN,
                scheduleState = ScheduleState.THIS_EVENING,
                whenDate = todayEpoch,
                deadline = null,
                reminderTime = null,
                completedAt = null,
                projectId = null,
                headingId = null,
                areaId = areaPersonalId,
                recurrenceRuleJson = null,
                sortOrder = 3000.0,
                tagIdsJson = "[]",
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ) to emptyList(),

            TaskEntity(
                id = taskUpcoming1Id,
                title = strings.taskUpcomingTitle,
                notes = strings.taskUpcomingNotes,
                status = TaskStatus.OPEN,
                scheduleState = ScheduleState.UPCOMING,
                whenDate = tomorrowEpoch,
                deadline = inFiveDaysEpoch,
                reminderTime = null,
                completedAt = null,
                projectId = projLaunchId,
                headingId = headArchId,
                areaId = areaWorkId,
                recurrenceRuleJson = weeklyRecurrenceJson,
                sortOrder = 4000.0,
                tagIdsJson = json.encodeToString(listOf(tagFocusId)),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ) to listOf(tagFocusId),

            TaskEntity(
                id = taskAnytime1Id,
                title = strings.taskAnytimeTitle,
                notes = strings.taskAnytimeNotes,
                status = TaskStatus.OPEN,
                scheduleState = ScheduleState.ANYTIME,
                whenDate = null,
                deadline = null,
                reminderTime = null,
                completedAt = null,
                projectId = projLaunchId,
                headingId = headDesignId,
                areaId = areaWorkId,
                recurrenceRuleJson = null,
                sortOrder = 5000.0,
                tagIdsJson = json.encodeToString(listOf(tagDeepWorkId)),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ) to listOf(tagDeepWorkId),

            TaskEntity(
                id = taskSomeday1Id,
                title = strings.taskSomedayTitle,
                notes = strings.taskSomedayNotes,
                status = TaskStatus.OPEN,
                scheduleState = ScheduleState.SOMEDAY,
                whenDate = null,
                deadline = null,
                reminderTime = null,
                completedAt = null,
                projectId = projTripId,
                headingId = headLogisticsId,
                areaId = areaPersonalId,
                recurrenceRuleJson = null,
                sortOrder = 6000.0,
                tagIdsJson = json.encodeToString(listOf(tagErrandsId)),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ) to listOf(tagErrandsId),

            TaskEntity(
                id = taskCompleted1Id,
                title = strings.taskCompletedTitle,
                notes = "",
                status = TaskStatus.COMPLETED,
                scheduleState = ScheduleState.TODAY,
                whenDate = todayEpoch,
                deadline = null,
                reminderTime = null,
                completedAt = now - 3600_000L,
                projectId = projLaunchId,
                headingId = headArchId,
                areaId = areaWorkId,
                recurrenceRuleJson = null,
                sortOrder = 500.0,
                tagIdsJson = json.encodeToString(listOf(tagFocusId)),
                createdAt = now - 7200_000L,
                updatedAt = now,
                isDirty = true
            ) to listOf(tagFocusId)
        )

        val checklistItems = listOf(
            ChecklistItemEntity(
                id = "chk-001",
                taskId = taskToday1Id,
                title = strings.check1,
                isCompleted = true,
                sortOrder = 100.0,
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ),
            ChecklistItemEntity(
                id = "chk-002",
                taskId = taskToday1Id,
                title = strings.check2,
                isCompleted = false,
                sortOrder = 200.0,
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ),
            ChecklistItemEntity(
                id = "chk-003",
                taskId = taskToday1Id,
                title = strings.check3,
                isCompleted = false,
                sortOrder = 300.0,
                createdAt = now,
                updatedAt = now,
                isDirty = true
            )
        )

        return LocalizedSeedBundle(
            tags = tags,
            areas = areas,
            projects = projects,
            headings = headings,
            tasks = tasks,
            checklistItems = checklistItems
        )
    }

    private data class SeedStrings(
        val tagFocus: String,
        val tagQuick: String,
        val tagDeepWork: String,
        val tagErrands: String,
        val areaWork: String,
        val areaPersonal: String,
        val projLaunchTitle: String,
        val projLaunchNotes: String,
        val projTripTitle: String,
        val projTripNotes: String,
        val headArch: String,
        val headDesign: String,
        val headLogistics: String,
        val taskInboxTitle: String,
        val taskInboxNotes: String,
        val taskToday1Title: String,
        val taskToday1Notes: String,
        val taskToday2Title: String,
        val taskToday2Notes: String,
        val taskEveningTitle: String,
        val taskEveningNotes: String,
        val taskUpcomingTitle: String,
        val taskUpcomingNotes: String,
        val taskAnytimeTitle: String,
        val taskAnytimeNotes: String,
        val taskSomedayTitle: String,
        val taskSomedayNotes: String,
        val taskCompletedTitle: String,
        val check1: String,
        val check2: String,
        val check3: String
    )
}
