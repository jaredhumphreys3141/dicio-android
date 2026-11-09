package org.stypox.dicio.skills.youtube

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Permission
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.util.PERMISSION_NOTIFICATION_LISTENER

object YoutubeInfo : SkillInfo("youtube") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_youtube)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_youtube)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.SkipNext)

    // Needed to find YouTube's media session among the other active ones.
    override val neededPermissions: List<Permission>
            = listOf(PERMISSION_NOTIFICATION_LISTENER)

    override fun build(ctx: SkillContext): Skill<*>? {
        val data = Sentences.Youtube[ctx.sentencesLanguage] ?: return null
        return YoutubeSkill(YoutubeInfo, data)
    }
}
