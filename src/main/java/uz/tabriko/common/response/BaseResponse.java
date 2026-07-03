package uz.tabriko.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class BaseResponse<T> {
    private boolean success;
    private int httpStatus;
    private int code;
    private MessageDto message;
    private T data;

    public static <T> BaseResponse<T> ok(T data) {
        BaseResponse<T> r = new BaseResponse<>();
        r.success = true;
        r.httpStatus = 200;
        r.code = 0;
        r.message = MessageDto.ok();
        r.data = data;
        return r;
    }

    public static <T> BaseResponse<T> ok() {
        return ok(null);
    }

    public static <T> BaseResponse<T> created(T data) {
        BaseResponse<T> r = new BaseResponse<>();
        r.success = true;
        r.httpStatus = 201;
        r.code = 0;
        r.message = MessageDto.ok();
        r.data = data;
        return r;
    }

    public static <T> BaseResponse<T> error(int httpStatus, int code, String text) {
        BaseResponse<T> r = new BaseResponse<>();
        r.success = false;
        r.httpStatus = httpStatus;
        r.code = code;
        r.message = MessageDto.of(code, text);
        r.data = null;
        return r;
    }
}
