package net.bteuk.proxy.utils;

import net.bteuk.proxy.Discord;
import net.bteuk.proxy.Proxy;
import net.bteuk.proxy.database.sql.GlobalSQL;
import net.bteuk.proxy.database.sql.PlotSQL;
import net.bteuk.proxy.database.sql.RegionSQL;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;

import java.awt.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * This class manages the embedded message in the support-info channel, this will tell reviewers the status of submitted plots, and other reviewing related information.
 */
public class ReviewStatus {

    private String messageID;
    private final TextChannel supportInfoChannel;
    private final TextChannel supportChatChannel;

    private ArrayList<Integer> plots;

    private final PlotSQL plotSQL;
    private final GlobalSQL globalSQL;
    private final RegionSQL regionSQL;

    private final long day = 24L * 60L * 60L * 1000L;

    //Build embed.
    public ReviewStatus() {

        plotSQL = Proxy.getInstance().getPlotSQL();
        globalSQL = Proxy.getInstance().getGlobalSQL();
        regionSQL = Proxy.getInstance().getRegionSQL();

        //Get channel.
        supportInfoChannel = Proxy.getInstance().getDiscord().getSupportInfoChannel();
        supportChatChannel = Proxy.getInstance().getDiscord().getSupportChatChannel();

        //Try to get the message ID from config, if it does not exist, create a new message id.
        messageID = Proxy.getInstance().getConfig().getString("message.reviewer");

        //Create scheduler to update the message frequently.
        //Run a delayed task to remove this from the list.
        //Create new message.
        //Update message.
        Proxy.getInstance().getServer().getScheduler().buildTask(Proxy.getInstance(), () -> {

                    if (messageID == null) {

                        //Create new message.
                        createMessage();

                    } else {

                        //Update message.
                        updateMessage();

                    }

                    //Check for plots that have not been queried within a certain amount of time.
                    long time = Time.currentTime();

                    //Get all plots that have not been reviewed in the last 24 hours, and have not already been queried.
                    plots = plotSQL.getIntList("SELECT plot_id FROM plot_submission WHERE last_query<" + (time - day) + ";");

                    //Update the last query time for the same plots.
                    plotSQL.update("UPDATE plot_submission SET last_query=" + time + " WHERE last_query<" + (time - day) + ";");

                    //For each plot post in the support-chat how long they've not been reviewed.
                    //If longer that 3 days ping reviewers.
                    for (int id : plots) {

                        //Get time since submission.
                        long submit_time = plotSQL.getLong("SELECT submit_time FROM plot_submission WHERE plot_id=" + id + ";");
                        int days = (int) ((time - submit_time) / 1000L / 60L / 60L / 24L);

                        String status = plotSQL.getString("SELECT status FROM plot_submission WHERE plot_id=" + id + ";");
                        String textStatus = switch (status) {
                            case "awaiting verification", "under verification" -> "verify";
                            default -> "review";
                        };

                        if (time - (3 * day) > submit_time) {

                            supportChatChannel.sendMessage("<@&" + Proxy.getInstance().getDiscord().getReviewerRoleID() + "> Plot " + id + " has been submitted for " + days + " days, please " + textStatus + " it as soon as possible!").queue();

                        } else if (days == 1) {

                            supportChatChannel.sendMessage("Plot " + id + " has been submitted for " + days + " day, please " + textStatus + " it soon.").queue();

                        } else {

                            supportChatChannel.sendMessage("Plot " + id + " has been submitted for " + days + " days, please " + textStatus + " it soon.").queue();

                        }
                    }
                })
                .repeat(1L, TimeUnit.MINUTES)
                .schedule();
    }

    private void createMessage() {

        try {
            supportInfoChannel.sendMessageEmbeds(getEmbed()).queue((message) -> {

                //Set message id for next time.
                messageID = message.getId();
            });
        } catch (SQLException e) {
            Proxy.getInstance().getLogger().error(String.format("An error occurred while fetching region requests, %s.", e.getMessage()));
        }

    }

    private void updateMessage() {

        supportInfoChannel.retrieveMessageById(messageID).queue((message) -> {
            // use the message here, its an async callback
            try {
                message.editMessageEmbeds(getEmbed()).queue();
            } catch (SQLException e) {
                Proxy.getInstance().getLogger().error(String.format("An error occurred while fetching region requests, %s.", e.getMessage()));
            }
        }, new ErrorHandler().handle(ErrorResponse.UNKNOWN_MESSAGE, (e) -> supportInfoChannel.sendMessage("The message with id " + messageID + " does not exist!").queue()));


    }

