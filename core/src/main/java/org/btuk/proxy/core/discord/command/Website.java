package org.btuk.proxy.core.discord.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

public class Website extends AbstractCommand {

    private final String link;

   /**
    * Constructor, saved the name and description of the command.
    * Also registers the command in Discord.
    * @param name Name of the command
    * @param description Description of the command
    */
   public Website(String name, String description, String link) {
       super(name, description);
       this.link = link;
   }

   @Override
   public void onCommand(SlashCommandInteractionEvent event) {
       ReplyCallbackAction reply = event.reply(link);
       reply = reply.setEphemeral(true);
       reply.queue();
   }
}