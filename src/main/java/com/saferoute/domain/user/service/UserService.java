package com.saferoute.domain.user.service;

import com.saferoute.domain.user.dto.LoginRequest;
import com.saferoute.domain.user.dto.LoginResponse;
import com.saferoute.domain.user.dto.SignupRequest;
import com.saferoute.domain.user.dto.SignupResponse;
import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.entity.UserRole;
import com.saferoute.domain.user.repository.UserRepository;
import com.saferoute.global.api.error.UserErrorCode;
import com.saferoute.global.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        validateDuplicateEmail(request.email());
        validateDuplicateUsername(request.username());

        UserRole role = request.role() != null ? request.role() : UserRole.NORMAL;

        User user = User.create(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.email(),
                role,
                request.schoolName()
        );

        return SignupResponse.from(userRepository.save(user));
    }

    // 로그인: JWT 없이 자격 증명만 확인하고 사용자 정보를 반환
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ApiException(UserErrorCode.INVALID_CREDENTIAL));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ApiException(UserErrorCode.INVALID_CREDENTIAL);
        }

        return LoginResponse.from(user);
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