package org.stypox.dicio.skills.dislike

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.Rating
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import androidx.core.content.ContextCompat
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.sentences.Sentences.Dislike
import org.stypox.dicio.skills.notify.NotifyHandler

/**
 * Dislikes the song that is currently playing, by pressing the "dislike" / "thumbs down" button
 * that the music app exposes on its media session (the same buttons shown in the media
 * notification, Android Auto, etc.). Falls back to a thumbs-down rating for apps that accept it.
 */
class DislikeSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<Dislike>) :
    StandardRecognizerSkill<Dislike>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: Dislike): SkillOutput {
        val context = ctx.android
        val sessionManager = ContextCompat.getSystemService(context, MediaSessionManager::class.java)
            ?: return DislikeOutput.NoSession

        val controllers = try {
            // Notification access granted to NotifyHandler is what authorizes this call
            sessionManager.getActiveSessions(ComponentName(context, NotifyHandler::class.java))
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification access not granted", e)
            return DislikeOutput.NoPermission
        }

        val controller = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull() ?: return DislikeOutput.NoSession

        val metadata = controller.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val actions = controller.playbackState?.customActions.orEmpty()

        // Logged so the exact button names an app uses can be checked with `adb logcat`
        Log.i(TAG, "${controller.packageName} custom actions: " + actions.joinToString {
            "${it.name} (${it.action})"
        })

        val dislikeAction = actions.firstOrNull {
            DISLIKE_REGEX.containsMatchIn(it.action) || DISLIKE_REGEX.containsMatchIn(it.name)
        }
        if (dislikeAction != null) {
            controller.transportControls.sendCustomAction(dislikeAction, null)
            return DislikeOutput.Disliked(title, artist)
        }

        if (controller.ratingType == Rating.RATING_THUMB_UP_DOWN) {
            controller.transportControls.setRating(Rating.newThumbRating(false))
            return DislikeOutput.Disliked(title, artist)
        }

        return DislikeOutput.Unsupported(
            appName = appLabel(context, controller),
            availableActions = actions.map { it.name.toString() }.filter { it.isNotBlank() },
        )
    }

    private fun appLabel(context: Context, controller: MediaController): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(controller.packageName, 0)).toString()
    } catch (e: Exception) {
        controller.packageName
    }

    companion object {
        val TAG: String = DislikeSkill::class.simpleName!!

        // Matches button names/IDs like "Dislike", "Thumbs down", "THUMB_DOWN", "thumbs_down".
        // Deliberately excludes "remove dislike"/"undislike" style toggles.
        private val DISLIKE_REGEX = Regex(
            "(?<!un)(?<!remove )dislike|thumbs?[ _-]?down",
            RegexOption.IGNORE_CASE,
        )
    }
}
