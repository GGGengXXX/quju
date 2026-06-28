#!/usr/bin/env bash
set -euo pipefail

API_BASE="${API_BASE:-http://127.0.0.1:8541/v1}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-13306}"
DB_NAME="${DB_NAME:-quju_dev_thh_activity_one}"
DB_USER="${DB_USER:-quju}"
DB_PASS="${DB_PASS:-0KDA1j2eZJGJgSWEMMynDg}"
OWNER_EMAIL="${OWNER_EMAIL:-activity.demo.owner@example.com}"
OWNER_PASS="${OWNER_PASS:-Pass123456!}"
MEMBER_EMAIL="${MEMBER_EMAIL:-activity.demo.member@example.com}"
MEMBER_PASS="${MEMBER_PASS:-Pass123456!}"

mysql_exec() {
  mysql --default-character-set=utf8mb4 -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" -D "$DB_NAME" -N -e "$1"
}

json_field() {
  local payload="$1"
  local key="$2"
  printf '%s' "$payload" | sed -n "s/.*\"$key\":\"\([^\"]*\)\".*/\1/p"
}

assert_contains() {
  local payload="$1"
  local expected="$2"
  if [[ "$payload" != *"$expected"* ]]; then
    echo "ASSERT FAILED: expected [$expected] in [$payload]" >&2
    exit 1
  fi
}

echo "==> login demo users"
OWNER_LOGIN=$(curl -s -X POST "$API_BASE/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"$OWNER_EMAIL\",\"password\":\"$OWNER_PASS\"}")
MEMBER_LOGIN=$(curl -s -X POST "$API_BASE/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"$MEMBER_EMAIL\",\"password\":\"$MEMBER_PASS\"}")
OWNER_TOKEN=$(json_field "$OWNER_LOGIN" token)
MEMBER_TOKEN=$(json_field "$MEMBER_LOGIN" token)
assert_contains "$OWNER_LOGIN" '"code":0'
assert_contains "$MEMBER_LOGIN" '"code":0'

echo "==> repair demo cn text"
mysql_exec "UPDATE activity SET name='周末羽毛球局', intro='适合新手到中级，校内体育馆 2 小时', city='北京', address='海淀区学院路体育馆' WHERE id=1; \
UPDATE activity SET name='城市漫步打卡夜游', intro='傍晚出发，边走边拍，轻社交活动', city='北京', address='朝阳区亮马河' WHERE id=2; \
UPDATE activity_tag SET tag='运动' WHERE id=1; \
UPDATE activity_tag SET tag='羽毛球' WHERE id=2; \
UPDATE activity_tag SET tag='城市漫步' WHERE id=3; \
UPDATE activity_tag SET tag='摄影' WHERE id=4; \
UPDATE user SET nickname='活动测试用户' WHERE id=1;"

echo "==> verify ai plan"
AI_PLAN=$(curl -s -X POST "$API_BASE/activities/ai-plan" -H "Authorization: Bearer $OWNER_TOKEN" -H 'Content-Type: application/json' -d '{"theme":"脚本验证活动","category":"SPORTS"}')
assert_contains "$AI_PLAN" '"code":0'
assert_contains "$AI_PLAN" 'AI 初稿'

echo "==> verify discover filters"
DISCOVER=$(curl -s "$API_BASE/activities?tab=NEARBY&lng=116.3521&lat=39.9835&distanceKm=5&page=1&size=10")
assert_contains "$DISCOVER" '"code":0'
assert_contains "$DISCOVER" '"total":'
if [[ "$DISCOVER" == *'"status":"DRAFT"'* ]] || [[ "$DISCOVER" == *'"status":"PENDING_REVIEW"'* ]]; then
  echo "ASSERT FAILED: discover should not expose draft/review items publicly" >&2
  exit 1
fi

echo "==> verify owner mine"
MINE=$(curl -s "$API_BASE/activities/mine?status=PUBLISHED&page=1&size=10" -H "Authorization: Bearer $OWNER_TOKEN")
assert_contains "$MINE" '"code":0'
assert_contains "$MINE" "Activity Demo Owner"

echo "==> prepare activity 1 signup/checkin mock data"
mysql_exec "DELETE FROM activity_checkin WHERE activity_id=1; DELETE FROM activity_signup WHERE activity_id=1; DELETE FROM activity_waitlist WHERE activity_id=1; UPDATE activity SET creator_id=4,status='PUBLISHED',capacity=1,signup_deadline=DATE_ADD(NOW(), INTERVAL 2 DAY),start_time=DATE_ADD(NOW(), INTERVAL 3 DAY),end_time=DATE_ADD(DATE_ADD(NOW(), INTERVAL 3 DAY), INTERVAL 2 HOUR),checkin_code=NULL WHERE id=1;"

echo "==> verify signup / waitlist / confirm"
SIGNUP_OWNER=$(curl -s -X POST "$API_BASE/activities/1/signup" -H "Authorization: Bearer $OWNER_TOKEN" -H 'Content-Type: application/json' -d '{"signupInfo":{"source":"verify"}}')
SIGNUP_MEMBER=$(curl -s -X POST "$API_BASE/activities/1/signup" -H "Authorization: Bearer $MEMBER_TOKEN" -H 'Content-Type: application/json' -d '{"signupInfo":{"source":"verify"}}')
assert_contains "$SIGNUP_OWNER" 'REGISTERED'
assert_contains "$SIGNUP_MEMBER" 'WAITLISTED'
CANCEL_OWNER=$(curl -s -X DELETE "$API_BASE/activities/1/signup" -H "Authorization: Bearer $OWNER_TOKEN")
assert_contains "$CANCEL_OWNER" '"code":0'
CONFIRM_MEMBER=$(curl -s -X POST "$API_BASE/activities/1/waitlist/confirm" -H "Authorization: Bearer $MEMBER_TOKEN")
assert_contains "$CONFIRM_MEMBER" '"code":0'

