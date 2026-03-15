package org.btuk.proxy.core.user;

import net.bteuk.network.lib.dto.OnlineUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Contains the core user management features with minimal implementation/dependencies.
 */
public class CoreUserManager {

    private final List<User> users = new ArrayList<>();

    private final Set<OnlineUser> onlineUsers = new HashSet<>();

    public void addUser(User user) {
        users.add(user);
    }

    public void removeUser(User user) {
        users.remove(user);
    }

    /**
     * Unmutes the specified user for all other users.
     * @param user the user to unmute
     */
    public void unmuteUser(User user) {
        users.forEach(u -> u.unmute(user));
    }

    public void runForEach(Consumer<User> consumer) {
        users.forEach(consumer);
    }

    public void runForEachOnline(Consumer<User> consumer) {
        users.stream().filter(User::isOnline).forEach(consumer);
    }

    /**
     * Get a user by uuid.
     *
     * @param uuid the uuid of the user to get
     * @return the {@link User} or null if not exists
     */
    public User getUserByUuid(String uuid) {
        return users.stream().filter(user -> user.getUuid().equals(uuid)).findFirst().orElse(null);
    }

    /**
     * Get a user by name.
     *
     * @param name the uuid of the user to get
     * @return the {@link User} or null if not exists
     */
    public User getUserByName(String name) {
        return users.stream().filter(user -> user.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    /**
     * Check if a user has another user muted.
     *
     * @param userUuid      uuid of user
     * @param otherUserUuid uuid of user to check
     * @return boolean if the user has otherUser muted
     */
    public boolean isMutedForUser(String userUuid, String otherUserUuid) {
        User user = getUserByUuid(userUuid);
        if (user != null) {
            User otherUser = getUserByUuid(otherUserUuid);
            if (otherUser != null) {
                return user.isMuted(otherUser);
            }
        }
        return false;
    }

    public long countOnlineUsers() {
        return users.stream().filter(User::isOnline).count();
    }

    public int countUsers() {
        return users.size();
    }

    public User getFirst() {
        return users.getFirst();
    }

    public List<User> getUsersOnServer(String serverName) {
        return users.stream().filter(user -> user.getServer().equals(serverName)).toList();
    }

    public void addOnlineUser(OnlineUser user) {
        onlineUsers.add(user);
    }

    public void removeOnlineUser(OnlineUser user) {
        onlineUsers.remove(user);
    }

    public Set<OnlineUser> getOnlineUsers() {
        return Collections.unmodifiableSet(onlineUsers);
    }
}
