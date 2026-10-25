package com.riseai.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseAPI<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ResponseAPI<T> ok(T data) {
        return new ResponseAPI<>(200, "success", data);
    }

    public static <T> ResponseAPI<T> created(T data) {
        return new ResponseAPI<>(201, "created", data);
    }
}
