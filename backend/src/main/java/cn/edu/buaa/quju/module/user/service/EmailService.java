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

    public void sendActivation(String to, String token) {
        send(to, "【趣聚】激活你的账号",
                "欢迎注册趣聚！请点击以下链接激活账号（24 小时内有效）：\n"
                + webBaseUrl + "/activate?token=" + token);
    }

    public void sendPasswordReset(String to, String token) {
        send(to, "【趣聚】重置密码",
                "你申请了重置密码，请点击以下链接（1 小时内有效）：\n"
                + webBaseUrl + "/reset-password?token=" + token
                + "\n如非本人操作请忽略此邮件。");
    }

    private void send(String to, String subject, String text) {
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(from);
            m.setTo(to);
            m.setSubject(subject);
            m.setText(text);
            sender.send(m);
            log.info("邮件已发送 subject='{}' -> {}", subject, to);
        } catch (Exception e) {
            log.warn("邮件发送失败 -> {}: {}", to, e.getMessage());
        }
    }
}
