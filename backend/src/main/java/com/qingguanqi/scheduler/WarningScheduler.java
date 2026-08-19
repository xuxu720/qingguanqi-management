package com.qingguanqi.scheduler;

import com.qingguanqi.entity.Warning;
import com.qingguanqi.service.WarningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WarningScheduler {

    private final WarningService warningService;

    @Scheduled(fixedDelay = 300_000) // every 5 minutes
    public void checkStuckPigs() {
        try {
            List<Warning> warnings = warningService.checkStuckConditions();
            if (!warnings.isEmpty()) {
                log.info("生成 {} 条卡阻预警", warnings.size());
            }
        } catch (Exception e) {
            log.error("卡阻预警检查异常", e);
        }
    }
}
