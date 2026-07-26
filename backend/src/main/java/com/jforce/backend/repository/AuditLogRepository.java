package com.jforce.backend.repository;

import com.jforce.backend.model.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog,String> {

}
