package io.buildlens.core;

/** Thrown when the wrapped build tool cannot be launched at all. */
public class BuildLaunchException extends Exception {
    public BuildLaunchException(String message, Throwable cause) {
        super(message, cause);
    }
}
