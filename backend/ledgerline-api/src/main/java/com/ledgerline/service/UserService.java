package com.ledgerline.service;

import com.ledgerline.dto.AuthResponse;
import com.ledgerline.dto.LoginRequest;
import com.ledgerline.dto.RegisterRequest;
import com.ledgerline.dto.UserProfileResponse;
import com.ledgerline.dto.UserSummaryDto;
import com.ledgerline.security.UserPrincipal;
import java.util.List;

public interface UserService {
    UserProfileResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    UserProfileResponse getCurrentUserProfile(UserPrincipal currentUser);
    List<UserSummaryDto> getAllUsers();
}

