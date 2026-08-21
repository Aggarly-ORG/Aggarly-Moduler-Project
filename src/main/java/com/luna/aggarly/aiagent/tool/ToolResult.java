package com.luna.aggarly.aiagent.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult<T> {
    private boolean success;
    private T data;
    private String errorCode;
    private String errorMessage;
    private String message;

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public String getMessage() {
        return message != null ? message : errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage != null ? errorMessage : message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public static <T> ToolResult<T> ok(T data) {
        return ToolResult.<T>builder().success(true).data(data).build();
    }

    public static <T> ToolResult<T> success(T data) {
        return ToolResult.<T>builder().success(true).data(data).build();
    }

    public static <T> ToolResult<T> failed(String errorCode, String errorMessage) {
        return ToolResult.<T>builder().success(false).errorCode(errorCode).errorMessage(errorMessage).message(errorMessage).build();
    }

    public static <T> ToolResult<T> failure(String errorMessage) {
        return ToolResult.<T>builder().success(false).errorCode("TOOL_EXECUTION_ERROR").errorMessage(errorMessage).message(errorMessage).build();
    }
}
