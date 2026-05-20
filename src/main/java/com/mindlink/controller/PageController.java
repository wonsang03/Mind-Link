package com.mindlink.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
public class PageController {

    private static final List<Map<String, Object>> NOTICES = List.of(
            Map.of(
                    "id", 1,
                    "category", "중요",
                    "date", "2026-05-10",
                    "title", "AI 기반 정서 케어 베타 오픈 안내",
                    "summary", "AI가 감정을 분석하고 단계별 케어를 제공하는 새로운 서비스가 베타 오픈되었습니다.",
                    "content", "안녕하세요, 마음이음입니다.\n\n많은 분들이 기다려주신 'AI 기반 정서 케어' 서비스가 베타 오픈되었습니다. " +
                            "AI가 사용자의 메시지를 분석해 감정 상태와 위험도를 판단하고, 단계별로 맞춤 케어를 제공합니다.\n\n" +
                            "■ 주요 기능\n" +
                            "  - 실시간 감정 분석 및 위험도 단계 제시\n" +
                            "  - 위험도에 따른 맞춤형 추천 (호흡법, 활동, 전문 상담 연계)\n" +
                            "  - 고위험 상황에서 긴급 연락처 자동 안내\n\n" +
                            "■ 이용 방법\n" +
                            "  상단의 'AI 정서 케어' 메뉴에서 바로 이용하실 수 있습니다.\n\n" +
                            "베타 기간 동안 발견되는 개선점은 지속적으로 반영하겠습니다. 많은 관심과 의견 부탁드립니다."
            ),
            Map.of(
                    "id", 2,
                    "category", "서비스",
                    "date", "2026-05-02",
                    "title", "상담소 찾기 기능이 새롭게 개편되었습니다",
                    "summary", "지도 기반의 보기 좋은 카드 형태로 상담 센터를 더 빠르게 찾아볼 수 있습니다.",
                    "content", "안녕하세요, 마음이음입니다.\n\n상담소 찾기 페이지가 새롭게 단장했습니다. " +
                            "기존의 세로 리스트 형태에서, 지도 서비스에서 익숙한 가로형 카드 레이아웃으로 개편하여 " +
                            "센터의 위치, 운영 시간, 전문 분야를 한눈에 비교하실 수 있습니다.\n\n" +
                            "■ 개편 내용\n" +
                            "  - 가로형 카드 디자인 (썸네일 + 평점/거리/영업상태 + 위치/시간/전화)\n" +
                            "  - 센터 유형별 필터링 (심리상담센터, 정신건강복지센터 등)\n" +
                            "  - 키워드 검색 (센터명, 지역, 전문 분야)\n\n" +
                            "앞으로도 더 편리한 사용 경험을 제공하기 위해 계속해서 개선해 나가겠습니다."
            ),
            Map.of(
                    "id", 3,
                    "category", "점검",
                    "date", "2026-04-18",
                    "title", "정기 시스템 점검 안내",
                    "summary", "매월 둘째 주 화요일 오전 2시~4시 정기 점검이 진행됩니다.",
                    "content", "안녕하세요, 마음이음입니다.\n\n서비스 안정성을 높이기 위해 정기 점검을 진행합니다.\n\n" +
                            "■ 점검 일정\n" +
                            "  매월 둘째 주 화요일 02:00 ~ 04:00 (KST)\n\n" +
                            "■ 점검 내용\n" +
                            "  - 인프라 보안 패치\n" +
                            "  - 데이터베이스 백업 및 무결성 점검\n" +
                            "  - 신규 기능 배포\n\n" +
                            "점검 시간 동안에는 서비스 이용이 일시적으로 제한될 수 있습니다. 이용에 불편을 드려 죄송합니다."
            )
    );

    @GetMapping("/")
    public String home() {
        return "home";
    }

    @GetMapping("/info")
    public String info() {
        return "info";
    }

    @GetMapping("/ai-care")
    public String aiCare() {
        return "ai-care";
    }

    @GetMapping("/recommendations")
    public String recommendations(
            // TODO: 설문 팀과 emotion 파라미터 전달 방식 협의 필요 (테스트용 구현)
            @RequestParam(defaultValue = "NORMAL") String emotion,
            Model model) {
        model.addAttribute("emotion", emotion);
        return "recommendations";
    }

    @GetMapping("/notice")
    public String notice(Model model) {
        model.addAttribute("notices", NOTICES);
        return "notice";
    }

    @GetMapping("/notice/{id}")
    public String noticeDetail(@PathVariable int id, Model model) {
        Map<String, Object> n = NOTICES.stream()
                .filter(x -> Integer.valueOf(id).equals(x.get("id")))
                .findFirst().orElse(null);
        if (n == null) {
            return "redirect:/notice";
        }
        model.addAttribute("notice", n);
        return "notice-detail";
    }
}
