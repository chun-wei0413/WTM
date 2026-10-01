package com.memehub.application.port.out;

import com.memehub.domain.user.Role;
import com.memehub.domain.user.User;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findByUsername(String username);

    boolean existsByRole(Role role);

    void save(User user);
}
