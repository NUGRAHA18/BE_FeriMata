package com.fertimata.backend.actuator.safety;

/**
 * One interlock checked before a command is recorded and published.
 *
 * <p>Implementations throw to refuse: {@link com.fertimata.backend.common.exception.InvalidCommandException}
 * when the request itself is malformed for this actuator, {@link SafetyInterlockException} when the
 * request is fine but the plant is not in a state where it may run. Adding an interlock is adding a
 * component; {@link CommandSafetyPolicy} picks up every guard in the context.
 */
public interface CommandGuard {

    void check(CommandContext context);
}
