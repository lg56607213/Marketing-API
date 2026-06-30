package com.marketingagent.domain.template;

import com.marketingagent.common.ContentType;
import com.marketingagent.common.exception.ResourceNotFoundException;
import com.marketingagent.domain.brand.BrandService;
import com.marketingagent.domain.template.dto.TemplateRequest;
import com.marketingagent.domain.template.dto.TemplateResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final BrandService brandService;

    @Transactional
    public TemplateResponse create(TemplateRequest request) {
        Template template = new Template(
                request.name(),
                brandService.getOrThrow(request.brandId()),
                request.contentType(),
                request.body()
        );
        return TemplateResponse.from(templateRepository.save(template));
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> findAll(Long brandId, ContentType contentType) {
        if (brandId != null && contentType != null) {
            return templateRepository.findByBrandIdAndContentType(brandId, contentType)
                    .stream().map(TemplateResponse::from).toList();
        }
        if (brandId != null) {
            return templateRepository.findByBrandId(brandId).stream().map(TemplateResponse::from).toList();
        }
        return templateRepository.findAll().stream().map(TemplateResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse findById(Long id) {
        return TemplateResponse.from(getOrThrow(id));
    }

    @Transactional
    public TemplateResponse update(Long id, TemplateRequest request) {
        Template template = getOrThrow(id);
        template.update(request.name(), request.contentType(), request.body());
        return TemplateResponse.from(template);
    }

    @Transactional
    public void delete(Long id) {
        templateRepository.delete(getOrThrow(id));
    }

    private Template getOrThrow(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + id));
    }
}
