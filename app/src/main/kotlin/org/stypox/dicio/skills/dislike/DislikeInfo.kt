package org.stypox.dicio.skills.dislike

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Permission
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.util.PERMISSION_NOTIFICATION_LISTENER

object DislikeInfo : SkillInfo("dislike") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_dislike)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_dislike)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.ThumbDown)

    // Notification access is what lets Dicio see and control other apps' media sessions.
    override val neededPermissions: List<Permission>
            = listOf(PERMISSION_NOTIFICATION_LISTENER)

    override fun build(ctx: SkillContext): Skill<*>? {
        val data = Sentences.Dislike[ctx.sentencesLanguage] ?: return null
        return DislikeSkill(DislikeInfo, data)
    }
}
