package net.bteuk.proxy.commands;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages all Discord commands.
 * Each command is stored in a map so when a command interaction is run it can be routed accordingly.
 */
public class CommandManager extends ListenerAdapter {

    private final List<Command> commands;


    public CommandManager() {

        commands = new ArrayList<>();

    }

    /**
     * Iterates through all the commands to see if any match. If true run the onCommand method for that command.
     *
     * @param event The command event.
     */
    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {

        for (Command command : commands) {
            if (event.getName().equals(command.getName())) {
                command.onCommand(event);
                break;
            }
        }
    }

    @Override
    public void onGuildReady(GuildReadyEvent event) {

        registerCommands(event.getGuild());

    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {

        registerCommands(event.getGuild());

    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {

        //Search for the command.
        for (Command command : commands) {
            if (command.getName().equals(event.getComponentId().split(",")[0])) {
                command.onButtonInteraction(event);
                break;
            }
        }
    }

    /**
     * Registers all the Discord commands. Is called when either the guild is ready, or the bot has joined the guild.
     */
    private void registerCommands(Guild guild) {

        //Create an option for player selection.
        OptionData playerOption = new OptionData(OptionType.STRING, "player", "The player to fetch the plots for", false);

        //Create commands.
        commands.add(new Playerlist("playerlist", "List all online players on the Minecraft server."));
        commands.add(new Map("map", "Sends a link to the UK progress map."));

        commands.add(new ClaimedPlots("claimedplots", "List all plots that are currently claimed.", playerOption));
        commands.add(new SubmittedPlots("submittedplots", "List all plots that are currently submitted.", playerOption));
        commands.add(new ActivePlots("activeplots", "List all plots that are currently claimed or submitted.", playerOption));
        commands.add(new CompletedPlots("completedplots", "List all completed plots.", playerOption));

        List<CommandData> commandData = new ArrayList<>();

        //Add the command data for each command.
        for (Command command : commands) {
            commandData.add(Commands.slash(command.getName(), command.getDescription()).addOptions(command.getOptions()));
        }

        //Add the commands to the guild.
        guild.updateCommands().addCommands(commandData).queue();

    }
}
