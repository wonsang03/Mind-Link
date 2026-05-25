package com.mindlink.chatcluster;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** /community/cluster-viz → /admin/cluster 301 리다이렉트 (일반 사용자 노출 차단) */
@Controller
@RequestMapping("/community")
public class ChatClusterVizController {

    @GetMapping("/cluster-viz")
    public String redirect() {
        return "redirect:/admin/cluster";
    }
}
