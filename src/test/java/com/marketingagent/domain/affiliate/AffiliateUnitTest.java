package com.marketingagent.domain.affiliate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.domain.affiliate.AffiliateWriter.Draft;
import com.marketingagent.domain.affiliate.NaverShopClient.ShopItem;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AffiliateUnitTest {

    private final ObjectMapper om = new ObjectMapper();

    /** 네이버 쇼핑 검색 API 응답 모양(개발자센터 문서 기준). */
    private static final String SHOP = """
            {"total":2,"items":[
             {"title":"<b>스탠리</b> 퀜처 텀블러 887ml","link":"https://smartstore.naver.com/main/products/1111111111",
              "image":"https://shopping-phinf.pstatic.net/main_1/1.jpg","lprice":"39800","hprice":"","mallName":"A몰",
              "productId":"1111111111","productType":"2","brand":"스탠리","maker":"스탠리","category1":"생활/건강",
              "category2":"주방용품","category3":"보온보냉용품","category4":"텀블러"},
             {"title":"보온병 &amp; 텀블러 세트","link":"https://search.shopping.naver.com/catalog/2222222222",
              "image":"https://shopping-phinf.pstatic.net/main_2/2.jpg","lprice":"12000","mallName":"네이버",
              "productId":"2222222222","brand":"","maker":"","category1":"생활/건강","category2":"","category3":"","category4":""}
            ]}""";

    @Test
    void 쇼핑_검색결과에서_태그를_지우고_분류를_잇는다() throws Exception {
        List<ShopItem> items = NaverShopClient.parse(om, SHOP);
        assertThat(items.get(0).title()).isEqualTo("스탠리 퀜처 텀블러 887ml");
        assertThat(items.get(0).lowPrice()).isEqualTo(39800L);
        assertThat(items.get(0).category()).isEqualTo("생활/건강 > 주방용품 > 보온보냉용품 > 텀블러");
        assertThat(items.get(1).title()).isEqualTo("보온병 & 텀블러 세트");
        assertThat(items.get(1).brand()).isNull();
    }

    @Test
    void 링크의_상품번호가_같은_것을_먼저_고르고_이름이_안맞으면_쓰지_않는다() throws Exception {
        List<ShopItem> items = NaverShopClient.parse(om, SHOP);
        assertThat(NaverShopClient.pick(items, "아무거나", "https://smartstore.naver.com/x/products/2222222222").productId())
                .isEqualTo("2222222222");
        assertThat(NaverShopClient.pick(items, "스탠리 퀜처 텀블러", "https://naver.me/abc").productId())
                .isEqualTo("1111111111");
        assertThat(NaverShopClient.pick(items, "다이슨 에어랩 드라이기", "https://naver.me/abc")).isNull();
    }

    @Test
    void 없는_사진번호와_중복은_지우고_안쓴_사진은_빈_섹션에_채운다() throws Exception {
        String raw = "설명 {\"title\":\"스탠리 텀블러 고르는 법\",\"tags\":[\"#텀블러\",\"텀블러\",\"스탠리\"],"
                + "\"sections\":[{\"heading\":\"A\",\"body\":\"가\",\"photo\":\"P1\"},"
                + "{\"heading\":\"B\",\"body\":\"나\",\"photo\":\"P1\"},"
                + "{\"heading\":\"C\",\"body\":\"다\",\"photo\":\"M9\"},"
                + "{\"heading\":\"D\",\"body\":\"\",\"photo\":null}],\"summary\":\"요약\"}";
        Draft d = AffiliateWriter.parse(om, raw, List.of("P1", "M1"));
        assertThat(d.tags()).containsExactly("텀블러", "스탠리");
        assertThat(d.sections()).hasSize(3);
        assertThat(d.sections()).extracting(AffiliateWriter.Section::photo).containsExactly("P1", "M1", null);
    }

    @Test
    void 제목이나_본문이_없으면_실패로_본다() {
        assertThatThrownBy(() -> AffiliateWriter.parse(om, "{\"title\":\"\",\"sections\":[]}", List.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AffiliateWriter.parse(om, "죄송합니다", List.of())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 후기가_없으면_체험담_금지를_프롬프트에_분명히_적는다() {
        String p = AffiliateWriter.userPrompt("텀블러", null, null, null, List.of("박스 사진"), null, LocalDate.of(2026, 11, 28));
        assertThat(p).contains("확인하지 못함").contains("체험담 금지").contains("M1: 내가 찍은 사진 — 박스 사진")
                .doesNotContain("P1:");
    }

    @Test
    void 사진_내려받기는_네이버_사진서버만_허용한다() {
        assertThat(AffiliateController.allowedImage("https://shopping-phinf.pstatic.net/main_1/1.jpg")).isTrue();
        assertThat(AffiliateController.allowedImage("http://shopping-phinf.pstatic.net/1.jpg")).isFalse();
        assertThat(AffiliateController.allowedImage("https://pstatic.net.evil.com/1.jpg")).isFalse();
        assertThat(AffiliateController.allowedImage("https://169.254.169.254/latest")).isFalse();
        assertThat(AffiliateController.allowedImage("https://x.pstatic.net:8443/1.jpg")).isFalse();
    }

    @Test
    void 판매링크는_http_주소만_받는다() {
        assertThat(AffiliateService.validLink("https://naver.me/5abcDEF")).isTrue();
        assertThat(AffiliateService.validLink("javascript:alert(1)")).isFalse();
        assertThat(AffiliateService.validLink("그냥 글자")).isFalse();
    }
}
