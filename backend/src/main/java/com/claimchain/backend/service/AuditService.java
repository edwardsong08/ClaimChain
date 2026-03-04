package com.claimchain.backend.service;

import com.claimchain.backend.config.RequestIdFilter;
import com.claimchain.backend.model.AuditEvent;
import com.claimchain.backend.model.User;
import com.claimchain.backend.repository.AuditEventRepository;
import com.claimchain.backend.repository.UserRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final int MAX_REQUEST_ID_LENGTH = 64;
    private static final int MAX_ACTOR_ROLE_LENGTH = 50;
    private static final int MAX_ACTION_LENGTH = 100;
    private static final int MAX_ENTITY_TYPE_LENGTH = 50;
    private static final int MAX_METADATA_LENGTH = 10000;

    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;

    public AuditService(AuditEventRepository auditEventRepository, UserRepository userRepository) {
        this.auditEventRepository = auditEventRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AuditEvent record(
            Long actorUserId,
            String actorRole,
            String action,
            String entityType,
            Long entityId,
            String metadataJson
    ) {
        AuditEvent event = new AuditEvent();
        event.setRequestId(trimToNull(MDC.get(RequestIdFilter.MDC_KEY), MAX_REQUEST_ID_LENGTH));
        event.setActorRole(trimToNull(actorRole, MAX_ACTOR_ROLE_LENGTH));
        event.setAction(requireAndTrim(action, "action", MAX_ACTION_LENGTH));
        event.setEntityType(requireAndTrim(entityType, "entityType", MAX_ENTITY_TYPE_LENGTH));
        event.setEntityId(entityId);
        event.setMetadataJson(trimToNull(metadataJson, MAX_METADATA_LENGTH));

        if (actorUserId != null) {
            User actor = userRepository.findById(actorUserId).orElse(null);
            event.setActorUser(actor);
        }

        return auditEventRepository.save(event);
    }

    private String requireAndTrim(String value, String fieldName, int maxLength) {
        String normalized = trimToNull(value, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private String trimToNull(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (trimmed.length() > maxLength) {
            return trimmed.substring(0, maxLength);
        }
        return trimmed;
    }
}
