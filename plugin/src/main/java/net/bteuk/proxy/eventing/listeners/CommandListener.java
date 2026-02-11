package net.bteuk.proxy.eventing.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.bteuk.proxy.Proxy;

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
