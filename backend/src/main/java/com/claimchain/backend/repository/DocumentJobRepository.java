package com.claimchain.backend.repository;

import com.claimchain.backend.model.DocumentJob;
import com.claimchain.backend.model.JobStatus;
import com.claimchain.backend.model.JobType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DocumentJobRepository extends JpaRepository<DocumentJob, Long> {
    List<DocumentJob> findByDocumentIdOrderByCreatedAtDesc(Long documentId);
    List<DocumentJob> findByStatusAndJobType(JobStatus status, JobType jobType);
    List<DocumentJob> findByStatusAndJobTypeOrderByCreatedAtAsc(JobStatus status, JobType jobType, Pageable pageable);

    @Query(
            value = """
                    SELECT dj.*
                    FROM document_jobs dj
                    WHERE dj.status = 'QUEUED'
                      AND dj.job_type = :#{#jobType.name()}
                      AND (dj.next_run_at IS NULL OR dj.next_run_at <= NOW())
                    ORDER BY dj.created_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<DocumentJob> claimQueuedJobsForType(@Param("jobType") JobType jobType, @Param("limit") int limit);

    @Query(
            value = """
                    SELECT dj.*
                    FROM document_jobs dj
                    JOIN claim_documents cd ON cd.id = dj.document_id
                    WHERE cd.claim_id = :claimId
                      AND dj.status = 'QUEUED'
                      AND dj.job_type = :#{#jobType.name()}
                      AND (dj.next_run_at IS NULL OR dj.next_run_at <= NOW())
                    ORDER BY dj.created_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<DocumentJob> claimQueuedJobsForClaimAndType(
            @Param("claimId") Long claimId,
            @Param("jobType") JobType jobType,
            @Param("limit") int limit
    );

    boolean existsByDocumentClaimIdAndJobTypeAndStatusIn(Long claimId, JobType jobType, Collection<JobStatus> statuses);
}
