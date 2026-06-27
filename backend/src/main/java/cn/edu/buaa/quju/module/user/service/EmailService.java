package cn.edu.buaa.quju.module.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender sender;
    private final String from;
    private final String webBaseUrl;

    public EmailService(JavaMailSender sender,
                        @Value("${quju.mail.from}") String from,
                        @Value("${quju.web-base-url:http://1.92.124.5}") String webBaseUrl) {
        this.sender = sender;
        this.from = from;
        this.webBaseUrl = webBaseUrl;
    }

    /** 发送激活邮件（尽力而为，失败不阻断注册）。 */
    public void sendActivation(String to, String token) {
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(from);
            m.setTo(to);
            m.setSubject("【趣聚】激活你的账号");
            m.setText("欢迎注册趣聚！请点击以下链接激活账号（24 小时内有效）：\n"
                    + webBaseUrl + "/activate?token=" + token);
            sender.send(m);
            log.info("激活邮件已发送 -> {}", to);
        } catch (Exception e) {
            log.warn("激活邮件发送失败 -> {}: {}", to, e.getMessage());
        }
    }
}
