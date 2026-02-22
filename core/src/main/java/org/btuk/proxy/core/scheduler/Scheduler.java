package org.btuk.proxy.core.scheduler;

import java.util.concurrent.TimeUnit;

public interface Scheduler {

    ScheduledTask createDelayedTask(Runnable runnable, long delay, TimeUnit unit);

    ScheduledTask createRepeatingTask(Runnable runnable, long delay, long period, TimeUnit unit);

}
