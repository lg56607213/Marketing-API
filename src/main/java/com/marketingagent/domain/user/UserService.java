package com.marketingagent.domain.user;

import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.common.exception.DuplicateResourceException;
import com.marketingagent.common.exception.ForbiddenException;
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
import org.springframework.beans.factory.annotation.Value;
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

    /** 공개 운영 중 아무나 업체를 만들지 못하게 막을 수 있다. */
    @Value("${app.signup-enabled:true}")
    private boolean signupEnabled;

    /** true 면 가입한 계정은 최고 관리자가 승인해야 로그인할 수 있다. */
    @Value("${app.signup-approval-required:false}")
    private boolean signupApprovalRequired;

    /**
     * 회원가입. 가입자마다 새 업체를 만들고 그 업체의 관리자로 등록한다.
     * 같은 업체에 사람을 더 넣으려면 관리자가 {@link #createMember} 로 추가한다.
     * 승인이 필요한 설정이면 토큰 없이 승인 대기(PENDING) 상태로 돌려준다.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!signupEnabled) {
            throw new BadRequestException("지금은 새 회원가입을 받지 않습니다. 관리자에게 문의하세요.");
        }
        requireNewEmail(request.email());
        String companyName = request.companyName() != null && !request.companyName().isBlank()
                ? request.companyName().trim()
                : request.email();
        Company company = companyRepository.save(new Company(companyName));
        User user = new User(request.email(), passwordEncoder.encode(request.password()),
                UserRole.ADMIN, company.getId());
        if (signupApprovalRequired) {
            user.markPending();
        }
        user = userRepository.save(user);
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
        // 비밀번호가 맞은 뒤에만 상태를 알려 준다. 남의 아이디로 가입 여부를 떠보지 못하게.
        if (user.getStatus() == UserStatus.PENDING) {
            throw new ForbiddenException("가입 승인을 기다리고 있습니다. 관리자가 승인하면 로그인할 수 있습니다.");
        }
        if (user.getStatus() == UserStatus.REJECTED) {
            throw new ForbiddenException("이용이 승인되지 않은 계정입니다. 관리자에게 문의하세요.");
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
        String token = user.isActive() ? jwtService.generate(user) : null;
        return new AuthResponse(token, user.getEmail(), user.getRole().name(),
                user.getCompanyId(), companyName, user.getStatus().name(), user.isSuperAdmin());
    }
}
