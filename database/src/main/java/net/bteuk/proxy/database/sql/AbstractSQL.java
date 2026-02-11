package net.bteuk.proxy.database.sql;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public abstract class AbstractSQL {

    private final DataSource dataSource;

    public AbstractSQL(DataSource datasource) {

        this.dataSource = datasource;

    }

    Connection conn() throws SQLException {
        return dataSource.getConnection();
    }

    public boolean hasRow(String sql) {

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement(sql); ResultSet results = statement.executeQuery()) {

            return results.next();

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    //Generic update statement, return true if successful.
    public boolean update(String sql) {

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement(sql)) {

            statement.executeUpdate();

            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean getBoolean(String sql) {

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement(sql); ResultSet results = statement.executeQuery()) {

            if (results.next()) {

                return results.getBoolean(1);

            } else {

                return false;

            }

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public int getInt(String sql) {

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement(sql); ResultSet results = statement.executeQuery()) {

            if (results.next()) {

                return results.getInt(1);

            } else {

                return 0;

            }

        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public double getDouble(String sql) {

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement(sql); ResultSet results = statement.executeQuery()) {

            if (results.next()) {

                return results.getDouble(1);

            } else {

                return 0;

            }

        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public float getFloat(String sql) {

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement(sql); ResultSet results = statement.executeQuery()) {

            if (results.next()) {

                return results.getInt(1);

            } else {

                return 0;

            }

        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public long getLong(String sql) {

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement(sql); ResultSet results = statement.executeQuery()) {

            if (results.next()) {

                return results.getLong(1);

            } else {

                return 0;

            }

        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public ResultSet getResultSet(String sql) {

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement(sql); ResultSet results = statement.executeQuery()) {

            return results;

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getString(String sql) {

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement(sql); ResultSet results = statement.executeQuery()) {

            if (results.next()) {

                return results.getString(1);

            } else {

                return null;

            }

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public ArrayList<String> getStringList(String sql) {

        ArrayList<String> list = new ArrayList<>();

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement(sql); ResultSet results = statement.executeQuery()) {

            while (results.next()) {

                list.add(results.getString(1));

            }

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }

        return list;
    }

    public ArrayList<Integer> getIntList(String sql) {

        ArrayList<Integer> list = new ArrayList<>();

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement(sql); ResultSet results = statement.executeQuery()) {

            while (results.next()) {

                list.add(results.getInt(1));

            }

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }

        return list;
    }
}
