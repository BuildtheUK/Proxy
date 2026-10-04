package org.btuk.proxy.core.role;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for managing player roles and temporary cosmetic/reward roles.
 * Decouples role management from payment/stripe logic so roles can be granted through any mechanism.
 */
public interface RoleManager {

    /**
     * Adds calendar months to a temporary role for a player.
     * Takes into account variable month lengths and leap years.
     * If the player already has an active role, adds to existing expiry.
     *
     * @param uuid player UUID
     * @param roleName role/group name (e.g. "reward")
     * @param months number of months to add
     * @return CompletableFuture completing with true if successfully applied
     */
    CompletableFuture<Boolean> addTemporaryRoleMonths(UUID uuid, String roleName, int months);

    /**
     * Adds calendar years to a temporary role for a player.
     * Takes into account leap years.
     *
     * @param uuid player UUID
     * @param roleName role/group name (e.g. "reward")
     * @param years number of years to add
     * @return CompletableFuture completing with true if successfully applied
     */
    CompletableFuture<Boolean> addTemporaryRoleYears(UUID uuid, String roleName, int years);

    /**
     * Adds duration to a temporary role for a player.
     *
     * @param uuid player UUID
     * @param roleName role/group name (e.g. "reward")
     * @param duration duration to grant
     * @return CompletableFuture completing with true if successfully applied
     */
    CompletableFuture<Boolean> addTemporaryRole(UUID uuid, String roleName, Duration duration);

    /**
     * Helper to add the default "reward" role for months.
     */
    default CompletableFuture<Boolean> addRewardRoleMonths(UUID uuid, int months) {
        return addTemporaryRoleMonths(uuid, "reward", months);
    }

    /**
     * Helper to add the default "reward" role for years.
     */
    default CompletableFuture<Boolean> addRewardRoleYears(UUID uuid, int years) {
        return addTemporaryRoleYears(uuid, "reward", years);
    }

    /**
     * Helper to add the default "reward" role for a duration.
     */
    default CompletableFuture<Boolean> addRewardRole(UUID uuid, Duration duration) {
        return addTemporaryRole(uuid, "reward", duration);
    }

    /**
     * Completely removes a role/group from a player.
     *
     * @param uuid player UUID
     * @param roleName role/group name
     * @return CompletableFuture completing with true if successfully removed
     */
    CompletableFuture<Boolean> removeRole(UUID uuid, String roleName);

    /**
     * Helper to remove the default "reward" role.
     */
    default CompletableFuture<Boolean> removeRewardRole(UUID uuid) {
        return removeRole(uuid, "reward");
    }

    /**
     * Reduces the duration of an active temporary role by calendar months (e.g. on refund).
     * If the remaining time is less than or equal to now, the role is completely removed.
     *
     * @param uuid player UUID
     * @param roleName role/group name
     * @param months months to reduce
     * @return CompletableFuture completing with true if successfully adjusted
     */
    CompletableFuture<Boolean> reduceTemporaryRoleMonths(UUID uuid, String roleName, int months);

    /**
     * Reduces the duration of an active temporary role by calendar years (e.g. on refund).
     *
     * @param uuid player UUID
     * @param roleName role/group name
     * @param years years to reduce
     * @return CompletableFuture completing with true if successfully adjusted
     */
    CompletableFuture<Boolean> reduceTemporaryRoleYears(UUID uuid, String roleName, int years);

    /**
     * Reduces the duration of an active temporary role for a player (e.g. on refund).
     *
     * @param uuid player UUID
     * @param roleName role/group name
     * @param duration duration to reduce
     * @return CompletableFuture completing with true if successfully adjusted
     */
    CompletableFuture<Boolean> reduceTemporaryRole(UUID uuid, String roleName, Duration duration);

    /**
     * Helper to reduce the default "reward" role by calendar months.
     */
    default CompletableFuture<Boolean> reduceRewardRoleMonths(UUID uuid, int months) {
        return reduceTemporaryRoleMonths(uuid, "reward", months);
    }

    /**
     * Helper to reduce the default "reward" role by calendar years.
     */
    default CompletableFuture<Boolean> reduceRewardRoleYears(UUID uuid, int years) {
        return reduceTemporaryRoleYears(uuid, "reward", years);
    }

    /**
     * Helper to reduce the default "reward" role by duration.
     */
    default CompletableFuture<Boolean> reduceRewardRole(UUID uuid, Duration duration) {
        return reduceTemporaryRole(uuid, "reward", duration);
    }
}
