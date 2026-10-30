package ma.electricwave.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import ma.electricwave.repository.UserRepository

@Service
class CustomUserDetailsService(private val userRepository: UserRepository) : UserDetailsService {

    override fun loadUserByUsername(email: String): UserDetails {
        val user = userRepository.findByEmail(email.lowercase()).orElseThrow {
            UsernameNotFoundException("Invalid email or password")
        }
        val springUser = org.springframework.security.core.userdetails.User (
            user.email,
            user.password ?: "",
            listOf<GrantedAuthority>(SimpleGrantedAuthority("ROLE_${user.role.name}"))
        )
        return springUser
    }
}
