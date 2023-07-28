package solution;

import internal.Environment;
import internal.MyMessage;

/**
 * Distributed Dijkstra algorithm implementation.
 * All functions are called from the single main thread.
 *
 * @author Elena Soboleva
 */
public class DijkstraProcessImpl implements DijkstraProcess {
    private final Environment env;

    private Long d;

    private int balance;

    private int parentId;

    private int childCnt;

    private final String distanceMessage = "D";

    private final String signalMessage = "Ack";

    private final String newChildMessage = "Child";
    private final String notAChildAnymoreMessage = "Not child";

    private boolean isCoordinator;

    public DijkstraProcessImpl(Environment env) {
        this.env = env;
        this.d = null;
        this.balance = 0;
        this.parentId = -1;
        this.childCnt = 0;
        this.isCoordinator = false;
    }

    @Override
    public void onMessage(int senderPid, Object message) {
        MyMessage m = (MyMessage) message;
        final String contentStr = m.str();
        switch (contentStr) {
            case distanceMessage -> {
                final Long newDist = m.content();
                if (d == null || newDist < d) {
                    d = newDist;
                    if (parentId != -1) {
                        separateFromParent();
                    }
                    parentId = senderPid;
                    sendNewChildMessage();
                    sendDistanceToNeighbours();
                }
                sendAckMessage(senderPid);
            }
            case signalMessage -> {
                balance--;
            }
            case newChildMessage -> {
                childCnt++;
            }
            case notAChildAnymoreMessage -> {
                childCnt--;
            }
        }
        tryTerminate();
    }

    @Override
    public Long getDistance() {
        return d;
    }

    @Override
    public void onComputationStart() {
        this.isCoordinator = true;
        this.d = 0L;
        sendDistanceToNeighbours();
        tryTerminate();
    }

    private void tryTerminate() {
        if (balance == 0 && childCnt == 0) {
            if (isCoordinator) {
                env.finishExecution();
            } else {
                if (parentId != -1) {
                    separateFromParent();
                }
            }
        }
    }

    private void separateFromParent() {
        env.send(parentId, new MyMessage(notAChildAnymoreMessage, 0L));
        this.parentId = -1;
    }

    private void sendAckMessage(final int senderPid) {
        env.send(senderPid, new MyMessage(signalMessage, 0L));
    }

    private void sendNewChildMessage() {
        env.send(parentId, new MyMessage(newChildMessage, 0L));
    }

    private void sendDistanceToNeighbours() {
        for (final var entry : env.getNeighbours().entrySet()) {
            if (entry.getKey() == parentId || entry.getKey() == env.getProcessId()) {
                continue;
            }
            final Long newDist = d + entry.getValue();
            balance++;
            env.send(entry.getKey(), new MyMessage(distanceMessage, newDist));
        }
    }

}
