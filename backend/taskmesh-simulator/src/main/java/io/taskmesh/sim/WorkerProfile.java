package io.taskmesh.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Capacity template expanded into {@code count} identical simulated workers.
 * Spec syntax: {@code "CPU:MEMORY_MB[xCOUNT],..."}, e.g. {@code "8:16384x2,4:8192x4"}.
 */
public record WorkerProfile(int cpu, long memoryMb, int count) {

    public static List<WorkerProfile> parse(String spec) {
        List<WorkerProfile> profiles = new ArrayList<>();
        for (String part : spec.split(",")) {
            String[] countSplit = part.strip().split("x");
            int count = countSplit.length > 1 ? Integer.parseInt(countSplit[1].strip()) : 1;
            String[] resources = countSplit[0].split(":");
            if (resources.length != 2) {
                throw new IllegalArgumentException("Bad worker profile: '" + part
                        + "'. Expected CPU:MEMORY_MB[xCOUNT].");
            }
            profiles.add(new WorkerProfile(
                    Integer.parseInt(resources[0].strip()),
                    Long.parseLong(resources[1].strip()),
                    count));
        }
        if (profiles.isEmpty()) {
            throw new IllegalArgumentException("No worker profiles in spec: '" + spec + "'");
        }
        return profiles;
    }

    public static List<WorkerProfile> defaults() {
        return parse("8:16384x2,4:8192x4,2:4096x2");
    }
}