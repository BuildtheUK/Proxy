package net.bteuk.proxy.commands;

import net.bteuk.proxy.utils.PlotListMessage;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

public abstract class PlotListCommand extends AbstractCommand {

    private final String TYPE;
    private final String TITLE;
    private final String QUERY;
    private final String EMPTY;

    public PlotListCommand(String name, String description, String title, String query, String empty, OptionData... options) {
        super(name, description, options);
        TYPE = name;
        TITLE = title;
        QUERY = query;
        EMPTY = empty;
    }

    @Override
    public void onCommand(SlashCommandInteractionEvent event) {

        String player = null;

        OptionMapping option = event.getOption("player");
        if (option != null) {
            player = option.getAsString();
        }

        PlotListMessage message = new PlotListMessage(TYPE, TITLE, QUERY, EMPTY, 1, player);

        ReplyCallbackAction reply = event.replyEmbeds(message.createList());
        message.addButtons(reply);
        reply = reply.setEphemeral(true);
        reply.queue();

    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {

        //Unwrap the component.
        String[] args = event.getComponentId().split(",");

        int page = Integer.parseInt(args[1]);
        String player = (args.length == 3) ? args[2] : null;

        PlotListMessage message = new PlotListMessage(TYPE, TITLE, QUERY, EMPTY, page, player);

        MessageEditCallbackAction edit = event.editMessageEmbeds(message.createList());
        message.addButtons(edit);
        edit.queue();

    }
}
