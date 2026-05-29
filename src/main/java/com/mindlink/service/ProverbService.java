package com.mindlink.service;

import com.mindlink.domain.Proverb;
import com.mindlink.repository.ProverbRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ProverbService {

    private static final Logger log = LoggerFactory.getLogger(ProverbService.class);

    private final ProverbRepository repository;

    public ProverbService(ProverbRepository repository) {
        this.repository = repository;
    }

    /** page 에 해당하는 속담 중 하나를 랜덤 반환. 테이블 미존재 시 null 반환. */
    public Proverb getRandom(String page) {
        try {
            List<Proverb> list = repository.findByPage(page);
            if (list.isEmpty()) return null;
            return list.get(ThreadLocalRandom.current().nextInt(list.size()));
        } catch (Exception e) {
            log.debug("proverbs 테이블 조회 실패 (PROVERBS_SEED.sql 미실행): {}", e.getMessage());
            return null;
        }
    }
}
