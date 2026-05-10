package com.resumeai.auth.service;

import com.resumeai.auth.dto.AuthDtos.AuthResponse;
import com.resumeai.auth.dto.AuthDtos.ForgotPasswordRequest;
import com.resumeai.auth.dto.AuthDtos.LoginRequest;
import com.resumeai.auth.dto.AuthDtos.MessageResponse;
import com.resumeai.auth.dto.AuthDtos.PasswordChangeRequest;
import com.resumeai.auth.dto.AuthDtos.ProfileUpdateRequest;
import com.resumeai.auth.dto.AuthDtos.RegisterRequest;
import com.resumeai.auth.dto.AuthDtos.ResetPasswordRequest;
import com.resumeai.auth.dto.AuthDtos.RoleUpdateRequest;
import com.resumeai.auth.dto.AuthDtos.SubscriptionUpdateRequest;
import com.resumeai.auth.dto.AuthDtos.TokenRefreshRequest;
import com.resumeai.auth.model.User;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse createSession(User user);
    MessageResponse logout(TokenRefreshRequest request);
    AuthResponse refresh(TokenRefreshRequest request);
    MessageResponse requestPasswordReset(ForgotPasswordRequest request);
    MessageResponse resetPassword(ResetPasswordRequest request);
    User getUserById(Long userId);
    List<User> getAllUsers();
    User updateProfile(Long userId, ProfileUpdateRequest request);
    User uploadProfilePicture(Long userId, MultipartFile file);
    Resource loadProfilePicture(String fileName);
    User changePassword(Long userId, PasswordChangeRequest request);
    User updateSubscription(Long userId, SubscriptionUpdateRequest request);
    User updateRole(Long userId, RoleUpdateRequest request);
    User deactivateAccount(Long userId);
    User reactivateAccount(Long userId);
    void deleteAccount(Long userId);
}
