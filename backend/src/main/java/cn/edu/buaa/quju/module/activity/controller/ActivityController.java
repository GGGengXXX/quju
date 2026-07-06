package cn.edu.buaa.quju.module.activity.controller;

import cn.edu.buaa.quju.common.PageResult;
import cn.edu.buaa.quju.common.R;
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
import cn.edu.buaa.quju.module.activity.service.ActivityService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/v1")
public class ActivityController {
    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("/activity-templates")
    public R<List<TemplateVO>> templates() {
        return R.ok(activityService.listTemplates());
    }

    @GetMapping("/activities")
    public R<PageResult<ActivityVO>> discover(@RequestParam(required = false) String tab,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String category,
                                              @RequestParam(required = false) String categories,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String phase,
                                              @RequestParam(required = false) String city,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startFrom,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTo,
                                              @RequestParam(required = false) BigDecimal feeMin,
                                              @RequestParam(required = false) BigDecimal feeMax,
                                              @RequestParam(required = false) BigDecimal lng,
                                              @RequestParam(required = false) BigDecimal lat,
                                              @RequestParam(required = false) BigDecimal distanceKm,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return R.ok(activityService.discover(tab, keyword, category, categories, status, phase, city,
                startFrom, startTo, feeMin, feeMax, lng, lat, distanceKm, page, size));
    }

    @PostMapping("/activities")
    public R<ActivityVO> create(@RequestBody @Valid ActivityUpsertReq req) {
        return R.ok(activityService.create(req));
    }

    @GetMapping("/activities/mine")
    public R<PageResult<ActivityVO>> mine(@RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        return R.ok(activityService.myActivities(status, page, size));
    }

    @GetMapping("/activities/map")
    public R<List<ActivityPointVO>> map(@RequestParam(required = false) BigDecimal minLng,
                                        @RequestParam(required = false) BigDecimal maxLng,
                                        @RequestParam(required = false) BigDecimal minLat,
                                        @RequestParam(required = false) BigDecimal maxLat) {
        return R.ok(activityService.mapPoints(minLng, maxLng, minLat, maxLat));
    }

    @PostMapping("/activities/ai-plan")
    public R<ActivityVO> aiPlan(@RequestBody(required = false) AiPlanReq req) {
        return R.ok(activityService.aiPlan(req));
    }

    @GetMapping("/activities/{id}")
    public R<ActivityDetailVO> detail(@PathVariable long id) {
        return R.ok(activityService.getDetail(id));
    }

    @PutMapping("/activities/{id}")
    public R<ActivityVO> update(@PathVariable long id, @RequestBody @Valid ActivityUpsertReq req) {
        return R.ok(activityService.update(id, req));
    }

    @DeleteMapping("/activities/{id}")
    public R<Void> delete(@PathVariable long id) {
        activityService.deleteActivity(id);
        return R.ok(null);
    }

    @PostMapping("/activities/{id}/submit")
    public R<Void> submit(@PathVariable long id) {
        activityService.submit(id);
        return R.ok(null);
    }

    @PostMapping("/activities/{id}/clone")
    public R<ActivityVO> cloneActivity(@PathVariable long id) {
        return R.ok(activityService.cloneActivity(id));
    }

    @PostMapping("/activities/{id}/signup")
    public R<SignupResultVO> signup(@PathVariable long id, @RequestBody(required = false) SignupReq req) {
        return R.ok(activityService.signup(id, req));
    }

    @DeleteMapping("/activities/{id}/signup")
    public R<Void> cancelSignup(@PathVariable long id) {
        activityService.cancelSignup(id);
        return R.ok(null);
    }

    @GetMapping("/activities/{id}/signups")
    public R<PageResult<SignupManageVO>> signups(@PathVariable long id,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return R.ok(activityService.listSignups(id, page, size));
    }

    @GetMapping("/activities/{id}/waitlist")
    public R<WaitlistPageVO> waitlist(@PathVariable long id) {
        return R.ok(activityService.getWaitlist(id));
    }

    @PostMapping("/activities/{id}/waitlist/confirm")
    public R<Void> confirmWaitlist(@PathVariable long id) {
        activityService.confirmWaitlist(id);
        return R.ok(null);
    }

    @PostMapping("/activities/{id}/checkin-code")
    public R<CheckinCodeVO> checkinCode(@PathVariable long id) {
        return R.ok(activityService.generateCheckinCode(id));
    }

    @PostMapping("/activities/{id}/checkin")
    public R<Void> checkin(@PathVariable long id, @RequestBody @Valid CheckinReq req) {
        activityService.checkin(id, req);
        return R.ok(null);
    }

    @PostMapping("/public/activities/{id}/checkin")
    public R<Void> publicCheckin(@PathVariable long id, @RequestBody @Valid CheckinReq req) {
        activityService.publicCheckin(id, req);
        return R.ok(null);
    }

    @GetMapping("/activities/{id}/summary")
    public R<SummaryVO> summary(@PathVariable long id) {
        return R.ok(activityService.getSummary(id));
    }

    @PostMapping("/activities/{id}/summary")
    public R<SummaryVO> upsertSummary(@PathVariable long id, @RequestBody(required = false) SummaryUpsertReq req) {
        return R.ok(activityService.upsertSummary(id, req));
    }

    @PostMapping("/activities/{id}/summary/images")
    public R<List<SummaryImageVO>> uploadSummaryImages(@PathVariable long id,
                                                       @RequestBody @Valid SummaryImageUploadReq req) {
        return R.ok(activityService.uploadSummaryImages(id, req));
    }

    @PostMapping(value = "/activities/{id}/summary/images/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<SummaryImageVO> uploadSummaryImageFile(@PathVariable long id,
                                                    @RequestPart("file") MultipartFile file) {
        return R.ok(activityService.uploadSummaryImageFile(id, file));
    }

    @PutMapping("/activities/{id}/summary/images/{imageId}")
    public R<SummaryImageVO> updateSummaryImage(@PathVariable long id,
                                                @PathVariable long imageId,
                                                @RequestBody @Valid SummaryImageUpdateReq req) {
        return R.ok(activityService.updateSummaryImage(id, imageId, req));
    }

    @DeleteMapping("/activities/{id}/summary/images/{imageId}")
    public R<Void> deleteSummaryImage(@PathVariable long id,
                                      @PathVariable long imageId) {
        activityService.deleteSummaryImage(id, imageId);
        return R.ok(null);
    }

    @GetMapping("/activities/{id}/reviews")
    public R<PageResult<ReviewVO>> reviews(@PathVariable long id,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return R.ok(activityService.listReviews(id, page, size));
    }

    @PostMapping("/activities/{id}/reviews")
    public R<ReviewVO> createReview(@PathVariable long id, @RequestBody @Valid ReviewUpsertReq req) {
        return R.ok(activityService.upsertReview(id, req));
    }
}
