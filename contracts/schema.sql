-- =====================================================================
-- 趣聚 QuJu 数据库 Schema (MySQL 8.4, utf8mb4) —— 权威 DDL / 契约
-- 唯一事实来源。改动须先写 ADR + 平台核心组评审。可作为 Flyway V1 基线。
-- 约定:
--   - 主键 bigint unsigned 自增; 表/列 snake_case;
--   - 业务表含 created_at/updated_at; 需保留历史的表加 deleted_at(软删, 查询默认过滤);
--   - 时间统一存 UTC(TIMESTAMP); 枚举存大写字符串(见 enums.md);
--   - 逻辑外键(应用层保证一致性) + 索引; 不建物理 FK 约束(避免迁移顺序/删除耦合);
--   - 金额 decimal(10,2); 经纬度 decimal(10,7)(高德 GCJ-02); 附近查询用 bounding-box + ST_Distance_Sphere。
-- =====================================================================
SET NAMES utf8mb4;

-- ============================== 一、用户 / 账号 ==============================

CREATE TABLE IF NOT EXISTS `user` (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  account_id    VARCHAR(32)   DEFAULT NULL                   COMMENT '趣聚号(唯一，注册自动生成，可修改)',
  email         VARCHAR(128)  NOT NULL,
  password_hash VARCHAR(100)  NOT NULL                       COMMENT 'BCrypt',
  user_type     VARCHAR(16)   NOT NULL                       COMMENT 'INDIVIDUAL|MERCHANT',
  status        VARCHAR(24)   NOT NULL DEFAULT 'PENDING_ACTIVATION' COMMENT 'PENDING_ACTIVATION|ACTIVE|BANNED',
  nickname      VARCHAR(32)   DEFAULT NULL                   COMMENT '全平台唯一',
  avatar        VARCHAR(255)  DEFAULT NULL,
  gender        VARCHAR(8)    NOT NULL DEFAULT 'UNKNOWN'      COMMENT 'MALE|FEMALE|UNKNOWN',
  birthday      DATE          DEFAULT NULL,
  signature     VARCHAR(140)  DEFAULT NULL,
  reputation    INT           NOT NULL DEFAULT 100           COMMENT '信誉分(报名校验用)',
  created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at    TIMESTAMP     NULL DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_email (email),
  UNIQUE KEY uk_user_account_id (account_id),
  UNIQUE KEY uk_user_nickname (nickname),
  KEY idx_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户(个人/商家)';

CREATE TABLE IF NOT EXISTS user_interest_tag (
  id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  tag     VARCHAR(32) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_tag (user_id, tag),
  KEY idx_uit_tag (tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户兴趣标签';

CREATE TABLE IF NOT EXISTS merchant_profile (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id        BIGINT UNSIGNED NOT NULL,
  merchant_name  VARCHAR(64)  NOT NULL,
  nickname       VARCHAR(32)  DEFAULT NULL,
  focus_fields   VARCHAR(255) DEFAULT NULL                   COMMENT '关注活动领域',
  license_url    VARCHAR(255) DEFAULT NULL                   COMMENT '营业执照/凭证',
  audit_status   VARCHAR(16)  NOT NULL DEFAULT 'PENDING'     COMMENT 'PENDING|APPROVED|REJECTED',
  audit_reason   VARCHAR(255) DEFAULT NULL,
  audit_admin_id BIGINT UNSIGNED DEFAULT NULL,
  created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_merchant_user (user_id),
  KEY idx_merchant_audit (audit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家资料+审核';

CREATE TABLE IF NOT EXISTS email_token (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    BIGINT UNSIGNED NOT NULL,
  token      VARCHAR(64)  NOT NULL,
  type       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVATION'      COMMENT 'ACTIVATION|RESET_PASSWORD',
  expires_at TIMESTAMP    NOT NULL,
  used       TINYINT(1)   NOT NULL DEFAULT 0,
  created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_email_token (token),
  KEY idx_email_token_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮箱激活/重置令牌';

CREATE TABLE IF NOT EXISTS admin (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username      VARCHAR(32)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台管理员(系统预置)';

CREATE TABLE IF NOT EXISTS user_ban (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id           BIGINT UNSIGNED NOT NULL,
  reason            VARCHAR(255) NOT NULL,
  ban_until         TIMESTAMP    NULL DEFAULT NULL           COMMENT 'NULL=永久',
  status            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'   COMMENT 'ACTIVE|LIFTED',
  operator_admin_id BIGINT UNSIGNED NOT NULL,
  lifted_at         TIMESTAMP    NULL DEFAULT NULL,
  created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ban_user (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='封禁记录(原因+期限)';

-- ============================== 二、活动 ==============================

CREATE TABLE IF NOT EXISTS activity (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  creator_id      BIGINT UNSIGNED NOT NULL,
  team_id         BIGINT UNSIGNED DEFAULT NULL               COMMENT '非空=队内活动',
  name            VARCHAR(80)  NOT NULL,
  intro           TEXT         DEFAULT NULL,
  category        VARCHAR(24)  NOT NULL DEFAULT 'OTHER'      COMMENT 'SPORTS|HIKING|BOARD_GAME|STUDY|CHARITY|CITY_WALK|OTHER',
  cover_image     VARCHAR(255) DEFAULT NULL,
  start_time      TIMESTAMP    NULL DEFAULT NULL,
  end_time        TIMESTAMP    NULL DEFAULT NULL,
  signup_deadline TIMESTAMP    NULL DEFAULT NULL,
  city            VARCHAR(32)  DEFAULT NULL,
  address         VARCHAR(255) DEFAULT NULL,
  lng             DECIMAL(10,7) DEFAULT NULL,
  lat             DECIMAL(10,7) DEFAULT NULL,
  capacity        INT          DEFAULT NULL                  COMMENT '人数上限',
  fee             DECIMAL(10,2) NOT NULL DEFAULT 0,
  status          VARCHAR(24)  NOT NULL DEFAULT 'DRAFT'      COMMENT 'DRAFT|PENDING_REVIEW|PUBLISHED|REJECTED|TAKEN_DOWN|CANCELLED',
  is_ai_generated TINYINT(1)   NOT NULL DEFAULT 0,
  template_id     BIGINT UNSIGNED DEFAULT NULL,
  cloned_from_id  BIGINT UNSIGNED DEFAULT NULL,
  checkin_code    VARCHAR(64)  DEFAULT NULL                  COMMENT '签到码/二维码内容',
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at      TIMESTAMP    NULL DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_act_creator (creator_id),
  KEY idx_act_team (team_id),
  KEY idx_act_status (status),
  KEY idx_act_category (category),
  KEY idx_act_city (city),
  KEY idx_act_start (start_time),
  KEY idx_act_geo (lat, lng)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动主表(时间相位 未开始/报名中/截止/活动中/已结束 由 start/deadline/end 计算)';

CREATE TABLE IF NOT EXISTS activity_tag (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  activity_id BIGINT UNSIGNED NOT NULL,
  tag         VARCHAR(32) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_act_tag (activity_id, tag),
  KEY idx_act_tag_tag (tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动标签';

CREATE TABLE IF NOT EXISTS activity_template (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name            VARCHAR(48) NOT NULL,
  category        VARCHAR(24) NOT NULL,
  default_intro   VARCHAR(500) DEFAULT NULL,
  default_capacity INT        DEFAULT NULL,
  icon            VARCHAR(255) DEFAULT NULL,
  is_system       TINYINT(1)  NOT NULL DEFAULT 1,
  created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_tpl_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动模板';

CREATE TABLE IF NOT EXISTS activity_signup (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  activity_id BIGINT UNSIGNED NOT NULL,
  user_id     BIGINT UNSIGNED NOT NULL,
  status      VARCHAR(16) NOT NULL DEFAULT 'REGISTERED'      COMMENT 'REGISTERED|CANCELLED',
  signup_info JSON        DEFAULT NULL                       COMMENT '报名时填写的必要信息',
  created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  cancelled_at TIMESTAMP  NULL DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_signup_act (activity_id, status),
  KEY idx_signup_user (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动报名';

CREATE TABLE IF NOT EXISTS activity_waitlist (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  activity_id      BIGINT UNSIGNED NOT NULL,
  user_id          BIGINT UNSIGNED NOT NULL,
  position         INT         NOT NULL,
  status           VARCHAR(16) NOT NULL DEFAULT 'WAITING'    COMMENT 'WAITING|NOTIFIED|PROMOTED|EXPIRED|CANCELLED',
  notified_at      TIMESTAMP   NULL DEFAULT NULL,
  confirm_deadline TIMESTAMP   NULL DEFAULT NULL,
  created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_wait_act (activity_id, status, position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='等待队列';

CREATE TABLE IF NOT EXISTS activity_checkin (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  activity_id BIGINT UNSIGNED NOT NULL,
  user_id     BIGINT UNSIGNED NOT NULL,
  checkin_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  lng         DECIMAL(10,7) DEFAULT NULL,
  lat         DECIMAL(10,7) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_checkin (activity_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='扫码签到(可带位置)';

CREATE TABLE IF NOT EXISTS activity_summary (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  activity_id BIGINT UNSIGNED NOT NULL,
  author_id   BIGINT UNSIGNED NOT NULL,
  content     TEXT        DEFAULT NULL,
  status      VARCHAR(16) NOT NULL DEFAULT 'DRAFT'           COMMENT 'DRAFT|PUBLISHED',
  created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_summary_act (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动图文总结';

CREATE TABLE IF NOT EXISTS activity_summary_image (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  activity_id        BIGINT UNSIGNED NOT NULL,
  image_url          VARCHAR(255) NOT NULL,
  ai_category        VARCHAR(24) DEFAULT NULL                COMMENT 'GROUP_PHOTO|VENUE|PROCESS|MATERIAL|RESULT(AI识别)',
  confirmed_category VARCHAR(24) DEFAULT NULL                COMMENT '发起人确认后的分类',
  confirmed          TINYINT(1)  NOT NULL DEFAULT 0,
  created_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_sumimg_act (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='总结图片+AI分类(需人工确认)';

CREATE TABLE IF NOT EXISTS activity_review (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  activity_id   BIGINT UNSIGNED NOT NULL,
  user_id       BIGINT UNSIGNED NOT NULL,
  rating        TINYINT     NOT NULL                         COMMENT '1-5',
  content       VARCHAR(500) DEFAULT NULL,
  visible_until TIMESTAMP   NULL DEFAULT NULL                COMMENT '限期可见, 过期不展示',
  created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_review (activity_id, user_id),
  KEY idx_review_act (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户评价';

CREATE TABLE IF NOT EXISTS activity_audit_log (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  activity_id     BIGINT UNSIGNED NOT NULL,
  audit_type      VARCHAR(8)  NOT NULL                       COMMENT 'AI|MANUAL',
  result          VARCHAR(24) NOT NULL                       COMMENT 'PASSED|REJECTED|NEEDS_REVISION|TO_MANUAL',
  reason          VARCHAR(255) DEFAULT NULL,
  auditor_admin_id BIGINT UNSIGNED DEFAULT NULL,
  created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_audit_act (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动审核流水(AI/人工)';

-- ============================== 三、社交(好友/关注/IM) ==============================

CREATE TABLE IF NOT EXISTS friend_request (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  from_user_id BIGINT UNSIGNED NOT NULL,
  to_user_id   BIGINT UNSIGNED NOT NULL,
  status       VARCHAR(16) NOT NULL DEFAULT 'PENDING'        COMMENT 'PENDING|ACCEPTED|REJECTED',
  source       VARCHAR(16) DEFAULT NULL                      COMMENT 'PROFILE|ACTIVITY|QRCODE',
  message      VARCHAR(140) DEFAULT NULL,
  created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  handled_at   TIMESTAMP   NULL DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_freq_to (to_user_id, status),
  KEY idx_freq_from (from_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友申请';

CREATE TABLE IF NOT EXISTS friendship (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  owner_id   BIGINT UNSIGNED NOT NULL,
  friend_id  BIGINT UNSIGNED NOT NULL,
  remark     VARCHAR(32) DEFAULT NULL,
  group_tag  VARCHAR(32) DEFAULT NULL,
  created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_friendship (owner_id, friend_id),
  KEY idx_friendship_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友关系(每方向一行, 各自备注/分组)';

CREATE TABLE IF NOT EXISTS follow (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  follower_id BIGINT UNSIGNED NOT NULL,
  followee_id BIGINT UNSIGNED NOT NULL,
  created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_follow (follower_id, followee_id),
  KEY idx_follow_followee (followee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单向关注(互关升级为好友)';

CREATE TABLE IF NOT EXISTS user_block (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id         BIGINT UNSIGNED NOT NULL,
  blocked_user_id BIGINT UNSIGNED NOT NULL,
  created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_block (user_id, blocked_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='黑名单';

CREATE TABLE IF NOT EXISTS message (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  scope             VARCHAR(8)  NOT NULL                     COMMENT 'FRIEND|TEAM',
  sender_id         BIGINT UNSIGNED NOT NULL,
  receiver_id       BIGINT UNSIGNED DEFAULT NULL             COMMENT 'scope=FRIEND',
  team_id           BIGINT UNSIGNED DEFAULT NULL             COMMENT 'scope=TEAM',
  content_type      VARCHAR(16) NOT NULL DEFAULT 'TEXT'      COMMENT 'TEXT|EMOJI|IMAGE|LOCATION',
  content           TEXT        NOT NULL,
  is_read           TINYINT(1)  NOT NULL DEFAULT 0           COMMENT '单聊已读',
  is_recalled       TINYINT(1)  NOT NULL DEFAULT 0,
  recalled_at       TIMESTAMP   NULL DEFAULT NULL,
  forwarded_from_id BIGINT UNSIGNED DEFAULT NULL,
  created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_msg_friend (scope, receiver_id, created_at),
  KEY idx_msg_team (team_id, created_at),
  KEY idx_msg_sender (sender_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='即时通讯消息(好友+群)';

-- ============================== 四、兴趣小队 ==============================

CREATE TABLE IF NOT EXISTS team (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name         VARCHAR(64)  NOT NULL,
  intro        VARCHAR(500) DEFAULT NULL,
  avatar       VARCHAR(255) DEFAULT NULL,
  join_type    VARCHAR(16)  NOT NULL DEFAULT 'PUBLIC'        COMMENT 'PUBLIC|APPROVAL',
  capacity     INT          NOT NULL DEFAULT 100,
  member_count INT          NOT NULL DEFAULT 1,
  status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'        COMMENT 'ACTIVE|DISSOLVED|SUSPENDED',
  owner_id     BIGINT UNSIGNED NOT NULL,
  created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at   TIMESTAMP    NULL DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_team_status (status),
  KEY idx_team_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='兴趣小队';

CREATE TABLE IF NOT EXISTS team_tag (
  id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  team_id BIGINT UNSIGNED NOT NULL,
  tag     VARCHAR(32) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_team_tag (team_id, tag),
  KEY idx_team_tag_tag (tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小队标签';

CREATE TABLE IF NOT EXISTS team_member (
  id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  team_id   BIGINT UNSIGNED NOT NULL,
  user_id   BIGINT UNSIGNED NOT NULL,
  role      VARCHAR(16) NOT NULL DEFAULT 'MEMBER'            COMMENT 'OWNER|ADMIN|MEMBER',
  points    INT         NOT NULL DEFAULT 0,
  joined_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_team_member (team_id, user_id),
  KEY idx_tm_role (team_id, role),
  KEY idx_tm_points (team_id, points)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小队成员+角色+积分';

CREATE TABLE IF NOT EXISTS team_join_request (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  team_id    BIGINT UNSIGNED NOT NULL,
  user_id    BIGINT UNSIGNED NOT NULL,
  status     VARCHAR(16) NOT NULL DEFAULT 'PENDING'          COMMENT 'PENDING|APPROVED|REJECTED',
  handler_id BIGINT UNSIGNED DEFAULT NULL,
  created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  handled_at TIMESTAMP   NULL DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_tjr_team (team_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小队加入申请(审核制)';

CREATE TABLE IF NOT EXISTS team_announcement (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  team_id    BIGINT UNSIGNED NOT NULL,
  author_id  BIGINT UNSIGNED NOT NULL,
  content    TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ann_team (team_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群公告';

CREATE TABLE IF NOT EXISTS team_vote (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  team_id     BIGINT UNSIGNED NOT NULL,
  creator_id  BIGINT UNSIGNED NOT NULL,
  title       VARCHAR(140) NOT NULL,
  options     JSON        NOT NULL                           COMMENT '["选项1","选项2",...]',
  multi_choice TINYINT(1) NOT NULL DEFAULT 0,
  deadline    TIMESTAMP   NULL DEFAULT NULL,
  created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_vote_team (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群投票';

CREATE TABLE IF NOT EXISTS team_vote_record (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  vote_id      BIGINT UNSIGNED NOT NULL,
  user_id      BIGINT UNSIGNED NOT NULL,
  option_index INT NOT NULL,
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_vote_record (vote_id, user_id, option_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投票记录';

CREATE TABLE IF NOT EXISTS team_file (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  team_id    BIGINT UNSIGNED NOT NULL,
  uploader_id BIGINT UNSIGNED NOT NULL,
  file_name  VARCHAR(255) NOT NULL,
  file_url   VARCHAR(255) NOT NULL,
  file_size  BIGINT      DEFAULT NULL,
  created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_tfile_team (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群文件';

CREATE TABLE IF NOT EXISTS team_album_photo (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  team_id     BIGINT UNSIGNED NOT NULL,
  uploader_id BIGINT UNSIGNED NOT NULL,
  image_url   VARCHAR(255) NOT NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_album_team (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小队相册';

CREATE TABLE IF NOT EXISTS team_moment (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  team_id     BIGINT UNSIGNED NOT NULL,
  author_id   BIGINT UNSIGNED NOT NULL,
  content     TEXT        DEFAULT NULL,
  images      JSON        DEFAULT NULL,
  is_featured TINYINT(1)  NOT NULL DEFAULT 0                 COMMENT '被小队精选',
  created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_moment_team (team_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小队动态';

CREATE TABLE IF NOT EXISTS team_points_log (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  team_id    BIGINT UNSIGNED NOT NULL,
  user_id    BIGINT UNSIGNED NOT NULL,
  points     INT         NOT NULL,
  reason     VARCHAR(32) NOT NULL                            COMMENT 'JOIN_ACTIVITY|POST_MOMENT|MOMENT_FEATURED|...',
  ref_id     BIGINT UNSIGNED DEFAULT NULL,
  created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_points_team_user (team_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分明细';

-- ============================== 五、后台管理 ==============================

CREATE TABLE IF NOT EXISTS moderation_action (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  target_type VARCHAR(16) NOT NULL                           COMMENT 'ACTIVITY|TEAM',
  target_id   BIGINT UNSIGNED NOT NULL,
  action      VARCHAR(16) NOT NULL                           COMMENT 'TAKE_DOWN|RESTORE|SUSPEND',
  reason      VARCHAR(255) NOT NULL,
  admin_id    BIGINT UNSIGNED NOT NULL,
  created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_mod_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='下架/停用/恢复记录';

CREATE TABLE IF NOT EXISTS report (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  reporter_id      BIGINT UNSIGNED NOT NULL,
  target_type      VARCHAR(16) NOT NULL                      COMMENT 'ACTIVITY|TEAM|USER|MOMENT',
  target_id        BIGINT UNSIGNED NOT NULL,
  reason           VARCHAR(64) NOT NULL,
  detail           VARCHAR(500) DEFAULT NULL,
  status           VARCHAR(16) NOT NULL DEFAULT 'PENDING'    COMMENT 'PENDING|HANDLED|DISMISSED',
  handler_admin_id BIGINT UNSIGNED DEFAULT NULL,
  created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  handled_at       TIMESTAMP   NULL DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_report_status (status),
  KEY idx_report_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举报记录';

-- ============================== 系统数据(模板) ==============================
INSERT INTO activity_template (name, category, default_capacity, is_system) VALUES
  ('运动健身', 'SPORTS', 20, 1),
  ('户外徒步', 'HIKING', 15, 1),
  ('桌游聚会', 'BOARD_GAME', 8, 1),
  ('学习交流', 'STUDY', 30, 1),
  ('公益活动', 'CHARITY', 50, 1),
  ('城市探索', 'CITY_WALK', 12, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);
