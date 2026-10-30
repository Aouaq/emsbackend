package ma.electricwave.exception

import org.springframework.http.HttpStatus

class ApiException(val status: HttpStatus, message: String) : RuntimeException(message)
