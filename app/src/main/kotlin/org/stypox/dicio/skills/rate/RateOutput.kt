package org.stypox.dicio.skills.rate

import org.dicio.skill.context.SkillContext
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.util.getString

sealed interface RateOutput : HeadlineSpeechSkillOutput {

    data class Rated(val like: Boolean, val title: String?, val artist: String?) : RateOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = when {
            title.isNullOrBlank() -> ctx.getString(
                if (like) R.string.skill_rate_liked else R.string.skill_rate_disliked
            )
            artist.isNullOrBlank() -> ctx.getString(
                if (like) R.string.skill_rate_liked_song else R.string.skill_rate_disliked_song,
                title,
            )
            else -> ctx.getString(
                if (like) R.string.skill_rate_liked_song_artist
                else R.string.skill_rate_disliked_song_artist,
                title,
                artist,
            )
        }
    }

    data class Unsupported(
        val like: Boolean,
        val appName: String,
        val availableActions: List<String>,
    ) : RateOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            if (availableActions.isEmpty()) {
                ctx.getString(
                    if (like) R.string.skill_rate_unsupported_like
                    else R.string.skill_rate_unsupported_dislike,
                    appName,
                )
            } else {
                ctx.getString(
                    R.string.skill_rate_unsupported_actions,
                    appName,
                    availableActions.joinToString(", "),
                )
            }
    }
}
