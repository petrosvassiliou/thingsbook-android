import Foundation

public struct LocalizedSeedBundle: Sendable {
    public let tags: [Tag]
    public let areas: [Area]
    public let projects: [Project]
    public let headings: [Heading]
    public let tasks: [TaskItem]
    public let checklistItems: [ChecklistItem]
}

public enum DatabaseSeeder {
    public static let sampleTaskIds: Set<String> = [
        "task-inbox-001",
        "task-today-002",
        "task-today-003",
        "task-evening-004",
        "task-upcoming-005",
        "task-anytime-006",
        "task-someday-007",
        "task-completed-008"
    ]

    public static let sampleProjectIds: Set<String> = [
        "proj-vacation-001",
        "proj-home-002"
    ]

    public static let sampleHeadingIds: Set<String> = [
        "head-booking-001",
        "head-packing-002",
        "head-everyday-003"
    ]

    public static let sampleChecklistIds: Set<String> = [
        "chk-001",
        "chk-002",
        "chk-003"
    ]

    public static let sampleAreaIds: Set<String> = [
        "area-work-001",
        "area-home-002"
    ]

    public static let sampleTagIds: Set<String> = [
        "tag-important-001",
        "tag-home-002",
        "tag-office-003",
        "tag-errands-004",
        "tag-quick-005"
    ]

    private struct SeedStrings {
        let tagImportant: String
        let tagHome: String
        let tagOffice: String
        let tagErrands: String
        let tagQuick: String
        let areaPersonal: String
        let areaWork: String
        let projVacationTitle: String
        let projVacationNotes: String
        let projHomeTitle: String
        let projHomeNotes: String
        let headBooking: String
        let headPacking: String
        let headEveryday: String
        let taskInboxTitle: String
        let taskInboxNotes: String
        let taskToday1Title: String
        let taskToday1Notes: String
        let taskToday2Title: String
        let taskToday2Notes: String
        let taskEveningTitle: String
        let taskEveningNotes: String
        let taskUpcomingTitle: String
        let taskUpcomingNotes: String
        let taskAnytimeTitle: String
        let taskAnytimeNotes: String
        let taskSomedayTitle: String
        let taskSomedayNotes: String
        let taskCompletedTitle: String
        let check1: String
        let check2: String
        let check3: String
    }

