package net.perfectdreams.loritta.helper.utils.tickets

import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.entity.ArchiveDuration
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Overwrite
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.Optional
import dev.kord.common.entity.optional.OptionalBoolean
import dev.kord.common.entity.optional.OptionalInt
import dev.kord.common.entity.optional.OptionalSnowflake
import dev.kord.common.entity.optional.optional
import dev.kord.rest.json.request.ListThreadsByTimestampRequest
import dev.kord.rest.json.request.StartThreadRequest
import dev.kord.rest.route.Route
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.perfectdreams.discordinteraktions.api.entities.User
import net.perfectdreams.discordinteraktions.common.components.buttons.ButtonClickExecutorDeclaration
import net.perfectdreams.discordinteraktions.common.components.buttons.ButtonClickWithDataExecutor
import net.perfectdreams.discordinteraktions.common.context.components.ComponentContext
import net.perfectdreams.discordinteraktions.common.context.components.GuildComponentContext
import net.perfectdreams.loritta.api.messages.LorittaReply
import net.perfectdreams.loritta.helper.LorittaHelperKord
import net.perfectdreams.loritta.helper.i18n.I18nKeysData
import net.perfectdreams.loritta.helper.utils.ComponentDataUtils
import java.util.concurrent.TimeUnit

class CreateTicketButtonExecutor(val m: LorittaHelperKord) : ButtonClickWithDataExecutor {
    companion object : ButtonClickExecutorDeclaration(CreateTicketButtonExecutor::class, "create_ticket")

    val recentlyCreatedTickets = Caffeine.newBuilder()
        .expireAfterWrite(5L, TimeUnit.MINUTES)
        .build<Snowflake, Long>()
        .asMap()

