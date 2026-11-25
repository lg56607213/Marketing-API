package com.marketingagent.domain.ads.report;

import com.marketingagent.common.exception.BadRequestException;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * 보고 메일 발송. 메일 서버(MAIL_HOST)가 설정되지 않은 서버에서는 보내지 않는다.
 * 하이웍스 같은 일반 SMTP 를 쓰며, 보내는 사람은 MAIL_FROM(없으면 로그인 아이디)이다.
 */
@Slf4j
@Component
public class ReportMailer {

    private final ObjectProvider<JavaMailSender> senderProvider;

    @Value("${spring.mail.host:}")
    private String host;

    @Value("${spring.mail.username:}")
    private String username;

    @Value("${app.mail.from:}")
    private String from;

    @Value("${app.mail.from-name:마케팅 에이전트}")
    private String fromName;

    public ReportMailer(ObjectProvider<JavaMailSender> senderProvider) {
        this.senderProvider = senderProvider;
    }

    public boolean configured() {
        return host != null && !host.isBlank() && senderProvider.getIfAvailable() != null && !sender().isBlank();
    }

    /** 보내는 주소. 화면에 보여 준다. */
    public String sender() {
        return from != null && !from.isBlank() ? from : username == null ? "" : username;
    }

    public void send(List<String> to, String subject, String html) {
        JavaMailSender mail = senderProvider.getIfAvailable();
        if (mail == null || !configured()) {
            throw new BadRequestException("메일 서버가 설정되지 않아 보낼 수 없습니다. 서버의 MAIL_HOST·MAIL_USERNAME·MAIL_PASSWORD 를 확인해 주세요.");
        }
        try {
            MimeMessage message = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(sender(), fromName);
            helper.setTo(to.toArray(String[]::new));
            helper.setSubject(subject);
            helper.setText(html, true);
            mail.send(message);
            log.info("보고 메일 발송: {}명 '{}'", to.size(), subject);
        } catch (Exception e) {
            log.error("보고 메일 발송 실패: {}", e.getMessage());
            throw new BadRequestException("메일을 보내지 못했습니다: " + rootMessage(e));
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : e.getClass().getSimpleName();
    }
}
