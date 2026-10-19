package com.fertimata.backend.automation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The phase-1 automation engine: it does nothing, deliberately.
 *
 * <p>No agricultural thresholds are hard-coded anywhere in this codebase. Automatic control of a
 * pump or a valve is a safety decision; it waits until the hardware, sensor and threshold
 * specifications are final.
 */
@Component
public class DisabledAutomationEngine implements AutomationEngine {

    private static final Logger log = LoggerFactory.getLogger(DisabledAutomationEngine.class);

    @Override
    public void evaluate(AutomationSignal signal) {
        log.trace("Automation is not enabled in this phase; ignoring signal {}", signal);
    }
}