    override suspend fun onClick(user: User, context: ComponentContext, data: String) {
        if (context is GuildComponentContext) {
            val ticketLanguageData = ComponentDataUtils.decode<TicketLanguageData>(data)
            val language = ticketLanguageData.language.getI18nContext(m)

            // Avoid users closing and reopening threads constantly
            val lastTicketCreatedAt = recentlyCreatedTickets[user.id]

            if (lastTicketCreatedAt != null) {
                context.sendEphemeralMessage {
                    // 300 = 5 minutes
                    content = language.get(
                        I18nKeysData.Tickets.YouAlreadyCreatedATicketRecently(
                            "<:lori_sob:556524143281963008>",
                            "<t:${(lastTicketCreatedAt / 1000) + 300}:R>"
                        )
                    )
                }
                return
            }
            recentlyCreatedTickets[user.id] = System.currentTimeMillis()

            context.sendEphemeralMessage {
                content = language.get(I18nKeysData.Tickets.CreatingATicket)
            }

            var ticketThread = m.helperRest.guild.listActiveThreads(context.guildId)
                .threads
                .firstOrNull {
                    val name = it.name.value ?: return@firstOrNull false
                    if (!name.contains("(") && !name.contains(")"))
                        return@firstOrNull false

                    val onlyTheId = name.substringAfterLast("(").substringBeforeLast(")")
                    onlyTheId.toULongOrNull() == context.sender.id.value
                }

            val wasAnAlreadyActiveThread = ticketThread != null

            if (ticketThread == null) {
                // Active Ticket Thread is null, let's try finding an archived thread!
                var searchedAll = false
                var lastInstant: Instant? = null

                while (!searchedAll && ticketThread == null) {
                    val result = m.helperRest.channel.listPrivateArchivedThreads(
                        context.channelId,
                        ListThreadsByTimestampRequest(
                            before = lastInstant
                        )
                    )

                    ticketThread = result
                        .threads
                        .firstOrNull {
                            val name = it.name.value ?: return@firstOrNull false
                            if (!name.contains("(") && !name.contains(")"))
                                return@firstOrNull false

                            val onlyTheId = name.substringAfterLast("(").substringBeforeLast(")")
                            onlyTheId.toULongOrNull() == context.sender.id.value
                        }

                    searchedAll = result.threads.isEmpty()
                    if (!searchedAll)
                        lastInstant = result.threads.last().id.timestamp
                }
            }

            // Max username size = 32
            // Max ID length (well it can be bigger): 18
            // So if we do the sum of everything...
            // 3 (beginning) + 32 (username) + 2 (space and "(") + 18 (user ID) + 1 (")")
            // = 56
            // Threads can have at most 100 chars!
            val threadName = "\uD83D\uDCE8 ${user.name} (${user.id.value})"

            if (ticketThread == null) {
                // If it is STILL null, we will create a thread!
                ticketThread = m.helperRest.channel.startThread(
                    context.channelId,
                    StartThreadRequest(
                        threadName,
                        ArchiveDuration.Day,
                        ChannelType.PrivateThread.optional(),
                    ),
                    "Ticket created for <@${user.id.value}>"
                )
            }

            // Hacky workaround, because it looks like Discord Mobile gets kinda confused and doesn't allow the user to send a message, weird...
            m.helperRest.channel.addUserToThread(
                ticketThread.id,
                user.id
            )

            // Update thread metadata and name juuuust to be sure
            // Makeshift hack while Kord does not support updating thread metadata
            m.helperRest.unsafe(Route.ChannelPatch) {
                keys[Route.ChannelId] = ticketThread.id
                body(
                    ChannelModifyPatchRequestMakeshift.serializer(),
                    ChannelModifyPatchRequestMakeshift(
                        name = threadName.optional(),
                        archived = false.optional(),
                        locked = false.optional(), // For now let's keep it as not locked to avoid a bug in Discord Mobile related to "You don't have permission!"
                        invitable = false.optional()
                    )
                )
            }

            // Only resend the message if the thread was archived or if it is a new thread
            if (!wasAnAlreadyActiveThread)
                m.helperRest.channel.createMessage(
                    ticketThread.id
                ) {
                    content = (
                            listOf(
                                LorittaReply(
                                    language.get(I18nKeysData.Tickets.ThreadCreated.Ready),
                                    "<:lori_coffee:727631176432484473>",
                                    mentionUser = true
                                ),
                                LorittaReply(
                                    language.get(I18nKeysData.Tickets.ThreadCreated.QuestionTips("<@&${ticketLanguageData.language.supportRoleId.value}>")),
                                    "<:lori_coffee:727631176432484473>",
                                    mentionUser = false
                                ),
                                LorittaReply(
                                    "**${language.get(I18nKeysData.Tickets.ThreadCreated.PleaseRead("<#${ticketLanguageData.language.faqChannelId.value}>", "<https://loritta.website/extras>"))}**",
                                    "<:lori_analise:853052040425766922>",
                                    mentionUser = false
                                ),
                                LorittaReply(
                                    language.get(I18nKeysData.Tickets.ThreadCreated.AfterAnswer),
                                    "<a:lori_pat:706263175892566097>",
                                    mentionUser = false
                                )
                            )
                            )
                        .joinToString("\n")
                        { it.build(context.sender) }
                }

            context.sendEphemeralMessage {
                content = language.get(
                    I18nKeysData.Tickets.TicketWasCreated("<#${ticketThread.id.value}>")
                )            }
        }
    }

    @Serializable
    data class ChannelModifyPatchRequestMakeshift(
        val name: Optional<String> = Optional.Missing(),
        val position: OptionalInt? = OptionalInt.Missing,
        val topic: Optional<String?> = Optional.Missing(),
        val nsfw: OptionalBoolean? = OptionalBoolean.Missing,
        @SerialName("rate_limit_per_user")
        val rateLimitPerUser: OptionalInt? = OptionalInt.Missing,
        val bitrate: OptionalInt? = OptionalInt.Missing,
        @SerialName("user_limit")
        val userLimit: OptionalInt? = OptionalInt.Missing,
        @SerialName("permission_overwrites")
        val permissionOverwrites: Optional<Set<Overwrite>?> = Optional.Missing(),
        @SerialName("parent_id")
        val parentId: OptionalSnowflake? = OptionalSnowflake.Missing,
        val archived: OptionalBoolean = OptionalBoolean.Missing,
        @SerialName("auto_archive_duration")
        val autoArchiveDuration: OptionalInt = OptionalInt.Missing,
        val locked: OptionalBoolean = OptionalBoolean.Missing,
        val invitable: OptionalBoolean = OptionalBoolean.Missing,
    )
}