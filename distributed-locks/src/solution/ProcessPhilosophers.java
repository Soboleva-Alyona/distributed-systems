package solution;

import internal.Environment;

/**
 * Distributed mutual exclusion implementation.
 * All functions are called from the single main thread.
 *
 * @author <First-Name> <Last-Name> // todo: replace with your name
 */
public class ProcessPhilosophers implements MutexProcess {
    private final Environment env;

    private final boolean[] fork;

    private final boolean[] request;

    private final boolean[] dirty;

    private final String requestMessageString = "Req";

    private final String okMessageString = "Ok";

    private State myState;


    public ProcessPhilosophers(Environment env) {
        this.env = env;
        this.fork = new boolean[env.getNumberOfProcesses()];
        this.request = new boolean[env.getNumberOfProcesses()];
        this.dirty = new boolean[env.getNumberOfProcesses()];
        this.myState = State.THINKING;
        for (int i = 0; i < env.getNumberOfProcesses(); i++) {
            if (env.getProcessId() - 1 == i) {
                continue;
            }
            if (env.getProcessId() - 1 > i) { // i has higher priority
                fork[i] = false;
                request[i] = true;
            } else {
                fork[i] = true;
                request[i] = false;
            }
            dirty[i] = true;
        }
    }

    @Override
    public void onMessage(int sourcePid, Object message) {
        final String messageString = (String) message;
        switch (messageString) {
            case requestMessageString -> {
                request[sourcePid - 1] = true;
                if (!(myState == State.EATING) && fork[sourcePid - 1] && dirty[sourcePid - 1]) {
                    env.send(sourcePid, okMessageString);
                    fork[sourcePid - 1] = false;
                    if (myState == State.HUNGRY) {
                        env.send(sourcePid, requestMessageString);
                        request[sourcePid - 1] = false;
                    }
                }
            }
            case okMessageString -> {
                fork[sourcePid - 1] = true;
                dirty[sourcePid - 1] = false;
                if (haveForks()) {
                    lock();
                }
            }
        }

    }

    @Override
    public void onLockRequest() {
        myState = State.HUNGRY;
        if (haveForks()) {
            lock();
        } else {
            for (int i = 0; i < env.getNumberOfProcesses(); i++) {
                if (i + 1 == env.getProcessId()) {
                    continue;
                }
                if (request[i] && !fork[i]) { // i requested a fork and we don't hold it
                    env.send(i + 1, requestMessageString);
                    request[i] = false;
                }
            }
        }
    }

    @Override
    public void onUnlockRequest() {
        myState = State.THINKING;
        env.unlock();
        for (int i = 0; i < env.getNumberOfProcesses(); i++) {
            if (i + 1 == env.getProcessId()) {
                continue;
            }
            dirty[i] = true;
            if (request[i]) {
                env.send(i + 1, okMessageString);
                fork[i] = false;
            }
        }
    }

    private void lock() {
        myState = State.EATING;
        env.lock();
    }

    private boolean haveForks() {
        for (int i = 0; i < env.getNumberOfProcesses(); i++) {
            if (i + 1 == env.getProcessId()) {
                continue;
            }
            if (!fork[i]) {
                return false;
            }
        }
        return true;
    }

    private enum State {
        THINKING,
        HUNGRY,
        EATING
    }
}