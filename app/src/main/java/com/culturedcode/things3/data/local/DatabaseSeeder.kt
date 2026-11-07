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
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import javax.inject.Provider

/**
 * Language-sensitive `RoomDatabase.Callback` that safely installs FTS triggers on open
 * and seeds inspiring, everyday GTD hierarchy data (Areas, Projects, Headings, Tasks, ChecklistItems, Tags)
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
        val today = LocalDate.now(zone)
        val todayEpoch = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val now = todayEpoch
        val todayReminder15h = today.atTime(LocalTime.of(15, 0)).atZone(zone).toInstant().toEpochMilli()
        val tomorrowEpoch = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val inThreeDaysEpoch = today.plusDays(3).atStartOfDay(zone).toInstant().toEpochMilli()
        val inFiveDaysEpoch = today.plusDays(5).atStartOfDay(zone).toInstant().toEpochMilli()

        val bundle = buildLocalizedSeedBundle(
            lang = languageCode.lowercase(Locale.ROOT),
            now = now,
            todayEpoch = todayEpoch,
            todayReminder15h = todayReminder15h,
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
        todayReminder15h: Long,
        tomorrowEpoch: Long,
        inThreeDaysEpoch: Long,
        inFiveDaysEpoch: Long
    ): LocalizedSeedBundle {
        val tagImportantId = "tag-important-001"
        val tagHomeId = "tag-home-002"
        val tagOfficeId = "tag-office-003"
        val tagErrandsId = "tag-errands-004"
        val tagQuickId = "tag-quick-005"

        val areaPersonalId = "area-personal-001"
        val areaWorkId = "area-work-002"

        val projVacationId = "proj-vacation-001"
        val projHomeId = "proj-home-002"

        val headBookingId = "head-booking-001"
        val headPackingId = "head-packing-002"
        val headEverydayId = "head-everyday-003"

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
                tagImportant = "Wichtig",
                tagHome = "Zuhause",
                tagOffice = "Büro",
                tagErrands = "Unterwegs",
                tagQuick = "5 Min.",
                areaPersonal = "Privat & Zuhause",
                areaWork = "Beruf & Fokus",
                projVacationTitle = "Urlaub & Reiseplanung",
                projVacationNotes = "Unterkunft, Anreise, Ausflüge und Packliste entspannt im Voraus vorbereiten.",
                projHomeTitle = "Zuhause & Wohlfühl-Alltag",
                projHomeNotes = "Ideen, Besorgungen und gesunde Routinen für jede Woche.",
                headBooking = "Buchung & Dokumente",
                headPacking = "Packliste & Vorbereitung",
                headEveryday = "Woche & Besorgungen",
                taskInboxTitle = "Zahnarzt-Vorsorgetermin für nächsten Monat vereinbaren",
                taskInboxNotes = "Kurz anrufen und Termin vormittags auswählen.",
                taskToday1Title = "Hotel am Meer & Zugverbindung buchen",
                taskToday1Notes = "Frühbucher-Option mit kostenloser Stornierung wählen.",
                taskToday2Title = "Frisches Obst, Gemüse & Kaffeebohnen vom Wochenmarkt holen",
                taskToday2Notes = "Stoffbeutel nicht vergessen.",
                taskEveningTitle = "30 Minuten Abendspaziergang & Buch lesen",
                taskEveningNotes = "Handy auf Flugmodus schalten und entspannen.",
                taskUpcomingTitle = "Reisepass-Gültigkeit & Reiseapotheke prüfen",
                taskUpcomingNotes = "Rechtzeitig vor der Abreise kontrollieren.",
                taskAnytimeTitle = "Kamera, Ladegeräte & Sonnenbrille bereitlegen",
                taskAnytimeNotes = "SD-Karte leeren und Powerbank aufladen.",
                taskSomedayTitle = "Schönste Panorama-Wanderrouten & Restaurants vor Ort sammeln",
                taskSomedayNotes = "Empfehlungen von Freunden notieren.",
                taskCompletedTitle = "Schreibtisch aufgeräumt & Wochenfokus festgelegt",
                check1 = "Reisedaten im Kalender bestätigen",
                check2 = "Zugsitzplätze am Fenster reservieren",
                check3 = "Buchungsbestätigung offline speichern"
            )
            "it" -> SeedStrings(
                tagImportant = "Importante",
                tagHome = "Casa",
                tagOffice = "Ufficio",
                tagErrands = "Commissioni",
                tagQuick = "5 min",
                areaPersonal = "Casa e Vita personale",
                areaWork = "Lavoro e Progetti",
                projVacationTitle = "Vacanze e Viaggio",
                projVacationNotes = "Alloggio, treni, escursioni e valigia organizzati con calma.",
                projHomeTitle = "Casa e Benessere Quotidiano",
                projHomeNotes = "Acquisti, idee per la casa e buone abitudini.",
                headBooking = "Prenotazioni e Documenti",
                headPacking = "Valigia e Preparativi",
                headEveryday = "Settimana e Spesa",
                taskInboxTitle = "Prenotare visita di controllo dal dentista",
                taskInboxNotes = "Scegliere un orario al mattino.",
                taskToday1Title = "Prenotare hotel sul mare e biglietti del treno",
                taskToday1Notes = "Verificare l'opzione di cancellazione gratuita.",
                taskToday2Title = "Comprare frutta fresca, verdura e caffè al mercato",
                taskToday2Notes = "Portare le borse riutilizzabili.",
                taskEveningTitle = "Passeggiata serale di 30 minuti e lettura",
                taskEveningNotes = "Rilassarsi senza schermi prima di dormire.",
                taskUpcomingTitle = "Controllare validità documenti e kit da viaggio",
                taskUpcomingNotes = "Da verificare prima della partenza.",
                taskAnytimeTitle = "Preparare fotocamera, caricabatterie e occhiali da sole",
                taskAnytimeNotes = "Ricaricare la powerbank.",
                taskSomedayTitle = "Raccogliere consigli su ristoranti e sentieri panoramici",
                taskSomedayNotes = "Idee per le prossime escursioni.",
                taskCompletedTitle = "Scrivania riordinata e priorità della settimana definite",
                check1 = "Confermare le date nel calendario",
                check2 = "Prenotare i posti sul treno",
                check3 = "Salvare la conferma di prenotazione"
            )
            "fr" -> SeedStrings(
                tagImportant = "Important",
                tagHome = "Maison",
                tagOffice = "Bureau",
                tagErrands = "Courses",
                tagQuick = "5 min",
                areaPersonal = "Maison & Vie personnelle",
                areaWork = "Travail & Projets",
                projVacationTitle = "Vacances & Planification de voyage",
                projVacationNotes = "Hébergement, billets de train, excursions et bagages en toute sérénité.",
                projHomeTitle = "Maison & Quotidien Bien-être",
                projHomeNotes = "Courses, idées déco et routines saines.",
                headBooking = "Réservations & Documents",
                headPacking = "Bagages & Préparatifs",
                headEveryday = "Semaine & Courses",
                taskInboxTitle = "Prendre rendez-vous chez le dentiste pour le mois prochain",
                taskInboxNotes = "Privilégier un créneau le matin.",
                taskToday1Title = "Réserver l'hôtel en bord de mer et les billets de train",
                taskToday1Notes = "Choisir le tarif avec annulation flexible.",
                taskToday2Title = "Acheter des fruits frais, des légumes et du café au marché",
                taskToday2Notes = "Penser aux sacs cabas.",
                taskEveningTitle = "Promenade du soir de 30 minutes et lecture",
                taskEveningNotes = "Déconnexion calme en fin de journée.",
                taskUpcomingTitle = "Vérifier les pièces d'identité et la trousse de voyage",
                taskUpcomingNotes = "Contrôler avant le départ.",
                taskAnytimeTitle = "Préparer appareil photo, chargeurs et lunettes de soleil",
                taskAnytimeNotes = "Charger la batterie externe.",
                taskSomedayTitle = "Noter les bonnes adresses de restaurants et randonnées",
                taskSomedayNotes = "Inspirations pour les vacances.",
                taskCompletedTitle = "Bureau rangé et priorités de la semaine définies",
                check1 = "Confirmer les dates dans le calendrier",
                check2 = "Réserver les places côté fenêtre",
                check3 = "Enregistrer la confirmation hors ligne"
            )
            "es" -> SeedStrings(
                tagImportant = "Importante",
                tagHome = "Casa",
                tagOffice = "Oficina",
                tagErrands = "Recados",
                tagQuick = "5 min",
                areaPersonal = "Hogar y Vida personal",
                areaWork = "Trabajo y Proyectos",
                projVacationTitle = "Vacaciones y Plan de Viaje",
                projVacationNotes = "Alojamiento, trenes, rutas y equipaje organizados con tranquilidad.",
                projHomeTitle = "Hogar y Bienestar Diario",
                projHomeNotes = "Compras semanales, hogar y hábitos saludables.",
                headBooking = "Reservas y Documentos",
                headPacking = "Equipaje y Preparativos",
                headEveryday = "Semana y Compras",
                taskInboxTitle = "Pedir cita de revisión con el dentista",
                taskInboxNotes = "Elegir horario por la mañana.",
                taskToday1Title = "Reservar hotel junto al mar y billetes de tren",
                taskToday1Notes = "Seleccionar tarifa con cancelación flexible.",
                taskToday2Title = "Comprar fruta fresca, verdura y café en el mercado",
                taskToday2Notes = "Llevar bolsas reutilizables.",
                taskEveningTitle = "Paseo tranquilo de 30 minutos y lectura nocturna",
                taskEveningNotes = "Desconectar el móvil por la noche.",
                taskUpcomingTitle = "Revisar pasaporte y botiquín de viaje",
                taskUpcomingNotes = "Comprobar con antelación.",
                taskAnytimeTitle = "Preparar cámara, cargadores y gafas de sol",
                taskAnytimeNotes = "Cargar la batería portátil.",
                taskSomedayTitle = "Guardar recomendaciones de restaurantes y miradores",
                taskSomedayNotes = "Ideas para próximas escapadas.",
                taskCompletedTitle = "Escritorio ordenado y objetivos semanales listos",
                check1 = "Confirmar fechas en el calendario",
                check2 = "Reservar asientos junto a la ventana",
                check3 = "Guardar confirmación de reserva"
            )
            else -> SeedStrings(
                tagImportant = "Important",
                tagHome = "Home",
                tagOffice = "Office",
                tagErrands = "Errands",
                tagQuick = "5 min",
                areaPersonal = "Personal & Home",
                areaWork = "Work & Focus",
                projVacationTitle = "Summer Vacation & Travel Plan",
                projVacationNotes = "Hotel, train tickets, local sights, and packing checklist organized calmly.",
                projHomeTitle = "Home & Everyday Wellbeing",
                projHomeNotes = "Weekly groceries, home organization, and healthy routines.",
                headBooking = "Bookings & Documents",
                headPacking = "Packing & Preparation",
                headEveryday = "Weekly & Errands",
                taskInboxTitle = "Book dental checkup appointment for next month",
                taskInboxNotes = "Pick a morning slot.",
                taskToday1Title = "Book seaside hotel & train tickets",
                taskToday1Notes = "Choose flexible rate with free cancellation.",
                taskToday2Title = "Pick up fresh fruit, vegetables & coffee beans at the market",
                taskToday2Notes = "Bring reusable tote bags.",
                taskEveningTitle = "30-minute evening walk & book reading",
                taskEveningNotes = "Unplug and wind down for the night.",
                taskUpcomingTitle = "Check passport validity & travel kit",
                taskUpcomingNotes = "Review ahead of departure.",
                taskAnytimeTitle = "Pack camera, chargers & sunglasses",
                taskAnytimeNotes = "Charge power bank and clear SD card.",
                taskSomedayTitle = "Collect favorite local restaurants & scenic hiking trails",
                taskSomedayNotes = "Saved ideas for the trip.",
                taskCompletedTitle = "Tidy desk & set weekly focus priorities",
                check1 = "Confirm travel dates in calendar",
                check2 = "Reserve window seats on the train",
                check3 = "Save booking confirmation offline"
            )
        }

        val tags = listOf(
            TagEntity(
                id = tagImportantId,
                title = strings.tagImportant,
                parentTagId = null,
                shortcut = "w",
                sortOrder = 100.0,
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ),
            TagEntity(
                id = tagHomeId,
                title = strings.tagHome,
                parentTagId = null,
                shortcut = "h",
                sortOrder = 200.0,
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ),
            TagEntity(
                id = tagOfficeId,
                title = strings.tagOffice,
                parentTagId = null,
                shortcut = "b",
                sortOrder = 300.0,
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ),
            TagEntity(
                id = tagErrandsId,
                title = strings.tagErrands,
                parentTagId = null,
                shortcut = "u",
                sortOrder = 400.0,
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ),
            TagEntity(
                id = tagQuickId,
                title = strings.tagQuick,
                parentTagId = null,
                shortcut = "5",
                sortOrder = 500.0,
                createdAt = now,
                updatedAt = now,
                isDirty = true
            )
        )

        val areas = listOf(
            AreaEntity(
                id = areaPersonalId,
                title = strings.areaPersonal,
                sortOrder = 1000.0,
                tagIdsJson = json.encodeToString(listOf(tagHomeId)),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ),
            AreaEntity(
                id = areaWorkId,
                title = strings.areaWork,
                sortOrder = 2000.0,
                tagIdsJson = json.encodeToString(listOf(tagOfficeId)),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            )
        )

        val projVacationTags = listOf(tagImportantId)
        val projHomeTags = listOf(tagHomeId, tagErrandsId)

        val projects = listOf(
            ProjectEntity(
                id = projVacationId,
                areaId = areaPersonalId,
                title = strings.projVacationTitle,
                notes = strings.projVacationNotes,
                status = ProjectStatus.ACTIVE,
                scheduleState = ScheduleState.TODAY,
                whenDate = todayEpoch,
                deadline = inFiveDaysEpoch,
                completedAt = null,
                sortOrder = 1000.0,
                tagIdsJson = json.encodeToString(projVacationTags),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ) to projVacationTags,
            ProjectEntity(
                id = projHomeId,
                areaId = areaPersonalId,
                title = strings.projHomeTitle,
                notes = strings.projHomeNotes,
                status = ProjectStatus.ACTIVE,
                scheduleState = ScheduleState.ANYTIME,
                whenDate = null,
                deadline = null,
                completedAt = null,
                sortOrder = 2000.0,
                tagIdsJson = json.encodeToString(projHomeTags),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ) to projHomeTags
        )

        val headings = listOf(
            HeadingEntity(
                id = headBookingId,
                projectId = projVacationId,
                title = strings.headBooking,
                sortOrder = 1000.0,
                isArchived = false,
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ),
            HeadingEntity(
                id = headPackingId,
                projectId = projVacationId,
                title = strings.headPacking,
                sortOrder = 2000.0,
                isArchived = false,
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ),
            HeadingEntity(
                id = headEverydayId,
                projectId = projHomeId,
                title = strings.headEveryday,
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
                daysOfWeek = listOf(5)
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
                reminderTime = todayReminder15h,
                completedAt = null,
                projectId = projVacationId,
                headingId = headBookingId,
                areaId = areaPersonalId,
                recurrenceRuleJson = null,
                sortOrder = 1000.0,
                tagIdsJson = json.encodeToString(listOf(tagImportantId, tagHomeId)),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ) to listOf(tagImportantId, tagHomeId),

            TaskEntity(
                id = taskToday2Id,
                title = strings.taskToday2Title,
                notes = strings.taskToday2Notes,
                status = TaskStatus.OPEN,
                scheduleState = ScheduleState.TODAY,
                whenDate = todayEpoch,
                deadline = null,
                reminderTime = null,
                completedAt = null,
                projectId = projHomeId,
                headingId = headEverydayId,
                areaId = areaPersonalId,
                recurrenceRuleJson = null,
                sortOrder = 2000.0,
                tagIdsJson = json.encodeToString(listOf(tagErrandsId)),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ) to listOf(tagErrandsId),

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
                projectId = projHomeId,
                headingId = headEverydayId,
                areaId = areaPersonalId,
                recurrenceRuleJson = null,
                sortOrder = 3000.0,
                tagIdsJson = json.encodeToString(listOf(tagHomeId)),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ) to listOf(tagHomeId),

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
                projectId = projVacationId,
                headingId = headBookingId,
                areaId = areaPersonalId,
                recurrenceRuleJson = weeklyRecurrenceJson,
                sortOrder = 4000.0,
                tagIdsJson = json.encodeToString(listOf(tagImportantId)),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ) to listOf(tagImportantId),

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
                projectId = projVacationId,
                headingId = headPackingId,
                areaId = areaPersonalId,
                recurrenceRuleJson = null,
                sortOrder = 5000.0,
                tagIdsJson = json.encodeToString(listOf(tagHomeId)),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ) to listOf(tagHomeId),

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
                projectId = projVacationId,
                headingId = headPackingId,
                areaId = areaPersonalId,
                recurrenceRuleJson = null,
                sortOrder = 6000.0,
                tagIdsJson = json.encodeToString(listOf(tagQuickId)),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ) to listOf(tagQuickId),

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
                projectId = projHomeId,
                headingId = headEverydayId,
                areaId = areaPersonalId,
                recurrenceRuleJson = null,
                sortOrder = 500.0,
                tagIdsJson = json.encodeToString(listOf(tagHomeId)),
                createdAt = now - 7200_000L,
                updatedAt = now,
                isDirty = true
            ) to listOf(tagHomeId)
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
        val tagImportant: String,
        val tagHome: String,
        val tagOffice: String,
        val tagErrands: String,
        val tagQuick: String,
        val areaPersonal: String,
        val areaWork: String,
        val projVacationTitle: String,
        val projVacationNotes: String,
        val projHomeTitle: String,
        val projHomeNotes: String,
        val headBooking: String,
        val headPacking: String,
        val headEveryday: String,
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
