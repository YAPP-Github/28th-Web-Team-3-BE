package backend.yapp.core.auth.port

interface ValueHashPort {
    fun hash(value: String): String
}
