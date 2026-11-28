package com.marketingagent.domain.affiliate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.common.exception.ResourceNotFoundException;
import com.marketingagent.common.security.CurrentUser;
import com.marketingagent.domain.affiliate.AffiliateDtos.PostRequest;
import com.marketingagent.domain.affiliate.AffiliateDtos.PostView;
import com.marketingagent.domain.affiliate.AffiliateDtos.Status;
import com.marketingagent.domain.affiliate.AffiliateWriter.Draft;
import com.marketingagent.domain.affiliate.NaverShopClient.ShopItem;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 상품 1개 → 네이버 쇼핑 검색으로 실제 정보 확인 → AI 글 초안. AI 를 부르는 동안에는 DB 트랜잭션을 잡지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AffiliateService {

    static final int MAX_PHOTOS = 10;

    private final AffiliatePostRepository repository;
    private final NaverShopClient shopClient;
    private final AffiliateWriter writer;
    private final AffiliateProperties properties;
    private final ObjectMapper objectMapper;

    public Status status() {
        return new Status(shopClient.configured(), (int) usedToday(CurrentUser.companyId()), properties.maxPerDay());
    }

    public List<PostView> list() {
        return repository.findTop100ByCompanyIdOrderByIdDesc(CurrentUser.companyId()).stream().map(this::view).toList();
    }

    public PostView get(Long id) {
        return view(require(id));
    }

    public PostView create(PostRequest req) {
        Long companyId = CurrentUser.companyId();
        if (usedToday(companyId) >= properties.maxPerDay()) {
            throw new BadRequestException("오늘 만들 수 있는 글 수(" + properties.maxPerDay() + "개)를 다 썼습니다. 내일 다시 해 주세요.");
        }
        AffiliatePost post = new AffiliatePost(companyId, CurrentUser.id());
        apply(post, req);
        return generate(repository.save(post));
    }

    /** 저장한 입력 그대로 다시 쓴다. 상품 정보도 다시 확인한다. */
    public PostView rewrite(Long id) {
        AffiliatePost post = require(id);
        post.retry();
        return generate(repository.save(post));
    }

    public void delete(Long id) {
        repository.delete(require(id));
    }

    private PostView generate(AffiliatePost post) {
        List<String> captions = captions(post);
        try {
            ShopItem item = shopClient.find(post.getProductName(), post.getLinkUrl());
            post.product(item == null ? null : objectMapper.writeValueAsString(item));
            Draft d = writer.write(post.getProductName(), item, post.getProductNotes(), post.getReview(), captions,
                    post.getKeyword());
            post.written(d.title(), objectMapper.writeValueAsString(d));
        } catch (Exception e) {
            log.warn("상품 글 생성 실패: {} {}", post.getProductName(), e.getMessage());
            post.failed("글을 만들지 못했습니다: " + e.getMessage());
        }
        return view(repository.save(post));
    }

    private void apply(AffiliatePost post, PostRequest req) {
        String name = trim(req.productName());
        if (name == null) {
            throw new BadRequestException("상품명을 넣어 주세요.");
        }
        String link = trim(req.linkUrl());
        if (link == null || !validLink(link)) {
            throw new BadRequestException("브랜드커넥트에서 받은 판매 링크(https://...)를 넣어 주세요.");
        }
        List<String> caps = req.photoCaptions() == null ? List.of() : req.photoCaptions();
        if (caps.size() > MAX_PHOTOS) {
            throw new BadRequestException("사진은 상품당 " + MAX_PHOTOS + "장까지 넣을 수 있습니다.");
        }
        List<String> clean = new ArrayList<>();
        caps.forEach(c -> clean.add(c == null ? "" : c.trim()));
        try {
            post.input(cut(name, 200), link, trim(req.productNotes()), trim(req.review()),
                    objectMapper.writeValueAsString(clean), cut(trim(req.keyword()), 100));
        } catch (Exception e) {
            throw new BadRequestException("사진 설명을 읽지 못했습니다.");
        }
    }

    static boolean validLink(String link) {
        if (link.length() > 1000) {
            return false;
        }
        try {
            URI u = URI.create(link);
            return ("https".equals(u.getScheme()) || "http".equals(u.getScheme())) && u.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private long usedToday(Long companyId) {
        return repository.countByCompanyIdAndCreatedAtAfter(companyId, LocalDate.now().atStartOfDay());
    }

    private AffiliatePost require(Long id) {
        return repository.findByIdAndCompanyId(id, CurrentUser.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("글을 찾을 수 없습니다."));
    }

    private List<String> captions(AffiliatePost p) {
        try {
            return p.getPhotoCaptions() == null ? List.of()
                    : objectMapper.readValue(p.getPhotoCaptions(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private PostView view(AffiliatePost p) {
        ShopItem item = null;
        Draft draft = null;
        try {
            if (p.getProductJson() != null) {
                item = objectMapper.readValue(p.getProductJson(), ShopItem.class);
            }
            if (p.getPostJson() != null) {
                draft = objectMapper.readValue(p.getPostJson(), Draft.class);
            }
        } catch (Exception e) {
            log.warn("상품 글 읽기 실패 id={} {}", p.getId(), e.getMessage());
        }
        return new PostView(p.getId(), p.getProductName(), p.getLinkUrl(), p.getKeyword(), p.getStatus(), p.getError(),
                item, draft, captions(p), AffiliateWriter.DISCLOSURE, p.getCreatedAt());
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String cut(String s, int max) {
        return s == null ? null : s.substring(0, Math.min(max, s.length()));
    }
}
