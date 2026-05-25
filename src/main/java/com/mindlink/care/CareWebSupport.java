package com.mindlink.care;

import com.mindlink.domain.User;
import com.mindlink.service.UserService;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpSession;

/** care 패키지 컨트롤러 공통 — 세션 사용자 조회·편지 발췌. */
final class CareWebSupport {

    private CareWebSupport() {}

    static User currentUser(HttpSession session, UserService userService) {
        Object uid = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (uid instanceof Long id) {
            return userService.findById(id).orElse(null);
        }
        return null;
    }

    static String excerpt(String body, int max) {
        if (body == null || body.isBlank()) return "";
        String t = body.replace("\r", " ").replace("\n", " ").replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
