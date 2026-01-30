package com.example.LlmSpring.task;

import com.example.LlmSpring.task.request.TaskRequestDTO;
import com.example.LlmSpring.task.response.TaskResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;

    @Transactional
    public void createTask(Long projectId, String userId, TaskRequestDTO requestDTO) {
        TaskVO vo = new TaskVO();
        vo.setProjectId(projectId);
        vo.setTitle(requestDTO.getTitle());
        vo.setUserId(userId);
        vo.setBranch(requestDTO.getBranch());
        vo.setPriority(requestDTO.getPriority());
        vo.setDueDate(requestDTO.getDueDate());
        vo.setStatus("TODO");
        vo.setContent(requestDTO.getContent());

        taskMapper.insertTask(vo);

        if (requestDTO.getAssigneeIds() != null) {
            for (String assigneeId : requestDTO.getAssigneeIds()) {
                taskMapper.insertTaskUser(vo.getTaskId(), assigneeId);
            }
        }
        insertLog(vo.getTaskId(), "CREATE", "업무를 생성했습니다.", userId);
    }

    public List<TaskResponseDTO> getTaskList(Long projectId) {
        List<TaskVO> tasks = taskMapper.selectTasksByProjectId(projectId);
        return tasks.stream().map(task -> {
            TaskResponseDTO dto = new TaskResponseDTO(task);
            dto.setAssigneeIds(taskMapper.selectTaskUsers(task.getTaskId()));
            return dto;
        }).collect(Collectors.toList());
    }

    public TaskResponseDTO getTaskDetail(Long taskId) {
        TaskVO task = taskMapper.selectTaskById(taskId);
        if (task == null) throw new IllegalArgumentException("Task not found");

        TaskResponseDTO dto = new TaskResponseDTO(task);
        dto.setAssigneeIds(taskMapper.selectTaskUsers(taskId));
        dto.setCheckLists(taskMapper.selectCheckLists(taskId));
        return dto;
    }

    @Transactional
    public void updateStatus(Long taskId, String userId, String status) {
        taskMapper.updateTaskStatus(taskId, status);
        insertLog(taskId, "STATUS", "상태를 [" + status + "]로 변경했습니다.", userId);
    }

    @Transactional
    public void updateTask(Long taskId, String userId, TaskRequestDTO requestDTO) {
        TaskVO vo = new TaskVO();
        vo.setTaskId(taskId);
        vo.setTitle(requestDTO.getTitle());
        vo.setContent(requestDTO.getContent());
        vo.setPriority(requestDTO.getPriority());
        vo.setBranch(requestDTO.getBranch());
        vo.setDueDate(requestDTO.getDueDate());

        taskMapper.updateTask(vo);
        insertLog(taskId, "UPDATE", "업무 상세 정보를 수정했습니다.", userId);

        if (requestDTO.getAssigneeIds() != null) {
            taskMapper.deleteTaskUsers(taskId);
            for (String assigneeId : requestDTO.getAssigneeIds()) {
                taskMapper.insertTaskUser(taskId, assigneeId);
            }
        }
    }

    @Transactional
    public void deleteTask(Long taskId) {
        taskMapper.softDeleteTask(taskId);
    }

    public List<TaskCheckListVO> getCheckLists(Long taskId) {
        return taskMapper.selectCheckLists(taskId);
    }

    @Transactional
    public void addCheckList(Long taskId, String userId, String content) {
        TaskCheckListVO vo = new TaskCheckListVO();
        vo.setTaskId(taskId);
        vo.setContent(content);
        taskMapper.insertCheckList(vo);
        insertLog(taskId, "CHECKLIST", "새 할 일 [" + content + "] 추가", userId);
    }

    @Transactional
    public void deleteCheckList(Long taskId, Long checklistId, String userId) {
        taskMapper.deleteCheckList(checklistId);
        insertLog(taskId, "CHECKLIST", "체크리스트 항목 삭제", userId);
    }

    @Transactional
    public void toggleCheckList(Long checklistId, boolean isDone, Long taskId, String userId) {
        taskMapper.updateCheckListStatus(checklistId, isDone);
        String action = isDone ? "완료" : "미완료";
        insertLog(taskId, "CHECKLIST", "항목을 [" + action + "] 상태로 변경", userId);
    }

    public List<Map<String, Object>> getChats(Long taskId) {
        return taskMapper.selectChats(taskId);
    }

    public void addChat(Long taskId, String userId, String content) {
        taskMapper.insertChat(taskId, userId, content);
    }

    public List<Map<String, Object>> getLogs(Long taskId) {
        return taskMapper.selectLogs(taskId);
    }

    private void insertLog(Long taskId, String type, String content, String userId) {
        taskMapper.insertTaskLog(taskId, type, content, userId);
    }
}