package com.marketingagent.domain.user;

import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.common.exception.ResourceNotFoundException;
import com.marketingagent.domain.company.Company;
import com.marketingagent.domain.company.CompanyRepository;
import com.marketingagent.domain.user.dto.SignupSummary;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 최고 관리자가 모든 업체의 가입 신청을 보고 승인·거절한다. */
@Service
@RequiredArgsConstructor
public class SignupApprovalService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;

    /** 최고 관리자를 뺀 모든 계정. 최근 가입한 순서. */
    @Transactional(readOnly = true)
    public List<SignupSummary> list() {
        List<User> users = userRepository.findBySuperAdminFalseOrderByIdDesc();
        List<Long> companyIds = users.stream().map(User::getCompanyId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> companyNames = companyRepository.findAllById(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, Company::getName, (a, b) -> a));
        return users.stream()
                .map(user -> SignupSummary.from(user, companyNames.get(user.getCompanyId())))
                .toList();
    }

    @Transactional
    public SignupSummary approve(Long userId) {
        return change(userId, User::approve);
    }

    /** 가입 거절. 이미 이용 중인 계정이면 이용 중지가 된다. */
    @Transactional
    public SignupSummary reject(Long userId) {
        return change(userId, User::reject);
    }

    private SignupSummary change(Long userId, java.util.function.Consumer<User> action) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("계정을 찾을 수 없습니다: " + userId));
        if (user.isSuperAdmin()) {
            throw new BadRequestException("최고 관리자 계정은 바꿀 수 없습니다.");
        }
        action.accept(user);
        String companyName = user.getCompanyId() == null ? null
                : companyRepository.findById(user.getCompanyId()).map(Company::getName).orElse(null);
        return SignupSummary.from(user, companyName);
    }
}
