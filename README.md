# Redis 세션 저장소

`Java 25` · `Spring Boot 4.1` · `Spring Session` · `Redis 7` · `Lettuce` · `Docker Compose` · `GitHub Actions`

세션 인증을 Spring Security 없이 직접 구현하고 저장소를 **단일 Redis → Sentinel → Cluster** 로 옮기며
각 단계의 한계를 명령 수준에서 확인하고 장애를 주입해 검증한 학습 프로젝트

---

## 실행

```bash
# 단일 Redis (개발·디버깅용)
docker compose up -d --build

# Sentinel  (master 1 + replica 1~3 + sentinel 3)
docker compose -f docker/sentinel.yml up -d --build
docker compose -f docker/sentinel.yml --profile r3 up -d   # replica 3대

# Cluster  (master 3 + replica 3)
docker compose -f docker/cluster.yml up -d --build
docker compose -f docker/cluster.yml exec redis-node-1 \
  redis-cli --cluster create \
    172.30.0.11:6379 172.30.0.12:6379 172.30.0.13:6379 \
    172.30.0.14:6379 172.30.0.15:6379 172.30.0.16:6379 \
    --cluster-replicas 1 --cluster-yes
```

```bash
curl -si -X POST localhost:8080/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password123"}'   # X-Auth-Token 헤더로 세션 ID 발급

curl -H "X-Auth-Token: <토큰>" localhost:8080/me
```

---

## 1. 앱을 2대로 늘리자 로그인이 풀렸다

nginx 뒤에 앱 2대를 두자 로그인 다음 요청이 401이 됐다. 세션이 각 톰캣 메모리에 있어 다른 인스턴스가 찾지 못했다. Spring Session으로 저장소를 Redis로 옮겨 해결했다.

CI에 통합 테스트를 넣어 매번 검증한다 — 로그인 → `/me` 6회(인스턴스 교차) → 로그아웃 → 401
응답에 `instanceId` 를 실어 어느 서버가 처리했는지 드러나게 했다.

<details>
<summary>인증을 어떻게 구현했나 — 네 가지 도구를 다 만들어보고 둘을 골랐다</summary>

컨트롤러가 세션을 직접 꺼내는 코드를 걷어내려고 Filter / Interceptor / ArgumentResolver / AOP를 각각 브랜치로 구현했다. CI 스크립트를 한 글자도 고치지 않고 네 브랜치를 비교하며 결정했다.

결론은 **검사는 Interceptor, 주입은 ArgumentResolver** 였다.

- Filter는 실행 시점에 handler가 없어 `@LoginRequired` 를 읽지 못한다
- ArgumentResolver는 파라미터가 없는 `/logout` 에서 호출조차 되지 않아 차단에 못 쓴다
- AOP는 리졸버 뒤에 있어 파라미터를 만들지 못하고 덮어쓸 수만 있다

Spring Security도 같은 이유로 검사와 주입을 나눠 두고 있었다. 다만 Security는 검사에 **Filter**를 쓴다.
매핑되지 않은 URL과 정적 리소스까지 막으려면 DispatcherServlet 바깥이어야 하기 때문이다.
내 구현은 애노테이션으로 대상을 지정하려고 Interceptor를 택했고 트레이드오프로 **기본적으로 열린** 구조가 됐다.
fail-safe 원칙에는 Security 쪽이 맞다.

같은 리팩토링에서 세션 접근을 `LoginSessionStore` 인터페이스 뒤로 옮겨 **서블릿 API를 아는 클래스를 하나로 좁혔다.**

</details>

## 2. 이번엔 Redis 한 대가 전부를 쥐고 있었다

인증은 모든 요청이 거치는 길목이라 이 노드가 곧 서비스 상한이자 장애 반경이다.
**가용성과 처리량을 함께 확보하려고** Sentinel을 도입하고 read replica를 늘렸다.

```java
builder.readFrom(ReadFrom.REPLICA_PREFERRED)
```

**이 한 줄이 없으면 replica는 복제만 받으며 논다.** Lettuce 기본값이 `MASTER` 라 읽기까지 master로 간다.
서버를 나눠도 클라이언트가 나눠 보내지 않으면 아무 일도 일어나지 않는다.

