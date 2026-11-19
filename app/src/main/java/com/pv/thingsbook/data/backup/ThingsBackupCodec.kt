package com.pv.thingsbook.data.backup

import com.pv.thingsbook.domain.model.ProjectStatus
import com.pv.thingsbook.domain.model.ScheduleState
import com.pv.thingsbook.domain.model.TaskStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class InvalidBackupFormatException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
class BackupPasswordRequiredException(message: String) : IllegalStateException(message)
class InvalidBackupPasswordException(message: String, cause: Throwable? = null) : SecurityException(message, cause)

@Serializable
data class ThingsbookBackupEnvelope(
    val magic: String = ThingsBackupCodec.BACKUP_MAGIC,
    val formatVersion: Int = ThingsBackupCodec.FORMAT_VERSION,
    val appVersionName: String = "",
    val appVersionCode: Int = 0,
    val createdAt: Long = 0L,
    val encrypted: Boolean = false,
    val kdf: String? = null,
    val kdfIterations: Int? = null,
    val saltBase64: String? = null,
    val cipher: String? = null,
    val ivBase64: String? = null,
    val gcmTagBits: Int? = null,
    val payloadGzipBase64: String? = null,
    val ciphertextBase64: String? = null
)

@Serializable
data class ThingsbookBackupPayload(
    val schemaVersion: Int = ThingsBackupCodec.FORMAT_VERSION,
    val createdAt: Long = 0L,
    val appVersionName: String = "",
    val areas: List<BackupAreaDto> = emptyList(),
    val projects: List<BackupProjectDto> = emptyList(),
    val headings: List<BackupHeadingDto> = emptyList(),
    val tasks: List<BackupTaskDto> = emptyList(),
    val checklistItems: List<BackupChecklistItemDto> = emptyList(),
    val tags: List<BackupTagDto> = emptyList(),
    val taskTagRefs: List<BackupTaskTagRefDto> = emptyList(),
    val projectTagRefs: List<BackupProjectTagRefDto> = emptyList(),
    val attachments: List<BackupAttachmentDto> = emptyList(),
    val projectTemplates: List<BackupProjectTemplateDto> = emptyList(),
    val preferences: BackupPreferencesDto = BackupPreferencesDto()
)

