package solution

data class MyMessage(
    val key: Int,
    val value: String
    ) : java.io.Serializable

data class MyMessageLamport(
    val value: String,
    val c: Int
) : java.io.Serializable