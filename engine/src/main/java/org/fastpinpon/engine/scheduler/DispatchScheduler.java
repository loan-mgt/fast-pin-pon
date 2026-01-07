package org.fastpinpon.engine.scheduler;

import org.fastpinpon.engine.domain.service.DispatchService;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Scheduler for periodic dispatch operations.
 * Runs at a configurable interval to redispatch freed units.
 */
public final class DispatchScheduler {

    private static final Logger LOG = Logger.getLogger(DispatchScheduler.class.getName());

    private final ScheduledExecutorService executor;
    private final DispatchService dispatchService;
    private final int intervalSeconds;
    private volatile boolean running = false;

    public DispatchScheduler(DispatchService dispatchService, int intervalSeconds) {
        this.dispatchService = Objects.requireNonNull(dispatchService, "dispatchService must not be null");
        
        if (intervalSeconds < 1) {
            throw new IllegalArgumentException("intervalSeconds must be at least 1");
        }
        this.intervalSeconds = intervalSeconds;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dispatch-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Start the scheduler.
     */
    public void start() {
        if (running) {
            LOG.warning("Scheduler already running");
            return;
        }

        LOG.info(() -> "Starting dispatch scheduler with interval: " + intervalSeconds + "s");
        
        executor.scheduleAtFixedRate(
                this::runDispatchCycle,
                intervalSeconds, // Initial delay
                intervalSeconds, // Period
                TimeUnit.SECONDS
        );
        
        running = true;
    }

    /**
     * Stop the scheduler.
     */
    public void stop() {
        if (!running) {
            return;
        }

        LOG.info("Stopping dispatch scheduler");
        executor.shutdown();
        
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        running = false;
    }

    /**
     * Check if the scheduler is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Run a single dispatch cycle.
     */
    private void runDispatchCycle() {
        try {
            LOG.fine("Running dispatch cycle");
            int dispatched = dispatchService.periodicDispatch();
            if (dispatched > 0) {
                LOG.info(() -> "Dispatch cycle completed: " + dispatched + " units dispatched");
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error in dispatch cycle", e);
        }
    }
}
