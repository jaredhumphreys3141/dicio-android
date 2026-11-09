package org.stypox.dicio.skills.rate

import android.media.Rating
import android.util.Log
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.sentences.Sentences.Rate
import org.stypox.dicio.skills.mediasession.MediaSessionOutput
import org.stypox.dicio.skills.mediasession.MediaSessions
import org.stypox.dicio.skills.mediasession.MediaSessions.appLabel
import org.stypox.dicio.skills.mediasession.MediaSessions.artist
import org.stypox.dicio.skills.mediasession.MediaSessions.current
import org.stypox.dicio.skills.mediasession.MediaSessions.title

/**
 * Likes or dislikes the song that is currently playing, by pressing the thumbs up/down button that
 * the music app exposes on its media session (the same buttons shown in the media notification,
 * Android Auto, etc.). Falls back to a thumbs rating for apps that accept one.
 */
class RateSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<Rate>) :
    StandardRecognizerSkill<Rate>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: Rate): SkillOutput {
        val like = inputData is Rate.ThumbsUp

        val controller = when (val result = MediaSessions.activeSessions(ctx.android)) {
            MediaSessions.Result.NoPermission -> return MediaSessionOutput.NoPermission
            is MediaSessions.Result.Sessions -> result.controllers.current()
        } ?: return MediaSessionOutput.NoSession

        val actions = controller.playbackState?.customActions.orEmpty()
        // Logged so the exact button names an app uses can be checked with `adb logcat`
        Log.i(TAG, "${controller.packageName} custom actions: " + actions.joinToString {
            "${it.name} (${it.action})"
        })

        val regex = if (like) LIKE_REGEX else DISLIKE_REGEX
        val action = actions.firstOrNull {
            regex.containsMatchIn(it.action) || regex.containsMatchIn(it.name)
        }
        if (action != null) {
            controller.transportControls.sendCustomAction(action, null)
            return RateOutput.Rated(like, controller.title(), controller.artist())
        }

        if (controller.ratingType == Rating.RATING_THUMB_UP_DOWN) {
            controller.transportControls.setRating(Rating.newThumbRating(like))
            return RateOutput.Rated(like, controller.title(), controller.artist())
        }

        return RateOutput.Unsupported(
            like = like,
            appName = controller.appLabel(ctx.android),
            availableActions = actions.map { it.name.toString() }.filter { it.isNotBlank() },
        )
    }

    companion object {
        val TAG: String = RateSkill::class.simpleName!!

        // Matches names/IDs like "Like", "Thumbs up", "THUMB_UP", "thumbs_up", but not "Dislike",
        // "Unlike", "Liked" or "Remove like", which would undo or do the opposite.
        private val LIKE_REGEX = Regex(
            "(?<![a-z])(?<!remove )like(?![a-z])|thumbs?[ _-]?up",
            RegexOption.IGNORE_CASE,
        )

        // Matches names/IDs like "Dislike", "Thumbs down", "THUMB_DOWN", "thumbs_down", but not
        // "Undislike", "Disliked" or "Remove dislike".
        private val DISLIKE_REGEX = Regex(
            "(?<!un)(?<!remove )dislike(?!d)|thumbs?[ _-]?down",
            RegexOption.IGNORE_CASE,
        )
    }
}
