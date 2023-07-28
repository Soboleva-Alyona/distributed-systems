package internal;

import java.io.Serializable;

public record Message(int sourcePid, int destinationPid, Object content) implements Serializable {
}
