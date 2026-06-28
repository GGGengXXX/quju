package cn.edu.buaa.quju.module.social.controller;

import cn.edu.buaa.quju.common.PageResult;
import cn.edu.buaa.quju.common.R;
import cn.edu.buaa.quju.common.UserContext;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.BlockVO;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.FollowVO;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.FriendRequestVO;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.FriendVO;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.SendFriendRequestReq;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.UpdateFriendReq;
import cn.edu.buaa.quju.module.social.service.FriendService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 好友申请 / 好友管理 / 关注 / 黑名单（需登录）。 */
@RestController
public class FriendController {
    private final FriendService friendService;
    public FriendController(FriendService friendService) { this.friendService = friendService; }

    // ---- 好友申请 ----
    @GetMapping("/v1/friend-requests")
    public R<PageResult<FriendRequestVO>> getRequests(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(friendService.getReceivedRequests(UserContext.require(), page, size));
    }

    @PostMapping("/v1/friend-requests")
    public R<Void> sendRequest(@RequestBody @Valid SendFriendRequestReq req) {
        friendService.sendRequest(UserContext.require(), req);
        return R.<Void>ok(null);
    }

    @PostMapping("/v1/friend-requests/{id}/accept")
    public R<Void> accept(@PathVariable Long id) {
        friendService.acceptRequest(UserContext.require(), id);
        return R.<Void>ok(null);
    }

    @PostMapping("/v1/friend-requests/{id}/reject")
    public R<Void> reject(@PathVariable Long id) {
        friendService.rejectRequest(UserContext.require(), id);
        return R.<Void>ok(null);
    }

    // ---- 好友 ----
    @GetMapping("/v1/friends")
    public R<List<FriendVO>> getFriends() {
        return R.ok(friendService.getFriends(UserContext.require()));
    }

    @PutMapping("/v1/friends/{userId}")
    public R<Void> updateFriend(@PathVariable Long userId, @RequestBody UpdateFriendReq req) {
        friendService.updateFriend(UserContext.require(), userId, req);
        return R.<Void>ok(null);
    }

    @DeleteMapping("/v1/friends/{userId}")
    public R<Void> deleteFriend(@PathVariable Long userId) {
        friendService.deleteFriend(UserContext.require(), userId);
        return R.<Void>ok(null);
    }

    // ---- 关注 ----
    @PostMapping("/v1/follows/{userId}")
    public R<Void> follow(@PathVariable Long userId) {
        friendService.follow(UserContext.require(), userId);
        return R.<Void>ok(null);
    }

    @DeleteMapping("/v1/follows/{userId}")
    public R<Void> unfollow(@PathVariable Long userId) {
        friendService.unfollow(UserContext.require(), userId);
        return R.<Void>ok(null);
    }

    @GetMapping("/v1/follows")
    public R<List<FollowVO>> getFollows(@RequestParam(defaultValue = "FOLLOWING") String type) {
        return R.ok(friendService.getFollows(UserContext.require(), type));
    }

    // ---- 黑名单 ----
    @PostMapping("/v1/blocks/{userId}")
    public R<Void> block(@PathVariable Long userId) {
        friendService.block(UserContext.require(), userId);
        return R.<Void>ok(null);
    }

    @DeleteMapping("/v1/blocks/{userId}")
    public R<Void> unblock(@PathVariable Long userId) {
        friendService.unblock(UserContext.require(), userId);
        return R.<Void>ok(null);
    }

    @GetMapping("/v1/blocks")
    public R<List<BlockVO>> getBlocks() {
        return R.ok(friendService.getBlockList(UserContext.require()));
    }
}
