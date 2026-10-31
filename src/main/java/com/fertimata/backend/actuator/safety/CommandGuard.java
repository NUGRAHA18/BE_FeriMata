package com.fertimata.backend.actuator.safety;

/** One interlock before record/publish: throw {@code InvalidCommandException} for a malformed request,
 * {@link SafetyInterlockException} when plant state forbids it. Every guard bean is picked up automatically. */
public interface CommandGuard {

    void check(CommandContext context);
}
