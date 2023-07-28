package solution;

import internal.Environment;

import java.util.LinkedList;

/**
 * Distributed mutual exclusion implementation.
 * All functions are called from the single main thread.
 *
 * @author Elena Soboleva
 */
public class ProcessCentralized implements MutexProcess {
    private final Environment env;

    private final Integer coordinatorId = 1;

    private final String requestMessageString = "Req";

    private final String releaseMessageString = "Rel";


    private final String okRequestMessageString = "Ok request";

    private Boolean coordinatorInCs = false;

    private Boolean notCoordinatorInCs = false;

    private final LinkedList<Integer> queue = new LinkedList<>();


    public ProcessCentralized(Environment env) {
        this.env = env;
    }

    @Override
    public void onMessage(int sourcePid, Object message) {
        MyMessage m = (MyMessage) message;
        switch (m.getValue()) {
            case releaseMessageString -> {
                // coordinator
                notCoordinatorInCs = false;
                if (!queue.isEmpty()) {
                    final Integer waitingProcess = queue.poll();
                    if (!waitingProcess.equals(coordinatorId)) {
                        notCoordinatorInCs = true;
                        env.send(waitingProcess, new MyMessage(waitingProcess, okRequestMessageString));
                    } else {
                        coordinatorInCs = true;
                        env.lock();
                    }
                }
            }
            case requestMessageString -> {
                // coordinator
                if (!coordinatorInCs && !notCoordinatorInCs) {
                    notCoordinatorInCs = true;
                    env.send(sourcePid, new MyMessage(sourcePid, okRequestMessageString));
                } else {
                    queue.add(sourcePid);
                }
            }
            case okRequestMessageString -> {
                // not coordinator
                if (!coordinatorInCs && !notCoordinatorInCs) {
                    notCoordinatorInCs = true;
                    coordinatorInCs = false;
                    env.lock();
                }
            }
        }
    }

    @Override
    public void onLockRequest() {
        if (coordinatorId != env.getProcessId()) {
            env.send(coordinatorId, new MyMessage(coordinatorId, requestMessageString));
        } else {
            if (!notCoordinatorInCs) {
                env.lock();
                coordinatorInCs = true;
            } else {
                queue.add(coordinatorId);
            }
        }
    }

    @Override
    public void onUnlockRequest() {
        env.unlock();
        if (coordinatorId != env.getProcessId()) {
            notCoordinatorInCs = false;
            env.send(coordinatorId, new MyMessage(coordinatorId, releaseMessageString));
        } else {
            coordinatorInCs = false;
            final Integer waitingP = queue.poll();
            if (waitingP != null && waitingP != env.getProcessId()) {
                notCoordinatorInCs = true;
                env.send(waitingP, new MyMessage(waitingP, okRequestMessageString));
            }
        }
    }
}
