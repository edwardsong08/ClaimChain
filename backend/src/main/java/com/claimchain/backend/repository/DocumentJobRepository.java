package com.claimchain.backend.repository;

import com.claimchain.backend.model.DocumentJob;
import com.claimchain.backend.model.JobStatus;
import com.claimchain.backend.model.JobType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentJobRepository extends JpaRepository<DocumentJob, Long> {
    List<DocumentJob> findByDocumentIdOrderByCreatedAtDesc(Long documentId);
    List<DocumentJob> findByStatusAndJobType(JobStatus status, JobType jobType);
    List<DocumentJob> findByStatusAndJobTypeOrderByCreatedAtAsc(JobStatus status, JobType jobType, Pageable pageable);
}
