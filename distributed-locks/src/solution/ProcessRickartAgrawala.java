package solution;

import internal.Environment;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Distributed mutual exclusion implementation.
 * All functions are called from the single main thread.
 *
 * @author <First-Name> <Last-Name> // todo: replace with your name
 */
public class ProcessRickartAgrawala implements MutexProcess {

    private final String requestMessageString = "Req";

    private final String okMessageString = "Ok";

    private final LamportClock v = new LamportClock();

    private int okCnt;

    private int myRequestTS;

    private final LinkedList<Integer> pendingQueue = new LinkedList<>();


    private final Environment env;

    public ProcessRickartAgrawala(Environment env) {
        this.env = env;
        this.okCnt = 0;
        this.myRequestTS = 0;
    }

    @Override
    public void onMessage(int sourcePid, Object message) {
        final MyMessageLamport m = (MyMessageLamport) message;
        final int myPid = env.getProcessId();
        final int timeStamp = m.getC();
        v.update(timeStamp);

        switch (m.getValue()) {
            case requestMessageString -> {
                v.tick();
                if (myRequestTS == 0 || timeStamp < myRequestTS || timeStamp == myRequestTS && sourcePid < myPid) {
                    env.send(sourcePid, new MyMessageLamport(okMessageString, v.getC()));
                } else {
                    pendingQueue.add(sourcePid);
                }
            }
            case okMessageString -> {
                okCnt++;
                if (okCnt == env.getNumberOfProcesses() - 1) {
                    env.lock();
                }
            }
        }
    }

    @Override
    public void onLockRequest() {
        if (env.getNumberOfProcesses() == 1) {
            env.lock();
        }
        v.tick();
        myRequestTS = v.getC();
        broadcastRequest(v.getC());
        okCnt = 0;
    }

    @Override
    public void onUnlockRequest() {
        env.unlock();
        myRequestTS = 0;
        v.tick();
        while (!pendingQueue.isEmpty()) {
            final int pid = pendingQueue.remove();
            env.send(pid, new MyMessageLamport(okMessageString, v.getC()));
        }
    }

    private void broadcastRequest(final int time) {
        final int myId = env.getProcessId();
        for (int i = 1; i <= env.getNumberOfProcesses(); i++) {
            if (i == myId) {
                continue;
            }
            env.send(i, new MyMessageLamport(requestMessageString, time));
        }
    }
}
