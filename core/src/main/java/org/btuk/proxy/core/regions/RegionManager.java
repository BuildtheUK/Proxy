package org.btuk.proxy.core.regions;

import org.btuk.network.lib.dto.DirectMessage;
import org.btuk.network.lib.dto.RegionRequestEvent;
import org.btuk.network.lib.utils.ChatUtils;
import org.btuk.proxy.core.chat.ChatHandler;
import org.btuk.proxy.core.exceptions.ServerNotFoundException;
import org.btuk.proxy.database.sql.GlobalSQL;
import org.btuk.proxy.database.sql.PlotSQL;
import org.btuk.proxy.database.sql.RegionSQL;

import static org.btuk.network.lib.enums.ChatChannels.GLOBAL;
import static org.btuk.proxy.core.utils.Constants.SERVER_SENDER;

public class RegionManager {

    private final ChatHandler chatHandler;
    private final GlobalSQL globalSQL;
    private final RegionSQL regionSQL;
    private final PlotSQL plotSQL;

    public RegionManager(ChatHandler chatHandler, GlobalSQL globalSQL, RegionSQL regionSQL, PlotSQL plotSQL) {
        this.chatHandler = chatHandler;
        this.globalSQL = globalSQL;
        this.regionSQL = regionSQL;
        this.plotSQL = plotSQL;
    }

    public void handleRegionRequestEvent(RegionRequestEvent regionRequestEvent) {
        // Find the server of the region and route the request to it.
        String server = getServer(regionRequestEvent.getRegionName());
        try {
            chatHandler.handle(regionRequestEvent, server);
        } catch (ServerNotFoundException e) {
            chatHandler.handle(new DirectMessage(GLOBAL.getChannelName(), regionRequestEvent.getReviewerUuid(), SERVER_SENDER, ChatUtils.error("The server of this region is currently unavailable, please try again later."), false));
        }
    }

    // Get the server of the region.
    private String getServer(String regionName) {
        if (regionSQL.hasRow("SELECT region FROM regions WHERE region='" + regionName + "' AND " + "status='plot'")) {
            return plotSQL.getRegionServer(regionName);
        } else {
            return (globalSQL.getString("SELECT name FROM server_data WHERE type='EARTH';"));
        }
    }
}
