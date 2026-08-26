package com.saferoute.domain.user.service;

import com.saferoute.domain.user.dto.LoginRequest;
import com.saferoute.domain.user.dto.LoginResponse;
import com.saferoute.domain.user.dto.SignupRequest;
import com.saferoute.domain.user.dto.SignupResponse;
import com.saferoute.domain.user.dto.UpdateUserProfileRequest;
import com.saferoute.domain.user.dto.UserProfileResponse;
import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.entity.UserRole;
import com.saferoute.domain.user.repository.UserRepository;
import com.saferoute.global.api.error.UserErrorCode;
import com.saferoute.global.api.code.ErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        validateDuplicateEmail(request.email());
        validateDuplicateUsername(request.username());

        UserRole role = request.role() != null ? request.role() : UserRole.NORMAL;

        User user = User.create(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.email(),
                request.phoneNumber(),
                role,
                request.schoolName()
        );

        return SignupResponse.from(userRepository.save(user));
    }

    // 로그인
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() ->
                        new ApiException(UserErrorCode.INVALID_CREDENTIAL));

        if (!passwordEncoder.matches(
                request.password(),
                user.getPassword()
        )) {
            throw new ApiException(UserErrorCode.INVALID_CREDENTIAL);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user);

        return LoginResponse.of(
                user,
                accessToken,
                jwtTokenProvider.getAccessTokenExpirationSeconds()
        );
    }

    public UserProfileResponse getProfile(String email) {
        return UserProfileResponse.from(findUserByEmail(email));
    }

    @Transactional
    public UserProfileResponse updateProfile(
            String email,
            UpdateUserProfileRequest request
    ) {
        User user = findUserByEmail(email);

        if (request.schoolName() != null
                && !request.schoolName().equals(user.getSchoolName())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }

        if (request.username() != null
                && userRepository.existsByUsernameAndIdNot(request.username(), user.getId())) {
            throw new ApiException(UserErrorCode.DUPLICATE_USERNAME);
        }
        if (request.email() != null
                && userRepository.existsByEmailAndIdNot(request.email(), user.getId())) {
            throw new ApiException(UserErrorCode.DUPLICATE_EMAIL);
        }

        user.updateProfile(
                request.username(),
                request.phoneNumber(),
                request.email()
        );

        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw mapProfileConflict(request, exception);
        }

        return UserProfileResponse.from(user);
    }

    private ApiException mapProfileConflict(
            UpdateUserProfileRequest request,
            DataIntegrityViolationException exception
    ) {
        String detail = exception.getMostSpecificCause().getMessage();
        String normalizedDetail = detail == null ? "" : detail.toLowerCase();

        if (request.email() != null && request.username() == null) {
            return new ApiException(UserErrorCode.DUPLICATE_EMAIL);
        }
        if (request.username() != null && request.email() == null) {
            return new ApiException(UserErrorCode.DUPLICATE_USERNAME);
        }
        if (normalizedDetail.contains("users(email")
                || normalizedDetail.contains("users_email")
                || normalizedDetail.contains("key (email)")) {
            return new ApiException(UserErrorCode.DUPLICATE_EMAIL);
        }
        if (normalizedDetail.contains("users(username")
                || normalizedDetail.contains("users_username")
                || normalizedDetail.contains("key (username)")) {
            return new ApiException(UserErrorCode.DUPLICATE_USERNAME);
        }

        throw exception;
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(UserErrorCode.USER_NOT_FOUND));
    }

    private void validateDuplicateEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(UserErrorCode.DUPLICATE_EMAIL);
        }
    }

    private void validateDuplicateUsername(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new ApiException(UserErrorCode.DUPLICATE_USERNAME);
        }
    }
}
