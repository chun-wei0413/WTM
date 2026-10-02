package com.usethatmeme.application.port.out;

import com.usethatmeme.domain.user.Role;
import com.usethatmeme.domain.user.User;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findByUsername(String username);

    boolean existsByRole(Role role);

    void save(User user);
}
