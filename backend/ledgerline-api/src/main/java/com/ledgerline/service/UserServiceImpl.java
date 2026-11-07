package com.ledgerline.service;

import com.ledgerline.dto.AuthResponse;
import com.ledgerline.dto.LoginRequest;
import com.ledgerline.dto.RegisterRequest;
import com.ledgerline.dto.UserProfileResponse;
import com.ledgerline.dto.UserSummaryDto;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.AccountStatus;
import com.ledgerline.entity.AccountType;
import com.ledgerline.entity.Role;
import com.ledgerline.entity.User;
import com.ledgerline.entity.UserStatus;
import com.ledgerline.exception.DuplicateResourceException;
import com.ledgerline.exception.ResourceNotFoundException;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.RoleRepository;
import com.ledgerline.repository.UserRepository;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.security.jwt.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final String DEFAULT_ROLE = "ROLE_ACCOUNT_HOLDER";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public UserServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           AccountRepository accountRepository,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager,
                           JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
    }

    @Override
    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        String cleanUsername = request.getUsername().trim().toLowerCase();
        String cleanEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByUsername(cleanUsername)) {
            throw new DuplicateResourceException("Username '" + request.getUsername() + "' is already taken.");
        }

        if (userRepository.existsByEmail(cleanEmail)) {
            throw new DuplicateResourceException("Email address is already registered.");
        }

        // Secure password hashing before persistence
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User user = new User(cleanUsername, cleanEmail, encodedPassword, request.getFullName().trim());
        user.setStatus(UserStatus.ACTIVE);

        // Security rule: Public registration ONLY assigns ROLE_ACCOUNT_HOLDER
        Role accountHolderRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseGet(() -> roleRepository.save(new Role(DEFAULT_ROLE, "Standard financial account holder")));
        user.addRole(accountHolderRole);

        User savedUser = userRepository.save(user);

        // Provision initial checking account for the new account holder
        String accountNumber = generateAccountNumber();
        Account initialAccount = new Account(
                accountNumber,
                savedUser.getId(),
                AccountType.CHECKING,
                "USD",
                BigDecimal.ZERO.setScale(2)
        );
        initialAccount.setStatus(AccountStatus.ACTIVE);
        accountRepository.save(initialAccount);

        log.info("Registered new user id={} username={} with initial account={}",
                savedUser.getId(), savedUser.getUsername(), accountNumber);

        return mapToProfileResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String cleanUsername = request.getUsername().trim().toLowerCase();

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(cleanUsername, request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String jwt = tokenProvider.generateToken(authentication);

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findWithRolesById(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserSummaryDto userSummary = mapToSummaryDto(user);

        return new AuthResponse(jwt, "Bearer", tokenProvider.getExpirationMs(), userSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new ResourceNotFoundException("Current user not authenticated");
        }

        User user = userRepository.findWithRolesById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + currentUser.getId()));

        return mapToProfileResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSummaryDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToSummaryDto)
                .collect(Collectors.toList());
    }

    private UserSummaryDto mapToSummaryDto(User user) {
        List<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());

        return new UserSummaryDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                roleNames
        );
    }

    private UserProfileResponse mapToProfileResponse(User user) {
        List<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());

        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                roleNames,
                user.getCreatedAt()
        );
    }

    private String generateAccountNumber() {
        int randomPart = 100000 + RANDOM.nextInt(900000);
        return "LL-US-8921-" + randomPart;
    }
}

