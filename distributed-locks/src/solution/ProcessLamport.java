package solution;

import internal.Environment;

/**
 * Distributed mutual exclusion implementation.
 * All functions are called from the single main thread.
 *
 * @author Elena Soboleva
 */
public class ProcessLamport implements MutexProcess {

    private final String requestMessageString = "Req";

    private final String releaseMessageString = "Rel";


    private final String okRequestMessageString = "Ok request";

    private final LamportClock v = new LamportClock();

    private final int[] requestsQueue;

    private final int[] okArray;

    private final Environment env;

    public ProcessLamport(Environment env) {
        this.env = env;
        this.requestsQueue = new int[env.getNumberOfProcesses()];
        this.okArray = new int[env.getNumberOfProcesses()];
    }

    @Override
    public void onMessage(int sourcePid, Object message) {
        MyMessageLamport m = (MyMessageLamport) message;
        final int timeStamp = m.getC();
        v.update(timeStamp);
        switch (m.getValue()) {
            case requestMessageString -> {
                v.tick();
                requestsQueue[sourcePid - 1] = timeStamp;
                env.send(sourcePid, new MyMessageLamport(okRequestMessageString, v.getC()));
            }
            case releaseMessageString -> {
                requestsQueue[sourcePid - 1] = 0;
                if (okayCS()) {
                    requestsQueue[env.getProcessId() - 1] = 0;
                    env.lock();
                }
            }
            case okRequestMessageString -> {
                okArray[sourcePid - 1] = timeStamp;
                if (okayCS()) {
                    requestsQueue[env.getProcessId() - 1] = 0;
                    env.lock();
                }
            }
        }
    }

    private boolean okayCS() {
        final int myPid = env.getProcessId() - 1;
        if (requestsQueue[myPid] == 0) {
            return false;
        }
        for (int otherPid = 0; otherPid < env.getNumberOfProcesses(); otherPid++) {
            if (otherPid == myPid) {
                continue;
            }
            if (okArray[otherPid] == 0) { // ещё не все ok-сообщения получены
                return false;
            }
            if (okArray[otherPid] < requestsQueue[myPid]) { // ещё не все ok-сообщения получены для текущего lockRequest
                return false;
            }
            if (requestsQueue[otherPid] != 0 && requestsQueue[myPid] > requestsQueue[otherPid]) { // есть более приоритетный процесс
                return false;
            }
            if (requestsQueue[myPid] == requestsQueue[otherPid] && myPid > otherPid) { // есть с тем же приоритетом, упорядочиваем по номеру
                return false;
            }
        }
        return true;
    }

    @Override
    public void onLockRequest() {
        final int myId = env.getProcessId();
        v.tick();
        requestsQueue[myId - 1] = v.getC();
        if (env.getNumberOfProcesses() == 1) {
            env.lock();
        }
        broadcastMessage(requestMessageString, v.getC());
    }

    @Override
    public void onUnlockRequest() {
        env.unlock();
        final int myId = env.getProcessId();
        v.tick();
        requestsQueue[myId - 1] = 0;
        broadcastMessage(releaseMessageString, 0);
    }

    private void broadcastMessage(final String message, final int time) {
        final int myId = env.getProcessId();
        for (int i = 1; i <= env.getNumberOfProcesses(); i++) {
            if (i == myId) {
                continue;
            }
            env.send(i, new MyMessageLamport(message, time));
        }
    }
}
