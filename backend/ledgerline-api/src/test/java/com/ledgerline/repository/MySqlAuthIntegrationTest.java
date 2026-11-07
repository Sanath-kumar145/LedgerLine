package com.ledgerline.repository;

import com.ledgerline.entity.Account;
import com.ledgerline.entity.AccountStatus;
import com.ledgerline.entity.AccountType;
import com.ledgerline.entity.Role;
import com.ledgerline.entity.User;
import com.ledgerline.entity.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("MySQL Integration Tests — Repositories and Data Persistence")
class MySqlAuthIntegrationTest {

    @Autowired(required = false)
    private UserRepository userRepository;

    @Autowired(required = false)
    private RoleRepository roleRepository;

    @Autowired(required = false)
    private AccountRepository accountRepository;

    @Test
    @DisplayName("Verify Flyway migrations and User persistence in real MySQL")
    void testUserAndRolePersistenceInMySql() {
        assertNotNull(userRepository, "UserRepository must be initialized by Spring Data JPA");
        assertNotNull(roleRepository, "RoleRepository must be initialized by Spring Data JPA");

        // Verify seeded roles from V1 Flyway migration
        Optional<Role> adminRole = roleRepository.findByName("ROLE_ADMIN");
        assertTrue(adminRole.isPresent(), "ROLE_ADMIN must exist in seed data");

        Optional<Role> holderRole = roleRepository.findByName("ROLE_ACCOUNT_HOLDER");
        assertTrue(holderRole.isPresent(), "ROLE_ACCOUNT_HOLDER must exist in seed data");

        // Create and persist test user
        User user = new User("mysql_user", "mysql@example.com", "$2a$12$eX4mP1eH4sH...", "MySQL Test User");
        user.setRoles(Set.of(holderRole.get()));
        User savedUser = userRepository.save(user);

        assertNotNull(savedUser.getId());
        assertEquals(UserStatus.ACTIVE, savedUser.getStatus());

        // Create and persist test account
        Account account = new Account("LL-US-TEST-9999", savedUser.getId(), AccountType.CHECKING, "USD", new BigDecimal("1000.00"));
        Account savedAccount = accountRepository.save(account);

        assertNotNull(savedAccount.getId());
        assertEquals(AccountStatus.ACTIVE, savedAccount.getStatus());
        assertEquals(0, new BigDecimal("1000.00").compareTo(savedAccount.getBalance()));
    }
}
