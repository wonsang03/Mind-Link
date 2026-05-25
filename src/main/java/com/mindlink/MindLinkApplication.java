package com.mindlink;

import com.mindlink.config.DotEnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Mind-Link 진입점. 모든 컴포넌트·엔티티·리포지토리가 com.mindlink 하위에 있으므로
 * 기본 컴포넌트 스캔·@EntityScan·JPA 리포지토리 스캔으로 충분하다.
 */
@SpringBootApplication
public class MindLinkApplication {

    public static void main(String[] args) {
        DotEnvLoader.load();
        SpringApplication.run(MindLinkApplication.class, args);
    }
}