캐시에서는 통하는 방식이었지만 세션에서는 통하지 않았다.

## 3. 조회할 때마다 쓰기가 따라붙었다

`MONITOR` 를 replica에 붙이고 `/me` 를 한 번 호출했다.

```
[172.22.0.8:39330] "HGETALL"    앱이 replica 에 직접        읽기
[172.22.0.8:39330] "EXISTS"     앱이 replica 에 직접        읽기
[172.22.0.2:6379]  "HMSET"      master 가 실행 후 복제 전달  쓰기
[172.22.0.2:6379]  "PEXPIREAT"  마찬가지                    쓰기
```

출처 IP 하나로 읽기와 쓰기가 어디서 처리됐는지가 갈렸다.

세션은 sliding expiration이라 **조회할 때마다 만료 시각을 다시 쓴다.**
로그인 시 `HMSET` 은 필드 5개를 담지만 `/me` 의 `HMSET` 은 `lastAccessedTime` 하나만 사용한다.
순전히 만료 갱신 때문에 발생하는 쓰기다.

읽기 편중(9:1)인 캐시와 달리 세션은 1:1이라 **replica를 늘려도 절반만 분산된다.**
거기에 Redis는 명령 처리가 단일 스레드여서 master 코어 하나가 쓰기 상한이고 scale-up으로는 해결할 수 없다. Sentinel의 failover는 복구 시간만 줄일 뿐 상한도 장애 반경도 그대로다.

**트래픽이 늘면 master가 먼저 무너지는 구조였다.** 수평 분할 외에 길이 없었다.

## 4. Cluster로 수평 분할

master 3 + replica 3. 키 공간을 16384개 슬롯으로 나눠 master 3대가 3등분한다.

```
node-1 EXISTS <세션키> → MOVED 12025 172.30.0.13:6379
node-3 EXISTS <세션키> → 1
```

`cluster-require-full-coverage` 기본값이 `yes` 라는 점이 중요했다.
슬롯 하나만 비어도 **클러스터 전체가 요청을 거부**한다. `no` 로 바꾸지 않으면 부분 장애 실험 자체가 불가능하다.

**세션 관련 자바 코드는 한 줄도 바뀌지 않았다.** 커밋에 `.java` 파일이 없다.
1단계에서 서블릿 API를 한 클래스에 가둔 것이, 저장소 구조가 완전히 바뀌어도 애플리케이션이
영향받지 않는 결과로 이어졌다.

## 5. shard 하나를 죽였더니 클라이언트가 문제였다

부하 중에 master 한 대를 종료하고 요청별 성공·실패를 기록했다.

**격리 범위는 설계대로였다.** 죽은 shard가 담당하던 세션만 매번 같은 건이 실패했다.
이론 33%에 근접(실측 20~30%, 세션이 어느 shard에 떨어지느냐에 따라 변동)

**문제는 시간이었다.**

```
01:44:23.515  Connection with master lost.
01:44:31.490  Failover election won.        ← 서버 복구 8.0초
```

서버는 8초 만에 복구됐는데 애플리케이션은 그 뒤로도 13초 이상 실패했다.

첫 가설은 "Lettuce의 토폴로지 자동 갱신이 기본 꺼짐"이었으나 틀렸다.
**Lettuce 7.0부터 adaptive 트리거는 기본으로 전부 켜져 있다.**
실제 원인은 `adaptiveRefreshTriggersTimeout`(기본 30초) **rate limit** 이었다.

```
t=5s   노드 사망 → 트리거 발동 → CLUSTER SLOTS 조회
       그러나 승격 전이라 서버조차 새 담당을 모른다. 낡은 지도를 낡은 지도로 갈아끼움
       ↓ 30초 잠금 시작
t=13s  승격 완료. 서버가 새 지도를 갖는다
       트리거가 또 터져도 잠금에 막혀 조회 불가
```

**클라이언트가 서버보다 8초 빨랐던 것이 문제였다.** adaptive 트리거는 "뭔가 잘못됐다"에 반응하는데 잘못된 직후에는 서버도 아직 답을 모른다. 한 번 헛되이 갱신하고 30초 잠기는 구조다.