    private MessageEmbed getEmbed() throws SQLException {

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Support Information");

        //Submitted plots, show up to 5 in a list.
        //Order by submit time ascending, as the oldest plots get reviewed first, this way it is always clear to see if plots are currently being reviewed.
        ArrayList<Integer> plots = plotSQL.getIntList("SELECT plot_id FROM plot_submission ORDER BY submit_time ASC;");
        StringBuilder plot_message = new StringBuilder();

        if (plots.isEmpty()) {
            plot_message = new StringBuilder("There are 0 plots waiting to be reviewed/verified!");
        } else {

            //Add up to 5 plots to the list.
            int counter = 0;
            for (int plot : plots) {

                //If plot status is 'reviewing', then add additional info that the plot is currently under review.
                String status = plotSQL.getString("SELECT status FROM plot_submission WHERE plot_id=" + plot + ";");
                plot_message.append("• Plot ")
                        .append(plot).append(" submitted by ")
                        .append(Discord.escapeDiscordFormatting(globalSQL.getString("SELECT name FROM player_data WHERE uuid='" +
                        plotSQL.getString("SELECT uuid FROM plot_members WHERE id=" + plot + " AND is_owner=1;") + "';")))
                        .append(" (")
                        .append(status)
                        .append(")");

                counter++;

                if (plots.size() > counter) {
                    plot_message.append("\n");
                }

                if (counter > 4) {
                    break;
                }
            }

            if (plots.size() > 5) {
                plot_message.append("*and ").append(plots.size() - 5).append(" more...*");
            }
        }

        //Number of available plots in the plotsystem.
        MessageEmbed.Field plot_submissions = new MessageEmbed.Field("Plot Submissions", plot_message.toString(), false);

        //Plot availability, list number of unclaimed plots for each difficulty.
        String plots_per_difficulty = "• Easy: " + plotSQL.getInt("SELECT COUNT(id) FROM plot_data WHERE status='unclaimed' AND difficulty=1;") +
                "\n• Normal: " + plotSQL.getInt("SELECT COUNT(id) FROM plot_data WHERE status='unclaimed' AND difficulty=2;") +
                "\n• Hard: " + plotSQL.getInt("SELECT COUNT(id) FROM plot_data WHERE status='unclaimed' AND difficulty=3;");

        MessageEmbed.Field plot_availability = new MessageEmbed.Field("Plots Available", plots_per_difficulty, false);

        //Number of navigation requests.
        //Submitted plots, show up to 5 in a list.
        ArrayList<String> locations = globalSQL.getStringList("SELECT location FROM location_requests;");
        StringBuilder navigation_message = new StringBuilder();

        if (locations.isEmpty()) {
            navigation_message = new StringBuilder("There are 0 navigation requests waiting to be reviewed!");
        } else {

            //Add up to 5 plots to the list.
            int counter = 0;
            for (String location : locations) {

                navigation_message.append("• Location ").append(location).append(" requested");

                counter++;

                if (locations.size() > counter) {
                    navigation_message.append("\n");
                }

                if (counter > 4) {
                    break;
                }
            }

            if (locations.size() > 5) {
                navigation_message.append("*and ").append(locations.size() - 5).append(" more...*");
            }
        }

        MessageEmbed.Field navigation_requests = new MessageEmbed.Field("Navigation Requests", navigation_message.toString(), false);

        //Number of region requests.
        ArrayList<String[]> regions = regionSQL.getStringArrayList("SELECT region,uuid FROM region_requests WHERE staff_accept=0;");
        StringBuilder region_message = new StringBuilder();

        if (regions.isEmpty()) {
            region_message = new StringBuilder("There are 0 region requests waiting to be reviewed!");
        } else {

            //Add up to 5 plots to the list.
            int counter = 0;
            for (String[] region : regions) {

                region_message.append("• Region ").append(region[0]).append(" requested by ")
                        .append(Discord.escapeDiscordFormatting(globalSQL.getString("SELECT name FROM player_data WHERE uuid='" + region[1] + "';")));

                counter++;

                if (regions.size() > counter) {
                    region_message.append("\n");
                }

                if (counter > 4) {
                    break;
                }
            }

            if (regions.size() > 5) {
                region_message.append("*and ").append(locations.size() - 5).append(" more...*");
            }
        }

        MessageEmbed.Field region_requests = new MessageEmbed.Field("Region Requests", region_message.toString(), false);

        eb.addField(plot_submissions);
        eb.addField(plot_availability);
        eb.addField(navigation_requests);
        eb.addField(region_requests);

        eb.setFooter("Last updated: " + Time.getDateTime(Time.currentTime()));
        //eb.addField(navigation);
        //eb.setDescription("**" + chatMessage + "**");
        eb.setColor(Color.CYAN);
        return eb.build();

    }
}
