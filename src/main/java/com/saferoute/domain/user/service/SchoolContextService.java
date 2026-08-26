package com.saferoute.domain.user.service;

import com.saferoute.domain.user.repository.UserRepository;
import com.saferoute.global.api.error.UserErrorCode;
import com.saferoute.global.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolContextService {

    private final UserRepository userRepository;

    public String getSchoolName(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(UserErrorCode.USER_NOT_FOUND))
                .getSchoolName();
    }
}
