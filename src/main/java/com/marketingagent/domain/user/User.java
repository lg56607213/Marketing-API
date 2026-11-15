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

    public User(String email, String password, UserRole role, Long companyId) {
        this.email = email;
        this.password = password;
        this.role = role;
        this.companyId = companyId;
    }

    public void assignCompany(Long companyId) {
        this.companyId = companyId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }
}
