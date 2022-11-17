package net.sonmoosans.jdak.command

import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData
import net.dv8tion.jda.internal.interactions.CommandDataImpl
import net.sonmoosans.jdak.builder.CommandDsl
import net.sonmoosans.jdak.event.SlashCommandContext
import net.sonmoosans.jdak.listener.CommandHandler
import net.sonmoosans.jdak.listener.CommandHandlerChunk
import net.sonmoosans.jdak.listener.CommandKey

@CommandDsl
interface CommandGroup {
    val subcommands: MutableList<SubCommand>
}

@CommandDsl
open class CommandNode: OptionsContainer {
    var nameLocale: Map<DiscordLocale, String>? = null
    var descriptionLocale: Map<DiscordLocale, String>? = null
    override val options = arrayListOf<CommandOption>()

    var handler: CommandHandler? = null
        get() {
            val handler = field?: return null

            return {
                parseOptions(this@CommandNode)

                if (filter(this)) {
                    handler.invoke(this)
                }
            }
        }
        private set

    /**
     * filter events
     * If false, skip the event
     */
    var filter: SlashCommandContext.() -> Boolean = { true }

    fun filterEvent(filter: (@CommandDsl SlashCommandContext).() -> Boolean) {
        this.filter = filter
    }

    fun onEvent(handler: (@CommandDsl SlashCommandContext).() -> Unit) {
        this.handler = handler
    }
}

data class SlashCommand(
    val name: String,
    val description: String,
): CommandNode(), ApplicationCommand, CommandGroup {
    override val subcommands = arrayListOf<SubCommand>()
    val subcommandGroups = arrayListOf<SubCommandGroup>()
    override var guildOnly: Boolean = false
    override var permissions: DefaultMemberPermissions? = null

    override fun build(): CommandData {
        return CommandDataImpl(name, description)
            .setGuildOnly(guildOnly)
            .setNameLocalizations(nameLocale?: mapOf())
            .setDescriptionLocalizations(descriptionLocale?: mapOf())
            .addOptions(options.map { it.build() })
            .addSubcommands(subcommands.map { it.build() })
            .addSubcommandGroups(subcommandGroups.map { it.build() })
            .also { command ->
                permissions?.let {
                    command.defaultPermissions = it
                }
            }
    }

    fun buildHandler(chunk: CommandHandlerChunk) {
        val key = CommandKey(name)
        handler?.let { chunk[key] = it }

        for (subcommandGroup in subcommandGroups) {
            subcommandGroup.buildHandler(this.name, chunk)
        }

        for (subcommand in subcommands) {
            val subKey = CommandKey(this.name, subcommand = subcommand.name)

            subcommand.handler?.let { chunk[subKey] = it }
        }
    }
}

data class SubCommandGroup(
    val name: String,
    val description: String
): CommandGroup {
    override val subcommands = arrayListOf<SubCommand>()
    var nameLocale: Map<DiscordLocale, String>? = null
    var descriptionLocale: Map<DiscordLocale, String>? = null

    fun build(): SubcommandGroupData {
        return SubcommandGroupData(name, description)
            .addSubcommands(subcommands.map { it.build() })
            .setNameLocalizations(nameLocale?: mapOf())
            .setDescriptionLocalizations(descriptionLocale?: mapOf())
    }

    fun buildHandler(root: String, chunk: CommandHandlerChunk) {
        for (subcommand in subcommands) {
            val key = CommandKey(root, group = this.name, subcommand = subcommand.name)

            subcommand.handler?.let { chunk[key] = it }
        }
    }
}

data class SubCommand(
    val name: String,
    val description: String,
): CommandNode() {

    fun build(): SubcommandData {
        return SubcommandData(name, description)
            .setNameLocalizations(nameLocale?: mapOf())
            .setDescriptionLocalizations(descriptionLocale?: mapOf())
            .addOptions(options.map { it.build() })
    }
}