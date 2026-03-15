package org.btuk.proxy.core.scheduler;

public interface ScheduledTask {

    void cancel();

    TaskStatus getStatus();

}
