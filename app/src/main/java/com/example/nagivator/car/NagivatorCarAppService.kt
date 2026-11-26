package com.example.nagivator.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.example.nagivator.data.repository.OfflineRoutingRepository

class NagivatorCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        // Allow all hosts to enable testing with Desktop Head Unit (DHU) and wireless Android Auto
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        OfflineRoutingRepository.init(applicationContext)
        return NagivatorCarSession()
    }
}
