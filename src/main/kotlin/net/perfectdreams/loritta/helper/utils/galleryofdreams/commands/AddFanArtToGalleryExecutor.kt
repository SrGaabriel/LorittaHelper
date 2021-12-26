package net.perfectdreams.loritta.helper.utils.galleryofdreams.commands

import net.perfectdreams.discordinteraktions.common.commands.message.MessageCommandExecutor
import net.perfectdreams.discordinteraktions.common.context.commands.ApplicationCommandContext
import net.perfectdreams.discordinteraktions.common.context.commands.GuildApplicationCommandContext
import net.perfectdreams.discordinteraktions.common.entities.messages.Message
import net.perfectdreams.discordinteraktions.declarations.commands.message.MessageCommandExecutorDeclaration
import net.perfectdreams.discordinteraktions.platforms.kord.entities.messages.KordMessage
import net.perfectdreams.loritta.helper.LorittaHelperKord

class AddFanArtToGalleryExecutor(private val m: LorittaHelperKord) : MessageCommandExecutor() {
    companion object : MessageCommandExecutorDeclaration(AddFanArtToGalleryExecutor::class)

    override suspend fun execute(context: ApplicationCommandContext, targetMessage: Message) {
        context.deferChannelMessageEphemerally()

        if (context !is GuildApplicationCommandContext || !context.member.roles.any { it in GalleryOfDreamsUtils.ALLOWED_ROLES }) {
            context.sendEphemeralMessage {
                content = "Você não tem o poder de adicionar fan arts na galeria!"
            }
            return
        }

        targetMessage as KordMessage
        println(targetMessage.data)
        
        context.sendEphemeralMessage {
            content = """Mensagem enviada em ${targetMessage.timestamp}
                Arquivos: ${targetMessage.attachments.size}
                ${targetMessage.attachments.joinToString { "\n" }}
                """
        }
    }
}