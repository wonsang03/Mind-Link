package com.mindlink.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP 요청 액세스 로그 필터.
 * 개인정보 보호 정책:
 *  - 요청/응답 body는 절대 기록하지 않음
 *  - 세션 ID, 사용자명, 이메일, 전체 IP는 기록하지 않음
 *  - 민감 쿼리 파라미터(password, token 등)는 값을 *** 로 마스킹
 *  - 클라이언트 IP는 마지막 옥텟을 마스킹 (예: 192.168.0.*)
 *  - 인증 여부만 auth/anon 으로 구분 (사용자 식별자 미기록)
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("ACCESS");

    /** 마스킹할 쿼리 파라미터 키 (소문자 비교, 부분일치) */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "pwd",
            "token", "secret", "key", "apikey", "api_key",
            "email", "mail",
            "phone", "tel", "mobile",
            "code", "otp", "auth"
    );

    /** 액세스 로그에서 제외할 경로 prefix */
    private static final Set<String> SKIP_PREFIXES = Set.of(
            "/css/", "/js/", "/images/", "/img/", "/static/",
            "/webjars/", "/uploads/", "/favicon",
            "/admin/logs/"  /* 로그 뷰어 SSE·API — 자기 자신 노이즈 방지 */
    );

    private static final Pattern QUERY_PAIR = Pattern.compile("([^=&]+)=([^&]*)");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        for (String prefix : SKIP_PREFIXES) {
            if (uri.startsWith(prefix)) return true;
        }
        return uri.endsWith(".ico") || uri.endsWith(".png") || uri.endsWith(".jpg")
                || uri.endsWith(".jpeg") || uri.endsWith(".gif") || uri.endsWith(".svg")
                || uri.endsWith(".woff") || uri.endsWith(".woff2");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(req, res);
        } finally {
            long took = System.currentTimeMillis() - start;
            String method = req.getMethod();
            String path = req.getRequestURI();
            String query = maskQuery(req.getQueryString());
            int status = res.getStatus();
            String ip = maskIp(clientIp(req));
            String auth = isAuthenticated(req) ? "auth" : "anon";

            // 형식: METHOD path[?query] -> status (took ms) ip=x.x.x.* user=auth|anon
            if (query == null) {
                log.info("{} {} -> {} ({} ms) ip={} user={}",
                        method, path, status, took, ip, auth);
            } else {
                log.info("{} {}?{} -> {} ({} ms) ip={} user={}",
                        method, path, query, status, took, ip, auth);
            }
        }
    }

    /** 쿼리스트링을 파싱해 민감 키 값만 *** 로 치환 */
    private static String maskQuery(String q) {
        if (q == null || q.isEmpty()) return null;
        Matcher m = QUERY_PAIR.matcher(q);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String val = m.group(2);
            String lower = key.toLowerCase();
            boolean sensitive = false;
            for (String k : SENSITIVE_KEYS) {
                if (lower.contains(k)) { sensitive = true; break; }
            }
            if (sb.length() > 0) sb.append('&');
            sb.append(key).append('=').append(sensitive ? "***" : val);
        }
        return sb.length() == 0 ? q : sb.toString();
    }

    /** IPv4의 마지막 옥텟을 *로, IPv6는 앞 4그룹만 노출 */
    private static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) return "-";
        if (ip.contains(":")) {
            String[] parts = ip.split(":");
            if (parts.length <= 4) return ip + "::*";
            return parts[0] + ":" + parts[1] + ":" + parts[2] + ":" + parts[3] + "::*";
        }
        int last = ip.lastIndexOf('.');
        if (last <= 0) return "-";
        return ip.substring(0, last) + ".*";
    }

    /** 프록시 뒤에서도 동작하도록 X-Forwarded-For 우선 사용 */
    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr();
    }

    /** 세션에 로그인 ID가 있으면 인증 상태로 판정 (식별자 자체는 로그 미기록) */
    private static boolean isAuthenticated(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session != null && session.getAttribute(SessionConst.LOGIN_USER_ID) != null;
    }
}
