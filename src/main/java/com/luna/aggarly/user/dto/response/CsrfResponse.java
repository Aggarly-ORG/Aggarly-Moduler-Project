package com.luna.aggarly.user.dto.response;

public record CsrfResponse(String token,String headerName,String ParameterName) {
}
