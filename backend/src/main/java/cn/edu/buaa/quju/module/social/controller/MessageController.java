package cn.edu.buaa.quju.module.social.controller;

import cn.edu.buaa.quju.common.PageResult;
import cn.edu.buaa.quju.common.R;
import cn.edu.buaa.quju.common.UserContext;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.ForwardMessageReq;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.MarkReadReq;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.MessageVO;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.SendMessageReq;
import cn.edu.buaa.quju.module.social.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** IM 消息：历史拉取 / 发送 / 已读 / 撤回 / 转发（需登录）。 */
@RestController
@RequestMapping("/v1/messages")
public class MessageController {
    private final MessageService messageService;
    public MessageController(MessageService messageService) { this.messageService = messageService; }

    @GetMapping
    public R<PageResult<MessageVO>> getMessages(
            @RequestParam String scope,
            @RequestParam Long peerId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(messageService.getMessages(UserContext.require(), scope, peerId, page, size));
    }

    @PostMapping
    public R<MessageVO> send(@RequestBody @Valid SendMessageReq req) {
        return R.ok(messageService.send(UserContext.require(), req));
    }

    @PostMapping("/read")
    public R<Void> markRead(@RequestBody @Valid MarkReadReq req) {
        messageService.markRead(UserContext.require(), req);
        return R.<Void>ok(null);
    }

    @PostMapping("/{id}/recall")
    public R<Void> recall(@PathVariable Long id) {
        messageService.recall(UserContext.require(), id);
        return R.<Void>ok(null);
    }

    @PostMapping("/{id}/forward")
    public R<MessageVO> forward(@PathVariable Long id, @RequestBody @Valid ForwardMessageReq req) {
        return R.ok(messageService.forward(UserContext.require(), id, req));
    }
}
