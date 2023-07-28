package solution

import java.lang.Integer.max

class LamportClock(
    var c: Int = 1
) {

    fun tick() {
        c += 1
    }

    fun update(receivedC: Int) {
        c = max(receivedC, c)
    }

}