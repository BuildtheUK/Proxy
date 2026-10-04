package org.btuk.proxy.database.sql;

import lombok.extern.java.Log;
import org.btuk.proxy.database.dto.StripeCustomerDTO;
import org.btuk.proxy.database.dto.StripeEventDTO;
import org.btuk.proxy.database.dto.StripePaymentDTO;
import org.btuk.proxy.database.dto.StripeSubscriptionDTO;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

@Log
public class StripeSQL extends AbstractSQL {

    public StripeSQL(DataSource datasource) {
        super(datasource);
    }

    // --- Stripe Events ---

    public boolean isEventProcessed(String eventId) {
        if (eventId == null) return false;
        try (Connection conn = conn();
             PreparedStatement stmt = conn.prepareStatement("SELECT status FROM stripe_events WHERE id = ?;")) {
            stmt.setString(1, eventId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String status = rs.getString("status");
                    return "PROCESSED".equalsIgnoreCase(status);
                }
            }
        } catch (SQLException e) {
            log.severe("Failed to check if event " + eventId + " is processed: " + e.getMessage());
        }
        return false;
    }

    public boolean recordEvent(String eventId, String type, String status, long createdAt) {
        if (eventId == null || type == null) return false;
        final String sql = """
            INSERT INTO stripe_events (id, type, status, created_at)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE type = VALUES(type), status = VALUES(status);
            """;
        try (Connection conn = conn(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, eventId);
            stmt.setString(2, type);
            stmt.setString(3, status);
            stmt.setLong(4, createdAt);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.severe("Failed to record stripe event " + eventId + ": " + e.getMessage());
            return false;
        }
    }

    public boolean markEventProcessed(String eventId) {
        if (eventId == null) return false;
        final String sql = "UPDATE stripe_events SET status = 'PROCESSED', processed_at = ? WHERE id = ?;";
        try (Connection conn = conn(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setString(2, eventId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.severe("Failed to mark event processed " + eventId + ": " + e.getMessage());
            return false;
        }
    }

    public boolean markEventFailed(String eventId, String errorMessage) {
        if (eventId == null) return false;
        final String sql = "UPDATE stripe_events SET status = 'FAILED', error_message = ?, processed_at = ? WHERE id = ?;";
        try (Connection conn = conn(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, errorMessage);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setString(3, eventId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.severe("Failed to mark event failed " + eventId + ": " + e.getMessage());
            return false;
        }
    }

    public StripeEventDTO getEvent(String eventId) {
        if (eventId == null) return null;
        try (Connection conn = conn();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM stripe_events WHERE id = ?;")) {
            stmt.setString(1, eventId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long processedAt = rs.getLong("processed_at");
                    return new StripeEventDTO(
                        rs.getString("id"),
                        rs.getString("type"),
                        rs.getString("status"),
                        rs.getString("error_message"),
                        rs.getLong("created_at"),
                        rs.wasNull() ? null : processedAt
                    );
                }
            }
        } catch (SQLException e) {
            log.severe("Failed to fetch event " + eventId + ": " + e.getMessage());
        }
        return null;
    }

    // --- Stripe Customers ---

    public boolean saveOrUpdateCustomer(String customerId, String minecraftUuid, String minecraftName) {
        if (customerId == null || minecraftUuid == null) return false;
        final String sql = """
            INSERT INTO stripe_customers (customer_id, minecraft_uuid, minecraft_name, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                minecraft_uuid = VALUES(minecraft_uuid),
                minecraft_name = VALUES(minecraft_name),
                updated_at = VALUES(updated_at);
            """;
        long now = System.currentTimeMillis();
        try (Connection conn = conn(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, customerId);
            stmt.setString(2, minecraftUuid);
            stmt.setString(3, minecraftName);
            stmt.setLong(4, now);
            stmt.setLong(5, now);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.severe("Failed to save/update customer " + customerId + ": " + e.getMessage());
            return false;
        }
    }

    public String getMinecraftUuidByCustomer(String customerId) {
        if (customerId == null) return null;
        try (Connection conn = conn();
             PreparedStatement stmt = conn.prepareStatement("SELECT minecraft_uuid FROM stripe_customers WHERE customer_id = ?;")) {
            stmt.setString(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("minecraft_uuid");
                }
            }
        } catch (SQLException e) {
            log.severe("Failed to get minecraft UUID for customer " + customerId + ": " + e.getMessage());
        }
        return null;
    }

    public StripeCustomerDTO getCustomer(String customerId) {
        if (customerId == null) return null;
        try (Connection conn = conn();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM stripe_customers WHERE customer_id = ?;")) {
            stmt.setString(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new StripeCustomerDTO(
                        rs.getString("customer_id"),
                        rs.getString("minecraft_uuid"),
                        rs.getString("minecraft_name"),
                        rs.getLong("created_at"),
                        rs.getLong("updated_at")
                    );
                }
            }
        } catch (SQLException e) {
            log.severe("Failed to get customer " + customerId + ": " + e.getMessage());
        }
        return null;
    }

    // --- Stripe Subscriptions ---

    public boolean saveOrUpdateSubscription(String subscriptionId, String customerId, String minecraftUuid,
                                            String status, String planId, int durationMonths,
                                            Long periodStart, Long periodEnd) {
        if (subscriptionId == null || customerId == null || minecraftUuid == null) return false;
        final String sql = """
            INSERT INTO stripe_subscriptions (
                subscription_id, customer_id, minecraft_uuid, status, plan_id,
                duration_months, current_period_start, current_period_end, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                customer_id = VALUES(customer_id),
                minecraft_uuid = VALUES(minecraft_uuid),
                status = VALUES(status),
                plan_id = VALUES(plan_id),
                duration_months = VALUES(duration_months),
                current_period_start = VALUES(current_period_start),
                current_period_end = VALUES(current_period_end),
                updated_at = VALUES(updated_at);
            """;
        long now = System.currentTimeMillis();
        try (Connection conn = conn(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, subscriptionId);
            stmt.setString(2, customerId);
            stmt.setString(3, minecraftUuid);
            stmt.setString(4, status != null ? status : "active");
            stmt.setString(5, planId);
            stmt.setInt(6, durationMonths > 0 ? durationMonths : 1);
            if (periodStart != null) stmt.setLong(7, periodStart); else stmt.setNull(7, Types.BIGINT);
            if (periodEnd != null) stmt.setLong(8, periodEnd); else stmt.setNull(8, Types.BIGINT);
            stmt.setLong(9, now);
            stmt.setLong(10, now);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.severe("Failed to save/update subscription " + subscriptionId + ": " + e.getMessage());
            return false;
        }
    }

    public boolean updateSubscriptionStatus(String subscriptionId, String status, Long periodStart, Long periodEnd) {
        if (subscriptionId == null) return false;
        final String sql = """
            UPDATE stripe_subscriptions
            SET status = ?,
                current_period_start = COALESCE(?, current_period_start),
                current_period_end = COALESCE(?, current_period_end),
                updated_at = ?
            WHERE subscription_id = ?;
            """;
        try (Connection conn = conn(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            if (periodStart != null) stmt.setLong(2, periodStart); else stmt.setNull(2, Types.BIGINT);
            if (periodEnd != null) stmt.setLong(3, periodEnd); else stmt.setNull(3, Types.BIGINT);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.setString(5, subscriptionId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.severe("Failed to update subscription status " + subscriptionId + ": " + e.getMessage());
            return false;
        }
    }

    public String getMinecraftUuidBySubscription(String subscriptionId) {
        if (subscriptionId == null) return null;
        try (Connection conn = conn();
             PreparedStatement stmt = conn.prepareStatement("SELECT minecraft_uuid FROM stripe_subscriptions WHERE subscription_id = ?;")) {
            stmt.setString(1, subscriptionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("minecraft_uuid");
                }
            }
        } catch (SQLException e) {
            log.severe("Failed to get minecraft UUID for subscription " + subscriptionId + ": " + e.getMessage());
        }
        return null;
    }

    public StripeSubscriptionDTO getSubscription(String subscriptionId) {
        if (subscriptionId == null) return null;
        try (Connection conn = conn();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM stripe_subscriptions WHERE subscription_id = ?;")) {
            stmt.setString(1, subscriptionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long periodStart = rs.getLong("current_period_start");
                    Long startVal = rs.wasNull() ? null : periodStart;
                    long periodEnd = rs.getLong("current_period_end");
                    Long endVal = rs.wasNull() ? null : periodEnd;
                    return new StripeSubscriptionDTO(
                        rs.getString("subscription_id"),
                        rs.getString("customer_id"),
                        rs.getString("minecraft_uuid"),
                        rs.getString("status"),
                        rs.getString("plan_id"),
                        rs.getInt("duration_months"),
                        startVal,
                        endVal,
                        rs.getLong("created_at"),
                        rs.getLong("updated_at")
                    );
                }
            }
        } catch (SQLException e) {
            log.severe("Failed to get subscription " + subscriptionId + ": " + e.getMessage());
        }
        return null;
    }

    // --- Stripe Payments ---

    public boolean isPaymentProcessed(String paymentId) {
        if (paymentId == null) return false;
        try (Connection conn = conn();
             PreparedStatement stmt = conn.prepareStatement("SELECT status FROM stripe_payments WHERE id = ?;")) {
            stmt.setString(1, paymentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String status = rs.getString("status");
                    return "PAID".equalsIgnoreCase(status) || "REFUNDED".equalsIgnoreCase(status);
                }
            }
        } catch (SQLException e) {
            log.severe("Failed to check payment " + paymentId + ": " + e.getMessage());
        }
        return false;
    }

    public boolean recordPayment(String paymentId, String chargeId, String customerId, String subscriptionId,
                                 String minecraftUuid, String paymentType, long amount, String currency,
                                 int durationDays, String status, long createdAt) {
        if (paymentId == null || minecraftUuid == null) return false;
        final String sql = """
            INSERT INTO stripe_payments (
                id, charge_id, customer_id, subscription_id, minecraft_uuid,
                payment_type, amount, currency, duration_days, status, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                charge_id = COALESCE(VALUES(charge_id), charge_id),
                status = VALUES(status),
                amount = VALUES(amount),
                currency = VALUES(currency),
                duration_days = VALUES(duration_days);
            """;
        try (Connection conn = conn(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, paymentId);
            stmt.setString(2, chargeId);
            stmt.setString(3, customerId);
            stmt.setString(4, subscriptionId);
            stmt.setString(5, minecraftUuid);
            stmt.setString(6, paymentType != null ? paymentType : "ONE_TIME");
            stmt.setLong(7, amount);
            stmt.setString(8, currency);
            stmt.setInt(9, durationDays);
            stmt.setString(10, status != null ? status : "PAID");
            stmt.setLong(11, createdAt);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.severe("Failed to record payment " + paymentId + ": " + e.getMessage());
            return false;
        }
    }

    public StripePaymentDTO getPayment(String paymentId) {
        if (paymentId == null) return null;
        try (Connection conn = conn();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM stripe_payments WHERE id = ?;")) {
            stmt.setString(1, paymentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapPayment(rs);
                }
            }
        } catch (SQLException e) {
            log.severe("Failed to get payment " + paymentId + ": " + e.getMessage());
        }
        return null;
    }

    public StripePaymentDTO getPaymentByChargeId(String chargeId) {
        if (chargeId == null) return null;
        try (Connection conn = conn();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM stripe_payments WHERE charge_id = ?;")) {
            stmt.setString(1, chargeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapPayment(rs);
                }
            }
        } catch (SQLException e) {
            log.severe("Failed to get payment by charge " + chargeId + ": " + e.getMessage());
        }
        return null;
    }

    public StripePaymentDTO getLatestPaymentByCustomer(String customerId) {
        if (customerId == null) return null;
        try (Connection conn = conn();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM stripe_payments WHERE customer_id = ? ORDER BY created_at DESC LIMIT 1;")) {
            stmt.setString(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapPayment(rs);
                }
            }
        } catch (SQLException e) {
            log.severe("Failed to get latest payment for customer " + customerId + ": " + e.getMessage());
        }
        return null;
    }

    public boolean markPaymentRefunded(String paymentId, long refundedAt) {
        if (paymentId == null) return false;
        final String sql = "UPDATE stripe_payments SET status = 'REFUNDED', refunded_at = ? WHERE id = ?;";
        try (Connection conn = conn(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, refundedAt);
            stmt.setString(2, paymentId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.severe("Failed to mark payment refunded " + paymentId + ": " + e.getMessage());
            return false;
        }
    }

    public boolean markPaymentRefundedByChargeId(String chargeId, long refundedAt) {
        if (chargeId == null) return false;
        final String sql = "UPDATE stripe_payments SET status = 'REFUNDED', refunded_at = ? WHERE charge_id = ?;";
        try (Connection conn = conn(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, refundedAt);
            stmt.setString(2, chargeId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.severe("Failed to mark payment refunded for charge " + chargeId + ": " + e.getMessage());
            return false;
        }
    }

    private StripePaymentDTO mapPayment(ResultSet rs) throws SQLException {
        long refundedAt = rs.getLong("refunded_at");
        Long refundedVal = rs.wasNull() ? null : refundedAt;
        return new StripePaymentDTO(
            rs.getString("id"),
            rs.getString("charge_id"),
            rs.getString("customer_id"),
            rs.getString("subscription_id"),
            rs.getString("minecraft_uuid"),
            rs.getString("payment_type"),
            rs.getLong("amount"),
            rs.getString("currency"),
            rs.getInt("duration_days"),
            rs.getString("status"),
            rs.getLong("created_at"),
            refundedVal
        );
    }
}
