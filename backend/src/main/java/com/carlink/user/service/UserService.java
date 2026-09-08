package com.carlink.user.service;

import com.carlink.common.exception.NotFoundException;
import com.carlink.user.dto.UpdateProfileRequest;
import com.carlink.user.model.User;
import com.carlink.user.model.UserResponse;
import com.carlink.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Owner profile management. Returns {@link UserResponse} which never
 * contains the private phone number.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserResponse me(UUID userId) {
        return UserResponse.from(getUser(userId));
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = getUser(userId);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        if (request.phone() != null) {
            // Only the owner's own profile can set the private phone.
            user.setPhone(request.phone().isEmpty() ? null : request.phone());
        }
        return UserResponse.from(userRepository.save(user));
    }

    public User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}