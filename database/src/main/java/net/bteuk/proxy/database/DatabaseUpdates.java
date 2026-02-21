package net.bteuk.proxy.database;

import lombok.extern.java.Log;
import net.bteuk.proxy.database.sql.GlobalSQL;
import net.bteuk.proxy.database.sql.PlotSQL;
import net.bteuk.proxy.database.sql.RegionSQL;
import net.bteuk.proxy.database.sql.migration.AcceptData;
import net.bteuk.proxy.database.sql.migration.DenyData;
import net.bteuk.proxy.database.sql.migration.PlotSubmissions;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;

import java.util.List;

@Log
public class DatabaseUpdates {

    private final GlobalSQL globalSQL;

    private final PlotSQL plotSQL;

    private final RegionSQL regionSQL;

    private final EarthGeneratorSettings bteGeneratorSettings =
            EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);

    public DatabaseUpdates(GlobalSQL globalSQL, PlotSQL plotSQL, RegionSQL regionSQL) {
        this.globalSQL = globalSQL;
        this.plotSQL = plotSQL;
        this.regionSQL = regionSQL;
    }

    // Update database if the config was outdated, this implies the database is also outdated.
    public void updateDatabase() {

        // Get the database version from the database.
        String version = "1.0.0";
        if (globalSQL.hasRow("SELECT data_value FROM unique_data WHERE data_key='version';")) {
            version = globalSQL.getString("SELECT data_value FROM unique_data WHERE data_key='version';");
        } else {
            // Insert the current database version as version.
            globalSQL.update("INSERT INTO unique_data(data_key, data_value) VALUES('version','1.7.3')");
        }

        // Check for specific table columns that could be missing,
        // All changes have to be tested from 1.0.0.
        // We update 1 version at a time.

        // Convert config version to integer, so we can easily use them.
        int oldVersionInt = getVersionInt(version);

        // Update sequentially.

        // 1.0.0 -> 1.1.0
        if (oldVersionInt <= 1) {
            update1_2();
        }

        // 1.1.0 -> 1.2.0
        if (oldVersionInt <= 2) {
            update2_3();
        }

        // 1.2.0 -> 1.3.0
        if (oldVersionInt <= 3) {
            update3_4();
        }

        // 1.3.0 -> 1.4.4
        if (oldVersionInt <= 4) {
            update4_5();
        }

        // 1.4.4 -> 1.5.0
        if (oldVersionInt <= 5) {
            update5_6();
        }

        // 1.5.0 -> 1.6.0
        if (oldVersionInt <= 6) {
            update6_7();
        }

        // 1.6.0 -> 1.7.0
        if (oldVersionInt <= 7) {
            update7_8();
        }

        // 1.7.0 -> 1.7.1
        if (oldVersionInt <= 8) {
            update8_9();
        }

        // 1.7.1 -> 1.7.2
        if (oldVersionInt <= 9) {
            update9_10();
        }

        // 1.7.2 -> 1.7.3
        if (oldVersionInt <= 10) {
            update10_11();
        }

        // 1.7.3 -> 1.9.4
        if (oldVersionInt <= 11) {
            update12_11();
        }

        if (oldVersionInt <= 12){
            update12_13();
        }
    }

    private int getVersionInt(String version) {

        switch (version) {

            case "1.9.5" -> {
                return 13;
            }

            // 1.9.4 = 12
            case "1.9.4" -> {
                return 12;
            }

            // 1.7.3 = 11
            case "1.7.3" -> {
                return 11;
            }

            // 1.7.2 = 10
            case "1.7.2" -> {
                return 10;
            }

            // 1.7.1 = 9
            case "1.7.1" -> {
                return 9;
            }

            // 1.7.0 = 8
            case "1.7.0" -> {
                return 8;
            }

            // 1.6.0 = 7
            case "1.6.0" -> {
                return 7;
            }

            // 1.5.0 = 6
            case "1.5.0" -> {
                return 6;
            }

            // 1.4.4 = 5
            case "1.4.4" -> {
                return 5;
            }

            // 1.3.0 = 4
            case "1.3.0" -> {
                return 4;
            }

            // 1.2.0 = 3
            case "1.2.0" -> {
                return 3;
            }

            // 1.1.0 = 2
            case "1.1.0" -> {
                return 2;
            }

            // Default is 1.0.0 = 1;
            default -> {
                return 1;
            }

        }

    }

    private void update12_13() {

        log.info("Updating database from 1.9.4 to 1.9.5");

        //add the new fields isPublic, playerBuilt and timeAdded to the buildings database
        globalSQL.update("ALTER TABLE buildings ADD COLUMN isPublic BOOLEAN NOT NULL DEFAULT TRUE, ADD COLUMN playerBuilt BOOLEAN NOT NULL DEFAULT TRUE, ADD COLUMN timeAdded DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;");

        //add and populate the new fields lat and lon

        globalSQL.update("ALTER TABLE buildings ADD COLUMN lat DOUBLE DEFAULT 0;");
        globalSQL.update("ALTER TABLE buildings ADD COLUMN lon DOUBLE DEFAULT 0;");

        List<Integer> buildingIds = globalSQL.getIntList("SELECT building_id FROM buildings;");

        for (int id : buildingIds) {

            try {
                int coordinateId = globalSQL.getInt(String.format("SELECT coordinate_id FROM buildings WHERE building_id = %d;", id));

                double x = globalSQL.getDouble(String.format("SELECT x FROM coordinates WHERE id = %d;", coordinateId));

                double z = globalSQL.getDouble(String.format("SELECT z FROM coordinates WHERE id = %d;", coordinateId));

                double[] coords = bteGeneratorSettings.projection().toGeo(x, z);
                globalSQL.update(String.format("UPDATE buildings SET lat = %f, lon = %f WHERE building_id = %d;", coords[1], coords[0], id));
            } catch (OutOfProjectionBoundsException e) {
                log.warning("Failed to convert coordinates for building " + id);
            }
        }

        globalSQL.update("UPDATE unique_data SET data_value='1.9.5' WHERE data_key='version';");
    }


    private void update12_11() {
        log.info("Updating database from 1.7.3 to 1.9.4");

        // Remove the type column from server_events and join_events.
        globalSQL.update("ALTER TABLE server_events DROP COLUMN type;");
        globalSQL.update("ALTER TABLE join_events DROP COLUMN type;");

        globalSQL.update("ALTER TABLE player_data ADD COLUMN display_name TEXT NULL DEFAULT NULL;");

        globalSQL.update("UPDATE unique_data SET data_value='1.9.4' WHERE data_key='version';");
    }

    private void update10_11() {
        log.info("Updating database from 1.7.2 to 1.7.3");

        // Create a copy of the plot corners table, so we can migrate the data without effecting functionality.
        // Then clear the existing table.
        plotSQL.update("CREATE TABLE old_plot_corners AS SELECT * FROM plot_corners;");
        plotSQL.update("DELETE FROM plot_corners;");

        // Migrate the old data to the new table using the plotsystem locations.
        List<Integer> plots = plotSQL.getIntList("SELECT DISTINCT(id) FROM old_plot_corners");
        for (int plot : plots) {
            // Get the location of the plot.
            String location = plotSQL.getString("SELECT location FROM plot_data WHERE id=" + plot + ";");
            int xTransform;
            int zTransform;
            if (location.equalsIgnoreCase("solihull")) {
                xTransform = 2696704;
                zTransform = -5555200;
            } else {
                xTransform = -plotSQL.getInt("SELECT xTransform FROM location_data WHERE name='" + location + "';");
                zTransform = -plotSQL.getInt("SELECT zTransform FROM location_data WHERE name='" + location + "';");
            }

            // Save all the corners of the plot with the coordinate transformation.
            int[][] plotCorners = plotSQL.getOldPlotCorners(plot);
            int cornerId = 1;
            for (int[] corner : plotCorners) {
                plotSQL.update("INSERT INTO plot_corners(id,corner,x,z) VALUES(" + plot + "," + cornerId + "," + (corner[0] + xTransform) + "," + (corner[1] + zTransform) + ");");
                cornerId++;
            }
            log.info("Migrated all plot corners to Earth location.");
        }

        // Version 1.7.3
        globalSQL.update("UPDATE unique_data SET data_value='1.7.3' WHERE data_key='version';");
    }

    private void update9_10() {

        log.info("Updating database from 1.7.1 to 1.7.2");

        plotSQL.update("ALTER TABLE plot_data MODIFY status ENUM('unclaimed','claimed','submitted','completed','deleted') NOT NULL");

        // Migrate existing data from accept_data and deny_data to the new plot_review table.
        List<DenyData> denyData = plotSQL.getDenyData();
        for (DenyData deny : denyData) {
            int reviewId = plotSQL.insertReturnId(
                    "INSERT INTO plot_review(plot_id,uuid,reviewer,attempt,review_time,accepted,completed) " + "VALUES(" + deny.id() + ",'" + deny.uuid() + "','" + deny.reviewer() + "'," + deny.attempt() + "," + deny.denyTime() + "," + "0,1);");
            // Insert the feedback as category feedback for the GENERAL category.
            plotSQL.update("INSERT INTO plot_category_feedback(review_id,category,selection,book_id) " + "VALUES(" + reviewId + ",'GENERAL','NONE'," + deny.bookId() + ");");
        }
        List<AcceptData> acceptData = plotSQL.getAcceptData();
        for (AcceptData accept : acceptData) {
            // Get the highest denied attempt for the user, the accept attempt will be that +1.
            int attempt = 1 + plotSQL.getInt("SELECT MAX(attempt) FROM deny_data WHERE id=" + accept.id() + " AND uuid='" + accept.uuid() + "';");
            int reviewId = plotSQL.insertReturnId(
                    "INSERT INTO plot_review(plot_id,uuid,reviewer,attempt,review_time,accepted,completed) " + "VALUES(" + accept.id() + ",'" + accept.uuid() + "','" + accept.reviewer() + "'," + attempt + "," + accept.acceptTime() + "," + "1,1);");
            if (accept.bookId() != 0) {
                // Insert the feedback as category feedback for the GENERAL category.
                plotSQL.update("INSERT INTO plot_category_feedback(review_id,category,selection,book_id) " + "VALUES(" + reviewId + ",'GENERAL','NONE'," + accept.bookId() + ");");
            }
        }

        // Migrate existing data from plot_submissions to the new plot_submission tabel.
        List<PlotSubmissions> plotSubmissions = plotSQL.getPlotSubmissions();
        for (PlotSubmissions plotSubmission : plotSubmissions) {
            plotSQL.update(
                    "INSERT INTO plot_submission(plot_id,submit_time,status,last_query) " + "VALUES(" + plotSubmission.id() + "," + plotSubmission.submit_time() + ",'submitted',"
                            + plotSubmission.last_query() + ");");
        }

        // Rename the accept_data, deny_data and plot_submissions tables to indicate they are old.
        plotSQL.update("RENAME TABLE accept_data TO old_accept_data;");
        plotSQL.update("RENAME TABLE deny_data TO old_deny_data;");
        plotSQL.update("RENAME TABLE plot_submissions TO old_plot_submissions");

        // Version 1.7.2
        globalSQL.update("UPDATE unique_data SET data_value='1.7.2' WHERE data_key='version';");
    }

    private void update8_9() {

        log.info("Updating database from 1.7.0 to 1.7.1");

        // Add pinned column in region_members.
        plotSQL.update("ALTER TABLE plot_members ADD COLUMN inactivity_notice TINYINT(1) NOT NULL DEFAULT 0;");

        // Version 1.7.1
        globalSQL.update("UPDATE unique_data SET data_value='1.7.1' WHERE data_key='version';");

    }

    private void update7_8() {

        log.info("Updating database from 1.6.0 to 1.7.0");

        // Add pinned column in region_members.
        regionSQL.update("ALTER TABLE region_members ADD COLUMN pinned TINYINT(1) NOT NULL DEFAULT 0;");

        // Version 1.7.0
        globalSQL.update("UPDATE unique_data SET data_value='1.7.0' WHERE data_key='version';");

    }

    private void update6_7() {

        log.info("Updating database from 1.5.0 to 1.6.0");

        // Remove online users table.
        globalSQL.update("DROP TABLE online_users;");

        // Convert messages message column from varchar(256) to clob type.
        // Add id column and use that for the primary key.
        globalSQL.update("ALTER TABLE messages DROP CONSTRAINT fk_messages_1;");
        globalSQL.update("ALTER TABLE messages DROP PRIMARY KEY;");
        globalSQL.update("ALTER TABLE messages ADD id INT NOT NULL AUTO_INCREMENT PRIMARY KEY;");
        globalSQL.update("ALTER TABLE messages MODIFY message TEXT NOT NULL;");
        globalSQL.update("ALTER TABLE messages ADD CONSTRAINT fk_messages_1 FOREIGN KEY (recipient) REFERENCES player_data(uuid);");

        // Remove staff_chat column in player_data.
        globalSQL.update("ALTER TABLE player_data DROP COLUMN staff_chat;");

        // Add chat_channel column in player_data.
        globalSQL.update("ALTER TABLE player_data ADD COLUMN chat_channel VARCHAR(64) NOT NULL DEFAULT 'global';");

        // Version 1.6.0
        globalSQL.update("UPDATE unique_data SET data_value='1.6.0' WHERE data_key='version';");

    }

    private void update5_6() {

        log.info("Updating database from 1.4.4 to 1.5.0");

        // Update column in plot_data to add a coordinate_id with foreign key.
        plotSQL.update("ALTER TABLE plot_data ADD COLUMN coordinate_id INT NOT NULL DEFAULT 0;");

        // Version 1.5.0
        globalSQL.update("UPDATE unique_data SET data_value='1.5.0' WHERE data_key='version';");
    }

    private void update4_5() {

        log.info("Updating database from 1.3.0 to 1.4.4");

        // Version 1.4.4
        globalSQL.update("UPDATE unique_data SET data_value='1.4.4' WHERE data_key='version';");

        // Update column in location_data for the new subcategory id as int.
        globalSQL.update("UPDATE location_data SET subcategory=NULL;");
        globalSQL.update("ALTER TABLE location_data MODIFY subcategory INT NULL DEFAULT NULL;");

        // Update column in location_requests for the new subcategory id as int.
        globalSQL.update("ALTER TABLE location_requests MODIFY subcategory INT NULL DEFAULT NULL;");

        // Add foreign key to location_data referencing the new location_subcategory table.
        globalSQL.update("ALTER TABLE location_data ADD CONSTRAINT fk_location_data_2 FOREIGN KEY (subcategory) REFERENCES location_subcategory(id);");

        // Add foreign key to location_requests referencing the new location_subcategory table.
        globalSQL.update("ALTER TABLE location_requests ADD CONSTRAINT fk_location_requests_2 FOREIGN KEY (subcategory) REFERENCES location_subcategory(id);");
    }

    private void update3_4() {

        log.info("Updating database from 1.2.0 to 1.3.0");

        // Version 1.3.0.
        globalSQL.update("UPDATE unique_data SET data_value='1.3.0' WHERE data_key='version';");

        // Add tips_enabled to the player_data table.
        globalSQL.update("ALTER TABLE player_data ADD COLUMN tips_enabled TINYINT(1) NOT NULL DEFAULT 1;");

    }

    private void update2_3() {

        log.info("Updating database from 1.1.0 to 1.2.0");

        // Version 1.2.0.
        globalSQL.update("UPDATE unique_data SET data_value='1.2.0' WHERE data_key='version';");

        // Add applicant to list of builder roles.
        globalSQL.update("ALTER TABLE player_data MODIFY builder_role ENUM('default','applicant','apprentice','jrbuilder','builder','architect','reviewer') DEFAULT 'default'");

    }

    private void update1_2() {

        log.info("Updating database from 1.0.0 to 1.1.0");

        // Version 1.1.0.
        globalSQL.getString("UPDATE unique_data SET data_value='1.1.0' WHERE data_key='version';");

        // Add skin texture id column.
        globalSQL.update("ALTER TABLE player_data ADD COLUMN player_skin TEXT NULL DEFAULT NULL;");

        // Add foreign constraints.

        // id to location_data (coordinate), location_requests (coordinate) and home (coordinate_id)
        // since it references an id from the coordinates table.
        globalSQL.update("ALTER TABLE location_data ADD CONSTRAINT fk_location_data_1 FOREIGN KEY (coordinate) REFERENCES coordinates(id);");
        globalSQL.update("ALTER TABLE location_requests ADD CONSTRAINT fk_location_requests_1 FOREIGN KEY (coordinate) REFERENCES coordinates(id);");
        globalSQL.update("ALTER TABLE home ADD CONSTRAINT fk_home_1 FOREIGN KEY (coordinate_id) REFERENCES coordinates(id);");

        // uuid to join_events, server_events, statistics, online_users, server_switch, moderation, coins, discord and home
        // since it references a player that will always be in the player_data table.
        globalSQL.update("ALTER TABLE messages ADD CONSTRAINT fk_messages_1 FOREIGN KEY (recipient) REFERENCES player_data(uuid);");
        globalSQL.update("ALTER TABLE join_events ADD CONSTRAINT fk_join_events_1 FOREIGN KEY (uuid) REFERENCES player_data(uuid);");
        globalSQL.update("ALTER TABLE server_events ADD CONSTRAINT fk_server_events_1 FOREIGN KEY (uuid) REFERENCES player_data(uuid);");
        globalSQL.update("ALTER TABLE statistics ADD CONSTRAINT fk_statistics_1 FOREIGN KEY (uuid) REFERENCES player_data(uuid);");
        globalSQL.update("ALTER TABLE online_users ADD CONSTRAINT fk_online_users_1 FOREIGN KEY (uuid) REFERENCES player_data(uuid);");
        globalSQL.update("ALTER TABLE server_switch ADD CONSTRAINT fk_server_switch_1 FOREIGN KEY (uuid) REFERENCES player_data(uuid);");
        globalSQL.update("ALTER TABLE moderation ADD CONSTRAINT fk_moderation_1 FOREIGN KEY (uuid) REFERENCES player_data(uuid);");
        globalSQL.update("ALTER TABLE coins ADD CONSTRAINT fk_coins_1 FOREIGN KEY (uuid) REFERENCES player_data(uuid);");
        globalSQL.update("ALTER TABLE discord ADD CONSTRAINT fk_discord_1 FOREIGN KEY (uuid) REFERENCES player_data(uuid);");
        globalSQL.update("ALTER TABLE home ADD CONSTRAINT fk_home_2 FOREIGN KEY (uuid) REFERENCES player_data(uuid);");

        // name to online_users (server), server_switch (from_server and to_server), coordinates (server)
        // since it references servers in the server_data table.
        globalSQL.update("ALTER TABLE online_users ADD fk_online_users_2 FOREIGN KEY (server) REFERENCES server_data(name);");
        globalSQL.update("ALTER TABLE server_switch ADD fk_server_switch_2 FOREIGN KEY (from_server) REFERENCES server_data(name);");
        globalSQL.update("ALTER TABLE server_switch ADD fk_server_switch_3 FOREIGN KEY (to_server) REFERENCES server_data(name);");
        globalSQL.update("ALTER TABLE coordinates ADD fk_coordinates_1 FOREIGN KEY (server) REFERENCES server_data(name);");

    }
}
