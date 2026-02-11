package net.bteuk.proxy.commands;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public abstract class AbstractCommand implements Command {

    private final String name;
    private final String description;
    private final OptionData[] options;

    /**
     * Constructor, saves the name, description and potential options of the command.
     * Also registers the command in Discord.
     * @param name Name of the command
     * @param description Description of the command
     * @param options Command options to add to the command
     */
    public AbstractCommand(String name, String description, OptionData... options) {

        this.name = name;
        this.description = description;
        this.options = options;

    }
    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public OptionData[] getOptions() {
        return options;
    }

    public void onButtonInteraction(ButtonInteractionEvent event) {}
}
