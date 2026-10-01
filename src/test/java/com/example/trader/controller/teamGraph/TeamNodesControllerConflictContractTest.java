package com.example.trader.controller.teamGraph;

import com.example.trader.dto.ResponseUserDto;
import com.example.trader.dto.canvas.ConflictResult;
import com.example.trader.dto.map.UpdateTeamNodeContentRequest;
import com.example.trader.exception.NodeConflictException;
import com.example.trader.security.details.UserContext;
import com.example.trader.service.NodeService;
import com.example.trader.service.TeamNodeContentUpdateCoordinator;
import com.example.trader.service.TeamNodePositionService;
import com.example.trader.ws.raw.edit.NodeEditSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class TeamNodesControllerConflictContractTest {

    @Mock NodeService nodeService;
    @Mock TeamNodeContentUpdateCoordinator coordinator;
    @Mock TeamNodePositionService positionService;
    @Mock NodeEditSessionService editSessionService;
    @Mock UserContext userContext;
    @Mock ResponseUserDto userDto;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(userContext.getUserDto()).thenReturn(userDto);
        when(userDto.getId()).thenReturn(40L);

        TeamNodesController controller = new TeamNodesController(
                nodeService,
                coordinator,
                positionService,
                editSessionService
        );
        mockMvc = standaloneSetup(controller)
                .setCustomArgumentResolvers(authenticationPrincipalResolver())
                .build();
    }

    @Test
    @DisplayName("비중첩 콘텐츠 변경 409는 병합 검토에 필요한 전체 JSON 계약을 보존")
    void serialize_merge_review_required_contract() throws Exception {
        Map<String, Object> currentValues = contentValues("서버 제목", "기존 내용", null);
        Map<String, Object> incomingValues = Map.of("content", "내가 수정한 내용");
        Map<String, Object> proposedValues = contentValues("서버 제목", "내가 수정한 내용", null);
        ConflictResult result = ConflictResult.mergeReviewRequired(
                3,
                4,
                List.of("subject"),
                List.of("content"),
                List.of("content"),
                currentValues,
                incomingValues,
                proposedValues
        );
        when(coordinator.update(eq(1L), eq(2L), eq(3L), eq(40L), any(UpdateTeamNodeContentRequest.class)))
                .thenThrow(new NodeConflictException(result));

        mockMvc.perform(patch("/api/teams/1/graphs/2/nodes/3")
                        .contentType("application/json")
                        .content("""
                                {
                                  "baseVersion": 3,
                                  "dirtyFields": ["content"],
                                  "content": "내가 수정한 내용"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("MERGE_REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.baseVersion").value(3))
                .andExpect(jsonPath("$.currentVersion").value(4))
                .andExpect(jsonPath("$.serverChangedFields[0]").value("subject"))
                .andExpect(jsonPath("$.requestedFields[0]").value("content"))
                .andExpect(jsonPath("$.effectiveFields[0]").value("content"))
                .andExpect(jsonPath("$.conflictingFields").isEmpty())
                .andExpect(jsonPath("$.currentValues.subject").value("서버 제목"))
                .andExpect(jsonPath("$.currentValues.symb").doesNotExist())
                .andExpect(jsonPath("$.incomingValues.content").value("내가 수정한 내용"))
                .andExpect(jsonPath("$.proposedValues.subject").value("서버 제목"))
                .andExpect(jsonPath("$.proposedValues.content").value("내가 수정한 내용"));
    }

    private HandlerMethodArgumentResolver authenticationPrincipalResolver() {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory
            ) {
                return userContext;
            }
        };
    }

    private Map<String, Object> contentValues(String subject, String content, String symb) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("subject", subject);
        values.put("content", content);
        values.put("symb", symb);
        values.put("recordDate", "2026-09-18");
        values.put("noteIds", List.of());
        return values;
    }
}
