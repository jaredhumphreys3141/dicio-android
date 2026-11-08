package org.stypox.dicio.skills.dislike

import org.dicio.skill.context.SkillContext
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.util.getString

sealed interface DislikeOutput : HeadlineSpeechSkillOutput {

    data class Disliked(val title: String?, val artist: String?) : DislikeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = when {
            title.isNullOrBlank() -> ctx.getString(R.string.skill_dislike_done)
            artist.isNullOrBlank() -> ctx.getString(R.string.skill_dislike_done_song, title)
            else -> ctx.getString(R.string.skill_dislike_done_song_artist, title, artist)
        }
    }

    data object NoSession : DislikeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_dislike_no_session)
    }

    data object NoPermission : DislikeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_dislike_no_permission)
    }

    data class Unsupported(
        val appName: String,
        val availableActions: List<String>,
    ) : DislikeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            if (availableActions.isEmpty()) {
                ctx.getString(R.string.skill_dislike_unsupported, appName)
            } else {
                ctx.getString(
                    R.string.skill_dislike_unsupported_actions,
                    appName,
                    availableActions.joinToString(", "),
                )
            }
    }
}
