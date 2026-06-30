package com.marketingagent.domain.prompt;

import com.marketingagent.common.exception.ResourceNotFoundException;
import com.marketingagent.domain.brand.BrandService;
import com.marketingagent.domain.prompt.dto.PromptRequest;
import com.marketingagent.domain.prompt.dto.PromptResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PromptService {

    private final PromptRepository promptRepository;
    private final BrandService brandService;

    @Transactional
    public PromptResponse create(PromptRequest request) {
        PromptTemplate pt = new PromptTemplate(
                request.name(),
                brandService.getOrThrow(request.brandId()),
                request.contentType(),
                request.systemPrompt(),
                request.userPromptTemplate()
        );
        return PromptResponse.from(promptRepository.save(pt));
    }

    @Transactional(readOnly = true)
    public List<PromptResponse> findAll(Long brandId) {
        if (brandId != null) {
            return promptRepository.findByBrandId(brandId).stream().map(PromptResponse::from).toList();
        }
        return promptRepository.findAll().stream().map(PromptResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PromptResponse findById(Long id) {
        return PromptResponse.from(getOrThrow(id));
    }

    @Transactional
    public PromptResponse update(Long id, PromptRequest request) {
        PromptTemplate pt = getOrThrow(id);
        pt.update(request.name(), request.contentType(), request.systemPrompt(), request.userPromptTemplate());
        return PromptResponse.from(pt);
    }

    @Transactional
    public void delete(Long id) {
        promptRepository.delete(getOrThrow(id));
    }

    private PromptTemplate getOrThrow(Long id) {
        return promptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PromptTemplate not found: " + id));
    }
}
