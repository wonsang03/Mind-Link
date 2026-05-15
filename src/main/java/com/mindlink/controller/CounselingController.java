package com.mindlink.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/counseling")
public class CounselingController {

    private static final List<Map<String, Object>> CENTERS = List.of(
            Map.<String, Object>ofEntries(
                    Map.entry("id", 1),
                    Map.entry("name", "마음다움 심리상담센터"),
                    Map.entry("title", "심리상담센터"),
                    Map.entry("specialty", List.of("우울증", "불안장애", "트라우마")),
                    Map.entry("rating", 4.9),
                    Map.entry("reviews", 128),
                    Map.entry("location", "서울 강남구 테헤란로 123"),
                    Map.entry("distance", "1.2km"),
                    Map.entry("phone", "02-1234-5678"),
                    Map.entry("hours", "평일 09:00 - 21:00"),
                    Map.entry("openNow", true),
                    Map.entry("introduction", "임상심리전문가 4명이 상주하며, 인지행동치료와 EMDR 기반 상담을 제공하는 종합 심리상담센터입니다.")
            ),
            Map.<String, Object>ofEntries(
                    Map.entry("id", 2),
                    Map.entry("name", "푸른 정신건강복지센터"),
                    Map.entry("title", "정신건강복지센터"),
                    Map.entry("specialty", List.of("대인관계", "직장 스트레스", "위기 상담")),
                    Map.entry("rating", 4.8),
                    Map.entry("reviews", 94),
                    Map.entry("location", "서울 마포구 양화로 45"),
                    Map.entry("distance", "2.4km"),
                    Map.entry("phone", "02-2345-6789"),
                    Map.entry("hours", "평일 09:00 - 18:00, 토요일 09:00 - 13:00"),
                    Map.entry("openNow", true),
                    Map.entry("introduction", "지역 주민 누구나 이용할 수 있는 공공 정신건강복지센터로, 무료 상담과 위기 개입 서비스를 제공합니다.")
            ),
            Map.<String, Object>ofEntries(
                    Map.entry("id", 3),
                    Map.entry("name", "이음 청소년상담복지센터"),
                    Map.entry("title", "청소년상담복지센터"),
                    Map.entry("specialty", List.of("청소년 상담", "학업 스트레스", "진로 고민")),
                    Map.entry("rating", 4.9),
                    Map.entry("reviews", 156),
                    Map.entry("location", "서울 송파구 올림픽로 88"),
                    Map.entry("distance", "3.1km"),
                    Map.entry("phone", "02-3456-7890"),
                    Map.entry("hours", "평일 10:00 - 20:00"),
                    Map.entry("openNow", false),
                    Map.entry("introduction", "청소년과 보호자를 위한 전문 상담 센터로, 학교 연계 프로그램과 진로/학업 코칭을 함께 운영합니다.")
            ),
            Map.<String, Object>ofEntries(
                    Map.entry("id", 4),
                    Map.entry("name", "한가람 가족상담연구소"),
                    Map.entry("title", "가족상담센터"),
                    Map.entry("specialty", List.of("부부상담", "가족치료", "육아 스트레스")),
                    Map.entry("rating", 5.0),
                    Map.entry("reviews", 203),
                    Map.entry("location", "경기 성남시 분당구 판교로 210"),
                    Map.entry("distance", "5.6km"),
                    Map.entry("phone", "031-456-7890"),
                    Map.entry("hours", "평일 10:00 - 21:00, 주말 10:00 - 18:00"),
                    Map.entry("openNow", true),
                    Map.entry("introduction", "정서중심 부부치료(EFT)와 가족체계치료를 전문으로 하는 가족상담 전문 센터입니다.")
            ),
            Map.<String, Object>ofEntries(
                    Map.entry("id", 5),
                    Map.entry("name", "햇살 심리상담연구소"),
                    Map.entry("title", "심리상담센터"),
                    Map.entry("specialty", List.of("우울", "공황장애", "수면 문제")),
                    Map.entry("rating", 4.7),
                    Map.entry("reviews", 76),
                    Map.entry("location", "서울 종로구 종로 33"),
                    Map.entry("distance", "4.3km"),
                    Map.entry("phone", "02-4567-8901"),
                    Map.entry("hours", "평일 11:00 - 22:00"),
                    Map.entry("openNow", true),
                    Map.entry("introduction", "야간 상담이 가능한 직장인 친화 상담센터로, 단기 집중 상담 프로그램을 운영합니다.")
            ),
            Map.<String, Object>ofEntries(
                    Map.entry("id", 6),
                    Map.entry("name", "온마음 정신건강의학과 부설 상담센터"),
                    Map.entry("title", "병원 부설 상담센터"),
                    Map.entry("specialty", List.of("의학적 평가", "약물 상담", "ADHD")),
                    Map.entry("rating", 4.8),
                    Map.entry("reviews", 142),
                    Map.entry("location", "서울 서초구 서초대로 99"),
                    Map.entry("distance", "2.0km"),
                    Map.entry("phone", "02-5678-9012"),
                    Map.entry("hours", "평일 09:00 - 18:00"),
                    Map.entry("openNow", true),
                    Map.entry("introduction", "정신건강의학과 진료와 상담이 한 공간에서 이뤄지는 통합 케어 센터입니다.")
            )
    );

    @GetMapping
    public String list(Model model) {
        model.addAttribute("centers", CENTERS);
        return "counseling/list";
    }

    @GetMapping("/booking/{id}")
    public String bookingForm(@PathVariable int id, Model model) {
        Map<String, Object> center = CENTERS.stream()
                .filter(c -> Integer.valueOf(id).equals(c.get("id")))
                .findFirst().orElse(CENTERS.get(0));
        model.addAttribute("counselor", center);
        return "counseling/booking";
    }

    @PostMapping("/booking/{id}")
    public String submitBooking(@PathVariable int id,
                                @RequestParam String name,
                                @RequestParam String phone,
                                @RequestParam String email,
                                @RequestParam String date,
                                @RequestParam String time,
                                Model model) {
        Map<String, Object> center = CENTERS.stream()
                .filter(c -> Integer.valueOf(id).equals(c.get("id")))
                .findFirst().orElse(CENTERS.get(0));
        model.addAttribute("counselor", center);
        model.addAttribute("name", name);
        model.addAttribute("phone", phone);
        model.addAttribute("email", email);
        model.addAttribute("date", date);
        model.addAttribute("time", time);
        return "counseling/booking-complete";
    }
}
