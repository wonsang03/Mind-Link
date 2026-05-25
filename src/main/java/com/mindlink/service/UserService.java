package com.mindlink.service;

import com.mindlink.domain.User;
import com.mindlink.domain.UserRole;
import com.mindlink.dto.UserUpdateRequest;
import com.mindlink.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User signup(String name, String email, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }
        User user = new User(name, email, passwordEncoder.encode(rawPassword));
        user.setRole(UserRole.USER);
        user.setNotificationEnabled(false);
        return userRepository.save(user);
    }

    public Optional<User> login(String email, String rawPassword) {
        return userRepository.findByEmail(email)
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPassword()));
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Transactional
    public void changeRole(Long userId, UserRole newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        user.setRole(newRole);
    }

    @Transactional
    public User updateProfile(Long userId, UserUpdateRequest req, String newProfileImageUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        if (!user.getEmail().equals(req.getEmail()) && userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
        user.setName(req.getName());
        user.setEmail(req.getEmail());
        user.setNickname(req.getNickname());
        user.setRegion(req.getRegion());
        user.setNotificationEnabled(req.getNotificationEnabled());
        user.setPhone(req.getPhone());
        if (newProfileImageUrl != null) {
            user.setProfileImageUrl(newProfileImageUrl);
        }
        return user;
    }
}
