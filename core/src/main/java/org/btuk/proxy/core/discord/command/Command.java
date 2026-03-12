package org.btuk.proxy.core.discord.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public interface Command {

    String getName();

    String getDescription();

    OptionData[] getOptions();

    void onCommand(SlashCommandInteractionEvent event);

    void onButtonInteraction(ButtonInteractionEvent event);

}
