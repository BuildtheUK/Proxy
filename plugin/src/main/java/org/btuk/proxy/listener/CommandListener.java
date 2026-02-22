package org.btuk.proxy.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.proxy.Player;
import org.btuk.proxy.Proxy;

public class CommandListener {

    public CommandListener(Proxy proxy) {
        proxy.getServer().getEventManager().register(proxy, this);
    }

    @Subscribe
    public void onPlayerCommand(CommandExecuteEvent event) {
        if (event.getCommandSource() instanceof Player) {
            if (event.getCommand().startsWith("server")) {
                event.setResult(CommandExecuteEvent.CommandResult.forwardToServer(event.getCommand()));
            }
        }
    }
}
