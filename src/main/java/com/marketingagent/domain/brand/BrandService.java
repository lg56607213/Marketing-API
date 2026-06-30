package com.marketingagent.domain.brand;

import com.marketingagent.common.exception.DuplicateResourceException;
import com.marketingagent.common.exception.ResourceNotFoundException;
import com.marketingagent.common.security.CurrentUser;
import com.marketingagent.domain.brand.dto.BrandRequest;
import com.marketingagent.domain.brand.dto.BrandResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional
    public BrandResponse create(BrandRequest request) {
        Long userId = CurrentUser.id();
        if (brandRepository.existsByNameAndOwnerId(request.name(), userId)) {
            throw new DuplicateResourceException("Brand name already exists: " + request.name());
        }
        Brand brand = new Brand(
                request.name(), request.description(), request.toneAndManner(),
                request.cta(), request.forbiddenWords(), request.allowedWords(),
                request.seoRules(), userId
        );
        return BrandResponse.from(brandRepository.save(brand));
    }

    @Transactional(readOnly = true)
    public List<BrandResponse> findAll() {
        return brandRepository.findAll().stream().map(BrandResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BrandResponse findById(Long id) {
        return BrandResponse.from(getOrThrow(id));
    }

    @Transactional
    public BrandResponse update(Long id, BrandRequest request) {
        Brand brand = getOrThrow(id);
        brand.update(request.name(), request.description(), request.toneAndManner(),
                request.cta(), request.forbiddenWords(), request.allowedWords(), request.seoRules());
        return BrandResponse.from(brand);
    }

    @Transactional
    public void delete(Long id) {
        brandRepository.delete(getOrThrow(id));
    }

    public Brand getOrThrow(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found: " + id));
    }
}
