package net.bteuk.proxy.utils;

import net.bteuk.proxy.Discord;
import net.bteuk.proxy.Proxy;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.messages.MessageRequest;
import net.dv8tion.jda.api.components.buttons.Button;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;

public class PlotListMessage {

    private final String type;
    private final String title;
    private final String emptyMessage;
    private final int page;
    private final int maxPages;

    private final String player;

    private final ArrayList<Integer> plots;

    /**
     * @param type         Type of plot list.
     * @param title        Title of the MessageEmbed
     * @param query        SQL query that will get the list of plots
     * @param emptyMessage Message to send if no plots are found
     * @param page         Page
     * @param player       Optional only list plots that this player owns/completed
     */
    public PlotListMessage(String type, String title, String query, String emptyMessage, int page, String player) {
        this.type = type;
        this.title = title;
        this.emptyMessage = emptyMessage;
        this.player = player;

        //Alter the query to add the player.
        if (player != null) {
            if (Proxy.getInstance().getGlobalSQL().hasRow("SELECT uuid FROM player_data WHERE name='" + player + "';")) {
                String uuid = Proxy.getInstance().getGlobalSQL().getString("SELECT uuid FROM player_data WHERE name='" + player + "';");
                query = query.replace("%uuid%", " AND u.uuid='" + uuid + "'");
            } else {
                //This is the case when the player does not exist in the database.
                query = query.replace("%uuid%", " AND u.uuid='" + player + "'");
            }
        } else {
            query = query.replace("%uuid%", "");
        }

        plots = Proxy.getInstance().getPlotSQL().getIntList(query);

        //Calculate maximum number of pages.
        maxPages = (int) Math.ceil(plots.size() / 10d);

        //If the list has less plots than the current page number.
        //For example if there are 10 plots per page, and there are 17 plots then any page above 2 would be too much.
        //In this case decrease the page count to the highest.
        if (plots.size() < (page - 1) * 10) {
            this.page = maxPages;
        } else {
            this.page = page;
        }
    }

    /**
     * Adds buttons to the event reply.
     */
    public void addButtons(MessageRequest<?> message) {

        //Clear the components of the message.
        message.setComponents();

        Collection<Button> components = new ArrayList<>();

        //If the page is greater than 1, add a previous page button.
        if (page > 1) {

            //Set the id to the slash command.
            components.add(Button.primary(type + "," + (page - 1) + ((player != null) ? ("," + player) : ""), "◀"));

        }

        //If there are more plots than fit on this page, add a next page button.
        if (plots.size() > page * 10) {

            //Set the id to the slash command.
            components.add(Button.primary(type + "," + (page + 1) + ((player != null) ? ("," + player) : ""), "▶"));

        }

        //Add the buttons to the reply.
        if (!components.isEmpty()) {
            message.setComponents(ActionRow.of(components));
        }
    }

    /**
     * @return {@link MessageEmbed} with a list of plots, if any.
     */
    public MessageEmbed createList() {

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(title);
        eb.setColor(Color.CYAN);

        //Check if the list is empty.
        if (plots.isEmpty()) {

            eb.setDescription(emptyMessage);
            return eb.build();

        }

        StringBuilder plot_message = new StringBuilder();

        //If the page is greater than 1, set the starting index accordingly.
        int index = (page - 1) * 10;

        //Iterate until the index is divisible by 10 again.
        do {

            //Add message for the plot.
            plot_message.append("• Plot ").append(plots.get(index));

            //If the plot has an owner, add their name.
            if (Proxy.getInstance().getPlotSQL().hasRow("SELECT id FROM plot_members WHERE id=" + plots.get(index) + " AND is_owner=1;")) {
                plot_message.append(" - claimed by: ").append(Discord.escapeDiscordFormatting(Proxy.getInstance().getGlobalSQL().getString("SELECT name FROM player_data WHERE uuid='" +
                        Proxy.getInstance().getPlotSQL().getString("SELECT uuid FROM plot_members WHERE id=" + plots.get(index) + " AND is_owner=1;") + "';")));
            }

            //If the plot is completed, add the builder.
            if (Proxy.getInstance().getPlotSQL().hasRow("SELECT id FROM plot_data WHERE id=" + plots.get(index) + " AND status='completed'")) {
                plot_message.append(" - completed by: ").append(Discord.escapeDiscordFormatting(Proxy.getInstance().getGlobalSQL().getString("SELECT name FROM player_data WHERE uuid='" +
                        Proxy.getInstance().getPlotSQL().getString("SELECT uuid FROM plot_review WHERE plot_id=" + plots.get(index) + " AND accepted=1 AND completed=1;") + "';")));
            }

            //If the plot is currently being reviewed add that.
            if (Proxy.getInstance().getPlotSQL().hasRow("SELECT 1 FROM plot_submission WHERE plot_id=" + plots.get(index) + " AND status<>'submitted';")) {
                plot_message.append(" (")
                        .append(Proxy.getInstance().getPlotSQL().getString("SELECT status FROM plot_submission WHERE plot_id=" + plots.get(index) + ";"))
                        .append(")");
            }

            //Increment index.
            index++;

            //If there are more plots and this isn't the last of the page add a new line.
            if (plots.size() > index && index % 10 != 0) {
                plot_message.append("\n");
            }

        } while (index % 10 != 0 && plots.size() > index);

        eb.setDescription(plot_message.toString());

        eb.setFooter("Page " + page + "/" + maxPages);
        return eb.build();

    }
}
