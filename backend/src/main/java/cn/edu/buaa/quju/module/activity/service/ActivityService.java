package cn.edu.buaa.quju.module.activity.service;

import cn.edu.buaa.quju.common.BizException;
import cn.edu.buaa.quju.common.ErrorCode;
import cn.edu.buaa.quju.common.PageResult;
import cn.edu.buaa.quju.common.UserContext;
import cn.edu.buaa.quju.module.activity.dto.ActivityDtos.AiPlanReq;
import cn.edu.buaa.quju.module.activity.dto.ActivityDtos.ActivityDetailVO;
import cn.edu.buaa.quju.module.activity.dto.ActivityDtos.ActivityPointVO;
import cn.edu.buaa.quju.module.activity.dto.ActivityDtos.ActivityUpsertReq;
import cn.edu.buaa.quju.module.activity.dto.ActivityDtos.ActivityVO;
import cn.edu.buaa.quju.module.activity.dto.ActivityDtos.CheckinCodeVO;
import cn.edu.buaa.quju.module.activity.dto.ActivityDtos.CheckinReq;
import cn.edu.buaa.quju.module.activity.dto.ActivityDtos.ReviewUpsertReq;
import cn.edu.buaa.quju.module.activity.dto.ActivityDtos.ReviewVO;
import cn.edu.buaa.quju.module.activity.dto.ActivityDtos.SignupManageVO;
import cn.edu.buaa.quju.module.activity.dto.ActivityDtos.SignupReq;
import cn.edu.buaa.quju.module.activity.dto.ActivityDtos.SignupResultVO;
import cn.edu.buaa.quju.module.activity.dto.ActivityDtos.SummaryImageUpdateReq;
import cn.edu.buaa.quju.module.activity.dto.ActivityDtos.SummaryImageUploadReq;
import cn.edu.buaa.quju.module.activity.dto.ActivityDtos.SummaryImageVO;
import cn.edu.buaa.quju.module.activity.dto.ActivityDtos.SummaryUpsertReq;
import cn.edu.buaa.quju.module.activity.dto.ActivityDtos.SummaryVO;
import cn.edu.buaa.quju.module.activity.dto.ActivityDtos.TemplateVO;
import cn.edu.buaa.quju.module.activity.dto.ActivityDtos.WaitlistPageVO;
import cn.edu.buaa.quju.module.activity.dto.ActivityDtos.WaitlistVO;
import cn.edu.buaa.quju.module.activity.entity.Activity;
import cn.edu.buaa.quju.module.activity.entity.ActivityAuditLog;
import cn.edu.buaa.quju.module.activity.entity.ActivityCheckin;
import cn.edu.buaa.quju.module.activity.entity.ActivityReview;
import cn.edu.buaa.quju.module.activity.entity.ActivitySignup;
import cn.edu.buaa.quju.module.activity.entity.ActivitySummary;
import cn.edu.buaa.quju.module.activity.entity.ActivitySummaryImage;
import cn.edu.buaa.quju.module.activity.entity.ActivityTag;
import cn.edu.buaa.quju.module.activity.entity.ActivityTemplate;
import cn.edu.buaa.quju.module.activity.entity.ActivityWaitlist;
import cn.edu.buaa.quju.module.activity.mapper.ActivityCheckinMapper;
import cn.edu.buaa.quju.module.activity.mapper.ActivityAuditLogMapper;
import cn.edu.buaa.quju.module.activity.mapper.ActivityDomainMapper;
import cn.edu.buaa.quju.module.activity.mapper.ActivityReviewMapper;
import cn.edu.buaa.quju.module.activity.mapper.ActivitySignupMapper;
import cn.edu.buaa.quju.module.activity.mapper.ActivitySummaryImageMapper;
import cn.edu.buaa.quju.module.activity.mapper.ActivitySummaryMapper;
import cn.edu.buaa.quju.module.activity.mapper.ActivityTagMapper;
import cn.edu.buaa.quju.module.activity.mapper.ActivityTemplateMapper;
import cn.edu.buaa.quju.module.activity.mapper.ActivityWaitlistMapper;
import cn.edu.buaa.quju.module.notification.service.NotificationService;
import cn.edu.buaa.quju.module.user.dto.UserDtos.UserBrief;
import cn.edu.buaa.quju.module.user.entity.User;
import cn.edu.buaa.quju.module.user.mapper.UserMapper;
import cn.edu.buaa.quju.module.user.service.EmailService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ActivityService {
    private static final Set<String> SUMMARY_IMAGE_CATEGORIES = Set.of(
            "GROUP_PHOTO", "VENUE", "PROCESS", "MATERIAL", "RESULT"
    );
    private static final BigDecimal CHECKIN_RADIUS_KM = new BigDecimal("0.50");

    private final ActivityDomainMapper activityMapper;
    private final ActivityTagMapper activityTagMapper;
    private final ActivityTemplateMapper templateMapper;
    private final ActivitySignupMapper signupMapper;
    private final ActivityWaitlistMapper waitlistMapper;
    private final ActivityCheckinMapper checkinMapper;
    private final ActivitySummaryMapper summaryMapper;
    private final ActivitySummaryImageMapper summaryImageMapper;
    private final ActivityReviewMapper reviewMapper;
    private final UserMapper userMapper;
    private final ActivityAuditLogMapper activityAuditLogMapper;
    private final ActivityAiService activityAiService;
    private final ActivityImageStorageService activityImageStorageService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ActivityService(ActivityDomainMapper activityMapper,
                           ActivityTagMapper activityTagMapper,
                           ActivityTemplateMapper templateMapper,
                           ActivitySignupMapper signupMapper,
                           ActivityWaitlistMapper waitlistMapper,
                           ActivityCheckinMapper checkinMapper,
                           ActivitySummaryMapper summaryMapper,
                           ActivitySummaryImageMapper summaryImageMapper,
                           ActivityReviewMapper reviewMapper,
                           UserMapper userMapper,
                           ActivityAuditLogMapper activityAuditLogMapper,
                           ActivityAiService activityAiService,
                           ActivityImageStorageService activityImageStorageService,
                           EmailService emailService,
                           NotificationService notificationService,
                           org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.activityMapper = activityMapper;
        this.activityTagMapper = activityTagMapper;
        this.templateMapper = templateMapper;
        this.signupMapper = signupMapper;
        this.waitlistMapper = waitlistMapper;
        this.checkinMapper = checkinMapper;
        this.summaryMapper = summaryMapper;
        this.summaryImageMapper = summaryImageMapper;
        this.reviewMapper = reviewMapper;
        this.userMapper = userMapper;
        this.activityAuditLogMapper = activityAuditLogMapper;
        this.activityAiService = activityAiService;
        this.activityImageStorageService = activityImageStorageService;
        this.emailService = emailService;
        this.notificationService = notificationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TemplateVO> listTemplates() {
        return templateMapper.selectList(Wrappers.<ActivityTemplate>lambdaQuery()
                        .orderByDesc(ActivityTemplate::getIsSystem)
                        .orderByAsc(ActivityTemplate::getId))
                .stream()
                .map(t -> new TemplateVO(t.getId(), t.getName(), t.getCategory(), t.getDefaultIntro(),
                        t.getDefaultCapacity(), t.getIcon(), t.getIsSystem()))
                .toList();
    }

    public PageResult<ActivityVO> discover(String tab, String keyword, String category, String categories, String status, String city,
                                           LocalDateTime startFrom, LocalDateTime startTo,
                                           BigDecimal feeMin, BigDecimal feeMax,
                                           BigDecimal lng, BigDecimal lat, BigDecimal distanceKm,
                                           int page, int size) {
        Long currentUserId = UserContext.get();
        LambdaQueryWrapper<Activity> qw = Wrappers.<Activity>lambdaQuery()
                .isNull(Activity::getDeletedAt);
        if (currentUserId == null) {
            qw.eq(Activity::getStatus, "PUBLISHED");
        } else {
            qw.and(w -> w.eq(Activity::getStatus, "PUBLISHED")
                    .or()
                    .eq(Activity::getCreatorId, currentUserId));
        }
        if (status != null && !status.isBlank()) qw.eq(Activity::getStatus, status);
        if (city != null && !city.isBlank()) qw.eq(Activity::getCity, city);
        if (startFrom != null) qw.ge(Activity::getStartTime, startFrom);
        if (startTo != null) qw.le(Activity::getStartTime, startTo);
        if (feeMin != null) qw.ge(Activity::getFee, feeMin);
        if (feeMax != null) qw.le(Activity::getFee, feeMax);

        List<String> categoryList = normalizeCategories(category, categories);
        if (!categoryList.isEmpty()) qw.in(Activity::getCategory, categoryList);

        List<Activity> activities = activityMapper.selectList(qw);
        Map<Long, List<String>> tagsByActivity = getTags(activities.stream().map(Activity::getId).toList());
        if (keyword != null && !keyword.isBlank()) {
            String normalizedKeyword = keyword.trim().toLowerCase();
            activities = activities.stream().filter(activity -> tagsByActivity.getOrDefault(activity.getId(), List.of())
                    .stream().map(String::toLowerCase).anyMatch(tag -> tag.contains(normalizedKeyword))
                    || containsIgnoreCase(activity.getName(), normalizedKeyword)
                    || containsIgnoreCase(activity.getIntro(), normalizedKeyword))
                    .toList();
        }
        boolean nearbyMode = "NEARBY".equalsIgnoreCase(tab) || (lng != null && lat != null && distanceKm != null);
        BigDecimal effectiveDistance = distanceKm == null ? new BigDecimal("10") : distanceKm;
        Map<Long, BigDecimal> distanceMap = new HashMap<>();
        List<Activity> filtered = new ArrayList<>();
        for (Activity activity : activities) {
            if (nearbyMode) {
                if (lng == null || lat == null || activity.getLng() == null || activity.getLat() == null) continue;
                BigDecimal currentDistance = distanceKm(activity.getLat(), activity.getLng(), lat, lng);
                if (currentDistance.compareTo(effectiveDistance) > 0) continue;
                distanceMap.put(activity.getId(), currentDistance);
            }
            filtered.add(activity);
        }
        sortActivities(filtered, tab, distanceMap);
        return paginateActivities(filtered, page, size);
    }

    public PageResult<ActivityVO> myActivities(String status, int page, int size) {
        long userId = UserContext.require();
        Page<Activity> p = new Page<>(page, size);
        LambdaQueryWrapper<Activity> qw = Wrappers.<Activity>lambdaQuery()
                .eq(Activity::getCreatorId, userId)
                .isNull(Activity::getDeletedAt)
                .orderByDesc(Activity::getCreatedAt);
        if (status != null && !status.isBlank()) qw.eq(Activity::getStatus, status);
        activityMapper.selectPage(p, qw);
        return new PageResult<>(p.getTotal(), page, size, toActivityVOList(p.getRecords()));
    }

    public List<ActivityVO> userJoinedActivities(long userId) {
        List<ActivitySignup> signups = signupMapper.selectList(Wrappers.<ActivitySignup>lambdaQuery()
                .eq(ActivitySignup::getUserId, userId).eq(ActivitySignup::getStatus, "REGISTERED"));
        if (signups.isEmpty()) return List.of();
        List<Long> activityIds = signups.stream().map(ActivitySignup::getActivityId).toList();
        List<Activity> activities = activityMapper.selectBatchIds(activityIds);
        return toActivityVOList(activities.stream().filter(a -> a.getDeletedAt() == null).toList());
    }

    public List<ActivityPointVO> mapPoints(BigDecimal minLng, BigDecimal maxLng, BigDecimal minLat, BigDecimal maxLat) {
        LambdaQueryWrapper<Activity> qw = Wrappers.<Activity>lambdaQuery()
                .isNull(Activity::getDeletedAt)
                .isNotNull(Activity::getLng)
                .isNotNull(Activity::getLat)
                .eq(Activity::getStatus, "PUBLISHED")
                .orderByDesc(Activity::getStartTime);
        if (minLng != null) qw.ge(Activity::getLng, minLng);
        if (maxLng != null) qw.le(Activity::getLng, maxLng);
        if (minLat != null) qw.ge(Activity::getLat, minLat);
        if (maxLat != null) qw.le(Activity::getLat, maxLat);
        return activityMapper.selectList(qw).stream()
                .map(a -> new ActivityPointVO(a.getId(), a.getName(), a.getCategory(), a.getLng(), a.getLat(),
                        a.getCity(), a.getStatus(), calcPhase(a)))
                .toList();
    }

    public ActivityVO aiPlan(AiPlanReq req) {
        long userId = UserContext.require();
        UserBrief creator = loadUserBriefs(List.of(userId)).get(userId);
        String normalizedCategory = normalizeCategory(req == null ? null : req.category());
        String theme = req == null || req.theme() == null || req.theme().isBlank() ? categoryLabel(normalizedCategory) + "主题活动" : req.theme().trim();
        ActivityAiService.AiPlanResult plan = activityAiService.generatePlan(theme, normalizedCategory);
        LocalDateTime defaultStart = LocalDateTime.now().plusDays(7).withHour(19).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime defaultEnd = defaultStart.plusHours(2);
        LocalDateTime defaultDeadline = defaultStart.minusDays(2).withHour(18).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime plannedStart = parseAiDateTime(plan.startTime(), defaultStart);
        if (!plannedStart.isAfter(LocalDateTime.now().plusDays(1))) plannedStart = defaultStart;
        LocalDateTime plannedEnd = parseAiDateTime(plan.endTime(), plannedStart.plusHours(2));
        if (!plannedEnd.isAfter(plannedStart)) plannedEnd = plannedStart.plusHours(2);
        LocalDateTime plannedDeadline = parseAiDateTime(plan.signupDeadline(), defaultDeadline);
        if (!plannedDeadline.isBefore(plannedStart) || !plannedDeadline.isAfter(LocalDateTime.now())) {
            plannedDeadline = defaultDeadline;
        }
        Activity activity = new Activity();
        activity.setCreatorId(userId);
        activity.setName(plan.name());
        activity.setIntro(plan.intro());
        activity.setCategory(normalizeCategory(plan.category()));
        activity.setCity(plan.city());
        activity.setAddress(plan.address());
        activity.setCapacity(plan.capacity());
        activity.setFee(plan.fee() == null ? BigDecimal.ZERO : plan.fee());
        activity.setStatus("DRAFT");
        activity.setStartTime(plannedStart);
        activity.setEndTime(plannedEnd);
        activity.setSignupDeadline(plannedDeadline);
        return new ActivityVO(null, activity.getName(), activity.getIntro(), activity.getCategory(),
                plan.tags(), null, activity.getStartTime(), activity.getEndTime(),
                activity.getSignupDeadline(), activity.getCity(), activity.getAddress(), null, null,
                activity.getCapacity(), activity.getFee(), activity.getStatus(), calcPhase(activity), 0, creator, null);
    }

    public ActivityDetailVO getDetail(long id) {
        Activity activity = getActivityOrThrow(id);
        ensureReadable(activity, UserContext.get());
        Map<Long, List<String>> tags = getTags(List.of(id));
        UserBrief creator = loadCreators(List.of(activity)).get(activity.getCreatorId());
        Map<Long, Integer> signupCounts = getSignupCounts(List.of(id));
        Map<Long, Integer> waitlistCounts = getWaitlistCounts(List.of(id));
        String mySignupStatus = resolveMySignupStatus(id, UserContext.get());
        return new ActivityDetailVO(activity.getId(), activity.getName(), activity.getIntro(), activity.getCategory(),
                tags.getOrDefault(id, Collections.emptyList()), activity.getCoverImage(), activity.getStartTime(), activity.getEndTime(),
                activity.getSignupDeadline(), activity.getCity(), activity.getAddress(), activity.getLng(), activity.getLat(),
                activity.getCapacity(), activity.getFee(), activity.getStatus(), calcPhase(activity), signupCounts.getOrDefault(id, 0),
                creator, activity.getTeamId(), mySignupStatus, waitlistCounts.getOrDefault(id, 0),
                exposeCheckinCode(activity, mySignupStatus, UserContext.get()));
    }

    @Transactional
    public ActivityVO create(ActivityUpsertReq req) {
        long userId = UserContext.require();
        Activity activity = new Activity();
        fillActivity(activity, req);
        activity.setCreatorId(userId);
        activity.setStatus("DRAFT");
        activity.setIsAiGenerated(Boolean.FALSE);
        activityMapper.insert(activity);
        replaceTags(activity.getId(), req.tags());
        if (Boolean.TRUE.equals(req.submit())) {
            applySubmissionDecision(activity);
        }
        return toActivityVO(activity, getTags(List.of(activity.getId())), loadCreators(List.of(activity)), getSignupCounts(List.of(activity.getId())));
    }

    @Transactional
    public ActivityVO update(long id, ActivityUpsertReq req) {
        long userId = UserContext.require();
        Activity activity = getActivityOrThrow(id);
        ensureOwner(activity, userId);
        fillActivity(activity, req);
        activityMapper.updateById(activity);
        replaceTags(id, req.tags());
        if (Boolean.TRUE.equals(req.submit())) {
            applySubmissionDecision(activity);
        }
        return toActivityVO(activity, getTags(List.of(id)), loadCreators(List.of(activity)), getSignupCounts(List.of(id)));
    }

    @Transactional
    public void deleteActivity(long id) {
        long userId = UserContext.require();
        Activity activity = getActivityOrThrow(id);
        ensureOwner(activity, userId);
        if ("PUBLISHED".equals(activity.getStatus())) {
            activity.setStatus("CANCELLED");
        } else {
            activity.setDeletedAt(LocalDateTime.now());
        }
        activityMapper.updateById(activity);
    }

    @Transactional
    public void submit(long id) {
        long userId = UserContext.require();
        Activity activity = getActivityOrThrow(id);
        ensureOwner(activity, userId);
        applySubmissionDecision(activity);
    }

    @Transactional
    public ActivityVO cloneActivity(long id) {
        long userId = UserContext.require();
        Activity src = getActivityOrThrow(id);
        Activity clone = new Activity();
        clone.setCreatorId(userId);
        clone.setTeamId(src.getTeamId());
        clone.setName(src.getName() + " (副本)");
        clone.setIntro(src.getIntro());
        clone.setCategory(src.getCategory());
        clone.setCoverImage(src.getCoverImage());
        clone.setStartTime(src.getStartTime());
        clone.setEndTime(src.getEndTime());
        clone.setSignupDeadline(src.getSignupDeadline());
        clone.setCity(src.getCity());
        clone.setAddress(src.getAddress());
        clone.setLng(src.getLng());
        clone.setLat(src.getLat());
        clone.setCapacity(src.getCapacity());
        clone.setFee(src.getFee());
        clone.setStatus("DRAFT");
        clone.setIsAiGenerated(src.getIsAiGenerated());
        clone.setTemplateId(src.getTemplateId());
        clone.setClonedFromId(src.getId());
        activityMapper.insert(clone);
        replaceTags(clone.getId(), getTags(List.of(id)).getOrDefault(id, Collections.emptyList()));
        return toActivityVO(clone, getTags(List.of(clone.getId())), loadCreators(List.of(clone)), getSignupCounts(List.of(clone.getId())));
    }

    @Transactional
    public SignupResultVO signup(long activityId, SignupReq req) {
        long userId = UserContext.require();
        Activity activity = getActivityOrThrow(activityId);
        ensureSignupOpen(activity);
        ensureSignupChecks(userId, req);
        ActivitySignup activeSignup = signupMapper.selectOne(Wrappers.<ActivitySignup>lambdaQuery()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(ActivitySignup::getUserId, userId)
                .eq(ActivitySignup::getStatus, "REGISTERED")
                .last("LIMIT 1"));
        if (activeSignup != null) throw new BizException(ErrorCode.ALREADY_SIGNED_UP);
        ActivityWaitlist activeWait = waitlistMapper.selectOne(Wrappers.<ActivityWaitlist>lambdaQuery()
                .eq(ActivityWaitlist::getActivityId, activityId)
                .eq(ActivityWaitlist::getUserId, userId)
                .in(ActivityWaitlist::getStatus, List.of("WAITING", "NOTIFIED"))
                .last("LIMIT 1"));
        if (activeWait != null) return new SignupResultVO("WAITLISTED", activeWait.getPosition());

        int registered = countRegistered(activityId);
        if (activity.getCapacity() == null || registered < activity.getCapacity()) {
            ActivitySignup signup = new ActivitySignup();
            signup.setActivityId(activityId);
            signup.setUserId(userId);
            signup.setStatus("REGISTERED");
            signup.setSignupInfo(toJson(createSignupPayload(req)));
            signupMapper.insert(signup);
            // 通知活动创建者有人报名
            notificationService.send(activity.getCreatorId(), "ACTIVITY_SIGNUP",
                    "活动「" + activity.getName() + "」有新报名", null, "ACTIVITY", activityId);
            // 队内活动报名加积分
            if (activity.getTeamId() != null) {
                awardTeamPoints(activity.getTeamId(), userId, 3, "JOIN_ACTIVITY", activityId);
            }
            return new SignupResultVO("REGISTERED", null);
        }

        int position = nextWaitlistPosition(activityId);
        ActivityWaitlist wait = new ActivityWaitlist();
        wait.setActivityId(activityId);
        wait.setUserId(userId);
        wait.setPosition(position);
        wait.setStatus("WAITING");
        waitlistMapper.insert(wait);
        return new SignupResultVO("WAITLISTED", position);
    }

    @Transactional
    public void cancelSignup(long activityId) {
        long userId = UserContext.require();
        Activity activity = getActivityOrThrow(activityId);
        if (activity.getSignupDeadline() != null && LocalDateTime.now().isAfter(activity.getSignupDeadline())) {
            throw new BizException(ErrorCode.SIGNUP_DEADLINE_PASSED);
        }
        ActivitySignup signup = signupMapper.selectOne(Wrappers.<ActivitySignup>lambdaQuery()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(ActivitySignup::getUserId, userId)
                .eq(ActivitySignup::getStatus, "REGISTERED")
                .last("LIMIT 1"));
        if (signup == null) throw new BizException(ErrorCode.NOT_SIGNED_UP);
        signup.setStatus("CANCELLED");
        signup.setCancelledAt(LocalDateTime.now());
        signupMapper.updateById(signup);
        notifyNextWaiter(activityId);
    }

    public PageResult<SignupManageVO> listSignups(long activityId, int page, int size) {
        Activity activity = getActivityOrThrow(activityId);
        ensureOwner(activity, UserContext.require());
        Page<ActivitySignup> p = new Page<>(page, size);
        signupMapper.selectPage(p, Wrappers.<ActivitySignup>lambdaQuery()
                .eq(ActivitySignup::getActivityId, activityId)
                .orderByDesc(ActivitySignup::getCreatedAt));
        Map<Long, UserBrief> users = loadUserBriefs(p.getRecords().stream().map(ActivitySignup::getUserId).toList());
        Map<Long, ActivityCheckin> checkins = checkinMapper.selectList(Wrappers.<ActivityCheckin>lambdaQuery()
                        .eq(ActivityCheckin::getActivityId, activityId)
                        .in(!p.getRecords().isEmpty(), ActivityCheckin::getUserId, p.getRecords().stream().map(ActivitySignup::getUserId).toList()))
                .stream().collect(Collectors.toMap(ActivityCheckin::getUserId, Function.identity(), (a, b) -> a));
        List<SignupManageVO> list = p.getRecords().stream().map(s -> {
            UserBrief user = users.get(s.getUserId());
            ActivityCheckin checkin = checkins.get(s.getUserId());
            return new SignupManageVO(s.getId(), s.getUserId(), user == null ? null : user.nickname(), s.getStatus(),
                    checkin != null, s.getCreatedAt(), checkin == null ? null : checkin.getCheckinAt());
        }).toList();
        return new PageResult<>(p.getTotal(), page, size, list);
    }

    public WaitlistPageVO getWaitlist(long activityId) {
        Activity activity = getActivityOrThrow(activityId);
        ensureOwner(activity, UserContext.require());
        List<ActivityWaitlist> waits = waitlistMapper.selectList(Wrappers.<ActivityWaitlist>lambdaQuery()
                .eq(ActivityWaitlist::getActivityId, activityId)
                .orderByAsc(ActivityWaitlist::getPosition));
        Map<Long, UserBrief> users = loadUserBriefs(waits.stream().map(ActivityWaitlist::getUserId).toList());
        List<WaitlistVO> list = waits.stream().map(w -> {
            UserBrief user = users.get(w.getUserId());
            return new WaitlistVO(w.getId(), w.getUserId(), user == null ? null : user.nickname(), w.getPosition(), w.getStatus(),
                    w.getNotifiedAt(), w.getConfirmDeadline());
        }).toList();
        int count = (int) waits.stream().filter(w -> List.of("WAITING", "NOTIFIED").contains(w.getStatus())).count();
        return new WaitlistPageVO(count, list);
    }

    @Transactional
    public void confirmWaitlist(long activityId) {
        long userId = UserContext.require();
        ActivityWaitlist wait = waitlistMapper.selectOne(Wrappers.<ActivityWaitlist>lambdaQuery()
                .eq(ActivityWaitlist::getActivityId, activityId)
                .eq(ActivityWaitlist::getUserId, userId)
                .eq(ActivityWaitlist::getStatus, "NOTIFIED")
                .last("LIMIT 1"));
        if (wait == null) throw new BizException(ErrorCode.WAITLIST_CONFIRM_EXPIRED);
        if (wait.getConfirmDeadline() != null && LocalDateTime.now().isAfter(wait.getConfirmDeadline())) {
            wait.setStatus("EXPIRED");
            waitlistMapper.updateById(wait);
            notifyNextWaiter(activityId);
            throw new BizException(ErrorCode.WAITLIST_CONFIRM_EXPIRED);
        }
        ActivitySignup signup = new ActivitySignup();
        signup.setActivityId(activityId);
        signup.setUserId(userId);
        signup.setStatus("REGISTERED");
        signup.setSignupInfo(toJson(createSignupPayload(null)));
        signupMapper.insert(signup);
        wait.setStatus("PROMOTED");
        waitlistMapper.updateById(wait);
    }

    @Transactional
    public CheckinCodeVO generateCheckinCode(long activityId) {
        Activity activity = getActivityOrThrow(activityId);
        ensureOwner(activity, UserContext.require());
        backfillSignupCheckinTokens(activityId);
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        activity.setCheckinCode(code);
        activityMapper.updateById(activity);
        return new CheckinCodeVO(code);
    }

    @Transactional
    public void checkin(long activityId, CheckinReq req) {
        performCheckin(activityId, req, UserContext.require());
    }

    @Transactional
    public void publicCheckin(long activityId, CheckinReq req) {
        performCheckin(activityId, req, null);
    }

    public SummaryVO getSummary(long activityId) {
        Activity activity = getActivityOrThrow(activityId);
        ActivitySummary summary = summaryMapper.selectOne(Wrappers.<ActivitySummary>lambdaQuery()
                .eq(ActivitySummary::getActivityId, activityId)
                .last("LIMIT 1"));
        if (summary == null) throw new BizException(ErrorCode.NOT_FOUND);
        Long currentUserId = UserContext.get();
        boolean owner = currentUserId != null && Objects.equals(activity.getCreatorId(), currentUserId);
        if (!owner && !"PUBLISHED".equals(summary.getStatus())) throw new BizException(ErrorCode.NOT_FOUND);
        return toSummaryVO(summary, loadSummaryImages(activityId));
    }

    @Transactional
    public SummaryVO upsertSummary(long activityId, SummaryUpsertReq req) {
        Activity activity = getActivityOrThrow(activityId);
        long userId = UserContext.require();
        ensureOwner(activity, userId);
        ensureActivityEnded(activity);
        ActivitySummary summary = summaryMapper.selectOne(Wrappers.<ActivitySummary>lambdaQuery()
                .eq(ActivitySummary::getActivityId, activityId)
                .last("LIMIT 1"));
        if (summary == null) {
            summary = new ActivitySummary();
            summary.setActivityId(activityId);
            summary.setAuthorId(userId);
            summary.setContent(req == null ? null : req.content());
            summary.setStatus(Boolean.TRUE.equals(req == null ? null : req.publish()) ? "PUBLISHED" : "DRAFT");
            summaryMapper.insert(summary);
        } else {
            summary.setContent(req == null ? null : req.content());
            summary.setStatus(Boolean.TRUE.equals(req == null ? null : req.publish()) ? "PUBLISHED" : "DRAFT");
            summaryMapper.updateById(summary);
        }
        return toSummaryVO(summary, loadSummaryImages(activityId));
    }

    @Transactional
    public List<SummaryImageVO> uploadSummaryImages(long activityId, SummaryImageUploadReq req) {
        Activity activity = getActivityOrThrow(activityId);
        ensureOwner(activity, UserContext.require());
        ensureActivityEnded(activity);
        List<String> urls = req.imageUrls().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
        List<SummaryImageVO> result = new ArrayList<>();
        int i = 0;
        for (String url : urls) {
            ActivitySummaryImage image = new ActivitySummaryImage();
            image.setActivityId(activityId);
            image.setImageUrl(url);
            image.setAiCategory(inferImageCategory(url, i++, null));
            image.setConfirmed(Boolean.FALSE);
            summaryImageMapper.insert(image);
            result.add(toSummaryImageVO(image));
        }
        return result;
    }

    @Transactional
    public SummaryImageVO uploadSummaryImageFile(long activityId, MultipartFile file) {
        Activity activity = getActivityOrThrow(activityId);
        long userId = UserContext.require();
        ensureOwner(activity, userId);
        ensureActivityEnded(activity);
        ActivityImageStorageService.UploadResult upload = activityImageStorageService.uploadSummaryImage(activityId, userId, file);
        ActivitySummaryImage image = new ActivitySummaryImage();
        image.setActivityId(activityId);
        image.setImageUrl(upload.url());
        image.setAiCategory(activityAiService.classifyImage(upload.fileName(), upload.url()));
        image.setConfirmed(Boolean.FALSE);
        summaryImageMapper.insert(image);
        return toSummaryImageVO(image);
    }

    @Transactional
    public SummaryImageVO updateSummaryImage(long activityId, long imageId, SummaryImageUpdateReq req) {
        Activity activity = getActivityOrThrow(activityId);
        ensureOwner(activity, UserContext.require());
        ensureActivityEnded(activity);
        ActivitySummaryImage image = summaryImageMapper.selectById(imageId);
        if (image == null || !Objects.equals(image.getActivityId(), activityId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        image.setConfirmedCategory(normalizeImageCategory(req.category()));
        image.setConfirmed(Boolean.TRUE);
        summaryImageMapper.updateById(image);
        return toSummaryImageVO(image);
    }

    @Transactional
    public void deleteSummaryImage(long activityId, long imageId) {
        Activity activity = getActivityOrThrow(activityId);
        ensureOwner(activity, UserContext.require());
        ActivitySummaryImage image = summaryImageMapper.selectById(imageId);
        if (image == null || !Objects.equals(image.getActivityId(), activityId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        summaryImageMapper.deleteById(imageId);
        activityImageStorageService.deleteImageByUrl(image.getImageUrl());
    }

    public PageResult<ReviewVO> listReviews(long activityId, int page, int size) {
        getActivityOrThrow(activityId);
        Page<ActivityReview> p = new Page<>(page, size);
        reviewMapper.selectPage(p, Wrappers.<ActivityReview>lambdaQuery()
                .eq(ActivityReview::getActivityId, activityId)
                .and(w -> w.isNull(ActivityReview::getVisibleUntil).or().gt(ActivityReview::getVisibleUntil, LocalDateTime.now()))
                .orderByDesc(ActivityReview::getCreatedAt));
        Map<Long, UserBrief> users = loadUserBriefs(p.getRecords().stream().map(ActivityReview::getUserId).toList());
        List<ReviewVO> list = p.getRecords().stream().map(r -> {
            UserBrief user = users.get(r.getUserId());
            return new ReviewVO(r.getId(), r.getUserId(), user == null ? null : user.nickname(), r.getRating(), r.getContent(), r.getCreatedAt());
        }).toList();
        return new PageResult<>(p.getTotal(), page, size, list);
    }

    @Transactional
    public ReviewVO upsertReview(long activityId, ReviewUpsertReq req) {
        long userId = UserContext.require();
        Activity activity = getActivityOrThrow(activityId);
        ensureReviewWindowOpen(activity);
        boolean registered = signupMapper.selectCount(Wrappers.<ActivitySignup>lambdaQuery()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(ActivitySignup::getUserId, userId)
                .eq(ActivitySignup::getStatus, "REGISTERED")) > 0;
        boolean checkedIn = checkinMapper.selectCount(Wrappers.<ActivityCheckin>lambdaQuery()
                .eq(ActivityCheckin::getActivityId, activityId)
                .eq(ActivityCheckin::getUserId, userId)) > 0;
        if (!registered && !checkedIn) throw new BizException(ErrorCode.NOT_SIGNED_UP);

        ActivityReview review = reviewMapper.selectOne(Wrappers.<ActivityReview>lambdaQuery()
                .eq(ActivityReview::getActivityId, activityId)
                .eq(ActivityReview::getUserId, userId)
                .last("LIMIT 1"));
        LocalDateTime visibleUntil = activity.getEndTime().plusDays(7);
        if (review == null) {
            review = new ActivityReview();
            review.setActivityId(activityId);
            review.setUserId(userId);
            review.setRating(req.rating());
            review.setContent(req.content());
            review.setVisibleUntil(visibleUntil);
            reviewMapper.insert(review);
        } else {
            review.setRating(req.rating());
            review.setContent(req.content());
            review.setVisibleUntil(visibleUntil);
            reviewMapper.updateById(review);
        }
        review = reviewMapper.selectById(review.getId());
        UserBrief user = loadUserBriefs(List.of(userId)).get(userId);
        return new ReviewVO(review.getId(), userId, user == null ? null : user.nickname(), review.getRating(), review.getContent(), review.getCreatedAt());
    }

    private Activity getActivityOrThrow(long id) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null || activity.getDeletedAt() != null) throw new BizException(ErrorCode.NOT_FOUND);
        return activity;
    }

    private void ensureOwner(Activity activity, long userId) {
        if (!Objects.equals(activity.getCreatorId(), userId)) {
            throw new BizException(ErrorCode.ACTIVITY_NOT_OWNER);
        }
    }

    private void awardTeamPoints(long teamId, long userId, int points, String reason, long refId) {
        try {
            jdbcTemplate.update("INSERT INTO team_points_log(team_id, user_id, points, reason, ref_id) VALUES (?, ?, ?, ?, ?)", teamId, userId, points, reason, refId);
            jdbcTemplate.update("UPDATE team_member SET points = points + ? WHERE team_id = ? AND user_id = ?", points, teamId, userId);
        } catch (Exception ignored) {}
    }

    private void ensureReadable(Activity activity, Long userId) {
        if ("PUBLISHED".equals(activity.getStatus())) return;
        if (userId != null && Objects.equals(activity.getCreatorId(), userId)) return;
        // 队内活动：同小队成员可查看任何状态
        if (userId != null && activity.getTeamId() != null) {
            Long isMember = signupMapper.selectCount(
                Wrappers.<ActivitySignup>lambdaQuery()
                    .apply("EXISTS (SELECT 1 FROM team_member WHERE team_id = {0} AND user_id = {1})", activity.getTeamId(), userId)
                    .last("LIMIT 1"));
            // 上面的 hack 不对，直接用 userMapper 查
            Long memberCount = userMapper.selectCount(
                Wrappers.<cn.edu.buaa.quju.module.user.entity.User>lambdaQuery()
                    .apply("EXISTS (SELECT 1 FROM team_member WHERE team_id = {0} AND user_id = {1})", activity.getTeamId(), userId));
            if (memberCount != null && memberCount > 0) return;
        }
        throw new BizException(ErrorCode.NOT_FOUND);
    }

    private void ensureSignupOpen(Activity activity) {
        if (!"PUBLISHED".equals(activity.getStatus())) throw new BizException(ErrorCode.ACTIVITY_NOT_PUBLISHED);
        if (activity.getSignupDeadline() != null && LocalDateTime.now().isAfter(activity.getSignupDeadline())) {
            throw new BizException(ErrorCode.SIGNUP_DEADLINE_PASSED);
        }
    }

    private void ensureSignupChecks(long userId, SignupReq req) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getReputation() == null || user.getReputation() < 60) {
            throw new BizException(ErrorCode.SIGNUP_CHECK_FAILED, "信誉分低于报名要求");
        }
        if (user.getBirthday() == null) {
            throw new BizException(ErrorCode.SIGNUP_CHECK_FAILED, "请先完善生日信息后再报名");
        }
        int age = calcAge(user.getBirthday());
        if (age < 16) {
            throw new BizException(ErrorCode.SIGNUP_CHECK_FAILED, "当前活动仅支持 16 岁及以上用户报名");
        }
        if (req == null || !Boolean.TRUE.equals(req.safetyConfirmed())) {
            throw new BizException(ErrorCode.SIGNUP_CHECK_FAILED, "请先确认安全须知");
        }
    }

    private void ensureReviewWindowOpen(Activity activity) {
        if (activity.getEndTime() == null || LocalDateTime.now().isBefore(activity.getEndTime())) {
            throw new BizException(ErrorCode.REVIEW_WINDOW_CLOSED);
        }
        if (LocalDateTime.now().isAfter(activity.getEndTime().plusDays(7))) {
            throw new BizException(ErrorCode.REVIEW_WINDOW_CLOSED);
        }
    }

    private void fillActivity(Activity activity, ActivityUpsertReq req) {
        activity.setName(req.name());
        activity.setIntro(req.intro());
        activity.setCategory(normalizeCategory(req.category()));
        activity.setCoverImage(req.coverImage());
        activity.setStartTime(req.startTime());
        activity.setEndTime(req.endTime());
        activity.setSignupDeadline(req.signupDeadline());
        activity.setCity(req.city());
        activity.setAddress(req.address());
        activity.setLng(req.lng());
        activity.setLat(req.lat());
        activity.setCapacity(req.capacity());
        activity.setFee(req.fee() == null ? BigDecimal.ZERO : req.fee());
        activity.setTeamId(req.teamId());
    }

    private void replaceTags(Long activityId, List<String> tags) {
        activityTagMapper.delete(Wrappers.<ActivityTag>lambdaQuery().eq(ActivityTag::getActivityId, activityId));
        if (tags == null) return;
        tags.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).distinct().forEach(tag -> {
            ActivityTag entity = new ActivityTag();
            entity.setActivityId(activityId);
            entity.setTag(tag);
            activityTagMapper.insert(entity);
        });
    }

    private List<ActivityVO> toActivityVOList(List<Activity> activities) {
        List<Long> ids = activities.stream().map(Activity::getId).toList();
        Map<Long, List<String>> tags = getTags(ids);
        Map<Long, UserBrief> creators = loadCreators(activities);
        Map<Long, Integer> signupCounts = getSignupCounts(ids);
        return activities.stream().map(a -> toActivityVO(a, tags, creators, signupCounts)).toList();
    }

    private ActivityVO toActivityVO(Activity a, Map<Long, List<String>> tags, Map<Long, UserBrief> creators, Map<Long, Integer> signupCounts) {
        return new ActivityVO(a.getId(), a.getName(), a.getIntro(), a.getCategory(),
                tags.getOrDefault(a.getId(), Collections.emptyList()), a.getCoverImage(), a.getStartTime(),
                a.getEndTime(), a.getSignupDeadline(), a.getCity(), a.getAddress(), a.getLng(), a.getLat(),
                a.getCapacity(), a.getFee(), a.getStatus(), calcPhase(a), signupCounts.getOrDefault(a.getId(), 0),
                creators.get(a.getCreatorId()), a.getTeamId());
    }

    private Map<Long, List<String>> getTags(List<Long> activityIds) {
        if (activityIds == null || activityIds.isEmpty()) return Collections.emptyMap();
        List<ActivityTag> tags = activityTagMapper.selectList(Wrappers.<ActivityTag>lambdaQuery()
                .in(ActivityTag::getActivityId, activityIds)
                .orderByAsc(ActivityTag::getId));
        return tags.stream().collect(Collectors.groupingBy(ActivityTag::getActivityId,
                Collectors.mapping(ActivityTag::getTag, Collectors.toList())));
    }

    private Map<Long, UserBrief> loadCreators(List<Activity> activities) {
        return loadUserBriefs(activities.stream().map(Activity::getCreatorId).filter(Objects::nonNull).distinct().toList());
    }

    private Map<Long, UserBrief> loadUserBriefs(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyMap();
        List<User> users = userMapper.selectList(new QueryWrapper<User>().in("id", userIds));
        Map<Long, UserBrief> map = new HashMap<>();
        for (User user : users) {
            map.put(user.getId(), new UserBrief(user.getId(), user.getAccountId(), user.getNickname(), user.getAvatar(), user.getUserType(), user.getStatus()));
        }
        return map;
    }

    private Map<Long, Integer> getSignupCounts(List<Long> activityIds) {
        if (activityIds == null || activityIds.isEmpty()) return Collections.emptyMap();
        Map<Long, Integer> result = new HashMap<>();
        for (ActivitySignup signup : signupMapper.selectList(Wrappers.<ActivitySignup>lambdaQuery()
                .in(ActivitySignup::getActivityId, activityIds)
                .eq(ActivitySignup::getStatus, "REGISTERED"))) {
            result.merge(signup.getActivityId(), 1, Integer::sum);
        }
        return result;
    }

    private Map<Long, Integer> getWaitlistCounts(List<Long> activityIds) {
        if (activityIds == null || activityIds.isEmpty()) return Collections.emptyMap();
        Map<Long, Integer> result = new HashMap<>();
        for (ActivityWaitlist wait : waitlistMapper.selectList(Wrappers.<ActivityWaitlist>lambdaQuery()
                .in(ActivityWaitlist::getActivityId, activityIds)
                .in(ActivityWaitlist::getStatus, List.of("WAITING", "NOTIFIED")))) {
            result.merge(wait.getActivityId(), 1, Integer::sum);
        }
        return result;
    }

    private String resolveMySignupStatus(long activityId, Long userId) {
        if (userId == null) return null;
        ActivitySignup signup = signupMapper.selectOne(Wrappers.<ActivitySignup>lambdaQuery()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(ActivitySignup::getUserId, userId)
                .eq(ActivitySignup::getStatus, "REGISTERED")
                .last("LIMIT 1"));
        if (signup != null) return "REGISTERED";
        ActivityWaitlist wait = waitlistMapper.selectOne(Wrappers.<ActivityWaitlist>lambdaQuery()
                .eq(ActivityWaitlist::getActivityId, activityId)
                .eq(ActivityWaitlist::getUserId, userId)
                .in(ActivityWaitlist::getStatus, List.of("WAITING", "NOTIFIED"))
                .last("LIMIT 1"));
        return wait == null ? null : "WAITLISTED";
    }

    private int countRegistered(long activityId) {
        Long count = signupMapper.selectCount(Wrappers.<ActivitySignup>lambdaQuery()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(ActivitySignup::getStatus, "REGISTERED"));
        return count == null ? 0 : count.intValue();
    }

    private int nextWaitlistPosition(long activityId) {
        List<ActivityWaitlist> waits = waitlistMapper.selectList(Wrappers.<ActivityWaitlist>lambdaQuery()
                .eq(ActivityWaitlist::getActivityId, activityId)
                .orderByDesc(ActivityWaitlist::getPosition)
                .last("LIMIT 1"));
        if (waits.isEmpty() || waits.get(0).getPosition() == null) return 1;
        return waits.get(0).getPosition() + 1;
    }

    private void notifyNextWaiter(long activityId) {
        List<ActivityWaitlist> waits = waitlistMapper.selectList(Wrappers.<ActivityWaitlist>lambdaQuery()
                .eq(ActivityWaitlist::getActivityId, activityId)
                .eq(ActivityWaitlist::getStatus, "WAITING")
                .orderByAsc(ActivityWaitlist::getPosition)
                .last("LIMIT 1"));
        if (waits.isEmpty()) return;
        ActivityWaitlist next = waits.get(0);
        next.setStatus("NOTIFIED");
        next.setNotifiedAt(LocalDateTime.now());
        next.setConfirmDeadline(LocalDateTime.now().plusHours(1));
        waitlistMapper.updateById(next);
        User user = userMapper.selectById(next.getUserId());
        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            emailService.send(user.getEmail(), "【趣聚】候补名额已释放",
                    "你报名的活动已有空位，请在 1 小时内登录趣聚确认报名。活动ID: " + activityId);
        }
    }

    private String calcPhase(Activity a) {
        LocalDateTime now = LocalDateTime.now();
        if (a.getStatus() != null && ("CANCELLED".equals(a.getStatus()) || "TAKEN_DOWN".equals(a.getStatus()))) {
            return "ENDED";
        }
        if (a.getStartTime() == null) return "NOT_STARTED";
        if (a.getEndTime() != null && now.isAfter(a.getEndTime())) return "ENDED";
        if (!now.isBefore(a.getStartTime())) return "ONGOING";
        if (a.getSignupDeadline() != null && !now.isAfter(a.getSignupDeadline())) return "SIGNUP_OPEN";
        if (a.getSignupDeadline() != null && now.isAfter(a.getSignupDeadline())) return "SIGNUP_CLOSED";
        return "NOT_STARTED";
    }

    private List<SummaryImageVO> loadSummaryImages(long activityId) {
        return summaryImageMapper.selectList(Wrappers.<ActivitySummaryImage>lambdaQuery()
                        .eq(ActivitySummaryImage::getActivityId, activityId)
                        .orderByAsc(ActivitySummaryImage::getId))
                .stream()
                .map(this::toSummaryImageVO)
                .toList();
    }

    private SummaryVO toSummaryVO(ActivitySummary summary, List<SummaryImageVO> images) {
        return new SummaryVO(summary.getId(), summary.getActivityId(), summary.getAuthorId(),
                summary.getContent(), summary.getStatus(), images);
    }

    private SummaryImageVO toSummaryImageVO(ActivitySummaryImage image) {
        return new SummaryImageVO(image.getId(), image.getImageUrl(), image.getAiCategory(),
                image.getConfirmedCategory(), image.getConfirmed());
    }

    private String inferImageCategory(String url, int index, String originalName) {
        String aiCategory = activityAiService.classifyImage(originalName, url);
        if (aiCategory != null && !aiCategory.isBlank()) return aiCategory;
        String lower = url == null ? "" : url.toLowerCase();
        if (lower.contains("group") || lower.contains("team") || lower.contains("heying")) return "GROUP_PHOTO";
        if (lower.contains("venue") || lower.contains("site")) return "VENUE";
        if (lower.contains("material") || lower.contains("equip")) return "MATERIAL";
        if (lower.contains("result") || lower.contains("award")) return "RESULT";
        return switch (index % 5) {
            case 0 -> "PROCESS";
            case 1 -> "GROUP_PHOTO";
            case 2 -> "VENUE";
            case 3 -> "MATERIAL";
            default -> "RESULT";
        };
    }

    private void ensureActivityEnded(Activity activity) {
        if (activity.getEndTime() == null || LocalDateTime.now().isBefore(activity.getEndTime())) {
            throw new BizException(ErrorCode.CONFLICT, "活动结束后才能发布总结");
        }
    }

    private void applySubmissionDecision(Activity activity) {
        ActivityAiService.AuditDecision decision = activityAiService.auditActivity(buildAuditPayload(activity));
        if ((activity.getCapacity() != null && activity.getCapacity() > 50) || "TO_MANUAL".equals(decision.result())) {
            activity.setStatus("PENDING_REVIEW");
            activityMapper.updateById(activity);
            logAudit(activity.getId(), "AI", "TO_MANUAL", decision.reason(), null);
            return;
        }
        if ("REJECTED".equals(decision.result())) {
            activity.setStatus("REJECTED");
            activityMapper.updateById(activity);
            logAudit(activity.getId(), "AI", "REJECTED", decision.reason(), null);
            return;
        }
        activity.setStatus("PUBLISHED");
        activityMapper.updateById(activity);
        logAudit(activity.getId(), "AI", "PASSED", decision.reason(), null);
    }

    private void logAudit(Long activityId, String auditType, String result, String reason, Long adminId) {
        ActivityAuditLog log = new ActivityAuditLog();
        log.setActivityId(activityId);
        log.setAuditType(auditType);
        log.setResult(result);
        log.setReason(reason);
        log.setAuditorAdminId(adminId);
        activityAuditLogMapper.insert(log);
    }

    private String buildAuditPayload(Activity activity) {
        return "标题:" + blank(activity.getName())
                + "; 简介:" + blank(activity.getIntro())
                + "; 分类:" + blank(activity.getCategory())
                + "; 城市:" + blank(activity.getCity())
                + "; 地址:" + blank(activity.getAddress())
                + "; 人数:" + activity.getCapacity()
                + "; 费用:" + activity.getFee()
                + "; 标签:" + String.join(",", getTags(List.of(activity.getId())).getOrDefault(activity.getId(), List.of()));
    }

    private LocalDateTime parseAiDateTime(String value, LocalDateTime fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }

    private void performCheckin(long activityId, CheckinReq req, Long actorUserId) {
        Activity activity = getActivityOrThrow(activityId);
        Long targetUserId = resolveCheckinTargetUserId(activity, req == null ? null : req.code(), actorUserId);
        if (targetUserId == null) {
            throw new BizException(ErrorCode.CHECKIN_CODE_INVALID);
        }
        ActivitySignup signup = findRegisteredSignup(activityId, targetUserId);
        if (signup == null) throw new BizException(ErrorCode.NOT_SIGNED_UP);
        if (activity.getLng() != null && activity.getLat() != null) {
            if (req.lng() == null || req.lat() == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "签到需要获取当前位置");
            }
            BigDecimal distance = distanceKm(activity.getLat(), activity.getLng(), req.lat(), req.lng());
            if (distance.compareTo(CHECKIN_RADIUS_KM) > 0) {
                throw new BizException(ErrorCode.CHECKIN_LOCATION_TOO_FAR);
            }
        }
        ActivityCheckin existing = checkinMapper.selectOne(Wrappers.<ActivityCheckin>lambdaQuery()
                .eq(ActivityCheckin::getActivityId, activityId)
                .eq(ActivityCheckin::getUserId, targetUserId)
                .last("LIMIT 1"));
        if (existing != null) throw new BizException(ErrorCode.CONFLICT, "已签到");
        ActivityCheckin checkin = new ActivityCheckin();
        checkin.setActivityId(activityId);
        checkin.setUserId(targetUserId);
        checkin.setLng(req.lng());
        checkin.setLat(req.lat());
        checkinMapper.insert(checkin);
        // 队内活动签到加积分
        Activity act = activityMapper.selectById(activityId);
        if (act != null && act.getTeamId() != null) {
            awardTeamPoints(act.getTeamId(), targetUserId, 5, "CHECKIN_ACTIVITY", activityId);
        }
    }

    private String exposeCheckinCode(Activity activity, String mySignupStatus, Long viewerId) {
        if (activity.getCheckinCode() == null || viewerId == null) return null;
        if (Objects.equals(activity.getCreatorId(), viewerId)) {
            return activity.getCheckinCode();
        }
        if (!"REGISTERED".equals(mySignupStatus)) return null;
        ActivitySignup signup = findRegisteredSignup(activity.getId(), viewerId);
        String signupToken = ensureSignupCheckinToken(signup);
        if (signupToken == null || signupToken.isBlank()) return null;
        return buildUniqueCheckinCode(activity.getId(), activity.getCheckinCode(), signupToken);
    }

    private ActivitySignup findRegisteredSignup(long activityId, long userId) {
        return signupMapper.selectOne(Wrappers.<ActivitySignup>lambdaQuery()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(ActivitySignup::getUserId, userId)
                .eq(ActivitySignup::getStatus, "REGISTERED")
                .last("LIMIT 1"));
    }

    private Long resolveCheckinTargetUserId(Activity activity, String code, Long actorUserId) {
        if (code == null || code.isBlank() || activity.getCheckinCode() == null) return null;
        String trimmed = code.trim();
        if (activity.getCheckinCode().equals(trimmed)) {
            if (actorUserId == null) return null;
            ActivitySignup actorSignup = findRegisteredSignup(activity.getId(), actorUserId);
            return actorSignup == null ? null : actorUserId;
        }
        ParsedCheckinCode parsed = parseCheckinCode(trimmed);
        if (parsed == null) return null;
        if (parsed.activityId() != activity.getId()) return null;
        if (!Objects.equals(parsed.sessionToken(), activity.getCheckinCode())) return null;
        ActivitySignup signup = findSignupByCheckinToken(activity.getId(), parsed.signupToken());
        return signup == null ? null : signup.getUserId();
    }

    private ActivitySignup findSignupByCheckinToken(long activityId, String signupToken) {
        if (signupToken == null || signupToken.isBlank()) return null;
        List<ActivitySignup> signups = signupMapper.selectList(Wrappers.<ActivitySignup>lambdaQuery()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(ActivitySignup::getStatus, "REGISTERED"));
        for (ActivitySignup signup : signups) {
            if (signupToken.equals(extractSignupCheckinToken(signup))) {
                return signup;
            }
        }
        return null;
    }

    private String extractSignupCheckinToken(ActivitySignup signup) {
        if (signup == null || signup.getSignupInfo() == null || signup.getSignupInfo().isBlank()) return null;
        try {
            Map<?, ?> payload = objectMapper.readValue(signup.getSignupInfo(), Map.class);
            Object token = payload.get("checkinToken");
            return token == null ? null : token.toString();
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String buildUniqueCheckinCode(long activityId, String sessionToken, String signupToken) {
        return "quju-checkin:" + activityId + ":" + sessionToken + ":" + signupToken;
    }
    private void backfillSignupCheckinTokens(long activityId) {
        List<ActivitySignup> signups = signupMapper.selectList(Wrappers.<ActivitySignup>lambdaQuery()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(ActivitySignup::getStatus, "REGISTERED"));
        for (ActivitySignup signup : signups) {
            ensureSignupCheckinToken(signup);
        }
    }

    private String ensureSignupCheckinToken(ActivitySignup signup) {
        if (signup == null) return null;
        String existingToken = extractSignupCheckinToken(signup);
        if (existingToken != null && !existingToken.isBlank()) return existingToken;
        Map<String, Object> payload = createSignupPayload(parseSignupInfo(signup.getSignupInfo()));
        String token = String.valueOf(payload.get("checkinToken"));
        signup.setSignupInfo(toJson(payload));
        signupMapper.updateById(signup);
        return token;
    }

    private SignupReq parseSignupInfo(String rawSignupInfo) {
        if (rawSignupInfo == null || rawSignupInfo.isBlank()) {
            return new SignupReq(new HashMap<>(), true);
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(rawSignupInfo, Map.class);
            Object safetyConfirmed = payload.get("safetyConfirmed");
            return new SignupReq(payload, safetyConfirmed == null || Boolean.parseBoolean(String.valueOf(safetyConfirmed)));
        } catch (JsonProcessingException e) {
            return new SignupReq(new HashMap<>(), true);
        }
    }


    private ParsedCheckinCode parseCheckinCode(String raw) {
        if (raw == null) return null;
        String[] parts = raw.trim().split(":");
        if (parts.length != 4) return null;
        if (!"quju-checkin".equals(parts[0])) return null;
        try {
            return new ParsedCheckinCode(Long.parseLong(parts[1]), parts[2], parts[3]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record ParsedCheckinCode(long activityId, String sessionToken, String signupToken) {}

    private int calcAge(LocalDate birthday) {
        return birthday == null ? 0 : java.time.Period.between(birthday, LocalDate.now()).getYears();
    }

    private Map<String, Object> mergeSignupInfo(SignupReq req) {
        Map<String, Object> source = req == null || req.signupInfo() == null ? new HashMap<>() : new HashMap<>(req.signupInfo());
        source.put("safetyConfirmed", true);
        return source;
    }

    private Map<String, Object> createSignupPayload(SignupReq req) {
        Map<String, Object> payload = mergeSignupInfo(req);
        Object existingToken = payload.get("checkinToken");
        if (!(existingToken instanceof String token) || token.isBlank()) {
            payload.put("checkinToken", createCheckinToken());
        }
        return payload;
    }

    private String createCheckinToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        return text != null && keyword != null && text.toLowerCase().contains(keyword.toLowerCase());
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }

    private String normalizeImageCategory(String category) {
        if (category == null) throw new BizException(ErrorCode.BAD_REQUEST, "图片分类不能为空");
        String normalized = category.trim().toUpperCase();
        if (!SUMMARY_IMAGE_CATEGORIES.contains(normalized)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "图片分类非法");
        }
        return normalized;
    }

    private List<String> normalizeCategories(String category, String categories) {
        Set<String> values = new HashSet<>();
        if (category != null && !category.isBlank()) values.add(normalizeCategory(category));
        if (categories != null && !categories.isBlank()) {
            for (String item : categories.split(",")) {
                if (!item.isBlank()) values.add(normalizeCategory(item));
            }
        }
        return new ArrayList<>(values);
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return "OTHER";
        String normalized = category.trim().toUpperCase();
        return switch (normalized) {
            case "SPORTS", "HIKING", "BOARD_GAME", "STUDY", "CHARITY", "CITY_WALK", "OTHER" -> normalized;
            default -> "OTHER";
        };
    }

    private void sortActivities(List<Activity> activities, String tab, Map<Long, BigDecimal> distanceMap) {
        Comparator<Activity> comparator;
        if ("NEARBY".equalsIgnoreCase(tab)) {
            comparator = Comparator.comparing((Activity a) -> distanceMap.getOrDefault(a.getId(), new BigDecimal("99999")))
                    .thenComparing(Activity::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()));
        } else if ("RECOMMEND".equalsIgnoreCase(tab)) {
            comparator = Comparator.comparing((Activity a) -> !"PUBLISHED".equals(a.getStatus()))
                    .thenComparing(Activity::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Activity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        } else {
            comparator = Comparator.comparing(Activity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        activities.sort(comparator);
    }

    private PageResult<ActivityVO> paginateActivities(List<Activity> activities, int page, int size) {
        int from = (int) Math.max(0, (page - 1) * size);
        int to = (int) Math.min(activities.size(), from + size);
        List<Activity> sliced = from >= activities.size() ? Collections.emptyList() : activities.subList(from, to);
        return new PageResult<>(activities.size(), page, size, toActivityVOList(sliced));
    }

    private BigDecimal distanceKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        double earthRadius = 6371.0;
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLng = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1.doubleValue())) * Math.cos(Math.toRadians(lat2.doubleValue()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return BigDecimal.valueOf(earthRadius * c).setScale(2, RoundingMode.HALF_UP);
    }

    private String buildAiIntro(String theme, String category) {
        return switch (category) {
            case "SPORTS" -> "围绕" + theme + "安排热身、分组和赛后交流，兼顾新手参与体验与基础强度控制。";
            case "HIKING" -> "围绕" + theme + "设计轻量徒步路线、集合点与补给节奏，兼顾安全提示和节奏管理。";
            case "BOARD_GAME" -> "围绕" + theme + "安排破冰、桌游轮换与复盘交流，适合小组快速建立互动。";
            case "STUDY" -> "围绕" + theme + "组织主题分享、分组讨论与自由交流，兼顾输入和输出。";
            case "CHARITY" -> "围绕" + theme + "安排分工、物资准备与现场协同，突出参与感和执行节奏。";
            case "CITY_WALK" -> "围绕" + theme + "设计夜游/步行路线、打卡点与拍照节奏，适合轻社交和城市探索。";
            default -> "围绕" + theme + "生成一版活动初稿，包含时间安排、参与方式和现场协同建议。";
        };
    }

    private List<String> suggestTags(String theme, String category) {
        List<String> tags = new ArrayList<>();
        tags.add(categoryLabel(category));
        tags.add(theme.length() > 10 ? theme.substring(0, 10) : theme);
        if ("SPORTS".equals(category)) tags.add("轻运动");
        if ("CITY_WALK".equals(category)) tags.add("城市探索");
        if ("STUDY".equals(category)) tags.add("主题交流");
        return tags.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
    }

    private Integer defaultCapacityForCategory(String category) {
        return switch (category) {
            case "SPORTS" -> 16;
            case "HIKING" -> 20;
            case "BOARD_GAME" -> 10;
            case "STUDY" -> 30;
            case "CHARITY" -> 25;
            case "CITY_WALK" -> 18;
            default -> 20;
        };
    }

    private BigDecimal defaultFeeForCategory(String category) {
        return switch (category) {
            case "SPORTS" -> new BigDecimal("25.00");
            case "BOARD_GAME" -> new BigDecimal("39.00");
            case "CITY_WALK" -> new BigDecimal("0.00");
            default -> BigDecimal.ZERO;
        };
    }

    private String categoryLabel(String category) {
        return switch (category) {
            case "SPORTS" -> "运动健身";
            case "HIKING" -> "户外徒步";
            case "BOARD_GAME" -> "桌游聚会";
            case "STUDY" -> "学习交流";
            case "CHARITY" -> "公益活动";
            case "CITY_WALK" -> "城市探索";
            default -> "活动";
        };
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null) return null;
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "signupInfo 格式错误");
        }
    }
}