echo "==> verify location checkin guard"
CHECKIN_CODE=$(curl -s -X POST "$API_BASE/activities/1/checkin-code" -H "Authorization: Bearer $OWNER_TOKEN")
CODE_VALUE=$(json_field "$CHECKIN_CODE" code)
BAD_CHECKIN=$(curl -s -X POST "$API_BASE/activities/1/checkin" -H "Authorization: Bearer $MEMBER_TOKEN" -H 'Content-Type: application/json' -d "{\"code\":\"$CODE_VALUE\",\"lng\":117.0,\"lat\":40.5}")
assert_contains "$BAD_CHECKIN" '"code":3008'
GOOD_CHECKIN=$(curl -s -X POST "$API_BASE/activities/1/checkin" -H "Authorization: Bearer $MEMBER_TOKEN" -H 'Content-Type: application/json' -d "{\"code\":\"$CODE_VALUE\",\"lng\":116.3521,\"lat\":39.9835}")
assert_contains "$GOOD_CHECKIN" '"code":0'

echo "==> prepare activity 2 summary/review mock data"
mysql_exec "DELETE FROM activity_summary_image WHERE activity_id=2; DELETE FROM activity_summary WHERE activity_id=2; DELETE FROM activity_review WHERE activity_id=2; DELETE FROM activity_checkin WHERE activity_id=2; DELETE FROM activity_signup WHERE activity_id=2; DELETE FROM activity_waitlist WHERE activity_id=2; UPDATE activity SET creator_id=4,status='PUBLISHED',signup_deadline=DATE_SUB(NOW(), INTERVAL 3 DAY),start_time=DATE_SUB(NOW(), INTERVAL 2 DAY),end_time=DATE_SUB(NOW(), INTERVAL 1 DAY) WHERE id=2; INSERT INTO activity_signup (activity_id,user_id,status,signup_info) VALUES (2,5,'REGISTERED','{}');"

echo "==> verify summary and review"
SUMMARY=$(curl -s -X POST "$API_BASE/activities/2/summary" -H "Authorization: Bearer $OWNER_TOKEN" -H 'Content-Type: application/json' -d '{"content":"脚本验证总结","publish":true}')
assert_contains "$SUMMARY" '"code":0'
IMAGES=$(curl -s -X POST "$API_BASE/activities/2/summary/images" -H "Authorization: Bearer $OWNER_TOKEN" -H 'Content-Type: application/json' -d '{"imageUrls":["https://img.example.com/group-photo-verify.jpg"]}')
assert_contains "$IMAGES" 'GROUP_PHOTO'
REVIEW=$(curl -s -X POST "$API_BASE/activities/2/reviews" -H "Authorization: Bearer $MEMBER_TOKEN" -H 'Content-Type: application/json' -d '{"rating":5,"content":"脚本验证评价"}')
assert_contains "$REVIEW" '"code":0'
REVIEWS=$(curl -s "$API_BASE/activities/2/reviews?page=1&size=10")
assert_contains "$REVIEWS" '脚本验证评价'

echo "==> verify delete/cancel"
DRAFT=$(curl -s -X POST "$API_BASE/activities" -H "Authorization: Bearer $OWNER_TOKEN" -H 'Content-Type: application/json' -d '{"name":"Script Delete Draft","category":"OTHER","city":"北京","capacity":8,"fee":0,"submit":false}')
DRAFT_ID=$(printf '%s' "$DRAFT" | sed -n 's/.*"data":{"id":\([0-9][0-9]*\).*/\1/p')
OWNER_DRAFT_DETAIL=$(curl -s "$API_BASE/activities/$DRAFT_ID" -H "Authorization: Bearer $OWNER_TOKEN")
assert_contains "$OWNER_DRAFT_DETAIL" '"code":0'
MEMBER_DRAFT_DETAIL=$(curl -s "$API_BASE/activities/$DRAFT_ID" -H "Authorization: Bearer $MEMBER_TOKEN")
assert_contains "$MEMBER_DRAFT_DETAIL" '"code":1003'
SUBMIT_DRAFT=$(curl -s -X POST "$API_BASE/activities/$DRAFT_ID/submit" -H "Authorization: Bearer $OWNER_TOKEN")
assert_contains "$SUBMIT_DRAFT" '"code":0'
DELETE_DRAFT=$(curl -s -X DELETE "$API_BASE/activities/$DRAFT_ID" -H "Authorization: Bearer $OWNER_TOKEN")
assert_contains "$DELETE_DRAFT" '"code":0'
CANCEL_PUBLISHED=$(curl -s -X DELETE "$API_BASE/activities/1" -H "Authorization: Bearer $OWNER_TOKEN")
assert_contains "$CANCEL_PUBLISHED" '"code":0'
mysql_exec "UPDATE activity SET status='PUBLISHED' WHERE id=1;"

echo "==> activity domain verification passed"
