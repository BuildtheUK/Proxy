package net.bteuk.proxy.commands;

import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class ClaimedPlots extends PlotListCommand {

    /**
     * Constructor, saved the name and description of the command.
     * Also registers the command in Discord.
     *
     * @param name        Name of the command
     * @param description Description of the command
     */
    public ClaimedPlots(String name, String description, OptionData... options) {
        super(name, description, "Claimed Plots", "SELECT pd.id FROM plot_data AS pd INNER JOIN plot_members AS u ON pd.id=u.id WHERE pd.status='claimed' AND u.is_owner=1%uuid%;", "There are currently no claimed plots.", options);
    }
}
