package de.kreiscraft.core.module;

public final class ModuleException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ModuleException(String message) {
        super(message);
    }

    public ModuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
