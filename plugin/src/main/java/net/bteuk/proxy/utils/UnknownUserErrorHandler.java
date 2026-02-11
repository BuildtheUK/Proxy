package net.bteuk.proxy.utils;

import net.bteuk.proxy.Discord;
import net.dv8tion.jda.api.exceptions.ErrorHandler;

import java.util.Arrays;

import static net.dv8tion.jda.api.requests.ErrorResponse.UNKNOWN_MEMBER;
import static net.dv8tion.jda.api.requests.ErrorResponse.UNKNOWN_USER;

/**
 * Error handler for when the user is unknown, this implies they have left the discord and their link must therefore be removed.
 */
public class UnknownUserErrorHandler extends ErrorHandler {
    public UnknownUserErrorHandler(long userID) {
        super();
        handle(Arrays.asList(UNKNOWN_USER, UNKNOWN_MEMBER),
                e -> Discord.unlinkUser(userID));
    }
}
