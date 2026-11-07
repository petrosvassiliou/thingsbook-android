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

        val areaWorkId = "area-work-001"
        val areaHomeId = "area-home-002"

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
                areaPersonal = "Home",
                areaWork = "Work",
                projVacationTitle = "Urlaub & Reiseplanung",
                projVacationNotes = "Unterkunft, Anreise, Ausflüge und Packliste entspannt im Voraus vorbereiten.",
                projHomeTitle = "Quartalsziele & Team-Organisation",
                projHomeNotes = "Wichtige Meilensteine, Präsentationen und wöchentliche Routinen im Blick behalten.",
                headBooking = "Buchung & Dokumente",
                headPacking = "Packliste & Vorbereitung",
                headEveryday = "Wochenfokus & Meilensteine",
                taskInboxTitle = "Zahnarzt-Vorsorgetermin für nächsten Monat vereinbaren",
                taskInboxNotes = "Kurz anrufen und Termin vormittags auswählen.",
                taskToday1Title = "Hotel am Meer & Zugverbindung buchen",
                taskToday1Notes = "Frühbucher-Option mit kostenloser Stornierung wählen.",
                taskToday2Title = "Wochenplanung & Projektstatus für das Team vorbereiten",
                taskToday2Notes = "Kurzpräsentation und nächste Schritte abstimmen.",
                taskEveningTitle = "Zimmerpflanzen gießen & 30 Min. Buch lesen",
                taskEveningNotes = "Wiederholt sich automatisch 4 Tage nach Erledigung.",
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
                areaPersonal = "Home",
                areaWork = "Work",
                projVacationTitle = "Vacanze e Viaggio",
                projVacationNotes = "Alloggio, treni, escursioni e valigia organizzati con calma.",
                projHomeTitle = "Obiettivi e Organizzazione Lavoro",
                projHomeNotes = "Traguardi principali, presentazioni e routine settimanali.",
                headBooking = "Prenotazioni e Documenti",
                headPacking = "Valigia e Preparativi",
                headEveryday = "Focus Settimanale",
                taskInboxTitle = "Prenotare visita di controllo dal dentista",
                taskInboxNotes = "Scegliere un orario al mattino.",
                taskToday1Title = "Prenotare hotel sul mare e biglietti del treno",
                taskToday1Notes = "Verificare l'opzione di cancellazione gratuita.",
                taskToday2Title = "Preparare piano settimanale e aggiornamento progetti",
                taskToday2Notes = "Definire i prossimi passi con il team.",
                taskEveningTitle = "Annaffiare le piante e 30 minuti di lettura",
                taskEveningNotes = "Si ripete automaticamente 4 giorni dopo il completamento.",
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
                areaPersonal = "Home",
                areaWork = "Work",
                projVacationTitle = "Vacances & Planification de voyage",
                projVacationNotes = "Hébergement, billets de train, excursions et bagages en toute sérénité.",
                projHomeTitle = "Objectifs & Organisation Travail",
                projHomeNotes = "Étapes clés, présentations et routines hebdomadaires.",
                headBooking = "Réservations & Documents",
                headPacking = "Bagages & Préparatifs",
                headEveryday = "Focus de la Semaine",
                taskInboxTitle = "Prendre rendez-vous chez le dentiste pour le mois prochain",
                taskInboxNotes = "Privilégier un créneau le matin.",
                taskToday1Title = "Réserver l'hôtel en bord de mer et les billets de train",
                taskToday1Notes = "Choisir le tarif avec annulation flexible.",
                taskToday2Title = "Préparer le point hebdomadaire et les priorités d'équipe",
                taskToday2Notes = "Synthétiser les prochaines étapes.",
                taskEveningTitle = "Arroser les plantes & 30 minutes de lecture",
                taskEveningNotes = "Se répète automatiquement 4 jours après achèvement.",
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
                areaPersonal = "Home",
                areaWork = "Work",
                projVacationTitle = "Vacaciones y Plan de Viaje",
                projVacationNotes = "Alojamiento, trenes, rutas y equipaje organizados con tranquilidad.",
                projHomeTitle = "Objetivos y Organización del Trabajo",
                projHomeNotes = "Hitos principales, presentaciones y rutinas semanales.",
                headBooking = "Reservas y Documentos",
                headPacking = "Equipaje y Preparativos",
                headEveryday = "Enfoque Semanal",
                taskInboxTitle = "Pedir cita de revisión con el dentista",
                taskInboxNotes = "Elegir horario por la mañana.",
                taskToday1Title = "Reservar hotel junto al mar y billetes de tren",
                taskToday1Notes = "Seleccionar tarifa con cancelación flexible.",
                taskToday2Title = "Preparar planificación semanal y estado del proyecto",
                taskToday2Notes = "Alinear próximos pasos con el equipo.",
                taskEveningTitle = "Regar las plantas y 30 minutos de lectura",
                taskEveningNotes = "Se repite automáticamente 4 días después de completarse.",
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
                areaPersonal = "Home",
                areaWork = "Work",
                projVacationTitle = "Summer Vacation & Travel Plan",
                projVacationNotes = "Hotel, train tickets, local sights, and packing checklist organized calmly.",
                projHomeTitle = "Quarterly Goals & Team Organization",
                projHomeNotes = "Key milestones, presentations, and weekly focus routines.",
                headBooking = "Bookings & Documents",
                headPacking = "Packing & Preparation",
                headEveryday = "Weekly Focus & Milestones",
                taskInboxTitle = "Book dental checkup appointment for next month",
                taskInboxNotes = "Pick a morning slot.",
                taskToday1Title = "Book seaside hotel & train tickets",
                taskToday1Notes = "Choose flexible rate with free cancellation.",
                taskToday2Title = "Prepare weekly planning & project status update",
                taskToday2Notes = "Align next steps and priorities.",
                taskEveningTitle = "Water indoor plants & 30-minute book reading",
                taskEveningNotes = "Repeats automatically 4 days after completion.",
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

        // Preconfigured Things-for-Mac Areas: "Work" (1000.0) and "Home" (2000.0)
        val areas = listOf(
            AreaEntity(
                id = areaWorkId,
                title = strings.areaWork,
                sortOrder = 1000.0,
                tagIdsJson = json.encodeToString(listOf(tagOfficeId)),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ),
            AreaEntity(
                id = areaHomeId,
                title = strings.areaPersonal,
                sortOrder = 2000.0,
                tagIdsJson = json.encodeToString(listOf(tagHomeId)),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            )
        )

        val projVacationTags = listOf(tagImportantId, tagHomeId)
        val projWorkTags = listOf(tagOfficeId, tagImportantId)

        val projects = listOf(
            ProjectEntity(
                id = projHomeId,
                areaId = areaWorkId,
                title = strings.projHomeTitle,
                notes = strings.projHomeNotes,
                status = ProjectStatus.ACTIVE,
                scheduleState = ScheduleState.TODAY,
                whenDate = todayEpoch,
                deadline = inThreeDaysEpoch,
                completedAt = null,
                sortOrder = 1000.0,
                tagIdsJson = json.encodeToString(projWorkTags),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ) to projWorkTags,
            ProjectEntity(
                id = projVacationId,
                areaId = areaHomeId,
                title = strings.projVacationTitle,
                notes = strings.projVacationNotes,
                status = ProjectStatus.ACTIVE,
                scheduleState = ScheduleState.TODAY,
                whenDate = todayEpoch,
                deadline = inFiveDaysEpoch,
                completedAt = null,
                sortOrder = 2000.0,
                tagIdsJson = json.encodeToString(projVacationTags),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ) to projVacationTags
        )

        val headings = listOf(
            HeadingEntity(
                id = headEverydayId,
                projectId = projHomeId,
                title = strings.headEveryday,
                sortOrder = 1000.0,
                isArchived = false,
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ),
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
            )
        )

        // Things for Mac style Fixed Schedule recurrence (Every Monday & Friday)
        val weeklyRecurrenceJson = json.encodeToString(
            RecurrenceRule(
                mode = RecurrenceMode.FIXED_SCHEDULE,
                interval = 1,
                unit = RecurrenceUnit.WEEKS,
                daysOfWeek = listOf(1, 5)
            )
        )

        // Things for Mac style After Completion recurrence (4 days after completion)
        val afterCompletionRecurrenceJson = json.encodeToString(
            RecurrenceRule(
                mode = RecurrenceMode.AFTER_COMPLETION,
                interval = 4,
                unit = RecurrenceUnit.DAYS
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
                areaId = areaHomeId,
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
                areaId = areaWorkId,
                recurrenceRuleJson = weeklyRecurrenceJson,
                sortOrder = 2000.0,
                tagIdsJson = json.encodeToString(listOf(tagOfficeId)),
                createdAt = now,
                updatedAt = now,
                isDirty = true
            ) to listOf(tagOfficeId),

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
                areaId = areaHomeId,
                recurrenceRuleJson = afterCompletionRecurrenceJson,
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
                areaId = areaHomeId,
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
                areaId = areaHomeId,
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
                areaId = areaHomeId,
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
                areaId = areaWorkId,
                recurrenceRuleJson = null,
                sortOrder = 500.0,
                tagIdsJson = json.encodeToString(listOf(tagOfficeId)),
                createdAt = now - 7200_000L,
                updatedAt = now,
                isDirty = true
            ) to listOf(tagOfficeId)
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
