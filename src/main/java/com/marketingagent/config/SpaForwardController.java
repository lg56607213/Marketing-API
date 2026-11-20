package com.marketingagent.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 화면(React)의 주소를 브라우저에서 바로 열거나 새로고침해도 index.html 이 나가게 한다.
 * 화면 안의 이동은 브라우저가 처리하므로 서버는 어떤 화면 주소든 같은 파일을 주면 된다.
 * 점(.)이 들어간 주소는 js·css 같은 실제 파일이므로 여기서 받지 않고 정적 파일 처리로 넘긴다.
 */
@Controller
public class SpaForwardController {

    /** 첫 마디: 서버가 직접 쓰는 주소는 뺀다. */
    private static final String FIRST = "/{a:^(?!api$|h2-console$|swagger-ui$|v3$|assets$)[^.]*$}";
    /** 그 뒤 마디: 점이 없는 것만. */
    private static final String NEXT = ":[^.]*}";

    @GetMapping({
            FIRST,
            FIRST + "/{b" + NEXT,
            FIRST + "/{b" + NEXT + "/{c" + NEXT,
            FIRST + "/{b" + NEXT + "/{c" + NEXT + "/{d" + NEXT,
            FIRST + "/{b" + NEXT + "/{c" + NEXT + "/{d" + NEXT + "/{e" + NEXT
    })
    public String forward() {
        return "forward:/index.html";
    }
}
