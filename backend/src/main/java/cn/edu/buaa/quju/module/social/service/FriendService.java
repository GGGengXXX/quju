package cn.edu.buaa.quju.module.social.service;

import cn.edu.buaa.quju.common.BizException;
import cn.edu.buaa.quju.common.ErrorCode;
import cn.edu.buaa.quju.common.PageResult;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.BlockVO;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.FollowVO;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.FriendRequestVO;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.FriendVO;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.SendFriendRequestReq;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.UpdateFriendReq;
import cn.edu.buaa.quju.module.social.entity.Follow;
import cn.edu.buaa.quju.module.social.entity.FriendRequest;
import cn.edu.buaa.quju.module.social.entity.Friendship;
import cn.edu.buaa.quju.module.social.entity.UserBlock;
import cn.edu.buaa.quju.module.social.mapper.FollowMapper;
import cn.edu.buaa.quju.module.social.mapper.FriendRequestMapper;
import cn.edu.buaa.quju.module.social.mapper.FriendshipMapper;
import cn.edu.buaa.quju.module.social.mapper.UserBlockMapper;
import cn.edu.buaa.quju.module.user.entity.User;
import cn.edu.buaa.quju.module.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FriendService {
    private final FriendRequestMapper requestMapper;
    private final FriendshipMapper friendshipMapper;
    private final FollowMapper followMapper;
    private final UserBlockMapper blockMapper;
    private final UserMapper userMapper;

    public FriendService(FriendRequestMapper requestMapper, FriendshipMapper friendshipMapper,
                         FollowMapper followMapper, UserBlockMapper blockMapper, UserMapper userMapper) {
        this.requestMapper = requestMapper;
        this.friendshipMapper = friendshipMapper;
        this.followMapper = followMapper;
        this.blockMapper = blockMapper;
        this.userMapper = userMapper;
    }

    // ---- R3.1 好友申请 ----

    @Transactional
    public void sendRequest(long fromId, SendFriendRequestReq req) {
        long toId = req.toUserId();
        if (fromId == toId) throw new BizException(ErrorCode.BAD_REQUEST);
        // 黑名单检查
        if (isBlocked(fromId, toId) || isBlocked(toId, fromId))
            throw new BizException(ErrorCode.BLOCKED_RELATION);
        // 已是好友
        if (areFriends(fromId, toId)) throw new BizException(ErrorCode.ALREADY_FRIENDS);
        // 已有 PENDING 申请
        Long pending = requestMapper.selectCount(Wrappers.<FriendRequest>lambdaQuery()
                .eq(FriendRequest::getFromUserId, fromId)
                .eq(FriendRequest::getToUserId, toId)
                .eq(FriendRequest::getStatus, "PENDING"));
        if (pending != null && pending > 0) throw new BizException(ErrorCode.FRIEND_REQUEST_DUPLICATE);

        FriendRequest fr = new FriendRequest();
        fr.setFromUserId(fromId);
        fr.setToUserId(toId);
        fr.setStatus("PENDING");
        fr.setSource(req.source());
        fr.setMessage(req.message());
        requestMapper.insert(fr);
    }

    public PageResult<FriendRequestVO> getReceivedRequests(long userId, int page, int size) {
        IPage<FriendRequest> pg = requestMapper.selectPage(new Page<>(page, size),
                Wrappers.<FriendRequest>lambdaQuery()
                        .eq(FriendRequest::getToUserId, userId)
                        .eq(FriendRequest::getStatus, "PENDING")
                        .orderByDesc(FriendRequest::getCreatedAt));
        List<Long> fromIds = pg.getRecords().stream().map(FriendRequest::getFromUserId).toList();
        Map<Long, User> users = fromIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(fromIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<FriendRequestVO> list = pg.getRecords().stream().map(fr -> {
            User u = users.get(fr.getFromUserId());
            return new FriendRequestVO(fr.getId(), fr.getFromUserId(),
                    u != null ? u.getNickname() : null, u != null ? u.getAvatar() : null,
                    fr.getToUserId(), fr.getStatus(), fr.getSource(), fr.getMessage(), fr.getCreatedAt());
        }).toList();
        return PageResult.of(pg.getTotal(), page, size, list);
    }

    @Transactional
    public void acceptRequest(long userId, long requestId) {
        FriendRequest fr = requirePendingRequest(userId, requestId);
        fr.setStatus("ACCEPTED");
        fr.setHandledAt(LocalDateTime.now());
        requestMapper.updateById(fr);
        // 双向建立好友关系
        addFriendship(fr.getFromUserId(), fr.getToUserId());
        addFriendship(fr.getToUserId(), fr.getFromUserId());
        // 清理双方互相的关注（互关升级为好友，保留关注关系不强制删除，只升级好友表）
    }

    @Transactional
    public void rejectRequest(long userId, long requestId) {
        FriendRequest fr = requirePendingRequest(userId, requestId);
        fr.setStatus("REJECTED");
        fr.setHandledAt(LocalDateTime.now());
        requestMapper.updateById(fr);
    }

    // ---- R3.2 好友管理 ----

    public List<FriendVO> getFriends(long userId) {
        List<Friendship> friends = friendshipMapper.selectList(
                Wrappers.<Friendship>lambdaQuery().eq(Friendship::getOwnerId, userId));
        List<Long> ids = friends.stream().map(Friendship::getFriendId).toList();
        if (ids.isEmpty()) return List.of();
        Map<Long, User> users = userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return friends.stream().map(f -> {
            User u = users.get(f.getFriendId());
            return new FriendVO(f.getFriendId(), u != null ? u.getNickname() : null,
                    u != null ? u.getAvatar() : null, u != null ? u.getUserType() : null,
                    f.getRemark(), f.getGroupTag(), f.getCreatedAt());
        }).toList();
    }

    @Transactional
    public void updateFriend(long userId, long friendId, UpdateFriendReq req) {
        Friendship f = friendshipMapper.selectOne(Wrappers.<Friendship>lambdaQuery()
                .eq(Friendship::getOwnerId, userId).eq(Friendship::getFriendId, friendId));
        if (f == null) throw new BizException(ErrorCode.NOT_FRIENDS);
        if (req.remark() != null) f.setRemark(req.remark());
        if (req.groupTag() != null) f.setGroupTag(req.groupTag());
        friendshipMapper.updateById(f);
    }

    @Transactional
    public void deleteFriend(long userId, long friendId) {
        if (!areFriends(userId, friendId)) throw new BizException(ErrorCode.NOT_FRIENDS);
        // 双向删除
        friendshipMapper.delete(Wrappers.<Friendship>lambdaQuery()
                .eq(Friendship::getOwnerId, userId).eq(Friendship::getFriendId, friendId));
        friendshipMapper.delete(Wrappers.<Friendship>lambdaQuery()
                .eq(Friendship::getOwnerId, friendId).eq(Friendship::getFriendId, userId));
    }

    // ---- R3.3 关注关系 ----

    @Transactional
    public void follow(long followerId, long followeeId) {
        if (followerId == followeeId) throw new BizException(ErrorCode.BAD_REQUEST);
        if (isBlocked(followeeId, followerId)) throw new BizException(ErrorCode.BLOCKED_RELATION);
        Long exists = followMapper.selectCount(Wrappers.<Follow>lambdaQuery()
                .eq(Follow::getFollowerId, followerId).eq(Follow::getFolloweeId, followeeId));
        if (exists != null && exists > 0) return; // 已关注，幂等

        Follow f = new Follow();
        f.setFollowerId(followerId);
        f.setFolloweeId(followeeId);
        followMapper.insert(f);

        // 互关检查：若对方也关注了我，升级为好友
        Long mutual = followMapper.selectCount(Wrappers.<Follow>lambdaQuery()
                .eq(Follow::getFollowerId, followeeId).eq(Follow::getFolloweeId, followerId));
        if (mutual != null && mutual > 0 && !areFriends(followerId, followeeId)) {
            addFriendship(followerId, followeeId);
            addFriendship(followeeId, followerId);
        }
    }

    @Transactional
    public void unfollow(long followerId, long followeeId) {
        followMapper.delete(Wrappers.<Follow>lambdaQuery()
                .eq(Follow::getFollowerId, followerId).eq(Follow::getFolloweeId, followeeId));
        // 取关同步解除好友
        if (areFriends(followerId, followeeId)) {
            friendshipMapper.delete(Wrappers.<Friendship>lambdaQuery()
                    .eq(Friendship::getOwnerId, followerId).eq(Friendship::getFriendId, followeeId));
            friendshipMapper.delete(Wrappers.<Friendship>lambdaQuery()
                    .eq(Friendship::getOwnerId, followeeId).eq(Friendship::getFriendId, followerId));
        }
    }

    public List<FollowVO> getFollows(long userId, String type) {
        List<Follow> list;
        List<Long> targetIds;
        if ("FOLLOWING".equals(type)) {
            list = followMapper.selectList(Wrappers.<Follow>lambdaQuery().eq(Follow::getFollowerId, userId));
            targetIds = list.stream().map(Follow::getFolloweeId).toList();
        } else {
            list = followMapper.selectList(Wrappers.<Follow>lambdaQuery().eq(Follow::getFolloweeId, userId));
            targetIds = list.stream().map(Follow::getFollowerId).toList();
        }
        if (targetIds.isEmpty()) return List.of();
        Map<Long, User> users = userMapper.selectBatchIds(targetIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return list.stream().map(f -> {
            Long uid = "FOLLOWING".equals(type) ? f.getFolloweeId() : f.getFollowerId();
            User u = users.get(uid);
            return new FollowVO(uid, u != null ? u.getNickname() : null,
                    u != null ? u.getAvatar() : null, f.getCreatedAt());
        }).toList();
    }

    // ---- 黑名单 ----

    @Transactional
    public void block(long userId, long targetId) {
        if (userId == targetId) throw new BizException(ErrorCode.BAD_REQUEST);
        Long exists = blockMapper.selectCount(Wrappers.<UserBlock>lambdaQuery()
                .eq(UserBlock::getUserId, userId).eq(UserBlock::getBlockedUserId, targetId));
        if (exists != null && exists > 0) return;
        UserBlock b = new UserBlock();
        b.setUserId(userId);
        b.setBlockedUserId(targetId);
        blockMapper.insert(b);
        // 拉黑同时解除好友关系
        if (areFriends(userId, targetId)) {
            friendshipMapper.delete(Wrappers.<Friendship>lambdaQuery()
                    .eq(Friendship::getOwnerId, userId).eq(Friendship::getFriendId, targetId));
            friendshipMapper.delete(Wrappers.<Friendship>lambdaQuery()
                    .eq(Friendship::getOwnerId, targetId).eq(Friendship::getFriendId, userId));
        }
    }

    public void unblock(long userId, long targetId) {
        blockMapper.delete(Wrappers.<UserBlock>lambdaQuery()
                .eq(UserBlock::getUserId, userId).eq(UserBlock::getBlockedUserId, targetId));
    }

    public List<BlockVO> getBlockList(long userId) {
        List<UserBlock> list = blockMapper.selectList(
                Wrappers.<UserBlock>lambdaQuery().eq(UserBlock::getUserId, userId));
        List<Long> ids = list.stream().map(UserBlock::getBlockedUserId).toList();
        if (ids.isEmpty()) return List.of();
        Map<Long, User> users = userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return list.stream().map(b -> {
            User u = users.get(b.getBlockedUserId());
            return new BlockVO(b.getBlockedUserId(), u != null ? u.getNickname() : null,
                    u != null ? u.getAvatar() : null, b.getCreatedAt());
        }).toList();
    }

    // ---- 工具方法 ----

    private boolean areFriends(long a, long b) {
        return friendshipMapper.selectCount(Wrappers.<Friendship>lambdaQuery()
                .eq(Friendship::getOwnerId, a).eq(Friendship::getFriendId, b)) > 0;
    }

    private boolean isBlocked(long userId, long targetId) {
        return blockMapper.selectCount(Wrappers.<UserBlock>lambdaQuery()
                .eq(UserBlock::getUserId, userId).eq(UserBlock::getBlockedUserId, targetId)) > 0;
    }

    private void addFriendship(long ownerId, long friendId) {
        Long exists = friendshipMapper.selectCount(Wrappers.<Friendship>lambdaQuery()
                .eq(Friendship::getOwnerId, ownerId).eq(Friendship::getFriendId, friendId));
        if (exists != null && exists > 0) return;
        Friendship fs = new Friendship();
        fs.setOwnerId(ownerId);
        fs.setFriendId(friendId);
        friendshipMapper.insert(fs);
    }

    private FriendRequest requirePendingRequest(long toUserId, long requestId) {
        FriendRequest fr = requestMapper.selectOne(Wrappers.<FriendRequest>lambdaQuery()
                .eq(FriendRequest::getId, requestId)
                .eq(FriendRequest::getToUserId, toUserId)
                .eq(FriendRequest::getStatus, "PENDING"));
        if (fr == null) throw new BizException(ErrorCode.NOT_FOUND);
        return fr;
    }
}
