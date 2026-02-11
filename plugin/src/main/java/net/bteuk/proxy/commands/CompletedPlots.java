package net.bteuk.proxy.commands;

import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class CompletedPlots extends PlotListCommand {

    /**
     * Constructor, saved the name and description of the command.
     * Also registers the command in Discord.
     *
     * @param name        Name of the command
     * @param description Description of the command
     */
    public CompletedPlots(String name, String description, OptionData... options) {
        super(name, description, "Completed Plots", "SELECT pd.id FROM plot_data AS pd INNER JOIN plot_review AS pr ON pd.id=pr.plot_id WHERE pd.status='completed' AND pr.accepted=1 AND pr.completed=1%uuid% ORDER BY pr.review_time DESC;", "There are currently no completed plots.", options);
    }
}
