package net.bteuk.proxy.database.sql;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class GlobalSQL extends AbstractSQL {
    public GlobalSQL(DataSource datasource) {
        super(datasource);
    }

    public boolean createUser(String uuid, String name, String playerSkin) {

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement("INSERT INTO player_data(uuid,name,last_online,last_submit,player_skin) VALUES('" + uuid + "','" + name + "'," + System.currentTimeMillis() + "," + 0 + ",'" + playerSkin + "');")) {

            statement.executeUpdate();
            return true;


        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean insertMessage(String recipient, String message) {
        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement("INSERT INTO messages(recipient,message) VALUES(?,?);")) {
            statement.setString(1, recipient);
            statement.setString(2, message);

            statement.executeUpdate();
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<String> getOfflineMessages(String uuid) {
        List<String> messages = new ArrayList<>();
        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement("SELECT message FROM messages WHERE recipient=?;")) {
            statement.setString(1, uuid);
            ResultSet results = statement.executeQuery();

            while (results.next()) {
                messages.add(results.getString(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }

    public boolean checkIfUserExistsByName(String name) {
        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement("SELECT uuid FROM player_data WHERE name=?;")) {
            statement.setString(1, name);
            ResultSet results = statement.executeQuery();

            if (results.next()) {
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

}
