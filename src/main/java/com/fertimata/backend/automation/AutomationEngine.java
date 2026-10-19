package com.fertimata.backend.automation;

/**
 * Seam for the future rule engine.
 *
 * <p>Ingestion already calls this for every stored reading, so phase 2 can implement rule evaluation
 * without touching the telemetry pipeline. The only implementation today is
 * {@link DisabledAutomationEngine}, which does nothing.
 */
public interface AutomationEngine {

    /** Called once per persisted reading, inside the ingestion transaction. */
    void evaluate(AutomationSignal signal);
}
