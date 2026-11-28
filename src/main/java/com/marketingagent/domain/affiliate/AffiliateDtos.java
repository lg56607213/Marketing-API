package com.marketingagent.domain.affiliate;

import com.marketingagent.domain.affiliate.AffiliateWriter.Draft;
import com.marketingagent.domain.affiliate.NaverShopClient.ShopItem;
import java.time.LocalDateTime;
import java.util.List;

public final class AffiliateDtos {

    private AffiliateDtos() {}

    /**
     * @param photoCaptions 사용자가 올린 사진의 설명(순서대로 M1, M2..). 사진 파일은 서버로 보내지 않는다
     */
    public record PostRequest(String productName, String linkUrl, String productNotes, String review,
                              List<String> photoCaptions, String keyword) {}

    public record PostView(Long id, String productName, String linkUrl, String keyword, String status, String error,
                           ShopItem product, Draft draft, List<String> photoCaptions, String disclosure,
                           LocalDateTime createdAt) {}

    public record Status(boolean shopConfigured, int usedToday, int maxPerDay) {}
}
