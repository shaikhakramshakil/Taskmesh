package io.taskmesh.server.repo;

import io.taskmesh.server.model.WorkerEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkerRepository extends JpaRepository<WorkerEntity, String> {

    List<WorkerEntity> findByHealth(String health);

    long countByHealth(String health);
    /**
     * Capacity CAS: decrement only when enough headroom is still committed in
     * the row. Concurrent dispatcher passes and racing heartbeats share this
     * row, so the predicate — not the caller's in-memory view — is what
     * prevents selling the same CPU twice.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE WorkerEntity w SET w.availableCpu = w.availableCpu - :cpu, "
            + "w.availableMemoryMb = w.availableMemoryMb - :mem, w.runningJobs = w.runningJobs + 1 "
            + "WHERE w.id = :id AND w.availableCpu >= :cpu AND w.availableMemoryMb >= :mem AND w.health = 'HEALTHY'")
    int casReserve(@Param("id") String id, @Param("cpu") int cpu, @Param("mem") long mem);

    /**
     * Heartbeat CAS: advance the missed-beat counter only if it still holds the value
     * the checker observed, so concurrent checks cannot double-count a beat.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE WorkerEntity w SET w.missedBeats = :next, w.health = :health "
            + "WHERE w.id = :id AND w.missedBeats = :expected")
    int casMissedBeats(@Param("id") String id,
                       @Param("expected") int expected,
                       @Param("next") int next,
                       @Param("health") String health);
}
