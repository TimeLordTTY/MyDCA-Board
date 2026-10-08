package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.DataReadinessDTO;
import com.timelordtty.dca.service.DataReadinessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/data-readiness")
public class DataReadinessController {
    private final DataReadinessService service;
    public DataReadinessController(DataReadinessService service) { this.service=service; }
    @GetMapping
    public DataReadinessDTO diagnose(@RequestParam(defaultValue="PERSONAL") String scope,
                                     @RequestParam String month) {
        return service.diagnose(scope,month);
    }
    /** Same sanitized boundary for owners and admins; no technical exception details. */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String,String>> error(RuntimeException e) {
        int status=e instanceof AccessDeniedException?403:e instanceof IllegalArgumentException?400:503;
        return ResponseEntity.status(status).body(Map.of("message",status==403?"无权访问该诊断作用域":
                status==400?"作用域或月份参数无效":"诊断暂不可用，请稍后重试"));
    }
}
