package com.wtm.account.usecase.port.out;

import com.wtm.account.entity.Role;
import com.wtm.account.entity.User;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findByUsername(String username);

    boolean existsByRole(Role role);

    void save(User user);
}