@Serializable
data class BackupAreaDto(
    val id: String,
    val title: String,
    val sortOrder: Double = 0.0,
    val tagIdsJson: String = "[]",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class BackupProjectDto(
    val id: String,
    val areaId: String? = null,
    val title: String,
    val notes: String = "",
    val status: String = ProjectStatus.ACTIVE.name,
    val scheduleState: String = ScheduleState.ANYTIME.name,
    val whenDate: Long? = null,
    val deadline: Long? = null,
    val completedAt: Long? = null,
    val lastReviewedAt: Long? = null,
    val sortOrder: Double = 0.0,
    val tagIdsJson: String = "[]",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class BackupHeadingDto(
    val id: String,
    val projectId: String,
    val title: String,
    val endDate: Long? = null,
    val sortOrder: Double = 0.0,
    val isArchived: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class BackupTaskDto(
    val id: String,
    val title: String,
    val notes: String = "",
    val status: String = TaskStatus.OPEN.name,
    val scheduleState: String = ScheduleState.INBOX.name,
    val whenDate: Long? = null,
    val deadline: Long? = null,
    val reminderTime: Long? = null,
    val scheduledStartMinutes: Int? = null,
    val durationMinutes: Int? = null,
    val completedAt: Long? = null,
    val projectId: String? = null,
    val headingId: String? = null,
    val areaId: String? = null,
    val recurrenceRuleJson: String? = null,
    val sortOrder: Double = 0.0,
    val tagIdsJson: String = "[]",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class BackupChecklistItemDto(
    val id: String,
    val taskId: String,
    val title: String,
    val isCompleted: Boolean = false,
    val sortOrder: Double = 0.0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class BackupTagDto(
    val id: String,
    val title: String,
    val parentTagId: String? = null,
    val shortcut: String? = null,
    val sortOrder: Double = 0.0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class BackupTaskTagRefDto(
    val taskId: String,
    val tagId: String
)

@Serializable
data class BackupProjectTagRefDto(
    val projectId: String,
    val tagId: String
)

@Serializable
data class BackupAttachmentDto(
    val id: String,
    val taskId: String? = null,
    val projectId: String? = null,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val localRelativePath: String = "",
    val dataBase64: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class BackupProjectTemplateDto(
    val id: String,
    val title: String,
    val notes: String = "",
    val defaultAreaId: String? = null,
    val tagIdsJson: String = "[]",
    val blueprintJson: String = "{}",
    val sortOrder: Double = 0.0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class BackupPreferencesDto(
    val sidebarVisible: Boolean? = null,
    val dailyDigestEnabled: Boolean? = null,
    val dailyDigestHour: Int? = null,
    val dailyDigestMinute: Int? = null,
    val languageTag: String? = null,
    val calendarEventsEnabled: Boolean? = null,
    val selectedCalendarIds: List<String>? = null,
    val groupTodayByProjectOrArea: Boolean? = null,
    val lastWeeklyReviewAt: Long? = null,
    val includeBetaUpdates: Boolean? = null
)

data class BackupSummary(
    val createdAt: Long,
    val appVersionName: String,
    val encrypted: Boolean,
    val areasCount: Int = 0,
    val projectsCount: Int = 0,
    val headingsCount: Int = 0,
    val tasksCount: Int = 0,
    val checklistItemsCount: Int = 0,
    val tagsCount: Int = 0,
    val attachmentsCount: Int = 0,
    val projectTemplatesCount: Int = 0
)

sealed interface BackupInspectionResult {
    val createdAt: Long
    val appVersionName: String

    data class PasswordProtected(
        override val createdAt: Long,
        override val appVersionName: String
    ) : BackupInspectionResult

    data class UnencryptedPreview(
        override val createdAt: Long,
        override val appVersionName: String,
        val summary: BackupSummary
    ) : BackupInspectionResult
}

/**
 * Self-contained Backup Encoder/Decoder with optional strong Password Encryption:
 * - Key Derivation: `PBKDF2WithHmacSHA256` (120,000 iterations, 128-bit random salt, 256-bit AES key)
 * - Authenticated Encryption: `AES/GCM/NoPadding` (96-bit random IV, 128-bit authentication tag, AAD header binding)
 * - Payload Compression: GZIP with strict 120 MB decompression bound against Zip-Bomb / OOM inputs.
 */
object ThingsBackupCodec {
    const val BACKUP_MAGIC = "THINGSBOOK_BACKUP_V1"
    const val FORMAT_VERSION = 1
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KDF_ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    const val MAX_DECOMPRESSED_BYTES = 120 * 1024 * 1024 // 120 MB guard

    private val secureRandom = SecureRandom()

    private fun tr(en: String, de: String, fr: String, it: String, es: String): String {
        return when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
            "de" -> de
            "fr" -> fr
            "it" -> it
            "es" -> es
            else -> en
        }
    }

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val envelopePrettyJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun summarizePayload(payload: ThingsbookBackupPayload, encrypted: Boolean): BackupSummary =
        BackupSummary(
            createdAt = payload.createdAt,
            appVersionName = payload.appVersionName,
            encrypted = encrypted,
            areasCount = payload.areas.size,
            projectsCount = payload.projects.size,
            headingsCount = payload.headings.size,
            tasksCount = payload.tasks.size,
            checklistItemsCount = payload.checklistItems.size,
            tagsCount = payload.tags.size,
            attachmentsCount = payload.attachments.size,
            projectTemplatesCount = payload.projectTemplates.size
        )

    fun encodeBackup(
        payload: ThingsbookBackupPayload,
        password: CharArray?,
        appVersionName: String,
        appVersionCode: Int
    ): ByteArray {
        val rawJsonBytes = json.encodeToString(payload).toByteArray(StandardCharsets.UTF_8)
        val compressedBytes = gzipCompress(rawJsonBytes)
        val createdAt = payload.createdAt

        val hasPassword = password != null && password.isNotEmpty()
        val envelope = if (!hasPassword) {
            ThingsbookBackupEnvelope(
                magic = BACKUP_MAGIC,
                formatVersion = FORMAT_VERSION,
                appVersionName = appVersionName,
                appVersionCode = appVersionCode,
                createdAt = createdAt,
                encrypted = false,
                payloadGzipBase64 = Base64.getEncoder().encodeToString(compressedBytes)
            )
        } else {
            val nonNullPwd = password!!
            val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
            val iv = ByteArray(IV_BYTES).also { secureRandom.nextBytes(it) }
            val keyBytes = deriveAesKey(nonNullPwd, salt, KDF_ITERATIONS)
            val ciphertext = try {
                val secretKey = SecretKeySpec(keyBytes, "AES")
                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.updateAAD(buildAad(BACKUP_MAGIC, FORMAT_VERSION))
                cipher.doFinal(compressedBytes)
            } finally {
                keyBytes.fill(0)
            }

            ThingsbookBackupEnvelope(
                magic = BACKUP_MAGIC,
                formatVersion = FORMAT_VERSION,
                appVersionName = appVersionName,
                appVersionCode = appVersionCode,
                createdAt = createdAt,
                encrypted = true,
                kdf = KDF_ALGORITHM,
                kdfIterations = KDF_ITERATIONS,
                saltBase64 = Base64.getEncoder().encodeToString(salt),
                cipher = CIPHER_TRANSFORMATION,
                ivBase64 = Base64.getEncoder().encodeToString(iv),
                gcmTagBits = GCM_TAG_BITS,
                ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext)
            )
        }

        return envelopePrettyJson.encodeToString(envelope).toByteArray(StandardCharsets.UTF_8)
    }

    fun inspectBackup(rawBytes: ByteArray): BackupInspectionResult {
        val envelope = parseAndValidateEnvelope(rawBytes)
        return if (envelope.encrypted) {
            BackupInspectionResult.PasswordProtected(
                createdAt = envelope.createdAt,
                appVersionName = envelope.appVersionName
            )
        } else {
            val payload = decodePayloadFromEnvelope(envelope, password = null)
            BackupInspectionResult.UnencryptedPreview(
                createdAt = envelope.createdAt.takeIf { it > 0L } ?: payload.createdAt,
                appVersionName = envelope.appVersionName.ifBlank { payload.appVersionName },
                summary = summarizePayload(payload, encrypted = false)
            )
        }
    }

    fun decodeBackup(rawBytes: ByteArray, password: CharArray?): ThingsbookBackupPayload {
        val envelope = parseAndValidateEnvelope(rawBytes)
        return decodePayloadFromEnvelope(envelope, password)
    }

    private fun parseAndValidateEnvelope(rawBytes: ByteArray): ThingsbookBackupEnvelope {
        if (rawBytes.isEmpty()) {
            throw InvalidBackupFormatException(
                tr(
                    en = "The selected backup file is empty.",
                    de = "Die ausgewählte Backup-Datei ist leer.",
                    fr = "Le fichier de sauvegarde sélectionné est vide.",
                    it = "Il file di backup selezionato è vuoto.",
                    es = "El archivo de copia de seguridad seleccionado está vacío."
                )
            )
        }
        val text = try {
            String(rawBytes, StandardCharsets.UTF_8).removePrefix("\uFEFF").trim()
        } catch (t: Throwable) {
            throw InvalidBackupFormatException(
                tr(
                    en = "Could not read the backup file.",
                    de = "Die Datei konnte nicht gelesen werden.",
                    fr = "Impossible de lire le fichier de sauvegarde.",
                    it = "Impossibile leggere il file di backup.",
                    es = "No se pudo leer el archivo de copia de seguridad."
                ),
                t
            )
        }
        val envelope = try {
            json.decodeFromString<ThingsbookBackupEnvelope>(text)
        } catch (t: Throwable) {
            throw InvalidBackupFormatException(
                tr(
                    en = "Invalid file format: This is not a valid Thingsbook backup file.",
                    de = "Ungültiges Dateiformat: Dies ist keine gültige Thingsbook-Backup-Datei.",
                    fr = "Format de fichier invalide : ce n'est pas un fichier de sauvegarde Thingsbook valide.",
                    it = "Formato file non valido: non è un file di backup Thingsbook valido.",
                    es = "Formato de archivo no válido: no es un archivo de copia de seguridad válido de Thingsbook."
                ),
                t
            )
        }
        if (envelope.magic != BACKUP_MAGIC) {
            throw InvalidBackupFormatException(
                tr(
                    en = "Unknown backup header (${envelope.magic}). Please choose a valid Thingsbook backup file.",
                    de = "Unbekannte Backup-Kennung (${envelope.magic}). Bitte wähle eine gültige Thingsbook-Backup-Datei.",
                    fr = "En-tête de sauvegarde inconnu (${envelope.magic}). Veuillez choisir un fichier Thingsbook valide.",
                    it = "Intestazione di backup sconosciuta (${envelope.magic}). Seleziona un file di backup Thingsbook valido.",
                    es = "Encabezado de copia desconocido (${envelope.magic}). Elige un archivo válido de Thingsbook."
                )
            )
        }
        if (envelope.formatVersion < 1 || envelope.formatVersion > FORMAT_VERSION) {
            throw InvalidBackupFormatException(
                tr(
                    en = "This backup file was created with a newer format (v${envelope.formatVersion}) and cannot be opened.",
                    de = "Diese Backup-Datei stammt aus einer neueren Version (Format v${envelope.formatVersion}) und kann nicht gelesen werden.",
                    fr = "Cette sauvegarde provient d'une version plus récente (format v${envelope.formatVersion}) et ne peut pas être lue.",
                    it = "Questo backup proviene da una versione più recente (formato v${envelope.formatVersion}) e non può essere letto.",
                    es = "Esta copia proviene de una versión más reciente (formato v${envelope.formatVersion}) y no se puede abrir."
                )
            )
        }
        return envelope
    }

    private fun decodePayloadFromEnvelope(
        envelope: ThingsbookBackupEnvelope,
        password: CharArray?
    ): ThingsbookBackupPayload {
        val compressedBytes = if (!envelope.encrypted) {
            val b64 = envelope.payloadGzipBase64?.takeIf { it.isNotBlank() }
                ?: throw InvalidBackupFormatException(
                    tr(
                        en = "The unencrypted backup file contains no payload data.",
                        de = "Die unverschlüsselte Backup-Datei enthält keine Nutzdaten.",
                        fr = "Le fichier de sauvegarde non chiffré ne contient aucune donnée.",
                        it = "Il file di backup non crittografato non contiene dati.",
                        es = "El archivo de copia de seguridad sin cifrar no contiene datos."
                    )
                )
            decodeBase64Safe(b64, "Payload")
        } else {
            if (password == null || password.isEmpty()) {
                throw BackupPasswordRequiredException(
                    tr(
                        en = "This backup file is password-protected. Please enter the password.",
                        de = "Diese Backup-Datei ist passwortgeschützt. Bitte gib das Passwort ein.",
                        fr = "Ce fichier de sauvegarde est protégé par mot de passe. Veuillez saisir le mot de passe.",
                        it = "Questo file di backup è protetto da password. Inserisci la password.",
                        es = "Este archivo de copia de seguridad está protegido con contraseña. Introduce la contraseña."
                    )
                )
            }
            val saltB64 = envelope.saltBase64?.takeIf { it.isNotBlank() }
                ?: throw InvalidBackupFormatException(
                    tr(
                        en = "Encryption salt is missing in backup file.",
                        de = "Verschlüsselungs-Salt fehlt in der Backup-Datei.",
                        fr = "Sel de chiffrement manquant dans la sauvegarde.",
                        it = "Salt di crittografia mancante nel file di backup.",
                        es = "Falta la sal de cifrado en el archivo de copia de seguridad."
                    )
                )
            val ivB64 = envelope.ivBase64?.takeIf { it.isNotBlank() }
                ?: throw InvalidBackupFormatException(
                    tr(
                        en = "Encryption IV is missing in backup file.",
                        de = "Verschlüsselungs-IV fehlt in der Backup-Datei.",
                        fr = "IV de chiffrement manquant dans la sauvegarde.",
                        it = "IV di crittografia mancante nel file di backup.",
                        es = "Falta el IV de cifrado en el archivo de copia de seguridad."
                    )
                )
            val ctB64 = envelope.ciphertextBase64?.takeIf { it.isNotBlank() }
                ?: throw InvalidBackupFormatException(
                    tr(
                        en = "Encrypted ciphertext is missing in backup file.",
                        de = "Verschlüsselte Daten fehlen in der Backup-Datei.",
                        fr = "Données chiffrées manquantes dans la sauvegarde.",
                        it = "Dati crittografati mancanti nel file di backup.",
                        es = "Faltan los datos cifrados en el archivo de copia de seguridad."
                    )
                )

            val salt = decodeBase64Safe(saltB64, "Salt")
            val iv = decodeBase64Safe(ivB64, "IV")
            val ciphertext = decodeBase64Safe(ctB64, "Ciphertext")
            if (salt.size < 8 || iv.size < 8) {
                throw InvalidBackupFormatException(
                    tr(
                        en = "Invalid encryption parameters in backup file.",
                        de = "Ungültige Verschlüsselungsparameter in der Backup-Datei.",
                        fr = "Paramètres de chiffrement invalides dans la sauvegarde.",
                        it = "Parametri di crittografia non validi nel file di backup.",
                        es = "Parámetros de cifrado no válidos en el archivo de copia de seguridad."
                    )
                )
            }
            val iterations = (envelope.kdfIterations ?: KDF_ITERATIONS).coerceIn(10_000, 1_000_000)
            val tagBits = (envelope.gcmTagBits ?: GCM_TAG_BITS).let { if (it in setOf(96, 104, 112, 120, 128)) it else GCM_TAG_BITS }

            val keyBytes = deriveAesKey(password, salt, iterations)
            try {
                val secretKey = SecretKeySpec(keyBytes, "AES")
                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(tagBits, iv))
                cipher.updateAAD(buildAad(envelope.magic, envelope.formatVersion))
                cipher.doFinal(ciphertext)
            } catch (badTag: AEADBadTagException) {
                throw InvalidBackupPasswordException(
                    tr(
                        en = "Incorrect password or tampered backup file. Please check your password.",
                        de = "Falsches Passwort oder manipulierte Backup-Datei. Bitte überprüfe das eingegebene Passwort.",
                        fr = "Mot de passe incorrect ou sauvegarde altérée. Veuillez vérifier le mot de passe.",
                        it = "Password errata o file di backup manomesso. Verifica la password.",
                        es = "Contraseña incorrecta o copia de seguridad alterada. Verifica la contraseña."
                    ),
                    badTag
                )
            } catch (sec: GeneralSecurityException) {
                throw InvalidBackupPasswordException(
                    tr(
                        en = "Decryption failed — the password is incorrect or the file is corrupted.",
                        de = "Entschlüsselung fehlgeschlagen — das Passwort ist falsch oder die Datei ist beschädigt.",
                        fr = "Échec du déchiffrement — mot de passe incorrect ou fichier corrompu.",
                        it = "Decrittografia non riuscita — password errata o file danneggiato.",
                        es = "Error de descifrado: contraseña incorrecta o archivo dañado."
                    ),
                    sec
                )
            } finally {
                keyBytes.fill(0)
            }
        }

        val decompressedBytes = try {
            gzipDecompressBounded(compressedBytes, MAX_DECOMPRESSED_BYTES)
        } catch (e: InvalidBackupFormatException) {
            throw e
        } catch (t: Throwable) {
            throw InvalidBackupFormatException(
                tr(
                    en = "Could not decompress backup payload (corrupted file).",
                    de = "Backup-Inhalt konnte nicht entpackt werden (Datei beschädigt).",
                    fr = "Impossible de décompresser la sauvegarde (fichier corrompu).",
                    it = "Impossibile decomprimere il backup (file danneggiato).",
                    es = "No se pudo descomprimir la copia de seguridad (archivo dañado)."
                ),
                t
            )
        }

        return try {
            val payloadJson = String(decompressedBytes, StandardCharsets.UTF_8)
            json.decodeFromString<ThingsbookBackupPayload>(payloadJson)
        } catch (t: Throwable) {
            throw InvalidBackupFormatException(
                tr(
                    en = "Backup data structure is invalid or corrupted.",
                    de = "Backup-Datenstruktur ist ungültig oder beschädigt.",
                    fr = "La structure des données de sauvegarde est invalide ou corrompue.",
                    it = "La struttura dei dati di backup non è valida o è danneggiata.",
                    es = "La estructura de datos de la copia de seguridad no es válida o está dañada."
                ),
                t
            )
        }
    }

    private fun deriveAesKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun buildAad(magic: String, version: Int): ByteArray =
        "$magic|$version".toByteArray(StandardCharsets.UTF_8)

    private fun decodeBase64Safe(input: String, label: String): ByteArray =
        try {
            Base64.getMimeDecoder().decode(input.trim())
        } catch (t: Throwable) {
            throw InvalidBackupFormatException(
                tr(
                    en = "Invalid Base64 encoding ($label) in backup file.",
                    de = "Ungültige Base64-Kodierung ($label) in der Backup-Datei.",
                    fr = "Encodage Base64 invalide ($label) dans la sauvegarde.",
                    it = "Codifica Base64 non valida ($label) nel file di backup.",
                    es = "Codificación Base64 no válida ($label) en la copia de seguridad."
                ),
                t
            )
        }

    private fun gzipCompress(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { gzip ->
            gzip.write(data)
        }
        return out.toByteArray()
    }

    private fun gzipDecompressBounded(compressed: ByteArray, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gzip ->
            val buffer = ByteArray(8192)
            var totalRead = 0
            while (true) {
                val read = gzip.read(buffer)
                if (read == -1) break
                totalRead += read
                if (totalRead > maxBytes) {
                    throw InvalidBackupFormatException(
                        tr(
                            en = "Backup file exceeds the 120 MB safety limit.",
                            de = "Backup-Datei überschreitet das Sicherheitslimit von 120 MB.",
                            fr = "Le fichier de sauvegarde dépasse la limite de sécurité de 120 Mo.",
                            it = "Il file di backup supera il limite di sicurezza di 120 MB.",
                            es = "El archivo de copia de seguridad supera el límite de seguridad de 120 MB."
                        )
                    )
                }
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }
}
