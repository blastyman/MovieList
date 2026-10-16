package com.example.movielist.backend.features.users

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

/**
 * The uploaded picture: the request body itself (no multipart). Reads at most one byte past
 * [AvatarStore.MAX_BYTES], so a body without a declared length can't run away; [AvatarStore.save]
 * then turns anything too big into a 413.
 */
suspend fun ApplicationCall.receiveAvatar(): ByteArray {
    val declared = request.contentLength()
    if (declared != null && declared > AvatarStore.MAX_BYTES) throw AvatarTooLargeException()
    return receiveChannel().readRemaining(AvatarStore.MAX_BYTES + 1L).readByteArray()
}
