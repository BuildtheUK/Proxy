package org.btuk.proxy.scheduler;

import org.btuk.proxy.Proxy;

import java.util.concurrent.TimeUnit;

import org.btuk.proxy.core.scheduler.ScheduledTask;
import org.btuk.proxy.core.scheduler.Scheduler;

public class ProxyScheduler implements Scheduler {

    private final Proxy proxy;

    public ProxyScheduler(Proxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public ScheduledTask createDelayedTask(Runnable runnable, long delay, TimeUnit unit) {
        return new ProxyScheduledTask(proxy.getServer().getScheduler().buildTask(proxy, runnable).delay(delay, unit).schedule());
    }

    @Override
    public ScheduledTask createRepeatingTask(Runnable runnable, long delay, long period, TimeUnit unit) {
        return new ProxyScheduledTask(proxy.getServer().getScheduler().buildTask(proxy, runnable).delay(delay, unit).repeat(period, unit).schedule());
    }
}
