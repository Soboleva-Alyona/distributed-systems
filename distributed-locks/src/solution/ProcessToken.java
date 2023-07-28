package solution;

import internal.Environment;

/**
 * Distributed mutual exclusion implementation.
 * All functions are called from the single main thread.
 *
 * @author <First-Name> <Last-Name> // todo: replace with your name
 */
public class ProcessToken implements MutexProcess {
    private final Environment env;

    private boolean haveToken = false;

    private boolean requestedToCs;

    private final String freeTokenMessage = "token";


    public ProcessToken(Environment env) {
        this.env = env;
        if (env.getProcessId() == 1) {
            sendTokenToNextProcess();
        }
        this.requestedToCs = false;
    }

    @Override
    public void onMessage(int sourcePid, Object message) {
        final String messageString = (String) message;
        if (messageString.equals(freeTokenMessage)) {
            if (requestedToCs) {
                haveToken = true;
                env.lock();
                requestedToCs = false;
            } else {
                sendTokenToNextProcess();
            }
        }
    }

    @Override
    public void onLockRequest() {
        if (haveToken) {
            env.lock();
        } else {
            requestedToCs = true;
        }
    }

    @Override
    public void onUnlockRequest() {
        env.unlock();
        haveToken = false;
        sendTokenToNextProcess();
    }

    private void sendTokenToNextProcess() {
        final int nextProcess = env.getProcessId() % env.getNumberOfProcesses() + 1;
        env.send(nextProcess, freeTokenMessage);
    }
}
