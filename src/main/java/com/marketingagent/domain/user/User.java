package com.marketingagent.domain.user;

import com.marketingagent.common.BaseEntity;
import com.marketingagent.common.security.AuthenticatedUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User extends BaseEntity implements AuthenticatedUser, UserDetails {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    /** ADMIN 은 자기 업체의 관리자다. 다른 업체에는 권한이 없다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    @Column(name = "company_id")
    private Long companyId;

    /** ACTIVE 만 로그인하고 API 를 쓸 수 있다. 기본값은 DB 마이그레이션(V12)과 같다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ColumnDefault("'ACTIVE'")
    private UserStatus status = UserStatus.ACTIVE;

    /** 모든 업체의 가입 신청을 승인·거절할 수 있는 운영자 */
    @Column(name = "super_admin", nullable = false)
    @ColumnDefault("false")
    private boolean superAdmin = false;

    public User(String email, String password, UserRole role, Long companyId) {
        this.email = email;
        this.password = password;
        this.role = role;
        this.companyId = companyId;
    }

    /** 승인을 받아야 쓸 수 있는 계정으로 만든다(회원가입 직후). */
    public void markPending() {
        this.status = UserStatus.PENDING;
    }

    public void approve() {
        this.status = UserStatus.ACTIVE;
    }

    /** 가입 거절 또는 이용 중지 */
    public void reject() {
        this.status = UserStatus.REJECTED;
    }

    public void grantSuperAdmin() {
        this.superAdmin = true;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public void assignCompany(Long companyId) {
        this.companyId = companyId;
    }

    /** @param encodedPassword 이미 암호화한 비밀번호 */
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        SimpleGrantedAuthority roleAuthority = new SimpleGrantedAuthority("ROLE_" + role.name());
        return superAdmin
                ? List.of(roleAuthority, new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
                : List.of(roleAuthority);
    }

    @Override
    public String getUsername() {
        return email;
    }

    /** 승인 대기·거절 계정은 토큰이 있어도 인증하지 않는다. */
    @Override
    public boolean isEnabled() {
        return isActive();
    }
}
