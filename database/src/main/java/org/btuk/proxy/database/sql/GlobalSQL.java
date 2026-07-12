package org.btuk.proxy.database.sql;

import lombok.extern.java.Log;

import org.btuk.proxy.database.dto.AutoModFlagDTO;
import org.btuk.proxy.database.dto.BuildingDTO;
import org.btuk.proxy.database.dto.PlayerDTO;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Log
public class GlobalSQL extends AbstractSQL {
    public GlobalSQL(DataSource datasource) {
        super(datasource);
    }

    public boolean createUser(String uuid, String name, String playerSkin) {
        if (uuid == null || name == null || playerSkin == null) {
            log.warning("createUser called with null argument(s)");
            return false;
        }

        final String sql = """
                INSERT INTO player_data(uuid, name, last_online, last_submit, player_skin)
                VALUES(?, ?, ?, ?, ?)
                """;

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, uuid);
            statement.setString(2, name);
            statement.setLong(3, System.currentTimeMillis());
            statement.setLong(4, 0L);
            statement.setString(5, playerSkin);

            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.severe("Failed to create user " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    public boolean insertMessage(String recipient, String message) {
        if (recipient == null || message == null) {
            log.warning("insertMessage called with null argument(s)");
            return false;
        }

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement("INSERT INTO messages(recipient,message) VALUES(?,?);")) {
            statement.setString(1, recipient);
            statement.setString(2, message);

            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.severe("Failed to insert offline message for " + recipient + ": " + e.getMessage());
            return false;
        }
    }

