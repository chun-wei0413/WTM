package com.memehub.adapter.out.persistence;

import com.memehub.application.port.out.UserRepository;
import com.memehub.domain.user.Role;
import com.memehub.domain.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcUserRepository implements UserRepository {

    private final JdbcClient jdbc;

    JdbcUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jdbc.sql("SELECT id, username, password_hash, role FROM app_user WHERE lower(username) = lower(?)")
                .param(username)
                .query((rs, n) -> new User(rs.getObject("id", UUID.class), rs.getString("username"),
                        rs.getString("password_hash"), Role.valueOf(rs.getString("role"))))
                .optional();
    }

    @Override
    public boolean existsByRole(Role role) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM app_user WHERE role = ?)")
                .param(role.name())
                .query(Boolean.class)
                .single();
    }

    @Override
    public void save(User user) {
        jdbc.sql("INSERT INTO app_user (id, username, password_hash, role) VALUES (?, ?, ?, ?)")
                .params(List.of(user.id(), user.username(), user.passwordHash(), user.role().name()))
                .update();
    }
}
