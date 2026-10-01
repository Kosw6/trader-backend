package com.example.trader.exception;

import com.example.trader.entity.NodeHistory;
import com.example.trader.httpresponse.BaseResponseStatus;
import lombok.Getter;

@Getter
public class NodeHistoryCorruptedException extends BaseException {

    private final Long historyId;
    private final Long nodeId;
    private final Integer version;
    private final NodeHistoryCorruptionReason reason;

    public NodeHistoryCorruptedException(
            NodeHistory history,
            NodeHistoryCorruptionReason reason
    ) {
        super(BaseResponseStatus.NODE_HISTORY_CORRUPTED);
        this.historyId = history.getId();
        this.nodeId = history.getNodeId();
        this.version = history.getVersion();
        this.reason = reason;
    }

    public NodeHistoryCorruptedException(
            NodeHistory history,
            NodeHistoryCorruptionReason reason,
            Throwable cause
    ) {
        this(history, reason);
        initCause(cause);
    }
}
