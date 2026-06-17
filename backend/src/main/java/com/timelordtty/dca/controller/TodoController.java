package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.dto.TodayTodoDTO;
import com.timelordtty.dca.service.TodoService;
import com.timelordtty.dca.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 今日待办控制器，提供只读聚合入口，不执行草稿确认、结算或交易动作。
 */
@RestController
@RequestMapping("/api/v2/todos")
public class TodoController {
    private final TodoService todoService;
    private final UserService userService;

    public TodoController(TodoService todoService, UserService userService) {
        this.todoService = todoService;
        this.userService = userService;
    }

    /**
     * 查询当前用户今日待办摘要。
     */
    @GetMapping("/today")
    public ResponseEntity<TodayTodoDTO> getTodayTodos() {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        return ResponseEntity.ok(todoService.getTodayTodos(currentUser.getId(), currentUser.getFamilyId()));
    }
}
