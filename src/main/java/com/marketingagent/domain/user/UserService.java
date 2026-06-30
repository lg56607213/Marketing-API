package com.marketingagent.domain.user;

import com.marketingagent.common.exception.DuplicateResourceException;
import com.marketingagent.common.exception.UnauthorizedException;
import com.marketingagent.domain.user.dto.AuthResponse;
import com.marketingagent.domain.user.dto.LoginRequest;
import com.marketingagent.domain.user.dto.RegisterRequest;
import com.marketingagent.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already in use: " + request.email());
        }
        User user = new User(request.email(), passwordEncoder.encode(request.password()), UserRole.USER);
        userRepository.save(user);
        return new AuthResponse(jwtService.generate(user), user.getEmail(), user.getRole().name());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("Invalid credentials");
        }
        return new AuthResponse(jwtService.generate(user), user.getEmail(), user.getRole().name());
    }
}
