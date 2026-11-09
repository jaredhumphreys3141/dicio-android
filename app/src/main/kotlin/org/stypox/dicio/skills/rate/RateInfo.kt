package org.stypox.dicio.skills.rate

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbsUpDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Permission
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.util.PERMISSION_NOTIFICATION_LISTENER

object RateInfo : SkillInfo("rate") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_rate)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_rate)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.ThumbsUpDown)

    // Notification access is what lets Dicio see and control other apps' media sessions.
    override val neededPermissions: List<Permission>
            = listOf(PERMISSION_NOTIFICATION_LISTENER)

    override fun build(ctx: SkillContext): Skill<*>? {
        val data = Sentences.Rate[ctx.sentencesLanguage] ?: return null
        return RateSkill(RateInfo, data)
    }
}
