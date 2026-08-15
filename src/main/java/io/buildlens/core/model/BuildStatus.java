package io.buildlens.core.model;

/** Outcome of the wrapped build itself (not of BuildLens). */
public enum BuildStatus {
    SUCCESS,
    FAILURE,
    ABORTED,
    UNKNOWN
}
