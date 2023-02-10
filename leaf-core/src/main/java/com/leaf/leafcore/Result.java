package com.leaf.leafcore;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class Result {
    private List<Long> id;
    private String message;

    private Status status;

    public static Result id(List<Long> id) {
        return new Result(id, null, Status.SUCCESS);
    }

    //系统回拨
    public static Result systemClockGoBack(long currentTimestamp, long lastTimestamp) {
        String message = String.format("Current server system clock go back ! (current timestamp = %d last timestamp = %d)",
                currentTimestamp, lastTimestamp);
        return new Result(null, message, Status.EXCEPTION);
    }
}