    public static func buildBundle(
        languageCode: String,
        referenceDate: Date = Date(),
        calendar: Calendar = .current
    ) -> LocalizedSeedBundle {
        let lang = String(languageCode.prefix(2)).lowercased()
        let now = Int64(referenceDate.timeIntervalSince1970 * 1000.0)
        let sodToday = calendar.startOfDay(for: referenceDate)
        let todayEpoch = DateEpochConverter.startOfDayMillis(for: sodToday, calendar: calendar)
        let tomorrowDate = calendar.date(byAdding: .day, value: 1, to: sodToday) ?? sodToday.addingTimeInterval(86_400)
        let inThreeDaysDate = calendar.date(byAdding: .day, value: 3, to: sodToday) ?? sodToday.addingTimeInterval(3 * 86_400)
        let inFiveDaysDate = calendar.date(byAdding: .day, value: 5, to: sodToday) ?? sodToday.addingTimeInterval(5 * 86_400)

        let tomorrowEpoch = DateEpochConverter.startOfDayMillis(for: tomorrowDate, calendar: calendar)
        let inThreeDaysEpoch = DateEpochConverter.startOfDayMillis(for: inThreeDaysDate, calendar: calendar)
        let inFiveDaysEpoch = DateEpochConverter.startOfDayMillis(for: inFiveDaysDate, calendar: calendar)
        let todayReminder15h = WhenScheduleResolver.computeReminderEpoch(
            targetDate: sodToday,
            calendar: calendar,
            reminderEnabled: true,
            reminderHour: 15,
            reminderMinute: 0
        ) ?? (todayEpoch + 15 * 3_600_000)

        let strings: SeedStrings
        switch lang {
        case "de":
            strings = SeedStrings(
                tagImportant: "Wichtig",
                tagHome: "Zuhause",
                tagOffice: "Büro",
                tagErrands: "Unterwegs",
                tagQuick: "5 Min.",
                areaPersonal: "Home",
                areaWork: "Work",
                projVacationTitle: "Urlaub & Reiseplanung",
                projVacationNotes: "Unterkunft, Anreise, Ausflüge und Packliste entspannt im Voraus vorbereiten.",
                projHomeTitle: "Quartalsziele & Team-Organisation",
                projHomeNotes: "Wichtige Meilensteine, Präsentationen und wöchentliche Routinen im Blick behalten.",
                headBooking: "Buchung & Dokumente",
                headPacking: "Packliste & Vorbereitung",
                headEveryday: "Wochenfokus & Meilensteine",
                taskInboxTitle: "Zahnarzt-Vorsorgetermin für nächsten Monat vereinbaren",
                taskInboxNotes: "Kurz anrufen und Termin vormittags auswählen.",
                taskToday1Title: "Hotel am Meer & Zugverbindung buchen",
                taskToday1Notes: "Frühbucher-Option mit kostenloser Stornierung wählen.",
                taskToday2Title: "Wochenplanung & Projektstatus für das Team vorbereiten",
                taskToday2Notes: "Kurzpräsentation und nächste Schritte abstimmen.",
                taskEveningTitle: "Zimmerpflanzen gießen & 30 Min. Buch lesen",
                taskEveningNotes: "Wiederholt sich automatisch 4 Tage nach Erledigung.",
                taskUpcomingTitle: "Reisepass-Gültigkeit & Reiseapotheke prüfen",
                taskUpcomingNotes: "Rechtzeitig vor der Abreise kontrollieren.",
                taskAnytimeTitle: "Kamera, Ladegeräte & Sonnenbrille bereitlegen",
                taskAnytimeNotes: "SD-Karte leeren und Powerbank aufladen.",
                taskSomedayTitle: "Schönste Panorama-Wanderrouten & Restaurants vor Ort sammeln",
                taskSomedayNotes: "Empfehlungen von Freunden notieren.",
                taskCompletedTitle: "Schreibtisch aufgeräumt & Wochenfokus festgelegt",
                check1: "Reisedaten im Kalender bestätigen",
                check2: "Zugsitzplätze am Fenster reservieren",
                check3: "Buchungsbestätigung offline speichern"
            )
        case "it":
            strings = SeedStrings(
                tagImportant: "Importante",
                tagHome: "Casa",
                tagOffice: "Ufficio",
                tagErrands: "Commissioni",
                tagQuick: "5 min",
                areaPersonal: "Home",
                areaWork: "Work",
                projVacationTitle: "Vacanze e Viaggio",
                projVacationNotes: "Alloggio, treni, escursioni e valigia organizzati con calma.",
                projHomeTitle: "Obiettivi e Organizzazione Lavoro",
                projHomeNotes: "Traguardi principali, presentazioni e routine settimanali.",
                headBooking: "Prenotazioni e Documenti",
                headPacking: "Valigia e Preparativi",
                headEveryday: "Focus Settimanale",
                taskInboxTitle: "Prenotare visita di controllo dal dentista",
                taskInboxNotes: "Scegliere un orario al mattino.",
                taskToday1Title: "Prenotare hotel sul mare e biglietti del treno",
                taskToday1Notes: "Verificare l'opzione di cancellazione gratuita.",
                taskToday2Title: "Preparare piano settimanale e aggiornamento progetti",
                taskToday2Notes: "Definire i prossimi passi con il team.",
                taskEveningTitle: "Annaffiare le piante e 30 minuti di lettura",
                taskEveningNotes: "Si ripete automaticamente 4 giorni dopo il completamento.",
                taskUpcomingTitle: "Controllare validità documenti e kit da viaggio",
                taskUpcomingNotes: "Da verificare prima della partenza.",
                taskAnytimeTitle: "Preparare fotocamera, caricabatterie e occhiali da sole",
                taskAnytimeNotes: "Ricaricare la powerbank.",
                taskSomedayTitle: "Raccogliere consigli su ristoranti e sentieri panoramici",
                taskSomedayNotes: "Idee per le prossime escursioni.",
                taskCompletedTitle: "Scrivania riordinata e priorità della settimana definite",
                check1: "Confermare le date nel calendario",
                check2: "Prenotare i posti sul treno",
                check3: "Salvare la conferma di prenotazione"
            )
        case "fr":
            strings = SeedStrings(
                tagImportant: "Important",
                tagHome: "Maison",
                tagOffice: "Bureau",
                tagErrands: "Courses",
                tagQuick: "5 min",
                areaPersonal: "Home",
                areaWork: "Work",
                projVacationTitle: "Vacances & Planification de voyage",
                projVacationNotes: "Hébergement, billets de train, excursions et bagages en toute sérénité.",
                projHomeTitle: "Objectifs & Organisation Travail",
                projHomeNotes: "Étapes clés, présentations et routines hebdomadaires.",
                headBooking: "Réservations & Documents",
                headPacking: "Bagages & Préparatifs",
                headEveryday: "Focus de la Semaine",
                taskInboxTitle: "Prendre rendez-vous chez le dentiste pour le mois prochain",
                taskInboxNotes: "Privilégier un créneau le matin.",
                taskToday1Title: "Réserver l'hôtel en bord de mer et les billets de train",
                taskToday1Notes: "Choisir le tarif avec annulation flexible.",
                taskToday2Title: "Préparer le point hebdomadaire et les priorités d'équipe",
                taskToday2Notes: "Synthétiser les prochaines étapes.",
                taskEveningTitle: "Arroser les plantes & 30 minutes de lecture",
                taskEveningNotes: "Se répète automatiquement 4 jours après achèvement.",
                taskUpcomingTitle: "Vérifier les pièces d'identité et la trousse de voyage",
                taskUpcomingNotes: "Contrôler avant le départ.",
                taskAnytimeTitle: "Préparer appareil photo, chargeurs et lunettes de soleil",
                taskAnytimeNotes: "Charger la batterie externe.",
                taskSomedayTitle: "Noter les bonnes adresses de restaurants et randonnées",
                taskSomedayNotes: "Inspirations pour les vacances.",
                taskCompletedTitle: "Bureau rangé et priorités de la semaine définies",
                check1: "Confirmer les dates dans le calendrier",
                check2: "Réserver les places côté fenêtre",
                check3: "Enregistrer la confirmation hors ligne"
            )
        case "es":
            strings = SeedStrings(
                tagImportant: "Importante",
                tagHome: "Casa",
                tagOffice: "Oficina",
                tagErrands: "Recados",
                tagQuick: "5 min",
                areaPersonal: "Home",
                areaWork: "Work",
                projVacationTitle: "Vacaciones y Plan de Viaje",
                projVacationNotes: "Alojamiento, trenes, rutas y equipaje organizados con tranquilidad.",
                projHomeTitle: "Objetivos y Organización del Trabajo",
                projHomeNotes: "Hitos principales, presentaciones y rutinas semanales.",
                headBooking: "Reservas y Documentos",
                headPacking: "Equipaje y Preparativos",
                headEveryday: "Enfoque Semanal",
                taskInboxTitle: "Pedir cita de revisión con el dentista",
                taskInboxNotes: "Elegir horario por la mañana.",
                taskToday1Title: "Reservar hotel junto al mar y billetes de tren",
                taskToday1Notes: "Seleccionar tarifa con cancelación flexible.",
                taskToday2Title: "Preparar planificación semanal y estado del proyecto",
                taskToday2Notes: "Alinear próximos pasos con el equipo.",
                taskEveningTitle: "Regar las plantas y 30 minutos de lectura",
                taskEveningNotes: "Se repite automáticamente 4 días después de completarse.",
                taskUpcomingTitle: "Revisar pasaporte y botiquín de viaje",
                taskUpcomingNotes: "Comprobar con antelación.",
                taskAnytimeTitle: "Preparar cámara, cargadores y gafas de sol",
                taskAnytimeNotes: "Cargar la batería portátil.",
                taskSomedayTitle: "Guardar recomendaciones de restaurantes y miradores",
                taskSomedayNotes: "Ideas para próximas escapadas.",
                taskCompletedTitle: "Escritorio ordenado y objetivos semanales listos",
                check1: "Confirmar fechas en el calendario",
                check2: "Reservar asientos junto a la ventana",
                check3: "Guardar confirmación de reserva"
            )
        default:
            strings = SeedStrings(
                tagImportant: "Important",
                tagHome: "Home",
                tagOffice: "Office",
                tagErrands: "Errands",
                tagQuick: "5 min",
                areaPersonal: "Home",
                areaWork: "Work",
                projVacationTitle: "Summer Vacation & Travel Plan",
                projVacationNotes: "Hotel, train tickets, local sights, and packing checklist organized calmly.",
                projHomeTitle: "Quarterly Goals & Team Organization",
                projHomeNotes: "Key milestones, presentations, and weekly focus routines.",
                headBooking: "Bookings & Documents",
                headPacking: "Packing & Preparation",
                headEveryday: "Weekly Focus & Milestones",
                taskInboxTitle: "Book dental checkup appointment for next month",
                taskInboxNotes: "Pick a morning slot.",
                taskToday1Title: "Book seaside hotel & train tickets",
                taskToday1Notes: "Choose flexible rate with free cancellation.",
                taskToday2Title: "Prepare weekly planning & project status update",
                taskToday2Notes: "Align next steps and priorities.",
                taskEveningTitle: "Water indoor plants & 30-minute book reading",
                taskEveningNotes: "Repeats automatically 4 days after completion.",
                taskUpcomingTitle: "Check passport validity & travel kit",
                taskUpcomingNotes: "Review ahead of departure.",
                taskAnytimeTitle: "Pack camera, chargers & sunglasses",
                taskAnytimeNotes: "Charge power bank and clear SD card.",
                taskSomedayTitle: "Collect favorite local restaurants & scenic hiking trails",
                taskSomedayNotes: "Saved ideas for the trip.",
                taskCompletedTitle: "Tidy desk & set weekly focus priorities",
                check1: "Confirm travel dates in calendar",
                check2: "Reserve window seats on the train",
                check3: "Save booking confirmation offline"
            )
        }

        let tagImportant = Tag(id: "tag-important-001", title: strings.tagImportant, shortcut: "w", sortOrder: 100.0, createdAt: now, updatedAt: now)
        let tagHome = Tag(id: "tag-home-002", title: strings.tagHome, shortcut: "h", sortOrder: 200.0, createdAt: now, updatedAt: now)
        let tagOffice = Tag(id: "tag-office-003", title: strings.tagOffice, shortcut: "b", sortOrder: 300.0, createdAt: now, updatedAt: now)
        let tagErrands = Tag(id: "tag-errands-004", title: strings.tagErrands, shortcut: "u", sortOrder: 400.0, createdAt: now, updatedAt: now)
        let tagQuick = Tag(id: "tag-quick-005", title: strings.tagQuick, shortcut: "5", sortOrder: 500.0, createdAt: now, updatedAt: now)

        let tags = [tagImportant, tagHome, tagOffice, tagErrands, tagQuick]

        let areas = [
            Area(id: "area-work-001", title: strings.areaWork, sortOrder: 1000.0, tagIds: [tagOffice.id], createdAt: now, updatedAt: now),
            Area(id: "area-home-002", title: strings.areaPersonal, sortOrder: 2000.0, tagIds: [tagHome.id], createdAt: now, updatedAt: now)
        ]

        let projects = [
            Project(
                id: "proj-home-002",
                areaId: "area-work-001",
                title: strings.projHomeTitle,
                notes: strings.projHomeNotes,
                status: .active,
                scheduleState: .today,
                whenDate: todayEpoch,
                deadline: inThreeDaysEpoch,
                sortOrder: 1000.0,
                tagIds: [tagOffice.id, tagImportant.id],
                createdAt: now,
                updatedAt: now
            ),
            Project(
                id: "proj-vacation-001",
                areaId: "area-home-002",
                title: strings.projVacationTitle,
                notes: strings.projVacationNotes,
                status: .active,
                scheduleState: .today,
                whenDate: todayEpoch,
                deadline: inFiveDaysEpoch,
                sortOrder: 2000.0,
                tagIds: [tagImportant.id, tagHome.id],
                createdAt: now,
                updatedAt: now
            )
        ]

        let headings = [
            Heading(id: "head-everyday-003", projectId: "proj-home-002", title: strings.headEveryday, sortOrder: 1000.0, createdAt: now, updatedAt: now),
            Heading(id: "head-booking-001", projectId: "proj-vacation-001", title: strings.headBooking, sortOrder: 1000.0, createdAt: now, updatedAt: now),
            Heading(id: "head-packing-002", projectId: "proj-vacation-001", title: strings.headPacking, sortOrder: 2000.0, createdAt: now, updatedAt: now)
        ]

        let weeklyRecurrence = RecurrenceRule(
            mode: .fixedSchedule,
            interval: 1,
            unit: .weeks,
            daysOfWeek: [1, 5]
        )

        let afterCompletionRecurrence = RecurrenceRule(
            mode: .afterCompletion,
            interval: 4,
            unit: .days
        )

        let checklistItems = [
            ChecklistItem(id: "chk-001", taskId: "task-today-002", title: strings.check1, isCompleted: true, sortOrder: 100.0, createdAt: now, updatedAt: now),
            ChecklistItem(id: "chk-002", taskId: "task-today-002", title: strings.check2, isCompleted: false, sortOrder: 200.0, createdAt: now, updatedAt: now),
            ChecklistItem(id: "chk-003", taskId: "task-today-002", title: strings.check3, isCompleted: false, sortOrder: 300.0, createdAt: now, updatedAt: now)
        ]

        let tasks = [
            TaskItem(
                id: "task-inbox-001",
                title: strings.taskInboxTitle,
                notes: strings.taskInboxNotes,
                status: .open,
                scheduleState: .inbox,
                sortOrder: 1000.0,
                tags: [tagQuick],
                createdAt: now,
                updatedAt: now
            ),
            TaskItem(
                id: "task-today-002",
                title: strings.taskToday1Title,
                notes: strings.taskToday1Notes,
                status: .open,
                scheduleState: .today,
                whenDate: todayEpoch,
                deadline: inThreeDaysEpoch,
                reminderTime: todayReminder15h,
                projectId: "proj-vacation-001",
                headingId: "head-booking-001",
                areaId: "area-home-002",
                sortOrder: 1000.0,
                checklistItems: checklistItems,
                tags: [tagImportant, tagHome],
                createdAt: now,
                updatedAt: now
            ),
            TaskItem(
                id: "task-today-003",
                title: strings.taskToday2Title,
                notes: strings.taskToday2Notes,
                status: .open,
                scheduleState: .today,
                whenDate: todayEpoch,
                projectId: "proj-home-002",
                headingId: "head-everyday-003",
                areaId: "area-work-001",
                recurrenceRule: weeklyRecurrence,
                sortOrder: 2000.0,
                tags: [tagOffice],
                createdAt: now,
                updatedAt: now
            ),
            TaskItem(
                id: "task-evening-004",
                title: strings.taskEveningTitle,
                notes: strings.taskEveningNotes,
                status: .open,
                scheduleState: .thisEvening,
                whenDate: todayEpoch,
                areaId: "area-home-002",
                recurrenceRule: afterCompletionRecurrence,
                sortOrder: 3000.0,
                tags: [tagHome],
                createdAt: now,
                updatedAt: now
            ),
            TaskItem(
                id: "task-upcoming-005",
                title: strings.taskUpcomingTitle,
                notes: strings.taskUpcomingNotes,
                status: .open,
                scheduleState: .upcoming,
                whenDate: tomorrowEpoch,
                deadline: inFiveDaysEpoch,
                projectId: "proj-vacation-001",
                headingId: "head-booking-001",
                areaId: "area-home-002",
                recurrenceRule: weeklyRecurrence,
                sortOrder: 4000.0,
                tags: [tagImportant],
                createdAt: now,
                updatedAt: now
            ),
            TaskItem(
                id: "task-anytime-006",
                title: strings.taskAnytimeTitle,
                notes: strings.taskAnytimeNotes,
                status: .open,
                scheduleState: .anytime,
                projectId: "proj-vacation-001",
                headingId: "head-packing-002",
                areaId: "area-home-002",
                sortOrder: 5000.0,
                tags: [tagHome],
                createdAt: now,
                updatedAt: now
            ),
            TaskItem(
                id: "task-someday-007",
                title: strings.taskSomedayTitle,
                notes: strings.taskSomedayNotes,
                status: .open,
                scheduleState: .someday,
                projectId: "proj-vacation-001",
                headingId: "head-packing-002",
                areaId: "area-home-002",
                sortOrder: 6000.0,
                tags: [tagQuick],
                createdAt: now,
                updatedAt: now
            ),
            TaskItem(
                id: "task-completed-008",
                title: strings.taskCompletedTitle,
                notes: "",
                status: .completed,
                scheduleState: .today,
                whenDate: todayEpoch,
                completedAt: now - 3_600_000,
                projectId: "proj-home-002",
                headingId: "head-everyday-003",
                areaId: "area-work-001",
                sortOrder: 500.0,
                tags: [tagOffice],
                createdAt: now - 7_200_000,
                updatedAt: now
            )
        ]

        return LocalizedSeedBundle(
            tags: tags,
            areas: areas,
            projects: projects,
            headings: headings,
            tasks: tasks,
            checklistItems: checklistItems
        )
    }
}
