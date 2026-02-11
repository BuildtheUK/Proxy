package net.bteuk.proxy.exceptions;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * General exception that contains a {@link Component} message.
 */
@Getter
public class ErrorMessage extends Exception {

    private final Component error;

    public ErrorMessage(Component error) {
        super(PlainTextComponentSerializer.plainText().serialize(error));
        this.error = error;
    }
}