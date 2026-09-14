package io.taskmesh.server.repo;

import io.taskmesh.server.model.JobEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<JobEntity, String> {

    List<JobEntity> findByStatusOrderByCreatedAtAsc(String status);
    List<JobEntity> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    List<JobEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    List<JobEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(String status);

    Optional<JobEntity> findFirstByStatusOrderByCreatedAtAsc(String status);

    List<JobEntity> findByWorkerIdAndStatus(String workerId, String status);

    List<JobEntity> findByWorkerIdAndStatusIn(String workerId, Collection<String> statuses);

    List<JobEntity> findByStatusInAndDeadlineBefore(Collection<String> statuses, Instant deadline);

    /** Conditional state transition: only one dispatcher wins the race for a job. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE JobEntity j SET j.status = :next, j.workerId = :workerId "
            + "WHERE j.id = :id AND j.status = :expected")
    int casAssign(@Param("id") String id,
                  @Param("expected") String expected,
                  @Param("next") String next,
                  @Param("workerId") String workerId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE JobEntity j SET j.status = :next WHERE j.id = :id AND j.status = :expected")
    int casStatus(@Param("id") String id,
                  @Param("expected") String expected,
                  @Param("next") String next);
}
