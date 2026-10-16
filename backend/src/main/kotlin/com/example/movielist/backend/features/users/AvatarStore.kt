package com.example.movielist.backend.features.users

import com.example.movielist.backend.plugins.ApiException
import com.example.movielist.backend.plugins.ValidationException
import io.ktor.http.HttpStatusCode
import java.io.File
import java.util.UUID

class AvatarTooLargeException :
    ApiException(HttpStatusCode.PayloadTooLarge, "TOO_LARGE", "A profile picture can be at most ${AvatarStore.MAX_BYTES / 1024 / 1024} MB")

/**
 * Profile pictures as files in [directory] (a Docker volume in production). Each upload gets a
 * new random name, so the old picture can be cached forever and a new one shows at once. Only
 * JPEG, PNG and WebP are accepted, recognised by their first bytes rather than trusting the
 * client's content type. The app already crops and shrinks pictures before sending them.
 */
class AvatarStore(private val directory: File) {

    init {
        directory.mkdirs()
    }

    /** Saves [bytes] and returns the URL path the picture is served at. */
    fun save(userId: UUID, bytes: ByteArray): String {
        if (bytes.size > MAX_BYTES) throw AvatarTooLargeException()
        val extension = extensionOf(bytes) ?: throw ValidationException("A profile picture must be a JPEG, PNG or WebP image")
        val name = "$userId-${UUID.randomUUID()}.$extension"
        File(directory, name).writeBytes(bytes)
        return PATH_PREFIX + name
    }

    /** Deletes the picture at [path] if it's one of ours; anything else is ignored. */
    fun delete(path: String?) {
        val name = path?.removePrefix(PATH_PREFIX)?.takeIf { it != path && NAME.matches(it) } ?: return
        File(directory, name).delete()
    }

    /** The file for a served [name], or null for anything that isn't a stored picture's name. */
    fun file(name: String): File? = name.takeIf { NAME.matches(it) }?.let { File(directory, it) }?.takeIf { it.isFile }

    private fun extensionOf(bytes: ByteArray): String? {
        fun startsWith(vararg prefix: Int) = bytes.size >= prefix.size && prefix.indices.all { bytes[it] == prefix[it].toByte() }
        return when {
            startsWith(0xFF, 0xD8, 0xFF) -> "jpg"
            startsWith(0x89, 0x50, 0x4E, 0x47) -> "png"
            bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
            else -> null
        }
    }

    companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
        const val PATH_PREFIX = "/avatars/"

        /** `<user id>-<random id>.<ext>`: nothing else, so no path tricks like `../`. */
        private val NAME = Regex("^[0-9a-f-]{36}-[0-9a-f-]{36}\\.(jpg|png|webp)$")
    }
}

/**
 * Checks a requested avatar emoji and colour, for people and groups alike. Returns them trimmed
 * (colour upper-cased); null stays null (no change) and blank stays blank (remove it).
 */
fun validAvatarParts(emoji: String?, color: String?): Pair<String?, String?> {
    val e = emoji?.trim()
    val c = color?.trim()
    if (!e.isNullOrEmpty() && (e.length > 16 || e.any { it.isLetterOrDigit() })) throw ValidationException("Pick a single emoji")
    if (!c.isNullOrEmpty() && !Regex("^#[0-9A-Fa-f]{6}$").matches(c)) throw ValidationException("Colour must look like #RRGGBB")
    return e to c?.uppercase()
}
