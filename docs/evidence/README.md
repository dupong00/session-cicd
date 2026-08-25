# 장애 주입 실험 — Cluster shard 1대 종료

**구성**: Redis Cluster master 3 + replica 3, `cluster-node-timeout 5000`
**조건**: 세션 토큰 20개(3개 shard에 분산), `xargs -P 20` 병렬 요청, `curl -m 3`
**조작**: t=5초에 master 1대 `SHUTDOWN NOSAVE`

---

## 결과 요약

| | 개선 전 | 개선 후 |
|---|---|---|
| 서버 failover | 8.0초 | 8.1초 |
| **클라이언트 장애** | **21초 이상** | **약 13초** |
| 실패 구간 | 5s ~ 26s+ (관측 종료까지) | 5s ~ 17s |
| 영향받은 세션 | 6/20 (30%) | 4/20 (20%) |
| 처리량 | 2,700건 / 30초 | 12,360건 / 40초 |

**조치**: `spring.data.redis.lettuce.cluster.refresh.period=5s`

---

## 발견 1 — 격리 범위는 설계대로 동작했다

죽은 shard가 담당하던 세션만 실패하고 나머지는 영향 없었다. 이론 33%에 근접(20~30%, 토큰 분산에 따라 변동).
**슬롯 단위 격리는 Cluster가 약속한 대로 작동한다.**

## 발견 2 — 복구 시간은 서버가 아니라 클라이언트가 결정했다

개선 전 Redis 로그 (`failover-timeline.log`):
```
01:44:23.515  Connection with master lost.
01:44:31.490  Failover election won: I'm the new master.
              → 서버 복구 8.0초
```

같은 시각 애플리케이션 관측 (`shard-kill-raw.log`):
```
 5s  실패   6/260  (2%)   ← kill
 8s  실패   6/20  (30%)
11s  실패   6/20  (30%)
14s  실패   6/20  (30%)   ← 서버는 이미 복구됨 (t=13s)
17s  실패   6/20  (30%)
20s  실패   6/20  (30%)
23s  실패   6/20  (30%)
26s  실패   6/20  (30%)   ← 관측 종료. 여전히 실패
```

**서버 복구 후에도 최소 13초간 클라이언트만 실패했다.**

---

## 원인 — 기본값의 동작을 오해했다

처음 가설은 "Lettuce의 토폴로지 자동 갱신이 기본 꺼짐"이었으나 **틀렸다.**
Lettuce 7.0부터 adaptive 트리거는 기본으로 전부 켜져 있다 (`enableAdaptiveRefreshTrigger` 는 그래서 deprecated).

실제 원인은 **rate limit** 이었다.

```
adaptiveRefreshTriggersTimeout   기본 30초
```

트리거가 발동해 갱신을 한 번 하면 그 뒤 30초간 재갱신이 막힌다. 갱신 폭주를 막는 장치다.

```
t=5s   노드 사망 → 재연결 실패 → adaptive 트리거 발동 → 갱신 시도
       그러나 아직 승격 전이라 새 지도가 존재하지 않음. 헛수고
t=13s  서버 승격 완료. 새 지도 존재
       트리거가 다시 터져도 rate limit(30초)에 막혀 갱신 불가
```

**너무 일찍 갱신해서 헛수고하고, 정작 필요한 시점에는 막혀 있었다.**

Spring Boot는 `adaptiveRefreshTriggersTimeout` 을 프로퍼티로 노출하지 않는다.
접근 가능한 레버는 **주기 갱신**이며, 이것은 기본 꺼짐이다.

---

## 조치와 검증

```properties
spring.data.redis.lettuce.cluster.refresh.period=5s
```

개선 후 (`failover-timeline-after.log`, `shard-kill-after-raw.log`):
```
01:59:13.130  Connection with master lost.
01:59:21.246  Failover election won.        → 서버 8.1초

 5s  실패 1/180 (1%)
 8s  실패 4/20 (20%)
11s  실패 4/20 (20%)
14s  실패 4/20 (20%)
17s  실패 4/20 (20%)
                              ← 20s 부터 실패 없음
```

서버 복구 후 지연이 **21초+ → 약 5초**로 줄었다. 주기 갱신 간격과 일치한다.

## 부수 효과 — 처리량 4배

```
개선 전   2,700건 / 30초
개선 후  12,360건 / 40초
```

실패 요청이 타임아웃(3초)까지 커넥션을 점유하던 것이 사라졌다.
**격리 실패는 범위뿐 아니라 처리량에도 영향을 준다** — 죽은 노드를 계속 두드리면 정상 요청까지 밀린다.

---

## 결론

Cluster 전환으로 **장애 반경**은 100% → 33%로 줄었다.
그러나 **복구 시간**은 서버가 아니라 클라이언트 설정이 결정했다.

> 서버를 나눠도 클라이언트가 따라오지 않으면, 복구는 서버 시간이 아니라 클라이언트 시간으로 결정된다.

같은 계열의 문제를 Sentinel 단계에서도 겪었다 — `ReadFrom` 기본값이 `MASTER` 라, 그 한 줄을 켜기 전까지 replica는 복제만 받으며 놀고 있었다.

---

## 파일

| 파일 | 내용 |
|---|---|
| `failover-timeline.log` | 개선 전 Redis 승격 타임스탬프 |
| `shard-kill-raw.log` | 개선 전 요청별 `<경과초> <HTTP코드>` (2,700행) |
| `failover-timeline-after.log` | 개선 후 승격 타임스탬프 |
| `shard-kill-after-raw.log` | 개선 후 요청별 기록 (12,360행) |

집계 재현:
```bash
awk '{tot[$1]++; if($2!="200") bad[$1]++} END {for(t in tot) if(bad[t]) printf "%2ds  실패 %d/%d (%.0f%%)\n", t, bad[t], tot[t], bad[t]*100/tot[t]}' shard-kill-raw.log | sort -n
```

`000` = HTTP 응답 없음(타임아웃/연결 실패). `500` 이 아니라 `000` 이라는 점이 중요하다 —
빠르게 실패한 게 아니라 **매달려 있었다**는 뜻이고, 커넥션 점유 문제와 직결된다.
