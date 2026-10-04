package org.btuk.proxy.core.role;

import lombok.extern.java.Log;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.types.InheritanceNode;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Log
public class LuckPermsRoleManager implements RoleManager {

    private LuckPerms luckPerms;

    public LuckPermsRoleManager() {
    }

    public LuckPermsRoleManager(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    private LuckPerms getLuckPerms() {
        if (luckPerms == null) {
            try {
                luckPerms = LuckPermsProvider.get();
            } catch (IllegalStateException | NoClassDefFoundError e) {
                log.severe("LuckPerms is not available: " + e.getMessage());
                return null;
            }
        }
        return luckPerms;
    }

    @Override
    public CompletableFuture<Boolean> addTemporaryRoleMonths(UUID uuid, String roleName, int months) {
        if (months <= 0) return CompletableFuture.completedFuture(false);
        return addTemporaryRole(uuid, roleName, base -> base.plusMonths(months));
    }

    @Override
    public CompletableFuture<Boolean> addTemporaryRoleYears(UUID uuid, String roleName, int years) {
        if (years <= 0) return CompletableFuture.completedFuture(false);
        return addTemporaryRole(uuid, roleName, base -> base.plusYears(years));
    }

    @Override
    public CompletableFuture<Boolean> addTemporaryRole(UUID uuid, String roleName, Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return CompletableFuture.completedFuture(false);
        }
        return addTemporaryRole(uuid, roleName, base -> base.plus(duration));
    }

    private CompletableFuture<Boolean> addTemporaryRole(UUID uuid, String roleName, Function<ZonedDateTime, ZonedDateTime> adder) {
        if (uuid == null || roleName == null) {
            return CompletableFuture.completedFuture(false);
        }

        LuckPerms lp = getLuckPerms();
        if (lp == null) {
            log.severe("Cannot add temporary role " + roleName + " to " + uuid + " - LuckPerms is unavailable.");
            return CompletableFuture.completedFuture(false);
        }

        return lp.getUserManager().modifyUser(uuid, user -> {
            Instant now = Instant.now();
            ZonedDateTime base = now.atZone(ZoneOffset.UTC);

            List<InheritanceNode> existingNodes = user.getNodes().stream()
                .filter(n -> n instanceof InheritanceNode)
                .map(n -> (InheritanceNode) n)
                .filter(n -> n.getGroupName().equalsIgnoreCase(roleName))
                .toList();

            for (InheritanceNode node : existingNodes) {
                if (node.hasExpiry()) {
                    Instant currentExpiry = node.getExpiry();
                    if (currentExpiry != null && currentExpiry.isAfter(now)) {
                        base = currentExpiry.atZone(ZoneOffset.UTC);
                    }
                }
                user.data().remove(node);
            }

            Instant newExpiry = adder.apply(base).toInstant();
            InheritanceNode newNode = InheritanceNode.builder(roleName)
                .expiry(newExpiry)
                .build();
            user.data().add(newNode);
            log.info("Granted temporary role '" + roleName + "' to " + uuid + " until " + newExpiry);
        }).thenApply(v -> true).exceptionally(ex -> {
            log.severe("Failed to modify user " + uuid + " for role " + roleName + ": " + ex.getMessage());
            return false;
        });
    }

    @Override
    public CompletableFuture<Boolean> removeRole(UUID uuid, String roleName) {
        if (uuid == null || roleName == null) {
            return CompletableFuture.completedFuture(false);
        }

        LuckPerms lp = getLuckPerms();
        if (lp == null) {
            log.severe("Cannot remove role " + roleName + " from " + uuid + " - LuckPerms is unavailable.");
            return CompletableFuture.completedFuture(false);
        }

        return lp.getUserManager().modifyUser(uuid, user -> {
            user.data().clear(n -> n instanceof InheritanceNode && ((InheritanceNode) n).getGroupName().equalsIgnoreCase(roleName));
            log.info("Removed role '" + roleName + "' from " + uuid);
        }).thenApply(v -> true).exceptionally(ex -> {
            log.severe("Failed to remove role " + roleName + " from " + uuid + ": " + ex.getMessage());
            return false;
        });
    }

    @Override
    public CompletableFuture<Boolean> reduceTemporaryRoleMonths(UUID uuid, String roleName, int months) {
        if (months <= 0) return CompletableFuture.completedFuture(false);
        return reduceTemporaryRole(uuid, roleName, base -> base.minusMonths(months));
    }

    @Override
    public CompletableFuture<Boolean> reduceTemporaryRoleYears(UUID uuid, String roleName, int years) {
        if (years <= 0) return CompletableFuture.completedFuture(false);
        return reduceTemporaryRole(uuid, roleName, base -> base.minusYears(years));
    }

    @Override
    public CompletableFuture<Boolean> reduceTemporaryRole(UUID uuid, String roleName, Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return CompletableFuture.completedFuture(false);
        }
        return reduceTemporaryRole(uuid, roleName, base -> base.minus(duration));
    }

    private CompletableFuture<Boolean> reduceTemporaryRole(UUID uuid, String roleName, Function<ZonedDateTime, ZonedDateTime> reducer) {
        if (uuid == null || roleName == null) {
            return CompletableFuture.completedFuture(false);
        }

        LuckPerms lp = getLuckPerms();
        if (lp == null) {
            log.severe("Cannot reduce temporary role " + roleName + " for " + uuid + " - LuckPerms is unavailable.");
            return CompletableFuture.completedFuture(false);
        }

        return lp.getUserManager().modifyUser(uuid, user -> {
            Instant now = Instant.now();
            List<InheritanceNode> existingNodes = user.getNodes().stream()
                .filter(n -> n instanceof InheritanceNode)
                .map(n -> (InheritanceNode) n)
                .filter(n -> n.getGroupName().equalsIgnoreCase(roleName))
                .toList();

            for (InheritanceNode node : existingNodes) {
                user.data().remove(node);
                if (node.hasExpiry()) {
                    Instant currentExpiry = node.getExpiry();
                    if (currentExpiry != null && currentExpiry.isAfter(now)) {
                        Instant newExpiry = reducer.apply(currentExpiry.atZone(ZoneOffset.UTC)).toInstant();
                        if (newExpiry.isAfter(now)) {
                            InheritanceNode newNode = InheritanceNode.builder(roleName)
                                .expiry(newExpiry)
                                .build();
                            user.data().add(newNode);
                            log.info("Reduced temporary role '" + roleName + "' for " + uuid + " to " + newExpiry);
                        } else {
                            log.info("Reduced temporary role '" + roleName + "' for " + uuid + " to expiration (removed)");
                        }
                    }
                }
            }
        }).thenApply(v -> true).exceptionally(ex -> {
            log.severe("Failed to reduce temporary role " + roleName + " for " + uuid + ": " + ex.getMessage());
            return false;
        });
    }
}
