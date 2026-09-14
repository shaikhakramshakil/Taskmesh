package io.taskmesh.server.service;

import io.taskmesh.domain.scheduling.SchedulingStrategy;
import io.taskmesh.server.config.TaskmeshProperties;
import io.taskmesh.server.web.ApiExceptions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class StrategyRegistry {
    private final Map<String, SchedulingStrategy> strategies = new LinkedHashMap<>();
    private final Map<String, String> descriptions = new LinkedHashMap<>();
    private volatile String active;

    public StrategyRegistry(List<SchedulingStrategy> beans, TaskmeshProperties props) {
        for (SchedulingStrategy strategy : beans) {
            strategies.put(strategy.name(), strategy);
        }
        descriptions.put("fifo", "Strict submission order; first fitting worker takes the job.");
        descriptions.put("priority", "Highest priority first, submission order breaks ties.");
        descriptions.put("fair", "Deficit round-robin across priority classes; best-fit placement.");
        descriptions.put("resource", "Priority plus deadline pressure plus queue age; best-fit placement.");
        descriptions.put("deadline", "Earliest deadline first; least-utilized fitting worker.");
        String configured = props.getStrategy() == null ? "resource" : props.getStrategy().toLowerCase();
        if (!strategies.containsKey(configured)) {
            throw new IllegalStateException("Unknown taskmesh.strategy: '" + configured + "'");
        }
        this.active = configured;
    }

    public SchedulingStrategy active() {
        return strategies.get(active);
    }

    public String activeName() {
        return active;
    }

    public Map<String, String> all() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : strategies.keySet()) {
            out.put(name, descriptions.getOrDefault(name, ""));
        }
        return out;
    }

    public boolean known(String name) {
        return name != null && strategies.containsKey(name.toLowerCase());
    }

    public String switchTo(String name) {
        String key = name == null ? "" : name.toLowerCase();
        if (!strategies.containsKey(key)) {
            throw new ApiExceptions.BadRequestException("Unknown strategy: '" + name + "'");
        }
        active = key;
        return active;
    }
}
