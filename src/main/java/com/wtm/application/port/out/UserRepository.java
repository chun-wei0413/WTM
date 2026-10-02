package com.wtm.application.port.out;

import com.wtm.domain.user.Role;
import com.wtm.domain.user.User;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findByUsername(String username);

    boolean existsByRole(Role role);

    void save(User user);
}