Spring Boot는 이 타임아웃을 노출하지 않는다. 접근 가능한 레버는 주기 갱신이고 기본 꺼짐이다.

```properties
spring.data.redis.lettuce.cluster.refresh.period=5s
```

| | 개선 전 | 개선 후 |
|---|---|---|
| 서버 failover | 8.0초 | 8.1초 |
| **클라이언트 장애** | **21초 이상** | **약 13초** |
| 실패 구간 | 5s ~ 26s+ (관측 종료까지) | 5s ~ 17s |

**바뀐 것은 서버가 복구되는 시간이 아니라, 클라이언트가 그 사실을 아는 데 걸린 시간이다.**

> 실험 설계, 원본 로그, 초별 집계는 [`docs/evidence/`](docs/evidence/) 에 있다.

### 부수 효과 — 실패는 20%인데 처리량은 66분의 1이 됐다

```
 0~4s   초당 440건        정상
 8~17s  3초에 20건 = 초당 6.7건
20s~    초당 160건 이상    복구
```

동시 요청 20개 중 정상 16개는 수 ms에 끝나지만, 죽은 노드로 간 4개가 타임아웃까지 매달려
배치 전체를 3초로 늘렸다. 응답 코드가 `500` 이 아니라 `000`(응답 없음)이었던 것도 같은 얘기다.
빠르게 실패한 게 아니라 **매달려 있었다.**

실무에서는 이것이 커넥션 풀 고갈로 나타난다. **격리는 범위만이 아니라 속도까지 지켜져야 성립한다.**

---

## 결론

```
장애 반경 (누가)      Cluster 가 해결.   100% → 33%
복구 시간 (얼마나)    클라이언트가 결정.  서버 8초와 무관하게 21초
```

> 서버를 나눠도 클라이언트가 따라오지 않으면
> 복구는 서버 시간이 아니라 클라이언트 시간으로 결정된다.

같은 계열의 문제를 앞에서도 겪었다. `ReadFrom` 기본값이 `MASTER` 라 그 한 줄을 켜기 전까지 replica 세 대는 복제만 받으며 놀고 있었다.

**분산은 서버 구성으로 끝나지 않는다.** 클라이언트가 그 구성을 알고 따라가야 비로소 성립한다.

---

## 그 밖에 걸렸던 것

**설정 파일에 썼는데 아무도 읽지 않는 경우가 두 번 있었다.**
쿠키 설정은 Spring Session이 읽지 않았고, 타임아웃(`server.servlet.session.timeout`)은
`@EnableRedisHttpSession` 을 직접 선언한 탓에 Boot의 `SessionAutoConfiguration` 이
`@ConditionalOnMissingBean` 으로 물러나면서 함께 빠졌다. 둘 다 컴파일되고 앱도 뜬다.
`redis-cli TTL` 을 직접 재보고서야 알았다.
**자동설정을 수동 선언으로 대체하면 그 자동설정이 곁다리로 하던 일까지 사라진다.**

**손실 창과 다운타임 창은 다르다.** master가 죽은 순간부터 승격까지는 애초에 쓰기를 받지 않으므로
그 구간의 데이터는 존재하지 않는다. 실제 유실은 복제 지연(수 ms)만큼이다.

**AOF는 failover에 관여하지 않는다.** replica가 승격할 때 아무도 AOF를 읽지 않는다.

**Cluster가 데이터를 더 안전하게 만들지도 않는다.** 복제 모델은 Sentinel과 동일한 비동기다.
달라지는 것은 장애의 영향 범위뿐이다.

---

## 한계

- 실사용 트래픽이 없어 **처리량을 수치로 측정하지 않았다.** 대신 명령 수준에서 구조를 확인했다
- 단일 호스트 컨테이너 환경이라 **multi-AZ 장애를 검증하지 못했다.** 같은 호스트 배치는 HA로 볼 수 없다
- 모든 노드가 CPU를 공유하므로 절대 처리량이 아니라 **상대 변화만 유효하다**
- 리샤딩 중 `ASK` 리다이렉트 처리 미검증
