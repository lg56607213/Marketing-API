package com.marketingagent.domain.content;

import com.marketingagent.ai.AiProvider;
import com.marketingagent.ai.AiRequest;
import com.marketingagent.ai.AiResponse;
import com.marketingagent.common.exception.ResourceNotFoundException;
import com.marketingagent.common.security.CurrentUser;
import com.marketingagent.domain.approval.Approval;
import com.marketingagent.domain.approval.ApprovalAction;
import com.marketingagent.domain.approval.ApprovalRepository;
import com.marketingagent.domain.brand.Brand;
import com.marketingagent.domain.brand.BrandService;
import com.marketingagent.domain.content.dto.ApproveRequest;
import com.marketingagent.domain.content.dto.ContentResponse;
import com.marketingagent.domain.content.dto.GenerateRequest;
import com.marketingagent.domain.content.dto.RewriteRequest;
import com.marketingagent.domain.prompt.PromptRepository;
import com.marketingagent.domain.prompt.PromptTemplate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContentService {

    private final ContentRepository contentRepository;
    private final ApprovalRepository approvalRepository;
    private final BrandService brandService;
    private final PromptRepository promptRepository;
    private final AiProvider aiProvider;

    @Transactional
    public ContentResponse generate(GenerateRequest request) {
        Brand brand = brandService.getOrThrow(request.brandId());
        Long userId = CurrentUser.id();

        String systemPrompt = buildSystemPrompt(brand, request);
        String userPrompt = buildUserPrompt(brand, request);

        AiResponse aiResponse = aiProvider.generate(new AiRequest(systemPrompt, userPrompt, request.aiModel()));

        Content content = new Content(
                brand,
                request.contentType(),
                request.title(),
                request.topic(),
                aiResponse.content(),
                request.aiModel(),
                userId
        );
        return ContentResponse.from(contentRepository.save(content));
    }

    @Transactional
    public ContentResponse rewrite(Long id, RewriteRequest request) {
        Content original = getOrThrow(id);
        Brand brand = original.getBrand();

        String systemPrompt = """
                당신은 %s 브랜드의 마케팅 전문가입니다.
                톤앤매너: %s
                다음 지침에 따라 기존 콘텐츠를 개선해주세요.
                """.formatted(brand.getName(), nullToEmpty(brand.getToneAndManner()));

        String userPrompt = """
                개선 지침: %s

                기존 콘텐츠:
                %s
                """.formatted(request.instructions(), original.getBody());

        AiResponse aiResponse = aiProvider.generate(new AiRequest(systemPrompt, userPrompt, request.aiModel()));

        Content rewritten = new Content(
                brand,
                original.getContentType(),
                original.getTitle(),
                original.getTopic(),
                aiResponse.content(),
                request.aiModel(),
                CurrentUser.id()
        );
        return ContentResponse.from(contentRepository.save(rewritten));
    }

    @Transactional
    public ContentResponse approve(Long id, ApproveRequest request) {
        Content content = getOrThrow(id);
        content.approve();
        approvalRepository.save(new Approval(content, CurrentUser.id(), ApprovalAction.APPROVE, request.comment()));
        return ContentResponse.from(content);
    }

    @Transactional
    public ContentResponse reject(Long id, ApproveRequest request) {
        Content content = getOrThrow(id);
        content.reject();
        approvalRepository.save(new Approval(content, CurrentUser.id(), ApprovalAction.REJECT, request.comment()));
        return ContentResponse.from(content);
    }

    @Transactional
    public ContentResponse publish(Long id) {
        Content content = getOrThrow(id);
        content.publish();
        approvalRepository.save(new Approval(content, CurrentUser.id(), ApprovalAction.PUBLISH, null));
        return ContentResponse.from(content);
    }

    @Transactional(readOnly = true)
    public List<ContentResponse> findAll(Long brandId) {
        if (brandId != null) {
            return contentRepository.findByBrandId(brandId).stream().map(ContentResponse::from).toList();
        }
        return contentRepository.findAll().stream().map(ContentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ContentResponse findById(Long id) {
        return ContentResponse.from(getOrThrow(id));
    }

    private Content getOrThrow(Long id) {
        return contentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Content not found: " + id));
    }

    private String buildSystemPrompt(Brand brand, GenerateRequest request) {
        return promptRepository.findFirstByBrandIdAndContentType(brand.getId(), request.contentType())
                .map(PromptTemplate::getSystemPrompt)
                .orElseGet(() -> """
                        당신은 %s 브랜드의 마케팅 콘텐츠 전문가입니다.
                        콘텐츠 유형: %s
                        톤앤매너: %s
                        금지 표현: %s
                        SEO 규칙: %s
                        위 가이드라인을 철저히 준수하여 고품질 마케팅 콘텐츠를 작성하세요.
                        """.formatted(
                        brand.getName(),
                        request.contentType(),
                        nullToEmpty(brand.getToneAndManner()),
                        nullToEmpty(brand.getForbiddenWords()),
                        nullToEmpty(brand.getSeoRules())
                ));
    }

    private String buildUserPrompt(Brand brand, GenerateRequest request) {
        return promptRepository.findFirstByBrandIdAndContentType(brand.getId(), request.contentType())
                .map(pt -> pt.getUserPromptTemplate()
                        .replace("{topic}", request.topic())
                        .replace("{brand}", brand.getName())
                        .replace("{contentType}", request.contentType().name()))
                .orElseGet(() -> """
                        주제: %s
                        브랜드: %s
                        콘텐츠 유형: %s 에 맞는 마케팅 콘텐츠를 작성해주세요.
                        CTA: %s
                        """.formatted(
                        request.topic(),
                        brand.getName(),
                        request.contentType(),
                        nullToEmpty(brand.getCta())
                ));
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
