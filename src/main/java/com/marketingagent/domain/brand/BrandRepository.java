package com.marketingagent.domain.brand;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<Brand, Long> {
    List<Brand> findByCompanyIdOrderByIdAsc(Long companyId);
    boolean existsByNameAndCompanyId(String name, Long companyId);
    List<Brand> findByCompanyIdIsNull();
}
