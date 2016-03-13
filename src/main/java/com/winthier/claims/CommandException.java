package com.winthier.claims;

public class CommandException extends RuntimeException {
    public CommandException(String message) {
        super(message);
    }

    public CommandException(CommandException ce) {
        super(ce.getMessage());
    }
}
