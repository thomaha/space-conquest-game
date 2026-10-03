package com.spaceconquest.control.command;

/** Completion of a staged command, independent of whether its state change remains visible later. */
public enum CommandOutcome {
    EXECUTED,
    REJECTED,
    CANCELLED
}
