package internal;

import java.io.Serializable;

public record MyMessage(String str, Long content) implements Serializable {

}
