package org.stypox.dicio.skills.youtube

import android.media.session.MediaController
import android.media.session.PlaybackState
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.sentences.Sentences.Youtube
import org.stypox.dicio.skills.mediasession.MediaSessionOutput
import org.stypox.dicio.skills.mediasession.MediaSessions
import org.stypox.dicio.util.getString

/**
 * Skips to the next video in the YouTube app specifically, even if another app (e.g. YouTube
 * Music) was the last one to play audio, which is where plain media buttons would go.
 */
class YoutubeSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<Youtube>) :
    StandardRecognizerSkill<Youtube>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: Youtube): SkillOutput {
        val controllers = when (val result = MediaSessions.activeSessions(ctx.android)) {
            MediaSessions.Result.NoPermission -> return MediaSessionOutput.NoPermission
            is MediaSessions.Result.Sessions -> result.controllers
        }

        val youtube = controllers.filter { it.isYoutube() }.let { sessions ->
            sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: sessions.firstOrNull()
        } ?: return YoutubeOutput.NotRunning

        return when (inputData) {
            is Youtube.NextVideo -> {
                val supportedActions = youtube.playbackState?.actions ?: 0L
                if (supportedActions and PlaybackState.ACTION_SKIP_TO_NEXT == 0L) {
                    YoutubeOutput.NoNext
                } else {
                    youtube.transportControls.skipToNext()
                    YoutubeOutput.Next
                }
            }
        }
    }

    // The YouTube app and modified builds of it, but not YouTube Music
    private fun MediaController.isYoutube(): Boolean =
        packageName.contains("youtube", ignoreCase = true) &&
            !packageName.contains("music", ignoreCase = true)
}

sealed interface YoutubeOutput : HeadlineSpeechSkillOutput {
    data object Next : YoutubeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_youtube_next)
    }

    data object NoNext : YoutubeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_youtube_no_next)
    }

    data object NotRunning : YoutubeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_youtube_not_running)
    }
}
