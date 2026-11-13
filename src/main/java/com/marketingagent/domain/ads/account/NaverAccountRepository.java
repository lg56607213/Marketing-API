package com.marketingagent.domain.ads.account;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NaverAccountRepository extends JpaRepository<NaverAccount, Long> {

    Optional<NaverAccount> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
