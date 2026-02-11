package net.bteuk.proxy.commands;

import net.bteuk.network.lib.dto.OnlineUser;
import net.bteuk.network.lib.dto.TabPlayer;
import net.bteuk.proxy.Proxy;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

public class Playerlist extends AbstractCommand {

    /**
     * Constructor, saved the name and description of the command.
     * Also registers the command in Discord.
     * @param name Name of the command
     * @param description Description of the command
     */
    public Playerlist(String name, String description) {
        super(name, description);
    }

    @Override
    public void onCommand(SlashCommandInteractionEvent event) {

        String playerListMessage;

        //Create list of players.
        Set<OnlineUser> onlineUsers = Proxy.getInstance().getUserManager().getOnlineUsers();
        ArrayList<String> playerFormat = new ArrayList<>();

        //Set initial line and default message for empty server.
        if (onlineUsers.isEmpty()) {
            playerListMessage = "**There are currently no players online!**";
        } else {
            playerListMessage = "**Online players on UKnet (" + onlineUsers.size() + ")**";

            for (OnlineUser onlineUser : onlineUsers) {
                //Get primary role and name.
                String primaryRole = "[?]";
                Optional<TabPlayer> optionalTabPlayer = Proxy.getInstance().getTabManager().getTabPlayers().stream().filter(tabPlayer -> tabPlayer.getUuid().equals(onlineUser.getUuid())).findFirst();
                if (optionalTabPlayer.isPresent()) {
                    primaryRole = PlainTextComponentSerializer.plainText().serialize(optionalTabPlayer.get().getPrefix());
                }
                playerFormat.add(primaryRole + " " + onlineUser.getName());
            }

            playerListMessage += "\n```\n";

            //Sort the list of players by alphabetical order, since roles are first it'll be by role.
            playerFormat.sort(Comparator.naturalOrder());

            //Add the players separated by comma.
            playerListMessage += String.join(", ", playerFormat);

            //If message exceeds discord character limit cut it short.
            if (playerListMessage.length() > 2000) {
                playerListMessage = playerListMessage.substring(0, 1993) + "...";
            }

            playerListMessage += "\n```";

        }

        ReplyCallbackAction reply = event.reply(playerListMessage);
        reply = reply.setEphemeral(true);
        reply.queue();

    }
}
