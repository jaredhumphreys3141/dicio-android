package org.stypox.dicio.skills.mediasession

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import androidx.core.content.ContextCompat
import org.dicio.skill.context.SkillContext
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.skills.notify.NotifyHandler
import org.stypox.dicio.util.getString

/**
 * Access to other apps' media sessions. Authorized by the notification access that the user grants
 * to [NotifyHandler] (the same permission the notify skill uses).
 */
object MediaSessions {
    private const val TAG = "MediaSessions"

    sealed interface Result {
        data class Sessions(val controllers: List<MediaController>) : Result
        data object NoPermission : Result
    }

    fun activeSessions(context: Context): Result {
        val manager = ContextCompat.getSystemService(context, MediaSessionManager::class.java)
            ?: return Result.Sessions(emptyList())
        return try {
            Result.Sessions(
                manager.getActiveSessions(ComponentName(context, NotifyHandler::class.java))
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification access not granted", e)
            Result.NoPermission
        }
    }

    /** The session that is currently playing, or else the most recently active one. */
    fun List<MediaController>.current(): MediaController? =
        firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: firstOrNull()

    fun MediaController.title(): String? = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)

    fun MediaController.artist(): String? = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)

    fun MediaController.appLabel(context: Context): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: Exception) {
        packageName
    }
}

/** Shared spoken replies for when no session can be controlled. */
sealed interface MediaSessionOutput : HeadlineSpeechSkillOutput {
    data object NoSession : MediaSessionOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_media_session_none)
    }

    data object NoPermission : MediaSessionOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_media_session_no_permission)
    }
}
