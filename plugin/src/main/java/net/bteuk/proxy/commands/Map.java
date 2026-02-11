package net.bteuk.proxy.commands;

import net.bteuk.proxy.Proxy;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

public class Map extends AbstractCommand {

    /**
     * Constructor, saved the name and description of the command.
     * Also registers the command in Discord.
     * @param name Name of the command
     * @param description Description of the command
     */
    public Map(String name, String description) {
        super(name, description);
    }

    @Override
    public void onCommand(SlashCommandInteractionEvent event) {

        String playerListMessage = Proxy.getInstance().getConfig().getString("progress_map");

        ReplyCallbackAction reply = event.reply(playerListMessage);
        reply = reply.setEphemeral(true);
        reply.queue();

    }
}