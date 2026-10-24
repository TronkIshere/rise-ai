package com.riseai.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByStatus(UserStatus status);

    @Query("select u from User u where (:status is null or u.status = :status) and (:q is null or lower(u.email) like lower(concat('%', :q, '%')) or lower(u.fullName) like lower(concat('%', :q, '%')))")
    Page<User> search(String q, UserStatus status, Pageable pageable);
}
