package backend.yapp.infra.auth

import backend.yapp.core.auth.port.ValueHashPort
import java.security.MessageDigest
import org.springframework.stereotype.Component

@Component
class Sha256ValueHashAdapter : ValueHashPort {
    override fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
