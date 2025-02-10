package com.jubiman.jubichat.network;

public class RestartableThread {
    private Thread thread;
    private final Runnable runnable;
    private final String name;
    
    public RestartableThread(Runnable runnable, String name) {
        this.runnable = runnable;
        this.name = name;
    }
    
    public void start() {
        thread = new Thread(runnable, name);
        thread.start();
    }
    
    public void stop() {
        if (thread != null && thread.isAlive())
            thread.interrupt();
    }
    
    public void restart() {
        stop();
        start();
    }
}
