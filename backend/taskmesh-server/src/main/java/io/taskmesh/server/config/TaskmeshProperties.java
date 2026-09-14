package io.taskmesh.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taskmesh")
public class TaskmeshProperties {
    private int queueThreshold = 500;
    private String strategy = "resource";
    private long dispatchIntervalMs = 250;
    private Heartbeat heartbeat = new Heartbeat();
    private Kafka kafka = new Kafka();
    private Redis redis = new Redis();
    private Scheduling scheduling = new Scheduling();

    public int getQueueThreshold() { return queueThreshold; }
    public void setQueueThreshold(int queueThreshold) { this.queueThreshold = queueThreshold; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public long getDispatchIntervalMs() { return dispatchIntervalMs; }
    public void setDispatchIntervalMs(long dispatchIntervalMs) { this.dispatchIntervalMs = dispatchIntervalMs; }
    public Heartbeat getHeartbeat() { return heartbeat; }
    public void setHeartbeat(Heartbeat heartbeat) { this.heartbeat = heartbeat; }
    public Kafka getKafka() { return kafka; }
    public void setKafka(Kafka kafka) { this.kafka = kafka; }
    public Redis getRedis() { return redis; }
    public void setRedis(Redis redis) { this.redis = redis; }
    public Scheduling getScheduling() { return scheduling; }
    public void setScheduling(Scheduling scheduling) { this.scheduling = scheduling; }

    public static class Heartbeat {
        private long beatSeconds = 2;
        private int suspectMissed = 3;
        private int failMissed = 5;
        private long checkIntervalMs = 2000;

        public long getBeatSeconds() { return beatSeconds; }
        public void setBeatSeconds(long beatSeconds) { this.beatSeconds = beatSeconds; }
        public int getSuspectMissed() { return suspectMissed; }
        public void setSuspectMissed(int suspectMissed) { this.suspectMissed = suspectMissed; }
        public int getFailMissed() { return failMissed; }
        public void setFailMissed(int failMissed) { this.failMissed = failMissed; }
        public long getCheckIntervalMs() { return checkIntervalMs; }
        public void setCheckIntervalMs(long checkIntervalMs) { this.checkIntervalMs = checkIntervalMs; }
    }

    public static class Kafka {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class Redis {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class Scheduling {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
