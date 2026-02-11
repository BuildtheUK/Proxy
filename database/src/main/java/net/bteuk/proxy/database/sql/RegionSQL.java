package net.bteuk.proxy.database.sql;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class RegionSQL extends net.bteuk.proxy.database.sql.AbstractSQL {
    public RegionSQL(DataSource datasource) {
        super(datasource);
    }

    public ArrayList<String[]> getStringArrayList(String sql) throws SQLException {

        ArrayList<String[]> list = new ArrayList<>();

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement(sql); ResultSet results = statement.executeQuery()) {
            while (results.next()) {

                list.add(new String[]{results.getString(1), results.getString(2)});
            }
        }

        return list;
    }
}
