package net.bteuk.proxy.database.sql;

import lombok.extern.java.Log;
import net.bteuk.network.lib.enums.PlotDifficulties;
import net.bteuk.network.lib.utils.Reviewing;
import net.bteuk.proxy.database.sql.migration.AcceptData;
import net.bteuk.proxy.database.sql.migration.DenyData;
import net.bteuk.proxy.database.sql.migration.PlotSubmissions;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Log
public class PlotSQL extends net.bteuk.proxy.database.sql.AbstractSQL {

    public PlotSQL(DataSource datasource) {
        super(datasource);
    }

    public int insertReturnId(String sql) {
        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.executeUpdate();
            ResultSet result = statement.getGeneratedKeys();
            if (result.next()) {
                return result.getInt(1);
            }

        } catch (SQLException e) {
            log.severe("An error occurred while inserting a row in the database: " + e);
        }

        return 0;
    }

    public double getReviewerReputation(String uuid) {
        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement("SELECT reputation FROM reviewers WHERE uuid=?;")) {
            statement.setString(1, uuid);
            ResultSet results = statement.executeQuery();

            if (results.next()) {
                return results.getDouble(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public List<Integer> getReviewablePlots(String uuid, boolean isArchitect, boolean isReviewer) {
        addReviewerIfNotExists(uuid, isArchitect, isReviewer); // Add an entry for relevant users.
        List<PlotDifficulties> difficulties = Reviewing.getAvailablePlotDifficulties(isArchitect, isReviewer, getReviewerReputation(uuid));

        List<Integer> submitted_plots = new ArrayList<>();

        for (PlotDifficulties difficulty : difficulties) {
            submitted_plots.addAll(getIntList("SELECT pd.id FROM plot_data AS pd INNER JOIN plot_submission AS ps ON pd.id=ps.plot_id WHERE ps.status='submitted' AND pd.difficulty=" + difficulty.getValue() + ";"));
        }

        // Get all plots that the user is the owner or a member of, don't use those in the count.
        List<Integer> member_plots = getIntList("SELECT id FROM plot_members WHERE uuid='" + uuid + "';");

        submitted_plots.removeAll(member_plots);

        return submitted_plots;
    }

    public int getReviewablePlotCount(String uuid, boolean isArchitect, boolean isReviewer) {
        return getReviewablePlots(uuid, isArchitect, isReviewer).size();
    }

    private List<Integer> getVerifiablePlots(String uuid, boolean isReviewer) {
        List<PlotDifficulties> difficulties = Reviewing.getAvailablePlotDifficulties(isReviewer, isReviewer, getReviewerReputation(uuid));

        List<Integer> plots_awaiting_verification = new ArrayList<>();

        for (PlotDifficulties difficulty : difficulties) {
            plots_awaiting_verification.addAll(getIntList("SELECT pd.id FROM plot_data AS pd INNER JOIN plot_submission AS ps ON pd.id=ps.plot_id WHERE ps.status='awaiting verification' AND pd.difficulty=" + difficulty.getValue() + " ORDER BY ps.submit_time ASC;"));
        }

        // Get all plots that the user is the owner or a member of, don't use those in the count.
        List<Integer> member_plots = getIntList("SELECT id FROM plot_members WHERE uuid='" + uuid + "';");

        // Get all plots that the user has reviewed, don't use those in the count.
        List<Integer> reviewed_plots = getIntList("SELECT plot_id FROM plot_review WHERE reviewer='" + uuid + "' AND completed=0;");

        plots_awaiting_verification.removeAll(member_plots);
        plots_awaiting_verification.removeAll(reviewed_plots);

        return plots_awaiting_verification;
    }

    public int getVerifiablePlotCount(String uuid, boolean isReviewer) {
        return getVerifiablePlots(uuid, isReviewer).size();
    }

    public List<AcceptData> getAcceptData() {
        List<AcceptData> acceptData = new ArrayList<>();

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement("SELECT * FROM accept_data;"); ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                acceptData.add(new AcceptData(results.getInt("id"), results.getString("uuid"), results.getString("reviewer"), results.getInt("book_id"), results.getInt("accuracy"), results.getInt("quality"), results.getLong("accept_time")));
            }
        } catch (SQLException e) {
            log.severe("An error occurred while fetching accept_data: " + e.getLocalizedMessage());
        }
        return acceptData;
    }

    public List<DenyData> getDenyData() {
        List<DenyData> denyData = new ArrayList<>();

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement("SELECT * FROM deny_data;"); ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                denyData.add(new DenyData(results.getInt("id"), results.getString("uuid"), results.getString("reviewer"), results.getInt("book_id"), results.getInt("attempt"), results.getLong("deny_time")));
            }
        } catch (SQLException e) {
            log.severe("An error occurred while fetching deny_data: " + e.getLocalizedMessage());
        }
        return denyData;
    }

    /**
     * Get the plot submissions from the OLD table (plot_submissions)
     *
     * @return the old plot submissions
     */
    public List<PlotSubmissions> getPlotSubmissions() {
        List<PlotSubmissions> plotSubmissions = new ArrayList<>();

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement("SELECT * FROM plot_submissions;"); ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                plotSubmissions.add(new PlotSubmissions(results.getInt("id"), results.getLong("submit_time"), results.getLong("last_query")));
            }
        } catch (SQLException e) {
            log.severe("An error occurred while fetching plot_submissions: " + e.getLocalizedMessage());
        }
        return plotSubmissions;
    }

    public void addReviewerIfNotExists(String uuid, boolean isArchitect, boolean isReviewer) {
        if ((!isArchitect && !isReviewer) || hasRow("SELECT 1 FROM reviewers WHERE uuid='" + uuid + "';")) {
            return;
        }

        double initialValue = isReviewer ? 5 : 0;
        update("INSERT INTO reviewers(uuid,reputation) VALUES('" + uuid + "'," + initialValue + ");");
    }

    public int[][] getOldPlotCorners(int plotID) {

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement("SELECT COUNT(corner) FROM" + " old_plot_corners WHERE id=" + plotID + ";"); ResultSet results = statement.executeQuery()) {

            results.next();

            int[][] corners = new int[results.getInt(1)][2];

            getOldPlotCorners(corners, plotID);

            return corners;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private int[][] getOldPlotCorners(int[][] corners, int plotID) {

        try (Connection conn = conn(); PreparedStatement statement = conn.prepareStatement("SELECT x,z FROM old_plot_corners WHERE id=" + plotID + ";"); ResultSet results = statement.executeQuery()) {

            for (int i = 0; i < corners.length; i++) {

                results.next();
                corners[i][0] = results.getInt(1);
                corners[i][1] = results.getInt(2);
            }

            return corners;
        } catch (SQLException e) {
            e.printStackTrace();
            return corners;
        }
    }
}