    public List<String> getOfflineMessages(String uuid) {
        List<String> messages = new ArrayList<>();

        if (uuid == null) {
            log.warning("getOfflineMessages called with null uuid");
            return messages;
        }

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement("SELECT message FROM messages WHERE recipient=?;")) {
            statement.setString(1, uuid);

            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    messages.add(results.getString(1));
                }
            }
        } catch (SQLException e) {
            log.severe("Failed to fetch offline messages for " + uuid + ": " + e.getMessage());
        }
        return messages;
    }

    public boolean checkIfUserExistsByName(String name) {
        if (name == null) {
            log.warning("checkIfUserExistsByName called with null name");
            return false;
        }

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement("SELECT uuid FROM player_data WHERE name=?;")) {
            statement.setString(1, name);

            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        } catch (SQLException e) {
            log.severe("Failed to check if user exists by name '" + name + "': " + e.getMessage());
            return false;
        }
    }

    public void saveAutoModFlags(String uuid, List<AutoModFlagDTO> flags) {
        if (uuid == null || flags == null) {
            log.warning("saveAutoModFlags called with null argument(s)");
            return;
        }

        final String deleteSql = "DELETE FROM automod_flags WHERE uuid=?;";
        final String insertSql = "INSERT INTO automod_flags(uuid, rule_id, flag_timestamp, message, message_word, flagged_word) VALUES(?,?,?,?,?,?);";

        try (Connection conn = conn()) {
            conn.setAutoCommit(false);

            try (PreparedStatement deleteStatement = conn.prepareStatement(deleteSql);
                 PreparedStatement insertStatement = conn.prepareStatement(insertSql)) {

                deleteStatement.setString(1, uuid);
                deleteStatement.executeUpdate();

                for (AutoModFlagDTO flag : flags) {
                    insertStatement.setString(1, uuid);
                    insertStatement.setString(2, flag.ruleId());
                    insertStatement.setLong(3, flag.timestamp());
                    insertStatement.setString(4, flag.message());
                    insertStatement.setString(5, truncate(flag.messageWord(), 256));
                    insertStatement.setString(6, truncate(flag.flaggedWord(), 256));
                    insertStatement.addBatch();
                }

                insertStatement.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                log.severe("Failed to save automod flags for " + uuid + ": " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.severe("Failed to open database connection while saving automod flags for " + uuid + ": " + e.getMessage());
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    public List<AutoModFlagDTO> getAutoModFlags(String uuid) {
        List<AutoModFlagDTO> flags = new ArrayList<>();

        if (uuid == null) {
            log.warning("getAutoModFlags called with null uuid");
            return flags;
        }

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement("SELECT rule_id, flag_timestamp, message, message_word, flagged_word FROM automod_flags WHERE uuid=?;")) {
            statement.setString(1, uuid);

            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    flags.add(new AutoModFlagDTO(
                            results.getString(1),
                            results.getLong(2),
                            results.getString(3),
                            results.getString(4),
                            results.getString(5)
                    ));
                }
            }
        } catch (SQLException e) {
            log.severe("Failed to load automod flags for " + uuid + ": " + e.getMessage());
        }
        return flags;
    }

    public void insertModeration(String uuid, long startTime, long endTime, String reason, String type) {
        if (uuid == null || reason == null || type == null) {
            log.warning("insertModeration called with null argument(s)");
            return;
        }

        final String sql = """
                INSERT INTO moderation(uuid, start_time, end_time, reason, type)
                VALUES(?, ?, ?, ?, ?)
                """;

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, uuid);
            statement.setLong(2, startTime);
            statement.setLong(3, endTime);
            statement.setString(4, reason);
            statement.setString(5, type);

            statement.executeUpdate();
        } catch (SQLException e) {
            log.severe("Failed to insert moderation record for " + uuid + ": " + e.getMessage());
        }
    }

    public String getPlayerUuidByName(String name) {
        if (name == null) {
            return null;
        }

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement("SELECT uuid FROM player_data WHERE name=?;")) {
            statement.setString(1, name);
            try (ResultSet results = statement.executeQuery()) {
                if (results.next()) {
                    return results.getString(1);
                }
            }
        } catch (SQLException e) {
            log.severe("Failed to get player uuid for " + name + ": " + e.getMessage());
        }
        return null;
    }

    public String getPlayerUsernameByUuid(String uuid) {
        if (uuid == null) {
            return null;
        }

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement("SELECT name FROM player_data WHERE uuid=?;")) {
            statement.setString(1, uuid);
            try (ResultSet results = statement.executeQuery()) {
                if (results.next()) {
                    return results.getString(1);
                }
            }
        } catch (SQLException e) {
            log.severe("Failed to get player uuid for " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    public List<PlayerDTO> getOnlinePlayers() {
        List<PlayerDTO> players = new ArrayList<>();
        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement("SELECT uuid, name FROM player_data WHERE uuid IN (SELECT uuid FROM online_users);");
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                players.add(new PlayerDTO(results.getString(1), results.getString(2)));
            }
        } catch (SQLException e) {
            log.severe("Failed to get online players: " + e.getMessage());
        }
        return players;
    }

    public List<BuildingDTO> getBuildingsByPlayer(String uuid) {
        List<BuildingDTO> buildings = new ArrayList<>();
        if (uuid == null) return buildings;

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement("SELECT building_id, coordinate_id, player_id, is_public, player_built, time_added, lat, lon FROM buildings WHERE player_id=?;")) {
            statement.setString(1, uuid);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    buildings.add(mapBuilding(results));
                }
            }
        } catch (SQLException e) {
            log.severe("Failed to get buildings for " + uuid + ": " + e.getMessage());
        }
        return buildings;
    }

    public List<BuildingDTO> getBuildingsByArea(double minLat, double maxLat, double minLon, double maxLon) {
        List<BuildingDTO> buildings = new ArrayList<>();
        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement("SELECT building_id, coordinate_id, player_id, is_public, player_built, time_added, lat, lon FROM buildings WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?;")) {
            statement.setDouble(1, minLat);
            statement.setDouble(2, maxLat);
            statement.setDouble(3, minLon);
            statement.setDouble(4, maxLon);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    buildings.add(mapBuilding(results));
                }
            }
        } catch (SQLException e) {
            log.severe("Failed to get buildings by area: " + e.getMessage());
        }
        return buildings;
    }

    private BuildingDTO mapBuilding(ResultSet results) throws SQLException {
        Timestamp timestamp = results.getTimestamp(6);
        return new BuildingDTO(
                results.getInt(1),
                results.getInt(2),
                results.getString(3),
                results.getBoolean(4),
                results.getBoolean(5),
                timestamp != null ? timestamp.toLocalDateTime() : null,
                results.getDouble(7),
                results.getDouble(8)
        );
    }
}
