package cn.edu.buaa.quju.module.user.service;

import cn.edu.buaa.quju.common.BizException;
import cn.edu.buaa.quju.common.ErrorCode;
import cn.edu.buaa.quju.module.user.dto.UserDtos;
import cn.edu.buaa.quju.module.user.dto.UserDtos.UserAiSettings;
import cn.edu.buaa.quju.module.user.dto.UserDtos.UpdateProfileReq;
import cn.edu.buaa.quju.module.user.dto.UserDtos.UserVO;
import cn.edu.buaa.quju.module.user.entity.User;
import cn.edu.buaa.quju.module.user.entity.UserInterestTag;
import cn.edu.buaa.quju.module.user.mapper.UserInterestTagMapper;
import cn.edu.buaa.quju.module.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserService {
    private static final Map<String, Boolean> DEFAULT_PRIVACY = Map.of("showActivities", true, "showTeams", true);

    private final UserMapper userMapper;
    private final UserInterestTagMapper tagMapper;
    private final ObjectMapper objectMapper;

    public UserService(UserMapper userMapper, UserInterestTagMapper tagMapper, ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.tagMapper = tagMapper;
        this.objectMapper = objectMapper;
    }

    public UserVO getProfile(long userId) {
        User u = requireUser(userId);
        return toVO(u);
    }

    public UserVO getPublicProfile(long userId) {
        User u = requireUser(userId);
        return toVO(u);
    }

    @Transactional
    public UserVO updateProfile(long userId, UpdateProfileReq req) {
        User u = requireUser(userId);
        if (req.accountId() != null && !req.accountId().equals(u.getAccountId())) {
            if (req.accountId().length() < 4 || req.accountId().length() > 32)
                throw new BizException(ErrorCode.BAD_REQUEST);
            Long taken = userMapper.selectCount(Wrappers.<User>lambdaQuery()
                    .eq(User::getAccountId, req.accountId()).ne(User::getId, userId));
            if (taken != null && taken > 0) throw new BizException(ErrorCode.CONFLICT);
            u.setAccountId(req.accountId());
        }
        if (req.nickname() != null && !req.nickname().equals(u.getNickname())) {
            Long taken = userMapper.selectCount(Wrappers.<User>lambdaQuery()
                    .eq(User::getNickname, req.nickname()).ne(User::getId, userId));
            if (taken != null && taken > 0) throw new BizException(ErrorCode.NICKNAME_TAKEN);
            u.setNickname(req.nickname());
        }
        if (req.avatar() != null) u.setAvatar(req.avatar());
        if (req.gender() != null) u.setGender(req.gender());
        if (req.birthday() != null) u.setBirthday(req.birthday());
        if (req.signature() != null) u.setSignature(req.signature());
        if (req.privacySettings() != null || req.aiSettings() != null) {
            u.setPrivacySettings(mergeSettingsJson(u.getPrivacySettings(), req.privacySettings(), req.aiSettings()));
        }
        userMapper.updateById(u);

        if (req.interestTags() != null) {
            tagMapper.delete(Wrappers.<UserInterestTag>lambdaQuery().eq(UserInterestTag::getUserId, userId));
            req.interestTags().stream().distinct().forEach(tag -> {
                UserInterestTag t = new UserInterestTag();
                t.setUserId(userId);
                t.setTag(tag);
                tagMapper.insert(t);
            });
        }
        return toVO(u);
    }

    /** 我的二维码内容（供加好友扫码）：返回 userId，前端生成实际二维码图片。 */
    public String getQrCodeContent(long userId) {
        return "quju://user/" + userId;
    }

    // ---- 私有工具 ----

    private User requireUser(long userId) {
        User u = userMapper.selectById(userId);
        if (u == null || u.getDeletedAt() != null) throw new BizException(ErrorCode.NOT_FOUND);
        return u;
    }

    private UserVO toVO(User u) {
        List<String> tags = tagMapper.selectList(
                Wrappers.<UserInterestTag>lambdaQuery().eq(UserInterestTag::getUserId, u.getId()))
                .stream().map(UserInterestTag::getTag).collect(Collectors.toList());
        Map<String, Boolean> privacy = parsePrivacy(u.getPrivacySettings());
        UserAiSettings aiSettings = parseAiSettings(u.getPrivacySettings());
        return new UserVO(u.getId(), u.getAccountId(), u.getEmail(), u.getNickname(), u.getAvatar(), u.getUserType(),
                u.getStatus(), u.getGender(), u.getBirthday(), u.getSignature(), u.getReputation(), tags, privacy, aiSettings);
    }

    Map<String, Boolean> parsePrivacy(String json) {
        if (json == null || json.isBlank()) return DEFAULT_PRIVACY;
        try {
            JsonNode root = objectMapper.readTree(json);
            Map<String, Boolean> privacy = new LinkedHashMap<>(DEFAULT_PRIVACY);
            if (root.has("showActivities")) privacy.put("showActivities", root.path("showActivities").asBoolean(true));
            if (root.has("showTeams")) privacy.put("showTeams", root.path("showTeams").asBoolean(true));
            return privacy;
        } catch (Exception e) {
            return DEFAULT_PRIVACY;
        }
    }

    UserAiSettings parseAiSettings(String json) {
        if (json == null || json.isBlank()) return new UserAiSettings(null);
        try {
            JsonNode node = objectMapper.readTree(json).path("aiSettings");
            if (node.isMissingNode() || node.isNull()) return new UserAiSettings(null);
            String systemPrompt = node.path("systemPrompt").asText(null);
            return new UserAiSettings(normalizeSystemPrompt(systemPrompt));
        } catch (Exception e) {
            return new UserAiSettings(null);
        }
    }

    public UserAiSettings getAiSettings(long userId) {
        User u = requireUser(userId);
        return parseAiSettings(u.getPrivacySettings());
    }

    public boolean isPrivacyAllowed(long userId, String key) {
        User u = userMapper.selectById(userId);
        if (u == null) return false;
        Map<String, Boolean> privacy = parsePrivacy(u.getPrivacySettings());
        return privacy.getOrDefault(key, true);
    }

    public UserDtos.UserBrief searchByAccountId(String accountId) {
        User u = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getAccountId, accountId).isNull(User::getDeletedAt));
        if (u == null) throw new BizException(ErrorCode.NOT_FOUND);
        return new UserDtos.UserBrief(u.getId(), u.getAccountId(), u.getNickname(), u.getAvatar(), u.getUserType(), u.getStatus());
    }

    private String mergeSettingsJson(String currentJson, Map<String, Boolean> privacySettings, UserAiSettings aiSettings) {
        ObjectNode root = readSettingsNode(currentJson);
        if (privacySettings != null) {
            root.put("showActivities", privacySettings.getOrDefault("showActivities", true));
            root.put("showTeams", privacySettings.getOrDefault("showTeams", true));
        } else {
            Map<String, Boolean> privacy = parsePrivacy(currentJson);
            root.put("showActivities", privacy.getOrDefault("showActivities", true));
            root.put("showTeams", privacy.getOrDefault("showTeams", true));
        }
        if (aiSettings != null) {
            String systemPrompt = normalizeSystemPrompt(aiSettings.systemPrompt());
            ObjectNode aiNode = root.with("aiSettings");
            if (systemPrompt == null) aiNode.remove("systemPrompt");
            else aiNode.put("systemPrompt", systemPrompt);
            if (aiNode.isEmpty()) root.remove("aiSettings");
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private ObjectNode readSettingsNode(String currentJson) {
        if (currentJson == null || currentJson.isBlank()) return objectMapper.createObjectNode();
        try {
            JsonNode root = objectMapper.readTree(currentJson);
            if (root instanceof ObjectNode objectNode) return objectNode.deepCopy();
        } catch (Exception ignored) {}
        return objectMapper.createObjectNode();
    }

    private String normalizeSystemPrompt(String systemPrompt) {
        if (systemPrompt == null) return null;
        String normalized = systemPrompt.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > 1000) throw new BizException(ErrorCode.BAD_REQUEST, "system_prompt_too_long");
        return normalized;
    }
}
