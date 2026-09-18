package com.rmsolutions.centinela.persistence.repository;

import com.rmsolutions.centinela.persistence.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * @author Roger Rojas
 * @since 2026-09-18
 */
public interface AccountRepository extends JpaRepository<Account, UUID> {
}
