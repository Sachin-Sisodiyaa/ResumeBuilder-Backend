package com.resumeai.auth.repository;

import com.resumeai.auth.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link User}.
 * All methods are auto-implemented by the JPA layer.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRole(String role);

    List<User> findBySubscriptionPlan(String plan);

    List<User> findByActiveTrue();
}
