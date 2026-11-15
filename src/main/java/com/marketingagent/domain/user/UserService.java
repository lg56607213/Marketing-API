package com.marketingagent.domain.user;

import com.marketingagent.common.exception.DuplicateResourceException;
import com.marketingagent.common.exception.UnauthorizedException;
import com.marketingagent.common.security.CurrentUser;
import com.marketingagent.domain.company.Company;
import com.marketingagent.domain.company.CompanyRepository;
import com.marketingagent.domain.user.dto.AuthResponse;
import com.marketingagent.domain.user.dto.LoginRequest;
import com.marketingagent.domain.user.dto.RegisterRequest;
import com.marketingagent.security.JwtService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * 회원가입. 가입자마다 새 업체를 만들고 그 업체의 관리자로 등록한다.
     * 같은 업체에 사람을 더 넣으려면 관리자가 {@link #createMember} 로 추가한다.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        requireNewEmail(request.email());
        String companyName = request.companyName() != null && !request.companyName().isBlank()
                ? request.companyName().trim()
                : request.email();
        Company company = companyRepository.save(new Company(companyName));
        User user = userRepository.save(new User(request.email(),
                passwordEncoder.encode(request.password()), UserRole.ADMIN, company.getId()));
        return toResponse(user, company.getName());
    }

    /** 로그인한 관리자의 업체에 일반 사용자를 추가한다. */
    @Transactional
    public User createMember(RegisterRequest request) {
        requireNewEmail(request.email());
        return userRepository.save(new User(request.email(),
                passwordEncoder.encode(request.password()), UserRole.USER, CurrentUser.companyId()));
    }

    @Transactional(readOnly = true)
    public List<User> findMembers() {
        return userRepository.findByCompanyIdOrderByIdAsc(CurrentUser.companyId());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("Invalid credentials");
        }
        String companyName = user.getCompanyId() == null ? null
                : companyRepository.findById(user.getCompanyId()).map(Company::getName).orElse(null);
        return toResponse(user, companyName);
    }

    private void requireNewEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email already in use: " + email);
        }
    }

    private AuthResponse toResponse(User user, String companyName) {
        return new AuthResponse(jwtService.generate(user), user.getEmail(), user.getRole().name(),
                user.getCompanyId(), companyName);
    }
}
