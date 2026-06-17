package com.timelordtty.dca.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.dto.TodayTodoDTO;
import com.timelordtty.dca.service.TodoService;
import com.timelordtty.dca.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * 验证今日待办控制器只读取当前登录用户身份，并委托只读待办服务。
 */
class TodoControllerTest {
    private final TodoService todoService = mock(TodoService.class);
    private final UserService userService = mock(UserService.class);
    private final TodoController todoController = new TodoController(todoService, userService);

    @Test
    void getTodayTodosUsesCurrentUserAndFamilyScope() {
        AuthResponse.UserInfo userInfo = new AuthResponse.UserInfo();
        userInfo.setId(10L);
        userInfo.setFamilyId(20L);
        TodayTodoDTO expected = new TodayTodoDTO();
        expected.setDraftCount(3);
        expected.setTotalCount(3);
        when(userService.getCurrentUser()).thenReturn(userInfo);
        when(todoService.getTodayTodos(10L, 20L)).thenReturn(expected);

        ResponseEntity<TodayTodoDTO> response = todoController.getTodayTodos();

        assertEquals(expected, response.getBody());
        verify(userService).getCurrentUser();
        verify(todoService).getTodayTodos(10L, 20L);
    }
}
