package com.carlink.user.repository;

import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select u from User u where u.email = :email and u.active = true")
    Optional<User> findActiveByEmail(@Param("email") String email);

    /**
     * Admin user search: optional freetext over email/names, optional active/role
     * filters. Newest first. {@code :q} is {@code cast(... as text)} — inside
     * {@code lower(...)} PostgreSQL can't infer its type and the JDBC driver
     * would otherwise bind it as {@code bytea} ("function lower(bytea) does not
     * exist").
     */
    @Query("""
            select u from User u
            where (:q is null
                     or lower(u.email) like lower(concat('%', cast(:q as text), '%'))
                     or lower(coalesce(u.firstName, '')) like lower(concat('%', cast(:q as text), '%'))
                     or lower(coalesce(u.lastName, '')) like lower(concat('%', cast(:q as text), '%')))
              and (:active is null or u.active = :active)
              and (:role is null or u.role = :role)
            order by u.createdAt desc""")
    List<User> search(@Param("q") String q,
                      @Param("active") Boolean active,
                      @Param("role") Role role);

    long countByRole(Role role);

    long countByActive(boolean active);
}